package com.bos.sphere.core.catalog

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * HOM-17: the time-split widget. [UsageSplit.build] turns per-app foreground time into the share
 * each app (and an "Other" remainder) took of the day. [UsageStatsSource] reads the real numbers
 * from the system's usage statistics, which need the user to grant "usage access" in system
 * settings; without it the widget shows how to grant it instead of numbers.
 */
data class UsageSample(val packageName: String, val foregroundMs: Long)

data class UsageSlice(val packageName: String, val ms: Long, val share: Float)

object UsageSplit {
    /** The [top] apps by time plus one remainder slice (package "") when more apps were used. Zero-time entries are dropped. */
    fun build(samples: List<UsageSample>, top: Int = 5): List<UsageSlice> {
        val merged = samples.filter { it.foregroundMs > 0 }.groupBy { it.packageName }.map { (p, v) -> p to v.sumOf { it.foregroundMs } }
            .sortedByDescending { it.second }
        val total = merged.sumOf { it.second }
        if (total <= 0) return emptyList()
        val head = merged.take(top)
        val rest = merged.drop(top).sumOf { it.second }
        val slices = head.map { UsageSlice(it.first, it.second, it.second.toFloat() / total) }
        return if (rest > 0) slices + UsageSlice("", rest, rest.toFloat() / total) else slices
    }

    fun totalMs(slices: List<UsageSlice>) = slices.sumOf { it.ms }

    /** "2 h 5 min", "35 min", "<1 min". */
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "<1 min"
            min < 60 -> "$min min"
            else -> "${min / 60} h ${min % 60} min"
        }
    }
}

class UsageStatsSource(context: Context) {
    private val app = context.applicationContext

    /** True once the user granted usage access to this launcher (a special access, not a runtime permission). */
    fun granted(): Boolean {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time per package from [fromMs] to [toMs]; empty without access. */
    fun samples(fromMs: Long, toMs: Long): List<UsageSample> {
        if (!granted()) return emptyList()
        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return runCatching {
            usm.queryAndAggregateUsageStats(fromMs, toMs).values.map { UsageSample(it.packageName, it.totalTimeInForeground) }
        }.getOrDefault(emptyList())
    }
}
