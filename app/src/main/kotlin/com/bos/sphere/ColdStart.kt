package com.bos.sphere

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.bos.sphere.core.quality.ColdStartMath
import com.bos.sphere.ui.DeviceChecksStore

/**
 * Wave 23 (NFR-12; docs/decisions/deviceKit.md): the time from process start (`Process.getStartElapsedRealtime`, public, API 24) to the
 * first drawn home frame, captured once per process. It is the same clock the startup benchmark approximates, but taken on the phone in
 * the owner's hand. If something other than opening the app started the process earlier (boot, a widget, a broadcast), the number
 * includes that wait; the page says to swipe the app away from recents first. The process START REASON (ApplicationStartInfo) is
 * API 35 and minSdk is 31, so it is deliberately not read.
 */
object ColdStart {
    @Volatile var thisProcessMs: Long? = null; private set

    /** Call when the first home frame has been drawn; only the first call in a process counts. Disk work is off the main thread. */
    fun firstFrameDrawn(context: Context, processStartElapsedMs: Long = Process.getStartElapsedRealtime(), nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (thisProcessMs != null) return
        val ms = ColdStartMath.elapsedMs(processStartElapsedMs, nowElapsedMs) ?: return
        thisProcessMs = ms
        val app = context.applicationContext
        Thread { runCatching { DeviceChecksStore.open(app).saveColdStart(ms) } }.apply { name = "liftoff-cold-start"; isDaemon = true }.start()
    }
}
