package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.core.*
import org.junit.Test
import org.junit.Assert.*

class FailurePhilosophyTest {

    private fun createDefaultSnapshot(
        graphToken: GraphRiskToken? = GraphRiskToken("test", 0.0),
        baseline: Double? = 0.0,
        deviceCapture: Boolean? = false,
        deviceOverlay: Boolean? = false,
        commActive: Boolean? = false,
        amountBucket: AmountBucket = AmountBucket.LOW,
        novelty: Double = 0.0
    ): ContextSnapshot {
        val aggregator = ContextAggregator()
        return aggregator.aggregate(
            sessionId = "test-session",
            amountBucket = amountBucket,
            beneficiaryNovelty = novelty,
            channel = "UPI",
            communicationActive = commActive,
            captureRisk = deviceCapture,
            overlayRisk = deviceOverlay,
            deviationScore = baseline,
            graphRiskToken = graphToken
        )
    }

    @Test
    fun `testGraphApiUnavailable forces ABSTAIN`() {
        val snapshot = createDefaultSnapshot(graphToken = null)
        val beliefUpdater = BeliefUpdater()
        val triageGate = TriageGate()
        
        val edgeRisk = triageGate.evaluate(snapshot)
        val belief = beliefUpdater.update(snapshot, edgeRisk)
        
        assertTrue("Belief state should explicitly track graph as missing", belief.missingEvidenceMask.graphMissing)
        assertTrue("Missing graph evidence MUST inject ABSTAIN", belief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
        
        val policy = PolicyBandit()
        val decision = policy.decide(belief, snapshot)
        
        // ABSTAIN must prevent a purely PASS outcome even if coercion probability is low
        assertNotEquals("Should not PASS when uncertain due to missing graph", "A0_PASS", decision.action)
    }

    @Test
    fun `testOfflineMode degrades gracefully`() {
        // Complete offline: no graph, no device telemetry
        val snapshot = createDefaultSnapshot(graphToken = null, deviceCapture = null, deviceOverlay = null)
        val beliefUpdater = BeliefUpdater()
        val triageGate = TriageGate()
        
        val edgeRisk = triageGate.evaluate(snapshot)
        val belief = beliefUpdater.update(snapshot, edgeRisk)
        
        assertTrue(belief.missingEvidenceMask.deviceMissing)
        assertTrue(belief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
        
        val policy = PolicyBandit()
        val decision = policy.decide(belief, snapshot)
        
        // System must fallback to micro prompt or step up instead of crashing
        assertTrue(decision.action == "A1_MICRO_PROMPT" || decision.action == "A6_STEP_UP_REQUIRED")
    }

    @Test
    fun `testUnknownBeneficiary forces higher friction`() {
        // Novelty = 1.0 (Completely unknown beneficiary)
        val snapshot = createDefaultSnapshot(novelty = 1.0)
        val triageGate = TriageGate()
        val beliefUpdater = BeliefUpdater()
        val policy = PolicyBandit()
        
        val edgeRisk = triageGate.evaluate(snapshot)
        val belief = beliefUpdater.update(snapshot, edgeRisk)
        val decision = policy.decide(belief, snapshot)
        
        // High novelty should not automatically be treated as "no risk"
        assertNotEquals("A0_PASS", decision.action)
    }

    @Test
    fun `testNoHistoricalBaseline forces ABSTAIN`() {
        val snapshot = createDefaultSnapshot(baseline = null)
        val beliefUpdater = BeliefUpdater()
        val triageGate = TriageGate()
        
        val edgeRisk = triageGate.evaluate(snapshot)
        val belief = beliefUpdater.update(snapshot, edgeRisk)
        
        assertTrue(belief.missingEvidenceMask.baselineMissing)
        assertTrue(belief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
    }
}
