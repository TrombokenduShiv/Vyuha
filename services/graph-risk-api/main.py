"""
Vyuha 2.0 — Graph Risk API Simulator
=====================================
Simulates the institutional ST-GNN backend.
Returns signed GraphRiskTokens per the frozen contract schema.

This is a DETERMINISTIC MOCK for Vertical Slices 0 & 1.
Known VPAs return low risk; unknown VPAs return high risk.
"""

import hashlib
import hmac
import json
import time
from enum import Enum
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# ── App ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="Vyuha Graph Risk API (Simulator)",
    version="0.1.0-slice",
    description="Deterministic mock for Vertical Slices 0 & 1",
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
    model_version: str = "hgt-v0.1-mock"
    signature: str


def _sign_token(payload: dict) -> str:
    """HMAC-SHA256 signature over the token payload (demo only)."""
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
    return hmac.new(SIGNING_SECRET, canonical.encode(), hashlib.sha256).hexdigest()


@app.post("/v1/graph-risk", response_model=GraphRiskToken)
async def get_graph_risk(req: GraphRiskRequest):
    """
    Deterministic routing:
    - If vpa_hash is in KNOWN_SAFE_VPAS  → low risk (0.05)
    - If vpa_hash is in HIGH_RISK_VPAS   → high risk (0.92)
    - Otherwise                          → medium risk (0.45)
    """
    now = int(time.time())

    if req.vpa_hash in KNOWN_SAFE_VPAS:
        risk_score = 0.05
        confidence = 0.95
        reason_codes = []
    elif req.vpa_hash in HIGH_RISK_VPAS:
        risk_score = 0.92
        confidence = 0.88
        reason_codes = HIGH_RISK_VPAS[req.vpa_hash]
    else:
        risk_score = 0.45
        confidence = 0.60
        reason_codes = ["UNKNOWN_VPA"]

    payload = {
        "vpa_hash": req.vpa_hash,
        "risk_score": risk_score,
        "confidence": confidence,
        "reason_codes": reason_codes,
        "issued_at": now,
        "expires_at": now + 3600,
        "model_version": "hgt-v0.1-mock",
    }
    signature = _sign_token(payload)

    return GraphRiskToken(**payload, signature=signature)


@app.get("/health")
async def health():
    return {"status": "ok", "mode": "deterministic-mock"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
