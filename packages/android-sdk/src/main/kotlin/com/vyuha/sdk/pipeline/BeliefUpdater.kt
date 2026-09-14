/**
 * Vyuha 2.0 — Belief Updater (Deterministic Mock)
 * =================================================
 * Maintains P(Coercion | observations so far) via log-odds update.
 * In production: replaced by a stateful recurrent Bayesian updater.
 * Here: deterministic logic that accumulates evidence into a belief score.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §7 — ML Inference Path, Step 6.
 * Core to Decision D8: Coercion is sequential. No single trigger is conclusive.
 * Latency budget: Belief + Policy combined < 3 ms (per §13).
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class BeliefUpdater {

    private var logOdds: Double = 0.0  // prior: 50/50 in log-odds = 0
    private var eventCount: Int = 0

    /**
     * Update belief state given new edge risk output and optional graph token.
     * Returns the updated BeliefState conforming to the frozen schema.
     *
     * Log-odds update rules:
     * - Triage score > 0.3: positive evidence toward coercion
     * - Triage score ≤ 0.3: negative evidence (toward safety)
     * - Expert scores: each contributes (score - 0.5) * 2.0
     * - Graph risk: contributes (riskScore - 0.5) * 2.5
     * - Missing graph: +0.3 (increases uncertainty, not safety)
     */
    fun update(
        sessionId: String,
        edgeOutput: EdgeRiskOutput,
        graphToken: GraphRiskToken?,
        snapshot: ContextSnapshot
    ): BeliefState {
        eventCount++

        // ── Evidence from triage ────────────────────────────────────
        if (edgeOutput.triageScore > 0.3) {
            logOdds += (edgeOutput.triageScore - 0.3) * 3.0
        } else {
            logOdds -= 0.5  // evidence toward safety
        }

        // ── Evidence from experts ───────────────────────────────────
        for (expert in edgeOutput.expertScores) {
            logOdds += (expert.score - 0.5) * 2.0
        }

        // ── Evidence from graph ─────────────────────────────────────
        val graphMissing = graphToken == null
        if (!graphMissing) {
            val graphRisk = graphToken!!.riskScore
            logOdds += (graphRisk - 0.5) * 2.5
        } else {
            // Missing graph evidence → increase uncertainty, not safety
            // Per Decision D4: fail-safe principle
            logOdds += 0.3
        }

        // ── Convert log-odds to probability ─────────────────────────
        val pCoercion = 1.0 / (1.0 + Math.exp(-logOdds))

        // ── Conformal uncertainty set ───────────────────────────────
        val uncertaintySet = when {
            pCoercion > 0.75 -> listOf(UncertaintyLabel.RISK)
            pCoercion < 0.25 -> listOf(UncertaintyLabel.SAFE)
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
        logOdds = 0.0
        eventCount = 0
    }
}
