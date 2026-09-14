"""Cross-language oracle vectors generated from PyTorch and Python policy."""
import json
import torch
from ml.policy.runtime import ROOT, PaymentRiskEngine, PaymentContext

torch.manual_seed(730)
engine = PaymentRiskEngine(policy_path=ROOT / "checkpoints/frozen_policy.json")
vectors = []
for i in range(128):
    amount = [.1, .3, .6, 1.0][i % 4]
    context = PaymentContext(i % 2 == 0, i % 3 == 0, amount, torch.rand(1).item(), torch.rand(1).item(), i % 2 == 0)
    c = [None, .05, .45, .92][i % 4]
    result = engine.evaluate(context, c)
    vectors.append({"features": context.pack()[0].tolist(), "counterparty": c,
                    "online_purchase": context.online_purchase, **result})
root = ROOT / "packages/android-sdk/src/test/resources"
root.mkdir(parents=True, exist_ok=True)
(root / "parity_vectors.json").write_text(json.dumps(vectors))
from ml.graph.inference.tokens import TokenSigner
signer = TokenSigner(key_id="fixture")
token = signer.issue({"risk_score": None, "confidence": 0.0, "risk_class": "UNKNOWN",
    "reason_codes": ["UNKNOWN_RECEIVER"], "model_version": "hgt-test", "graph_as_of": 123,
    "data_kind": "synthetic"}, "a" * 64, "11111111-1111-4111-8111-111111111111", "bank", now=1000)
(root / "token_fixture.json").write_text(json.dumps({"token": token, "public_key": signer.public_key_der()}))
