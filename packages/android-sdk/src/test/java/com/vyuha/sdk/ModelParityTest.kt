package com.vyuha.sdk
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.Pipeline
import org.junit.Assert.*
import org.junit.Test

class ModelParityTest {
    @Test fun nativeModelAndPolicyMatchPythonAcross128Cases() {
        val rows = javaClass.getResourceAsStream("/parity_vectors.json")!!.bufferedReader().use {
            Gson().fromJson(it, JsonArray::class.java)
        }
        val pipeline = Pipeline()
        for (element in rows) {
            val row = element.asJsonObject
            val f = row.getAsJsonArray("features")
            val amount = f[2].asDouble
            val bucket = if (amount < .2) AmountBucket.LOW else if (amount < .5) AmountBucket.MEDIUM else if (amount < .8) AmountBucket.HIGH else AmountBucket.CRITICAL
            val now = System.currentTimeMillis() / 1000
            val c = row["counterparty"].takeUnless { it.isJsonNull }?.asDouble
            val token = if (c == null) null else GraphRiskToken("receiver", c, 1.0, emptyList(), now, now + 120,
                signature = "fixture", riskClass = if (c >= .7) "HIGH" else if (c < .3) "LOW" else "ELEVATED")
            val snapshot = ContextSnapshot(transaction = TransactionContext(bucket, f[3].asDouble,
                onlinePurchase = row["online_purchase"].asBoolean), communication = CommunicationContext(f[0].asDouble > .5),
                device = DeviceContext(f[1].asDouble > .5, false), baseline = BaselineContext(f[5].asDouble), graphRiskToken = token)
            val decision = pipeline.evaluate(snapshot)
            assertEquals(row["agency_risk"].asDouble, decision.beliefScore, 1e-5)
            assertEquals(row["action_id"].asString, decision.actionId.name)
            assertEquals(row["template_id"].asString, decision.templateId)
        }
    }
}
