package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Policy Bandit — Deterministic Mock for Slices 0 & 1
 *
 * In production: A frozen offline-trained contextual bandit that maps
 * (BeliefState, ContextSnapshot) → ActionId from the A0–A6 space.
 *
 * For the deterministic mock:
 * - p_coercion < 0.3                    → A0_PASS
 * - p_coercion in [0.3, 0.5)            → A1_MICRO_PROMPT
 * - p_coercion in [0.5, 0.65)           → A2_REFLECTION_CHALLENGE
 * - p_coercion in [0.65, 0.75)          → A3_COOLING_DELAY
 * - p_coercion >= 0.75 + active call    → A4_ISOLATION_BREAK
 * - p_coercion >= 0.75 + no call        → A5_TRUSTED_VERIFY
 * - uncertainty set contains ABSTAIN    → A6_STEP_UP_REQUIRED
 */
class PolicyBandit {

    /**
     * Decide the intervention action based on belief state and context.
     */
    fun decide(
        beliefState: BeliefState,
        snapshot: ContextSnapshot
    ): InterventionDecision {
        val p = beliefState.pCoercion
        val reasonCodes = mutableListOf<String>()

        // ── Check for ABSTAIN (insufficient confidence) ──
        if (UncertaintyLabel.ABSTAIN in beliefState.uncertaintySet) {
            reasonCodes.add("INSUFFICIENT_CONFIDENCE")
            return InterventionDecision(
                sessionId = beliefState.sessionId,
                timestampMs = System.currentTimeMillis(),
                actionId = ActionId.A6_STEP_UP_REQUIRED,
                beliefScore = p,
                uncertainty = 1.0,
                reasonCodes = reasonCodes
            )
        }

        // ── Build reason codes from evidence ──
        if (snapshot.communication.active) reasonCodes.add("ACTIVE_CALL")
        if (snapshot.graphRiskToken != null && snapshot.graphRiskToken.riskScore > 0.7) {
            reasonCodes.add("HIGH_GRAPH_RISK")
        }
        if (snapshot.transaction.beneficiaryNovelty > 0.8) reasonCodes.add("NEW_BENEFICIARY")
        if (snapshot.transaction.amountBucket == AmountBucket.HIGH ||
            snapshot.transaction.amountBucket == AmountBucket.CRITICAL) {
            reasonCodes.add("HIGH_AMOUNT")
        }
        if (snapshot.device.captureRisk) reasonCodes.add("SCREEN_CAPTURE_RISK")
        if (snapshot.device.overlayRisk) reasonCodes.add("OVERLAY_RISK")

        // ── Deterministic policy mapping ──
        val actionId = when {
            p < 0.3 -> ActionId.A0_PASS
            p < 0.5 -> ActionId.A1_MICRO_PROMPT
            p < 0.65 -> ActionId.A2_REFLECTION_CHALLENGE
            p < 0.75 -> ActionId.A3_COOLING_DELAY
            snapshot.communication.active -> ActionId.A4_ISOLATION_BREAK
            else -> ActionId.A5_TRUSTED_VERIFY
        }

        // ── Calculate uncertainty from the set size ──
        val uncertainty = when (beliefState.uncertaintySet.size) {
            1 -> 0.05   // Confident
            2 -> 0.35   // Ambiguous
            else -> 0.8 // Multiple labels → high uncertainty
        }

        return InterventionDecision(
            sessionId = beliefState.sessionId,
            timestampMs = System.currentTimeMillis(),
            actionId = actionId,
            beliefScore = p,
            uncertainty = uncertainty,
            reasonCodes = reasonCodes
        )
    }
}
