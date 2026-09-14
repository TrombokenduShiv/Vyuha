package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.BeliefUpdater
import com.vyuha.sdk.pipeline.TriageGate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the Bayesian log-odds Belief Updater.
 * Validates sequential accumulation of P(Coercion).
 */
class BeliefUpdaterTest {

    private lateinit var beliefUpdater: BeliefUpdater
    private lateinit var triageGate: TriageGate

    @Before
    fun setup() {
        beliefUpdater = BeliefUpdater()
        triageGate = TriageGate()
    }

    @Test
    fun testBenignSignals_LowCoercion() {
        val snapshot = buildSnapshot(
            communicationActive = false,
            amountBucket = AmountBucket.LOW,
            beneficiaryNovelty = 0.1
        )
        val graphToken = buildSafeToken()
        val edgeOutput = triageGate.evaluate(snapshot, graphToken)
        val belief = beliefUpdater.update("test-1", edgeOutput, graphToken, snapshot)

        assertTrue("Benign signals should yield low P(Coercion): ${belief.pCoercion}",
            belief.pCoercion < 0.3)
        assertTrue("Uncertainty set should contain SAFE",
            UncertaintyLabel.SAFE in belief.uncertaintySet)
    }

    @Test
    fun testCoercionSignals_HighCoercion() {
        val snapshot = buildSnapshot(
            communicationActive = true,
            amountBucket = AmountBucket.CRITICAL,
            beneficiaryNovelty = 0.95,
            captureRisk = true,
            deviationScore = 0.85
        )
        val graphToken = buildRiskyToken()
        val edgeOutput = triageGate.evaluate(snapshot, graphToken)
        val belief = beliefUpdater.update("test-2", edgeOutput, graphToken, snapshot)

        assertTrue("Coercion signals should yield high P(Coercion): ${belief.pCoercion}",
            belief.pCoercion > 0.7)
        assertTrue("Uncertainty set should contain RISK",
            UncertaintyLabel.RISK in belief.uncertaintySet)
    }

    @Test
    fun testSequentialAccumulation_RisesWithRepeatedRisk() {
        val snapshot = buildSnapshot(
            communicationActive = true,
            amountBucket = AmountBucket.HIGH,
            beneficiaryNovelty = 0.8
        )
        val graphToken = buildRiskyToken()
        val edgeOutput = triageGate.evaluate(snapshot, graphToken)

        // First evaluation
        val belief1 = beliefUpdater.update("test-3", edgeOutput, graphToken, snapshot)
        // Second evaluation with same risk signals — should accumulate
        val belief2 = beliefUpdater.update("test-3", edgeOutput, graphToken, snapshot)

        assertTrue("Repeated risk should increase P(Coercion): ${belief1.pCoercion} -> ${belief2.pCoercion}",
            belief2.pCoercion >= belief1.pCoercion)
        assertEquals("Event sequence should increment", 2, belief2.eventSequenceLength)
    }

    @Test
    fun testSignalsClear_CoercionDrops() {
        // First: risky signals
        val riskySnapshot = buildSnapshot(communicationActive = true, amountBucket = AmountBucket.CRITICAL)
        val riskyToken = buildRiskyToken()
        val riskyEdge = triageGate.evaluate(riskySnapshot, riskyToken)
        val beliefRisky = beliefUpdater.update("test-4", riskyEdge, riskyToken, riskySnapshot)

        // Then: signals clear (call ended, benign context)
        val safeSnapshot = buildSnapshot(communicationActive = false, amountBucket = AmountBucket.LOW)
        val safeToken = buildSafeToken()
        val safeEdge = triageGate.evaluate(safeSnapshot, safeToken)
        val beliefSafe = beliefUpdater.update("test-4", safeEdge, safeToken, safeSnapshot)

        assertTrue("Cleared signals should reduce P(Coercion): ${beliefRisky.pCoercion} -> ${beliefSafe.pCoercion}",
            beliefSafe.pCoercion < beliefRisky.pCoercion)
    }

    @Test
    fun testReset_ClearsState() {
        val snapshot = buildSnapshot(communicationActive = true)
        val graphToken = buildRiskyToken()
        val edgeOutput = triageGate.evaluate(snapshot, graphToken)
        beliefUpdater.update("test-5", edgeOutput, graphToken, snapshot)

        beliefUpdater.reset()

        // After reset, should behave like fresh updater
        val freshSnapshot = buildSnapshot(communicationActive = false)
        val freshToken = buildSafeToken()
        val freshEdge = triageGate.evaluate(freshSnapshot, freshToken)
        val belief = beliefUpdater.update("test-5-reset", freshEdge, freshToken, freshSnapshot)

        assertEquals("Event count should be 1 after reset", 1, belief.eventSequenceLength)
    }

    @Test
    fun testPCoercion_AlwaysBounded() {
        // Run many iterations with extreme signals
        repeat(20) {
            val snapshot = buildSnapshot(
                communicationActive = true,
                amountBucket = AmountBucket.CRITICAL,
                beneficiaryNovelty = 1.0,
                captureRisk = true,
                deviationScore = 1.0
            )
            val graphToken = buildRiskyToken()
            val edgeOutput = triageGate.evaluate(snapshot, graphToken)
            val belief = beliefUpdater.update("test-bounded", edgeOutput, graphToken, snapshot)
            assertTrue("pCoercion must be in [0,1]: ${belief.pCoercion}",
                belief.pCoercion in 0.0..1.0)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun buildSnapshot(
        communicationActive: Boolean = false,
        amountBucket: AmountBucket = AmountBucket.LOW,
        beneficiaryNovelty: Double = 0.1,
        captureRisk: Boolean = false,
        deviationScore: Double = 0.1
    ) = ContextSnapshot(
        sessionId = "test",
        transaction = TransactionContext(amountBucket, beneficiaryNovelty),
        communication = CommunicationContext(communicationActive),
        device = DeviceContext(captureRisk = captureRisk, overlayRisk = false),
        baseline = BaselineContext(deviationScore = deviationScore)
    )

    private fun buildSafeToken() = GraphRiskToken(
        vpaHash = "safe", riskScore = 0.05, confidence = 0.95,
        reasonCodes = emptyList(), issuedAt = 0, expiresAt = 0, signature = "sig"
    )

    private fun buildRiskyToken() = GraphRiskToken(
        vpaHash = "risky", riskScore = 0.92, confidence = 0.88,
        reasonCodes = listOf("HIGH_FAN_IN"), issuedAt = 0, expiresAt = 0, signature = "sig"
    )
}
