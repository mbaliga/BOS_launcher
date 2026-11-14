package com.bos.sphere.core.quality

import java.io.File

/**
 * NFR-17 / NFR-09: crash reports live in app-private storage only and leave the device solely when the user shares
 * one by an explicit action. Nothing here opens a network connection. The same store keeps a session ledger so the
 * crash-free session rate can be computed locally.
 */
class CrashReports(private val dir: File, private val maxReports: Int = 20) {

    init { dir.mkdirs() }

    /** Saves [t] as a text report and returns the file; keeps only the newest [maxReports]. */
    fun record(t: Throwable, thread: String = "main", nowMs: Long = System.currentTimeMillis()): File {
        val f = File(dir, "crash-$nowMs.txt")
        f.writeText(format(t, thread, nowMs))
        markSessionCrashed()
        list().drop(maxReports).forEach { it.delete() }
        return f
    }

    /** Newest first. */
    fun list(): List<File> = dir.listFiles { x -> x.name.startsWith("crash-") && x.name.endsWith(".txt") }
        ?.sortedByDescending { it.name }.orEmpty()

    /** The text a user shares by explicit action (the share sheet is the only way a report leaves the device). */
    fun textForSharing(f: File): String = f.readText()

    fun clear() = list().forEach { it.delete() }

    private val ledger get() = File(dir, "sessions.txt")

    fun sessionStarted() { ledger.appendText("S\n") }
    private fun markSessionCrashed() { ledger.appendText("C\n") }

    /** Crash-free session rate in percent from the local ledger; 100 when no session was recorded. */
    fun crashFreePercent(): Double = crashFreePercent(ledger.takeIf { it.exists() }?.readLines().orEmpty())

    companion object {
        fun format(t: Throwable, thread: String, nowMs: Long): String = buildString {
            appendLine("Liftoff crash report (saved locally, never uploaded)")
            appendLine("time: $nowMs")
            appendLine("thread: $thread")
            appendLine(t.stackTraceToString())
        }

        fun crashFreePercent(ledger: List<String>): Double {
            val sessions = ledger.count { it == "S" }
            if (sessions == 0) return 100.0
            val crashed = ledger.count { it == "C" }.coerceAtMost(sessions)
            return (sessions - crashed) * 100.0 / sessions
        }

        /** NFR-09 gate: 99.8% crash-free. */
        const val TARGET_PERCENT = 99.8

        /** Chains in front of the existing handler so nothing already installed (stock crash dialog, loop guard) is lost. */
        fun install(reports: CrashReports) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { th, e ->
                runCatching { reports.record(e, th.name) }
                previous?.uncaughtException(th, e)
            }
        }
    }
}
