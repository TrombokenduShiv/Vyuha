> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# HCI Test Cases
**Owner: Alaukik Saxena (HCI / Safety Interaction Engineer)**
**Executor: Sneha (UI), Aditya (SDK), Trombokendu (policy integration)**

This document defines persona-based acceptance test cases for all Vyuha intervention flows. Each test case specifies preconditions, input signals, expected intervention, expected user behavior, and pass/fail criteria.

---

## 1. Persona Definitions

### P1 — Rekha (65, Retired Teacher, Low Digital Literacy)
- **Profile:** Receives pension via direct deposit. Uses UPI only for household bills. Has a grandson who set up her phone.
- **Vulnerability:** High susceptibility to authority-based scams ("digital arrest"). Slow reader. Will follow instructions from anyone who sounds official.
- **Device:** Budget Android (API 28), large font enabled (1.5×), no accessibility services.
- **Financial Behavior:** Small transactions (₹500–₹3,000). Same 5 payees monthly. Never sends >₹5,000.

### P2 — Arjun (28, Software Engineer, High Digital Literacy)
- **Profile:** Frequent UPI user. 50+ transactions/month. Sends money to friends, merchants, and subscriptions.
- **Vulnerability:** Low susceptibility to standard scams. May be vulnerable to sophisticated social engineering or investment fraud.
- **Device:** Flagship Android (API 34), default settings.
- **Financial Behavior:** Variable amounts (₹100–₹50,000). Mix of known and new payees. Occasional large transfers.

### P3 — Meera (45, Small Business Owner, Medium Digital Literacy)
- **Profile:** Manages inventory payments via UPI. Regularly pays new suppliers.
- **Vulnerability:** Medium. Time pressure from business operations. May dismiss warnings as "annoying" if too frequent.
- **Device:** Mid-range Android (API 30), default settings.
- **Financial Behavior:** Regular large payments (₹10,000–₹80,000) to new payees. High novelty score is normal.

### P4 — Vikram (72, Retired Army Officer, Medium Digital Literacy)
- **Profile:** Fixed pension income. Cautious but trusts authority figures. Uses TalkBack due to low vision.
- **Vulnerability:** Very high susceptibility to authority scams. TalkBack user — accessibility MUST work.
- **Device:** Mid-range Android (API 29), TalkBack enabled, large font (2.0×).
- **Financial Behavior:** Small, predictable transactions. Any large amount is highly anomalous.

---

## 2. Test Scenarios

---

### HCI-TC-01: Benign Payment — Silent Pass (P2: Arjun)

**Scenario:** Regular payment to a known payee, no risk signals.

| Field | Value |
|---|---|
| **Persona** | P2 (Arjun) |
| **Precondition** | SDK initialized, graph available |
| **Transaction** | ₹2,500 to known merchant (`zomato@paytm`) |
| **Signals** | `comm_active=false`, `capture_risk=false`, `overlay_risk=false` |
| **Expected Belief** | `p_coercion < 0.10` |
| **Expected Action** | `A0_PASS` |
| **Expected UI** | NONE — payment proceeds without interruption |
| **Pass Criteria** | ✅ No UI rendered. Latency <50ms. Audit trace shows A0_PASS with reason codes empty. |
| **Fail Criteria** | ❌ Any intervention screen appears. |
| **HCI Principle Tested** | Silent automation; anti-habituation. |

---

### HCI-TC-02: Micro-Prompt — Low Risk Anomaly (P3: Meera)

**Scenario:** Slightly unusual payment, no strong risk signals. Tests that A1 is non-intrusive.

