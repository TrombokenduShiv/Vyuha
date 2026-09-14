/**
 * Vyuha 2.0 — Context Aggregator
 * ================================
 * Merges local device/transaction signals with the institutional
 * GraphRiskToken into a unified ContextSnapshot.
 *
 * In production: will also handle feature bucketing, timestamp alignment,
 * and missing-evidence defaults.
 * For hackathon: acts as a simple builder.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §5 — Dataflow.
 * Latency budget: Context Aggregation < 2 ms (per §13).
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*

class ContextAggregator {

    /**
     * Builds a ContextSnapshot from raw transaction parameters and optional graph token.
     */
    fun aggregate(
        sessionId: String,
        amountBucket: AmountBucket,
        beneficiaryNovelty: Double,
        channel: String = "UPI",
        communicationActive: Boolean? = null,
        captureRisk: Boolean? = null,
        overlayRisk: Boolean? = null,
        deviationScore: Double? = null,
        graphRiskToken: GraphRiskToken? = null
    ): ContextSnapshot {
        return ContextSnapshot(
            sessionId = sessionId,
            timestampMs = System.currentTimeMillis(),
            transaction = TransactionContext(
                amountBucket = amountBucket,
                beneficiaryNovelty = beneficiaryNovelty,
                channel = channel
            ),
            communication = communicationActive?.let { CommunicationContext(active = it) },
            device = if (captureRisk != null && overlayRisk != null) DeviceContext(captureRisk = captureRisk, overlayRisk = overlayRisk) else null,
            baseline = deviationScore?.let { BaselineContext(deviationScore = it) },
            graphRiskToken = graphRiskToken
        )
    }
}
