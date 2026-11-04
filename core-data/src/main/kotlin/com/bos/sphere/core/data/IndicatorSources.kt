package com.bos.sphere.core.data

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Live = coloured by value; Idle = solid neutral; Off = dark. */
enum class ReadingKind { Live, Idle, Off }

/** [value] is 0..1 where 1 means "high". */
data class IndicatorReading(val kind: ReadingKind, val value: Float, val label: String)

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

    private var metric: IndicatorMetric = IndicatorMetric.Network
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
            IndicatorMetric.Network -> {
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
            IndicatorMetric.Storage -> publishStorage()
            IndicatorMetric.Memory -> jobs += sc.launch {
                while (isActive) { publishMemory(); delay(5_000) }
            }
            IndicatorMetric.Notifications -> jobs += sc.launch {
                NotificationProvider.notifications.collect { list ->
                    val n = list.size
                    _reading.value = if (n == 0) IndicatorReading(ReadingKind.Idle, 0f, "0")
                    else IndicatorReading(ReadingKind.Live, minOf(n / 10f, 1f), n.toString())
                }
            }
            IndicatorMetric.Volume -> {
                publishVolume()
                register(VOLUME_ACTION) { publishVolume() }
            }
            IndicatorMetric.DayProgress -> {
                publishDay()
                register(Intent.ACTION_TIME_TICK) { publishDay() }
            }
            IndicatorMetric.Fixed -> _reading.value = IndicatorReading(ReadingKind.Idle, 0f, "")
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

    private fun live(value: Float, label: String) {
        _reading.value = IndicatorReading(ReadingKind.Live, value.coerceIn(0f, 1f), label)
    }

    private fun publishBattery(i: Intent?) {
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) return
        val f = level / scale.toFloat()
        live(f, "${(f * 100).toInt()}%")
    }

    private fun publishStorage() {
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.totalBytes.toFloat()
            val used = if (total > 0f) (total - st.availableBytes) / total else 0f
            live(used, "${(used.coerceIn(0f, 1f) * 100).toInt()}%")
        }.onFailure { _reading.value = IndicatorReading(ReadingKind.Idle, 0f, "") }
    }

    private fun publishMemory() {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem <= 0) return
        val used = (mi.totalMem - mi.availMem).toFloat() / mi.totalMem
        live(used, "${(used.coerceIn(0f, 1f) * 100).toInt()}%")
    }

    private fun publishVolume() {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val f = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        live(f, "${(f * 100).toInt()}%")
    }

    private fun publishDay() {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        live((h * 60 + m) / 1440f, "%02d:%02d".format(h, m))
    }

    private companion object {
        const val VOLUME_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
