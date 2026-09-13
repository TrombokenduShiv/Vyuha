package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Belief Updater — Deterministic Mock for Slices 0 & 1
 *
 * In production: Bayesian log-odds update that incrementally adjusts P(Coercion)
 * based on new expert evidence. Uses a prior belief and sequential observations.
 *
 * For the deterministic mock:
 * - Maps the triage score directly to p_coercion.
 * - Generates the conformal uncertainty set based on p_coercion thresholds.
 * - Tracks the missing evidence mask from the context.
 */
class BeliefUpdater {

    private var eventCount = 0

    /**
     * Update the belief state given the triage output and the context.
     */
    fun update(
        snapshot: ContextSnapshot,
        edgeRiskOutput: EdgeRiskOutput
    ): BeliefState {
        eventCount++

        val pCoercion = edgeRiskOutput.triageScore

        // ── Conformal uncertainty set ──
        // Low risk → {SAFE}, High risk → {RISK}, Ambiguous → {SAFE, RISK}
        val uncertaintySet = when {
            pCoercion < 0.3 -> listOf(UncertaintyLabel.SAFE)
            pCoercion > 0.7 -> listOf(UncertaintyLabel.RISK)
            else -> listOf(UncertaintyLabel.SAFE, UncertaintyLabel.RISK)
        }

        // ── Missing evidence mask ──
        val missingMask = MissingEvidenceMask(
            graphMissing = snapshot.graphRiskToken == null,
            baselineMissing = snapshot.baseline == null,
            deviceMissing = snapshot.device == null
        )

        val finalUncertaintySet = if (missingMask.graphMissing || missingMask.baselineMissing || missingMask.deviceMissing) {
            val augmented = uncertaintySet.toMutableList()
            if (!augmented.contains(UncertaintyLabel.ABSTAIN)) {
                augmented.add(UncertaintyLabel.ABSTAIN)
            }
            augmented
        } else {
            uncertaintySet
        }

        return BeliefState(
            sessionId = snapshot.sessionId,
            timestampMs = System.currentTimeMillis(),
            pCoercion = pCoercion,
            uncertaintySet = finalUncertaintySet,
            missingEvidenceMask = missingMask,
            eventSequenceLength = eventCount
        )
    }

    /**
     * Reset event counter for a new session.
     */
    fun reset() {
        eventCount = 0
    }
}
