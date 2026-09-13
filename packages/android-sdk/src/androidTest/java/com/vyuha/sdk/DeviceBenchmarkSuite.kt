package com.vyuha.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.instrumentation.MemoryCpuProfiler
import com.vyuha.sdk.pipeline.ContextAggregator
import com.vyuha.sdk.pipeline.Pipeline
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

/**
 * Vyuha 2.0 — Device Benchmark Suite
 * ====================================
 * Runs on a physical Android device or emulator to measure actual
 * CPU time, Memory allocations, and P95 latency of the inference pipeline.
 *
 * Validates Architecture Decision D13 (Latency & Resource Constraints).
 */
@RunWith(AndroidJUnit4::class)
class DeviceBenchmarkSuite {

    @Test
    fun benchmarkPipeline_1000Iterations() {
        val pipeline = Pipeline()
        val aggregator = ContextAggregator()
        
        // Warmup (JIT compilation)
        repeat(100) {
            runIteration(pipeline, aggregator)
        }
        
        // Start Profiling
        MemoryCpuProfiler.startProfiling()
        val startCpuTime = MemoryCpuProfiler.getCurrentThreadCpuTimeMs()
        
        // Benchmark
        val iterations = 1000
        for (i in 0 until iterations) {
            runIteration(pipeline, aggregator)
        }
        
        // End Profiling
        val result = MemoryCpuProfiler.endProfiling(startCpuTime)
        
        val avgCpuTime = result.cpuTimeConsumedMs / iterations.toDouble()
        
        Log.i("VyuhaBenchmark", "Completed 1000 iterations.")
        Log.i("VyuhaBenchmark", result.toDebugString())
        Log.i("VyuhaBenchmark", "Average CPU time per inference: ${"%.3f".format(avgCpuTime)} ms")
        
        val tracer = pipeline.getTracer()
        Log.i("VyuhaBenchmark", "P95 Latency: ${tracer.getP95(com.vyuha.sdk.instrumentation.LatencyTracer.SPAN_TOTAL_LOCAL)} ms")
        
        // Validate constraints
        assertTrue("Average CPU time should be < 10ms", avgCpuTime < 10.0)
        assertTrue("P95 Latency should be < 50ms", tracer.getP95(com.vyuha.sdk.instrumentation.LatencyTracer.SPAN_TOTAL_LOCAL) < 50.0)
    }
    
    private fun runIteration(pipeline: Pipeline, aggregator: ContextAggregator) {
        val snapshot = aggregator.aggregate(
            sessionId = "bench",
            amountBucket = AmountBucket.HIGH,
            beneficiaryNovelty = 0.5,
            channel = "UPI",
            communicationActive = true,
            captureRisk = false,
            overlayRisk = false,
            deviationScore = 0.5,
            graphRiskToken = GraphRiskToken(
                vpaHash = "bench_hash",
                riskScore = 0.5,
                confidence = 0.9,
                reasonCodes = emptyList(),
                issuedAt = 0,
                expiresAt = System.currentTimeMillis() / 1000 + 3600,
                signature = "sig"
            )
        )
        pipeline.evaluate(snapshot)
    }
}
