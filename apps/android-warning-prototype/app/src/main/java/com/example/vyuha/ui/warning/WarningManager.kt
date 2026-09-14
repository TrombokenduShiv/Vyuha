package com.example.vyuha.ui.warning

data class UiAction(
    val id: String,
    val labelRes: String
)

data class WarningSpec(
    val uiSpecId: String,
    val titleRes: String,
    val bodyRes: String,
    val primaryAction: UiAction,
    val secondaryAction: UiAction?,
    val cooldownMs: Long = 0,
    val requiresRiskRemediation: Boolean = false,
    val showTrustedVerify: Boolean = false,
    val accessibilityHintRes: String? = null
)

enum class WarningState {
    PASS,
    MICRO_INQUIRY,
    REFLECTION,
    ISOLATION_BREAK,
    TRUSTED_VERIFY,
    RESOLUTION
}

class WarningManager {
    fun getWarningSpec(state: WarningState): WarningSpec {
        return when (state) {
            WarningState.PASS -> WarningSpec("PASS", "", "", UiAction("NO_ACTION", ""), null)
            WarningState.MICRO_INQUIRY -> WarningSpec("MICRO_INQUIRY", "Take a moment", "Is someone asking you to make this payment right now?", UiAction("SOMEONE_DIRECTING", "YES, I WAS ASKED"), UiAction("CONFIRM_SAFE", "NO, THIS WAS MY CHOICE"), accessibilityHintRes = "Choose whether someone is directing this payment.")
            WarningState.REFLECTION -> WarningSpec("REFLECTION", "Take a step back", "You can pause before making this payment. Make sure this is a decision you want to make.", UiAction("CONTINUE", "CONTINUE"), UiAction("CANCEL", "CANCEL"), accessibilityHintRes = "Review your decision before continuing.")
            WarningState.ISOLATION_BREAK -> WarningSpec("ISOLATION_BREAK", "Pause before continuing", "We're giving you a moment to make this decision independently.", UiAction("CONTINUE_AFTER_COOLDOWN", "CONTINUE WHEN READY"), UiAction("CANCEL", "CANCEL"), cooldownMs = 20_000L, requiresRiskRemediation = true, accessibilityHintRes = "Wait for the safety pause before continuing.")
            WarningState.TRUSTED_VERIFY -> WarningSpec("TRUSTED_VERIFY", "Verify before continuing", "You can check this payment with someone you trust or your bank.", UiAction("TRUSTED_VERIFIED", "CONTACT TRUSTED SUPPORT"), UiAction("CANCEL", "I'LL VERIFY MYSELF"), showTrustedVerify = true, accessibilityHintRes = "Choose an independent way to verify this payment.")
            WarningState.RESOLUTION -> WarningSpec("RESOLUTION", "Safety check complete", "You can now choose how you want to proceed.", UiAction("RESOLUTION_CONTINUE", "CONTINUE"), UiAction("CANCEL", "CANCEL"), accessibilityHintRes = "Choose whether to continue or cancel.")
        }
    }
}
