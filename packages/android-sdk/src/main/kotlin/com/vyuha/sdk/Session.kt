/**
 * Vyuha 2.0 — Session
 * =====================
 * Orchestrates the full edge pipeline for a single payment session:
 *   ContextSnapshot → Triage → Belief → Policy → InterventionDecision
 *
 * This is the OODA loop. Each call to evaluate() runs one cycle.
 * The host app calls recordResponse() when the user interacts with an intervention,
 * then calls evaluate() again to get the next decision.
 */
package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.network.GraphRiskClient
import com.vyuha.sdk.pipeline.BeliefUpdater
import com.vyuha.sdk.pipeline.PolicyBandit
import com.vyuha.sdk.pipeline.TriageGate
import java.util.UUID

class Session internal constructor(
    private val graphClient: GraphRiskClient,
    val sessionId: String = UUID.randomUUID().toString()
) {
    private val triage = TriageGate()
    private val beliefUpdater = BeliefUpdater()
    private val policy = PolicyBandit()

    private var currentSnapshot: ContextSnapshot? = null
    private var graphToken: GraphRiskToken? = null
    private var lastDecision: InterventionDecision? = null
    private var lastBelief: BeliefState? = null

    /**
     * Prefetch graph risk for the beneficiary VPA.
     * Should be called as early as possible (when user enters VPA).
     * Returns null on timeout — fail-safe.
     */
    suspend fun prefetchGraphRisk(beneficiaryVpaHash: String) {
        graphToken = graphClient.fetchRisk(beneficiaryVpaHash, sessionId)
    }

    /**
     * Update the current context snapshot.
     * Called by the host app whenever relevant signals change
     * (call started, amount entered, screen share detected, etc.)
     */
    fun updateContext(snapshot: ContextSnapshot) {
        currentSnapshot = snapshot.copy(sessionId = sessionId)
    }

    /**
     * Run one full OODA cycle:
     *   Context → Triage → Belief → Policy → Decision
     *
     * Returns the InterventionDecision the host should render.
     */
    fun evaluate(): InterventionDecision {
        val snapshot = currentSnapshot
            ?: throw IllegalStateException("updateContext() must be called before evaluate()")

        // 1. Triage
        val edgeOutput = triage.evaluate(snapshot, graphToken)

        // 2. Belief update
        val belief = beliefUpdater.update(sessionId, edgeOutput, graphToken, snapshot)
        lastBelief = belief

        // 3. Policy
        val decision = policy.decide(belief)
        lastDecision = decision

        return decision
    }

    /**
     * Record the user's response to the last intervention.
     * This updates context so the next evaluate() cycle has new evidence.
     *
     * For example, if A4_ISOLATION_BREAK was rendered and the user ended
     * their call, the host calls recordResponse(CONTINUED) and then
     * evaluate() again with an updated snapshot where communication.active = false.
     */
    fun recordResponse(response: UserResponse) {
        // The response itself becomes evidence for the next belief update.
        // In the deterministic mock, the host app is responsible for updating
        // the ContextSnapshot (e.g., setting communication.active = false)
        // and calling evaluate() again.
    }

    /**
     * Complete the session and produce an OutcomeFeedback record.
     */
    fun complete(finalStatus: FinalStatus): OutcomeFeedback {
        return OutcomeFeedback(
            sessionId = sessionId,
            actionId = lastDecision?.actionId?.name ?: "UNKNOWN",
            userResponse = UserResponse.NONE,
            finalStatus = finalStatus
        )
    }

    /**
     * Get the last computed belief state (for debugging / dashboard).
     */
    fun getLastBelief(): BeliefState? = lastBelief

    /**
     * Get the last intervention decision.
     */
    fun getLastDecision(): InterventionDecision? = lastDecision
}
