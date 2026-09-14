package com.vyuha.sdk
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vyuha.sdk.contracts.GraphRiskToken
import com.vyuha.sdk.security.TokenVerifier
import org.junit.Assert.*
import org.junit.Test

class TokenV2Test {
    @Test fun pythonTokenVerifiesInKotlinAndCannotBeSubstituted() {
        val gson = Gson()
        val fixture = javaClass.getResourceAsStream("/token_fixture.json")!!.bufferedReader().use {
            gson.fromJson(it, JsonObject::class.java)
        }
        val token = gson.fromJson(fixture["token"], GraphRiskToken::class.java)
        val verifier = TokenVerifier(trustedKeys = mapOf("fixture" to fixture["public_key"].asString),
            audience = "bank", allowSynthetic = true, nowEpochSeconds = { 1001 })
        assertEquals(TokenVerifier.VerificationResult.VALID, verifier.verify(token, token.vpaHash, token.sessionId))
        assertNotEquals(TokenVerifier.VerificationResult.VALID, verifier.verify(token, "b".repeat(64), token.sessionId))
        assertNotEquals(TokenVerifier.VerificationResult.VALID, verifier.verify(token, token.vpaHash, "another-session"))
        assertNotEquals(TokenVerifier.VerificationResult.VALID, verifier.verify(token.copy(riskScore = .01, riskClass = "LOW"), token.vpaHash, token.sessionId))
        val expired = TokenVerifier(trustedKeys = mapOf("fixture" to fixture["public_key"].asString),
            audience = "bank", allowSynthetic = true, nowEpochSeconds = { 1120 })
        assertEquals(TokenVerifier.VerificationResult.EXPIRED, expired.verify(token, token.vpaHash, token.sessionId))
    }
}
