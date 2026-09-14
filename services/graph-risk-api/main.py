"""
Vyuha 2.0 -- Graph Risk API (HGT-Backed)
==========================================
Serves GraphRiskTokens backed by the trained HGT model.

Falls back to deterministic mock if:
    - No trained HGT checkpoint exists
    - Model loading fails
    - Graph data not available

Endpoints:
    POST /v1/graph-risk   -> GraphRiskToken (signed)
    GET  /v1/graph-risk/stats -> Model & inference stats
    POST /v1/telemetry    -> Async audit payload (placeholder)
    GET  /v1/bundles      -> ML model bundle metadata
    GET  /health          -> Service health check

Architecture ref: ARCHITECTURE_FREEZE_V1 sec11, ADR-003 secGraph Output.
"""

import hashlib
import hmac
import json
import os
import sys
import time
from enum import Enum
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Add project root to path for ML imports
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, PROJECT_ROOT)

# ── App ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="Vyuha Graph Risk API",
    version="0.2.0",
    description="HGT-backed graph risk scoring service for the Vyuha Edge SDK",
)

# ── Shared secret for HMAC signing (demo only) ──────────────────────
SIGNING_SECRET = b"vyuha-demo-secret-do-not-use-in-prod"

# ── Known-safe VPAs (Slice 0 golden path) ───────────────────────────
KNOWN_SAFE_VPAS = {
    hashlib.sha256(b"friend@upi").hexdigest(),
    hashlib.sha256(b"mom@upi").hexdigest(),
    hashlib.sha256(b"known_payee@upi").hexdigest(),
}

# ── High-risk VPA hashes (Slice 1 coercion scenario) ────────────────
HIGH_RISK_VPAS = {
    hashlib.sha256(b"unknown_scammer@upi").hexdigest(): ["HIGH_FAN_IN", "MULE_RING_CLUSTER"],
    hashlib.sha256(b"unknown_vpa@upi").hexdigest(): ["HIGH_FAN_IN", "NEW_VPA_BURST"],
}

# ── ML Engine (loaded at startup) ───────────────────────────────────
_inference_engine = None
_engine_mode = "deterministic-mock"


def _load_ml_engine():
    """Attempt to load the HGT inference engine."""
    global _inference_engine, _engine_mode
    try:
        from ml.graph.inference.serve import GraphInferenceEngine
        engine = GraphInferenceEngine()
        engine.load()
        if engine.loaded:
            _inference_engine = engine
            _engine_mode = "hgt-inference"
            print("[GraphRiskAPI] HGT inference engine loaded successfully")
        else:
            _engine_mode = "deterministic-mock"
            print("[GraphRiskAPI] HGT not available, using deterministic mock")
    except Exception as e:
        _engine_mode = "deterministic-mock"
        print(f"[GraphRiskAPI] ML engine load failed ({e}), using deterministic mock")


@app.on_event("startup")
async def startup_event():
    """Load ML model on startup."""
    _load_ml_engine()


# ── Request / Response models (match contracts/) ────────────────────
class GraphRiskRequest(BaseModel):
    vpa_hash: str = Field(..., description="SHA-256 hash of the beneficiary VPA")
    session_id: str = Field(..., description="Transaction session UUID")


class GraphRiskToken(BaseModel):
    """Matches contracts/GraphRiskToken.schema.json exactly."""
    vpa_hash: str
    risk_score: float = Field(ge=0.0, le=1.0)
    confidence: float = Field(ge=0.0, le=1.0)
    reason_codes: List[str]
    issued_at: int
    expires_at: int
    model_version: str
    signature: str


class TelemetryPayload(BaseModel):
    """Async audit payload from the SDK."""
    session_id: str
    event_type: str
    payload: dict = Field(default_factory=dict)
    timestamp_ms: int = Field(default=0)


class BundleInfo(BaseModel):
    """ML model bundle metadata."""
    bundle_id: str
    model_type: str
    version: str
    size_bytes: int
    checksum: str
    download_url: str


def _sign_token(payload: dict) -> str:
    """HMAC-SHA256 signature over the token payload (demo only)."""
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
    return hmac.new(SIGNING_SECRET, canonical.encode(), hashlib.sha256).hexdigest()


