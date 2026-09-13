package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Pipeline — Wires together the full edge inference chain:
 *   ContextAggregator → TriageGate → BeliefUpdater → PolicyBandit
 *
 * This is the central orchestrator for a single evaluation cycle.
 * In production, it will handle async graph token fetching and model loading.
 * For Slices 0 & 1, all operations are synchronous and deterministic.
 */
class Pipeline(
    private val contextAggregator: ContextAggregator = ContextAggregator(),
    private val triageGate: TriageGate = TriageGate(),
    private val beliefUpdater: BeliefUpdater = BeliefUpdater(),
    private val policyBandit: PolicyBandit = PolicyBandit()
) {

    /**
     * Run the full inference pipeline on a pre-assembled ContextSnapshot.
     *
     * @return The InterventionDecision for the host app to render.
     */
    fun evaluate(snapshot: ContextSnapshot): InterventionDecision {
        // Step 1: Triage + Sparse MoE (deterministic mock)
        val edgeRiskOutput = triageGate.evaluate(snapshot)

        // Step 2: Update belief state
        val beliefState = beliefUpdater.update(snapshot, edgeRiskOutput)

        // Step 3: Policy decision
        return policyBandit.decide(beliefState, snapshot)
    }

    /**
     * Reset pipeline state for a new session.
     */
    fun reset() {
        beliefUpdater.reset()
    }
}
