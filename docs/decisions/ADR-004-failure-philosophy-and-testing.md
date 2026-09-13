# ADR 004: Mandatory Failure Philosophy and Testing Hierarchy

## Context
Vyuha operates in a highly adversarial edge environment. Telemetry APIs can fail, network conditions fluctuate, and attackers actively attempt to subvert the system (e.g., intercepting graph tokens or blocking telemetry APIs). 

A critical flaw in legacy rule-based systems is treating missing signals as safe signals (e.g., `if (graphRiskToken == null) risk = 0`). This fabricates risk certainty out of thin air.

## Decision

### 1. Mandatory Failure Philosophy
Vyuha must fail safe without becoming unusable. The core architectural rule is:
**`MISSING SIGNAL != SAFE SIGNAL`**

Unknown evidence must explicitly increase system uncertainty. When evidence is missing, the Edge Belief Engine must inject `UncertaintyLabel.ABSTAIN` into the Conformal Uncertainty Set. This mathematically forces the Policy Bandit to abandon low-friction passes (`A0_PASS`, `A1_MICRO_PROMPT`) in favor of authenticated fallback interventions (e.g., `A6_STEP_UP_REQUIRED`).

### 2. Testing Hierarchy
All Vyuha features must be tested sequentially across this exact hierarchy:

1. **Unit**: Mathematical correctness of isolated components (e.g., conformal probability thresholds).
2. **Contract**: Ensuring JSON/serialization schemas are perfectly adhered to.
3. **ML**: Validating model inference output shapes and simulated digital twin trajectories.
4. **Integration**: Ensuring the pipeline (Aggregator → Triage → Belief → Policy) orchestrates correctly.
5. **Android**: Ensuring SDK state survives Android lifecycle events (e.g., configuration changes).
6. **Latency**: Strict measurement of hot-path bounds via JMH/JUnit (`< 50ms`).
7. **Failure**: Adversarial testing of all critical failure vectors (offline mode, API timeouts, malformed tokens).
8. **Demo Rehearsal**: End-to-end visual validation on the Judge Dashboard or Demo App.

### Consequences
- Requires explicitly nullable properties on telemetry schemas.
- Requires robust `androidTest` directories using Espresso/JUnit4 to simulate Android lifecycle events like `Activity` recreation.
