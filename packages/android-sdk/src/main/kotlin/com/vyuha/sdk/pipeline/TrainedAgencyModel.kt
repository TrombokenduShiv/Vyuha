package com.vyuha.sdk.pipeline

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vyuha.sdk.contracts.*
import kotlin.math.exp

/** Exact dense layers + top-2 sparse experts from the trained 954-parameter MoE.
 * Weights are exported from PyTorch; no personal data or network inference.
 */
class TrainedAgencyModel {
    private val bundle: JsonObject = javaClass.getResourceAsStream("/edge_moe.json")?.bufferedReader()?.use {
        Gson().fromJson(it, JsonObject::class.java)
    } ?: error("Missing trained agency model bundle")
    private val weights = bundle.getAsJsonObject("weights")
    val checksum: String = bundle["checkpoint_sha256"].asString

    private fun linear(input: DoubleArray, name: String): DoubleArray {
        val matrix = weights.getAsJsonArray("$name.weight")
        val bias = weights.getAsJsonArray("$name.bias")
        return DoubleArray(matrix.size()) { row ->
            val values = matrix[row].asJsonArray
            bias[row].asDouble + input.indices.sumOf { col -> input[col] * values[col].asDouble }
        }
    }

    fun predict(snapshot: ContextSnapshot): Double {
        val amount = when (snapshot.transaction.amountBucket) {
            AmountBucket.LOW -> .1; AmountBucket.MEDIUM -> .3
            AmountBucket.HIGH -> .6; AmountBucket.CRITICAL -> 1.0
        }
        val input = doubleArrayOf(if (snapshot.communication?.active == true) 1.0 else 0.0,
            if (snapshot.device?.captureRisk == true) 1.0 else 0.0, amount,
            snapshot.transaction.beneficiaryNovelty, 0.0, snapshot.baseline?.deviationScore ?: 0.0)
        require(input.all { it.isFinite() && it in 0.0..1.0 }) { "Invalid payment features" }
        val hidden = linear(input, "gate.0").map { it.coerceAtLeast(0.0) }.toDoubleArray()
        val gate = linear(hidden, "gate.2")
        val selected = gate.indices.sortedByDescending { gate[it] }.take(2)
        val maximum = selected.maxOf { gate[it] }
        val denominator = selected.sumOf { exp(gate[it] - maximum) }
        var logit = 0.0
        for (expert in selected) {
            val activation = linear(input, "experts.$expert.net.0").map { it.coerceAtLeast(0.0) }.toDoubleArray()
            logit += exp(gate[expert] - maximum) / denominator * linear(activation, "experts.$expert.net.2")[0]
        }
        return 1.0 / (1.0 + exp(-logit))
    }
}
