# Warning UI prototype integration

This directory contains an additive integration of the uploaded standalone Android warning UI into Vyuha 2.0.

The uploaded prototype models a visual warning progression, while the repository already owns the canonical policy contract (`ActionId.A0` through `ActionId.A6`), coercion belief, graph-risk token flow, and transaction orchestration. Replacing those pieces would fork the architecture.

`VyuhaWarningBridge` therefore accepts the existing `InterventionDecision` and renders the prototype visual language without changing policy semantics.

Mapping:
- A0_PASS -> PASS
- A1_MICRO_PROMPT -> MICRO_INQUIRY
- A2_REFLECTION_CHALLENGE -> REFLECTION
- A3_COOLING_DELAY -> COOLING_DELAY
- A4_ISOLATION_BREAK -> ISOLATION_BREAK
- A5_TRUSTED_VERIFY -> TRUSTED_VERIFY
- A6_STEP_UP_REQUIRED -> STEP_UP_REQUIRED

The existing `InterventionRenderer` remains untouched. A host flow may opt into `VyuhaWarningBridge` at its composition boundary when desired.
