package com.bos.sphere

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Trace
import androidx.compose.runtime.withFrameNanos
import com.bos.sphere.core.quality.TraceSections

/**
 * NFR-01 / NFR-02: marks the moment home is interactive (first frame with the app list drawn). `reportFullyDrawn` is
 * what the startup macrobenchmark reads as time to full display; the async trace section runs from process start.
 */
object HomeTiming {
    private const val COOKIE = 1
    @Volatile private var reported = false

    fun processStarted() = Trace.beginAsyncSection(TraceSections.HOME_INTERACTIVE, COOKIE)

    suspend fun homeDrawn(context: Context) {
        if (reported) return
        withFrameNanos { }
        reported = true
        Trace.endAsyncSection(TraceSections.HOME_INTERACTIVE, COOKIE)
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) { c.reportFullyDrawn(); break }; c = c.baseContext }
    }
}
