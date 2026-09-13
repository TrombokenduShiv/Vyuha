/**
 * Vyuha 2.0 — Payment ViewModel
 * ===============================
 * Drives the two vertical slices from the UI layer.
 * Manages the Vyuha Session lifecycle and exposes pipeline state to Compose.
 */
package com.vyuha.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyuha.sdk.Session
import com.vyuha.sdk.Vyuha
import com.vyuha.sdk.contracts.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

// ── UI State ────────────────────────────────────────────────────────

sealed class PaymentUiState {
    object Idle : PaymentUiState()
    object Processing : PaymentUiState()
    data class Passed(val decision: InterventionDecision) : PaymentUiState()
    data class Intervention(
        val decision: InterventionDecision,
        val belief: BeliefState?
    ) : PaymentUiState()
    data class Completed(val feedback: OutcomeFeedback) : PaymentUiState()
}

class PaymentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var currentSession: Session? = null

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ── Slice 0: Golden Path ────────────────────────────────────────

    fun runGoldenPath() {
        _uiState.value = PaymentUiState.Processing

        viewModelScope.launch {
            val vyuha = Vyuha.getInstance()
            val session = vyuha.beginPayment()
            currentSession = session

            // 1. Prefetch graph risk for a known payee
            session.prefetchGraphRisk(sha256("known_payee@upi"))

            // 2. Build a benign context snapshot
            val snapshot = ContextSnapshot(
                sessionId = session.sessionId,
                transaction = TransactionContext(
                    amountBucket = AmountBucket.LOW,
                    beneficiaryNovelty = 0.05f,  // well-known payee
                    channel = "UPI"
                ),
                communication = CommunicationContext(active = false),
                device = DeviceContext(captureRisk = false, overlayRisk = false),
                baseline = BaselineContext(deviationScore = 0.10f)
            )
            session.updateContext(snapshot)

            // 3. Evaluate
            val decision = session.evaluate()

            if (decision.actionId == ActionId.A0_PASS) {
                _uiState.value = PaymentUiState.Passed(decision)
            } else {
                // Unexpected — but render it anyway
                _uiState.value = PaymentUiState.Intervention(decision, session.getLastBelief())
            }
        }
    }

    // ── Slice 1: Coercion Scenario ──────────────────────────────────

    fun runCoercionScenario() {
        _uiState.value = PaymentUiState.Processing

        viewModelScope.launch {
            val vyuha = Vyuha.getInstance()
            val session = vyuha.beginPayment()
            currentSession = session

            // 1. Prefetch graph risk for an unknown (high-risk) VPA
            session.prefetchGraphRisk(sha256("unknown_scammer@upi"))

            // 2. Build a coercion context snapshot
            val snapshot = ContextSnapshot(
                sessionId = session.sessionId,
                transaction = TransactionContext(
                    amountBucket = AmountBucket.CRITICAL,  // ₹85,000
                    beneficiaryNovelty = 0.94f,            // never seen before
                    channel = "UPI"
                ),
                communication = CommunicationContext(active = true),  // on a call
                device = DeviceContext(
                    captureRisk = true,    // screen being shared
                    overlayRisk = false
                ),
                baseline = BaselineContext(deviationScore = 0.82f)  // very unusual
            )
            session.updateContext(snapshot)

            // 3. Evaluate — expect A4_ISOLATION_BREAK or higher
            val decision = session.evaluate()
            _uiState.value = PaymentUiState.Intervention(decision, session.getLastBelief())
        }
    }

    // ── User responds to intervention ───────────────────────────────

    fun respondToIntervention() {
        val session = currentSession ?: return

        viewModelScope.launch {
            // Simulate: user ended the call
            session.recordResponse(UserResponse.CONTINUED)

            // Update context: call ended, screen share stopped
            val updatedSnapshot = ContextSnapshot(
                sessionId = session.sessionId,
                transaction = TransactionContext(
                    amountBucket = AmountBucket.CRITICAL,
                    beneficiaryNovelty = 0.94f,
                    channel = "UPI"
                ),
                communication = CommunicationContext(active = false),  // call ended
                device = DeviceContext(captureRisk = false, overlayRisk = false),
                baseline = BaselineContext(deviationScore = 0.82f)
            )
            session.updateContext(updatedSnapshot)

            // Re-evaluate — should step down (e.g., to A5_TRUSTED_VERIFY or lower)
            val newDecision = session.evaluate()

            if (newDecision.actionId == ActionId.A0_PASS) {
                val feedback = session.complete(FinalStatus.PAYMENT_COMPLETED)
                _uiState.value = PaymentUiState.Completed(feedback)
            } else {
                _uiState.value = PaymentUiState.Intervention(newDecision, session.getLastBelief())
            }
        }
    }

    // ── Reset ───────────────────────────────────────────────────────

    fun reset() {
        currentSession = null
        _uiState.value = PaymentUiState.Idle
    }
}
