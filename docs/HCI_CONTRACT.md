# Human-Computer Interaction (HCI) Contract
**Vyuha 2.0 Intervention Layer**

This document establishes the strict behavioral and cognitive boundaries for all UI interventions triggered by the Vyuha policy engine. This is a binding contract—Sneha (or any frontend developer) must implement these interventions exactly as specified to ensure they effectively interrupt coercion without causing undue friction or distress.

---

## 1. PASS (A0)
**Purpose:** Ensure zero-friction execution for definitively benign transactions.
**Cognitive principle:** Silent automation; do not habituate the user to meaningless security pop-ups.
**When allowed:** When `BeliefState` is low and `Uncertainty` is low.
**When forbidden:** If ANY high-risk signal (active call, overlay, high novelty) is present.
**Maximum words:** 0 (No UI rendered).
**Required accessibility:** N/A.
**User responses:** None.
**Escalation consequences:** None.
**Manipulation risk:** None.
**Privacy considerations:** Standard transactional privacy.

---

## 2. MICRO_PROMPT (A1)
**Purpose:** Interrupt automatic compliance during low-to-moderate risk scenarios.
**Cognitive principle:** Cognitive speedbump; shift the user from fast (System 1) to slow (System 2) thinking.
**When allowed:** Low-risk anomalies (e.g., slightly unusual time or device).
**When forbidden:** Active screen sharing, active voice call, or high absolute coercion probability.
**Maximum words:** 10.
**Required accessibility:** High-contrast text, screen-reader compatible (must read: "Security Question").
**User responses:** 
- YES
- NO
- NOT_SURE
**Escalation consequences:** If `NOT_SURE` or ignored, escalate to `REFLECTION_CHALLENGE` or `COOLING_DELAY`.
**Manipulation risk:** High. A scammer can easily instruct the victim: "Just click Yes."
**Privacy considerations:** Do not reveal specific risk flags (e.g., do not say "We detected an unusual location").

---

## 3. REFLECTION_CHALLENGE (A2)
**Purpose:** Force active cognitive processing through dynamic questioning.
**Cognitive principle:** Active friction; breaking script adherence by requiring the user to articulate intent.
**When allowed:** Moderate coercion probability; anomalous beneficiary profiles.
**When forbidden:** Extreme risk contexts where the user is already heavily panicked.
**Maximum words:** 25.
**Required accessibility:** Simple language (5th-grade reading level), prominent tap targets.
**User responses:** 
- I AM SENDING TO FAMILY/FRIENDS
- I AM PAYING A MERCHANT
- SOMEONE ASKED ME TO DOWNLOAD AN APP
- OTHER
**Escalation consequences:** If response conflicts with transaction metadata (e.g., selected "Family" but beneficiary is a known merchant aggregator), escalate to `TRUSTED_VERIFY` or `ISOLATION_BREAK`.
**Manipulation risk:** Moderate. The attacker must actively coach the user on which option to select, which buys time and creates behavioral anomalies (hesitation).
**Privacy considerations:** Must not display past transaction history in the prompt.

---

## 4. COOLING_DELAY (A3)
**Purpose:** Deplete the attacker's sense of urgency and allow victim panic to subside.
**Cognitive principle:** Urgency neutralization; breaking the temporal pressure of the scam.
**When allowed:** High-value transactions with novel beneficiaries; suspected but unconfirmed coercion.
**When forbidden:** Medical/Emergency merchant categories.
**Maximum words:** 20.
**Required accessibility:** Clear visual countdown timer.
**User responses:** 
- CANCEL TRANSACTION (Primary/Prominent)
- WAIT (Passive)
**Escalation consequences:** If the transaction is retried immediately after cancellation, trigger `ISOLATION_BREAK`.
**Manipulation risk:** Low during the delay, but attacker may maintain the connection to ensure completion after the timer.
**Privacy considerations:** Standard.

---

## 5. ISOLATION_BREAK (A4)
**Purpose:** Temporarily remove attacker-controlled communication from the decision loop.
**Cognitive principle:** Sensory isolation; severing the primary vector of psychological control.
**When allowed:** Confirmed active call, screen sharing, or remote access during a high-risk transfer.
**When forbidden:** Never forbidden if technical telemetry confirms a high-risk concurrent session.
**Maximum words:** 20.
**Required accessibility:** Blinking or highly salient visual indicator (e.g., deep red overlay), aggressive haptic feedback.
**User responses:** 
- I HAVE ENDED THE CALL (Requires technical confirmation)
- CANCEL TRANSACTION
**Escalation consequences:** If technical telemetry detects the call is NOT ended despite the user claiming it is, immediately abort transaction and trigger `TRUSTED_VERIFY`.
**Manipulation risk:** Minimal, as the technical layer enforces the disconnection of the communication channel.
**Privacy considerations:** The app must explicitly state it is reacting to an "active call" or "screen share" to maintain transparency about device telemetry.

---

## 6. TRUSTED_VERIFY (A5)
**Purpose:** Offload authorization to a rational, un-coerced third party (Safety Circle).
**Cognitive principle:** Social proof and distributed trust; removing the burden of decision from the panicked victim.
**When allowed:** Extreme coercion probability, elderly/vulnerable demographic profiles under attack, or failed `ISOLATION_BREAK`.
**When forbidden:** When the user has not opted into or configured a Safety Circle.
**Maximum words:** 30.
**Required accessibility:** Clear identification of WHO is being contacted.
**User responses:** 
- SEND VERIFICATION REQUEST
- CANCEL TRANSACTION
**Escalation consequences:** If the Trusted Contact rejects the transaction or times out, the transaction is permanently blocked.
**Manipulation risk:** The attacker may attempt to coerce the victim to call the Trusted Contact to lie, but this introduces massive friction and a secondary human into the loop.
**Privacy considerations:** The Trusted Contact will see the transaction amount and beneficiary name. This must be strictly consented to during onboarding.

---

## 7. STEP_UP_REQUIRED (A6)
**Purpose:** Fallback friction when the policy engine's confidence is insufficient (`ABSTAIN`).
**Cognitive principle:** Standard security friction; falling back to established authentication paradigms.
**When allowed:** When the RL policy lacks confidence to trigger a high-friction behavioral intervention (e.g., out-of-distribution events).
**When forbidden:** If the transaction is definitively low-risk.
**Maximum words:** 15.
**Required accessibility:** Standard biometric or PIN entry accessibility.
**User responses:** 
- AUTHENTICATE (Biometric / PIN)
- CANCEL
**Escalation consequences:** Success allows passage, failure locks the account.
**Manipulation risk:** High. Coerced users will successfully authenticate because they are the legitimate owners. This is a technical fallback, not a behavioral solution.
**Privacy considerations:** Standard authentication privacy.
