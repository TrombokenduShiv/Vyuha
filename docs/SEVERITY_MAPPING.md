> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# Severity Mapping
**Owner: Alaukik Saxena (HCI / Safety Interaction Engineer)**
**Consumers: Trombokendu (PolicyBandit thresholds), Sneha (UI severity rendering), Aditya (SDK InterventionDecision.severity)**

This document defines the formal mapping from risk signals and belief state to intervention severity levels. It bridges the gap between Trombokendu's ML output and Sneha's UI rendering.

---

## 1. Severity Tiers

| Tier | Numeric | Risk Level | Action | UI Treatment | Color | Haptic |
|---|---|---|---|---|---|---|
| **TIER_0** | 0 | NONE | A0_PASS | No UI | — | None |
| **TIER_1** | 1 | LOW | A1_MICRO_PROMPT | Inline card / bottom sheet | `#1E88E5` (Blue) | None |
| **TIER_2** | 2 | MODERATE | A2_REFLECTION_CHALLENGE | Full-height modal | `#FB8C00` (Amber) | Single short pulse |
| **TIER_3** | 3 | ELEVATED | A3_COOLING_DELAY | Full-screen overlay with timer | `#F4511E` (Deep Orange) | Double pulse |
| **TIER_4** | 4 | HIGH | A4_ISOLATION_BREAK | Full-screen blocking overlay | `#D32F2F` (Red) | Continuous pulse (3 cycles) |
| **TIER_5** | 5 | CRITICAL | A5_TRUSTED_VERIFY | Full-screen with contact verification | `#B71C1C` (Dark Red) | Long pulse |
| **TIER_6** | 6 | INSTITUTIONAL | A6_STEP_UP_REQUIRED | Host-rendered auth + Vyuha banner | `#9C27B0` (Purple) | Host-defined |

---

## 2. Belief-to-Severity Mapping

This mapping is the **contract between the PolicyBandit (Trombokendu) and the UI layer (Sneha)**.

| P(Coercion) Range | Uncertainty State | Graph State | Severity Tier | Action |
|---|---|---|---|---|
| `[0.00, 0.15)` | `{SAFE}` | Any | TIER_0 | A0_PASS |
| `[0.00, 0.15)` | `{SAFE, RISK}` or `{ABSTAIN}` | Any | TIER_1 | A1_MICRO_PROMPT |
| `[0.15, 0.30)` | Any | Available | TIER_0 | A0_PASS |
| `[0.15, 0.30)` | Any | Missing | TIER_1 | A1_MICRO_PROMPT |
| `[0.30, 0.50)` | Any | Any | TIER_2 | A2_REFLECTION_CHALLENGE |
| `[0.50, 0.65)` | Any | Any | TIER_3 | A3_COOLING_DELAY |
| `[0.65, 0.85)` | `{RISK}` or comm_active | Any | TIER_4 | A4_ISOLATION_BREAK |
| `[0.65, 0.85)` | `{SAFE, RISK}` and !comm_active | Any | TIER_3 | A3_COOLING_DELAY |
| `[0.85, 0.95)` | Any | Any | TIER_5 | A5_TRUSTED_VERIFY |
| `[0.95, 1.00]` | Any | Any | TIER_6 | A6_STEP_UP_REQUIRED |
| Any | `{ABSTAIN}` only | Any | TIER_6 | A6_STEP_UP_REQUIRED |

> [!NOTE]
> **Key design choice:** At `p ∈ [0.65, 0.85)`, the action depends on whether an active communication channel exists. If `comm_active=true`, Isolation Break (A4) is triggered to sever the scammer's control loop. If `comm_active=false`, Cooling Delay (A3) is sufficient because there's no live synchronous control.

---

## 3. Signal-to-Evidence Mapping

Each signal family contributes evidence that feeds into the belief state. This table maps raw signals to their semantic meaning in the HCI layer.

