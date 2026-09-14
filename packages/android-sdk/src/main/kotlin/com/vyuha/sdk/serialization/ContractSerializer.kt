/**
 * Vyuha 2.0 — Contract Serializer
 * ==================================
 * Gson-based serializer/deserializer for all contract data classes.
 * Handles snake_case ↔ camelCase mapping for backend interop.
 *
 * Provides toJson() and fromJson<T>() extension functions
 * for clean, type-safe serialization across the SDK.
 */
package com.vyuha.sdk.serialization

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.vyuha.sdk.contracts.*

/**
 * Central serializer for all Vyuha contract types.
 *
 * Usage:
 *   val json = ContractSerializer.toJson(contextSnapshot)
 *   val snapshot = ContractSerializer.fromJson<ContextSnapshot>(json)
 */
object ContractSerializer {

    /**
     * Gson instance configured for Vyuha contract serialization.
     * - LOWER_CASE_WITH_UNDERSCORES for backend API compatibility
     * - Pretty printing disabled for compact output
     * - Nulls serialized for completeness
     */
    @PublishedApi internal val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .create()

    /**
     * Gson instance with pretty printing for debug/logging output.
     */
    private val prettyGson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    /**
     * Serialize any contract object to compact JSON.
     */
    fun <T> toJson(obj: T): String = gson.toJson(obj)

    /**
     * Serialize any contract object to pretty-printed JSON (for debug/logs).
     */
    fun <T> toPrettyJson(obj: T): String = prettyGson.toJson(obj)

    /**
     * Deserialize JSON to a contract type.
     *
     * Usage:
     *   val snapshot = ContractSerializer.fromJson<ContextSnapshot>(jsonString)
     */
    inline fun <reified T> fromJson(json: String): T = gson.fromJson(json, T::class.java)

    /**
     * Deserialize JSON to a contract type (non-inline version for Java interop).
     */
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)

    // ── Convenience extension functions ─────────────────────────────

    /**
     * Serialize a ContextSnapshot for audit logging.
     */
    fun serializeSnapshot(snapshot: ContextSnapshot): String = toJson(snapshot)

    /**
     * Serialize an InterventionDecision for audit logging.
     */
    fun serializeDecision(decision: InterventionDecision): String = toJson(decision)

    /**
     * Serialize a BeliefState for audit logging.
     */
    fun serializeBelief(belief: BeliefState): String = toJson(belief)

    /**
     * Serialize an OutcomeFeedback for telemetry submission.
     */
    fun serializeFeedback(feedback: OutcomeFeedback): String = toJson(feedback)
}
