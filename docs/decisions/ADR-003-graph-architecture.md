> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# ADR-003: Institutional Heterogeneous Spatio-Temporal Graph Architecture

- Status: Proposed
- Date: 2026-09-13
- Decision Owner: Trombokendu Chakraborty
- Technical Authority: Trombokendu Chakraborty

## Context

Recipient-account risk cannot be inferred reliably only from the payer's local
device.

Mule accounts and fraud networks exhibit relational and temporal structure.

Examples include:

- high fan-in;
- rapid fan-out;
- account-device reuse;
- shared phone relationships;
- short-lived transfer chains;
- burst activity;
- smurfing;
- layered movement;
- camouflage with legitimate-looking edges.

A flat transaction classifier loses important relational information.

The graph also contains different entity types.

Therefore the system requires heterogeneous and temporal reasoning.

## Decision

Vyuha's institutional intelligence SHALL represent financial risk as a
heterogeneous temporal graph.

The graph engine SHALL execute institution-side, not on-device.

## MVP Node Types

The minimum hackathon graph schema SHALL contain:

- ACCOUNT
- VPA
- DEVICE
- PHONE

## Production-Extensible Node Types

The architecture may later support:

- MERCHANT
- IP_ADDRESS
- COMPLAINT
- TELECOM_RISK_ENTITY
- SESSION
- BENEFICIARY
- BANK

These are not mandatory for the hackathon.

## MVP Edge Types

At minimum:

ACCOUNT --SENDS_TO--> ACCOUNT

ACCOUNT --OWNS--> VPA

DEVICE --ACCESSES--> ACCOUNT

PHONE --ASSOCIATED_WITH--> ACCOUNT

VPA --TRANSFERS_TO--> VPA

Every transaction-like edge SHALL carry temporal information.

## Transaction Edge Features

Candidate features:

- timestamp;
- amount bucket;
- direction;
- transaction velocity;
- inter-arrival time;
- receiver novelty;
- transaction channel;
- burst statistics.

Exact feature availability in production depends on institutional data.

## Model Requirement

The model must jointly reason about:

1. heterogeneous relations;
2. graph topology;
3. temporal evolution.

Preferred research direction:

Heterogeneous Graph Transformer-style relational encoding

combined with

temporal event/history encoding.

Potential architectures for evaluation include:

- HGT + temporal encoding;
- TGN adapted to heterogeneous relationships;
- TGAT-derived temporal attention;
- heterogeneous GraphSAGE + recurrent temporal state.

## Hackathon Architecture Decision

Because implementation reliability is more important than research purity,
the team MAY select the simplest architecture that satisfies:

- heterogeneity;
- temporal information;
- real graph inference;
- measurable evaluation.

The final exact network SHALL be chosen through an implementation spike.

The selected model must be documented before final integration.

## Forbidden Shortcut

A standard tabular neural network SHALL NOT be labeled an ST-GNN.

A static graph model without temporal features SHALL NOT be presented as fully
spatio-temporal.

If a simplified graph model is used during an early prototype, it must be
explicitly described as such.

## Graph Output

The graph plane SHALL output an institutional risk artifact.

Conceptual fields:

- entity identifier;
- network risk score;
- model confidence;
- reason codes;
- model version;
- issued timestamp;
- expiry;
- signature/integrity proof where implemented.

The Android SDK SHALL NOT consume the entire graph.

It consumes only the risk artifact.

## Risk Token

GraphRiskToken should conceptually contain:

{
  beneficiary_ref,
  graph_risk,
  confidence,
  reason_codes,
  model_version,
  issued_at,
  expires_at,
  signature
}

No unnecessary graph topology should be transmitted to the phone.

## Synthetic Graph Policy

The hackathon prototype does not have access to private bank/NPCI transaction
graphs.

Therefore the graph dataset SHALL be synthetic.

Synthetic graph generation SHALL contain both benign and adversarial motifs.

Benign patterns:

- ordinary peer-to-peer transfers;
- household relationships;
- merchant payments;
- salary/payment hubs;
- repeated trusted transactions.

Suspicious motifs:

- high fan-in mule;
- rapid fan-out;
- burst laundering;
- chain laundering;
- smurfing;
- device-sharing anomaly;
- account-phone reuse;
- camouflage edges;
- mixed legitimate/fraud neighborhood.

## Dataset Integrity

The synthetic pipeline SHALL use reproducible random seeds.

Train/validation/test separation must avoid temporal leakage.

Future events must not leak into earlier graph states.

## Evaluation

Required model metrics:

- ROC-AUC;
- PR-AUC;
- precision;
- recall;
- F1;
- calibration;
- false-positive rate;
- inference latency.

PR-AUC is especially important under class imbalance.

No production-accuracy claims may be derived from synthetic evaluation.

## Explainability

The graph engine SHOULD emit reason codes such as:

- HIGH_FAN_IN
- RAPID_FAN_OUT
- SHARED_DEVICE_CLUSTER
- BURST_ACTIVITY
- MULE_NEIGHBORHOOD
- TEMPORAL_CHAIN_PATTERN

The UI must not expose raw technical graph reasoning directly to vulnerable
end users unless translated into safe wording.

## Research Separation

The graph engine is complementary to payer-side coercion intelligence.

Vyuha SHALL NOT claim:

graph risk == coercion.

A legitimate recipient may receive money from a coerced victim.

A risky beneficiary may also be encountered by a non-coerced user.

Graph risk is one evidence source in the global belief state.

## Consequences

### Positive

- strong relational fraud modeling;
- clear justification for graph ML;
- naturally supports mule-ring detection;
- preserves institutional data boundary.

### Negative

- difficult to obtain real training data;
- synthetic evaluation does not establish real-world performance;
- implementation is significantly more complex than tabular fraud scoring.

## Interfaces Affected

- GraphRiskRequest
- GraphRiskToken
- BeliefState

## Acceptance Criteria

1. Graph contains >= 4 heterogeneous entity types.
2. Time is represented explicitly.
3. Model executes actual graph inference.
4. Benign and suspicious graph fixtures exist.
5. Evaluation dataset is reproducible.
6. Graph risk reaches Android only through defined contract.
7. No raw institutional graph is packaged inside the SDK.

## Open Decision

The exact neural architecture remains:

TO BE BENCHMARKED

Candidate final choices:

A. HGT + temporal encoder
B. heterogeneous GraphSAGE + GRU temporal state
C. heterogeneous TGN/TGAT derivative

The team leader shall approve the final model after implementation comparison.

## Revisit Triggers

- availability of a real anonymized financial graph;
- insufficient performance of selected architecture;
- inability to meet hackathon implementation deadline;
- significant schema expansion.