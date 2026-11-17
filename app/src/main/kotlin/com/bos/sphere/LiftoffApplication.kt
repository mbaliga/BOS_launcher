package com.bos.sphere

import android.app.Application
import com.bos.sphere.core.quality.CrashFreeLedger
import com.bos.sphere.core.quality.DebugStrictMode
import dev.aarso.crashrecovery.CrashRecovery
import java.io.File

/**
 * Process-level quality hooks (NFR-09, NFR-12, NFR-17). Everything here is additive and failure-tolerant.
 *
 * Crash capture and the recovery screen are the shared crash-recovery utility (module :crash-recovery, docs/decisions/crashRecovery.md):
 * it is installed as the FIRST statement, before anything that could itself throw, so a startup crash is captured. The only local
 * piece left is the session ledger behind the crash-free percentage (NFR-09). No network, no upload (NFR-17).
 */
class LiftoffApplication : Application() {
    override fun onCreate() {
        CrashRecovery.install(this, appLabel = "Liftoff") // must stay the first statement (CrashRecoveryWiringTest)
        super.onCreate()
        // Native crashes and ANRs never reach the JVM handler: the OS exit history is read here, once per death (API 30+).
        CrashRecoveryHost.onProcessStart(this)
        DebugStrictMode.installIfDebuggable(applicationInfo)
        HomeTiming.processStarted()
        PrefsWarmup.start(this)
        // PAL-24 (wave 20 audit): the Lab's forced signal state never reaches a build without the Lab (import or upgrade can carry the pref).
        com.bos.sphere.feature.grid.SignalForceGate.labAvailable = com.bos.sphere.ui.LabGate.visible()
        if (!com.bos.sphere.ui.LabGate.visible()) {
            Thread {
                runCatching {
                    val prefs = com.bos.sphere.core.data.LauncherStores.get(this).prefs
                    if (prefs.prefs.value.radarSignalForce != com.bos.sphere.core.data.SignalForce.Live) prefs.update { it.copy(radarSignalForce = com.bos.sphere.core.data.SignalForce.Live) }
                }
            }.apply { name = "liftoff-signal-force-reset"; isDaemon = true }.start()
        }
        runCatching {
            val ledger = CrashFreeLedger(File(filesDir, "crash-reports"))
            // Disk write kept off the main thread so process start stays clean under StrictMode (NFR-02, NFR-12).
            Thread { runCatching { ledger.sessionStarted() } }.apply { name = "liftoff-session-ledger"; isDaemon = true }.start()
            CrashFreeLedger.install(ledger)
        }
    }

    companion object {
        const val APP_LABEL = CrashRecoveryHost.APP_LABEL
    }
}
