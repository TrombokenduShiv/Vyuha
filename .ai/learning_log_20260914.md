# Learning Log — 2026-09-14

## Session: Alaukik HCI Deliverables

### What was done
Built 5 new documents from scratch under Alaukik's HCI ownership:

1. `docs/INTERVENTION_STATE_MACHINE.md` — 12-state FSM with full transition table, guards, effects, timeouts, escalation/de-escalation rules
2. `docs/COPY_MATRIX.md` — Exact user-facing text for all A0-A6 actions with word count compliance, randomized variants, string resource keys
3. `docs/SEVERITY_MAPPING.md` — Belief-to-severity tiers, compound signal patterns, forbidden transitions, visual language, reason code taxonomy
4. `docs/ACCESSIBILITY_CONSTRAINTS.md` — Touch targets, typography, WCAG contrast verification, TalkBack focus order, live regions, motion constraints
5. `docs/HCI_TEST_CASES.md` — 15 persona-based test cases with 4 personas covering all actions and edge cases

### What was NOT altered
- `docs/HCI_CONTRACT.md` — already perfect, used as input reference
- `docs/decisions/ADR-004-risk-policy.md` — already perfect, used as input reference  
- `contracts/*.schema.json` — frozen
- `packages/android-sdk/` — all Kotlin files untouched
- `ml/` — all Python files untouched

### Key decisions made
- TIER_1 color corrected from `#1E88E5` to `#1565C0` for WCAG AA compliance (documented in ACCESSIBILITY_CONSTRAINTS.md)
- Timeout values set: A1=30s, A2=45s, A3=15-30s, A4=60s, A5=120s
- Escalation rule ESC-06 formalized: Once A4 is triggered, never downgrade below A4 except to A0 (full clear)
- Copy variant randomization added for A1 to reduce scammer coaching effectiveness

### Dependencies on other owners
- Sneha: Must implement UI screens per COPY_MATRIX and ACCESSIBILITY_CONSTRAINTS
- Aditya: Must wire timeout handling and re-evaluation loop per INTERVENTION_STATE_MACHINE
- Trombokendu: Must respect forbidden transitions (FT-01 through FT-06) in PolicyBandit
