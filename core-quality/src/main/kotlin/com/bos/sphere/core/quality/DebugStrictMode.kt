package com.bos.sphere.core.quality

import android.content.pm.ApplicationInfo
import android.os.StrictMode

/**
 * NFR-12: StrictMode in debug builds only. Logs (never kills) so a daily-driver debug build keeps working, and the log
 * is what the "StrictMode clean" check reads. Release builds are never touched.
 */
object DebugStrictMode {
    fun shouldEnable(applicationFlags: Int): Boolean = applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun installIfDebuggable(appInfo: ApplicationInfo): Boolean {
        if (!shouldEnable(appInfo.flags)) return false
        install()
        return true
    }

    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
