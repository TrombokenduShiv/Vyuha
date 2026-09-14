"""Offline policy fitting with the trained MoE and explicitly assumed twin outcomes.

Rewards measure simulated residual harm plus user friction. They are not evidence
of intervention effectiveness in people. No online policy exploration exists.
"""
import argparse
import json
from collections import defaultdict
import numpy as np
import torch
from ml.policy.runtime import ROOT, ACTIONS, PaymentContext, PaymentRiskEngine, allowed_actions, state_key
from ml.edge.training.train_moe import generate_synthetic_data


class OfflinePaymentTwin:
    # Assumptions to validate through separately governed HCI studies.
    def reward(self, action, coerced, receiver_fraud, live, rng):
        prevention = {"A0_PASS": 0, "A1_MICRO_PROMPT": .12, "A2_REFLECTION_CHALLENGE": .55,
                      "A3_COOLING_DELAY": .3, "A4_ISOLATION_BREAK": .8 if live and coerced else .05,
                      "A5_TRUSTED_VERIFY": .7, "A6_STEP_UP_REQUIRED": .92}[action]
        friction = [0, 1, 4, 9, 12, 18, 30][ACTIONS.index(action)]
        harm = (coerced or receiver_fraud) and rng.random() > prevention
        return -100 * int(harm) - friction


def train_frozen_policy(n_scenarios=5000, seed=2718):
    if n_scenarios < 100:
        raise ValueError("At least 100 scenarios required")
    torch.set_num_threads(2)
    engine = PaymentRiskEngine()
    cal_x, cal_y = generate_synthetic_data(2000, seed=999)
    with torch.inference_mode():
        scores = engine.moe(cal_x)["risk_logits"].sigmoid().numpy()
    engine.conformal.calibrate(scores, cal_y.numpy())
    x, y = generate_synthetic_data(n_scenarios, seed=seed)
    with torch.inference_mode():
        agency_scores = engine.moe(x)["risk_logits"].sigmoid().numpy()
    rng = np.random.default_rng(seed)
    twin, rewards = OfflinePaymentTwin(), defaultdict(lambda: defaultdict(list))
    for i, agency in enumerate(agency_scores):
        # Counterparty truth and communication/coercion truth are independent.
        receiver_fraud = rng.random() < .15
        c = None if rng.random() < .2 else float(rng.beta(7, 2) if receiver_fraud else rng.beta(2, 8))
        context = PaymentContext(bool(x[i, 0]), bool(x[i, 1]), float(x[i, 2]), float(x[i, 3]),
                                 float(x[i, 5]), online_purchase=bool(rng.random() < .4))
        uncertain = len(engine.conformal.predict_set(float(agency))) != 1
        live = context.communication_active or context.capture_risk
        key = state_key(float(agency), c, uncertain, live, context.online_purchase)
        # Common random draws across actions reduce counterfactual comparison noise.
        outcome_seed = int(rng.integers(2**31))
        for action in allowed_actions(float(agency), c, uncertain, context):
            action_rng = np.random.default_rng(outcome_seed)
            values = [twin.reward(action, bool(y[i]), receiver_fraud, live, action_rng) for _ in range(16)]
            rewards[key][action].append(float(np.mean(values)))
    table = {key: max(actions, key=lambda action: np.mean(actions[action])) for key, actions in rewards.items()}
    artifact = {"version": "2.1", "moe_sha256": engine.checksum, "seed": seed,
                "n_scenarios": n_scenarios, "data_kind": "synthetic",
                "reward_source": "OfflinePaymentTwin assumptions; not validated human outcomes",
                "conformal": {"safe": engine.conformal.q_hat_safe, "risk": engine.conformal.q_hat_risk,
                              "alpha": engine.conformal.alpha, "calibration_seed": 999},
                "support": {k: {a: len(v) for a, v in acts.items()} for k, acts in rewards.items()},
                "policy": table}
    (ROOT / "checkpoints/frozen_policy.json").write_text(json.dumps(artifact, indent=2))
    print(f"Saved {len(table)} policy states from {n_scenarios} synthetic scenarios; safety constraints applied at runtime")
    return table


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--n-scenarios", type=int, default=5000)
    parser.add_argument("--seed", type=int, default=2718)
    args = parser.parse_args()
    train_frozen_policy(args.n_scenarios, args.seed)
