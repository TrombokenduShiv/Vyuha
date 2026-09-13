package com.vyuha.demo

import android.app.Application
import com.vyuha.sdk.Vyuha
import com.vyuha.sdk.VyuhaConfig

/**
 * DemoBankApplication
 * ====================
 * Initializes the Vyuha SDK at application startup.
 *
 * This is how a real bank app would integrate:
 *   1. Add Vyuha SDK as a dependency
 *   2. Call Vyuha.initialize() in Application.onCreate()
 *   3. Done. The rest of the SDK usage is per-transaction.
 */
class DemoBankApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize the Vyuha Edge SDK
        // In production: provide real API URL and signing secret
        Vyuha.initialize(
            VyuhaConfig(
                graphApiUrl = "http://10.0.2.2:8080",
                debugLogging = true,
                graphSigningSecret = "vyuha-demo-secret-do-not-use-in-prod"
            )
        )
    }
}
