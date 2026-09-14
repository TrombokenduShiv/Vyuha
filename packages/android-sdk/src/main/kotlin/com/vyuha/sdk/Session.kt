/**
 * Vyuha 2.0 — Session
 * =====================
 * Orchestrates the full edge pipeline for a single payment session:
 *   ContextSnapshot → Triage → Belief → Policy → InterventionDecision
 *
 * This is the OODA loop. Each call to evaluate() runs one cycle.
 * The host app calls recordResponse() when the user interacts with an intervention,
 * then calls evaluate() again to get the next decision.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §4 — Runtime State Diagram.
 * API contract ref: §11 — Session.updateContext, Session.evaluate,
 *                   Session.recordResponse, Session.complete.
 *
 * Lifecycle:
 *   beginPayment() → [updateContext() → evaluate()]* → recordResponse()? → complete()
 */
package com.vyuha.sdk

import com.vyuha.sdk.cache.GraphTokenCache
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.network.GraphRiskClient
import com.vyuha.sdk.pipeline.BeliefUpdater
import com.vyuha.sdk.pipeline.ContextAggregator
import com.vyuha.sdk.pipeline.Pipeline
import com.vyuha.sdk.pipeline.PolicyBandit
import com.vyuha.sdk.pipeline.TriageGate
import kotlinx.coroutines.*
import java.util.UUID

