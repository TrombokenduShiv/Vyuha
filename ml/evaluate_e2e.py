"""
Vyuha 2.0 — End-to-End ML Pipeline Evaluation
=================================================
Runs the full ML pipeline:
    Data -> HGT (graph risk) -> Edge MoE (local inference)
    -> Belief Updater -> Conformal Predictor -> Policy Bandit -> Decision

Reports:
    - End-to-end accuracy
    - Per-stage latency
    - Intervention action distribution
    - False alarm rate (A1+ on legitimate transactions)
    - Miss rate (A0 on fraud transactions)
    - Conformal coverage

Architecture ref: ARCHITECTURE_FREEZE_V1 §7 — Full ML Inference Path.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch
from collections import Counter

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ml.edge.experts.moe import EdgeRiskMoE
from ml.edge.belief.belief_state import CoercionBeliefState
from ml.edge.conformal.conformal_predictor import ConformalPredictor, UncertaintyLabel
from ml.edge.training.train_moe import generate_synthetic_data

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../checkpoints"))
REPORT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../datasets/synthetic"))


def policy_bandit_decide(p_coercion: float, uncertainty_set: list) -> str:
    """
    Simplified Python version of the Kotlin PolicyBandit.
    Maps belief + uncertainty -> action (A0-A6).
    """
    uncertain = any(l == UncertaintyLabel.ABSTAIN for l in uncertainty_set)

    if p_coercion < 0.15 and not uncertain:
        return "A0_PASS"
    elif p_coercion < 0.30:
        return "A0_PASS"
    elif p_coercion < 0.50:
        return "A2_REFLECTION_CHALLENGE"
    elif p_coercion < 0.65:
        return "A3_COOLING_DELAY"
    elif p_coercion < 0.85:
        return "A4_ISOLATION_BREAK"
    elif p_coercion < 0.95:
        return "A5_TRUSTED_VERIFY"
    else:
        return "A6_STEP_UP_REQUIRED"


def run_e2e_evaluation(n_samples: int = 2000, device_str: str = "cpu"):
    """Run the full end-to-end evaluation."""
    device = torch.device(device_str)

    print("=" * 60)
    print("Vyuha 2.0 — End-to-End ML Pipeline Evaluation")
    print("=" * 60)

    # ── Load MoE model ───────────────────────────────────────────
    moe_ckpt_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")
    if os.path.exists(moe_ckpt_path):
        ckpt = torch.load(moe_ckpt_path, map_location=device, weights_only=False)
        moe = EdgeRiskMoE(**ckpt["config"]).to(device)
        moe.load_state_dict(ckpt["model_state_dict"])
        print("  [OK] Loaded trained MoE checkpoint")
    else:
        moe = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2).to(device)
        print("  [WARN] No MoE checkpoint found, using untrained model")

    moe.eval()

    # ── Calibrate conformal predictor ────────────────────────────
    print("\nCalibrating conformal predictor...")
    cal_X, cal_y = generate_synthetic_data(n_samples=1000, fraud_rate=0.15, seed=999)
    with torch.no_grad():
        cal_out = moe(cal_X.to(device))
        cal_scores = torch.sigmoid(cal_out["risk_logits"]).cpu().numpy()

    conformal = ConformalPredictor(alpha=0.10)
    conformal.calibrate(cal_scores, cal_y.numpy())

    # ── Generate test data ───────────────────────────────────────
    print(f"\nGenerating {n_samples} test scenarios...")
    test_X, test_y = generate_synthetic_data(n_samples, fraud_rate=0.15, seed=777)

    # ── Run full pipeline ────────────────────────────────────────
    print("\nRunning full pipeline (MoE -> Belief -> Conformal -> Policy)...\n")

    actions = []
    latencies = []
    beliefs = []
    correct_interventions = 0
    false_alarms = 0
    misses = 0

    for i in range(n_samples):
        t0 = time.perf_counter()

        x = test_X[i:i + 1].to(device)
        true_label = test_y[i].item()

        # ── Stage 1: MoE inference ──────────────────────────
        with torch.no_grad():
            moe_out = moe(x)
            risk_logit = moe_out["risk_logits"].item()
            risk_score = torch.sigmoid(torch.tensor(risk_logit)).item()
            moe_confidence = moe_out["confidence"].item()

        # ── Stage 2: Belief update ──────────────────────────
        belief = CoercionBeliefState(initial_belief=0.11)

        # Simulate observations from feature vector
        comm_active = test_X[i, 0].item()
        capture_risk = test_X[i, 1].item()
        graph_risk = test_X[i, 4].item()

        if comm_active > 0.5:
            belief.update(0.25, 0.0, 0.0, "active_communication")
        if capture_risk > 0.5:
            belief.update(0.30, 0.0, 0.0, "screen_capture_risk")
        if graph_risk > 0.5:
            belief.update(0.0, graph_risk * 0.5, 0.0, "graph_topology_risk")

        # Incorporate MoE risk score
        belief.update(risk_score * 0.3, 0.0, 0.0, "moe_risk_assessment")

        p_coercion = belief.posterior_coercion_probability
        beliefs.append(p_coercion)

        # ── Stage 3: Conformal prediction ───────────────────
        uncertainty_set = conformal.predict_set(p_coercion)

        # ── Stage 4: Policy decision ────────────────────────
        action = policy_bandit_decide(p_coercion, uncertainty_set)

        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)  # ms

        actions.append(action)

        # ── Score the decision ──────────────────────────────
        is_intervention = action != "A0_PASS"

        if true_label == 1 and is_intervention:
            correct_interventions += 1
        elif true_label == 0 and is_intervention:
            false_alarms += 1
        elif true_label == 1 and not is_intervention:
            misses += 1

    # ── Compute metrics ──────────────────────────────────────────
    n_fraud = int(test_y.sum().item())
    n_legit = n_samples - n_fraud

    action_dist = Counter(actions)
    conformal_coverage = conformal.evaluate_coverage(
        np.array(beliefs), test_y.numpy()
    )

    latency_arr = np.array(latencies)

    metrics = {
        "n_samples": n_samples,
        "n_fraud": n_fraud,
        "n_legit": n_legit,
        "correct_interventions": correct_interventions,
        "detection_rate": round(correct_interventions / max(1, n_fraud), 4),
        "false_alarm_rate": round(false_alarms / max(1, n_legit), 4),
        "miss_rate": round(misses / max(1, n_fraud), 4),
        "action_distribution": {k: v for k, v in sorted(action_dist.items())},
        "latency_ms": {
            "mean": round(latency_arr.mean(), 3),
            "p50": round(np.percentile(latency_arr, 50), 3),
            "p95": round(np.percentile(latency_arr, 95), 3),
            "p99": round(np.percentile(latency_arr, 99), 3),
            "max": round(latency_arr.max(), 3),
        },
        "conformal_coverage": conformal_coverage,
        "mean_belief_fraud": round(float(np.mean([b for b, y in zip(beliefs, test_y.numpy()) if y == 1])), 4),
        "mean_belief_legit": round(float(np.mean([b for b, y in zip(beliefs, test_y.numpy()) if y == 0])), 4),
    }

    # ── Print report ─────────────────────────────────────────────
    print("=" * 60)
    print("END-TO-END EVALUATION REPORT")
    print("=" * 60)
    print(f"\n  Detection Rate:    {metrics['detection_rate']:.1%} ({correct_interventions}/{n_fraud} fraud caught)")
    print(f"  False Alarm Rate:  {metrics['false_alarm_rate']:.1%} ({false_alarms}/{n_legit} legit blocked)")
    print(f"  Miss Rate:         {metrics['miss_rate']:.1%} ({misses}/{n_fraud} fraud missed)")
    print(f"\n  Mean Belief (Fraud):  {metrics['mean_belief_fraud']:.3f}")
    print(f"  Mean Belief (Legit):  {metrics['mean_belief_legit']:.3f}")
    print(f"\n  Action Distribution:")
    for action, count in sorted(action_dist.items()):
        pct = count / n_samples * 100
        print(f"    {action:.<35s} {count:5d} ({pct:.1f}%)")
    print(f"\n  Latency (per sample):")
    for k, v in metrics["latency_ms"].items():
        print(f"    {k:.<10s} {v:.3f} ms")
    print(f"\n  Conformal Coverage: {conformal_coverage['coverage']:.1%} "
          f"(target: {conformal_coverage['target_coverage']:.0%})")
    print(f"  Avg Set Size:      {conformal_coverage['avg_set_size']:.2f}")
    print("=" * 60)

    # ── Save report ──────────────────────────────────────────────
    report_path = os.path.join(REPORT_DIR, "E2E_REPORT.json")
    with open(report_path, "w") as f:
        json.dump(metrics, f, indent=4, default=str)
    print(f"\nReport saved to: {report_path}")

    return metrics


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run E2E ML pipeline evaluation")
    parser.add_argument("--n-samples", type=int, default=2000)
    parser.add_argument("--device", type=str, default="cpu")
    args = parser.parse_args()

    run_e2e_evaluation(n_samples=args.n_samples, device_str=args.device)
