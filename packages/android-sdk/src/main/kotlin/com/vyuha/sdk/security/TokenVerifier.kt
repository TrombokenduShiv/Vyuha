/**
 * Vyuha 2.0 — Token Verifier
 * ============================
 * Validates the HMAC-SHA256 signature on incoming GraphRiskTokens
 * to ensure they haven't been tampered with in transit.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §12 — Inter-Module Schemas.
 * The backend signs tokens with HMAC-SHA256 over the canonical JSON payload.
 *
 * Security note: In production, the signing secret would be provisioned
 * via secure key management, not hardcoded. For the hackathon, we use
 * a shared demo secret that matches the backend's SIGNING_SECRET.
 */
package com.vyuha.sdk.security

import com.vyuha.sdk.contracts.GraphRiskToken
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TokenVerifier(
    private val signingSecret: String = "vyuha-demo-secret-do-not-use-in-prod"
) {

    /**
     * Result of token verification.
     */
    enum class VerificationResult {
        /** Token signature is valid and token is not expired. */
        VALID,
        /** Token has expired (expiresAt < now). */
        EXPIRED,
        /** HMAC signature does not match the payload. */
        INVALID_SIGNATURE,
        /** Token is missing required fields or is otherwise malformed. */
        MALFORMED
    }

    /**
     * Verify a GraphRiskToken's signature and expiry.
     *
     * Steps:
     * 1. Check required fields are non-empty
     * 2. Verify HMAC-SHA256 signature matches the canonical payload
     * 3. Check token has not expired
     *
     * @return [VerificationResult] indicating the outcome.
     */
    fun verify(token: GraphRiskToken): VerificationResult {
        // 1. Basic sanity checks
        if (token.vpaHash.isBlank() || token.signature.isBlank()) {
            return VerificationResult.MALFORMED
        }

        // 2. Rebuild the canonical payload (must match backend's _sign_token)
        val canonicalPayload = buildCanonicalPayload(token)
        val expectedSignature = computeHmac(canonicalPayload)

        if (expectedSignature != token.signature) {
            return VerificationResult.INVALID_SIGNATURE
        }

        // 3. Check expiry
        val nowSec = System.currentTimeMillis() / 1000
        if (nowSec > token.expiresAt) {
            return VerificationResult.EXPIRED
        }

        return VerificationResult.VALID
    }

    /**
     * Build the canonical JSON string for signature verification.
     * Must exactly match the Python backend's json.dumps(payload, sort_keys=True, separators=(",", ":"))
     *
     * The backend signs over:
     * {"confidence":0.95,"expires_at":1694603600,"issued_at":1694600000,
     *  "model_version":"hgt-v0.1-mock","reason_codes":[],"risk_score":0.05,
     *  "vpa_hash":"abc123"}
     */
    internal fun buildCanonicalPayload(token: GraphRiskToken): String {
        val reasonCodesJson = token.reasonCodes.joinToString(",") { "\"$it\"" }

        // Sort keys alphabetically and use compact separators (no spaces)
        // to match Python's json.dumps(sort_keys=True, separators=(",",":"))
        return buildString {
            append("{")
            append("\"confidence\":${formatDouble(token.confidence)}")
            append(",\"expires_at\":${token.expiresAt}")
            append(",\"issued_at\":${token.issuedAt}")
            append(",\"model_version\":\"${token.modelVersion}\"")
            append(",\"reason_codes\":[${reasonCodesJson}]")
            append(",\"risk_score\":${formatDouble(token.riskScore)}")
            append(",\"vpa_hash\":\"${token.vpaHash}\"")
            append("}")
        }
    }

    /**
     * Format a Double to match Python's default JSON serialization.
     * Python outputs: 0.05, 0.95, 0.92, etc.
     */
    private fun formatDouble(value: Double): String {
        // Remove trailing zeros but keep at least one decimal
        val formatted = value.toBigDecimal().stripTrailingZeros().toPlainString()
        return if ("." in formatted) formatted else "$formatted.0"
    }

    /**
     * Compute HMAC-SHA256 over the given data using the signing secret.
     */
    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}
