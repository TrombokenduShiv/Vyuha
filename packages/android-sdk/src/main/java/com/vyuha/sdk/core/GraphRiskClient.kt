package com.vyuha.sdk.core

import com.vyuha.sdk.contracts.*

/**
 * Graph Risk Client — HTTP client for the institutional Graph Risk API.
 *
 * In production: Makes async HTTPS calls to the bank's ST-GNN backend.
 * For Slices 0 & 1: Calls the local FastAPI simulator at localhost:8080.
 *
 * Note: This is a simplified synchronous stub for the deterministic mock.
 * In the real SDK, this will use OkHttp with coroutines and proper error handling.
 */
class GraphRiskClient(
    private val baseUrl: String = "http://10.0.2.2:8080"  // Android emulator → host localhost
) {

    /**
     * Fetch graph risk token for a VPA hash.
     *
     * For Slices 0 & 1, we provide a local mock implementation that
     * doesn't require actual HTTP calls. The Session layer will handle
     * the async HTTP call in the real implementation.
     */
    fun buildRequestUrl(): String = "$baseUrl/v1/graph-risk"

    /**
     * Create a mock GraphRiskToken locally for testing without the server.
     * This mirrors the FastAPI simulator logic for deterministic testing.
     */
    fun mockToken(vpaHash: String): GraphRiskToken {
        // Known safe VPAs
        val knownSafeHashes = setOf(
            sha256("friend@upi"),
            sha256("mom@upi"),
            sha256("known_payee@upi")
        )

        // High risk VPAs
        val highRiskMap = mapOf(
            sha256("unknown_scammer@upi") to listOf("HIGH_FAN_IN", "MULE_RING_CLUSTER"),
            sha256("unknown_vpa@upi") to listOf("HIGH_FAN_IN", "NEW_VPA_BURST")
        )

        val now = System.currentTimeMillis() / 1000

        return when {
            vpaHash in knownSafeHashes -> GraphRiskToken(
                vpaHash = vpaHash,
                riskScore = 0.05,
                confidence = 0.95,
                reasonCodes = emptyList(),
                issuedAt = now,
                expiresAt = now + 3600,
                signature = "mock-sig-safe"
            )
            vpaHash in highRiskMap -> GraphRiskToken(
                vpaHash = vpaHash,
                riskScore = 0.92,
                confidence = 0.88,
                reasonCodes = highRiskMap[vpaHash]!!,
                issuedAt = now,
                expiresAt = now + 3600,
                signature = "mock-sig-risk"
            )
            else -> GraphRiskToken(
                vpaHash = vpaHash,
                riskScore = 0.45,
                confidence = 0.60,
                reasonCodes = listOf("UNKNOWN_VPA"),
                issuedAt = now,
                expiresAt = now + 3600,
                signature = "mock-sig-unknown"
            )
        }
    }

    private fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
