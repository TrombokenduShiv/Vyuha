/**
 * Vyuha 2.0 — Frozen Contract Data Classes
 * ==========================================
 * These Kotlin data classes map 1:1 to the JSON schemas in contracts/.
 * NO module may communicate through ad-hoc dictionaries.
 * Any schema change requires updating both this file AND contracts/.
 */
package com.vyuha.sdk.contracts

import java.util.UUID

// ── Enums ───────────────────────────────────────────────────────────

enum class AmountBucket { LOW, MEDIUM, HIGH, CRITICAL }

enum class ActionId {
    A0_PASS,
    A1_MICRO_PROMPT,
    A2_REFLECTION_CHALLENGE,
    A3_COOLING_DELAY,
    A4_ISOLATION_BREAK,
    A5_TRUSTED_VERIFY,
    A6_STEP_UP_REQUIRED
}

enum class UncertaintyLabel { SAFE, RISK, ABSTAIN }

enum class EscalationLevel { PRIMARY, SECONDARY, BANK_FRAUD_DESK }

enum class SafetyCircleStatus { PENDING, ACKNOWLEDGED, VERIFIED_SAFE, VERIFIED_RISK, TIMEOUT }

enum class UserResponse { CANCELLED, CONTINUED, VERIFIED, TIMEOUT, IGNORED, NONE }

enum class FinalStatus { PAYMENT_COMPLETED, PAYMENT_CANCELLED, INSTITUTION_BLOCKED, UNKNOWN }

enum class GroundTruthLabel { FRAUD, LEGITIMATE, UNKNOWN }

// ── ContextSnapshot (contracts/ContextSnapshot.schema.json) ─────────

data class TransactionContext(
    val amountBucket: AmountBucket,
    val beneficiaryNovelty: Float,
    val channel: String = "UPI"
)

data class CommunicationContext(
    val active: Boolean
)

data class DeviceContext(
    val captureRisk: Boolean,
    val overlayRisk: Boolean
)

data class BaselineContext(
    val deviationScore: Float
)

data class ContextSnapshot(
    val sessionId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val transaction: TransactionContext,
    val communication: CommunicationContext,
    val device: DeviceContext,
    val baseline: BaselineContext
)

// ── EdgeRiskOutput (contracts/EdgeRiskOutput.schema.json) ───────────

data class ExpertScore(
    val expertId: String,
    val score: Float
)

data class EdgeRiskOutput(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val triageScore: Float,
    val isSparseRouted: Boolean = true,
    val expertScores: List<ExpertScore>
)

// ── GraphRiskToken (contracts/GraphRiskToken.schema.json) ───────────

data class GraphRiskToken(
    val vpaHash: String,
    val riskScore: Float,
    val confidence: Float,
    val reasonCodes: List<String>,
    val issuedAt: Long,
    val expiresAt: Long,
    val modelVersion: String = "hgt-v0.1-mock",
    val signature: String
)

// ── BeliefState (contracts/BeliefState.schema.json) ─────────────────

data class MissingEvidenceMask(
    val graphMissing: Boolean = false,
    val baselineMissing: Boolean = false,
    val deviceMissing: Boolean = false
)

data class BeliefState(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val pCoercion: Float,
    val uncertaintySet: List<UncertaintyLabel>,
    val missingEvidenceMask: MissingEvidenceMask,
    val eventSequenceLength: Int
)

// ── InterventionDecision (contracts/InterventionDecision.schema.json)

data class InterventionDecision(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val actionId: ActionId,
    val beliefScore: Float,
    val uncertainty: Float,
    val reasonCodes: List<String>
)

// ── SafetyCircleEvent (contracts/SafetyCircleEvent.schema.json) ─────

data class SafetyCircleEvent(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val escalationLevel: EscalationLevel,
    val messageTemplateId: String? = null,
    val status: SafetyCircleStatus
)

// ── OutcomeFeedback (contracts/OutcomeFeedback.schema.json) ─────────

data class OutcomeFeedback(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val actionId: String,
    val userResponse: UserResponse,
    val finalStatus: FinalStatus,
    val groundTruthFraudLabel: GroundTruthLabel = GroundTruthLabel.UNKNOWN
)
