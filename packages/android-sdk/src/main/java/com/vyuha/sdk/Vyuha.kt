package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.core.*

/**
 * Vyuha — The main entry point of the Vyuha Edge SDK.
 *
 * Host bank apps integrate with this singleton to:
 * 1. Initialize the SDK with configuration.
 * 2. Begin a payment session which returns a [Session] handle.
 *
 * Matches the API contract from ARCHITECTURE_FREEZE_V1 §11:
 *   Vyuha.initialize(config: VyuhaConfig)
 *   Vyuha.beginPayment(ctx: TransactionContext): Session
 */
object Vyuha {

    private var initialized = false
    private var config: VyuhaConfig? = null
    private val graphRiskClient = GraphRiskClient()

    /**
     * Initialize the SDK. Must be called once before any payment sessions.
     */
    fun initialize(config: VyuhaConfig) {
        this.config = config
        this.initialized = true
    }

    /**
     * Begin a new payment session.
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
        check(initialized) { "Vyuha SDK not initialized. Call Vyuha.initialize() first." }

        // Fetch graph risk token (synchronous mock for Slices 0/1)
        val graphToken = graphRiskClient.mockToken(vpaHash)

        return Session(
            vpaHash = vpaHash,
            amountBucket = amountBucket,
            beneficiaryNovelty = beneficiaryNovelty,
            channel = channel,
            graphRiskToken = graphToken
        )
    }
}

/**
 * SDK configuration. In production, this includes bundle paths, signing keys, etc.
 */
data class VyuhaConfig(
    val graphApiUrl: String = "http://10.0.2.2:8080",
    val offlineMode: Boolean = false,
    val debugLogging: Boolean = false
)
