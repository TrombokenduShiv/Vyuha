package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/GraphRiskToken.schema.json
 *
 * A signed risk assessment token issued by the institutional graph backend.
 * Contains the ST-GNN's risk score for a beneficiary VPA.
 */
data class GraphRiskToken(
    val vpaHash: String,
    val riskScore: Double,
    val confidence: Double,
    val reasonCodes: List<String>,
    val issuedAt: Long,
    val expiresAt: Long,
    val modelVersion: String = "hgt-v0.1-mock",
    val signature: String
)
