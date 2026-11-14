package com.bos.sphere.core.quality

import java.io.File

/**
 * NFR-17: what the user-initiated share action sends. Pure text building; the Android share sheet (ACTION_SEND with
 * the text, chosen by the user) lives in the app. Nothing here, or anywhere in the launcher, uploads a report.
 */
object CrashShare {
    const val SUBJECT = "Liftoff crash report"

    /** One line per report for a list: file time and the first line of the exception. */
    fun summary(report: File, text: String = report.readText()): String {
        val firstException = text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("Liftoff crash report") && !it.startsWith("time:") && !it.startsWith("thread:") }
        return firstException?.take(120) ?: report.name
    }

    /** Body handed to the share sheet: the report as saved, with a note that nothing was sent automatically. */
    fun body(reportText: String): String = "$reportText\n(Shared by you from Liftoff. Liftoff never sends reports by itself.)\n"

    /** Crash-free line for the page header, from the local ledger. */
    fun ledgerLine(percent: Double): String = "%.2f%% of recorded sessions ended without a crash (target %.1f%%)".format(percent, CrashReports.TARGET_PERCENT)
}
