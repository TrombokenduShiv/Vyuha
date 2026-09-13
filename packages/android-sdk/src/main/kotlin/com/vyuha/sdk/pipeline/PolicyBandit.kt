/**
 * Vyuha 2.0 — Policy Bandit (Deterministic Mock)
 * ================================================
 * Frozen offline-trained policy: maps BeliefState → InterventionDecision.
 * In production: replaced by a contextual bandit / lookup table from RL training.
 * Here: deterministic threshold-based mapping (A0-A6).
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §7 — ML Inference Path, Step 8.
 * Decision D9: Frozen offline-trained policy with A0-A6 Action Space.
 * Latency budget: Belief + Policy combined < 3 ms (per §13).
 *
 * Deterministic policy mapping:
 * - p < 0.15 && confident     → A0_PASS
 * - p < 0.30                  → A0_PASS (or A1 if graph missing)
 * - p in [0.30, 0.50)         → A2_REFLECTION_CHALLENGE
 * - p in [0.50, 0.65)         → A3_COOLING_DELAY
 * - p in [0.65, 0.85)         → A4_ISOLATION_BREAK
 * - p in [0.85, 0.95)         → A5_TRUSTED_VERIFY
 * - p >= 0.95                 → A6_STEP_UP_REQUIRED
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
            p < 0.15 && !uncertain -> {
                actionId = ActionId.A0_PASS
            }
            // Low risk, slight uncertainty
            p < 0.30 -> {
                actionId = if (graphMissing) ActionId.A1_MICRO_PROMPT else ActionId.A0_PASS
                if (graphMissing) reasonCodes.add("GRAPH_UNAVAILABLE")
            }
            // Moderate risk
            p < 0.50 -> {
                actionId = ActionId.A2_REFLECTION_CHALLENGE
                reasonCodes.add("MODERATE_BELIEF")
                if (graphMissing) reasonCodes.add("GRAPH_UNAVAILABLE")
            }
            // Elevated risk
            p < 0.65 -> {
                actionId = ActionId.A3_COOLING_DELAY
                reasonCodes.add("ELEVATED_BELIEF")
            }
            // High risk — isolation break territory
            p < 0.85 -> {
                actionId = ActionId.A4_ISOLATION_BREAK
                reasonCodes.add("HIGH_COERCION_BELIEF")
                if (belief.uncertaintySet == listOf(UncertaintyLabel.RISK)) {
                    reasonCodes.add("CONFORMAL_RISK_ONLY")
                }
            }
            // Very high risk
            p < 0.95 -> {
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
            1 -> 0.05    // high confidence
            2 -> 0.25    // moderate
            else -> 0.50 // abstain present
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
