from enum import Enum, auto

class RiskClass(Enum):
    SAFE = auto()
    SUSPICIOUS = auto()
    HIGH_RISK = auto()
    ABSTAIN = auto()  # Model confidence is insufficient for a high-friction intervention.

class Action(Enum):
    PASS = auto()
    MICRO_PROMPT = auto()
    REFLECTION = auto()
    COOLDOWN = auto()
    ISOLATION_BREAK = auto()
    TRUSTED_VERIFY = auto()
    STEP_UP = auto()