| Field | Value |
|---|---|
| **Persona** | P3 (Meera) |
| **Precondition** | SDK initialized, graph available |
| **Transaction** | ₹8,000 to slightly new payee (novelty=0.6) |
| **Signals** | `comm_active=false`, `capture_risk=false`, `deviation_score=0.4` |
| **Expected Belief** | `0.15 < p_coercion < 0.30` |
| **Expected Action** | `A1_MICRO_PROMPT` |
| **Expected UI** | Bottom sheet with: "Is someone directing this payment right now?" |
| **User Action** | Taps "No, this is my choice" |
| **Expected Re-evaluation** | `A0_PASS` (response reduces belief) |
| **Pass Criteria** | ✅ Bottom sheet appears. Tapping "No" dismisses and allows payment. Total friction <5 seconds. |
| **Fail Criteria** | ❌ Full-screen modal appears (over-friction). ❌ Payment blocked after "No" response. |
| **HCI Principle Tested** | Cognitive speedbump; System 1 → System 2 shift. |

---

### HCI-TC-03: Micro-Prompt Escalation — NOT_SURE Response (P1: Rekha)

**Scenario:** Low-risk anomaly, user responds "Not Sure" — tests escalation.

| Field | Value |
|---|---|
| **Persona** | P1 (Rekha) |
| **Precondition** | SDK initialized, graph shows moderate risk |
| **Transaction** | ₹15,000 to new payee |
| **Signals** | `comm_active=false`, `deviation_score=0.5` |
| **Expected Action** | `A1_MICRO_PROMPT` |
| **User Action** | Taps "I'm not sure" |
| **Expected Escalation** | Re-evaluate → `A2_REFLECTION_CHALLENGE` or `A3_COOLING_DELAY` |
| **Pass Criteria** | ✅ NOT_SURE triggers escalation. Higher-friction screen appears. |
| **Fail Criteria** | ❌ Payment proceeds after "Not Sure" without escalation. |
| **HCI Principle Tested** | ESC-04: Uncertainty is weak positive evidence. |

---

### HCI-TC-04: Reflection Challenge — Conflict Detection (P2: Arjun)

**Scenario:** User claims "Sending to family" but beneficiary is flagged merchant aggregator.

| Field | Value |
|---|---|
| **Persona** | P2 (Arjun) |
| **Precondition** | SDK initialized, graph flags beneficiary as merchant |
| **Transaction** | ₹25,000 to new payee (graph_risk=0.6) |
| **Expected Action** | `A2_REFLECTION_CHALLENGE` |
| **Expected UI** | Shows ₹25,000 amount and payee name. 4 options displayed. |
| **User Action** | Selects "Sending to family or friends" |
| **Expected Re-evaluation** | Metadata conflict (user says family, graph says merchant) → escalate to `A4` or `A5` |
| **Pass Criteria** | ✅ Conflict between user response and metadata triggers escalation. ✅ Amount and payee name visible in reflection screen. |
| **Fail Criteria** | ❌ Payment proceeds without conflict check. ❌ Amount or payee name not shown. |
| **HCI Principle Tested** | Active friction; script-breaking through articulation. |

---

### HCI-TC-05: Cooling Delay — Timer and Safety Message (P1: Rekha)

**Scenario:** High-value payment to novel beneficiary, suspected coercion. Tests cooldown UX.

| Field | Value |
|---|---|
| **Persona** | P1 (Rekha) |
| **Precondition** | Belief state elevated, no active communication |
| **Transaction** | ₹40,000 to new payee |
| **Signals** | `comm_active=false`, `deviation_score=0.8`, `beneficiary_novelty=0.9` |
| **Expected Action** | `A3_COOLING_DELAY` |
| **Expected UI** | Full-screen countdown (30s), safety message, Cancel prominent |
| **Pass Criteria** | ✅ Timer counts down visually. ✅ Safety message visible: "Real banks and police will never ask you to transfer money on a call." ✅ Continue button disabled until timer=0. ✅ Cancel button active throughout. |
| **Fail Criteria** | ❌ Timer skippable. ❌ Continue enabled before timer=0. ❌ Safety message missing. |
| **HCI Principle Tested** | Urgency neutralization; temporal pressure break. |

---

### HCI-TC-06: Cooling Delay — Rapid Retry Escalation (P2: Arjun)

**Scenario:** User cancels during cooldown, retries within 30 seconds.

