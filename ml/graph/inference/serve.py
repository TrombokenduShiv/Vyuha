"""
Vyuha 2.0 — Graph Inference Service
======================================
Server-side HGT inference module used by the Graph Risk API.
Loads the trained HGT model checkpoint and runs inference on
the full graph to produce GraphRiskTokens.

This bridges the gap between:
    - ml/graph/training (trains the model)
    - services/graph-risk-api (serves tokens to the SDK)

Architecture ref: ARCHITECTURE_FREEZE_V1 §2 (Component Diagram),
                  ADR-003 §Graph Output.
"""
import os
import sys
import json
import time
import hashlib
import hmac
import numpy as np
import torch

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.graph.model.hgt import HGTModel
from ml.graph.data.loader import load_hetero_data

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../checkpoints"))
SIGNING_SECRET = b"vyuha-demo-secret-do-not-use-in-prod"

# Reason code thresholds
REASON_CODE_THRESHOLDS = {
    "HIGH_FAN_IN": {"feature_idx": 0, "threshold": 0.7},       # in-degree
    "RAPID_FAN_OUT": {"feature_idx": 1, "threshold": 0.7},     # out-degree
    "BURST_ACTIVITY": {"feature_idx": 4, "threshold": 0.6},    # tx count
    "TEMPORAL_CHAIN_PATTERN": {"feature_idx": 7, "threshold": 0.5},  # mean IAT
}


