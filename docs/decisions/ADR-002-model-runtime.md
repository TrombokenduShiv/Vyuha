> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# ADR-002: Model Runtime and Offline Execution Architecture

- Status: Proposed
- Date: 2026-09-13
- Decision Owners:
  - Trombokendu Chakraborty
  - Aditya Kumar
- Technical Authority: Trombokendu Chakraborty

## Context

Vyuha requires very low perceived latency while preserving meaningful AI
functionality when network connectivity or institutional graph intelligence is
unavailable.

Not every model belongs on the device.

In particular, institution-wide graph intelligence depends on data that an
individual device cannot possess.

The architecture must therefore separate edge inference from institutional
inference.

## Decision

Vyuha SHALL use a hybrid edge/institutional model architecture.

### On-Device Runtime

The following components SHALL execute locally:

1. feature packing;
2. fast triage gate;
3. edge Sparse Risk MoE;
4. personal baseline logic;
5. transaction novelty analysis;
6. communication-context analysis;
7. device-integrity feature handling;
8. coercion belief-state update;
9. uncertainty/selective prediction;
10. frozen intervention policy or equivalent low-latency policy logic;
11. offline/degraded behavior.

### Institutional Runtime

The following SHALL execute outside the user's device:

1. heterogeneous financial graph storage;
2. heterogeneous ST-GNN inference;
3. institutional beneficiary/network risk computation;
4. institutional risk-token generation;
5. graph-model training.

### Development/Hackathon Runtime

For the hackathon, the institutional runtime MAY execute on a team's laptop or
local server.

This is a deployment simulation.

It must remain architecturally separated from the Android process.

## On-Device ML Runtime

Preferred production-oriented implementation:

- exported compact models;
- ExecuTorch or another approved edge inference runtime;
- quantization where validation permits;
- Kotlin/native integration through the SDK.

If model-runtime integration threatens the functional MVP, an explicit
fallback may temporarily use a deterministic/local implementation, but it MUST
be marked:

PROTOTYPE FALLBACK

and SHALL NOT be represented as the final intended runtime.

## Latency Budget

The <50 ms target applies ONLY to the local safety hot path.

Initial target budget:

- context preparation: <= 5 ms
- feature packing: <= 2 ms
- triage gate: <= 5 ms
- edge experts: <= 20 ms
- belief update: <= 3 ms
- intervention policy: <= 3 ms

Target local total:

median < 30 ms

p95 < 50 ms

Institutional graph network latency SHALL NOT be included in the local <50 ms
claim.

## Asynchronous Institutional Intelligence

Where possible:

institutional graph risk should be prefetched, cached or asynchronously
retrieved.

The local runtime must be able to begin evaluation before the graph result
arrives.

When institutional risk becomes available, it MAY update the session belief.

## Offline Definition

Offline-first SHALL mean:

Core agency-protection capability remains available without institutional
network connectivity.

Offline mode includes:

- Edge Risk MoE;
- local transaction features;
- local personal baseline;
- coercion belief state;
- cached institutional intelligence if valid;
- local intervention policy.

The following state rule is mandatory:

missing_graph_signal != low_graph_risk

Instead:

graph_signal_state = UNKNOWN

Unknown evidence must increase uncertainty rather than be interpreted as safe.

## Model Loading

The SDK SHALL have explicit model states:

- NOT_INITIALIZED
- LOADING
- READY
- DEGRADED
- FAILED

Payment handling must define behavior for all states.

## Determinism

The judge demo SHALL use versioned/frozen model artifacts.

No training will occur in the live transaction path.

No production-style online reinforcement learning will occur on demo users.

## Safety Critical Dependency Policy

The local transaction-safety path SHALL NOT require:

- a cloud LLM;
- remote generative model inference;
- remote speech recognition.

## Benchmarking

Every performance claim must be produced from measured instrumentation.

Required metrics:

- model load time;
- median inference time;
- p95 inference time;
- memory usage;
- APK/AAR/model size where relevant.

No benchmark may be entered in presentation material before measurement.

## Alternatives Considered

### Everything in Cloud

Rejected because:

- network dependency;
- privacy concerns;
- increased latency;
- contradicts offline-first positioning.

### Everything On Device

Rejected because:

- device lacks institution-wide graph;
- graph size cannot realistically live on each consumer device;
- institutional fraud intelligence belongs inside trusted infrastructure.

### LLM Agent in Transaction Critical Path

Rejected because:

- unpredictable latency;
- non-deterministic output;
- unnecessary compute;
- safety concerns.

## Consequences

### Positive

- low latency;
- privacy-preserving;
- realistic data ownership;
- meaningful offline mode;
- strong architecture story.

### Negative

- two-plane architecture increases engineering complexity;
- requires signed/cached graph risk;
- model export/runtime integration adds build effort.

## Interfaces Affected

- ContextSnapshot
- EdgeRiskOutput
- GraphRiskToken
- BeliefState
- InterventionDecision

## Acceptance Criteria

1. Normal local evaluation executes without graph server.
2. Local evaluation is instrumented.
3. Graph service can independently fail.
4. Failure does not crash payment UI.
5. UNKNOWN graph state propagates correctly.
6. No network operation is included in local latency benchmark.

## Revisit Triggers

- chosen edge runtime proves unsupported on target Android devices;
- measured latency exceeds acceptable limits;
- architecture gains persistent bank-side low-latency connectivity guarantees.