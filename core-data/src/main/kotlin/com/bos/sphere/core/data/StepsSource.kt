package com.bos.sphere.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** Pure helper: today's steps from the since-boot counter and the day's stored baseline. */
object StepMath {
    /** [baseline] is the counter value at the start of the day; a counter below it means the phone rebooted. */
    fun today(counter: Float, baseline: Float?): Pair<Int, Float> {
        val base = when {
            baseline == null -> counter
            counter < baseline -> 0f // rebooted: the counter restarted
            else -> baseline
        }
        return (counter - base).toInt().coerceAtLeast(0) to base
    }

    fun dayKey(cal: Calendar): Int = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

/**
 * Steps today, from the hardware step counter. Needs the ACTIVITY_RECOGNITION permission (runtime);
 * the counter counts since boot, so a per-day baseline is stored.
 */
class StepsSource(context: Context) {
    private val ctx = context.applicationContext
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
    private val _steps = MutableStateFlow<Int?>(null)
    val steps: StateFlow<Int?> = _steps.asStateFlow()
    private var running = false

    val available: Boolean get() = sensor != null
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val counter = e.values.firstOrNull() ?: return
            val day = StepMath.dayKey(Calendar.getInstance())
            val baseline = if (prefs.getInt("day", -1) == day) prefs.getFloat("base", counter) else null
            val (today, base) = StepMath.today(counter, baseline)
            prefs.edit().putInt("day", day).putFloat("base", base).apply()
            _steps.value = today
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (running || sensor == null || !hasPermission()) return
        running = true
        sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(listener)
    }
}
