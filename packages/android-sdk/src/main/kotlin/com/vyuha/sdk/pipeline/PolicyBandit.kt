package com.vyuha.sdk.pipeline
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vyuha.sdk.contracts.*

/** Frozen offline policy with non-negotiable dual-integrity safety constraints. */
class PolicyBandit {
    private val artifact = javaClass.getResourceAsStream("/frozen_policy.json")!!.bufferedReader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }
    init { require(artifact["moe_sha256"].asString == TrainedAgencyModel().checksum) { "Policy/model mismatch" } }
    fun decide(belief: BeliefState, snapshot: ContextSnapshot? = null): InterventionDecision {
        val p = belief.pCoercion
        val uncertain = belief.uncertaintySet.size != 1
        val token = snapshot?.graphRiskToken
        val now = System.currentTimeMillis() / 1000
        val c = token?.takeIf { it.expiresAt > now && it.issuedAt <= now + 30 && it.riskClass != "UNKNOWN" }
            ?.riskScore?.takeIf { it.isFinite() && it in 0.0..1.0 }
        val live = snapshot?.communication?.active == true || snapshot?.device?.captureRisk == true
        val novel = (snapshot?.transaction?.beneficiaryNovelty ?: 0.0) >= .7
        val purchase = snapshot?.transaction?.onlinePurchase == true
        val verified = snapshot?.transaction?.independentlyVerified == true
        val candidates = when {
            p >= .65 && live && !uncertain -> listOf(ActionId.A4_ISOLATION_BREAK, ActionId.A6_STEP_UP_REQUIRED)
            c != null && c >= .7 -> listOf(ActionId.A2_REFLECTION_CHALLENGE, ActionId.A6_STEP_UP_REQUIRED)
            c == null && novel && !verified -> listOf(if (purchase) ActionId.A2_REFLECTION_CHALLENGE else ActionId.A1_MICRO_PROMPT)
            p >= .4 || (c != null && c >= .3) -> listOf(ActionId.A2_REFLECTION_CHALLENGE, ActionId.A3_COOLING_DELAY)
            uncertain && novel -> listOf(ActionId.A1_MICRO_PROMPT, ActionId.A2_REFLECTION_CHALLENGE)
            else -> listOf(ActionId.A0_PASS, ActionId.A1_MICRO_PROMPT)
        }
        val category = if (c == null) "UNKNOWN" else if (c >= .7) "HIGH" else if (c < .3) "LOW" else "ELEVATED"
        val key = "${(p * 5).toInt().coerceAtMost(4)}:$category:${if (uncertain) 1 else 0}:${if (live) 1 else 0}:${if (purchase) 1 else 0}"
        val learned = artifact.getAsJsonObject("policy")[key]?.asString
        val action = candidates.firstOrNull { it.name == learned } ?: candidates.first()
        val (reason, template) = when {
            action == ActionId.A4_ISOLATION_BREAK -> "HIGH_AGENCY_RISK" to "ISOLATION_BREAK"
            c != null && c >= .7 -> "COUNTERPARTY_HIGH" to "COUNTERPARTY_WARNING"
            c == null && novel -> "COUNTERPARTY_UNKNOWN" to if (purchase) "MERCHANT_VERIFICATION" else "RECEIVER_CHECK"
            action != ActionId.A0_PASS -> "PAYMENT_UNCERTAIN" to "REFLECTION"
            else -> "LOW_OBSERVED_RISK" to "PASS"
        }
        return InterventionDecision(sessionId = belief.sessionId, actionId = action, beliefScore = p,
            uncertainty = if (uncertain || c == null) 1.0 else 0.0, reasonCodes = listOf(reason),
            counterpartyRisk = c, templateId = template)
    }
}
