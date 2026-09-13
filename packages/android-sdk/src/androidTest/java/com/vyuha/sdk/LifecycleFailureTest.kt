package com.vyuha.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumentation Tests for Vyuha 2.0
 * 
 * Enforces the Mandatory Failure Philosophy during Android lifecycle events.
 * Simulates process recreation and orientation changes to ensure that missing 
 * telemetry or re-initialized state correctly injects UncertaintyLabel.ABSTAIN 
 * rather than fabricating a SAFE state.
 */
@RunWith(AndroidJUnit4::class)
class LifecycleFailureTest {

    @Test
    fun testOrientationChangeMaintainsAbstainOnMissingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Simulating session before orientation change
        val aggregator = ContextAggregator()
        val triage = TriageGate()
        val beliefUpdater = BeliefUpdater()
        
        // Pre-rotation: We have graph token, but NO device/camera telemetry yet
        val preSnapshot = aggregator.aggregate(
            sessionId = "session-123",
            amountBucket = AmountBucket.HIGH,
            beneficiaryNovelty = 0.9,
            channel = "UPI",
            communicationActive = null, // Missing
            captureRisk = null,         // Missing
            overlayRisk = null,         // Missing
            deviationScore = null,      // Missing
            graphRiskToken = GraphRiskToken("test-node", 0.5)
        )
        
        val preRisk = triage.evaluate(preSnapshot)
        val preBelief = beliefUpdater.update(preSnapshot, preRisk)
        
        assertTrue("Pre-rotation: Should have ABSTAIN due to missing telemetry", 
            preBelief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
            
        // Simulating Orientation Change (Activity recreated, SDK re-initialized)
        beliefUpdater.reset()
        
        // Post-rotation: The host app restores the session ID but telemetry is still catching up
        val postSnapshot = aggregator.aggregate(
            sessionId = "session-123",
            amountBucket = AmountBucket.HIGH,
            beneficiaryNovelty = 0.9,
            channel = "UPI",
            communicationActive = null, // Still missing
            captureRisk = null,
            overlayRisk = null,
            deviationScore = null,
            graphRiskToken = GraphRiskToken("test-node", 0.5)
        )
        
        val postRisk = triage.evaluate(postSnapshot)
        val postBelief = beliefUpdater.update(postSnapshot, postRisk)
        
        assertTrue("Post-rotation: Missing evidence MUST continue to force ABSTAIN", 
            postBelief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
            
        val policy = PolicyBandit()
        val decision = policy.decide(postBelief, postSnapshot)
        
        assertNotEquals("Orientation change with missing data cannot result in A0_PASS", 
            "A0_PASS", decision.action)
    }
    
    @Test
    fun testProcessRecreationRecoversSafely() {
        // Simulating the system killing the app process while in background,
        // and the user returning to it. 
        val aggregator = ContextAggregator()
        val policy = PolicyBandit()
        
        // Host app re-creates the session but the graph token cache was wiped
        val snapshot = aggregator.aggregate(
            sessionId = "session-recovered",
            amountBucket = AmountBucket.MEDIUM,
            beneficiaryNovelty = 0.1,
            channel = "UPI",
            communicationActive = false,
            captureRisk = false,
            overlayRisk = false,
            deviationScore = 0.0,
            graphRiskToken = null // Lost during process death
        )
        
        val triage = TriageGate()
        val updater = BeliefUpdater()
        
        val edgeRisk = triage.evaluate(snapshot)
        val belief = updater.update(snapshot, edgeRisk)
        
        assertTrue("Process recreation wiping graph token must trigger graphMissing = true",
            belief.missingEvidenceMask.graphMissing)
            
        assertTrue("Process recreation wiping graph token must force ABSTAIN",
            belief.uncertaintySet.contains(UncertaintyLabel.ABSTAIN))
            
        val decision = policy.decide(belief, snapshot)
        assertEquals("ABSTAIN from missing graph token must force STEP_UP_REQUIRED fallback",
            "A6_STEP_UP_REQUIRED", decision.action)
    }
}
