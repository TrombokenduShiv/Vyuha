"""Read-only rehearsal using trained models; optional real localhost demo API.

Run from the repository root: python -m scripts.judge_demo
For live graph evidence: python -m scripts.judge_demo --api-url http://127.0.0.1:8080
No payments, model training or artifact replacement occur here.
"""
import argparse
import base64
import json
from urllib.parse import urlparse
from uuid import uuid4

import httpx
import torch
from cryptography.hazmat.primitives import serialization

from ml.policy.runtime import ROOT, PaymentContext, PaymentRiskEngine
from ml.graph.inference.serve import GraphInferenceEngine
from ml.graph.inference.tokens import verify_token


def show(name, result):
    receiver = result["counterparty_risk"]
    receiver_text = "UNKNOWN" if receiver is None else f"{receiver:.6f}"
    print(f"\n{name}")
    print(f"  Agency: {result['agency_risk']:.6f} | Receiver: {receiver_text}")
    print(f"  Action: {result['action_id']} | Message: {result['template_id']}")
    print(f"  Uncertain: {result['uncertain']} | Reasons: {', '.join(result['reason_codes'])}")


def run(api_url=None):
    torch.set_num_threads(2)
    engine = PaymentRiskEngine(policy_path=ROOT / "checkpoints/frozen_policy.json")
    print("VYUHA JUDGE REHEARSAL - SYNTHETIC DATA; NO REAL PAYMENTS", flush=True)
    print(f"Loaded agency model: {engine.checksum}")
    if api_url is None:
        report = json.loads((ROOT / "datasets/synthetic/E2E_REPORT.json").read_text())
        print("Live local MoE + policy; receiver values are supplied scenario inputs.")
        print("This mode makes no graph API requests.")
        for case in report["scenarios"]:
            result = engine.evaluate(PaymentContext(**case["context"]), case["counterparty_risk"])
            show(case["case"], result)
            if result["action_id"] != case["action_id"] or abs(result["agency_risk"] - case["agency_risk"]) > 1e-5:
                raise RuntimeError("Saved dashboard evidence differs from current model; rehearse a consistent bundle")
        print("\nPASS: recomputed decisions match the saved scenario report.")
        return

    parsed = urlparse(api_url)
    if (parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"}
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise ValueError("This helper supports only a local HTTP synthetic-demo service")
    with httpx.Client(base_url=api_url, timeout=10, trust_env=False) as client:
        ready = client.get("/ready")
        ready.raise_for_status()
        if ready.json().get("demo") is not True:
            raise RuntimeError("Use a separate service with VYUHA_DEMO_MODE=1")
        stats = client.get("/v1/graph-risk/stats")
        stats.raise_for_status()
        print("Live API:", json.dumps(stats.json()), flush=True)
        key_response = client.get("/v1/signing-key")
        key_response.raise_for_status()
        key = serialization.load_der_public_key(base64.b64decode(key_response.json()["public_key_der"]))
        print("Demo verification key fetched from localhost; production requires independently trusted pinned keys.")

        # Select illustrative extremes from actual model predictions, not assigned scores.
        # This is a developer demonstration, not random sampling or an accuracy test.
        print("Selecting low/high examples from the local trained HGT snapshot...", flush=True)
        graph = GraphInferenceEngine(allow_synthetic=True)
        graph.load()
        if graph.get_stats()["model_version"] != stats.json()["model_version"]:
            raise RuntimeError("API and local checkpoint differ; restart the API with the rehearsed bundle")
        candidates = [(score, vpa) for vpa, account in graph._snapshot.by_vpa.items()
                      if graph._snapshot.account_stats[account]["activity_count"] >= 3
                      for score in [graph._snapshot.scores[account]]]
        if not candidates:
            raise RuntimeError("No recipients have enough evidence")
        low, high = min(candidates), max(candidates)
        if low[0] >= .3 or high[0] < .7:
            raise RuntimeError("Current checkpoint does not supply both illustrative risk classes")
        missing = "f" * 64
        if missing in graph._snapshot.by_vpa:
            raise RuntimeError("Unknown-receiver fixture unexpectedly exists")
        contexts = [
            ("Calm payment, low HGT receiver risk", low[1], PaymentContext()),
            ("Calm online buyer, high HGT receiver risk", high[1], PaymentContext(amount=.3, novelty=.95, online_purchase=True)),
            ("New online seller, missing HGT history", missing, PaymentContext(amount=.3, novelty=.95, online_purchase=True)),
            ("Active coercion, low HGT receiver risk", low[1], PaymentContext(True, True, 1, .95, .9)),
        ]
        for name, vpa, context in contexts:
            sid = str(uuid4())
            response = client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid})
            response.raise_for_status()
            token = response.json()
            verified = verify_token(token, key, vpa, sid, "vyuha-demo")
            expected = graph.query_risk(vpa_hash=vpa)["risk_score"]
            if verified["risk_score"] != expected:
                raise RuntimeError("API and local graph data differ; use the same rehearsal snapshot")
            show(name, engine.evaluate(context, verified["risk_score"]))
            print("  Verified live API token. Copy this request into Swagger:")
            print("  " + json.dumps({"vpa_hash": vpa, "session_id": sid}))

        changed = {**token, "risk_score": 1.0 - token["risk_score"]}
        for name, bad_token, expected_session in [
            ("Changed score rejected", changed, sid),
            ("Another session rejected", token, str(uuid4())),
        ]:
            try:
                verify_token(bad_token, key, vpa, expected_session, "vyuha-demo")
            except ValueError:
                print(f"\nPASS: {name}")
            else:
                raise RuntimeError(f"Verification unexpectedly accepted: {name}")
        private_field = client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid, "messages": "SYNTHETIC TEST"})
        if private_field.status_code != 422:
            raise RuntimeError("Unexpected request fields were not rejected")
        print("PASS: extra message-content field rejected (HTTP 422)")
        print("\nPASS: real HGT -> HTTP -> verified evidence -> real MoE/policy demonstration completed.")
        print("This Python harness is separate from the replay dashboard and Android host app.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", help="Optional localhost demo API URL; omission runs local scenario recomputation")
    args = parser.parse_args()
    run(args.api_url)
