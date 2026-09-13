/**
 * Vyuha 2.0 — Graph Risk Client
 * ===============================
 * HTTP client that calls the institutional Graph Risk API
 * and returns a validated GraphRiskToken.
 * Handles timeouts gracefully (returns null → triggers MissingGraphMask).
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §6 — Deployment Model.
 * Latency budget: Graph API (Async) < 120 ms p95 (per §13).
 */
package com.vyuha.sdk.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.vyuha.sdk.contracts.GraphRiskToken
import com.vyuha.sdk.security.TokenVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class GraphRiskClient(
    private val baseUrl: String = "http://10.0.2.2:8080",  // Android emulator → host
    private val tokenVerifier: TokenVerifier? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    companion object {
        /** Maximum retries for transient network failures. */
        private const val MAX_RETRIES = 1
        /** Backoff between retries in milliseconds. */
        private const val RETRY_BACKOFF_MS = 500L
    }

    /**
     * Fetches graph risk for a VPA hash.
     * Retries once on transient failure with 500ms backoff.
     * Returns null on timeout or persistent error (fail-safe: graphMissing = true).
     */
    suspend fun fetchRisk(vpaHash: String, sessionId: String): GraphRiskToken? {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(5000L) {
                var lastException: Exception? = null
                repeat(MAX_RETRIES + 1) { attempt ->
                    try {
                        val result = executeGraphRequest(vpaHash, sessionId)
                        if (result != null) return@withTimeoutOrNull result
                    } catch (e: Exception) {
                        lastException = e
                        if (attempt < MAX_RETRIES) {
                            kotlinx.coroutines.delay(RETRY_BACKOFF_MS)
                        }
                    }
                }
                // All retries exhausted → fail-safe
                null
            }
        }
    }

    /**
     * Execute a single graph risk API call.
     */
    private fun executeGraphRequest(vpaHash: String, sessionId: String): GraphRiskToken? {
        val body = gson.toJson(GraphRiskRequest(vpaHash, sessionId))
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/v1/graph-risk")
            .post(body)
            .build()

        val response = client.newCall(request).execute()

        return if (response.isSuccessful) {
            response.body?.string()?.let { json ->
                val token = gson.fromJson(json, GraphRiskTokenResponse::class.java)?.toDomain()
                // Verify token signature if verifier is configured
                if (token != null && tokenVerifier != null) {
                    val result = tokenVerifier.verify(token)
                    if (result == TokenVerifier.VerificationResult.VALID) token else null
                } else {
                    token
                }
            }
        } else {
            null
        }
    }

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
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ── Wire format (snake_case from Python API) ────────────────────

    private data class GraphRiskRequest(
        @SerializedName("vpa_hash") val vpaHash: String,
        @SerializedName("session_id") val sessionId: String
    )

    private data class GraphRiskTokenResponse(
        @SerializedName("vpa_hash") val vpaHash: String,
        @SerializedName("risk_score") val riskScore: Double,
        val confidence: Double,
        @SerializedName("reason_codes") val reasonCodes: List<String>,
        @SerializedName("issued_at") val issuedAt: Long,
        @SerializedName("expires_at") val expiresAt: Long,
        @SerializedName("model_version") val modelVersion: String,
        val signature: String
    ) {
        fun toDomain() = GraphRiskToken(
            vpaHash = vpaHash,
            riskScore = riskScore,
            confidence = confidence,
            reasonCodes = reasonCodes,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            modelVersion = modelVersion,
            signature = signature
        )
    }
}
