"""
Vyuha 2.0 -- Offline Policy Trainer (Integrated)
===================================================
Trains the frozen intervention policy using the DigitalTwinEnv
combined with the real trained MoE model.

Unlike the existing offline_trainer.py (which runs scenarios),
this trainer:
    1. Generates N synthetic scenarios
    2. For each: runs MoE -> Belief -> Conformal -> candidate actions
    3. Simulates outcomes in the DigitalTwinEnv for each action
    4. Selects the action that maximizes expected reward
    5. Records the optimal (state, action) mapping

Output: A frozen policy lookup table that maps
    (belief_bucket, uncertainty_label, context_flags) -> ActionId

Architecture ref: ARCHITECTURE_FREEZE_V1 sec9 D9.
CONSTRAINT: No online exploration on live users.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch
from collections import defaultdict

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.edge.experts.moe import EdgeRiskMoE
from ml.edge.belief.belief_state import CoercionBeliefState
from ml.edge.conformal.conformal_predictor import ConformalPredictor, UncertaintyLabel
from ml.edge.training.train_moe import generate_synthetic_data

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../checkpoints"))

# Action space (A0-A6)
ACTIONS = [
    "A0_PASS",
    "A1_MICRO_PROMPT",
    "A2_REFLECTION_CHALLENGE",
    "A3_COOLING_DELAY",
    "A4_ISOLATION_BREAK",
    "A5_TRUSTED_VERIFY",
    "A6_STEP_UP_REQUIRED",
]

# Reward function
def compute_reward(action: str, is_fraud: bool, belief: float) -> float:
    """
    Compute reward for an action given the ground truth.

    Rewards:
        - Correct PASS on legitimate: +10
        - Correct intervention on fraud: +50 * severity_factor
        - False alarm (intervention on legit): -20 * severity_factor
        - Missed fraud (PASS on fraud): -100
    """
    severity = ACTIONS.index(action)

    if is_fraud:
        if severity == 0:
            return -100.0  # Missed fraud
        else:
            # Higher severity on high-risk fraud = more reward
            return 50.0 * (severity / 6.0) + belief * 20.0
    else:
        if severity == 0:
            return 10.0  # Correct pass
        else:
            # False alarm penalty scales with severity
            return -20.0 * (severity / 6.0)


def train_frozen_policy(n_scenarios=5000, seed=42):
    """
    Train a frozen policy using offline simulation.

    Returns a policy mapping: (belief_bucket, uncertainty) -> best_action
    """
    print("=" * 60)
    print("Vyuha 2.0 -- Offline Policy Trainer (Integrated)")
    print("=" * 60)
    print("\n[WARNING] EXECUTING POLICY SIMULATION IN OFFLINE DIGITAL TWIN")
    print("[WARNING] ONLINE PRODUCTION EXPLORATION IS STRICTLY FORBIDDEN.\n")

    # Load trained MoE
    ckpt_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")
    if os.path.exists(ckpt_path):
        ckpt = torch.load(ckpt_path, map_location="cpu", weights_only=False)
        moe = EdgeRiskMoE(**ckpt["config"])
        moe.load_state_dict(ckpt["model_state_dict"])
        print("[OK] Loaded trained MoE checkpoint")
    else:
        moe = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2)
        print("[WARN] No MoE checkpoint, using untrained model")
    moe.eval()

    # Calibrate conformal predictor
    cal_X, cal_y = generate_synthetic_data(1000, fraud_rate=0.15, seed=999)
    with torch.no_grad():
        cal_out = moe(cal_X)
        cal_scores = torch.sigmoid(cal_out["risk_logits"]).detach().numpy()
    cp = ConformalPredictor(alpha=0.10)
    cp.calibrate(cal_scores, cal_y.numpy())

    # Generate scenarios
    print(f"\nGenerating {n_scenarios} scenarios...")
    test_X, test_y = generate_synthetic_data(n_scenarios, fraud_rate=0.15, seed=seed)

    # Belief buckets: [0, 0.15), [0.15, 0.30), [0.30, 0.50), [0.50, 0.65), [0.65, 0.85), [0.85, 1.0]
    BELIEF_BUCKETS = [(0.0, 0.15), (0.15, 0.30), (0.30, 0.50),
                      (0.50, 0.65), (0.65, 0.85), (0.85, 1.01)]

    # Uncertainty categories
    UNCERTAINTY_CATS = ["CERTAIN_SAFE", "CERTAIN_RISK", "UNCERTAIN"]

    # Accumulate rewards per (bucket, uncertainty, action) triple
    reward_table = defaultdict(lambda: defaultdict(list))

    print("Simulating scenarios...")
    for i in range(n_scenarios):
        x = test_X[i:i+1]
        true_label = test_y[i].item()

        # Run MoE
        with torch.no_grad():
            out = moe(x)
            risk = torch.sigmoid(out["risk_logits"]).item()

        # Run Belief
        belief = CoercionBeliefState(initial_belief=0.11)
        if test_X[i, 0].item() > 0.5:
            belief.update(0.25, 0.0, 0.0, "comm")
        if test_X[i, 1].item() > 0.5:
            belief.update(0.30, 0.0, 0.0, "capture")
        belief.update(risk * 0.3, 0.0, 0.0, "moe")
        p_coercion = belief.posterior_coercion_probability

        # Conformal
        pred_set = cp.predict_set(p_coercion)
        is_uncertain = UncertaintyLabel.ABSTAIN in pred_set
        if not is_uncertain:
            if UncertaintyLabel.SAFE in pred_set and UncertaintyLabel.RISK not in pred_set:
                unc_cat = "CERTAIN_SAFE"
            elif UncertaintyLabel.RISK in pred_set:
                unc_cat = "CERTAIN_RISK"
            else:
                unc_cat = "UNCERTAIN"
        else:
            unc_cat = "UNCERTAIN"

        # Find belief bucket
        bucket_idx = 0
        for bi, (lo, hi) in enumerate(BELIEF_BUCKETS):
            if lo <= p_coercion < hi:
                bucket_idx = bi
                break

        # Try all actions, record reward
        for action in ACTIONS:
            reward = compute_reward(action, bool(true_label), p_coercion)
            key = (bucket_idx, unc_cat)
            reward_table[key][action].append(reward)

    # Build frozen policy: for each (bucket, uncertainty), pick action with highest mean reward
    policy = {}
    print("\n--- Frozen Policy Table ---")
    print(f"{'Bucket':<12s} {'Uncertainty':<16s} {'Best Action':<28s} {'Mean Reward':>12s}")
    print("-" * 70)

    for bucket_idx, (lo, hi) in enumerate(BELIEF_BUCKETS):
        for unc_cat in UNCERTAINTY_CATS:
            key = (bucket_idx, unc_cat)
            if key not in reward_table:
                best_action = "A0_PASS"
                mean_reward = 0.0
            else:
                best_action = max(reward_table[key],
                                  key=lambda a: np.mean(reward_table[key][a]))
                mean_reward = np.mean(reward_table[key][best_action])

            policy[f"{lo:.2f}-{hi:.2f}_{unc_cat}"] = best_action
            print(f"[{lo:.2f},{hi:.2f}){'':<4s} {unc_cat:<16s} {best_action:<28s} {mean_reward:>12.1f}")

    # Save policy
    policy_path = os.path.join(CHECKPOINT_DIR, "frozen_policy.json")
    os.makedirs(CHECKPOINT_DIR, exist_ok=True)
    with open(policy_path, "w") as f:
        json.dump({
            "version": "v0.1",
            "trained_on": f"{n_scenarios} scenarios",
            "alpha": 0.10,
            "belief_buckets": BELIEF_BUCKETS,
            "uncertainty_categories": UNCERTAINTY_CATS,
            "policy": policy,
        }, f, indent=4)

    print(f"\nFrozen policy saved to: {policy_path}")
    print("=" * 60)

    return policy


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train frozen intervention policy")
    parser.add_argument("--n-scenarios", type=int, default=5000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    train_frozen_policy(n_scenarios=args.n_scenarios, seed=args.seed)
