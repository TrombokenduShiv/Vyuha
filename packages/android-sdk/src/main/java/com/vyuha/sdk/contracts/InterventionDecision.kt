package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/InterventionDecision.schema.json
 *
 * The final output of the edge pipeline: which intervention action to take.
 * Consumed by the host app's ActionRenderer to display the appropriate UI.
 */
data class InterventionDecision(
    val sessionId: String,
    val timestampMs: Long,
    val actionId: ActionId,
    val beliefScore: Double,
    val uncertainty: Double,
    val reasonCodes: List<String>
)

/**
 * Frozen action space (A0–A6) per Decision D9.
 */
enum class ActionId {
    A0_PASS,
    A1_MICRO_PROMPT,
    A2_REFLECTION_CHALLENGE,
    A3_COOLING_DELAY,
    A4_ISOLATION_BREAK,
    A5_TRUSTED_VERIFY,
    A6_STEP_UP_REQUIRED
}
