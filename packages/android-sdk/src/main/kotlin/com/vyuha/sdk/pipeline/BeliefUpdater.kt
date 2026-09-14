package com.vyuha.sdk.pipeline
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vyuha.sdk.contracts.*

/** Current-session estimate. Repeated snapshots never manufacture new evidence.
 * A sequential learned likelihood updater requires longitudinal training data.
 */
class BeliefUpdater {
    private var previous: List<Any?>? = null
    private var eventCount = 0
    private val calibration = javaClass.getResourceAsStream("/frozen_policy.json")!!.bufferedReader().use {
        Gson().fromJson(it, JsonObject::class.java).getAsJsonObject("conformal")
    }
    fun update(sessionId: String, edgeOutput: EdgeRiskOutput, graphToken: GraphRiskToken?, snapshot: ContextSnapshot): BeliefState {
        val fingerprint = listOf(sessionId, snapshot.transaction, snapshot.communication, snapshot.device, snapshot.baseline)
        if (previous != fingerprint) { eventCount++; previous = fingerprint }
        val p = edgeOutput.triageScore
        val labels = mutableListOf<UncertaintyLabel>()
        if (p <= calibration["safe"].asDouble) labels.add(UncertaintyLabel.SAFE)
        if (1 - p <= calibration["risk"].asDouble) labels.add(UncertaintyLabel.RISK)
        if (labels.size != 1) {
            labels.clear()
            labels.addAll(listOf(UncertaintyLabel.SAFE, UncertaintyLabel.RISK, UncertaintyLabel.ABSTAIN))
        }
        return BeliefState(sessionId = sessionId, pCoercion = p, uncertaintySet = labels,
            missingEvidenceMask = MissingEvidenceMask(graphToken?.riskScore == null || graphToken.riskClass == "UNKNOWN",
                snapshot.baseline == null, snapshot.device == null), eventSequenceLength = eventCount)
    }
    fun reset() { previous = null; eventCount = 0 }
}
