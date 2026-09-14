package com.vyuha.sdk.pipeline
import com.vyuha.sdk.contracts.*

/** Runs trained sparse agency MoE; receiver evidence has a separate path. */
class TriageGate(private val model: TrainedAgencyModel = TrainedAgencyModel()) {
    companion object { const val TRIAGE_THRESHOLD = .3 }
    fun evaluate(snapshot: ContextSnapshot, graphToken: GraphRiskToken?): EdgeRiskOutput =
        EdgeRiskOutput(sessionId = snapshot.sessionId, triageScore = model.predict(snapshot),
            isSparseRouted = true, expertScores = emptyList())
}
