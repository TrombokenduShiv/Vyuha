/**
 * Vyuha 2.0 — Pipeline Metrics
 * ===============================
 * Data class holding per-evaluation metrics: stage durations,
 * total duration, graph fetch duration, cache hit/miss.
 *
 * Attached to InterventionDecision as optional debug metadata
 * for the hackathon dashboard and jury demonstration.
 */
package com.vyuha.sdk.instrumentation

/**
 * Metrics captured during a single pipeline evaluation cycle.
 */
data class PipelineMetrics(
    val sessionId: String,
    val timestampMs: Long = System.currentTimeMillis(),

    // ── Stage durations (milliseconds) ──────────────────────────────
    /** Time to aggregate context from host signals. */
    val contextAggregationMs: Double = 0.0,
    /** Time for the triage gate scoring. */
    val triageGateMs: Double = 0.0,
    /** Time for sparse MoE expert routing and execution. */
    val expertRoutingMs: Double = 0.0,
    /** Time for Bayesian belief state update. */
    val beliefUpdateMs: Double = 0.0,
    /** Time for policy bandit decision. */
    val policyEvalMs: Double = 0.0,
    /** Total local inference path duration. */
    val totalLocalMs: Double = 0.0,

    // ── Graph fetch metrics ─────────────────────────────────────────
    /** Time for graph risk API fetch (async, may be 0 if cached). */
    val graphFetchMs: Double = 0.0,
    /** Whether the graph token came from cache. */
    val graphCacheHit: Boolean = false,
    /** Whether the graph token was a stale cache hit. */
    val graphCacheStale: Boolean = false,
    /** Whether graph data was entirely missing. */
    val graphMissing: Boolean = false,

    // ── Token verification ──────────────────────────────────────────
    /** Whether the token signature was verified successfully. */
    val tokenVerified: Boolean = false
) {
    /**
     * Whether the total local path meets the latency budget (<50ms p95).
     */
    val withinBudget: Boolean get() = totalLocalMs < 50.0

    /**
     * Human-readable summary for debug display.
     */
    fun toDebugString(): String = buildString {
        appendLine("Pipeline Metrics (${sessionId.take(8)}...)")
        appendLine("  Context Agg:   ${String.format("%.2f", contextAggregationMs)} ms")
        appendLine("  Triage Gate:   ${String.format("%.2f", triageGateMs)} ms")
        appendLine("  Expert Route:  ${String.format("%.2f", expertRoutingMs)} ms")
        appendLine("  Belief Update: ${String.format("%.2f", beliefUpdateMs)} ms")
        appendLine("  Policy Eval:   ${String.format("%.2f", policyEvalMs)} ms")
        appendLine("  ─────────────────────────")
        appendLine("  Total Local:   ${String.format("%.2f", totalLocalMs)} ms ${if (withinBudget) "✅" else "⚠️ OVER BUDGET"}")
        appendLine("  Graph Fetch:   ${String.format("%.2f", graphFetchMs)} ms")
        appendLine("  Graph Cache:   ${if (graphCacheHit) "HIT" else if (graphCacheStale) "STALE" else if (graphMissing) "MISSING" else "NETWORK"}")
        appendLine("  Token Valid:   $tokenVerified")
    }
}
