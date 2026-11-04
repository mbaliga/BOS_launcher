package com.bos.sphere.core.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Binds [MotionGestureDetector] to the accelerometer + gyroscope. [start] / [stop] are
 * idempotent; the caller decides the lifetime (the home activity while resumed, or the
 * background service). Each recognised gesture gets a short haptic tick before [onGesture].
 */
class MotionGestureSensors(
    context: Context,
    private val onGesture: (HomeGesture) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)
    private val accel: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val detector = MotionGestureDetector { gesture ->
        runCatching { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)) }
        onGesture(gesture)
    }

    private var running = false

    /** Whether this device can recognise at least the accelerometer-based gestures. */
    val available: Boolean get() = accel != null

    var sensitivity: Float
        get() = detector.sensitivity
        set(value) { detector.sensitivity = value }

    fun start() {
        if (running || sensorManager == null || accel == null) return
        running = true
        detector.reset()
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> detector.onAccelerometer(event.timestamp, v[0], v[1], v[2])
            Sensor.TYPE_GYROSCOPE -> detector.onGyroscope(event.timestamp, v[0], v[1], v[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
