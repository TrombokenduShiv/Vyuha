/**
 * Vyuha 2.0 — Memory and CPU Profiler
 * =====================================
 * Utilities for tracking memory footprint and CPU time of the local
 * inference pipeline, ensuring we stay within low-end device constraints.
 *
 * Tracks:
 * - Allocated Heap Memory
 * - Peak Memory usage
 * - Thread CPU time (for accurate compute measurement independent of wall-clock)
 */
package com.vyuha.sdk.instrumentation

import android.os.Debug
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

object MemoryCpuProfiler {

    private val baseHeapAllocated = AtomicLong(0)
    private val peakHeapAllocated = AtomicLong(0)

    /**
     * Start a profiling session. Call this before running the pipeline.
     */
    fun startProfiling() {
        Runtime.getRuntime().gc() // Suggest GC for accurate baseline
        val allocated = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        baseHeapAllocated.set(allocated)
    }

    /**
     * End profiling session and return a summary of resource usage.
     * @param startCpuTimeMs The Thread CPU time (from SystemClock.currentThreadTimeMillis()) at start
     */
    fun endProfiling(startCpuTimeMs: Long): ProfilingResult {
        val currentCpuTime = SystemClock.currentThreadTimeMillis()
        val cpuTimeConsumed = currentCpuTime - startCpuTimeMs

        val allocated = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryDelta = allocated - baseHeapAllocated.get()

        // Update peak
        var currentPeak = peakHeapAllocated.get()
        while (allocated > currentPeak) {
            peakHeapAllocated.compareAndSet(currentPeak, allocated)
            currentPeak = peakHeapAllocated.get()
        }

        return ProfilingResult(
            cpuTimeConsumedMs = cpuTimeConsumed,
            memoryAllocatedBytes = memoryDelta.coerceAtLeast(0),
            peakMemoryBytes = peakHeapAllocated.get()
        )
    }

    /**
     * Get the current thread CPU time in milliseconds.
     * This measures actual CPU cycles spent, ignoring blocking I/O or preemptions.
     */
    fun getCurrentThreadCpuTimeMs(): Long {
        return SystemClock.currentThreadTimeMillis()
    }
}

/**
 * Results from a profiling session.
 */
data class ProfilingResult(
    /** Actual CPU time consumed (ms). */
    val cpuTimeConsumedMs: Long,
    /** Memory allocated during the block (bytes). */
    val memoryAllocatedBytes: Long,
    /** Peak memory observed since app start (bytes). */
    val peakMemoryBytes: Long
) {
    /** Memory delta in KB */
    val memoryAllocatedKb: Double get() = memoryAllocatedBytes / 1024.0
    
    /** Peak memory in MB */
    val peakMemoryMb: Double get() = peakMemoryBytes / (1024.0 * 1024.0)
    
    fun toDebugString(): String = 
        "CPU Time: ${cpuTimeConsumedMs}ms | Mem Allocated: ${"%.1f".format(memoryAllocatedKb)}KB | Peak Mem: ${"%.1f".format(peakMemoryMb)}MB"
}
