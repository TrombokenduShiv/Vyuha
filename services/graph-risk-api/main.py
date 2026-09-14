"""HGT-backed institutional service. Explicit demo mode; authenticated by default."""
from contextlib import asynccontextmanager
from pathlib import Path
import asyncio
import hmac
import logging
import os
import sys
import torch
from uuid import UUID
from fastapi import FastAPI, Depends, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from ml.graph.inference.serve import GraphInferenceEngine
from ml.graph.inference.tokens import TokenSigner

log = logging.getLogger("vyuha.graph")


class GraphRiskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    vpa_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    session_id: UUID


def create_app(engine=None, signer=None, demo=None):
    demo = os.getenv("VYUHA_DEMO_MODE") == "1" if demo is None else demo
    engine = engine or GraphInferenceEngine(allow_synthetic=demo)
    api_key = os.getenv("VYUHA_API_KEY", "")
    audience = os.getenv("VYUHA_AUDIENCE", "vyuha-demo")

    @asynccontextmanager
    async def lifespan(app):
        nonlocal signer
        torch.set_num_threads(int(os.getenv("VYUHA_TORCH_THREADS", "2")))
        if not demo and (len(api_key) < 32 or audience == "vyuha-demo"):
            raise RuntimeError("Configure institutional API key and audience, or explicitly enable demo mode")
        if signer is None:
            key_path = os.getenv("VYUHA_SIGNING_KEY_FILE")
            if key_path:
                signer = TokenSigner.from_pem(Path(key_path).read_bytes(), os.environ["VYUHA_KEY_ID"])
            elif demo:
                signer = TokenSigner()
            else:
                raise RuntimeError("Configure institutional signing key")
        app.state.signer = signer
        checkpoint = os.getenv("VYUHA_HGT_CHECKPOINT")
        data_dir = os.getenv("VYUHA_GRAPH_DATA_DIR")
        async def refresh():
            try:
                await asyncio.to_thread(engine.load, checkpoint, "full", data_dir)
            except Exception:
                # Do not leak filesystem paths or beneficiary information to clients/logs.
                log.error("Graph refresh failed; unavailable or stale evidence will abstain")
        if not engine.loaded:
            await refresh()
        async def refresh_loop():
            while True:
                await asyncio.sleep(60)
                await refresh()
        task = asyncio.create_task(refresh_loop())
        yield
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    app = FastAPI(title="Vyuha Counterparty Integrity API", version="2.1.0", lifespan=lifespan)
    app.state.engine = engine

    def authenticate(x_vyuha_api_key: str = Header(default="")):
        if not demo and not hmac.compare_digest(x_vyuha_api_key, api_key):
            raise HTTPException(401, "Institutional authentication required")

    @app.post("/v1/graph-risk", dependencies=[Depends(authenticate)])
    def graph_risk(req: GraphRiskRequest):
        evidence = engine.query_risk(vpa_hash=req.vpa_hash)
        return app.state.signer.issue(evidence, req.vpa_hash, str(req.session_id), audience)

    @app.get("/v1/graph-risk/stats", dependencies=[Depends(authenticate)])
    def stats():
        return engine.get_stats()

    @app.get("/v1/signing-key", dependencies=[Depends(authenticate)])
    def public_key():
        # Demo setup only. Production clients pin keys through a trusted release channel.
        if not demo:
            raise HTTPException(404)
        return {"key_id": app.state.signer.key_id, "algorithm": "RS256",
                "public_key_der": app.state.signer.public_key_der()}

    @app.post("/v1/telemetry", dependencies=[Depends(authenticate)])
    def telemetry():
        raise HTTPException(501, "Telemetry collection disabled until an approved audit sink is configured")

    @app.get("/health")
    def health():
        return {"status": "alive", "demo": demo}

    @app.get("/ready")
    def ready():
        stats = engine.get_stats()
        if not stats["loaded"] or stats["snapshot_age_s"] >= engine.max_snapshot_age:
            raise HTTPException(503, "Graph evidence unavailable")
        return {"status": "ready", "demo": demo}
    return app


app = create_app()

if __name__ == "__main__":
    import argparse
    import uvicorn
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    uvicorn.run(app, host="127.0.0.1", port=args.port)
