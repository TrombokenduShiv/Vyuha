/**
 * Vyuha 2.0 — Offline Risk Fallback
 * ====================================
 * Encapsulates the degraded-path logic when the Graph Risk API is unavailable.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §8 — Offline / Degraded Path.
 * Decision D4: Core safety continues with graphRisk = UNKNOWN.
 * Missing institutional evidence increases uncertainty, not safety.
 *
 * Behavior:
 * - Sets MissingGraphMask = true
 * - Adjusts belief state uncertainty upward
 * - Policy favors conservative friction (A1-A3)
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

object OfflineRiskFallback {

    /**
     * Generate a "null-evidence" graph contribution for the belief updater.
     * This is NOT a fake token — it signals the absence of graph data.
     *
     * @return A MissingEvidenceMask indicating graph is unavailable.
     */
    fun buildMissingMask(): MissingEvidenceMask {
        return MissingEvidenceMask(
            graphMissing = true,
            baselineMissing = false,
            deviceMissing = false
        )
    }

    /**
     * Determine if a session should operate in offline/degraded mode.
     *
     * @param graphToken The fetched (or cached) graph token, null if unavailable.
     * @param offlineMode Whether the SDK is configured in offline-only mode.
     * @return true if the pipeline should use the degraded path.
     */
    fun shouldDegrade(graphToken: GraphRiskToken?, offlineMode: Boolean): Boolean {
        if (offlineMode) return true
        if (graphToken == null) return true

        // Check if the token has expired
        val nowSec = System.currentTimeMillis() / 1000
        if (nowSec > graphToken.expiresAt) return true

        return false
    }

    /**
     * Apply conservative uncertainty adjustment for offline mode.
     * When the graph is missing, we increase the uncertainty by adding
     * ABSTAIN to the prediction set if it's not already present.
     *
     * This ensures the policy bandit favors conservative friction (A1-A3)
     * rather than passing through or escalating without data.
     */
    fun adjustUncertaintyForOffline(uncertaintySet: List<UncertaintyLabel>): List<UncertaintyLabel> {
        return if (UncertaintyLabel.ABSTAIN !in uncertaintySet) {
            uncertaintySet + UncertaintyLabel.ABSTAIN
        } else {
            uncertaintySet
        }
    }
}
