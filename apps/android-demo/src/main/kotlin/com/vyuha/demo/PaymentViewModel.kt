/**
 * Vyuha 2.0 — Payment ViewModel
 * ===============================
 * Drives the two vertical slices from the UI layer.
 * Manages the Vyuha Session lifecycle and exposes pipeline state to Compose.
 *
 * Slice 0: Golden Path (known payee, benign context → A0_PASS)
 * Slice 1: Coercion Scenario (unknown VPA + active call → A4+ intervention)
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
        val decision: InterventionDecision
    ) : PaymentUiState()
    data class Completed(val status: FinalStatus) : PaymentUiState()
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
            // 1. Begin payment with safe parameters
            val session = Vyuha.beginPayment(
                vpaHash = sha256("known_payee@upi"),
                amountBucket = AmountBucket.LOW,
                beneficiaryNovelty = 0.05,
                channel = "UPI"
            )
            currentSession = session

            // 2. Update real-time context (benign)
            session.updateContext(
                communicationActive = false,
                captureRisk = false,
                overlayRisk = false,
                deviationScore = 0.10
            )

            // 3. Evaluate
            val decision = session.evaluate()

            if (decision.action == "A0_PASS") {
                _uiState.value = PaymentUiState.Passed(decision)
                session.complete(FinalStatus.PAYMENT_COMPLETED)
            } else {
                _uiState.value = PaymentUiState.Intervention(decision)
            }
        }
    }

    // ── Slice 1: Coercion Scenario ──────────────────────────────────

    fun runCoercionScenario() {
        _uiState.value = PaymentUiState.Processing

        viewModelScope.launch {
            // 1. Begin payment with high-risk parameters
            val session = Vyuha.beginPayment(
                vpaHash = sha256("unknown_scammer@upi"),
                amountBucket = AmountBucket.CRITICAL,
                beneficiaryNovelty = 0.94,
                channel = "UPI"
            )
            currentSession = session

            // 2. Update real-time context (coercion signals)
            session.updateContext(
                communicationActive = true, // on a call
                captureRisk = true,         // screen shared
                overlayRisk = false,
                deviationScore = 0.82
            )

            // 3. Evaluate — expect A4_ISOLATION_BREAK or higher
            val decision = session.evaluate()
            _uiState.value = PaymentUiState.Intervention(decision)
        }
    }

    // ── User responds to intervention ───────────────────────────────

    fun respondToIntervention(response: UserResponse) {
        val session = currentSession ?: return

        viewModelScope.launch {
            if (response == UserResponse.CANCELLED) {
                session.complete(FinalStatus.PAYMENT_CANCELLED)
                _uiState.value = PaymentUiState.Completed(FinalStatus.PAYMENT_CANCELLED)
                return@launch
            }

            // Simulate: user ended the call and stopped screen share
            session.updateContext(
                communicationActive = false,
                captureRisk = false,
                overlayRisk = false
            )

            // Re-evaluate via recordResponse
            val newDecision = session.recordResponse(response)

            if (newDecision.action == "A0_PASS") {
                session.complete(FinalStatus.PAYMENT_COMPLETED)
                _uiState.value = PaymentUiState.Completed(FinalStatus.PAYMENT_COMPLETED)
            } else {
                _uiState.value = PaymentUiState.Intervention(newDecision)
            }
        }
    }

    // ── Reset ───────────────────────────────────────────────────────

    fun reset() {
        currentSession = null
        _uiState.value = PaymentUiState.Idle
    }
}
