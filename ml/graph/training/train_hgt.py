"""
Vyuha 2.0 — HGT Training Pipeline
=====================================
Trains the Heterogeneous Graph Transformer on synthetic fraud data.

Features:
    - Focal Loss for class imbalance (~97% legit, ~3% fraud)
    - AdamW optimizer with cosine annealing LR
    - Early stopping on validation PR-AUC
    - Checkpoint saving (best model by val PR-AUC)

Architecture ref: ADR-003 §Evaluation.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from sklearn.metrics import (
    roc_auc_score, average_precision_score, precision_score,
    recall_score, f1_score
)

# Add project root to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.graph.model.hgt import HGTModel
from ml.graph.data.loader import load_hetero_data

# ── Constants ──────────────────────────────────────────────────────
CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../checkpoints"))
os.makedirs(CHECKPOINT_DIR, exist_ok=True)


class FocalLoss(nn.Module):
    """
    Focal Loss for handling severe class imbalance.
    FL(p_t) = -alpha_t * (1 - p_t)^gamma * log(p_t)

    With ~3% fraud rate, standard BCE would be dominated by the majority class.
    Focal loss down-weights well-classified examples and focuses on hard ones.
    """

    def __init__(self, alpha: float = 0.75, gamma: float = 2.0):
        super().__init__()
        self.alpha = alpha
        self.gamma = gamma

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        bce = nn.functional.binary_cross_entropy_with_logits(logits, targets, reduction='none')
        p_t = torch.exp(-bce)
        alpha_t = self.alpha * targets + (1 - self.alpha) * (1 - targets)
        focal_weight = alpha_t * (1 - p_t) ** self.gamma
        return (focal_weight * bce).mean()


def evaluate_model(model, data, device):
    """Evaluate model on a data split. Returns metrics dict."""
    model.eval()
    with torch.no_grad():
        node_features = {k: v.to(device) for k, v in data["node_features"].items()}
        edge_index = {k: v.to(device) for k, v in data["edge_index"].items()}
        edge_attr = {k: v.to(device) for k, v in data["edge_attr"].items()}
        labels = data["labels"].to(device)
        mask = data["label_mask"].to(device)

        out = model(node_features, edge_index, edge_attr)
        logits = out["risk_logits"][mask]
        scores = out["risk_scores"][mask]
        targets = labels[mask]

        # Move to CPU for sklearn
        scores_np = scores.cpu().numpy()
        targets_np = targets.cpu().numpy()

        if len(np.unique(targets_np)) < 2:
            return {"roc_auc": 0.0, "pr_auc": 0.0, "precision": 0.0,
                    "recall": 0.0, "f1": 0.0, "loss": 0.0}

        preds = (scores_np > 0.5).astype(float)

        metrics = {
            "roc_auc": roc_auc_score(targets_np, scores_np),
            "pr_auc": average_precision_score(targets_np, scores_np),
            "precision": precision_score(targets_np, preds, zero_division=0),
            "recall": recall_score(targets_np, preds, zero_division=0),
            "f1": f1_score(targets_np, preds, zero_division=0),
        }

        # Loss
        loss_fn = FocalLoss()
        metrics["loss"] = loss_fn(logits, targets).item()

    return metrics


def train(epochs=50, lr=1e-3, hidden_dim=64, num_heads=4, num_layers=2,
          patience=10, device_str="cpu", quick_test=False):
    """Full training pipeline."""
    device = torch.device(device_str)

    print("=" * 60)
    print("Vyuha 2.0 — HGT Training Pipeline")
    print("=" * 60)

    if quick_test:
        epochs = min(epochs, 5)
        print(f"[QUICK TEST MODE] Running {epochs} epochs only.")

    # ── Load data ────────────────────────────────────────────────
    print("\nLoading data...")
    train_data = load_hetero_data("train")
    val_data = load_hetero_data("val")

    feature_dims = train_data["metadata"]["feature_dims"]

    # ── Initialize model ─────────────────────────────────────────
    model = HGTModel(
        feature_dims=feature_dims,
        hidden_dim=hidden_dim,
        num_heads=num_heads,
        num_layers=num_layers,
    ).to(device)

    num_params = sum(p.numel() for p in model.parameters())
    print(f"\nModel: HGT ({num_layers} layers, {hidden_dim} hidden, {num_heads} heads)")
    print(f"Parameters: {num_params:,}")

    # ── Training setup ───────────────────────────────────────────
    loss_fn = FocalLoss(alpha=0.75, gamma=2.0)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    # Move training data to device
    train_features = {k: v.to(device) for k, v in train_data["node_features"].items()}
    train_edges = {k: v.to(device) for k, v in train_data["edge_index"].items()}
    train_edge_attr = {k: v.to(device) for k, v in train_data["edge_attr"].items()}
    train_labels = train_data["labels"].to(device)
    train_mask = train_data["label_mask"].to(device)

    best_val_pr_auc = 0.0
    best_epoch = 0
    patience_counter = 0

    print(f"\nTraining for {epochs} epochs (patience={patience})...\n")

    for epoch in range(1, epochs + 1):
        t0 = time.time()
        model.train()

        # Forward
        out = model(train_features, train_edges, train_edge_attr)
        logits = out["risk_logits"][train_mask]
        targets = train_labels[train_mask]

        loss = loss_fn(logits, targets)

        # Backward
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        scheduler.step()

        t1 = time.time()

        # Validate every 5 epochs (or every epoch for quick test)
        if epoch % 5 == 0 or epoch == 1 or quick_test:
            val_metrics = evaluate_model(model, val_data, device)

            print(f"Epoch {epoch:3d}/{epochs} | "
                  f"Loss: {loss.item():.4f} | "
                  f"Val PR-AUC: {val_metrics['pr_auc']:.4f} | "
                  f"Val ROC-AUC: {val_metrics['roc_auc']:.4f} | "
                  f"F1: {val_metrics['f1']:.4f} | "
                  f"LR: {scheduler.get_last_lr()[0]:.6f} | "
                  f"Time: {t1 - t0:.2f}s")

            # Early stopping check
            if val_metrics["pr_auc"] > best_val_pr_auc:
                best_val_pr_auc = val_metrics["pr_auc"]
                best_epoch = epoch
                patience_counter = 0

                # Save checkpoint
                ckpt_path = os.path.join(CHECKPOINT_DIR, "hgt_best.pt")
                torch.save({
                    "epoch": epoch,
                    "model_state_dict": model.state_dict(),
                    "optimizer_state_dict": optimizer.state_dict(),
                    "val_pr_auc": best_val_pr_auc,
                    "val_metrics": val_metrics,
                    "config": {
                        "hidden_dim": hidden_dim,
                        "num_heads": num_heads,
                        "num_layers": num_layers,
                        "feature_dims": feature_dims,
                    }
                }, ckpt_path)
                print(f"  -> Saved best checkpoint (PR-AUC: {best_val_pr_auc:.4f})")
            else:
                patience_counter += 1
                if patience_counter >= patience and not quick_test:
                    print(f"\nEarly stopping at epoch {epoch} (no improvement for {patience} evals)")
                    break

    print(f"\n{'=' * 60}")
    print(f"Training complete. Best Val PR-AUC: {best_val_pr_auc:.4f} at epoch {best_epoch}")
    print(f"Checkpoint saved to: {os.path.join(CHECKPOINT_DIR, 'hgt_best.pt')}")
    print(f"{'=' * 60}")

    return model, best_val_pr_auc


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train HGT model")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--hidden-dim", type=int, default=64)
    parser.add_argument("--num-heads", type=int, default=4)
    parser.add_argument("--num-layers", type=int, default=2)
    parser.add_argument("--patience", type=int, default=10)
    parser.add_argument("--device", type=str, default="cpu")
    parser.add_argument("--quick-test", action="store_true")
    args = parser.parse_args()

    train(
        epochs=args.epochs,
        lr=args.lr,
        hidden_dim=args.hidden_dim,
        num_heads=args.num_heads,
        num_layers=args.num_layers,
        patience=args.patience,
        device_str=args.device,
        quick_test=args.quick_test,
    )
