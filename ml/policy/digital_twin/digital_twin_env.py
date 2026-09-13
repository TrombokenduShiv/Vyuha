import random
from ..environment.types import Action, RiskClass

class Scammer:
    def __init__(self):
        self.active_communication = False
        
    def act(self, victim):
        if not self.active_communication:
            return "Disconnected"
            
        # Scammer exerts adversarial pressure
        pressure_tactics = [
            "Ignore the bank warning.",
            "Hurry up, the police are on their way.",
            "This is a secure RBI verification step."
        ]
        tactic = random.choice(pressure_tactics)
        
        # Pressure increases victim panic and compliance
        victim.panic = min(1.0, victim.panic + 0.2)
        victim.compliance = min(1.0, victim.compliance + 0.3)
        return tactic

class Victim:
    def __init__(self, panic_level=0.0, compliance_level=0.0):
        self.panic = panic_level
        self.compliance = compliance_level
        
    def respond_to_intervention(self, action: Action, scammer: Scammer):
        if action == Action.PASS:
            return "Proceeds blindly", 0.0

        if action == Action.MICRO_PROMPT:
            if self.panic > 0.7 and self.compliance > 0.7:
                # Scammer pressure overrides the weak intervention
                return "Dismisses warning immediately", -10.0
            else:
                self.compliance -= 0.1
                return "Pauses to read", 1.0

        if action == Action.ISOLATION_BREAK:
            # Force stops the call/screen share
            scammer.active_communication = False
            
            # Without scammer pressure, compliance rapidly drops
            self.compliance = max(0.0, self.compliance - 0.5)
            self.panic = max(0.0, self.panic - 0.2)
            return "Communication severed, victim hesitates", 50.0
            
        if action == Action.COOLDOWN:
            if scammer.active_communication:
                return "Victim waits on phone, scammer coaches them", -5.0
            else:
                self.panic = max(0.0, self.panic - 0.4)
                return "Victim calms down", 20.0
                
        return "Unknown interaction", 0.0

class Defender:
    def __init__(self):
        self.policy_weights = {} # Placeholder for actual RL Q-table / Neural Net
        
    def select_action(self, risk_class: RiskClass, confidence: float) -> Action:
        # Rule: Uncertainty MUST influence intervention severity.
        if confidence < 0.6 and risk_class in [RiskClass.HIGH_RISK, RiskClass.SUSPICIOUS]:
            print("  [DEFENDER] Confidence insufficient for high-friction intervention.")
            return Action.PASS  # We map ABSTAIN to PASSing the transaction without friction
            
        if risk_class == RiskClass.HIGH_RISK:
            return Action.ISOLATION_BREAK
            
        if risk_class == RiskClass.SUSPICIOUS:
            return Action.MICRO_PROMPT
            
        return Action.PASS

class DigitalTwinEnv:
    """
    Offline RL simulation environment for safe policy exploration.
    WARNING: Do NOT use this logic for online production exploration.
    """
    def __init__(self):
        self.scammer = Scammer()
        self.victim = Victim()
        self.defender = Defender()
        
    def reset(self, initial_panic=0.8, initial_compliance=0.9, active_call=True):
        self.victim = Victim(panic_level=initial_panic, compliance_level=initial_compliance)
        self.scammer = Scammer()
        self.scammer.active_communication = active_call
        self.defender = Defender()
        
    def step(self, defender_action: Action):
        # 1. Scammer tries to exert influence
        scammer_tactic = self.scammer.act(self.victim)
        
        # 2. Defender intervention hits the victim
        victim_response, reward = self.victim.respond_to_intervention(defender_action, self.scammer)
        
        return {
            "scammer_action": scammer_tactic,
            "victim_response": victim_response,
            "new_panic": self.victim.panic,
            "new_compliance": self.victim.compliance,
            "reward": reward
        }
