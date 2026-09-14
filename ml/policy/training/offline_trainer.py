"""Backward-compatible entry point to the real MoE-integrated offline trainer."""
from ml.policy.training.train_policy import train_frozen_policy

if __name__ == "__main__":
    train_frozen_policy()
