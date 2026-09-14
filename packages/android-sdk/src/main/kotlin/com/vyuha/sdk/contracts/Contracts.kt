/**
 * Vyuha 2.0 — Frozen Contract Data Classes
 * ==========================================
 * These Kotlin data classes map 1:1 to the JSON schemas in contracts/.
 * NO module may communicate through ad-hoc dictionaries.
 * Any schema change requires updating both this file AND contracts/.
 *
 * Type convention: All scores use Double for numerical precision
 * consistency with the Python backend and JSON schema definitions.
 */
package com.vyuha.sdk.contracts

import java.util.UUID
import com.google.gson.annotations.SerializedName

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
    val beneficiaryNovelty: Double,
    val channel: String = "UPI",
    val onlinePurchase: Boolean = false,
    val independentlyVerified: Boolean = false,
    val beneficiaryRef: String? = null
)

data class CommunicationContext(
    val active: Boolean
)

data class DeviceContext(
    val captureRisk: Boolean,
    val overlayRisk: Boolean
)

data class BaselineContext(
    val deviationScore: Double
)

data class ContextSnapshot(
    val sessionId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val transaction: TransactionContext,
    val communication: CommunicationContext? = null,
    val device: DeviceContext? = null,
    val baseline: BaselineContext? = null,
    /** Injected from GraphRiskToken when available; null if offline / missing. */
    val graphRiskToken: GraphRiskToken? = null
)

// ── EdgeRiskOutput (contracts/EdgeRiskOutput.schema.json) ───────────

data class ExpertScore(
    val expertId: String,
    val score: Double
)

data class EdgeRiskOutput(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val triageScore: Double,
    val isSparseRouted: Boolean = true,
    val expertScores: List<ExpertScore>
)

// ── GraphRiskToken (contracts/GraphRiskToken.schema.json) ───────────

data class GraphRiskToken(
    @SerializedName("vpa_hash") val vpaHash: String,
    @SerializedName("risk_score") val riskScore: Double?,
    val confidence: Double,
    @SerializedName("reason_codes") val reasonCodes: List<String>,
    @SerializedName("issued_at") val issuedAt: Long,
    @SerializedName("expires_at") val expiresAt: Long,
    @SerializedName("model_version") val modelVersion: String = "unavailable",
    val signature: String,
    @SerializedName("risk_class") val riskClass: String = "UNKNOWN",
    @SerializedName("session_id") val sessionId: String = "",
    val audience: String = "",
    @SerializedName("schema_version") val schemaVersion: Int = 1,
    val algorithm: String = "",
    @SerializedName("key_id") val keyId: String = "",
    @SerializedName("signed_payload") val signedPayload: String = "",
    @SerializedName("graph_as_of") val graphAsOf: Long = 0,
    @SerializedName("data_kind") val dataKind: String = "unknown"
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
    /** P(Coercion | observations so far), range [0, 1]. */
    val pCoercion: Double,
    /** Conformal prediction set, e.g. [SAFE], [RISK], [SAFE, RISK, ABSTAIN]. */
    val uncertaintySet: List<UncertaintyLabel>,
    /** Indicates which evidence sources were unavailable during this evaluation. */
    val missingEvidenceMask: MissingEvidenceMask,
    /** Number of events processed in this session so far. */
    val eventSequenceLength: Int
)

// ── InterventionDecision (contracts/InterventionDecision.schema.json)

data class InterventionDecision(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val actionId: ActionId,
    val beliefScore: Double,
    val uncertainty: Double,
    val reasonCodes: List<String>,
    val counterpartyRisk: Double? = null,
    val templateId: String = "REFLECTION"
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
