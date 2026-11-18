package com.bos.sphere.ui

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bos.sphere.core.quality.FrameStats
import com.bos.sphere.core.quality.ProbeResult
import com.bos.sphere.core.quality.ProbeSurface

/**
 * Wave 23 (NFR-05; docs/decisions/deviceKit.md): the frame-timing probe of the Lab's device checks. It uses the public
 * `Window.addOnFrameMetricsAvailableListener` (API 24), registered with a handler on its own background thread (the platform delivers the
 * callback there, never on the UI thread), after a short lead-in so the owner can leave Settings, for [RUN_MS]. The listener is removed
 * when the run ends and whenever the activity stops or is destroyed; nothing outlives the run. A stopped run is discarded, never reported.
 */
object DeviceProbe {
    const val LEAD_IN_MS = 3_000L
    const val RUN_MS = 10_000L

    enum class Phase { Idle, LeadIn, Measuring }

    var phase by mutableStateOf(Phase.Idle); private set
    var surface by mutableStateOf<ProbeSurface?>(null); private set
    /** Wall-clock end of the current phase, for the countdown. */
    var phaseEndsAtMs by mutableStateOf(0L); private set

    private var run: Run? = null

    /** Starts a run on [activity]'s window for [which]; [onDone] gets the result (null when no frame arrived). Ignored while another run is active. */
    fun start(activity: Activity, which: ProbeSurface, now: () -> Long = System::currentTimeMillis, onDone: (ProbeSurface, ProbeResult?) -> Unit) {
        if (run != null) return
        val r = Run(activity, which, now, onDone)
        run = r
        surface = which
        r.begin()
    }

    /** Ends an active run without a result. */
    fun cancel() { run?.stop(report = false) }

    private class Run(val activity: Activity, val which: ProbeSurface, val now: () -> Long, val onDone: (ProbeSurface, ProbeResult?) -> Unit) {
        private val main = Handler(Looper.getMainLooper())
        private val stats = FrameStats()
        @Volatile private var measuring = false
        private var thread: HandlerThread? = null
        private var listener: Window.OnFrameMetricsAvailableListener? = null
        private var window: Window? = null
        private val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = stop(report = false)
            override fun onDestroy(owner: LifecycleOwner) = stop(report = false)
        }
        private val owner = activity as? androidx.lifecycle.LifecycleOwner
        private var finished = false

        fun begin() {
            owner?.lifecycle?.addObserver(observer)
            phase = Phase.LeadIn; phaseEndsAtMs = now() + LEAD_IN_MS
            main.postDelayed({ measure() }, LEAD_IN_MS)
        }

        private fun measure() {
            if (finished) return
            val w = activity.window ?: return stop(report = false)
            val t = HandlerThread("liftoff-frame-probe").also { it.start() }
            thread = t
            val l = Window.OnFrameMetricsAvailableListener { _, fm, _ ->
                if (measuring) synchronized(stats) { stats.add(fm.getMetric(FrameMetrics.TOTAL_DURATION)) }
            }
            listener = l; window = w
            measuring = true
            w.addOnFrameMetricsAvailableListener(l, Handler(t.looper))
            phase = Phase.Measuring; phaseEndsAtMs = now() + RUN_MS
            main.postDelayed({ stop(report = true) }, RUN_MS)
        }

        fun stop(report: Boolean) {
            if (finished) return
            finished = true
            measuring = false
            main.removeCallbacksAndMessages(null)
            runCatching { listener?.let { window?.removeOnFrameMetricsAvailableListener(it) } }
            thread?.quitSafely()
            owner?.lifecycle?.removeObserver(observer)
            val hz = runCatching { activity.display?.refreshRate }.getOrNull() ?: 60f
            val result = if (report) synchronized(stats) { stats.summary(FrameStats.intervalNs(hz), now()) } else null
            run = null; phase = Phase.Idle; surface = null
            if (report) onDone(which, result)
        }
    }
}