| Field | Value |
|---|---|
| **Persona** | P2 (Arjun) |
| **Precondition** | A3 triggered, user cancelled, retries same payment within 30s |
| **Expected Behavior** | Rapid retry triggers ESC-01 → `A4_ISOLATION_BREAK` |
| **Pass Criteria** | ✅ Second attempt escalates to A4 (not A3 again). ✅ Reason code includes `ESCALATED_FROM_A3`. |
| **Fail Criteria** | ❌ Same A3 cooldown shown again (no escalation). |
| **HCI Principle Tested** | ESC-01: Rapid retry = scammer maintaining control. |

---

### HCI-TC-07: Isolation Break — Active Call (P1: Rekha)

**Scenario:** Classic "digital arrest" scam. Victim on call, sending large amount to unknown payee.

| Field | Value |
|---|---|
| **Persona** | P1 (Rekha) |
| **Precondition** | Active phone call, high graph risk |
| **Transaction** | ₹75,000 to unknown VPA |
| **Signals** | `comm_active=true`, `capture_risk=false`, `deviation_score=0.95`, `graph_risk=0.85` |
| **Expected Action** | `A4_ISOLATION_BREAK` |
| **Expected UI** | Red overlay: "Active Call Detected." Status: "📞 Call in progress." |
| **User Action 1** | Taps "I Have Ended the Call" but `comm_active` still true |
| **Expected Behavior** | Verification failure message: "We can still detect an active call." → Escalation to A5 |
| **User Action 2** | Actually ends call. `comm_active` → false |
| **Expected Behavior** | Status updates to "✅ Call ended." Re-evaluate → belief drops → may allow or show lower friction |
| **Pass Criteria** | ✅ Transaction CANNOT proceed while call is active. ✅ Lie detection works. ✅ UI updates when call ends. ✅ Haptic feedback fires. |
| **Fail Criteria** | ❌ Payment proceeds despite active call. ❌ User claim accepted without verification. |
| **HCI Principle Tested** | Sensory isolation; control loop severance. |

---

### HCI-TC-08: Isolation Break — Screen Sharing (P2: Arjun)

**Scenario:** Remote access scam. Screen sharing active during high-value payment.

| Field | Value |
|---|---|
| **Persona** | P2 (Arjun) |
| **Precondition** | Screen capture detected |
| **Transaction** | ₹30,000 to new payee |
| **Signals** | `comm_active=false`, `capture_risk=true`, `overlay_risk=true` |
| **Expected Action** | `A4_ISOLATION_BREAK` |
| **Expected UI** | "You are sending money while sharing your screen. For your safety, please stop sharing before continuing." Status: "🖥️ Screen sharing active" |
| **Pass Criteria** | ✅ Screen sharing variant of A4 body text displayed. ✅ Status indicator reflects capture_risk. |
| **Fail Criteria** | ❌ Call-specific text shown instead of screen-sharing text. |
| **HCI Principle Tested** | Remote access scam detection + remediation. |

---

### HCI-TC-09: Trusted Verify — Safety Circle (P4: Vikram)

**Scenario:** Extreme coercion probability. Safety Circle invoked for elderly victim with TalkBack.

| Field | Value |
|---|---|
| **Persona** | P4 (Vikram) — TalkBack enabled, 2.0× font |
| **Precondition** | Belief state very high, Safety Circle configured |
| **Transaction** | ₹1,00,000 to unknown VPA |
| **Expected Action** | `A5_TRUSTED_VERIFY` |
| **Expected UI** | Contact selection: Primary + Secondary + Bank. TalkBack reads all elements. |
| **Pass Criteria** | ✅ TalkBack reads heading, body, all contacts, and buttons in correct order. ✅ Touch targets ≥ 48dp at 2.0× font. ✅ Contact notification contains ONLY: "{user_name} requested safety verification for a high-value payment." ✅ No amount or payee in notification. |
| **Fail Criteria** | ❌ TalkBack traversal broken. ❌ Touch targets too small at large font. ❌ Contact notification reveals amount. |
| **HCI Principle Tested** | Social proof; distributed trust; accessibility under duress. |

---

### HCI-TC-10: Trusted Verify — Contact Timeout → A6 Escalation (P1: Rekha)

