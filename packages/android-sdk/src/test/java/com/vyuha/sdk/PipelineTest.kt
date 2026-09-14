package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.ContextAggregator
import com.vyuha.sdk.pipeline.Pipeline
import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineTest {

    @Test
    fun testGoldenPath_YieldsPassAction() {
        val pipeline = Pipeline()
        val aggregator = ContextAggregator()
        
        val snapshot = aggregator.aggregate(
            sessionId = "test-session-1",
            amountBucket = AmountBucket.LOW,
            beneficiaryNovelty = 0.05,
            channel = "UPI",
            communicationActive = false,
            captureRisk = false,
            overlayRisk = false,
            deviationScore = 0.1,
            graphRiskToken = GraphRiskToken(
                vpaHash = "hash1",
                riskScore = 0.05,
                confidence = 0.95,
                reasonCodes = emptyList(),
                issuedAt = 0,
                expiresAt = 0,
                signature = "sig"
            )
        )

        val decision = pipeline.evaluate(snapshot)

        // For benign context, it should PASS (A0)
        assertEquals(ActionId.A0_PASS, decision.actionId)
    }

    @Test
    fun testCoercionScenario_YieldsHighIntervention() {
        val pipeline = Pipeline()
        val aggregator = ContextAggregator()
        
        val snapshot = aggregator.aggregate(
            sessionId = "test-session-2",
            amountBucket = AmountBucket.CRITICAL,
            beneficiaryNovelty = 0.95,
            channel = "UPI",
            communicationActive = true, // ACTIVE CALL!
            captureRisk = true,
            overlayRisk = false,
            deviationScore = 0.85,
            graphRiskToken = GraphRiskToken(
                vpaHash = "hash2",
                riskScore = 0.92,
                confidence = 0.88,
                reasonCodes = listOf("HIGH_FAN_IN"),
                issuedAt = 0,
                expiresAt = 0,
                signature = "sig"
            )
        )

        val decision = pipeline.evaluate(snapshot)

        // Coercion logic should push p_coercion high → A4+ intervention
        // The exact action depends on belief state (log-odds accumulation)
        val highInterventions = listOf(
            ActionId.A4_ISOLATION_BREAK,
            ActionId.A5_TRUSTED_VERIFY,
            ActionId.A6_STEP_UP_REQUIRED
        )
        assert(decision.actionId in highInterventions) {
            "Expected high intervention (A4-A6), got ${decision.actionId}"
        }
    }
}
