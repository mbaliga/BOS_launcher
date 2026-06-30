package com.bos.sphere.core.data

import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Performance monitoring utilities for profiling Sphere Launcher. Used during development to
 * identify bottlenecks and measure optimization impact.
 *
 * Example usage:
 * ```
 * val timer = PerformanceMonitor.start("search")
 * val results = AppSearch.rank(apps, query)
 * timer.end("${results.size} results")
 * ```
 */
object PerformanceMonitor {
    private const val TAG = "SpherePerf"
    private val enabled = Log.isLoggable(TAG, Log.DEBUG)

    data class Timer(val name: String, val startNs: Long) {
        fun end(label: String = "") {
            if (!enabled) return
            val durationMs = (System.nanoTime() - startNs) / 1_000_000f
            Log.d(TAG, "$name: ${String.format("%.2f", durationMs)}ms ${if (label.isNotEmpty()) "($label)" else ""}")
        }
    }

    fun start(name: String): Timer {
        if (enabled) Log.d(TAG, "$name START")
        return Timer(name, System.nanoTime())
    }

    fun log(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun logFrameMetrics(frameCount: Int, durationMs: Duration) {
        if (!enabled) return
        val fps = (frameCount / durationMs.inWholeMilliseconds.toFloat()) * 1000
        Log.d(TAG, "Frame metrics: $frameCount frames in ${durationMs.inWholeMilliseconds}ms = ${String.format("%.1f", fps)} FPS")
    }
}