**Scenario:** Safety Circle contact doesn't respond within 120s.

| Field | Value |
|---|---|
| **Persona** | P1 (Rekha) |
| **Precondition** | A5 triggered, notification sent |
| **Expected Behavior** | 120s timeout → `A6_STEP_UP_REQUIRED` |
| **Expected UI** | Timeout message: "Your contact hasn't responded yet. Escalating to your bank for verification." |
| **Pass Criteria** | ✅ After 120s, A6 screen appears. ✅ Reason code includes `SAFETY_CIRCLE_TIMEOUT`. |
| **Fail Criteria** | ❌ System waits indefinitely. ❌ Transaction proceeds without bank step-up. |
| **HCI Principle Tested** | ESC-03: Contact timeout → institution fallback. |

---

### HCI-TC-11: Offline Mode — Graph Unavailable (P3: Meera)

**Scenario:** Graph service is down. Local pipeline continues with increased uncertainty.

| Field | Value |
|---|---|
| **Persona** | P3 (Meera) |
| **Precondition** | Graph service unreachable (network down) |
| **Transaction** | ₹15,000 to new payee |
| **Signals** | `comm_active=false`, `graph_missing=true` |
| **Expected Action** | At least `A1_MICRO_PROMPT` (graph missing escalates from what would otherwise be A0) |
| **Pass Criteria** | ✅ Transaction NOT silently passed. ✅ Reason code includes `GRAPH_UNAVAILABLE`. ✅ Debug trace shows `graph_missing=true`. |
| **Fail Criteria** | ❌ Graph unavailability results in A0_PASS with no friction (missing evidence treated as safe). |
| **HCI Principle Tested** | Fail-safe: missing evidence ≠ safe. |

---

### HCI-TC-12: Timeout Escalation — No User Response (P1: Rekha)

**Scenario:** A1 displayed, user does not interact for 30 seconds.

| Field | Value |
|---|---|
| **Persona** | P1 (Rekha) |
| **Precondition** | A1_MICRO_PROMPT displayed |
| **User Action** | None (30s timeout) |
| **Expected Behavior** | Timeout → record TIMEOUT → re-evaluate → escalate by +1 severity |
| **Pass Criteria** | ✅ After 30s, higher-friction screen appears. ✅ Reason code includes `RESPONSE_TIMEOUT`. |
| **Fail Criteria** | ❌ Screen remains indefinitely. ❌ Payment proceeds after timeout. |
| **HCI Principle Tested** | ESC-05: Inaction under warning = external pressure signal. |

---

### HCI-TC-13: Step-Up Required — ABSTAIN (P3: Meera)

**Scenario:** Model confidence is too low for any behavioral intervention.

