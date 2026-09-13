/**
 * Vyuha 2.0 — Policy Bandit (Deterministic Mock)
 * ================================================
 * Frozen offline-trained policy: maps BeliefState → InterventionDecision.
 * In production: replaced by a contextual bandit / lookup table from RL training.
 * Here: deterministic threshold-based mapping (A0-A6).
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class PolicyBandit {

    /**
     * Maps the current belief state to an intervention action.
     * Uses the conformal uncertainty set + P(Coercion) to select
     * the minimum effective friction.
     */
    fun decide(belief: BeliefState): InterventionDecision {
        val p = belief.pCoercion
        val uncertain = belief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN)
        val graphMissing = belief.missingEvidenceMask.graphMissing

        // ── Determine action ────────────────────────────────────────
        val actionId: ActionId
        val reasonCodes = mutableListOf<String>()

        when {
            // Clear safe
            p < 0.15f && !uncertain -> {
                actionId = ActionId.A0_PASS
            }
            // Low risk, slight uncertainty
            p < 0.30f -> {
                actionId = if (graphMissing) ActionId.A1_MICRO_PROMPT else ActionId.A0_PASS
                if (graphMissing) reasonCodes.add("GRAPH_UNAVAILABLE")
            }
            // Moderate risk
            p < 0.50f -> {
                actionId = ActionId.A2_REFLECTION_CHALLENGE
                reasonCodes.add("MODERATE_BELIEF")
                if (graphMissing) reasonCodes.add("GRAPH_UNAVAILABLE")
            }
            // Elevated risk
            p < 0.65f -> {
                actionId = ActionId.A3_COOLING_DELAY
                reasonCodes.add("ELEVATED_BELIEF")
            }
            // High risk — isolation break territory
            p < 0.85f -> {
                actionId = ActionId.A4_ISOLATION_BREAK
                reasonCodes.add("HIGH_COERCION_BELIEF")
                if (belief.uncertaintySet == listOf(UncertaintyLabel.RISK)) {
                    reasonCodes.add("CONFORMAL_RISK_ONLY")
                }
            }
            // Very high risk
            p < 0.95f -> {
                actionId = ActionId.A5_TRUSTED_VERIFY
                reasonCodes.add("VERY_HIGH_COERCION_BELIEF")
            }
            // Near certain
            else -> {
                actionId = ActionId.A6_STEP_UP_REQUIRED
                reasonCodes.add("EXTREME_COERCION_BELIEF")
            }
        }

        // ── Uncertainty → compute scalar ────────────────────────────
        val uncertaintyScalar = when (belief.uncertaintySet.size) {
            1 -> 0.05f   // high confidence
            2 -> 0.25f   // moderate
            else -> 0.50f // abstain present
        }

        return InterventionDecision(
            sessionId = belief.sessionId,
            actionId = actionId,
            beliefScore = p,
            uncertainty = uncertaintyScalar,
            reasonCodes = reasonCodes
        )
    }
}
