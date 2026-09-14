"""
Vyuha 2.0 — Edge MoE Training Script
=======================================
Trains the EdgeRiskMoE model on synthetic data generated from
the digital twin environment.

Features:
    - Generates training data from digital twin scenarios
    - Binary cross-entropy + load-balance loss (prevents expert collapse)
    - AdamW optimizer
    - Validates on held-out synthetic scenarios
    - Exports trained weights to checkpoint

Architecture ref: ARCHITECTURE_FREEZE_V1 §7 D7.
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

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.edge.experts.moe import EdgeRiskMoE
from ml.artifacts import save_checkpoint

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../checkpoints"))
os.makedirs(CHECKPOINT_DIR, exist_ok=True)


def generate_synthetic_data(n_samples: int = 10000, fraud_rate: float = 0.15, seed: int = 42):
    """
    Generate synthetic feature vectors and labels for MoE training.

    Feature vector (6-dim, normalized [0, 1]):
        [0] communication_active (0 or 1)
        [1] capture_risk (0 or 1)
        [2] amount_bucket_norm (0.0-1.0: LOW=0.1, MED=0.3, HIGH=0.6, CRIT=1.0)
        [3] beneficiary_novelty (0.0-1.0)
        [4] reserved (ignored; counterparty is independently evaluated)
        [5] deviation_score (0.0-1.0)

    Labels:
        0 = no coercion, 1 = coercion (not general payment fraud)
    """
    np.random.seed(seed)

    features = np.zeros((n_samples, 6), dtype=np.float32)
    labels = np.zeros(n_samples, dtype=np.float32)

    n_fraud = int(n_samples * fraud_rate)
    n_legit = n_samples - n_fraud

    # ── Generate legitimate samples ──────────────────────────────
    # Low communication, low capture, low/medium amounts, known payees
    features[:n_legit, 0] = (np.random.random(n_legit) < 0.05).astype(float)  # Rarely on call
    features[:n_legit, 1] = (np.random.random(n_legit) < 0.02).astype(float)  # Almost never screen sharing
    features[:n_legit, 2] = np.random.choice([0.1, 0.3, 0.6], n_legit, p=[0.5, 0.35, 0.15])
    features[:n_legit, 3] = np.random.beta(2, 8, n_legit)  # Most payees are known
    features[:n_legit, 4] = np.random.beta(1.5, 10, n_legit)  # Low graph risk
    features[:n_legit, 5] = np.random.beta(2, 8, n_legit)  # Low deviation
    labels[:n_legit] = 0

    # ── Generate fraud/coercion samples ──────────────────────────
    # High communication, often screen shared, high amounts, novel payees
    idx = n_legit
    features[idx:, 0] = (np.random.random(n_fraud) < 0.85).astype(float)  # Usually on call
    features[idx:, 1] = (np.random.random(n_fraud) < 0.60).astype(float)  # Often screen sharing
    features[idx:, 2] = np.random.choice([0.6, 1.0], n_fraud, p=[0.3, 0.7])  # High/Critical amounts
    features[idx:, 3] = np.random.beta(8, 2, n_fraud)  # Novel payees
    features[idx:, 4] = np.random.beta(6, 3, n_fraud)  # Higher graph risk
    features[idx:, 5] = np.random.beta(7, 3, n_fraud)  # Higher deviation
    labels[idx:] = 1

    # Counterparty is independent: calm buyers can pay fraudulent receivers.
    features[:, 4] = np.random.random(n_samples)
    # Include benign calls, accessibility-like device flags, large/new payments.
    hard = np.arange(0, n_legit, 4)
    features[hard, 0] = (np.random.random(len(hard)) < 0.4)
    features[hard, 1] = (np.random.random(len(hard)) < 0.1)
    features[hard, 2:4] = np.random.random((len(hard), 2))
    features[hard, 5] = np.random.random(len(hard))
    # Shuffle
    perm = np.random.permutation(n_samples)
    features = features[perm]
    labels = labels[perm]

    return torch.tensor(features), torch.tensor(labels)


def load_balance_loss(routing_weights: torch.Tensor) -> torch.Tensor:
    """
    Load-balance loss to prevent expert collapse.

    Encourages uniform routing across experts.
    L_lb = num_experts * sum_i(f_i * P_i)
    where f_i = fraction of tokens routed to expert i
          P_i = average routing probability for expert i
    """
    num_experts = routing_weights.shape[1]
    # f_i: fraction of tokens where expert i has highest weight
    top_expert = routing_weights.argmax(dim=-1)
    f = torch.zeros(num_experts, device=routing_weights.device)
    for i in range(num_experts):
        f[i] = (top_expert == i).float().mean()

    # P_i: average routing probability for expert i
    P = routing_weights.mean(dim=0)

    return num_experts * (f * P).sum()


def train(epochs: int = 50, lr: float = 1e-3, batch_size: int = 256,
          n_train: int = 8000, n_val: int = 2000, lb_weight: float = 0.1,
          device_str: str = "cpu"):
    """Full MoE training pipeline."""
    device = torch.device(device_str)
    torch.manual_seed(42)
    torch.set_num_threads(2)

    print("=" * 60)
    print("Vyuha 2.0 — Edge MoE Training Pipeline")
    print("=" * 60)

    # ── Generate data ────────────────────────────────────────────
    print("\nGenerating synthetic training data...")
    train_X, train_y = generate_synthetic_data(n_train, fraud_rate=0.15, seed=42)
    val_X, val_y = generate_synthetic_data(n_val, fraud_rate=0.15, seed=123)

    train_X, train_y = train_X.to(device), train_y.to(device)
    val_X, val_y = val_X.to(device), val_y.to(device)

    print(f"  Train: {n_train} samples ({train_y.sum().item():.0f} fraud)")
    print(f"  Val:   {n_val} samples ({val_y.sum().item():.0f} fraud)")

    # ── Initialize model ─────────────────────────────────────────
    model = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2).to(device)
    num_params = sum(p.numel() for p in model.parameters())
    print(f"\nModel: EdgeRiskMoE (5 experts, top-k=2)")
    print(f"Parameters: {num_params:,}")

    # ── Training setup ───────────────────────────────────────────
    bce_loss = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    best_val_loss = float("inf")
    best_epoch = 0

    print(f"\nTraining for {epochs} epochs...\n")

    for epoch in range(1, epochs + 1):
        model.train()
        epoch_loss = 0.0
        n_batches = 0

        # Mini-batch training
        perm = torch.randperm(n_train, device=device)
        for i in range(0, n_train, batch_size):
            batch_idx = perm[i:i + batch_size]
            X_batch = train_X[batch_idx]
            y_batch = train_y[batch_idx]

            out = model(X_batch)
            risk_logits = out["risk_logits"]
            routing_weights = out["expert_routing_weights"]

            # BCE + load-balance
            loss_bce = bce_loss(risk_logits, y_batch)
            loss_lb = load_balance_loss(routing_weights)
            loss = loss_bce + lb_weight * loss_lb

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            epoch_loss += loss.item()
            n_batches += 1

        scheduler.step()
        avg_loss = epoch_loss / n_batches

        # Validate every 5 epochs
        if epoch % 5 == 0 or epoch == 1:
            model.eval()
            with torch.no_grad():
                val_out = model(val_X)
                val_loss = bce_loss(val_out["risk_logits"], val_y).item()
                val_preds = (torch.sigmoid(val_out["risk_logits"]) > 0.5).float()
                val_acc = (val_preds == val_y).float().mean().item()

                # Routing diversity
                routing = val_out["expert_routing_weights"]
                top_expert = routing.argmax(dim=-1)
                expert_usage = [(top_expert == i).float().mean().item() for i in range(5)]

            print(f"Epoch {epoch:3d}/{epochs} | "
                  f"Train Loss: {avg_loss:.4f} | "
                  f"Val Loss: {val_loss:.4f} | "
                  f"Val Acc: {val_acc:.3f} | "
                  f"Expert Usage: [{', '.join(f'{u:.2f}' for u in expert_usage)}]")

            if val_loss < best_val_loss:
                best_val_loss = val_loss
                best_epoch = epoch
                ckpt_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")
                save_checkpoint({
                    "schema_version": 2,
                    "objective": "agency_only",
                    "data_kind": "synthetic",
                    "epoch": epoch,
                    "model_state_dict": model.state_dict(),
                    "val_loss": val_loss,
                    "val_acc": val_acc,
                    "config": {
                        "input_dim": 6, "hidden_dim": 16,
                        "num_experts": 5, "top_k": 2,
                    }
                }, ckpt_path)

    print(f"\n{'=' * 60}")
    print(f"Training complete. Best Val Loss: {best_val_loss:.4f} at epoch {best_epoch}")
    print(f"Checkpoint saved to: {os.path.join(CHECKPOINT_DIR, 'moe_best.pt')}")
    print(f"{'=' * 60}")

    return model


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train Edge MoE model")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--device", type=str, default="cpu")
    args = parser.parse_args()

    train(epochs=args.epochs, lr=args.lr, batch_size=args.batch_size, device_str=args.device)
