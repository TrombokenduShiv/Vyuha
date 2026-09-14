"""Executable dual-integrity scenarios plus held-out agency quality."""
import argparse
import json
import torch
from ml.policy.runtime import ROOT, PaymentContext, PaymentRiskEngine
from ml.edge.training.train_moe import generate_synthetic_data
from ml.graph.evaluation.evaluate import metrics


def evaluate(n_samples=2000):
    torch.set_num_threads(2)
    engine = PaymentRiskEngine(policy_path=ROOT / "checkpoints/frozen_policy.json")
    cases = [
        ("Known normal merchant", PaymentContext(), .05),
        ("Calm social-commerce buyer, risky receiver", PaymentContext(amount=.3, novelty=.95, online_purchase=True), .92),
        ("New online seller, no graph history", PaymentContext(amount=.3, novelty=.95, online_purchase=True), None),
        ("Digital arrest with suspicious receiver", PaymentContext(True, True, 1, .95, .9), .92),
        ("Coercion with low-risk receiver", PaymentContext(True, True, 1, .95, .9), .05),
        ("Benign call to known contact", PaymentContext(True, False, .1, .1, .1), .05),
        ("Offline first receiver", PaymentContext(novelty=.95), None),
    ]
    from dataclasses import asdict
    results = [{"case": name, "context": asdict(context), **engine.evaluate(context, c)} for name, context, c in cases]
    assert results[1]["action_id"] != "A0_PASS" and results[1]["action_id"] != "A4_ISOLATION_BREAK"
    assert results[2]["template_id"] == "MERCHANT_VERIFICATION"
    x, y = generate_synthetic_data(n_samples, seed=8675309)
    with torch.inference_mode():
        p = engine.moe(x)["risk_logits"].sigmoid().numpy()
    report = {"data_kind": "synthetic", "scenarios": results, "agency_metrics": metrics(y.numpy(), p),
              "agency_conformal": engine.conformal.evaluate_coverage(p, y.numpy()),
              "limitation": "Graph risk is supplied scenario evidence here; real graph/API integration tested separately"}
    (ROOT / "datasets/synthetic/E2E_REPORT.json").write_text(json.dumps(report, indent=2))
    dashboard = ROOT / "apps/judge-dashboard/src/data"
    dashboard.mkdir(exist_ok=True)
    (dashboard / "evaluation.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--n-samples", type=int, default=2000)
    evaluate(parser.parse_args().n_samples)