| Signal Family | Signal | Evidence Weight | Reason Code | HCI Interpretation |
|---|---|---|---|---|
| **Communication** | `comm_active = true` | HIGH | `ACTIVE_COMMUNICATION` | Scammer may be exerting live control over the victim |
| **Communication** | `comm_active = false` | NEUTRAL | — | No live voice/VoIP control channel |
| **Device Access** | `capture_risk = true` | HIGH | `SCREEN_CAPTURE_RISK` | Scammer may be observing the victim's screen |
| **Device Access** | `overlay_risk = true` | MEDIUM | `OVERLAY_DETECTED` | Potential click-jacking or instruction overlay |
| **Transaction** | `amount_bucket = CRITICAL` | MEDIUM | `HIGH_AMOUNT` | Large amount increases loss magnitude |
| **Transaction** | `beneficiary_novelty > 0.8` | MEDIUM | `NEW_BENEFICIARY` | First-time payee increases risk |
| **Baseline** | `deviation_score > 0.7` | MEDIUM | `BASELINE_DEVIATION` | Transaction is unusual for this user |
| **Graph** | `graph_risk_score > 0.7` | HIGH | `GRAPH_MULE_RISK` | Beneficiary is linked to mule patterns |
| **Graph** | `graph_missing = true` | MEDIUM | `GRAPH_UNAVAILABLE` | Institutional intelligence unavailable; uncertainty increases |
| **Interaction** | User response = `SOMEONE_DIRECTING` | VERY HIGH | `USER_REPORTS_COERCION` | Direct self-report of external instruction |
| **Interaction** | User response = `NOT_SURE` | LOW | `USER_UNCERTAIN` | Weak positive signal for confusion/pressure |
| **Interaction** | User response = `TIMEOUT` | MEDIUM | `RESPONSE_TIMEOUT` | Non-response under intervention suggests external pressure |
| **Interaction** | User response = `CANCELLED` | NEGATIVE | `USER_CANCELLED` | User voluntarily cancelled; reduces risk |

---

## 4. Compound Signal Patterns

These patterns trigger specific severity responses regardless of individual signal strength.

| Pattern | Signals Present | Minimum Severity | Rationale |
|---|---|---|---|
| **Active Coercion** | `comm_active` + `beneficiary_novelty > 0.8` + `amount_bucket ∈ {HIGH, CRITICAL}` | TIER_4 (ISOLATION_BREAK) | Classic coercion pattern: victim on call, paying new beneficiary large amount |
| **Remote Control** | `capture_risk` + `overlay_risk` | TIER_4 (ISOLATION_BREAK) | Screen control indicates remote access scam |
| **High-Risk Novel** | `beneficiary_novelty > 0.9` + `amount_bucket = CRITICAL` + `deviation_score > 0.8` | TIER_3 (COOLING_DELAY) | Very unusual transaction, even without call |
| **Graph Flagged + Call** | `graph_risk_score > 0.7` + `comm_active` | TIER_4 (ISOLATION_BREAK) | Institutional intelligence confirms risk during live call |
| **All Clear** | `comm_active=false` + `capture_risk=false` + `overlay_risk=false` + `beneficiary_novelty < 0.3` + `amount_bucket ∈ {LOW, MEDIUM}` + `graph_risk_score < 0.2` | TIER_0 (PASS) | No risk indicators present |

---

## 5. Forbidden Transitions (Hard Constraints)

These constraints are **non-negotiable** and override any policy output.

| Constraint ID | Rule | Rationale |
|---|---|---|
| `FT-01` | NEVER downgrade severity within the same session | Once risk is established, reducing friction creates a bypass vector |
| `FT-02` | NEVER issue A0_PASS if `comm_active=true` AND `amount_bucket ∈ {HIGH, CRITICAL}` | Active call + large payment must always trigger at least A1 |
| `FT-03` | NEVER issue A0_PASS if ANY uncertainty_set contains `ABSTAIN` | Model uncertainty means "I don't know" — silence is wrong |
| `FT-04` | NEVER issue A4 if `comm_active=false` AND `capture_risk=false` | Isolation Break requires something to break; otherwise use A3 |
| `FT-05` | NEVER allow A1 (MICRO_PROMPT) if `comm_active=true` AND `p_coercion > 0.5` | MICRO_PROMPT is trivially overridable under active scammer coaching |
| `FT-06` | A6 is NEVER autonomously chosen by the SDK — it is the ABSTAIN fallback or the escalation terminal | Vyuha advises; institution decides |

