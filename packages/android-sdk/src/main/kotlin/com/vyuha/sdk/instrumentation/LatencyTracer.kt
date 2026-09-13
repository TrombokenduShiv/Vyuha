/**
 * Vyuha 2.0 — Latency Tracer
 * =============================
 * Instruments every stage of the edge pipeline with high-resolution
 * timing data to prove the <50ms p95 local latency budget.
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §13 — Latency Budget.
 * - Context Aggregation: < 2 ms
 * - Triage Gate: < 2 ms
 * - Sparse MoE (Top-K 2): < 25 ms
 * - Belief + Policy: < 3 ms
 * - Total Local Path: < 50 ms p95
 *
 * Uses System.nanoTime() for monotonic, high-precision timing.
 * Thread-safe via ConcurrentHashMap for multi-threaded access.
 */
package com.vyuha.sdk.instrumentation

import java.util.concurrent.ConcurrentHashMap

class LatencyTracer {

    /**
     * A single timing span.
     */
    data class Span(
        val name: String,
        val startNanos: Long,
        var endNanos: Long = 0L
    ) {
        /** Duration in milliseconds. */
        val durationMs: Double get() = (endNanos - startNanos) / 1_000_000.0
    }

    /**
     * Recorded latency for a complete pipeline evaluation.
     */
    data class TraceRecord(
        val sessionId: String,
        val timestampMs: Long = System.currentTimeMillis(),
        val spans: Map<String, Double>,  // spanName → durationMs
        val totalMs: Double
    )

    // Active spans (per-thread, keyed by span name)
    private val activeSpans = ConcurrentHashMap<String, Span>()

    // Ring buffer for historical traces
    private val traceHistory = ArrayDeque<TraceRecord>(HISTORY_SIZE)
    private val spanDurations = ConcurrentHashMap<String, MutableList<Double>>()

    companion object {
        /** Standard span names matching the architecture's latency budget. */
        const val SPAN_CONTEXT_AGGREGATION = "context_aggregation"
        const val SPAN_TRIAGE_GATE = "triage_gate"
        const val SPAN_EXPERT_ROUTING = "expert_routing"
        const val SPAN_BELIEF_UPDATE = "belief_update"
        const val SPAN_POLICY_EVAL = "policy_eval"
        const val SPAN_TOTAL_LOCAL = "total_local_path"
        const val SPAN_GRAPH_FETCH = "graph_fetch"

        /** Ring buffer size for historical traces. */
        private const val HISTORY_SIZE = 100
    }

    /**
     * Begin a named timing span.
     */
    fun beginSpan(name: String) {
        activeSpans[name] = Span(name = name, startNanos = System.nanoTime())
    }

    /**
     * End a named timing span.
     * @return Duration in milliseconds, or -1 if span was not started.
     */
    fun endSpan(name: String): Double {
        val span = activeSpans.remove(name) ?: return -1.0
        span.endNanos = System.nanoTime()

        // Record in duration history
        spanDurations.getOrPut(name) { mutableListOf() }.let { durations ->
            synchronized(durations) {
                durations.add(span.durationMs)
                // Keep only last HISTORY_SIZE entries
                while (durations.size > HISTORY_SIZE) {
                    durations.removeAt(0)
                }
            }
        }

        return span.durationMs
    }

    /**
     * Record a complete pipeline trace.
     */
    fun recordTrace(sessionId: String, spans: Map<String, Double>, totalMs: Double) {
        val record = TraceRecord(
            sessionId = sessionId,
            spans = spans,
            totalMs = totalMs
        )
        synchronized(traceHistory) {
            if (traceHistory.size >= HISTORY_SIZE) {
                traceHistory.removeFirst()
            }
            traceHistory.addLast(record)
        }
    }

    /**
     * Get P95 latency for a given span name (in ms).
     */
    fun getP95(spanName: String): Double {
        return getPercentile(spanName, 0.95)
    }

    /**
     * Get P50 (median) latency for a given span name (in ms).
     */
    fun getP50(spanName: String): Double {
        return getPercentile(spanName, 0.50)
    }

    /**
     * Get maximum observed latency for a given span name (in ms).
     */
    fun getMax(spanName: String): Double {
        val durations = spanDurations[spanName] ?: return 0.0
        synchronized(durations) {
            return durations.maxOrNull() ?: 0.0
        }
    }

    /**
     * Get a human-readable latency summary for all spans.
     */
    fun getSummary(): Map<String, Map<String, Double>> {
        return spanDurations.keys.associateWith { name ->
            mapOf(
                "p50" to getP50(name),
                "p95" to getP95(name),
                "max" to getMax(name)
            )
        }
    }

    /**
     * Get the last N trace records.
     */
    fun getRecentTraces(count: Int = 10): List<TraceRecord> {
        synchronized(traceHistory) {
            return traceHistory.takeLast(count)
        }
    }

    /**
     * Clear all recorded data.
     */
    fun clear() {
        activeSpans.clear()
        traceHistory.clear()
        spanDurations.clear()
    }

    private fun getPercentile(spanName: String, percentile: Double): Double {
        val durations = spanDurations[spanName] ?: return 0.0
        synchronized(durations) {
            if (durations.isEmpty()) return 0.0
            val sorted = durations.sorted()
            val index = ((sorted.size - 1) * percentile).toInt()
            return sorted[index]
        }
    }
}
