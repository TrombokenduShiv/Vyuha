package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/EdgeRiskOutput.schema.json
 *
 * The output of the Sparse Risk MoE (Triage + Experts).
 * Contains the triage score and individual expert scores.
 */
data class EdgeRiskOutput(
    val sessionId: String,
    val timestampMs: Long,
    val triageScore: Double,
    val isSparseRouted: Boolean = false,
    val expertScores: List<ExpertScore>
)

data class ExpertScore(
    val expertId: String,
    val score: Double
)
