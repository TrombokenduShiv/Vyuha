package com.vyuha.demo.warning

import androidx.compose.runtime.Composable
import com.vyuha.sdk.contracts.ActionId
import com.vyuha.sdk.contracts.InterventionDecision
import com.vyuha.sdk.contracts.UserResponse

/**
 * Additive bridge between the standalone warning UI prototype and Vyuha's
 * canonical SDK intervention contract. No existing renderer or policy code
 * is replaced by this integration.
 */
@Composable
fun VyuhaWarningBridge(
    decision: InterventionDecision,
    onResponse: (UserResponse) -> Unit
) {
    val state = when (decision.actionId) {
        ActionId.A0_PASS -> WarningState.PASS
        ActionId.A1_MICRO_PROMPT -> WarningState.MICRO_INQUIRY
        ActionId.A2_REFLECTION_CHALLENGE -> WarningState.REFLECTION
        ActionId.A3_COOLING_DELAY -> WarningState.COOLING_DELAY
        ActionId.A4_ISOLATION_BREAK -> WarningState.ISOLATION_BREAK
        ActionId.A5_TRUSTED_VERIFY -> WarningState.TRUSTED_VERIFY
        ActionId.A6_STEP_UP_REQUIRED -> WarningState.STEP_UP_REQUIRED
    }

    VyuhaWarningScreen(
        state = state,
        beliefScore = decision.beliefScore,
        uncertainty = decision.uncertainty,
        reasonCodes = decision.reasonCodes,
        onContinue = { onResponse(UserResponse.CONTINUED) },
        onCancel = { onResponse(UserResponse.CANCELLED) },
        onVerified = { onResponse(UserResponse.VERIFIED) }
    )
}
