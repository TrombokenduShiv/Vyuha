"""
Vyuha 2.0 — Graph Model Evaluation Harness
=============================================
Evaluates the trained HGT model against the 8 metrics
required by ADR-003 and updates MVP_REPORT.json.

Metrics:
    1. ROC-AUC
    2. PR-AUC (primary metric for imbalanced data)
    3. Precision @ 0.5 threshold
    4. Recall @ 0.5 threshold
    5. F1 Score
    6. Expected Calibration Error (ECE)
    7. False Positive Rate @ various thresholds
    8. Inference latency (per-sample, ms)

Architecture ref: ADR-003 §Evaluation.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch
from sklearn.metrics import (
    roc_auc_score, average_precision_score, precision_score,
    recall_score, f1_score, confusion_matrix, precision_recall_curve,
    roc_curve
)

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.graph.model.hgt import HGTModel
from ml.graph.data.loader import load_hetero_data

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../checkpoints"))
DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../datasets/synthetic"))


def compute_ece(y_true: np.ndarray, y_prob: np.ndarray, n_bins: int = 10) -> float:
    """
    Expected Calibration Error.
    Measures how well the predicted probabilities match actual frequencies.
    """
    bin_boundaries = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    for i in range(n_bins):
        mask = (y_prob >= bin_boundaries[i]) & (y_prob < bin_boundaries[i + 1])
        if mask.sum() == 0:
            continue
        bin_acc = y_true[mask].mean()
        bin_conf = y_prob[mask].mean()
        ece += mask.sum() * abs(bin_acc - bin_conf)
    return ece / len(y_true)


def evaluate(checkpoint_path: str = None, split: str = "test", device_str: str = "cpu"):
    """Full evaluation pipeline."""
    device = torch.device(device_str)

    print("=" * 60)
    print("Vyuha 2.0 — Graph Model Evaluation Harness")
    print("=" * 60)

    # ── Load checkpoint ──────────────────────────────────────────
    if checkpoint_path is None:
        checkpoint_path = os.path.join(CHECKPOINT_DIR, "hgt_best.pt")

    if not os.path.exists(checkpoint_path):
        print(f"\n[FAIL] No checkpoint found at {checkpoint_path}")
        print("  Run 'python -m ml.graph.training.train_hgt' first.")
        _generate_empty_report()
        return

    ckpt = torch.load(checkpoint_path, map_location=device, weights_only=False)
    config = ckpt["config"]

    print(f"\nLoaded checkpoint from epoch {ckpt['epoch']}")
    print(f"  Val PR-AUC at save: {ckpt['val_pr_auc']:.4f}")

    # ── Load model ───────────────────────────────────────────────
    model = HGTModel(
        feature_dims=config["feature_dims"],
        hidden_dim=config["hidden_dim"],
        num_heads=config["num_heads"],
        num_layers=config["num_layers"],
    ).to(device)
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    # ── Load test data ───────────────────────────────────────────
    print(f"\nLoading {split} data...")
    test_data = load_hetero_data(split)

    node_features = {k: v.to(device) for k, v in test_data["node_features"].items()}
    edge_index = {k: v.to(device) for k, v in test_data["edge_index"].items()}
    edge_attr = {k: v.to(device) for k, v in test_data["edge_attr"].items()}
    labels = test_data["labels"]
    mask = test_data["label_mask"]

    # ── Inference + Latency ──────────────────────────────────────
    print("\nRunning inference...")

    # Warmup
    with torch.no_grad():
        _ = model(node_features, edge_index, edge_attr)

    # Timed inference
    latencies = []
    for _ in range(10):
        t0 = time.perf_counter()
        with torch.no_grad():
            out = model(node_features, edge_index, edge_attr)
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    scores = out["risk_scores"][mask].cpu().numpy()
    confidence = out["confidence"][mask].cpu().numpy()
    targets = labels[mask].numpy()

    num_samples = mask.sum().item()
    avg_latency = np.mean(latencies)
    per_sample_latency = avg_latency / num_samples

    # ── Compute all metrics ──────────────────────────────────────
    print("\nComputing metrics...")

    if len(np.unique(targets)) < 2:
        print("  WARNING: Only one class in test set. Metrics may be unreliable.")
        metrics = {
            "AUROC": 0.0, "AUPRC": 0.0, "precision": 0.0,
            "recall": 0.0, "F1": 0.0, "ECE": 0.0,
            "FPR_at_50pct_recall": 0.0,
            "inference_latency_ms_per_batch": round(avg_latency, 3),
            "inference_latency_ms_per_sample": round(per_sample_latency, 6),
        }
    else:
        preds = (scores > 0.5).astype(float)
        tn, fp, fn, tp = confusion_matrix(targets, preds).ravel()

        # FPR at various recall thresholds
        precisions, recalls, thresholds = precision_recall_curve(targets, scores)
        fpr_at_50 = 0.0
        for i, r in enumerate(recalls):
            if r >= 0.50 and i < len(thresholds):
                thresh = thresholds[i]
                fp_count = ((scores >= thresh) & (targets == 0)).sum()
                tn_count = ((scores < thresh) & (targets == 0)).sum()
                fpr_at_50 = fp_count / max(1, fp_count + tn_count)
                break

        metrics = {
            "AUROC": round(roc_auc_score(targets, scores), 4),
            "AUPRC": round(average_precision_score(targets, scores), 4),
            "precision": round(precision_score(targets, preds, zero_division=0), 4),
            "recall": round(recall_score(targets, preds, zero_division=0), 4),
            "F1": round(f1_score(targets, preds, zero_division=0), 4),
            "ECE": round(compute_ece(targets, scores), 4),
            "FPR_at_50pct_recall": round(fpr_at_50, 4),
            "inference_latency_ms_per_batch": round(avg_latency, 3),
            "inference_latency_ms_per_sample": round(per_sample_latency, 6),
            "confusion_matrix": {"TP": int(tp), "FP": int(fp), "FN": int(fn), "TN": int(tn)},
            "mean_confidence": round(float(confidence.mean()), 4),
            "num_test_samples": num_samples,
            "fraud_rate": round(float(targets.mean()), 4),
        }

    # ── Print report ─────────────────────────────────────────────
    print(f"\n{'=' * 60}")
    print("VYUHA 2.0 — GRAPH MODEL EVALUATION REPORT")
    print(f"{'=' * 60}")
    for k, v in metrics.items():
        if isinstance(v, dict):
            print(f"  {k}: {v}")
        else:
            print(f"  {k:.<40s}: {v}")
    print(f"{'=' * 60}")

    # ── Save report ──────────────────────────────────────────────
    report = {
        "model": "HGT (Heterogeneous Graph Transformer)",
        "status": "Trained and Evaluated",
        "checkpoint": checkpoint_path,
        "split": split,
        "config": config,
        "metrics": metrics,
    }

    report_path = os.path.join(DATA_DIR, "MVP_REPORT.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=4)
    print(f"\nReport saved to: {report_path}")

    return metrics


def _generate_empty_report():
    """Generate report showing model needs training."""
    report = {
        "model": "HGT (Heterogeneous Graph Transformer)",
        "status": "NOT TRAINED — run train_hgt.py first",
        "metrics": {
            "AUROC": "UNMEASURED",
            "AUPRC": "UNMEASURED",
            "precision": "UNMEASURED",
            "recall": "UNMEASURED",
            "F1": "UNMEASURED",
            "ECE": "UNMEASURED",
            "inference_latency_ms": "UNMEASURED",
        }
    }
    report_path = os.path.join(DATA_DIR, "MVP_REPORT.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=4)
    print(f"Empty report saved to: {report_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Evaluate HGT model")
    parser.add_argument("--checkpoint", type=str, default=None)
    parser.add_argument("--split", type=str, default="test")
    parser.add_argument("--device", type=str, default="cpu")
    args = parser.parse_args()

    evaluate(
        checkpoint_path=args.checkpoint,
        split=args.split,
        device_str=args.device,
    )
