package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.BeliefUpdater
import com.vyuha.sdk.pipeline.TriageGate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the offline/degraded fallback behavior.
 * Validates Architecture Decision D4: Core safety continues with graphRisk = UNKNOWN.
 */
class OfflineFallbackTest {

    private lateinit var triageGate: TriageGate
    private lateinit var beliefUpdater: BeliefUpdater

    @Before
    fun setup() {
        triageGate = TriageGate()
        beliefUpdater = BeliefUpdater()
    }

    @Test
    fun testMissingGraph_SetsGraphMissingMask() {
        val edgeOutput = triageGate.evaluate(
            snapshot = buildSnapshot(communicationActive = true),
            graphToken = null  // Graph unavailable
        )

        val belief = beliefUpdater.update(
            sessionId = "offline-test",
            edgeOutput = edgeOutput,
            graphToken = null,
            snapshot = buildSnapshot(communicationActive = true)
        )

        assertTrue("Graph missing mask should be true", belief.missingEvidenceMask.graphMissing)
    }

    @Test
    fun testMissingGraph_IncreasesUncertainty() {
        // Evaluate WITH graph (low risk)
        val edgeWithGraph = triageGate.evaluate(
            snapshot = buildSnapshot(communicationActive = false),
            graphToken = GraphRiskToken(
                vpaHash = "test", riskScore = 0.05, confidence = 0.95,
                reasonCodes = emptyList(), issuedAt = 0, expiresAt = 0,
                signature = "sig"
            )
        )
        val beliefWithGraph = beliefUpdater.update(
            sessionId = "test-graph",
            edgeOutput = edgeWithGraph,
            graphToken = GraphRiskToken(
                vpaHash = "test", riskScore = 0.05, confidence = 0.95,
                reasonCodes = emptyList(), issuedAt = 0, expiresAt = 0,
                signature = "sig"
            ),
            snapshot = buildSnapshot(communicationActive = false)
        )

        // Reset and evaluate WITHOUT graph
        val freshUpdater = BeliefUpdater()
        val edgeWithout = triageGate.evaluate(
            snapshot = buildSnapshot(communicationActive = false),
            graphToken = null
        )
        val beliefWithout = freshUpdater.update(
            sessionId = "test-no-graph",
            edgeOutput = edgeWithout,
            graphToken = null,
            snapshot = buildSnapshot(communicationActive = false)
        )

        // Missing graph should result in higher pCoercion (more uncertain)
        assertEquals("Missing receiver evidence must not fabricate coercion",
            beliefWithGraph.pCoercion, beliefWithout.pCoercion, 0.0)
    }

    @Test
    fun testOfflineMode_DoesNotCrash() {
        // Simulate a full pipeline evaluation with no graph data
        val snapshot = buildSnapshot(
            communicationActive = true,
            amountBucket = AmountBucket.CRITICAL,
            beneficiaryNovelty = 0.9
        )
        val edgeOutput = triageGate.evaluate(snapshot, null)
        val belief = beliefUpdater.update("offline-crash-test", edgeOutput, null, snapshot)

        assertNotNull("Belief state should not be null", belief)
        assertTrue("pCoercion should be in [0,1]", belief.pCoercion in 0.0..1.0)
    }

    private fun buildSnapshot(
        communicationActive: Boolean = false,
        amountBucket: AmountBucket = AmountBucket.LOW,
        beneficiaryNovelty: Double = 0.5
    ): ContextSnapshot {
        return ContextSnapshot(
            sessionId = "test",
            transaction = TransactionContext(amountBucket, beneficiaryNovelty),
            communication = CommunicationContext(communicationActive),
            device = DeviceContext(captureRisk = false, overlayRisk = false),
            baseline = BaselineContext(deviationScore = 0.1)
        )
    }
}
