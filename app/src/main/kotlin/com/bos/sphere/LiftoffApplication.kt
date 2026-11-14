package com.bos.sphere

import android.app.Application
import com.bos.sphere.core.quality.CrashReports
import com.bos.sphere.core.quality.DebugStrictMode
import java.io.File

/**
 * Process-level quality hooks (NFR-09, NFR-12, NFR-17). Everything here is additive and failure-tolerant:
 * StrictMode in debuggable builds only, and a crash handler that saves a local report and then hands over to the
 * handler that was already installed. No network, no upload.
 */
class LiftoffApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.installIfDebuggable(applicationInfo)
        runCatching {
            val reports = CrashReports(File(filesDir, "crash-reports"))
            // Disk write kept off the main thread so process start stays clean under StrictMode (NFR-02, NFR-12).
            Thread { runCatching { reports.sessionStarted() } }.apply { name = "liftoff-session-ledger"; isDaemon = true }.start()
            CrashReports.install(reports)
        }
    }
}
