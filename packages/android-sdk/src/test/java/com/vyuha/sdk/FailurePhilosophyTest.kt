package com.vyuha.sdk
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.*
import org.junit.Test
import org.junit.Assert.*

class FailurePhilosophyTest {
    private fun context() = ContextSnapshot(sessionId = "test", transaction = TransactionContext(
        AmountBucket.MEDIUM, .95, onlinePurchase = true), communication = CommunicationContext(false),
        device = DeviceContext(false, false), baseline = BaselineContext(.1))

    @Test fun unknownSellerGetsVerificationWithoutIsolation() {
        val decision = Pipeline().evaluate(context())
        assertEquals("MERCHANT_VERIFICATION", decision.templateId)
        assertEquals(ActionId.A2_REFLECTION_CHALLENGE, decision.actionId)
        assertNull(decision.counterpartyRisk)
        assertEquals(1.0, decision.uncertainty, 0.0)
    }

    @Test fun calmBuyerWithRiskyReceiverIsProtected() {
        val now = System.currentTimeMillis() / 1000
        val token = GraphRiskToken("receiver", .92, .84, emptyList(), now, now + 120,
            signature = "fixture", riskClass = "HIGH")
        val snapshot = context().copy(graphRiskToken = token)
        val decision = Pipeline().evaluate(snapshot)
        assertNotEquals(ActionId.A0_PASS, decision.actionId)
        assertNotEquals(ActionId.A4_ISOLATION_BREAK, decision.actionId)
        assertEquals("COUNTERPARTY_WARNING", decision.templateId)
        assertTrue(decision.beliefScore < .2)
    }

    @Test fun expiredSafeTokenDoesNotVerifySeller() {
        val token = GraphRiskToken("receiver", .01, .98, emptyList(), 1, 2, signature = "fixture", riskClass = "LOW")
        assertEquals("MERCHANT_VERIFICATION", Pipeline().evaluate(context().copy(graphRiskToken = token)).templateId)
    }

    @Test fun missingFeaturesAreMarked() {
        val snapshot = context().copy(device = null, baseline = null)
        val edge = TriageGate().evaluate(snapshot, null)
        val belief = BeliefUpdater().update("test", edge, null, snapshot)
        assertTrue(belief.missingEvidenceMask.deviceMissing)
        assertTrue(belief.missingEvidenceMask.baselineMissing)
    }

    @Test fun repeatedEvidenceDoesNotEscalate() {
        val pipeline = Pipeline()
        val first = pipeline.evaluate(context())
        repeat(100) {
            val next = pipeline.evaluate(context())
            assertEquals(first.beliefScore, next.beliefScore, 0.0)
            assertEquals(first.actionId, next.actionId)
        }
    }
}
