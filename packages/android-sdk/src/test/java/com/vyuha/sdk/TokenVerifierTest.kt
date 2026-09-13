package com.vyuha.sdk

import com.vyuha.sdk.contracts.GraphRiskToken
import com.vyuha.sdk.security.TokenVerifier
import com.vyuha.sdk.security.TokenVerifier.VerificationResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for HMAC-SHA256 risk-token verification.
 * Validates signature integrity and expiry checking.
 */
class TokenVerifierTest {

    private lateinit var verifier: TokenVerifier

    @Before
    fun setup() {
        verifier = TokenVerifier("vyuha-demo-secret-do-not-use-in-prod")
    }

    @Test
    fun testValidToken_Passes() {
        // Build a token and sign it the same way the backend does
        val token = createSignedToken(
            riskScore = 0.92,
            confidence = 0.88,
            reasonCodes = listOf("HIGH_FAN_IN"),
            expiresAt = System.currentTimeMillis() / 1000 + 3600 // 1 hour from now
        )

        val result = verifier.verify(token)
        assertEquals("Valid token should pass verification", VerificationResult.VALID, result)
    }

    @Test
    fun testTamperedScore_FailsVerification() {
        val token = createSignedToken(
            riskScore = 0.05,
            confidence = 0.95,
            reasonCodes = emptyList(),
            expiresAt = System.currentTimeMillis() / 1000 + 3600
        )

        // Tamper with the risk score
        val tampered = token.copy(riskScore = 0.99)
        val result = verifier.verify(tampered)
        assertEquals("Tampered token should fail", VerificationResult.INVALID_SIGNATURE, result)
    }

    @Test
    fun testExpiredToken_ReturnsExpired() {
        val token = createSignedToken(
            riskScore = 0.05,
            confidence = 0.95,
            reasonCodes = emptyList(),
            expiresAt = System.currentTimeMillis() / 1000 - 100 // Already expired
        )

        val result = verifier.verify(token)
        assertEquals("Expired token should return EXPIRED", VerificationResult.EXPIRED, result)
    }

    @Test
    fun testMalformedToken_ReturnsMalformed() {
        val token = GraphRiskToken(
            vpaHash = "",  // Empty VPA hash
            riskScore = 0.5,
            confidence = 0.5,
            reasonCodes = emptyList(),
            issuedAt = 0,
            expiresAt = 0,
            signature = "some-sig"
        )

        val result = verifier.verify(token)
        assertEquals("Malformed token should return MALFORMED", VerificationResult.MALFORMED, result)
    }

    @Test
    fun testWrongSecret_FailsVerification() {
        val wrongVerifier = TokenVerifier("wrong-secret")
        val token = createSignedToken(
            riskScore = 0.05,
            confidence = 0.95,
            reasonCodes = emptyList(),
            expiresAt = System.currentTimeMillis() / 1000 + 3600
        )

        val result = wrongVerifier.verify(token)
        assertEquals("Wrong secret should fail", VerificationResult.INVALID_SIGNATURE, result)
    }

    /**
     * Create a token signed with the verifier's canonical payload method.
     * This mirrors how the Python backend creates and signs tokens.
     */
    private fun createSignedToken(
        riskScore: Double,
        confidence: Double,
        reasonCodes: List<String>,
        expiresAt: Long
    ): GraphRiskToken {
        val now = System.currentTimeMillis() / 1000
        val unsignedToken = GraphRiskToken(
            vpaHash = "test-vpa-hash",
            riskScore = riskScore,
            confidence = confidence,
            reasonCodes = reasonCodes,
            issuedAt = now,
            expiresAt = expiresAt,
            modelVersion = "hgt-v0.1-mock",
            signature = "" // Will be computed
        )

        // Compute the signature using the verifier's canonical payload
        val canonicalPayload = verifier.buildCanonicalPayload(unsignedToken)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val key = javax.crypto.spec.SecretKeySpec(
            "vyuha-demo-secret-do-not-use-in-prod".toByteArray(), "HmacSHA256"
        )
        mac.init(key)
        val signature = mac.doFinal(canonicalPayload.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return unsignedToken.copy(signature = signature)
    }
}
