"""Held-out receiver quality, calibration, fixed-threshold errors and baselines."""
import json
import time
import numpy as np
import torch
from sklearn.metrics import average_precision_score, roc_auc_score, precision_score, recall_score, f1_score, brier_score_loss
from sklearn.linear_model import LogisticRegression
from ml.policy.runtime import ROOT
from ml.graph.data.loader import load_hetero_data
from ml.graph.model.hgt import HGTModel


def compute_ece(y_true, y_prob, n_bins=10):
    total = 0.0
    for i in range(n_bins):
        mask = (y_prob >= i / n_bins) & (y_prob <= 1 if i == n_bins - 1 else y_prob < (i + 1) / n_bins)
        if mask.any():
            total += mask.mean() * abs(y_true[mask].mean() - y_prob[mask].mean())
    return float(total)


def metrics(y, p, threshold=.5):
    pred = p >= threshold
    return {"n": len(y), "fraud_count": int(y.sum()), "AUROC": float(roc_auc_score(y, p)),
            "AUPRC": float(average_precision_score(y, p)), "precision": float(precision_score(y, pred, zero_division=0)),
            "recall": float(recall_score(y, pred, zero_division=0)), "F1": float(f1_score(y, pred, zero_division=0)),
            "FPR": float(pred[y == 0].mean()), "Brier": float(brier_score_loss(y, p)),
            "ECE": compute_ece(y, p), "threshold": float(threshold)}


def evaluate(checkpoint_path=None, split="test", device_str="cpu"):
    torch.set_num_threads(2)
    ckpt = torch.load(checkpoint_path or ROOT / "checkpoints/hgt_best.pt", map_location="cpu", weights_only=True)
    model = HGTModel(**ckpt["config"]).eval()
    model.load_state_dict(ckpt["model_state_dict"])
    train, val, test = (load_hetero_data(s) for s in ("train", "val", split))
    def score(data, edges=True):
        with torch.inference_mode():
            return model(data["node_features"], data["edge_index"] if edges else {},
                         data["edge_attr"] if edges else {})["risk_scores"][data["label_mask"]].numpy()
    val_scores = score(val)
    val_y = val["labels"][val["label_mask"]].numpy()
    threshold = float(np.quantile(val_scores[val_y == 0], .99, method="higher"))
    start = time.perf_counter()
    p = score(test)
    elapsed = (time.perf_counter() - start) * 1000
    y = test["labels"][test["label_mask"]].numpy()
    mask = train["label_mask"]
    baseline = LogisticRegression(class_weight="balanced", random_state=42, max_iter=1000)
    baseline.fit(train["node_features"]["account"][mask].numpy(), train["labels"][mask].numpy())
    base_p = baseline.predict_proba(test["node_features"]["account"][test["label_mask"]].numpy())[:, 1]
    report = {"version": "2.1", "data_kind": "synthetic", "protocol": ckpt["split_protocol"],
              "limitations": ["Synthetic motif labels, not bank-confirmed fraud", "Static relationships assumed known at inception",
                              "Connected nodes across partitions: not an unseen fraud-ring holdout", "No production accuracy claim"],
              "metrics": metrics(y, p), "validation_selected_threshold": metrics(y, p, threshold),
              "feature_only_logistic_baseline": metrics(y, base_p),
              "no_edges_ablation": metrics(y, score(test, edges=False)),
              "full_snapshot_inference_ms": elapsed, "calibration": ckpt["calibration"]}
    (ROOT / "datasets/synthetic/MVP_REPORT.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    evaluate()
