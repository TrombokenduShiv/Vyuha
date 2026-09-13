/**
 * Vyuha 2.0 — Triage Gate (Deterministic Mock)
 * ==============================================
 * Scores the ContextSnapshot to decide if deeper expert routing is needed.
 * In production: replaced by a lightweight MLP on ExecuTorch.
 * Here: deterministic logic that maps known risk signals to a triage score.
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class TriageGate {

    /**
     * Returns a triage score in [0, 1].
     * Score > 0.3 triggers sparse expert routing.
     */
    fun evaluate(snapshot: ContextSnapshot, graphToken: GraphRiskToken?): EdgeRiskOutput {
        var score = 0.0f

        // Communication risk: active call during payment is a strong signal
        if (snapshot.communication.active) score += 0.35f

        // Transaction risk: high amount + novel beneficiary
        if (snapshot.transaction.amountBucket == AmountBucket.HIGH ||
            snapshot.transaction.amountBucket == AmountBucket.CRITICAL) {
            score += 0.15f
        }
        if (snapshot.transaction.beneficiaryNovelty > 0.7f) score += 0.10f

        // Device risk: screen capture or overlay
        if (snapshot.device.captureRisk) score += 0.15f
        if (snapshot.device.overlayRisk) score += 0.10f

        // Baseline deviation
        if (snapshot.baseline.deviationScore > 0.5f) score += 0.10f

        // Graph risk (if available)
        if (graphToken != null && graphToken.riskScore > 0.7f) score += 0.20f

        score = score.coerceIn(0.0f, 1.0f)

        // Build expert scores (mock: derive from sub-signals)
        val experts = mutableListOf<ExpertScore>()
        if (score > 0.3f) {
            // Top-K=2 sparse routing
            if (snapshot.communication.active) {
                experts.add(ExpertScore("communication_expert", 0.35f + (graphToken?.riskScore ?: 0f) * 0.3f))
            }
            if (snapshot.transaction.beneficiaryNovelty > 0.5f) {
                experts.add(ExpertScore("transaction_expert", snapshot.transaction.beneficiaryNovelty * 0.8f))
            }
            if (snapshot.device.captureRisk || snapshot.device.overlayRisk) {
                experts.add(ExpertScore("device_expert", if (snapshot.device.captureRisk) 0.7f else 0.4f))
            }
            if (snapshot.baseline.deviationScore > 0.3f) {
                experts.add(ExpertScore("baseline_expert", snapshot.baseline.deviationScore))
            }
            // Keep only top-2
            val topK = experts.sortedByDescending { it.score }.take(2)
            return EdgeRiskOutput(
                sessionId = snapshot.sessionId,
                triageScore = score,
                isSparseRouted = true,
                expertScores = topK
            )
        }

        return EdgeRiskOutput(
            sessionId = snapshot.sessionId,
            triageScore = score,
            isSparseRouted = false,
            expertScores = emptyList()
        )
    }
}
