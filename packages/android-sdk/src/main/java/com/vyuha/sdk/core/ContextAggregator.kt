package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Context Aggregator — merges local device/transaction signals with the
 * institutional GraphRiskToken into a unified ContextSnapshot.
 *
 * In production, this will also handle feature bucketing, timestamp alignment,
 * and missing-evidence defaults. For Slice 0/1, it acts as a simple builder.
 */
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
