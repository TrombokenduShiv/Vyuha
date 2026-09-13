> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# ADR-004: Risk Fusion, Uncertainty and Intervention Policy

- Status: Proposed
- Date: 2026-09-13
- Decision Owners:
  - Trombokendu Chakraborty
  - Alaukik Saxena
- Implementation Contributors:
  - Sneha Sinha
  - Aditya Kumar
- Technical Authority: Trombokendu Chakraborty
- HCI Authority: Alaukik Saxena

## Context

Vyuha receives incomplete and noisy evidence.

Potential evidence includes:

- personal transaction deviation;
- beneficiary novelty;
- communication context;
- device-integrity risk;
- institutional graph risk;
- user responses to interventions;
- previous session observations.

No single signal proves coercion.

A high transaction amount alone is not fraud.

An active call alone is not coercion.

A risky beneficiary alone does not prove the payer is compromised.

The system therefore requires temporal evidence fusion and uncertainty-aware
intervention.

## Decision

Vyuha SHALL maintain a session-level Coercion Belief State.

Conceptually:

b_t = P(C_t = 1 | observations up to time t)

where C represents the latent state that the user may be operating under
external coercive influence.

The implementation may use calibrated Bayesian-style updating, learned
state-transition logic, or another validated approximation.

The state MUST remain inspectable for the hackathon demonstration.

## Evidence Sources

Possible evidence includes:

### Edge

- personal amount deviation;
- beneficiary novelty;
- communication activity;
- device integrity;
- payment velocity;
- initiation context.

### Institutional

- beneficiary graph risk;
- account/network anomaly;
- signed external fraud-risk indicators where available.

### Interaction

- response to micro-prompt;
- uncertainty response;
- acknowledgment;
- refusal to stop communication;
- request for trusted verification.

## Uncertainty

The system SHALL model uncertainty separately from risk.

Conceptual outputs:

- SAFE
- SUSPICIOUS
- HIGH_RISK
- ABSTAIN

ABSTAIN means:

Evidence is insufficient to justify strong automated friction.

Missing evidence SHALL NOT automatically lower risk.

## Intervention Action Space

The standardized action enum SHALL be:

PASS

MICRO_PROMPT

REFLECTION

COOLDOWN

ISOLATION_BREAK

TRUSTED_VERIFY

STEP_UP_REQUIRED

Additional actions require an ADR amendment or explicit architecture approval.

## Intervention Philosophy

The system SHALL select the minimum effective friction consistent with safety.

Conceptually:

utility(action) =
expected_safety_gain
-
lambda * user_friction

The production objective is not maximum blocking.

It is maximum prevented harm with minimum unnecessary interruption.

## Default Risk Ladder

Initial policy:

### Low Risk

Action:
PASS

### Mild Suspicion

Action:
MICRO_PROMPT

Example purpose:

interrupt automatic action with minimal friction.

### Moderate Suspicion

Action:
REFLECTION

Purpose:

make payment intent explicit.

### Medium-High Risk

Action:
COOLDOWN

Purpose:

reduce urgency and interrupt scammer-induced tempo.

### High Risk

Action:
ISOLATION_BREAK

Purpose:

remove or weaken continuous attacker control before authorization.

### Critical Risk

Action:
TRUSTED_VERIFY or STEP_UP_REQUIRED

Purpose:

introduce independent verification.

Exact thresholds SHALL NOT be treated as immutable constants until calibrated.

## Isolation Break

Isolation Break is a first-class intervention.

It is conceptually different from a warning.

Its objective is to interrupt the scammer's real-time control loop.

Depending on host capabilities, Isolation Break may require:

- communication context to end;
- remote-control condition to stop;
- a cooling period;
- re-entry into payment flow;
- independent verification.

The demo may simulate some host/platform capabilities if clearly labelled.

## Safety Circle

TRUSTED_VERIFY may invoke a user-configured trusted contact.

The first notification SHOULD minimize financial disclosure.

Example:

"A trusted contact verification has been requested for a high-risk payment."

The system SHOULD NOT automatically disclose:

- exact transaction amount;
- beneficiary identity;
- transaction history;

unless permitted by user/system policy.

## Institution Authority

Vyuha SHALL NOT independently claim final permanent denial authority.

STEP_UP_REQUIRED means:

The host financial institution must apply additional authorization or fraud
review according to its own policy.

## Contextual Bandit

A contextual bandit MAY select among approved interventions.

For the hackathon:

- policy is trained offline or simulated;
- deployment policy is frozen;
- no unsafe live exploration;
- every available action belongs to the approved action set.

The policy must not learn by intentionally exposing live users to weaker
protections.

## Scam Digital Twin

A multi-agent simulation MAY be used to generate policy trajectories.

Roles may include:

- Scammer Agent;
- Victim Persona Agent;
- Vyuha Defender Agent.

Digital Twin output is training/simulation evidence.

It SHALL NOT independently determine production safety claims.

## Explainability

Every intervention decision must include machine-readable reason codes.

Example:

{
  action: "ISOLATION_BREAK",
  belief: 0.87,
  uncertainty: 0.08,
  reason_codes: [
    "ACTIVE_COMMUNICATION",
    "HIGH_AMOUNT_DEVIATION",
    "GRAPH_MULE_RISK"
  ]
}

The consumer warning UI SHALL NOT necessarily display all internal risk
signals.

Reason codes primarily support:

- auditability;
- debugging;
- analyst interfaces;
- jury demonstration.

## Hard-Coded Rules

A small number of hard safety constraints MAY override learned policy.

Examples:

- invalid/expired graph-risk artifact must not be trusted;
- model failure must enter degraded state;
- unsupported policy action must fall back safely.

General fraud detection SHOULD NOT devolve into a large hidden rule engine.

## Outcome Feedback

Possible outcomes:

- payment_completed;
- payment_cancelled;
- trusted_verified_safe;
- trusted_verified_risky;
- user_reported_scam;
- institution_confirmed_fraud;
- unknown.

Hackathon feedback MAY be synthetic.

Production learning from outcomes must occur outside the live critical path.

## Consequences

### Positive

- risk-proportionate UX;
- lower false-positive burden;
- mathematically meaningful OODA loop;
- RL has a justified role;
- uncertainty becomes explicit;
- HCI and ML connect through a real contract.

### Negative

- calibration is difficult;
- intervention effectiveness requires human evaluation;
- bandit reward design is non-trivial;
- synthetic personas cannot prove real behavioral efficacy.

## Interfaces Affected

- EdgeRiskOutput
- GraphRiskToken
- BeliefState
- InterventionDecision
- InterventionResponse
- OutcomeFeedback

## Acceptance Criteria

The hackathon system must demonstrate at least:

### Scenario A — Benign

Known beneficiary + normal context

Expected:
PASS

### Scenario B — Coercion

High deviation + active communication + device/context risk + suspicious graph

Expected:
ISOLATION_BREAK or stronger

### Scenario C — Legitimate Unusual Payment

Large/new transaction but low supporting coercion evidence

Expected:
proportionate low friction rather than automatic blocking

### Scenario D — Offline

Institutional graph unavailable

Expected:
local protection continues with increased uncertainty

## Revisit Triggers

- human-subject HCI evaluation reveals ineffective interventions;
- false-positive rate is unacceptable;
- new approved intervention is introduced;
- host bank grants additional transaction-control capability;
- policy architecture changes substantially.