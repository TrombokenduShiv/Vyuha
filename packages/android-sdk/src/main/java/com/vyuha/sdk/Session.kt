package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.core.*

/**
 * Session — Manages the active transaction state machine for a single payment.
 *
 * Matches the API contract from ARCHITECTURE_FREEZE_V1 §11:
 *   Session.updateContext(snapshot: EventSnapshot)
 *   Session.evaluate(): InterventionDecision
 *   Session.recordResponse(resp: UserResponse)
 *   Session.complete(outcome: TransactionOutcome)
 *
 * Lifecycle:
 *   beginPayment() → [updateContext() → evaluate()]* → recordResponse()? → complete()
 */
class Session(
    private val vpaHash: String,
    private val amountBucket: AmountBucket,
    private val beneficiaryNovelty: Double,
    private val channel: String,
    private val graphRiskToken: GraphRiskToken?
) {
    val sessionId: String = java.util.UUID.randomUUID().toString()

    private val contextAggregator = ContextAggregator()
    private val pipeline = Pipeline()

    /** Current communication state — updated via updateContext(). */
    private var communicationActive: Boolean? = null
    private var captureRisk: Boolean? = null
    private var overlayRisk: Boolean? = null
    private var deviationScore: Double? = null

    /** The last evaluation result. */
    private var lastDecision: InterventionDecision? = null

    /**
     * State of this session.
     */
    enum class State { ACTIVE, INTERVENTION_PENDING, COMPLETED }
    var state: State = State.ACTIVE
        private set

    /**
     * Update the real-time context signals.
     * Call this before evaluate() to reflect current device/communication state.
     */
    fun updateContext(
        communicationActive: Boolean? = null,
        captureRisk: Boolean? = null,
        overlayRisk: Boolean? = null,
        deviationScore: Double? = null
    ) {
        if (communicationActive != null) this.communicationActive = communicationActive
        if (captureRisk != null) this.captureRisk = captureRisk
        if (overlayRisk != null) this.overlayRisk = overlayRisk
        if (deviationScore != null) this.deviationScore = deviationScore
    }

    /**
     * Run the full edge inference pipeline and return the intervention decision.
     */
    fun evaluate(): InterventionDecision {
        check(state != State.COMPLETED) { "Session already completed." }

        val snapshot = contextAggregator.aggregate(
            sessionId = sessionId,
            amountBucket = amountBucket,
            beneficiaryNovelty = beneficiaryNovelty,
            channel = channel,
            communicationActive = communicationActive,
            captureRisk = captureRisk,
            overlayRisk = overlayRisk,
            deviationScore = deviationScore,
            graphRiskToken = graphRiskToken
        )

        val decision = pipeline.evaluate(snapshot)
        lastDecision = decision

        if (decision.action != "A0_PASS") {
            state = State.INTERVENTION_PENDING
        }

        return decision
    }

    /**
     * Record the user's response to an intervention.
     * After recording, re-evaluates if context has changed (e.g., call ended).
     */
    fun recordResponse(response: UserResponse): InterventionDecision {
        check(state == State.INTERVENTION_PENDING) {
            "No pending intervention to respond to."
        }
        state = State.ACTIVE

        // Re-evaluate with updated context
        return evaluate()
    }

    /**
     * Complete the session and record the outcome.
     */
    fun complete(finalStatus: FinalStatus = FinalStatus.PAYMENT_COMPLETED) {
        state = State.COMPLETED
        pipeline.reset()
    }
}
