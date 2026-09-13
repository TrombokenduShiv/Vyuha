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
                action = ActionId.A6_STEP_UP_REQUIRED.name,
                severity = 6,
                reasonCodes = reasonCodes,
                cooldownSeconds = 0,
                trustedVerification = false
            )
        }

        // ── Build reason codes from evidence ──
        if (snapshot.communication?.active == true) reasonCodes.add("ACTIVE_CALL")
        if (snapshot.graphRiskToken != null && snapshot.graphRiskToken.riskScore > 0.7) {
            reasonCodes.add("HIGH_GRAPH_RISK")
        }
        if (snapshot.transaction.beneficiaryNovelty > 0.8) reasonCodes.add("NEW_BENEFICIARY")
        if (snapshot.transaction.amountBucket == AmountBucket.HIGH ||
            snapshot.transaction.amountBucket == AmountBucket.CRITICAL) {
            reasonCodes.add("HIGH_AMOUNT")
        }
        if (snapshot.device?.captureRisk == true) reasonCodes.add("SCREEN_CAPTURE_RISK")
        if (snapshot.device?.overlayRisk == true) reasonCodes.add("OVERLAY_RISK")

        // ── Deterministic policy mapping ──
        val actionId = when {
            p < 0.3 -> ActionId.A0_PASS
            p < 0.5 -> ActionId.A1_MICRO_PROMPT
            p < 0.65 -> ActionId.A2_REFLECTION_CHALLENGE
            p < 0.75 -> ActionId.A3_COOLING_DELAY
            snapshot.communication?.active == true -> ActionId.A4_ISOLATION_BREAK
            else -> ActionId.A5_TRUSTED_VERIFY
        }

        // ── Calculate uncertainty from the set size ──
        val uncertainty = when (beliefState.uncertaintySet.size) {
            1 -> 0.05   // Confident
            2 -> 0.35   // Ambiguous
            else -> 0.8 // Multiple labels → high uncertainty
        }

        return InterventionDecision(
            action = actionId.name,
            severity = when(actionId) {
                ActionId.A0_PASS -> 0
                ActionId.A1_MICRO_PROMPT -> 1
                ActionId.A2_REFLECTION_CHALLENGE -> 2
                ActionId.A3_COOLING_DELAY -> 3
                ActionId.A4_ISOLATION_BREAK -> 4
                ActionId.A5_TRUSTED_VERIFY -> 5
                ActionId.A6_STEP_UP_REQUIRED -> 6
            },
            reasonCodes = reasonCodes,
            cooldownSeconds = if (actionId == ActionId.A3_COOLING_DELAY) 300 else 0,
            trustedVerification = actionId == ActionId.A5_TRUSTED_VERIFY
        )
    }
}
