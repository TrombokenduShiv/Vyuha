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
        communicationActive: Boolean,
        captureRisk: Boolean = false,
        overlayRisk: Boolean = false,
        deviationScore: Double = 0.0,
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
            communication = CommunicationContext(active = communicationActive),
            device = DeviceContext(captureRisk = captureRisk, overlayRisk = overlayRisk),
            baseline = BaselineContext(deviationScore = deviationScore),
            graphRiskToken = graphRiskToken
        )
    }
}