---

## 6. Severity Visual Language

| Tier | Background | Text Color | Icon | Animation | Sound |
|---|---|---|---|---|---|
| TIER_0 | None | — | — | — | — |
| TIER_1 | White / Surface | `#1E88E5` | Info circle | Slide up from bottom | None |
| TIER_2 | Light Amber surface | `#E65100` | Warning triangle | Fade in | Soft alert tone |
| TIER_3 | Light Red surface | `#BF360C` | Timer / clock | Scale in + countdown animation | Alert tone |
| TIER_4 | `#D32F2F` overlay (80% opacity) | White | Shield / stop hand | Pulse animation, pulsing border | Urgent alert + haptic |
| TIER_5 | `#B71C1C` overlay (90% opacity) | White | People / safety circle | Contact card slide in | Urgent alert + haptic |
| TIER_6 | `#9C27B0` overlay | White | Lock / institution | Host-defined | Host-defined |

---

## 7. Reason Code Taxonomy

Complete taxonomy of reason codes that can appear in `InterventionDecision.reasonCodes`:

| Category | Reason Code | Meaning |
|---|---|---|
| **Communication** | `ACTIVE_COMMUNICATION` | Call or VoIP active during payment |
| **Device** | `SCREEN_CAPTURE_RISK` | Capture/mirroring detected |
| **Device** | `OVERLAY_DETECTED` | Suspicious overlay present |
| **Transaction** | `HIGH_AMOUNT` | Amount bucket is HIGH or CRITICAL |
| **Transaction** | `NEW_BENEFICIARY` | Beneficiary novelty > threshold |
| **Transaction** | `BASELINE_DEVIATION` | Deviation from user's personal baseline |
| **Graph** | `GRAPH_MULE_RISK` | Beneficiary linked to mule topology |
| **Graph** | `GRAPH_HIGH_FAN_IN` | Beneficiary receiving from many sources |
| **Graph** | `GRAPH_UNAVAILABLE` | Graph service unreachable or token expired |
| **Belief** | `MODERATE_BELIEF` | p_coercion in moderate range |
| **Belief** | `ELEVATED_BELIEF` | p_coercion in elevated range |
| **Belief** | `HIGH_COERCION_BELIEF` | p_coercion in high range |
| **Belief** | `VERY_HIGH_COERCION_BELIEF` | p_coercion > 0.85 |
| **Belief** | `EXTREME_COERCION_BELIEF` | p_coercion > 0.95 |
| **Uncertainty** | `CONFORMAL_RISK_ONLY` | Prediction set = {RISK} only |
| **Uncertainty** | `CONFORMAL_AMBIGUOUS` | Prediction set includes multiple labels |
| **Uncertainty** | `MODEL_ABSTAIN` | Model confidence insufficient |
| **Interaction** | `USER_REPORTS_COERCION` | User selected "someone asked me" |
| **Interaction** | `USER_UNCERTAIN` | User selected "not sure" |
| **Interaction** | `RESPONSE_TIMEOUT` | User didn't respond within timeout |
| **Interaction** | `LIED_ABOUT_CALL` | User claimed call ended but comm_active=true |
| **Escalation** | `ESCALATED_FROM_A1` | Escalated from MICRO_PROMPT |
| **Escalation** | `ESCALATED_FROM_A3` | Escalated from COOLING (rapid retry) |
| **Escalation** | `ESCALATED_FROM_A4` | Escalated from ISOLATION_BREAK (lie/timeout) |
| **Escalation** | `SAFETY_CIRCLE_TIMEOUT` | Trusted contact didn't respond |
