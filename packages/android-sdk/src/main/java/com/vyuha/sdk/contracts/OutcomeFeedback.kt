package com.vyuha.sdk.contracts

/**
 * Mirrors contracts/OutcomeFeedback.schema.json
 *
 * Feedback recorded after a session completes or a user responds to an intervention.
 * Used for offline policy evaluation and model retraining.
 */
data class OutcomeFeedback(
    val sessionId: String,
    val timestampMs: Long,
    val actionId: String,
    val userResponse: UserResponse,
    val finalStatus: FinalStatus,
    val groundTruthFraudLabel: FraudLabel
)

enum class UserResponse { CANCELLED, CONTINUED, VERIFIED, TIMEOUT, IGNORED, NONE }
enum class FinalStatus { PAYMENT_COMPLETED, PAYMENT_CANCELLED, INSTITUTION_BLOCKED, UNKNOWN }
enum class FraudLabel { FRAUD, LEGITIMATE, UNKNOWN }
