/**
 * Vyuha 2.0 — Public API Data Classes
 * ======================================
 * These are the ONLY types a host bank app should ever see.
 * All ML internals (ContextSnapshot, BeliefState, Pipeline, TriageGate, etc.)
 * are hidden behind these clean, bank-friendly contracts.
 *
 * Design principle: A fintech engineer who has never heard of
 * "log-odds Bayesian belief update" should be able to integrate this SDK
 * by reading these data classes alone.
 */
package com.vyuha.sdk

import com.vyuha.sdk.contracts.ActionId

// ── What the bank app tells the SDK ─────────────────────────────────

/**
 * Transaction details provided by the host bank app.
 * The SDK handles all hashing and bucketing internally.
 *
 * Usage:
 *   val tx = TransactionInput(
 *       payeeVpa = "merchant@upi",
 *       amountInr = 5000.0,
 *       payeeName = "Ravi Kumar"
 *   )
 */
data class TransactionInput(
    /** Raw beneficiary VPA (e.g., "ravi@okaxis"). SDK hashes it internally. */
    val payeeVpa: String,
    /** Transaction amount in INR. SDK bucketizes internally. */
    val amountInr: Double,
    /** Display name of the payee (optional, for UI only). */
    val payeeName: String? = null,
    /** Payment channel. */
    val channel: String = "UPI",
    /** Supplied by institution history, never inferred from a hash. */
    val beneficiaryNovelty: Double = 1.0,
    val onlinePurchase: Boolean = false
)

/**
 * Real-time device and communication signals observed by the host app.
 * Updated continuously during the payment flow.
 *
 * Usage:
 *   session.observe(DeviceSignals(isOnCall = true, isScreenShared = true))
 */
data class DeviceSignals(
    /** Whether the user is currently on a phone/VoIP call. */
    val isOnCall: Boolean = false,
    /** Whether screen capture/mirroring is active. */
    val isScreenShared: Boolean = false,
    /** Whether a suspicious overlay is detected. */
    val hasOverlay: Boolean = false
)

// ── What the SDK tells the bank app ─────────────────────────────────

/**
 * The SDK's risk assessment for the current transaction.
 * This is the ONLY output the bank app needs to act on.
 */
data class RiskVerdict(
    /** If true, the transaction is safe to proceed. No intervention needed. */
    val shouldProceed: Boolean,
    /** Type of intervention required (null if shouldProceed is true). */
    val interventionType: InterventionType? = null,
    /** Short human-readable title for the intervention UI. */
    val title: String = "",
    /** Longer explanation for the user. */
    val message: String = "",
    /** Risk reason codes for audit/logging (optional). */
    val reasonCodes: List<String> = emptyList(),
    /** Internal belief score [0,1] for debug display. Not for business logic. */
    val debugBeliefScore: Double = 0.0
)

/**
 * Types of intervention the SDK can request.
 * Maps 1:1 to the internal ActionId enum but hides the A0-A6 naming.
 */
enum class InterventionType {
    /** A1: Brief "are you sure?" prompt. */
    PROMPT,
    /** A2: Reflective challenge question. */
    CHALLENGE,
    /** A3: Forced cooling delay before proceeding. */
    DELAY,
    /** A4: Must disconnect active call/screen share before proceeding. */
    CALL_BLOCK,
    /** A5: Requires approval from a trusted contact. */
    VERIFY_CONTACT,
    /** A6: Transaction blocked — contact bank. */
    HARD_BLOCK;

    companion object {
        /** Map internal ActionId to public InterventionType. */
        fun fromActionId(actionId: ActionId): InterventionType? = when (actionId) {
            ActionId.A0_PASS -> null
            ActionId.A1_MICRO_PROMPT -> PROMPT
            ActionId.A2_REFLECTION_CHALLENGE -> CHALLENGE
            ActionId.A3_COOLING_DELAY -> DELAY
            ActionId.A4_ISOLATION_BREAK -> CALL_BLOCK
            ActionId.A5_TRUSTED_VERIFY -> VERIFY_CONTACT
            ActionId.A6_STEP_UP_REQUIRED -> HARD_BLOCK
        }
    }
}

// ── What the bank app tells the SDK after the flow ends ─────────────

/**
 * Outcome of the payment flow, reported by the host app.
 */
data class PaymentOutcome(
    /** Whether the payment was completed (true) or cancelled (false). */
    val completed: Boolean,
    /** Whether the user approved/acknowledged an intervention. */
    val userApprovedIntervention: Boolean = false
)
