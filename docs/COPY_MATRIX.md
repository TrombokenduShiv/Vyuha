> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# Intervention Copy Matrix
**Owner: Alaukik Saxena (HCI / Safety Interaction Engineer)**
**Consumers: Sneha (Jetpack Compose UI), Aditya (string resources/SDK)**

This document is the **single source of truth** for all user-facing text in Vyuha interventions. Every word has been chosen to satisfy HCI_CONTRACT.md word limits, cognitive design principles, and privacy constraints.

**Rules:**
1. All copy MUST be used verbatim by the UI implementation.
2. Copy MUST NOT reveal internal risk flags, model scores, or technical details.
3. Copy MUST NOT mention "fraud," "scam," or "suspicious" unless in A5/A6 institutional context.
4. Language target: 5th-grade reading level (Flesch-Kincaid Grade ≤ 5).
5. All text must work with screen readers (TalkBack content descriptions provided separately in ACCESSIBILITY_CONSTRAINTS.md).

---

## A0 — PASS

**UI Rendered:** None.
**Copy:** None.
**Rationale:** Silent pass. Any UI here habituates users to ignore real warnings.

---

## A1 — MICRO_PROMPT

**Word Limit:** 10 words (per HCI_CONTRACT.md §2)

### Heading
> **Quick Check**

### Body (Primary Variant)
> Is someone directing this payment right now?

### Response Buttons
| Button | Label | Accessibility Label |
|---|---|---|
| Primary (Neutral) | **No, this is my choice** | "No, this is my choice. Tap to continue." |
| Secondary (Concern) | **I'm not sure** | "I'm not sure. Tap for help." |
| Tertiary (Danger) | **Yes, someone asked me to** | "Yes, someone asked me to pay. Tap for help." |

### Alternate Copy Variants (Randomized per TRD §12.2)
The policy engine MAY select from approved variants to reduce scammer coaching effectiveness:

| Variant ID | Body Text |
|---|---|
| `A1_V1` | Is someone directing this payment right now? |
| `A1_V2` | Are you making this payment on your own? |
| `A1_V3` | Is anyone asking you to send this money? |

> [!WARNING]
> **Manipulation Risk:** HIGH. A scammer can instruct "Just tap the first button." Randomized variant selection and button order shuffling (where accessible) reduce coaching effectiveness.

---

## A2 — REFLECTION_CHALLENGE

**Word Limit:** 25 words (per HCI_CONTRACT.md §3)

### Heading
> **Take a Moment**

### Body
> You're about to send **₹{amount}** to **{payee_name}**. Why are you making this payment?

### Response Options
| Option | Label | Risk Signal |
|---|---|---|
| 1 | **Sending to family or friends** | Low — but metadata conflict triggers escalation |
| 2 | **Paying a merchant or business** | Low — but novel beneficiary conflict triggers escalation |
| 3 | **Someone asked me to send this** | High — strong self-report of external instruction |
| 4 | **I'd rather not say** | Medium — refusal is weak coercion evidence |

### Cancel Button
> **Cancel Payment**

### Escalation Logic
- If user selects **Option 3** → record `SOMEONE_DIRECTING`, re-evaluate (belief spike)
- If user selects **Option 1** but beneficiary is flagged merchant aggregator → conflict escalation
- If user selects **Option 4** → record `REFUSAL`, increase uncertainty
- If timeout (45s) → record `TIMEOUT`, escalate

> [!WARNING]
> Body text includes `{amount}` and `{payee_name}` — these are intentional. The reflection challenge REQUIRES showing the recipient to break automatic compliance. This is NOT a privacy violation — the user already entered these values. Privacy constraint: Do NOT show past transaction history.

---

## A3 — COOLING_DELAY

**Word Limit:** 20 words (per HCI_CONTRACT.md §4)

### Heading
> **Pause Before You Pay**

### Body
> This payment needs a brief moment. Take a breath and think — is this really what you want to do?

### Timer Display
> **{seconds} seconds remaining**

### Safety Message (Shown During Countdown)
> Real banks and police will never ask you to transfer money on a call.

### Buttons
| Button | Label | State |
|---|---|---|
| Primary (Prominent) | **Cancel Payment** | Always active |
| Secondary (Passive) | **Continue After Timer** | Disabled until countdown = 0 |

### Post-Timer State
When countdown reaches 0:
- "Continue After Timer" button becomes active
- Body text changes to: > **Time's up. Are you sure you want to continue?**

> [!NOTE]
> **Cooldown Duration:** Configurable per policy. Default: 15 seconds for MEDIUM amounts, 30 seconds for HIGH/CRITICAL amounts. Never less than 15 seconds.

---

## A4 — ISOLATION_BREAK

**Word Limit:** 20 words (per HCI_CONTRACT.md §5)

### Heading
> **Active Call Detected**

### Body (Call Active)
> You are sending money while on a call. For your safety, please end the call before continuing.

### Body (Screen Sharing Active)
> You are sending money while sharing your screen. For your safety, please stop sharing before continuing.

### Body (Both Active)
> You are sending money while on a call and sharing your screen. Please end both before continuing.

### Status Indicator
| Signal | Display | Icon |
|---|---|---|
| Call active | **📞 Call in progress** (red) | Phone icon, pulsing |
| Screen sharing active | **🖥️ Screen sharing active** (red) | Monitor icon, pulsing |
| Call ended (verified) | **✅ Call ended** (green) | Checkmark |
| Screen sharing ended | **✅ Sharing stopped** (green) | Checkmark |

