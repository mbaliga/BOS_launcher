package com.bos.sphere.core.quality

import java.io.File

/**
 * NFR-09: the local session ledger behind the crash-free session percentage. One line per session start ("S") and one
 * per crash ("C") in app-private storage; nothing here opens a network connection.
 *
 * Wave 18: this is ALL that is left of the launcher's own crash reporting. The report itself (capture, file format,
 * the recovery screen, Copy, Save, Share) is the shared `dev.aarso:crash-recovery` utility (module :crash-recovery),
 * which is the single owner of crash reports; see docs/decisions/crashRecovery.md. The ledger exists only because
 * that utility does not count healthy sessions, so it cannot give a percentage.
 */
class CrashFreeLedger(private val dir: File) {
    private val ledger get() = File(dir, "sessions.txt")

    fun sessionStarted() { dir.mkdirs(); ledger.appendText("S\n") }

    /** Counts a crash against the session (called from the uncaught-exception hook, never throws). */
    fun crashed() { runCatching { dir.mkdirs(); ledger.appendText("C\n") } }

    /** Crash-free session rate in percent from the local ledger; 100 when no session was recorded. */
    fun crashFreePercent(): Double = crashFreePercent(ledger.takeIf { it.exists() }?.readLines().orEmpty())

    companion object {
        fun crashFreePercent(ledger: List<String>): Double {
            val sessions = ledger.count { it == "S" }
            if (sessions == 0) return 100.0
            val crashed = ledger.count { it == "C" }.coerceAtMost(sessions)
            return (sessions - crashed) * 100.0 / sessions
        }

        /** NFR-09 gate: 99.8% crash-free. */
        const val TARGET_PERCENT = 99.8

        /** Crash-free line for the Ground Control pane, from the local ledger. */
        fun line(percent: Double): String = "%.2f%% of recorded sessions ended without a crash (target %.1f%%)".format(percent, TARGET_PERCENT)

        /**
         * Chains in front of the handler that is already installed (the shared crash-recovery one, then the system's) so
         * nothing is lost: the crash is counted, then the previous handler captures the report.
         */
        fun install(ledger: CrashFreeLedger) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { th, e ->
                ledger.crashed()
                previous?.uncaughtException(th, e)
            }
        }
    }
}
