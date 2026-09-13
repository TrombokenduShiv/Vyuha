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
