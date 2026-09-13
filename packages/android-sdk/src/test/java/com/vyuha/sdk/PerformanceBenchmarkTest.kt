package com.vyuha.sdk

import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.core.*
import org.junit.Test
import kotlin.system.measureNanoTime

class PerformanceBenchmarkTest {

    @Test
    fun `hot path must adhere to performance budgets`() {
        val aggregator = ContextAggregator()
        val triageGate = TriageGate()
        val beliefUpdater = BeliefUpdater()
        val policyBandit = PolicyBandit()

        println("=== VYUHA HOT-PATH BENCHMARK ===")
        val iterations = 1000
        
        // Warmup
        for (i in 0..100) {
            val s = aggregator.aggregate(
                sessionId = "warmup", amountBucket = AmountBucket.LOW, beneficiaryNovelty = 0.1,
                channel = "P2P", communicationActive = false, captureRisk = false,
                overlayRisk = false, deviationScore = 0.0, graphRiskToken = null
            )
            val t = triageGate.evaluate(s)
            val b = beliefUpdater.update(s, t)
            policyBandit.decide(b, s)
        }

        val contextTimes = mutableListOf<Long>()
        val triageTimes = mutableListOf<Long>()
        val beliefTimes = mutableListOf<Long>()
        val policyTimes = mutableListOf<Long>()
        val totalTimes = mutableListOf<Long>()

        for (i in 0 until iterations) {
            val totalNanos = measureNanoTime {
                val tContext = measureNanoTime {
                    aggregator.aggregate(
                        sessionId = "bench", amountBucket = AmountBucket.LOW, beneficiaryNovelty = 0.5,
                        channel = "P2P", communicationActive = false, captureRisk = false,
                        overlayRisk = false, deviationScore = 0.0, graphRiskToken = null
                    )
                }
                contextTimes.add(tContext)
                val snapshot = aggregator.aggregate(
                    sessionId = "bench", amountBucket = AmountBucket.LOW, beneficiaryNovelty = 0.5,
                    channel = "P2P", communicationActive = false, captureRisk = false,
                    overlayRisk = false, deviationScore = 0.0, graphRiskToken = null
                )

                val tTriage = measureNanoTime {
                    triageGate.evaluate(snapshot)
                }
                triageTimes.add(tTriage)
                val edgeRisk = triageGate.evaluate(snapshot)

                val tBelief = measureNanoTime {
                    beliefUpdater.update(snapshot, edgeRisk)
                }
                beliefTimes.add(tBelief)
                val beliefState = beliefUpdater.update(snapshot, edgeRisk)

                val tPolicy = measureNanoTime {
                    policyBandit.decide(beliefState, snapshot)
                }
                policyTimes.add(tPolicy)
            }
            totalTimes.add(totalNanos)
        }

        fun printStats(name: String, times: List<Long>, targetMs: Double) {
            val sorted = times.sorted()
            val medianMs = sorted[sorted.size / 2] / 1_000_000.0
            val p95Ms = sorted[(sorted.size * 0.95).toInt()] / 1_000_000.0
            println(String.format("%-25s | Median: %6.3f ms | p95: %6.3f ms | Target: < %.1f ms", name, medianMs, p95Ms, targetMs))
            assert(p95Ms < targetMs) { "$name exceeded performance budget: $p95Ms ms > $targetMs ms" }
        }

        printStats("Context Extraction", contextTimes, 5.0)
        printStats("Triage Gate (Edge Risk)", triageTimes, 20.0) // 5ms triage + 15ms experts
        printStats("Belief Update", beliefTimes, 2.0)
        printStats("Policy Bandit", policyTimes, 2.0)
        
        println("-------------------------------------------------------------------------")
        printStats("TOTAL LOCAL HOT-PATH", totalTimes, 50.0)
        println("==========================================")
    }
}
