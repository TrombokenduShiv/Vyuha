/**
 * Vyuha 2.0 — Pipeline
 * ======================
 * Wires together the full edge inference chain:
 *   ContextSnapshot → TriageGate → BeliefUpdater → PolicyBandit → InterventionDecision
 *
 * This is the central orchestrator for a single evaluation cycle.
 * All operations are synchronous and deterministic for the hackathon mock.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §7 — ML Inference Path.
 * Total local path latency budget: < 50 ms p95 (per §13).
 *
 * Instrumented: Each stage is wrapped with LatencyTracer spans
 * to measure and report per-stage timing data.
 */
package com.vyuha.sdk.pipeline

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.instrumentation.LatencyTracer
import com.vyuha.sdk.instrumentation.PipelineMetrics

class Pipeline(
    private val triageGate: TriageGate = TriageGate(),
    private val beliefUpdater: BeliefUpdater = BeliefUpdater(),
    private val policyBandit: PolicyBandit = PolicyBandit(),
    private val tracer: LatencyTracer = LatencyTracer()
) {

    /** Last recorded pipeline metrics (for debug/dashboard access). */
    var lastMetrics: PipelineMetrics? = null
        private set

    /**
     * Run the full inference pipeline on a pre-assembled ContextSnapshot.
     *
     * Steps:
     * 1. Triage Gate scores the context [0, 1]
     * 2. If > threshold → Sparse MoE routes to Top-K=2 experts
     * 3. Belief Updater applies log-odds update with new evidence
     * 4. Policy Bandit maps belief state to intervention action
     *
     * @return The InterventionDecision for the host app to render.
     */
    fun evaluate(snapshot: ContextSnapshot): InterventionDecision {
        tracer.beginSpan(LatencyTracer.SPAN_TOTAL_LOCAL)

        // Step 1+2: Triage + Sparse MoE
        tracer.beginSpan(LatencyTracer.SPAN_TRIAGE_GATE)
        val edgeRiskOutput = triageGate.evaluate(snapshot, snapshot.graphRiskToken)
        val triageMs = tracer.endSpan(LatencyTracer.SPAN_TRIAGE_GATE)

        // Step 3: Update belief state
        tracer.beginSpan(LatencyTracer.SPAN_BELIEF_UPDATE)
        val beliefState = beliefUpdater.update(
            sessionId = snapshot.sessionId,
            edgeOutput = edgeRiskOutput,
            graphToken = snapshot.graphRiskToken,
            snapshot = snapshot
        )
        val beliefMs = tracer.endSpan(LatencyTracer.SPAN_BELIEF_UPDATE)

        // Step 4: Policy decision
        tracer.beginSpan(LatencyTracer.SPAN_POLICY_EVAL)
        val decision = policyBandit.decide(beliefState)
        val policyMs = tracer.endSpan(LatencyTracer.SPAN_POLICY_EVAL)

        val totalMs = tracer.endSpan(LatencyTracer.SPAN_TOTAL_LOCAL)

        // Record metrics
        val metrics = PipelineMetrics(
            sessionId = snapshot.sessionId,
            triageGateMs = triageMs,
            beliefUpdateMs = beliefMs,
            policyEvalMs = policyMs,
            totalLocalMs = totalMs,
            graphMissing = snapshot.graphRiskToken == null
        )
        lastMetrics = metrics

        tracer.recordTrace(
            sessionId = snapshot.sessionId,
            spans = mapOf(
                LatencyTracer.SPAN_TRIAGE_GATE to triageMs,
                LatencyTracer.SPAN_BELIEF_UPDATE to beliefMs,
                LatencyTracer.SPAN_POLICY_EVAL to policyMs
            ),
            totalMs = totalMs
        )

        return decision
    }

    /**
     * Get the latency tracer for external access (debug/dashboard).
     */
    fun getTracer(): LatencyTracer = tracer

    /**
     * Reset pipeline state for a new session.
     */
    fun reset() {
        beliefUpdater.reset()
        tracer.clear()
        lastMetrics = null
    }
}
