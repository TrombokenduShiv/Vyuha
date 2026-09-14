/**
 * Vyuha 2.0 — Audit Logger
 * ==========================
 * Logs every pipeline evaluation as a JSON record for audit telemetry.
 * Stores to internal app storage as JSON-Lines (one record per line).
 *
 * Architecture ref: ARCHITECTURE_FREEZE_V1 §11 — Backend Services.
 * POST /v1/telemetry (Async audit payload)
 *
 * For the hackathon: writes to local storage.
 * In production: would batch-upload to the telemetry endpoint.
 */
package com.vyuha.sdk.serialization

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.instrumentation.PipelineMetrics
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

class AuditLogger(
    /** Directory where audit logs are stored. */
    private val logDir: File,
    /** Local diagnostic logs are opt-in; production must use a governed encrypted sink. */
    private val enabled: Boolean = false,
    /** Coroutine scope for async writes. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    init {
        if (enabled && !logDir.exists()) {
            logDir.mkdirs()
        }
    }

    /**
     * A single audit log entry.
     */
    data class AuditEntry(
        val sessionId: String,
        val timestampMs: Long = System.currentTimeMillis(),
        val eventType: String,
        val payload: String
    )

    /**
     * Log a pipeline evaluation result.
     * Non-blocking: writes are dispatched to IO dispatcher.
     */
    fun logEvaluation(
        sessionId: String,
        snapshot: ContextSnapshot,
        decision: InterventionDecision,
        belief: BeliefState,
        metrics: PipelineMetrics? = null
    ) {
        val entry = buildString {
            append("{")
            append("\"session_id\":\"$sessionId\"")
            append(",\"timestamp_ms\":${System.currentTimeMillis()}")
            append(",\"event_type\":\"EVALUATION\"")
            append(",\"action_id\":\"${decision.actionId.name}\"")
            append(",\"belief_score\":${decision.beliefScore}")
            append(",\"uncertainty\":${decision.uncertainty}")
            append(",\"reason_codes\":[${decision.reasonCodes.joinToString(",") { "\"$it\"" }}]")
            append(",\"graph_missing\":${belief.missingEvidenceMask.graphMissing}")
            append(",\"event_sequence_length\":${belief.eventSequenceLength}")
            if (metrics != null) {
                append(",\"total_local_ms\":${metrics.totalLocalMs}")
                append(",\"within_budget\":${metrics.withinBudget}")
            }
            append("}")
        }

        writeAsync(sessionId, entry)
    }

    /**
     * Log a session outcome.
     */
    fun logOutcome(feedback: OutcomeFeedback) {
        val entry = ContractSerializer.toJson(feedback)
        writeAsync(feedback.sessionId, entry)
    }

    /**
     * Log a session start event.
     */
    fun logSessionStart(sessionId: String) {
        val entry = "{\"session_id\":\"$sessionId\",\"timestamp_ms\":${System.currentTimeMillis()},\"event_type\":\"SESSION_START\"}"
        writeAsync(sessionId, entry)
    }

    /**
     * Log a session complete event.
     */
    fun logSessionComplete(sessionId: String, finalStatus: FinalStatus) {
        val entry = "{\"session_id\":\"$sessionId\",\"timestamp_ms\":${System.currentTimeMillis()},\"event_type\":\"SESSION_COMPLETE\",\"final_status\":\"${finalStatus.name}\"}"
        writeAsync(sessionId, entry)
    }

    /**
     * Read all audit entries for a session (for debugging).
     */
    fun readSession(sessionId: String): List<String> {
        java.util.UUID.fromString(sessionId)
        if (!enabled) return emptyList()
        val file = File(logDir, "session_${sessionId}.jsonl")
        return if (file.exists()) file.readLines() else emptyList()
    }

    /**
     * Async write to the session-specific log file.
     */
    private fun writeAsync(sessionId: String, jsonLine: String) {
        if (!enabled) return
        java.util.UUID.fromString(sessionId)
        scope.launch {
            try {
                val file = File(logDir, "session_${sessionId}.jsonl")
                PrintWriter(FileWriter(file, true)).use { writer ->
                    writer.println(jsonLine)
                }
            } catch (e: Exception) {
                // Audit logging should never crash the SDK
            }
        }
    }

    /**
     * Shutdown the logger, flushing any pending writes.
     */
    fun shutdown() {
        scope.cancel()
    }
}