class Session internal constructor(
    private val graphClient: GraphRiskClient,
    private val vyuhaConfig: VyuhaConfig,
    val sessionId: String = UUID.randomUUID().toString(),
    // Convenience constructor params (for static Vyuha.beginPayment API)
    private val initialVpaHash: String? = null,
    private val initialAmountBucket: AmountBucket? = null,
    private val initialBeneficiaryNovelty: Double? = null,
    private val initialChannel: String? = null,
    initialGraphToken: GraphRiskToken? = null
) {
    // ── Pipeline components ─────────────────────────────────────────
    private val contextAggregator = ContextAggregator()
    private val pipeline = Pipeline()
    private val tokenCache = GraphTokenCache()

    // ── Structured concurrency ──────────────────────────────────────
    /** SupervisorJob so a single child failure doesn't cancel siblings. */
    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(Dispatchers.Default + sessionJob)

    // ── State ───────────────────────────────────────────────────────
    private var currentSnapshot: ContextSnapshot? = null
    private var graphToken: GraphRiskToken? = initialGraphToken
    private var lastDecision: InterventionDecision? = null
    private var lastBelief: BeliefState? = null

    /** Current communication/device state — updated via updateContext(). */
    private var communicationActive: Boolean = false
    private var captureRisk: Boolean = false
    private var overlayRisk: Boolean = false
    private var deviationScore: Double = 0.0

    /**
     * Session state machine.
     * ACTIVE → INTERVENTION_PENDING → ACTIVE → COMPLETED
     */
    enum class State { ACTIVE, INTERVENTION_PENDING, COMPLETED }
    var state: State = State.ACTIVE
        private set

    // ── Graph Risk Prefetch ─────────────────────────────────────────

    /**
     * Prefetch graph risk for the beneficiary VPA.
     * Should be called as early as possible (when user enters VPA).
     *
     * Lookup order:
     * 1. Check in-memory cache (fast path)
     * 2. Fetch from network (with retry)
     * 3. Check cache for stale token (graceful degradation)
     * 4. null → graphMissing = true (fail-safe)
     */
    suspend fun prefetchGraphRisk(beneficiaryVpaHash: String) {
        // 1. Check cache first
        when (val cacheResult = tokenCache.get(beneficiaryVpaHash)) {
            is GraphTokenCache.CacheResult.Hit -> {
                graphToken = cacheResult.token
                return
            }
            is GraphTokenCache.CacheResult.StaleHit -> {
                // Try network, fall back to stale
                val networkToken = fetchFromNetwork(beneficiaryVpaHash)
                graphToken = networkToken ?: cacheResult.token
                return
            }
            is GraphTokenCache.CacheResult.Miss -> {
                // Must fetch from network
            }
        }

        // 2. Fetch from network
        val networkToken = fetchFromNetwork(beneficiaryVpaHash)
        if (networkToken != null) {
            tokenCache.put(beneficiaryVpaHash, networkToken)
        }
        graphToken = networkToken
    }

    /**
     * Execute network fetch with structured concurrency.
     */
    private suspend fun fetchFromNetwork(vpaHash: String): GraphRiskToken? {
        return withContext(sessionScope.coroutineContext) {
            try {
                graphClient.fetchRisk(vpaHash, sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    // ── Context Update (Full API) ───────────────────────────────────

    /**
     * Update the current context snapshot (full API).
     * Called by the host app whenever relevant signals change.
     */
    fun updateContext(snapshot: ContextSnapshot) {
        currentSnapshot = snapshot.copy(sessionId = sessionId)
    }

    // ── Context Update (Convenience API) ────────────────────────────

    /**
     * Update the real-time context signals (convenience API).
     * Used by the demo app for simpler integration.
     * Call this before evaluate() to reflect current device/communication state.
     */
    fun updateContext(
        communicationActive: Boolean? = null,
        captureRisk: Boolean? = null,
        overlayRisk: Boolean? = null,
        deviationScore: Double? = null
    ) {
        communicationActive?.let { this.communicationActive = it }
        captureRisk?.let { this.captureRisk = it }
        overlayRisk?.let { this.overlayRisk = it }
        deviationScore?.let { this.deviationScore = it }

        // Rebuild the snapshot from current state
        if (initialAmountBucket != null && initialBeneficiaryNovelty != null) {
            currentSnapshot = contextAggregator.aggregate(
                sessionId = sessionId,
                amountBucket = initialAmountBucket,
                beneficiaryNovelty = initialBeneficiaryNovelty,
                channel = initialChannel ?: "UPI",
                communicationActive = this.communicationActive,
                captureRisk = this.captureRisk,
                overlayRisk = this.overlayRisk,
                deviationScore = this.deviationScore,
                graphRiskToken = graphToken
            )
        }
    }

    // ── Evaluate ────────────────────────────────────────────────────

    /**
     * Run one full OODA cycle:
     *   Context → Triage → Belief → Policy → Decision
     *
     * Returns the InterventionDecision the host should render.
     * @throws IllegalStateException if no context has been set or session is completed.
     */
    fun evaluate(): InterventionDecision {
        check(state != State.COMPLETED) { "Session already completed." }

        val snapshot = currentSnapshot
            ?: throw IllegalStateException("updateContext() must be called before evaluate()")

        val decision = pipeline.evaluate(snapshot)
        lastDecision = decision

        if (decision.actionId != ActionId.A0_PASS) {
            state = State.INTERVENTION_PENDING
        }

        return decision
    }

    // ── Response Recording ──────────────────────────────────────────

    /**
     * Record the user's response to the last intervention.
     * After recording, re-evaluates with updated context to get the next decision.
     *
     * For example, if A4_ISOLATION_BREAK was rendered and the user ended
     * their call, the host calls recordResponse(CONTINUED) and then
     * the method re-evaluates with the updated context.
     *
     * @return The new InterventionDecision after re-evaluation.
     */
    fun recordResponse(response: UserResponse): InterventionDecision {
        check(state == State.INTERVENTION_PENDING) {
            "No pending intervention to respond to."
        }
        state = State.ACTIVE

        // Re-evaluate with updated context
        return evaluate()
    }

    // ── Session Completion ──────────────────────────────────────────

    /**
     * Complete the session and produce an OutcomeFeedback record.
     * Cancels any inflight coroutines (e.g., pending graph prefetch).
     */
    fun complete(finalStatus: FinalStatus): OutcomeFeedback {
        state = State.COMPLETED
        sessionJob.cancel() // Cancel any inflight async operations
        pipeline.reset()

        return OutcomeFeedback(
            sessionId = sessionId,
            actionId = lastDecision?.actionId?.name ?: "UNKNOWN",
            userResponse = UserResponse.NONE,
            finalStatus = finalStatus
        )
    }

    // ── Debug Accessors ─────────────────────────────────────────────

    /**
     * Get the last computed belief state (for debugging / dashboard).
     */
    fun getLastBelief(): BeliefState? = lastBelief

    /**
     * Get the last intervention decision.
     */
    fun getLastDecision(): InterventionDecision? = lastDecision
}