| Field | Value |
|---|---|
| **Persona** | P3 (Meera) |
| **Precondition** | Out-of-distribution event; uncertainty_set = {ABSTAIN} |
| **Transaction** | ₹60,000 to payee with sparse graph data |
| **Expected Action** | `A6_STEP_UP_REQUIRED` |
| **Expected UI** | "Additional Verification Required. Your bank requires additional verification." |
| **Pass Criteria** | ✅ A6 rendered (not a lower action). ✅ Reason code includes `MODEL_ABSTAIN`. |
| **Fail Criteria** | ❌ ABSTAIN results in PASS (model doesn't know → assumes safe). |
| **HCI Principle Tested** | FT-03: ABSTAIN ≠ SAFE. |

---

### HCI-TC-14: Forbidden Transition — Downgrade Attempt (P2: Arjun)

**Scenario:** After A4 (ISOLATION_BREAK), communication ends. Re-evaluation should NOT drop to A1.

| Field | Value |
|---|---|
| **Persona** | P2 (Arjun) |
| **Precondition** | A4 was triggered. User ends call. comm_active → false. |
| **Expected Behavior** | Re-evaluate → may return A0, A2, A3, but NEVER A1 (below A4 minimum except A0 PASS) |
| **Pass Criteria** | ✅ Post-isolation re-evaluation does NOT issue A1 (per ESC-06: never downgrade BELOW A4 except to A0 if fully clear). |
| **Fail Criteria** | ❌ A1_MICRO_PROMPT issued after A4 was triggered. |
| **HCI Principle Tested** | FT-01: No severity downgrade within session. ESC-06: A4 floor. |

---

### HCI-TC-15: Accessibility — TalkBack Full Flow (P4: Vikram)

**Scenario:** Complete payment flow with TalkBack enabled, ending in A4 intervention.

| Field | Value |
|---|---|
| **Persona** | P4 (Vikram) — TalkBack + 2.0× font |
| **Transaction** | ₹50,000 to unknown VPA, active call |
| **Expected Action** | `A4_ISOLATION_BREAK` |
| **Pass Criteria** | ✅ Every element announced in correct order (per ACCESSIBILITY_CONSTRAINTS §5). ✅ Dynamic state changes (call ended) announced via live region. ✅ All buttons operable via TalkBack gestures. ✅ Text readable at 2.0× font (no truncation). ✅ No accessibility service flagged as device risk. |
| **Fail Criteria** | ❌ Any element not announced. ❌ Incorrect traversal order. ❌ Button not activatable via TalkBack. |
| **HCI Principle Tested** | Full accessibility compliance under coercion scenario. |

---

## 3. Test Execution Matrix

| Test ID | Persona | Action | Priority | Status |
|---|---|---|---|---|
| HCI-TC-01 | P2 | A0 PASS | P0 | ☐ Not Started |
| HCI-TC-02 | P3 | A1 MICRO_PROMPT | P0 | ☐ Not Started |
| HCI-TC-03 | P1 | A1 → A2/A3 (escalation) | P0 | ☐ Not Started |
| HCI-TC-04 | P2 | A2 REFLECTION (conflict) | P1 | ☐ Not Started |
| HCI-TC-05 | P1 | A3 COOLING_DELAY | P0 | ☐ Not Started |
| HCI-TC-06 | P2 | A3 → A4 (rapid retry) | P1 | ☐ Not Started |
| HCI-TC-07 | P1 | A4 ISOLATION_BREAK (call) | P0 | ☐ Not Started |
| HCI-TC-08 | P2 | A4 ISOLATION_BREAK (screen) | P1 | ☐ Not Started |
| HCI-TC-09 | P4 | A5 TRUSTED_VERIFY + TalkBack | P0 | ☐ Not Started |
| HCI-TC-10 | P1 | A5 → A6 (timeout) | P1 | ☐ Not Started |
| HCI-TC-11 | P3 | OFFLINE (graph missing) | P0 | ☐ Not Started |
| HCI-TC-12 | P1 | TIMEOUT escalation | P1 | ☐ Not Started |
| HCI-TC-13 | P3 | A6 ABSTAIN | P1 | ☐ Not Started |
| HCI-TC-14 | P2 | Forbidden downgrade | P1 | ☐ Not Started |
| HCI-TC-15 | P4 | Full TalkBack flow | P1 | ☐ Not Started |

---

## 4. Demo Scenario Recommendations

For the 7-minute judge presentation + 3-minute Q&A:

**Primary Demo Flow (MUST show):**
1. **HCI-TC-01** → Benign payment, silent pass (10 seconds)
2. **HCI-TC-07** → Full "digital arrest" scam interception (90 seconds)
   - Show call active → A4 → lie detection → A5 → contact verify → resolution

**Secondary Demo (Show if time):**
3. **HCI-TC-11** → Offline mode resilience (30 seconds)
4. **HCI-TC-05** → Cooling delay for high-value anomaly (30 seconds)

**Judge Q&A Preparation:**
- "What if the user lies?" → HCI-TC-07 (lie detection via telemetry)
- "What about accessibility?" → HCI-TC-15 (TalkBack flow)
- "What if your model is wrong?" → HCI-TC-13 (ABSTAIN → A6)
- "What about false positives?" → HCI-TC-02 (low friction, quick dismiss)
- "What if the graph is down?" → HCI-TC-11 (offline resilience)
