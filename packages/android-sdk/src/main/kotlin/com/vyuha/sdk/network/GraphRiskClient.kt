package com.vyuha.sdk.network
import com.google.gson.Gson
import com.vyuha.sdk.contracts.GraphRiskToken
import com.vyuha.sdk.security.TokenVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Async receiver lookup through the host institution's authenticated gateway.
 * Bank service credentials must never be embedded in the handset.
 */
class GraphRiskClient(
    private val baseUrl: String = "http://10.0.2.2:8080",
    private val tokenVerifier: TokenVerifier = TokenVerifier()
) {
    private val client = OkHttpClient.Builder().callTimeout(1500, TimeUnit.MILLISECONDS).build()
    private val gson = Gson()
    suspend fun fetchRisk(vpaHash: String, sessionId: String): GraphRiskToken? = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf("vpa_hash" to vpaHash, "session_id" to sessionId))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$baseUrl/v1/graph-risk").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string() ?: return@withContext null
                if (text.length > 16384) return@withContext null
                val token = gson.fromJson(text, GraphRiskToken::class.java)
                token.takeIf { tokenVerifier.verify(it, vpaHash, sessionId) == TokenVerifier.VerificationResult.VALID }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    @Deprecated("Unknown receiver evidence is not a scored mock")
    fun mockToken(vpaHash: String): GraphRiskToken {
        val now = System.currentTimeMillis() / 1000
        return GraphRiskToken(vpaHash, null, 0.0, listOf("GRAPH_UNAVAILABLE"), now, now + 120, signature = "")
    }
}
