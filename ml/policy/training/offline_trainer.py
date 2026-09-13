import sys
import os

# Add root project dir to path so we can import modules properly
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from ml.policy.environment.types import Action, RiskClass
from ml.policy.digital_twin.digital_twin_env import DigitalTwinEnv

def run_simulation_scenarios():
    print("================================================================")
    print("WARNING: EXECUTING POLICY SIMULATION IN OFFLINE DIGITAL TWIN")
    print("ANTIGRAVITY DIRECTIVE: ONLINE PRODUCTION EXPLORATION IS STRICTLY FORBIDDEN.")
    print("================================================================\n")
    
    env = DigitalTwinEnv()
    
    # Scenario 1: Weak Intervention against highly coerced victim
    print("--- SCENARIO 1: Weak Intervention (MICRO_PROMPT) ---")
    print("Context: Victim is highly panicked and compliant under active scammer call.")
    env.reset(initial_panic=0.9, initial_compliance=0.9, active_call=True)
    
    outcome = env.step(Action.MICRO_PROMPT)
    print(f"Scammer Tactic: {outcome['scammer_action']}")
    print(f"Victim Response: {outcome['victim_response']}")
    print(f"New Panic: {outcome['new_panic']:.2f}, New Compliance: {outcome['new_compliance']:.2f}")
    print(f"Reward: {outcome['reward']}\n")
    
    # Scenario 2: Strong Intervention against highly coerced victim
    print("--- SCENARIO 2: Strong Intervention (ISOLATION_BREAK) ---")
    print("Context: Victim is highly panicked and compliant under active scammer call.")
    env.reset(initial_panic=0.9, initial_compliance=0.9, active_call=True)
    
    outcome = env.step(Action.ISOLATION_BREAK)
    print(f"Scammer Tactic: {outcome['scammer_action']}")
    print(f"Victim Response: {outcome['victim_response']}")
    print(f"New Panic: {outcome['new_panic']:.2f}, New Compliance: {outcome['new_compliance']:.2f}")
    print(f"Reward: {outcome['reward']}\n")
    
    # Scenario 3: Uncertainty triggers ABSTAIN logic
    print("--- SCENARIO 3: Defender Confidence is low (ABSTAIN override) ---")
    print("Context: The model detects HIGH_RISK, but confidence is low (e.g. out-of-distribution).")
    
    action = env.defender.select_action(RiskClass.HIGH_RISK, confidence=0.4)
    print(f"Defender selects action: {action.name}")
    assert action == Action.PASS, "Defender failed to abstain!"

if __name__ == "__main__":
    run_simulation_scenarios()
