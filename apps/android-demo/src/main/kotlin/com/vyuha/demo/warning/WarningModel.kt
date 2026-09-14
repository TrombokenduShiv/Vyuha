package com.vyuha.demo.warning

data class UiAction(val id: String, val label: String)

data class WarningSpec(
    val uiSpecId: String,
    val title: String,
    val body: String,
    val primaryAction: UiAction?,
    val secondaryAction: UiAction?,
    val cooldownMs: Long = 0,
    val requiresRiskRemediation: Boolean = false,
    val showTrustedVerify: Boolean = false
)

enum class WarningState {
    PASS, MICRO_INQUIRY, REFLECTION, COOLING_DELAY, ISOLATION_BREAK, TRUSTED_VERIFY, STEP_UP_REQUIRED
}

object WarningManager {
    fun spec(state: WarningState): WarningSpec = when (state) {
        WarningState.PASS -> WarningSpec("A0_PASS", "Payment looks normal", "No additional safety check is required.", null, null)
        WarningState.MICRO_INQUIRY -> WarningSpec("A1_MICRO_PROMPT", "Take a moment", "Is someone asking you to make this payment right now?", UiAction("CONTINUE", "I'M MAKING THIS CHOICE"), UiAction("CANCEL", "CANCEL PAYMENT"))
        WarningState.REFLECTION -> WarningSpec("A2_REFLECTION_CHALLENGE", "Take a step back", "Make sure the recipient and purpose match what you intend before continuing.", UiAction("CONTINUE", "I'VE REVIEWED IT"), UiAction("CANCEL", "CANCEL PAYMENT"))
        WarningState.COOLING_DELAY -> WarningSpec("A3_COOLING_DELAY", "Safety pause", "A short cooling period gives you time to reconsider without outside pressure.", UiAction("CONTINUE", "CONTINUE WHEN READY"), UiAction("CANCEL", "CANCEL PAYMENT"), cooldownMs = 20_000L)
        WarningState.ISOLATION_BREAK -> WarningSpec("A4_ISOLATION_BREAK", "End outside influence", "End the active call or screen-sharing session before you continue this high-risk payment.", UiAction("CONTINUE", "I'VE ENDED THE SESSION"), UiAction("CANCEL", "CANCEL PAYMENT"), requiresRiskRemediation = true)
        WarningState.TRUSTED_VERIFY -> WarningSpec("A5_TRUSTED_VERIFY", "Verify independently", "Check this payment with a trusted contact or your bank before proceeding.", UiAction("VERIFIED", "SEND VERIFICATION REQUEST"), UiAction("CANCEL", "CANCEL PAYMENT"), showTrustedVerify = true)
        WarningState.STEP_UP_REQUIRED -> WarningSpec("A6_STEP_UP_REQUIRED", "Additional verification required", "Vyuha has escalated this transaction to the host bank for a stronger verification step.", null, UiAction("CANCEL", "CANCEL PAYMENT"), requiresRiskRemediation = true)
    }
}
