# Vyuha Performance Budget

This document formalizes the strict performance latency requirements for the Vyuha Edge AI hot-path. 
Vyuha operates at the critical juncture of an outgoing payment; any perceptible delay degrades the user experience and breaks the behavioral flow of the application. Therefore, all local AI logic must execute under a strict total budget of **50 ms (p95)**.

> [!CAUTION]
> **No Fake Benchmarks**
> These budgets are aggressively tested in CI via JMH/JUnit. Do not mock latency or assume it is fast. Measure every component.

## Hot-Path Targets

| Component | Target Budget | Description |
| :--- | :--- | :--- |
| **Context Extraction** | `< 5 ms` | Gathering local telemetry, device state, UI interactions, and assembling the raw `EventSnapshot`. |
| **Feature Packing** | `< 2 ms` | Converting the snapshot into the vectorized `ContextSnapshot` for neural inference. |
| **Triage Gate** | `< 5 ms` | Initial heuristic evaluation (Top-K expert routing). |
| **Edge Experts (MoE)**| `< 20 ms` | Inference for the selected experts (e.g. `TransactionNovelty`, `InstitutionalSignal`). |
| **Belief Update** | `< 2 ms` | Conformal uncertainty calibration and Bayesian posterior update. |
| **Policy Bandit** | `< 2 ms` | Outputting the final `InterventionDecision`. |

---

## Overall Purely Local Vyuha Hot-Path
- **TARGET MEDIAN:** `< 30 ms`
- **TARGET P95:** `< 50 ms`

> [!NOTE]
> **Network Operations Excluded**
> Network lookups (e.g., retrieving the ST-GNN graph risk token) are explicitly asynchronous. The network request is dispatched ahead of time and cached. The local `<50 ms` claim assumes the local cache lookup. Network latency MUST NOT be included in this hot-path calculation.
