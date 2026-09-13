package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/ContextSnapshot.schema.json
 *
 * Represents the aggregated context for a single evaluation point in a payment session.
 * Contains transaction metadata, communication state, device risk signals, and behavioral baseline.
 */
data class ContextSnapshot(
    val sessionId: String,
    val timestampMs: Long,
    val transaction: TransactionContext,
    val communication: CommunicationContext,
    val device: DeviceContext,
    val baseline: BaselineContext,
    /** Injected from GraphRiskToken when available; null if offline / missing. */
    val graphRiskToken: GraphRiskToken? = null
)

data class TransactionContext(
    val amountBucket: AmountBucket,
    val beneficiaryNovelty: Double,
    val channel: String
)

enum class AmountBucket { LOW, MEDIUM, HIGH, CRITICAL }

data class CommunicationContext(
    val active: Boolean
)

data class DeviceContext(
    val captureRisk: Boolean,
    val overlayRisk: Boolean
)

data class BaselineContext(
    val deviationScore: Double
)