class GraphInferenceEngine:
    """
    Loads the trained HGT model and precomputes risk scores
    for all accounts in the graph.

    Usage:
        engine = GraphInferenceEngine()
        engine.load()
        token = engine.query_risk("acc_12345")
    """

    def __init__(self):
        self.model = None
        self.risk_scores = None
        self.confidence_scores = None
        self.node_features = None
        self.account_map = None
        self.reverse_map = None  # idx -> account_id
        self.loaded = False

    def load(self, checkpoint_path: str = None, data_split: str = "full"):
        """Load model and precompute all account risk scores."""
        if checkpoint_path is None:
            checkpoint_path = os.path.join(CHECKPOINT_DIR, "hgt_best.pt")

        if not os.path.exists(checkpoint_path):
            print(f"[GraphInference] No checkpoint at {checkpoint_path}")
            print("[GraphInference] Running in fallback (deterministic) mode")
            self.loaded = False
            return

        print(f"[GraphInference] Loading HGT model from {checkpoint_path}")
        ckpt = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
        config = ckpt["config"]

        self.model = HGTModel(
            feature_dims=config["feature_dims"],
            hidden_dim=config["hidden_dim"],
            num_heads=config["num_heads"],
            num_layers=config["num_layers"],
        )
        self.model.load_state_dict(ckpt["model_state_dict"])
        self.model.eval()

        # Load graph data
        print(f"[GraphInference] Loading graph data (split={data_split})...")
        data = load_hetero_data(data_split)

        self.account_map = data["node_maps"]["account"]
        self.reverse_map = {v: k for k, v in self.account_map.items()}
        self.node_features = data["node_features"]

        # Run full-graph inference
        print("[GraphInference] Running full-graph inference...")
        t0 = time.perf_counter()
        with torch.no_grad():
            out = self.model(
                data["node_features"],
                data["edge_index"],
                data["edge_attr"]
            )
        t1 = time.perf_counter()

        self.risk_scores = out["risk_scores"].numpy()
        self.confidence_scores = out["confidence"].numpy()

        print(f"[GraphInference] Inference complete in {(t1-t0)*1000:.1f}ms")
        print(f"[GraphInference] {len(self.risk_scores)} accounts scored")
        print(f"[GraphInference] Risk range: [{self.risk_scores.min():.3f}, {self.risk_scores.max():.3f}]")

        self.loaded = True

    def query_risk(self, account_id: str = None, vpa_hash: str = None) -> dict:
        """
        Query risk for an account or VPA hash.

        Returns a GraphRiskToken-compatible dict with:
            - risk_score, confidence, reason_codes, model_version,
              issued_at, expires_at, signature
        """
        now = int(time.time())

        if not self.loaded:
            # Fallback: medium risk for unknown
            return self._build_token(
                vpa_hash=vpa_hash or "unknown",
                risk_score=0.45,
                confidence=0.50,
                reason_codes=["FALLBACK_MODE"],
                model_version="hgt-v0.1-fallback"
            )

        # Try to find the account
        idx = None
        if account_id and account_id in self.account_map:
            idx = self.account_map[account_id]
        elif vpa_hash:
            # Try matching by VPA naming convention
            for acc_id, acc_idx in self.account_map.items():
                vpa_id = f"vpa_{acc_id.split('_')[1]}" if '_' in acc_id else acc_id
                if hashlib.sha256(vpa_id.encode()).hexdigest() == vpa_hash:
                    idx = acc_idx
                    break

        if idx is None:
            # Unknown account — return medium risk
            return self._build_token(
                vpa_hash=vpa_hash or "unknown",
                risk_score=0.45,
                confidence=0.50,
                reason_codes=["UNKNOWN_ACCOUNT"],
                model_version="hgt-v0.1"
            )

        risk = float(self.risk_scores[idx])
        conf = float(self.confidence_scores[idx])

        # Generate reason codes from node features
        reason_codes = self._compute_reason_codes(idx)

        return self._build_token(
            vpa_hash=vpa_hash or account_id or "unknown",
            risk_score=round(risk, 4),
            confidence=round(conf, 4),
            reason_codes=reason_codes,
            model_version="hgt-v0.1"
        )

    def _compute_reason_codes(self, account_idx: int) -> list:
        """Derive explainability reason codes from node features."""
        codes = []
        features = self.node_features["account"][account_idx].numpy()

        for code, spec in REASON_CODE_THRESHOLDS.items():
            feat_idx = spec["feature_idx"]
            if feat_idx < len(features) and features[feat_idx] > spec["threshold"]:
                codes.append(code)

        # Risk-based codes
        risk = float(self.risk_scores[account_idx])
        if risk > 0.8:
            codes.append("MULE_NEIGHBORHOOD")
        if risk > 0.6 and "BURST_ACTIVITY" in codes:
            codes.append("SHARED_DEVICE_CLUSTER")

        return codes

    def _build_token(self, vpa_hash: str, risk_score: float, confidence: float,
                     reason_codes: list, model_version: str) -> dict:
        """Build a signed GraphRiskToken."""
        now = int(time.time())
        payload = {
            "vpa_hash": vpa_hash,
            "risk_score": risk_score,
            "confidence": confidence,
            "reason_codes": reason_codes,
            "issued_at": now,
            "expires_at": now + 3600,
            "model_version": model_version,
        }

        # HMAC signature
        canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        signature = hmac.new(SIGNING_SECRET, canonical.encode(), hashlib.sha256).hexdigest()
        payload["signature"] = signature

        return payload

    def get_stats(self) -> dict:
        """Return engine statistics."""
        if not self.loaded:
            return {"loaded": False, "mode": "fallback"}

        return {
            "loaded": True,
            "mode": "hgt-inference",
            "num_accounts": len(self.risk_scores),
            "risk_mean": round(float(self.risk_scores.mean()), 4),
            "risk_std": round(float(self.risk_scores.std()), 4),
            "high_risk_count": int((self.risk_scores > 0.7).sum()),
            "low_risk_count": int((self.risk_scores < 0.3).sum()),
        }


if __name__ == "__main__":
    print("=" * 60)
    print("Vyuha 2.0 -- Graph Inference Engine Test")
    print("=" * 60)

    engine = GraphInferenceEngine()
    engine.load()

    print(f"\nEngine stats: {json.dumps(engine.get_stats(), indent=2)}")

    # Query some accounts
    for acc_id in ["acc_0", "acc_100", "acc_500", "acc_9999"]:
        token = engine.query_risk(account_id=acc_id)
        print(f"\n{acc_id}: risk={token['risk_score']:.4f}, "
              f"confidence={token['confidence']:.4f}, "
              f"reasons={token['reason_codes']}")

    # Query unknown
    unknown = engine.query_risk(vpa_hash="deadbeef1234")
    print(f"\nUnknown VPA: risk={unknown['risk_score']}, reasons={unknown['reason_codes']}")
