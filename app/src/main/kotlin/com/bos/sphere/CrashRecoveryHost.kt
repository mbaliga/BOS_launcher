package com.bos.sphere

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import dev.aarso.crashrecovery.CrashRecovery
import java.io.File

/**
 * The few things the launcher adds around the shared crash-recovery utility (docs/decisions/crashRecovery.md). The library owns the
 * report, its screen and its streak; this object only (1) keeps a copy of the last shown report so safe mode can open it again after
 * Continue cleared the library's own file, and (2) stops one JVM crash from being reported twice. Everything is runCatching: it runs
 * at process start and must never be what crashes.
 *
 * The file names below are the library's private store names (crash-recovery 1.4.0); CrashRecoveryHostTest writes a report through
 * the public API and fails if they ever drift.
 */
object CrashRecoveryHost {
    const val APP_LABEL = "Liftoff"
    internal const val LIBRARY_REPORT = "crash_recovery_report.txt"
    internal const val LIBRARY_EXIT_SEEN = "crash_recovery_exit_seen.txt"
    internal const val LIBRARY_STREAK = "crash_recovery_streak.txt"
    internal const val RETAINED = "crash_recovery_last_shown.txt"

    /** How long after its newest crash the library's streak still counts as "crashed again after Continue" (30 minutes). */
    const val LIVE_STREAK_MS = 30 * 60_000L

    private fun files(c: Context) = c.applicationContext.filesDir

    /**
     * Called from Application.onCreate right after [CrashRecovery.install]. When a JVM crash report is pending, the OS exit record of
     * that same death must not be turned into a second report once Continue clears the first (the library advances its exit watermark
     * only for deaths it synthesised itself), so the watermark is moved past it here. Then the pending report is copied for safe mode.
     */
    fun onProcessStart(context: Context) {
        runCatching {
            if (CrashRecovery.pending(context) != null) markExitHistorySeen(context)
            CrashRecovery.captureExitDeath(context, APP_LABEL) // native crash / ANR: nothing to the JVM handler, so read from the OS history
            retainPending(context)
        }
    }

    private fun markExitHistorySeen(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val newest = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            .filter { it.reason == ApplicationExitInfo.REASON_CRASH }.maxOfOrNull { it.timestamp } ?: return
        val file = File(files(context), LIBRARY_EXIT_SEEN)
        val seen = runCatching { file.readText().trim().toLong() }.getOrDefault(0L)
        if (newest > seen) file.writeText(newest.toString())
    }

    private fun retainPending(context: Context) {
        val src = File(files(context), LIBRARY_REPORT)
        if (src.exists()) src.copyTo(File(files(context), RETAINED), overwrite = true)
    }

    /**
     * The library's crash streak, but only while it is recent. The library keeps the count until a stable start or its own reset, so a
     * streak of 2 left by a safe mode that was swiped away would put every start of the next days into safe mode again. Safe mode
     * needs a loop NOW: a streak whose newest crash is older than [LIVE_STREAK_MS], or stamped after [now] (a clock set back), is 0.
     */
    fun liveStreak(context: Context, now: Long = System.currentTimeMillis()): Int = runCatching {
        val parts = File(files(context), LIBRARY_STREAK).takeIf { it.exists() }?.readText()?.split('\t') ?: return@runCatching 0
        val count = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@runCatching 0
        val at = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: return@runCatching 0
        if (now - at in 0..LIVE_STREAK_MS) count else 0
    }.getOrDefault(0)

    /** Below API 30 (no exit history): a JVM crash report was written at or after [since], pending or already shown. */
    fun reportSince(context: Context, since: Long): Boolean = runCatching {
        File(files(context), LIBRARY_REPORT).takeIf { it.exists() }?.lastModified()?.let { it >= since } == true ||
            File(files(context), RETAINED).takeIf { it.exists() }?.lastModified()?.let { it >= since } == true
    }.getOrDefault(false)

    /** A report exists that safe mode can show (the library's pending one, or the last one shown before Continue). */
    fun hasReport(context: Context): Boolean = runCatching {
        CrashRecovery.pending(context) != null || File(files(context), RETAINED).exists()
    }.getOrDefault(false)

    /**
     * Safe mode's "View crash report": puts the last shown report back into the library's store (when Continue removed it) and asks the
     * library to show its recovery screen for it. Returns false when there is no report to show. The calling activity finishes (the
     * library does that), and Continue on that screen comes back to a fresh launcher start.
     */
    fun reopen(activity: Activity): Boolean = runCatching {
        if (CrashRecovery.pending(activity) == null) {
            val kept = File(files(activity), RETAINED)
            if (!kept.exists()) return false
            kept.copyTo(File(files(activity), LIBRARY_REPORT), overwrite = true)
        }
        CrashRecovery.maybeShowRecovery(activity, APP_LABEL)
    }.getOrDefault(false)
}
