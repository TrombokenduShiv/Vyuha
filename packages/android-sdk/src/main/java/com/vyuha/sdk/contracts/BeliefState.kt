package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/BeliefState.schema.json
 *
 * The Bayesian belief state representing the system's current estimate of coercion probability.
 * Updated incrementally as new evidence arrives during a session.
 */
data class BeliefState(
    val sessionId: String,
    val timestampMs: Long,
    /** P(Coercion | observations so far), range [0, 1]. */
    val pCoercion: Double,
    /** Conformal prediction set, e.g. [SAFE], [RISK], [SAFE, RISK], [ABSTAIN]. */
    val uncertaintySet: List<UncertaintyLabel>,
    /** Indicates which evidence sources were unavailable during this evaluation. */
    val missingEvidenceMask: MissingEvidenceMask,
    /** Number of events processed in this session so far. */
    val eventSequenceLength: Int
)

enum class UncertaintyLabel { SAFE, RISK, ABSTAIN }

data class MissingEvidenceMask(
    val graphMissing: Boolean,
    val baselineMissing: Boolean,
    val deviceMissing: Boolean
)
