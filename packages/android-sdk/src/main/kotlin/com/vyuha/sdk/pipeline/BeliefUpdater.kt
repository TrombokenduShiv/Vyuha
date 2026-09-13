/**
 * Vyuha 2.0 — Belief Updater (Deterministic Mock)
 * =================================================
 * Maintains P(Coercion | observations so far) via log-odds update.
 * In production: replaced by a stateful recurrent Bayesian updater.
 * Here: deterministic logic that accumulates evidence into a belief score.
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class BeliefUpdater {

    private var logOdds: Float = 0.0f  // prior: 50/50 in log-odds = 0
    private var eventCount: Int = 0

    /**
     * Update belief state given new edge risk output and optional graph token.
     * Returns the updated BeliefState conforming to the frozen schema.
     */
    fun update(
        sessionId: String,
        edgeOutput: EdgeRiskOutput,
        graphToken: GraphRiskToken?,
        snapshot: ContextSnapshot
    ): BeliefState {
        eventCount++

        // ── Evidence from triage ────────────────────────────────────
        if (edgeOutput.triageScore > 0.3f) {
            logOdds += (edgeOutput.triageScore - 0.3f) * 3.0f
        } else {
            logOdds -= 0.5f  // evidence toward safety
        }

        // ── Evidence from experts ───────────────────────────────────
        for (expert in edgeOutput.expertScores) {
            logOdds += (expert.score - 0.5f) * 2.0f
        }

        // ── Evidence from graph ─────────────────────────────────────
        val graphMissing = graphToken == null
        if (!graphMissing) {
            val graphRisk = graphToken!!.riskScore
            logOdds += (graphRisk - 0.5f) * 2.5f
        } else {
            // Missing graph evidence → increase uncertainty, not safety
            logOdds += 0.3f
        }

        // ── Convert log-odds to probability ─────────────────────────
        val pCoercion = (1.0f / (1.0f + Math.exp(-logOdds.toDouble()))).toFloat()

        // ── Conformal uncertainty set ───────────────────────────────
        val uncertaintySet = when {
            pCoercion > 0.75f -> listOf(UncertaintyLabel.RISK)
            pCoercion < 0.25f -> listOf(UncertaintyLabel.SAFE)
            else -> listOf(UncertaintyLabel.SAFE, UncertaintyLabel.RISK, UncertaintyLabel.ABSTAIN)
        }

        return BeliefState(
            sessionId = sessionId,
            pCoercion = pCoercion,
            uncertaintySet = uncertaintySet,
            missingEvidenceMask = MissingEvidenceMask(
                graphMissing = graphMissing,
                baselineMissing = false,
                deviceMissing = false
            ),
            eventSequenceLength = eventCount
        )
    }

    /**
     * Reset state for a new session.
     */
    fun reset() {
        logOdds = 0.0f
        eventCount = 0
    }
}