### Buttons
| Button | Label | Guard |
|---|---|---|
| Primary | **I Have Ended the Call** | Enabled always; SDK verifies via `comm_active` |
| Secondary | **Cancel Payment** | Always active |

### Verification Failure Text
If user taps "I Have Ended the Call" but `comm_active` is still `true`:
> **We can still detect an active call. Please fully disconnect before continuing.**

### Escalation Text (After Verification Failure)
> **For your protection, we're requesting verification from a trusted contact.**

> [!CAUTION]
> **This is NOT a warning.** Isolation Break is a remediation gate. The transaction CANNOT proceed until the communication channel is closed. This is Vyuha's core differentiator — not "we warned you," but "we broke the control loop."

---

## A5 — TRUSTED_VERIFY (Safety Circle)

**Word Limit:** 30 words (per HCI_CONTRACT.md §6)

### Heading
> **Verify with Someone You Trust**

### Body
> This payment has been flagged for elevated risk. We'd like a trusted contact to confirm you're safe before proceeding.

### Contact Selection
> **Choose a contact to verify with:**

| Contact | Display |
|---|---|
| Primary | **{contact_name}** (Primary Contact) |
| Secondary | **{contact_name}** (Secondary Contact) |
| Bank Fallback | **Contact your bank's fraud helpline** |

### Status Display (After Contact Notified)
> **Waiting for {contact_name} to respond...**

### Contact Notification Text (Minimal Disclosure — HCI_CONTRACT.md §6)
> **{user_name} requested safety verification for a high-value payment. Can you confirm they are safe?**

### Contact Response Options
| Option | Label |
|---|---|
| Confirm Safe | **Yes, they are safe** |
| Flag Concern | **I'm concerned — please check** |
| Cannot Verify | **I can't verify right now** |

### Timeout Text (120s)
> **Your contact hasn't responded yet. Escalating to your bank for verification.**

### Buttons (User Side)
| Button | Label |
|---|---|
| Primary | **Send Verification Request** |
| Secondary | **Cancel Payment** |

> [!WARNING]
> **Privacy Constraint:** The contact notification MUST NOT include: transaction amount, beneficiary name/VPA, transaction history, or location data — UNLESS the user has explicitly consented to expanded disclosure during Safety Circle enrollment.

---

## A6 — STEP_UP_REQUIRED

**Word Limit:** 15 words (per HCI_CONTRACT.md §7)

### Heading
> **Additional Verification Required**

### Body
> Your bank requires additional verification for this transaction. Please complete the step below.

### Buttons
| Button | Label |
|---|---|
| Primary | **Verify Now** |
| Secondary | **Cancel Payment** |

### Post-Auth Success
> **Verification successful. You may now complete your payment.**

### Post-Auth Failure
> **Verification failed. This transaction has been blocked. Contact your bank if you need assistance.**

> [!WARNING]
> **Manipulation Risk:** HIGH. Coerced users WILL successfully authenticate because they are the legitimate owner. A6 is a technical fallback, not a behavioral solution. It exists for ABSTAIN/OOD cases where Vyuha's model confidence is insufficient.

---

## Copy Versioning

| Field | Value |
|---|---|
| **Version** | `copy-v1.0` |
| **Language** | English (India) |
| **Flesch-Kincaid Grade** | Target ≤ 5 for all body text |
| **Last Updated** | 2026-09-14 |
| **Owner** | Alaukik Saxena |

---

## String Resource Mapping (for Sneha / Android)

All copy SHOULD be externalized to `res/values/strings_vyuha_interventions.xml` using these resource keys:

```
vyuha_a1_heading
vyuha_a1_body_v1
vyuha_a1_body_v2
vyuha_a1_body_v3
vyuha_a1_btn_no
vyuha_a1_btn_unsure
vyuha_a1_btn_yes
vyuha_a2_heading
vyuha_a2_body            (parameterized: {amount}, {payee_name})
vyuha_a2_opt_family
vyuha_a2_opt_merchant
vyuha_a2_opt_someone
vyuha_a2_opt_rather_not
vyuha_a2_btn_cancel
vyuha_a3_heading
vyuha_a3_body
vyuha_a3_timer_format    (parameterized: {seconds})
vyuha_a3_safety_msg
vyuha_a3_btn_cancel
vyuha_a3_btn_continue
vyuha_a3_post_timer
vyuha_a4_heading
vyuha_a4_body_call
vyuha_a4_body_screen
vyuha_a4_body_both
vyuha_a4_status_call_active
vyuha_a4_status_screen_active
vyuha_a4_status_call_ended
vyuha_a4_status_screen_ended
vyuha_a4_btn_ended_call
vyuha_a4_btn_cancel
vyuha_a4_verify_fail
vyuha_a4_escalation
vyuha_a5_heading
vyuha_a5_body
vyuha_a5_contact_select
vyuha_a5_status_waiting   (parameterized: {contact_name})
vyuha_a5_notify_text      (parameterized: {user_name})
vyuha_a5_timeout
vyuha_a5_btn_send
vyuha_a5_btn_cancel
vyuha_a6_heading
vyuha_a6_body
vyuha_a6_btn_verify
vyuha_a6_btn_cancel
vyuha_a6_success
vyuha_a6_failure
```
