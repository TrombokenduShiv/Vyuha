/**
 * Vyuha 2.0 — Public SDK Entry Point
 * ====================================
 * Host bank apps integrate with Vyuha through this single class.
 *
 * Usage:
 *   val vyuha = Vyuha.initialize(VyuhaConfig(graphApiUrl = "http://..."))
 *   val session = vyuha.beginPayment()
 *   session.prefetchGraphRisk(vpaHash)
 *   session.updateContext(snapshot)
 *   val decision = session.evaluate()
 *   // render decision...
 */
package com.vyuha.sdk

import com.vyuha.sdk.network.GraphRiskClient

data class VyuhaConfig(
    val graphApiUrl: String = "http://10.0.2.2:8080",
    val enableLogging: Boolean = false
)

class Vyuha private constructor(
    private val config: VyuhaConfig
) {
    private val graphClient = GraphRiskClient(config.graphApiUrl)

    /**
     * Begin a new payment session.
     * Returns a Session that orchestrates the full edge pipeline.
     */
    fun beginPayment(): Session {
        return Session(graphClient)
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
    }
}
