/**
 * Vyuha 2.0 — Graph Risk Client
 * ===============================
 * HTTP client that calls the institutional Graph Risk API
 * and returns a validated GraphRiskToken.
 * Handles timeouts gracefully (returns null → triggers MissingGraphMask).
 */
package com.vyuha.sdk.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.vyuha.sdk.contracts.GraphRiskToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GraphRiskClient(
    private val baseUrl: String = "http://10.0.2.2:8080"  // Android emulator → host
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Fetches graph risk for a VPA hash.
     * Returns null on timeout or error (fail-safe: graphMissing = true).
     */
    suspend fun fetchRisk(vpaHash: String, sessionId: String): GraphRiskToken? {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(5000L) {
                try {
                    val body = gson.toJson(GraphRiskRequest(vpaHash, sessionId))
                        .toRequestBody(jsonMediaType)

                    val request = Request.Builder()
                        .url("$baseUrl/v1/graph-risk")
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        response.body?.string()?.let { json ->
                            gson.fromJson(json, GraphRiskTokenResponse::class.java)?.toDomain()
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    // Network error → fail-safe, graph missing
                    null
                }
            }
        }
    }

    // ── Wire format (snake_case from Python API) ────────────────────

    private data class GraphRiskRequest(
        @SerializedName("vpa_hash") val vpaHash: String,
        @SerializedName("session_id") val sessionId: String
    )

    private data class GraphRiskTokenResponse(
        @SerializedName("vpa_hash") val vpaHash: String,
        @SerializedName("risk_score") val riskScore: Float,
        val confidence: Float,
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
