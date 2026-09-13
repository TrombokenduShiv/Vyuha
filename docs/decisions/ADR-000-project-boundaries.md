> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# ADR-000: Vyuha System and Project Boundaries

- Status: Proposed
- Date: 2026-09-13
- Decision Owners: Entire Team
- Technical Authority: Trombokendu Chakraborty
- Related Documents:
  - Vyuha 2.0 PRD
  - Vyuha 2.0 TRD
- Supersedes: None
- Superseded By: None

## Context

Vyuha is an Agency Integrity Layer for digital payments.

Existing authentication and fraud systems largely answer questions such as:

1. Is the person initiating the payment the legitimate account holder?
2. Is the device trusted?
3. Is the transaction or beneficiary anomalous?
4. Does the receiver exhibit known fraud or mule-account characteristics?

Vyuha addresses an additional failure mode:

A legitimate and correctly authenticated user may voluntarily authorize a
transaction while operating under live psychological coercion, social
engineering, remote influence, or attacker-controlled communication.

The primary problem is therefore not identity compromise.

It is agency compromise.

The system must detect evidence that the user's payment intent may be under
external influence and introduce proportionate intervention before payment
authorization.

## Decision

Vyuha SHALL be designed as an Agency Integrity Layer.

Vyuha SHALL NOT be positioned or implemented merely as:

- a generic fraud classifier;
- a scam chatbot;
- a rules engine;
- an LLM wrapper;
- a transaction anomaly detector;
- a remote-access detector;
- a mule-account detector;
- a payment gateway replacement.

The complete system SHALL consist conceptually of two cooperating intelligence
planes.

### Plane A — Edge Agency Intelligence

Runs within a participating payment/banking application environment.

Responsibilities:

- receive privacy-minimized context;
- construct transaction-session state;
- execute the fast triage gate;
- execute local risk experts;
- maintain coercion belief state;
- estimate uncertainty;
- select or consume an approved intervention policy;
- continue operating in degraded/offline mode.

### Plane B — Institutional Graph Intelligence

Runs within infrastructure that can legitimately access institutional
transaction/network information.

Responsibilities:

- heterogeneous financial graph construction;
- spatio-temporal graph inference;
- beneficiary/network risk estimation;
- signed graph-risk artifact generation;
- institutional model monitoring.

## Critical Path Boundary

The payment-time safety path MUST NOT depend on:

- general-purpose cloud LLM inference;
- continuous call recording;
- call transcription;
- screen recording;
- unrestricted monitoring of other applications;
- always-available internet connectivity.

An LLM MAY be used outside the critical path for:

- Scam Digital Twin simulation;
- synthetic scenario generation;
- analyst explanation;
- development tooling;
- documentation;
- research experimentation.

## Privacy Boundary

The prototype and intended production architecture SHALL follow data
minimization.

The core system SHALL NOT require raw:

- microphone audio;
- call audio;
- conversation transcripts;
- screen contents;
- user messages;
- contact lists;
- arbitrary browsing history.

Where possible, signals SHALL be represented as derived features, for example:

communication_active = true

instead of raw call content.

## Authority Boundary

Vyuha may recommend or trigger:

- PASS
- MICRO_PROMPT
- REFLECTION
- COOLDOWN
- ISOLATION_BREAK
- TRUSTED_VERIFY
- STEP_UP_REQUIRED

Vyuha SHALL NOT independently claim authority to permanently reject a bank
transaction in production.

Final authorization/blocking authority remains with the host financial
institution and its approved policies.

## Fraud Scope

The initial demonstration SHALL focus on digital-arrest-style coercion.

The underlying system SHALL target the broader category:

coercion-driven or live-guided authorized payment fraud.

Examples may include:

- digital arrest;
- impersonation scams;
- fake bank support;
- remote-access scams;
- authority impersonation;
- emergency impersonation;
- live-guided investment scams.

## Prototype Boundary

The hackathon prototype MAY use:

- a synthetic institutional financial graph;
- a simulated host bank/UPI application;
- deterministic demo fixtures;
- synthetic scam sessions;
- simulated device-risk signals where restricted platform APIs are unavailable.

Every simulated component MUST be clearly marked.

A simulated signal SHALL NOT be described as a production integration.

## Production Boundary

The production design assumes integration with authorized:

- banks;
- PSP applications;
- TPAPs;
- fraud-risk infrastructure;
- financial institutions;
- potentially telecom fraud-intelligence providers.

The architecture SHALL NOT claim to bypass NPCI or banking security controls.

## Explicit Non-Goals for Hackathon MVP

The MVP will not attempt to deliver:

- production NPCI certification;
- integration with a real private bank transaction graph;
- nationwide telecom integration;
- production fraud-loss guarantees;
- fully autonomous payment blocking;
- production-grade federated learning;
- online reinforcement learning on real customers.

## Consequences

### Positive

- Keeps Vyuha technically defensible.
- Prevents architecture drift into a generic AI application.
- Provides a strong privacy story.
- Enables offline-first operation.
- Separates payer-side agency intelligence from institutional graph intelligence.

### Negative / Trade-offs

- Full real-world deployment requires financial-institution partnerships.
- Some device-context signals cannot be universally obtained.
- Hackathon graph evaluation requires synthetic data.
- The prototype cannot claim production fraud-detection accuracy.

## Interfaces Affected

All system modules.

## Acceptance Criteria

The repository architecture and demo must visibly preserve:

1. local intelligence;
2. institutional graph intelligence;
3. belief-state fusion;
4. adaptive intervention;
5. offline degradation;
6. strict separation of production functionality and simulation.

## Revisit Triggers

Revisit this ADR only if:

- the primary product category changes;
- deployment moves outside financial applications;
- a financial institution provides direct production integration;
- privacy requirements substantially change.