def _deterministic_risk(vpa_hash: str) -> dict:
    """Deterministic mock risk scoring (fallback)."""
    now = int(time.time())

    if vpa_hash in KNOWN_SAFE_VPAS:
        risk_score = 0.05
        confidence = 0.95
        reason_codes = []
    elif vpa_hash in HIGH_RISK_VPAS:
        risk_score = 0.92
        confidence = 0.88
        reason_codes = HIGH_RISK_VPAS[vpa_hash]
    else:
        risk_score = 0.45
        confidence = 0.60
        reason_codes = ["UNKNOWN_VPA"]

    payload = {
        "vpa_hash": vpa_hash,
        "risk_score": risk_score,
        "confidence": confidence,
        "reason_codes": reason_codes,
        "issued_at": now,
        "expires_at": now + 3600,
        "model_version": "hgt-v0.1-mock",
    }
    payload["signature"] = _sign_token(payload)
    return payload


# ── Endpoints ────────────────────────────────────────────────────────

@app.post("/v1/graph-risk", response_model=GraphRiskToken)
async def get_graph_risk(req: GraphRiskRequest):
    """
    Score a beneficiary VPA hash using the HGT model.

    If the HGT model is loaded, runs real graph inference.
    Otherwise, falls back to deterministic mock routing.
    """
    if _inference_engine and _inference_engine.loaded:
        # Use trained HGT model
        token = _inference_engine.query_risk(vpa_hash=req.vpa_hash)
        return GraphRiskToken(**token)
    else:
        # Deterministic fallback
        token = _deterministic_risk(req.vpa_hash)
        return GraphRiskToken(**token)


@app.get("/v1/graph-risk/stats")
async def get_stats():
    """Return model and inference engine statistics."""
    if _inference_engine and _inference_engine.loaded:
        return {
            "mode": _engine_mode,
            "engine_stats": _inference_engine.get_stats(),
        }
    return {
        "mode": _engine_mode,
        "engine_stats": {"loaded": False},
    }


@app.post("/v1/telemetry")
async def ingest_telemetry(payload: TelemetryPayload):
    """
    Receive async telemetry/audit data from the SDK.
    In production: persists to audit store. Here: logs and acks.
    """
    print(f"[Telemetry] session={payload.session_id} "
          f"event={payload.event_type} ts={payload.timestamp_ms}")
    return {"status": "accepted", "session_id": payload.session_id}


@app.get("/v1/bundles")
async def list_bundles():
    """
    List available ML model bundles for OTA download.
    In production: signed bundles are downloaded by the SDK.
    """
    exports_dir = os.path.join(PROJECT_ROOT, "exports")
    bundles = []

    # Check for MoE export
    moe_path = os.path.join(exports_dir, "edge_risk_moe.pt")
    if os.path.exists(moe_path):
        size = os.path.getsize(moe_path)
        bundles.append(BundleInfo(
            bundle_id="edge-risk-moe-v0.1",
            model_type="EdgeRiskMoE",
            version="v0.1",
            size_bytes=size,
            checksum=hashlib.sha256(open(moe_path, "rb").read()).hexdigest()[:16],
            download_url="/v1/bundles/edge-risk-moe-v0.1/download"
        ))

    # Check for HGT export
    hgt_path = os.path.join(exports_dir, "hgt_server.pt")
    if os.path.exists(hgt_path):
        size = os.path.getsize(hgt_path)
        bundles.append(BundleInfo(
            bundle_id="hgt-server-v0.1",
            model_type="HGT",
            version="v0.1",
            size_bytes=size,
            checksum=hashlib.sha256(open(hgt_path, "rb").read()).hexdigest()[:16],
            download_url="/v1/bundles/hgt-server-v0.1/download"
        ))

    return {"bundles": bundles, "count": len(bundles)}


@app.get("/health")
async def health():
    """Service health check."""
    return {
        "status": "ok",
        "mode": _engine_mode,
        "model_loaded": _inference_engine.loaded if _inference_engine else False,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
