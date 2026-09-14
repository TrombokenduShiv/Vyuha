/**
 * Vyuha 2.0 — VyuhaSession (Public API)
 * ========================================
 * This is the ONLY session class a host bank app should use.
 * It wraps the internal Session and exposes a clean, ML-free interface.
 *
 * Usage:
 *   val session = Vyuha.beginPayment(TransactionInput(...))
 *   session.observe(DeviceSignals(isOnCall = true))
 *   val verdict = session.evaluate()
 *   if (!verdict.shouldProceed) { /* show intervention UI */ }
 *   session.recordOutcome(PaymentOutcome(completed = true))
 */
package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import java.security.MessageDigest

class VyuhaSession internal constructor(
    private val internal: Session,
    private val transactionInput: TransactionInput
) {
    /** Unique session identifier for audit/logging. */
    val sessionId: String get() = internal.sessionId

    /** The payee display name (from TransactionInput). */
    val payeeName: String? get() = transactionInput.payeeName

    /** The raw amount (from TransactionInput). */
    val amountInr: Double get() = transactionInput.amountInr

    /**
     * Update real-time device/communication signals.
     * Call this whenever signal state changes (e.g., user starts/ends a call).
     *
     * The SDK uses these signals internally for risk assessment.
     * The host app does NOT need to understand what happens with these signals.
     */
    fun observe(signals: DeviceSignals) {
        internal.updateContext(
            communicationActive = signals.isOnCall,
            captureRisk = signals.isScreenShared,
            overlayRisk = signals.hasOverlay
        )
    }

    /**
     * Evaluate the current transaction risk.
     *
     * @return A [RiskVerdict] telling the bank app whether to proceed
     *         or show an intervention screen.
     */
    fun evaluate(): RiskVerdict {
        val decision = internal.evaluate()
        return mapDecisionToVerdict(decision)
    }

    /**
     * Record the user's response to an intervention, then re-evaluate.
     *
     * Call this after the user has responded to an intervention
     * (e.g., ended their call, verified with a contact).
     *
     * @return A new [RiskVerdict] after re-evaluation.
     */
    fun respondToIntervention(): RiskVerdict {
        val decision = internal.recordResponse(UserResponse.CONTINUED)
        return mapDecisionToVerdict(decision)
    }

    /**
     * Record the final outcome of the payment flow.
     * Cleans up the session and sends audit data.
     */
    fun recordOutcome(outcome: PaymentOutcome) {
        val status = if (outcome.completed) {
            FinalStatus.PAYMENT_COMPLETED
        } else {
            FinalStatus.PAYMENT_CANCELLED
        }
        internal.complete(status)
    }

    // ── Internal mapping ────────────────────────────────────────────

    private fun mapDecisionToVerdict(decision: InterventionDecision): RiskVerdict {
        val interventionType = InterventionType.fromActionId(decision.actionId)

        if (interventionType == null) {
            // A0_PASS
            return RiskVerdict(
                shouldProceed = true,
                title = "Transaction Approved",
                message = "This transaction appears safe.",
                reasonCodes = decision.reasonCodes,
                debugBeliefScore = decision.beliefScore
            )
        }

        val (title, message) = getInterventionText(interventionType)

        return RiskVerdict(
            shouldProceed = false,
            interventionType = interventionType,
            title = title,
            message = message,
            reasonCodes = decision.reasonCodes,
            debugBeliefScore = decision.beliefScore
        )
    }

    private fun getInterventionText(type: InterventionType): Pair<String, String> = when (type) {
        InterventionType.PROMPT -> Pair(
            "Quick Check",
            "Do you know the person you're sending money to? Take a moment to verify."
        )
        InterventionType.CHALLENGE -> Pair(
            "Reflection Challenge",
            "This transaction looks unusual for your account. Please confirm why you are making this transfer."
        )
        InterventionType.DELAY -> Pair(
            "Cooling Period",
            "This transaction has been flagged for elevated risk. A brief cooling period has been applied."
        )
        InterventionType.CALL_BLOCK -> Pair(
            "Active Call Detected",
            "You are attempting a high-risk transfer while on an active call or sharing your screen. To proceed, you must disconnect the call."
        )
        InterventionType.VERIFY_CONTACT -> Pair(
            "Verify with Trusted Contact",
            "This transaction is highly unusual. We require verification from a trusted contact before proceeding."
        )
        InterventionType.HARD_BLOCK -> Pair(
            "Transaction Blocked",
            "This transaction has been blocked due to extreme risk indicators. Please contact your bank if you believe this is an error."
        )
    }

    companion object {
        /** Hash a VPA for privacy-preserving graph lookup. */
        internal fun hashVpa(vpa: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(vpa.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        /** Convert raw INR amount to AmountBucket. */
        internal fun bucketizeAmount(amountInr: Double): AmountBucket = when {
            amountInr <= 1_000 -> AmountBucket.LOW
            amountInr <= 10_000 -> AmountBucket.MEDIUM
            amountInr <= 50_000 -> AmountBucket.HIGH
            else -> AmountBucket.CRITICAL
        }

        /**
         * Estimate beneficiary novelty.
         * In production, this would query the user's transaction history.
         * For hackathon: unknown VPAs get high novelty, known ones get low.
         */
        internal fun estimateNovelty(vpaHash: String): Double {
            // Deterministic novelty based on hash for consistent demo behavior
            val hashByte = vpaHash.take(2).toInt(16)
            return (hashByte / 255.0).coerceIn(0.0, 1.0)
        }
    }
}
