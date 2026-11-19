package com.bos.sphere.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Live = coloured by value; Idle = solid neutral; Off = dark. */
enum class ReadingKind { Live, Idle, Off }

/** [value] is 0..1 where 1 means "high". */
data class IndicatorReading(val kind: ReadingKind, val value: Float, val label: String, val charging: Boolean = false)

/** Pure colour mapping (no Android types) so it can be unit-tested on the JVM. */
object IndicatorColors {
    private const val OFF = 0xFF000000L

    fun argb(reading: IndicatorReading, low: Long, mid: Long, high: Long, idle: Long, invert: Boolean): Long =
        when (reading.kind) {
            ReadingKind.Off -> OFF
            ReadingKind.Idle -> idle
            ReadingKind.Live -> {
                val v0 = reading.value.let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
                val v = if (invert) 1f - v0 else v0
                if (v < 0.5f) lerp(low, mid, v / 0.5f) else lerp(mid, high, (v - 0.5f) / 0.5f)
            }
        }

    /** Always this red when the value is lost (wave 35); the palette colours the rest of the alert. */
    const val URGENT_RED = 0xFFFF2D2DL

    /** The alert colour: the palette's [mid] (amber) while low, easing to its [low] (red) as it worsens; the fixed [URGENT_RED] once urgent. */
    fun alertArgb(severity: IndicatorSeverity, low: Long, mid: Long): Long =
        if (severity.urgent) URGENT_RED else lerp(mid, low, severity.level.coerceIn(0f, 1f))

    internal fun lerp(a: Long, b: Long, t: Float): Long {
        fun ch(shift: Int): Long {
            val ca = ((a shr shift) and 0xFF).toFloat()
            val cb = ((b shr shift) and 0xFF).toFloat()
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255).toLong()
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * Produces an [IndicatorReading] for the chosen [IndicatorMetric]. [start] / [stop] bracket the
 * observation (home screen visible) and are idempotent; [setMetric] re-targets the live sources.
 * Call all methods from the main thread.
 */
class IndicatorSource(context: Context) {
    private val ctx = context.applicationContext
    private val network = NetworkSignal(ctx)

    private val _reading = MutableStateFlow(IndicatorReading(ReadingKind.Idle, 0f, ""))
    val reading: StateFlow<IndicatorReading> = _reading.asStateFlow()

    private var metric: IndicatorMetric = IndicatorMetric.Signal
    private var running = false
    private var scope: CoroutineScope? = null
    private val jobs = mutableListOf<Job>()
    private val receivers = mutableListOf<BroadcastReceiver>()
    private var networkStarted = false

    fun setMetric(m: IndicatorMetric) {
        if (m == metric) return
        metric = m
        if (running) retarget()
    }

    fun start() {
        if (running) return
        running = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        retarget()
    }

    fun stop() {
        if (!running) return
        running = false
        teardown()
        scope?.cancel()
        scope = null
    }

    private fun teardown() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        receivers.forEach { runCatching { ctx.unregisterReceiver(it) } }
        receivers.clear()
        if (networkStarted) { network.stop(); networkStarted = false }
    }

    private fun retarget() {
        teardown()
        val sc = scope ?: return
        when (metric) {
            IndicatorMetric.Signal -> {
                network.start(); networkStarted = true
                jobs += sc.launch {
                    network.status.collect { s ->
                        _reading.value = when (s.kind) {
                            NetworkKind.Airplane -> IndicatorReading(ReadingKind.Idle, 0f, "Airplane")
                            NetworkKind.None -> IndicatorReading(ReadingKind.Off, 0f, "Offline")
                            else -> IndicatorReading(ReadingKind.Live, s.strength, "${(s.strength * 100).toInt()}%")
                        }
                    }
                }
            }
            IndicatorMetric.Battery -> {
                val sticky = register(Intent.ACTION_BATTERY_CHANGED) { publishBattery(it) }
                publishBattery(sticky)
            }
        }
    }

    /** Registers a not-exported receiver; returns the sticky intent (if any). */
    private fun register(action: String, onIntent: (Intent?) -> Unit): Intent? {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = onIntent(i)
        }
        val sticky = runCatching {
            ctx.registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        }.getOrNull()
        receivers += r
        return sticky
    }

    private fun publishBattery(i: Intent?) {
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) return
        val f = (level / scale.toFloat()).coerceIn(0f, 1f)
        val charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        _reading.value = IndicatorReading(ReadingKind.Live, f, "${(f * 100).toInt()}%", charging)
    }
}
