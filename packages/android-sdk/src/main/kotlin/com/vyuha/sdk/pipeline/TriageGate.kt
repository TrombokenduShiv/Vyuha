/**
 * Vyuha 2.0 — Triage Gate (Deterministic Mock)
 * ==============================================
 * Scores the ContextSnapshot to decide if deeper expert routing is needed.
 * In production: replaced by a lightweight MLP on ExecuTorch.
 * Here: deterministic logic that maps known risk signals to a triage score.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §7 — ML Inference Path, Step 3.
 * Latency budget: < 2 ms (per §13).
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class TriageGate {

    companion object {
        /** Threshold above which deeper expert evaluation is triggered. */
        const val TRIAGE_THRESHOLD = 0.3
    }

    /**
     * Returns a triage score in [0, 1].
     * Score > TRIAGE_THRESHOLD triggers sparse expert routing (Top-K=2).
     *
     * Deterministic signal weights:
     * - Communication risk (active call): 0.35
     * - Transaction amount (HIGH/CRITICAL): 0.15
     * - Beneficiary novelty (> 0.7): 0.10
     * - Device capture risk: 0.15
     * - Device overlay risk: 0.10
     * - Baseline deviation (> 0.5): 0.10
     * - Graph risk (> 0.7): 0.20
     */
    fun evaluate(snapshot: ContextSnapshot, graphToken: GraphRiskToken?): EdgeRiskOutput {
        var score = 0.0

        // Communication risk: active call during payment is a strong signal
        if (snapshot.communication?.active == true) score += 0.35

        // Transaction risk: high amount + novel beneficiary
        if (snapshot.transaction.amountBucket == AmountBucket.HIGH ||
            snapshot.transaction.amountBucket == AmountBucket.CRITICAL) {
            score += 0.15
        }
        if (snapshot.transaction.beneficiaryNovelty > 0.7) score += 0.10

        // Device risk: screen capture or overlay
        if (snapshot.device?.captureRisk == true) score += 0.15
        if (snapshot.device?.overlayRisk == true) score += 0.10

        // Baseline deviation
        if ((snapshot.baseline?.deviationScore ?: 0.0) > 0.5) score += 0.10

        // Graph risk (if available)
        if (graphToken != null && graphToken.riskScore > 0.7) score += 0.20

        score = score.coerceIn(0.0, 1.0)

        // Build expert scores (mock: derive from sub-signals)
        val experts = mutableListOf<ExpertScore>()
        if (score > TRIAGE_THRESHOLD) {
            // Top-K=2 sparse routing
            if (snapshot.communication?.active == true) {
                experts.add(ExpertScore("communication_expert", 0.35 + (graphToken?.riskScore ?: 0.0) * 0.3))
            }
            if (snapshot.transaction.beneficiaryNovelty > 0.5) {
                experts.add(ExpertScore("transaction_expert", snapshot.transaction.beneficiaryNovelty * 0.8))
            }
            if (snapshot.device?.captureRisk == true || snapshot.device?.overlayRisk == true) {
                experts.add(ExpertScore("device_expert", if (snapshot.device?.captureRisk == true) 0.7 else 0.4))
            }
            if ((snapshot.baseline?.deviationScore ?: 0.0) > 0.3) {
                experts.add(ExpertScore("baseline_expert", snapshot.baseline?.deviationScore ?: 0.0))
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
