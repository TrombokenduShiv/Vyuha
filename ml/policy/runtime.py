"""Shared dual-integrity policy runtime used by training, evaluation and benchmarks."""
from dataclasses import dataclass
from pathlib import Path
import hashlib
import json
import math
import torch
from ml.edge.experts.moe import EdgeRiskMoE
from ml.edge.conformal.conformal_predictor import ConformalPredictor

ROOT = Path(__file__).resolve().parents[2]
ACTIONS = ["A0_PASS", "A1_MICRO_PROMPT", "A2_REFLECTION_CHALLENGE", "A3_COOLING_DELAY",
           "A4_ISOLATION_BREAK", "A5_TRUSTED_VERIFY", "A6_STEP_UP_REQUIRED"]


@dataclass(frozen=True)
class PaymentContext:
    communication_active: bool = False
    capture_risk: bool = False
    amount: float = .1
    novelty: float = .1
    deviation: float = .1
    online_purchase: bool = False
    independently_verified: bool = False

    def pack(self):
        values = [float(self.communication_active), float(self.capture_risk), self.amount,
                  self.novelty, 0.0, self.deviation]
        if any(not math.isfinite(v) or not 0 <= v <= 1 for v in values):
            raise ValueError("Features must be finite normalized values")
        return torch.tensor([values], dtype=torch.float32)


def state_key(agency, counterparty, uncertain, live, purchase):
    a = min(4, int(agency * 5))
    c = "UNKNOWN" if counterparty is None else "HIGH" if counterparty >= .7 else "LOW" if counterparty < .3 else "ELEVATED"
    return f"{a}:{c}:{int(uncertain)}:{int(live)}:{int(purchase)}"


def allowed_actions(agency, counterparty, uncertain, context):
    live = context.communication_active or context.capture_risk
    if agency >= .65 and live and not uncertain:
        return ["A4_ISOLATION_BREAK", "A6_STEP_UP_REQUIRED"]
    if counterparty is not None and counterparty >= .7:
        return ["A2_REFLECTION_CHALLENGE", "A6_STEP_UP_REQUIRED"]
    if counterparty is None and context.novelty >= .7 and not context.independently_verified:
        return ["A2_REFLECTION_CHALLENGE"] if context.online_purchase else ["A1_MICRO_PROMPT"]
    if agency >= .4 or (counterparty is not None and counterparty >= .3):
        return ["A2_REFLECTION_CHALLENGE", "A3_COOLING_DELAY"]
    if uncertain and context.novelty >= .7:
        return ["A1_MICRO_PROMPT", "A2_REFLECTION_CHALLENGE"]
    return ["A0_PASS", "A1_MICRO_PROMPT"]


def decide(agency, counterparty, uncertain, context, table=None):
    if not math.isfinite(agency) or not 0 <= agency <= 1:
        raise ValueError("Invalid agency score")
    if counterparty is not None and (not math.isfinite(counterparty) or not 0 <= counterparty <= 1):
        counterparty, uncertain = None, True
    candidates = allowed_actions(agency, counterparty, uncertain, context)
    key = state_key(agency, counterparty, uncertain, context.communication_active or context.capture_risk,
                    context.online_purchase)
    learned = (table or {}).get(key)
    action = learned if learned in candidates else candidates[0]
    if action == "A4_ISOLATION_BREAK":
        reason, template = "HIGH_AGENCY_RISK", "ISOLATION_BREAK"
    elif counterparty is not None and counterparty >= .7:
        reason, template = "COUNTERPARTY_HIGH", "COUNTERPARTY_WARNING"
    elif counterparty is None and context.novelty >= .7:
        reason, template = "COUNTERPARTY_UNKNOWN", "MERCHANT_VERIFICATION" if context.online_purchase else "RECEIVER_CHECK"
    elif action != "A0_PASS":
        reason, template = "PAYMENT_UNCERTAIN", "REFLECTION"
    else:
        reason, template = "LOW_OBSERVED_RISK", "PASS"
    # Bounds on probability of either risk, without assuming independence.
    bounds = [agency, 1.0] if counterparty is None else [max(agency, counterparty), min(1.0, agency + counterparty)]
    return {"agency_risk": agency, "counterparty_risk": counterparty,
            "payment_risk_bounds": bounds, "uncertain": uncertain or counterparty is None,
            "action_id": action, "template_id": template, "reason_codes": [reason]}


class PaymentRiskEngine:
    def __init__(self, checkpoint=None, policy_path=None):
        path = Path(checkpoint or ROOT / "checkpoints/moe_best.pt")
        ckpt = torch.load(path, map_location="cpu", weights_only=True)
        if ckpt.get("objective") != "agency_only":
            raise ValueError("Retrain the agency-only MoE")
        self.moe = EdgeRiskMoE(**ckpt["config"]).eval()
        self.moe.load_state_dict(ckpt["model_state_dict"])
        self.checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        self.conformal = ConformalPredictor()
        self.policy = {}
        if policy_path is not None:
            artifact = json.loads(Path(policy_path).read_text())
            if artifact["moe_sha256"] != self.checksum:
                raise ValueError("Policy was trained with a different MoE")
            self.policy = artifact["policy"]
            self.conformal.q_hat_safe = artifact["conformal"]["safe"]
            self.conformal.q_hat_risk = artifact["conformal"]["risk"]
            self.conformal.calibrated = True

    def evaluate(self, context, counterparty=None):
        with torch.inference_mode():
            agency = self.moe(context.pack())["risk_logits"].sigmoid().item()
        prediction = self.conformal.predict_set(agency)
        uncertain = len(prediction) != 1
        return decide(agency, counterparty, uncertain, context, self.policy)
