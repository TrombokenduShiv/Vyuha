> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# ADR-001: Hackathon Demonstration Platform

- Status: Proposed
- Date: 2026-09-13
- Decision Owners:
  - Aditya Kumar
  - Alaukik Saxena
  - Sneha Sinha
- Technical Authority: Trombokendu Chakraborty

## Context

Vyuha is intended to operate as an embedded SDK within participating financial
applications.

The team does not have production access to PhonePe, Google Pay, Paytm, NPCI
or a private banking application.

Attempting to monitor arbitrary third-party UPI applications would introduce
platform restrictions and create a misleading prototype.

The demonstration must therefore prove the intended integration contract
without pretending to possess unauthorized production access.

## Decision

The hackathon demonstration SHALL use a native Android host-bank simulator.

Working name:

VyuhaBank Demo

The Android application SHALL represent a hypothetical participating financial
institution that has intentionally embedded the Vyuha SDK.

Architecture:

Host Banking App
        |
        v
Vyuha Android SDK
        |
        +---- Edge Runtime
        |
        +---- Intervention Renderer Contract
        |
        +---- Optional Graph Risk Client

The SDK SHALL be separately packageable as an Android library/AAR wherever
practical.

## Technology

Primary:

- Kotlin
- Android SDK
- Jetpack Compose
- Kotlin Coroutines
- Gradle

The primary transaction demo SHALL NOT be implemented as a browser-only web
application.

A web dashboard MAY be used for judge/developer observability.

## Demo Application Responsibilities

The host demo application will implement:

1. user home screen;
2. beneficiary selection;
3. payment amount entry;
4. payment confirmation;
5. pre-authorization interception point;
6. simulated authorization;
7. final transaction result.

The host application SHALL invoke Vyuha before final authorization.

## SDK Integration Contract

The application should conceptually expose a flow similar to:

Vyuha.initialize(...)

Vyuha.beginPayment(transactionContext)

Vyuha.updateContext(contextSnapshot)

Vyuha.evaluate()

Vyuha.recordOutcome(...)

Exact APIs may differ after engineering review.

## Warning Presentation

Warning/intervention UX SHALL be rendered inside the cooperating host
application.

The prototype SHALL NOT rely on an unrestricted Android system-overlay
architecture.

## Judge Dashboard

A separate development/judge dashboard MAY display:

- event timeline;
- context features;
- MoE routing;
- expert scores;
- graph risk;
- coercion belief;
- uncertainty;
- intervention action;
- component latency.

The judge dashboard is observability tooling and is not the end-user product.

## Alternatives Considered

### Alternative A — Standalone Vyuha App Monitoring Other Apps

Rejected because:

- poor production realism;
- Android restrictions;
- unnecessary permission complexity;
- creates surveillance concerns.

### Alternative B — Flutter Application

Rejected for primary implementation because:

- native Android integration is central to the SDK story;
- Android runtime access is easier to reason about in Kotlin;
- AAR packaging better communicates B2B deployability.

Flutter may still be used for auxiliary interfaces only if necessary.

### Alternative C — Web-Only Prototype

Rejected.

A web-only payment simulator would fail to prove the Edge SDK architecture.

## Offline Behavior

The Android host and Vyuha Edge Runtime SHALL be capable of completing a demo
with the graph service unavailable.

The interface should visibly expose:

Institutional Intelligence: Unavailable

while retaining:

Edge Protection: Active

## Consequences

### Positive

- technically honest;
- easy to demo;
- allows actual SDK packaging;
- gives complete control of warning UX;
- supports offline demonstration.

### Negative

- not a production banking integration;
- some platform risk signals must be simulated;
- Android development increases implementation effort.

## Acceptance Criteria

A successful build must demonstrate:

1. native Android transaction flow;
2. Vyuha SDK invocation;
3. normal payment fast path;
4. suspicious transaction intervention;
5. offline/degraded behavior;
6. independent SDK build artifact or equivalent module separation.

## Revisit Triggers

- Access to a legitimate financial institution sandbox.
- Requirement to support iOS during the event.
- Severe native Android build-blocking issue.