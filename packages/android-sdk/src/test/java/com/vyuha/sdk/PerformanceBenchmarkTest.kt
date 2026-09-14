package com.vyuha.sdk
import com.vyuha.sdk.contracts.*
import com.vyuha.sdk.pipeline.Pipeline
import org.junit.Assert.*
import org.junit.Test

/** JVM benchmark only. Physical Android latency still requires instrumentation. */
class PerformanceBenchmarkTest {
    @Test fun trainedLocalPathFitsDesktopJvmBudget() {
        val pipeline = Pipeline()
        val snapshot = ContextSnapshot(transaction = TransactionContext(AmountBucket.CRITICAL, .95),
            communication = CommunicationContext(true), device = DeviceContext(true, false), baseline = BaselineContext(.9))
        repeat(100) { pipeline.evaluate(snapshot) }
        val timings = DoubleArray(1000) {
            val start = System.nanoTime()
            val decision = pipeline.evaluate(snapshot)
            assertNotEquals(ActionId.A0_PASS, decision.actionId)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        println("Desktop JVM local path p50=${timings[499]}ms p95=${timings[949]}ms p99=${timings[989]}ms")
        assertTrue("Local JVM p95 exceeds 50ms", timings[949] < 50)
    }
}
