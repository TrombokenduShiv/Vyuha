/**
 * DemoBank — PaymentFlowViewModel
 * =================================
 * Single ViewModel that manages the entire payment flow state.
 * Drives the multi-screen navigation and Vyuha SDK integration.
 *
 * KEY POINT: This ViewModel ONLY imports from com.vyuha.sdk —
 * the public API (TransactionInput, DeviceSignals, RiskVerdict, VyuhaSession).
 * It NEVER imports ContextSnapshot, BeliefState, Pipeline, or any internal class.
 *
 * This proves the SDK's API surface is clean enough for a real bank integration.
 */
package com.vyuha.demo.viewmodel

import androidx.lifecycle.ViewModel
import com.vyuha.sdk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pre-defined contacts for the demo.
 */
data class Contact(
    val name: String,
    val vpa: String,
    val isKnown: Boolean
)

/**
 * Flow state — which screen to show.
 */
enum class FlowStep {
    HOME, ENTER_AMOUNT, BENEFICIARY, CONFIRM, INTERVENTION, RESULT
}

/**
 * Result of the payment flow.
 */
enum class PaymentResult {
    SUCCESS, CANCELLED, BLOCKED
}

class PaymentFlowViewModel : ViewModel() {

    // ── Predefined demo contacts ────────────────────────────────────
    val contacts = listOf(
        Contact("Ravi Kumar", "ravi.kumar@okaxis", true),
        Contact("Priya Sharma", "priya.s@ybl", true),
        Contact("Mom", "sunita.devi@sbi", true)
    )

    // ── Flow State ──────────────────────────────────────────────────
    private val _flowStep = MutableStateFlow(FlowStep.HOME)
    val flowStep: StateFlow<FlowStep> = _flowStep.asStateFlow()

    // ── Payment Data ────────────────────────────────────────────────
    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    private val _customVpa = MutableStateFlow("")
    val customVpa: StateFlow<String> = _customVpa.asStateFlow()

    // ── Simulation Toggles ──────────────────────────────────────────
    private val _isOnCall = MutableStateFlow(false)
    val isOnCall: StateFlow<Boolean> = _isOnCall.asStateFlow()

    private val _isScreenShared = MutableStateFlow(false)
    val isScreenShared: StateFlow<Boolean> = _isScreenShared.asStateFlow()

    // ── SDK State ───────────────────────────────────────────────────
    private var vyuhaSession: VyuhaSession? = null

    private val _verdict = MutableStateFlow<RiskVerdict?>(null)
    val verdict: StateFlow<RiskVerdict?> = _verdict.asStateFlow()

    private val _paymentResult = MutableStateFlow<PaymentResult?>(null)
    val paymentResult: StateFlow<PaymentResult?> = _paymentResult.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // ── Actions ─────────────────────────────────────────────────────

    fun goToEnterAmount() {
        _flowStep.value = FlowStep.ENTER_AMOUNT
    }

    fun setAmount(value: String) {
        // Only allow digits and one decimal point
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _amount.value = value
        }
    }

    fun setQuickAmount(value: Int) {
        _amount.value = value.toString()
    }

    fun goToBeneficiary() {
        if (_amount.value.isNotEmpty() && _amount.value.toDoubleOrNull() != null) {
            _flowStep.value = FlowStep.BENEFICIARY
        }
    }

    fun selectContact(contact: Contact) {
        _selectedContact.value = contact
        _customVpa.value = ""
    }

    fun setCustomVpa(vpa: String) {
        _customVpa.value = vpa
        _selectedContact.value = null
    }

    fun toggleOnCall(value: Boolean) {
        _isOnCall.value = value
    }

    fun toggleScreenShared(value: Boolean) {
        _isScreenShared.value = value
    }

    fun goToConfirm() {
        val hasPayee = _selectedContact.value != null || _customVpa.value.isNotBlank()
        if (hasPayee) {
            _flowStep.value = FlowStep.CONFIRM
        }
    }

    /**
     * The critical moment: user taps "Pay Now".
     *
     * SDK Integration:
     *   1. Vyuha.beginPayment(TransactionInput)
     *   2. session.observe(DeviceSignals)
     *   3. session.evaluate() → RiskVerdict
     *   4. Route based on verdict
     */
    fun confirmPayment() {
        _isProcessing.value = true

        val payeeVpa = _selectedContact.value?.vpa ?: _customVpa.value
        val amountInr = _amount.value.toDoubleOrNull() ?: 0.0
        val payeeName = _selectedContact.value?.name ?: payeeVpa

        // ── Step 1: Begin payment ───────────────────────────────
        val session = Vyuha.beginPayment(
            TransactionInput(
                payeeVpa = payeeVpa,
                amountInr = amountInr,
                payeeName = payeeName,
                beneficiaryNovelty = if (_selectedContact.value?.isKnown == true) .1 else .95
            )
        )
        vyuhaSession = session

        // ── Step 2: Observe current signals ─────────────────────
        session.observe(
            DeviceSignals(
                isOnCall = _isOnCall.value,
                isScreenShared = _isScreenShared.value
            )
        )

        // ── Step 3: Evaluate ────────────────────────────────────
        val verdict = session.evaluate()
        _verdict.value = verdict
        _isProcessing.value = false

        // ── Step 4: Route ───────────────────────────────────────
        if (verdict.shouldProceed) {
            // Safe — complete immediately
            session.recordOutcome(PaymentOutcome(completed = true))
            _paymentResult.value = PaymentResult.SUCCESS
            _flowStep.value = FlowStep.RESULT
        } else {
            // Intervention required
            _flowStep.value = FlowStep.INTERVENTION
        }
    }

    /**
     * User responded to intervention (e.g., ended the call).
     * Update signals and re-evaluate.
     */
    fun respondToIntervention() {
        val session = vyuhaSession ?: return
        _isProcessing.value = true

        session.observe(
            DeviceSignals(
                isOnCall = _isOnCall.value,
                isScreenShared = _isScreenShared.value
            )
        )

        val newVerdict = session.respondToIntervention()
        _verdict.value = newVerdict
        _isProcessing.value = false

        if (newVerdict.shouldProceed) {
            session.recordOutcome(PaymentOutcome(completed = true, userApprovedIntervention = true))
            _paymentResult.value = PaymentResult.SUCCESS
            _flowStep.value = FlowStep.RESULT
        } else if (newVerdict.interventionType == InterventionType.HARD_BLOCK) {
            session.recordOutcome(PaymentOutcome(completed = false))
            _paymentResult.value = PaymentResult.BLOCKED
            _flowStep.value = FlowStep.RESULT
        }
        // else: stay on intervention screen with updated verdict
    }

    /**
     * User cancelled the transaction from the intervention screen.
     */
    fun cancelPayment() {
        vyuhaSession?.recordOutcome(PaymentOutcome(completed = false))
        _paymentResult.value = PaymentResult.CANCELLED
        _flowStep.value = FlowStep.RESULT
    }

    /**
     * Reset everything and go back to home.
     */
    fun resetFlow() {
        vyuhaSession = null
        _flowStep.value = FlowStep.HOME
        _amount.value = ""
        _selectedContact.value = null
        _customVpa.value = ""
        _isOnCall.value = false
        _isScreenShared.value = false
        _verdict.value = null
        _paymentResult.value = null
        _isProcessing.value = false
    }
}
