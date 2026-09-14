"""Immutable HGT score snapshots. Querying is O(1); refresh runs off the hot path.

Unknown, sparse, stale and unavailable evidence abstain. No fabricated scores.
This module contains no token signing secrets and never serves raw graph data.
"""
from pathlib import Path
from dataclasses import dataclass
from types import MappingProxyType
import hashlib
import threading
import time
import numpy as np
import torch
from ml.graph.data.loader import load_hetero_data, FEATURE_VERSION
from ml.graph.model.hgt import HGTModel

ROOT = Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class Snapshot:
    scores: object
    by_vpa: object
    account_stats: object
    version: str
    refreshed_at: float
    graph_as_of: float
    inference_ms: float
    synthetic: bool


class GraphInferenceEngine:
    def __init__(self, max_snapshot_age=300, allow_synthetic=False):
        self._snapshot = None
        self._refresh_lock = threading.Lock()
        self.max_snapshot_age = max_snapshot_age
        self.allow_synthetic = allow_synthetic

    @property
    def loaded(self):
        return self._snapshot is not None

    def load(self, checkpoint_path=None, data_split="full", data_dir=None):
        with self._refresh_lock:
            path = Path(checkpoint_path or ROOT / "checkpoints/hgt_best.pt")
            ckpt = torch.load(path, map_location="cpu", weights_only=True)
            if ckpt.get("schema_version") != 2 or ckpt.get("feature_version") != FEATURE_VERSION:
                raise ValueError("Incompatible HGT checkpoint; retrain using v2 pipeline")
            synthetic = ckpt.get("data_kind") == "synthetic"
            if synthetic and not self.allow_synthetic:
                raise ValueError("Synthetic model requires explicit demo mode")
            if not ckpt.get("calibration"):
                raise ValueError("Uncalibrated checkpoint")
            model = HGTModel(**ckpt["config"]).eval()
            model.load_state_dict(ckpt["model_state_dict"])
            data = load_hetero_data(data_split, data_dir=data_dir)
            as_of = data["metadata"]["as_of"]
            if not synthetic and (time.time() - as_of > self.max_snapshot_age or as_of > time.time() + 30):
                raise ValueError("Graph snapshot is stale or future-dated")
            started = time.perf_counter()
            with torch.inference_mode():
                out = model(data["node_features"], data["edge_index"], data["edge_attr"])
            elapsed = (time.perf_counter() - started) * 1000
            values = out["risk_scores"].numpy()
            if not np.isfinite(values).all():
                raise ValueError("Non-finite model predictions")
            scores = {account: float(values[i]) for account, i in data["node_maps"]["account"].items()}
            # Resolve from actual OWNS edges, never identifier naming conventions.
            by_vpa = {hashlib.sha256(vpa.encode()).hexdigest(): account
                      for vpa, account in data["vpa_to_account"].items()}
            stats = {account: {k: float(v[i]) for k, v in data["account_stats"].items()}
                     for account, i in data["node_maps"]["account"].items()}
            version = "hgt-v2-" + hashlib.sha256(path.read_bytes()).hexdigest()[:16]
            snapshot = Snapshot(MappingProxyType(scores), MappingProxyType(by_vpa),
                                MappingProxyType(stats), version, time.time(), as_of, elapsed, synthetic)
            # Readers see the complete old or complete new snapshot, never half a refresh.
            self._snapshot = snapshot

    def query_risk(self, account_id=None, vpa_hash=None):
        snapshot = self._snapshot
        unknown = {"risk_score": None, "confidence": 0.0, "risk_class": "UNKNOWN",
                   "reason_codes": ["GRAPH_UNAVAILABLE"], "model_version": "unavailable",
                   "graph_as_of": 0, "data_kind": "unknown"}
        if snapshot is None:
            return unknown
        base = {**unknown, "model_version": snapshot.version, "graph_as_of": int(snapshot.graph_as_of),
                "data_kind": "synthetic" if snapshot.synthetic else "institutional"}
        if time.time() - snapshot.refreshed_at >= self.max_snapshot_age or (
                not snapshot.synthetic and time.time() - snapshot.graph_as_of >= self.max_snapshot_age):
            return {**base, "reason_codes": ["STALE_GRAPH"]}
        account = snapshot.by_vpa.get(vpa_hash) if vpa_hash else account_id
        if account not in snapshot.scores:
            return {**base, "reason_codes": ["UNKNOWN_RECEIVER"]}
        stats = snapshot.account_stats[account]
        if stats["activity_count"] < 3:
            return {**base, "reason_codes": ["INSUFFICIENT_HISTORY"]}
        risk = snapshot.scores[account]
        reasons = []
        # Observed support signals, NOT causal model explanations or allegations.
        if stats["in_count"] >= 20:
            reasons.append("HIGH_FAN_IN")
        if stats["out_count"] >= 5 and stats["mean_iat_seconds"] < 3600:
            reasons.append("RAPID_FAN_OUT")
        if not reasons:
            reasons.append("MODEL_NETWORK_PATTERN")
        return {**base, "risk_score": round(risk, 6), "confidence": round(abs(2 * risk - 1), 6),
                "risk_class": "HIGH" if risk >= .7 else "LOW" if risk < .3 else "ELEVATED",
                "reason_codes": reasons}

    def get_stats(self):
        s = self._snapshot
        return {"loaded": s is not None, "mode": "hgt-snapshot" if s else "unavailable",
                **({"num_accounts": len(s.scores), "model_version": s.version,
                    "snapshot_age_s": time.time() - s.refreshed_at, "inference_ms": s.inference_ms,
                    "data_kind": "synthetic" if s.synthetic else "institutional"} if s else {})}
