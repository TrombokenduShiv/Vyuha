package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Triage Gate — Deterministic Mock for Slices 0 & 1
 *
 * In production: A lightweight MLP that scores [0,1] whether deeper expert
 * analysis is needed. If > threshold, routes to Sparse MoE experts.
 *
 * For the deterministic mock:
 * - Sums risk signals from the ContextSnapshot.
 * - If combined score > 0.5, triggers expert routing and returns a high triage score.
 * - Otherwise, returns a low triage score (fast pass).
 */
class TriageGate {

    companion object {
        /** Threshold above which deeper expert evaluation is triggered. */
        const val TRIAGE_THRESHOLD = 0.5
    }

    /**
     * Evaluate the context and produce an EdgeRiskOutput.
     *
     * Deterministic logic:
     * - novelty (new payee) contributes 0.3
     * - active call contributes 0.3
     * - high amount bucket contributes 0.2
     * - graph risk (if present and high) contributes 0.3
     * - device risks contribute 0.1 each
     */
    fun evaluate(snapshot: ContextSnapshot): EdgeRiskOutput {
        var riskAccumulator = 0.0
        val expertScores = mutableListOf<ExpertScore>()

        // ── Transaction novelty signal ──
        val noveltyScore = snapshot.transaction.beneficiaryNovelty
        riskAccumulator += noveltyScore * 0.3
        expertScores.add(ExpertScore("NOVELTY_EXPERT", noveltyScore))

        // ── Communication signal (active call = high risk) ──
        val commScore = if (snapshot.communication.active) 1.0 else 0.0
        riskAccumulator += commScore * 0.3
        expertScores.add(ExpertScore("COMM_EXPERT", commScore))

        // ── Amount bucket signal ──
        val amountScore = when (snapshot.transaction.amountBucket) {
            AmountBucket.LOW -> 0.0
            AmountBucket.MEDIUM -> 0.2
            AmountBucket.HIGH -> 0.6
            AmountBucket.CRITICAL -> 1.0
        }
        riskAccumulator += amountScore * 0.2
        expertScores.add(ExpertScore("AMOUNT_EXPERT", amountScore))

        // ── Graph risk signal ──
        val graphScore = snapshot.graphRiskToken?.riskScore ?: 0.0
        riskAccumulator += graphScore * 0.3
        expertScores.add(ExpertScore("GRAPH_EXPERT", graphScore))

        // ── Device risk signals ──
        val deviceScore = (if (snapshot.device.captureRisk) 0.5 else 0.0) +
                (if (snapshot.device.overlayRisk) 0.5 else 0.0)
        riskAccumulator += deviceScore * 0.1
        expertScores.add(ExpertScore("DEVICE_EXPERT", deviceScore))

        val triageScore = riskAccumulator.coerceIn(0.0, 1.0)

        return EdgeRiskOutput(
            sessionId = snapshot.sessionId,
            timestampMs = System.currentTimeMillis(),
            triageScore = triageScore,
            isSparseRouted = triageScore > TRIAGE_THRESHOLD,
            expertScores = expertScores
        )
    }
}
