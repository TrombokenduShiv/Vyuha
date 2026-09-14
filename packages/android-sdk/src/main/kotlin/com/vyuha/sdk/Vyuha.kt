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
    val graphSigningSecret: String? = null,
    val graphPublicKeys: Map<String, String> = emptyMap(),
    val graphAudience: String = "vyuha-demo",
    val allowSyntheticEvidence: Boolean = false
)

class Vyuha private constructor(
    internal val config: VyuhaConfig
) {
    private val tokenVerifier = TokenVerifier(trustedKeys = config.graphPublicKeys,
        audience = config.graphAudience, allowSynthetic = config.allowSyntheticEvidence)
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
        internal fun beginPaymentInternal(
            vpaHash: String,
            amountBucket: AmountBucket,
            beneficiaryNovelty: Double,
            channel: String = "UPI",
            onlinePurchase: Boolean = false
        ): Session {
            val vyuha = getInstance()

            // Fetch graph risk token synchronously (mock for hackathon)
            val graphToken: GraphRiskToken? = null

            return Session(
                graphClient = vyuha.graphClient,
                vyuhaConfig = vyuha.config,
                initialVpaHash = vpaHash,
                initialAmountBucket = amountBucket,
                initialBeneficiaryNovelty = beneficiaryNovelty,
                initialChannel = channel,
                initialGraphToken = graphToken,
                initialOnlinePurchase = onlinePurchase
            )
        }

        // ── Clean Public API ────────────────────────────────────────

        /**
         * Begin a new payment session.
         *
         * This is the primary entry point for host bank apps.
         * The SDK handles VPA hashing, amount bucketing, and novelty
         * estimation internally — the bank app just provides raw values.
         *
         * Usage:
         *   val session = Vyuha.beginPayment(TransactionInput(
         *       payeeVpa = "merchant@upi",
         *       amountInr = 5000.0,
         *       payeeName = "Ravi Kumar"
         *   ))
         *   session.observe(DeviceSignals(isOnCall = false))
         *   val verdict = session.evaluate()
         *
         * @param transaction The transaction details.
         * @return A [VyuhaSession] handle for the active payment.
         */
        fun beginPayment(transaction: TransactionInput): VyuhaSession {
            val vpaHash = VyuhaSession.hashVpa(transaction.payeeVpa)
            val amountBucket = VyuhaSession.bucketizeAmount(transaction.amountInr)
            require(transaction.amountInr.isFinite() && transaction.amountInr > 0)
            require(transaction.payeeVpa.isNotBlank())
            val novelty = transaction.beneficiaryNovelty

            val internalSession = beginPaymentInternal(
                vpaHash = vpaHash,
                amountBucket = amountBucket,
                beneficiaryNovelty = novelty,
                channel = transaction.channel,
                onlinePurchase = transaction.onlinePurchase
            )

            return VyuhaSession(internalSession, transaction)
        }

        /** Compatibility entry point for the existing host simulator. */
        fun beginPayment(vpaHash: String, amountBucket: AmountBucket,
                         beneficiaryNovelty: Double, channel: String = "UPI"): Session =
            beginPaymentInternal(vpaHash, amountBucket, beneficiaryNovelty, channel)

        /**
         * Reset the singleton (for testing only).
         */
        internal fun resetForTesting() {
            instance = null
        }
    }
}
