/**
 * Vyuha 2.0 — Public SDK Entry Point
 * ====================================
 * Host bank apps integrate with Vyuha through this single class.
 *
 * Usage (host bank integration):
 *   // 1. Initialize once (e.g., in Application.onCreate)
 *   Vyuha.initialize(VyuhaConfig(graphApiUrl = "http://..."))
 *
 *   // 2a. Full API (async, with prefetch):
 *   val session = Vyuha.getInstance().beginPayment()
 *   session.prefetchGraphRisk(vpaHash)
 *   session.updateContext(snapshot)
 *   val decision = session.evaluate()
 *
 *   // 2b. Convenience API (sync, with mock graph):
 *   val session = Vyuha.beginPayment(vpaHash, amountBucket, novelty, channel)
 *   session.updateContext(communicationActive = true, ...)
 *   val decision = session.evaluate()
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §11 — API List.
 */
package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.network.GraphRiskClient
import com.vyuha.sdk.pipeline.ContextAggregator
import com.vyuha.sdk.security.TokenVerifier

/**
 * SDK configuration.
 * In production: includes bundle paths, signing keys, graph API endpoints, etc.
 */
data class VyuhaConfig(
    /** Base URL for the institutional Graph Risk API. */
    val graphApiUrl: String = "http://10.0.2.2:8080",
    /** If true, SDK operates without attempting network calls. */
    val offlineMode: Boolean = false,
    /** Enable detailed logging for debugging/hackathon. */
    val debugLogging: Boolean = false,
    /** HMAC-SHA256 shared secret for risk-token verification. */
    val graphSigningSecret: String = "vyuha-demo-secret-do-not-use-in-prod"
)

class Vyuha private constructor(
    internal val config: VyuhaConfig
) {
    private val tokenVerifier = TokenVerifier(config.graphSigningSecret)
    internal val graphClient = GraphRiskClient(config.graphApiUrl, tokenVerifier)

    /**
     * Begin a new payment session (full API).
     * Returns a Session that orchestrates the full edge pipeline.
     * Use with prefetchGraphRisk() for async graph fetching.
     */
    fun beginPayment(): Session {
        return Session(graphClient = graphClient, vyuhaConfig = config)
    }

    companion object {
        @Volatile
        private var instance: Vyuha? = null

        /**
         * Initialize the Vyuha SDK. Must be called once (e.g., in Application.onCreate).
         */
        fun initialize(config: VyuhaConfig = VyuhaConfig()): Vyuha {
            return instance ?: synchronized(this) {
                instance ?: Vyuha(config).also { instance = it }
            }
        }

        /**
         * Get the initialized instance.
         * @throws IllegalStateException if initialize() has not been called.
         */
        fun getInstance(): Vyuha {
            return instance ?: throw IllegalStateException(
                "Vyuha.initialize() must be called before getInstance()"
            )
        }

        /**
         * Convenience: Begin a payment session with pre-built context.
         * Uses the local mock graph client for synchronous token generation.
         * Ideal for the demo app and hackathon testing.
         *
         * @param vpaHash SHA-256 hash of the beneficiary VPA.
         * @param amountBucket Bucketed transaction amount.
         * @param beneficiaryNovelty Novelty score [0,1]. 1.0 = completely new payee.
         * @param channel Payment channel (default "UPI").
         * @return A [Session] handle for the active transaction.
         */
        fun beginPayment(
            vpaHash: String,
            amountBucket: AmountBucket,
            beneficiaryNovelty: Double,
            channel: String = "UPI"
        ): Session {
            val vyuha = getInstance()

            // Fetch graph risk token synchronously (mock for hackathon)
            val graphToken = vyuha.graphClient.mockToken(vpaHash)

            return Session(
                graphClient = vyuha.graphClient,
                vyuhaConfig = vyuha.config,
                initialVpaHash = vpaHash,
                initialAmountBucket = amountBucket,
                initialBeneficiaryNovelty = beneficiaryNovelty,
                initialChannel = channel,
                initialGraphToken = graphToken
            )
        }

        /**
         * Reset the singleton (for testing only).
         */
        internal fun resetForTesting() {
            instance = null
        }
    }
}
