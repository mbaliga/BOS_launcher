package com.bos.sphere.core.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure motion-gesture recogniser fed raw accelerometer (m/s², gravity included) and gyroscope
 * (rad/s) samples with timestamps in nanoseconds. Android-free so it is unit-tested with
 * synthetic traces; [MotionGestureSensors] wires it to the real sensors.
 *
 * Recognised (see [HomeGesture]):
 * - **Shake** — ≥4 sharp acceleration spikes inside ~1.2 s.
 * - **Flick twice** — ≥3 alternating-direction wrist twists (gyro about the long axis) inside
 *   ~1.1 s, *without* the screen turning face-down (that would be a flip).
 * - **Flip twice** — face-up → down → up → down → up inside ~3 s.
 * - **Place face down** — goes face-down from face-up and stays there for ~1 s.
 *
 * A shared cooldown after any gesture keeps one physical motion from firing two gestures.
 * [sensitivity] (0..1) lowers the thresholds as it rises.
 */
class MotionGestureDetector(
    sensitivity: Float = 0.5f,
    private val onGesture: (HomeGesture) -> Unit,
) {
    var sensitivity: Float = sensitivity.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    // Thresholds interpolate from firm (s=0) to light (s=1).
    private val shakeThreshold get() = lerp(17f, 9f, sensitivity) // m/s² above gravity
    private val twistThreshold get() = lerp(7.5f, 4f, sensitivity) // rad/s

    private var lastFireNs = Long.MIN_VALUE / 2

    // --- shake
    private var shakeArmed = true
    private val shakeSpikes = ArrayDeque<Long>()

    // --- flick (twist)
    private var twistArmed = true
    private val twistPeaks = ArrayDeque<Pair<Long, Int>>() // time, sign
    private var twistPeakSign = 0
    private var twistPeakMag = 0f

    // --- orientation (flip / face down), from low-passed gravity Z
    private var gz = Float.NaN
    private var face = Face.Unknown
    private val faceTransitions = ArrayDeque<Pair<Long, Face>>()
    private var faceDownSinceNs = -1L
    private var faceDownFromUp = false
    private var faceDownFired = false

    private enum class Face { Up, Down, Unknown }

    fun onAccelerometer(tNs: Long, x: Float, y: Float, z: Float) {
        // Shake: magnitude spikes above gravity, re-armed once the signal settles.
        val excess = abs(sqrt(x * x + y * y + z * z) - GRAVITY)
        if (shakeArmed && excess > shakeThreshold) {
            shakeArmed = false
            shakeSpikes.addLast(tNs)
            prune(shakeSpikes, tNs, SHAKE_WINDOW_NS) { it }
            if (shakeSpikes.size >= SHAKE_SPIKES) {
                shakeSpikes.clear()
                fire(tNs, HomeGesture.Shake)
            }
        } else if (!shakeArmed && excess < shakeThreshold * 0.45f) {
            shakeArmed = true
        }

        // Orientation: low-pass gravity Z with hysteresis between face-up and face-down.
        gz = if (gz.isNaN()) z else gz + (z - gz) * GRAVITY_ALPHA
        val next = when {
            gz > FACE_THRESHOLD -> Face.Up
            gz < -FACE_THRESHOLD -> Face.Down
            else -> face
        }
        if (next != face) {
            val prev = face
            face = next
            if (prev != Face.Unknown) {
                faceTransitions.addLast(tNs to next)
                prune(faceTransitions, tNs, FLIP_WINDOW_NS) { it.first }
                if (faceTransitions.size >= FLIP_TRANSITIONS && next == Face.Up) {
                    faceTransitions.clear()
                    fire(tNs, HomeGesture.DoubleFlip)
                }
            }
            if (next == Face.Down) {
                faceDownSinceNs = tNs
                faceDownFromUp = prev == Face.Up
                faceDownFired = false
            } else {
                faceDownSinceNs = -1L
            }
        }
        if (face == Face.Down && faceDownFromUp && !faceDownFired && faceDownSinceNs >= 0 &&
            tNs - faceDownSinceNs >= FACE_DOWN_HOLD_NS
        ) {
            faceDownFired = true
            // A deliberate rest is not the start of a double flip.
            faceTransitions.clear()
            fire(tNs, HomeGesture.FaceDown)
        }
    }

    fun onGyroscope(tNs: Long, @Suppress("UNUSED_PARAMETER") x: Float, y: Float, @Suppress("UNUSED_PARAMETER") z: Float) {
        // Wrist twist = rotation about the device's long (Y) axis. Track each excursion's peak.
        val mag = abs(y)
        val sign = if (y >= 0f) 1 else -1
        if (twistArmed && mag > twistThreshold) {
            twistArmed = false
            twistPeakSign = sign
            twistPeakMag = mag
        } else if (!twistArmed) {
            if (mag > twistPeakMag) twistPeakMag = mag
            if (mag < twistThreshold * 0.35f) {
                twistArmed = true
                val last = twistPeaks.lastOrNull()
                if (last != null && last.second == twistPeakSign) twistPeaks.clear()
                twistPeaks.addLast(tNs to twistPeakSign)
                prune(twistPeaks, tNs, FLICK_WINDOW_NS) { it.first }
                if (twistPeaks.size >= FLICK_PEAKS) {
                    val start = twistPeaks.first().first
                    twistPeaks.clear()
                    // Turning over (a flip) also twists — only count it if the face never flipped.
                    val flipped = faceTransitions.any { it.first >= start - FLICK_WINDOW_NS / 2 } || face == Face.Down
                    if (!flipped) fire(tNs, HomeGesture.DoubleFlick)
                }
            }
        }
    }

    /** Clears transient state (e.g. when listening resumes after a pause). */
    fun reset() {
        shakeSpikes.clear(); shakeArmed = true
        twistPeaks.clear(); twistArmed = true
        faceTransitions.clear(); gz = Float.NaN; face = Face.Unknown
        faceDownSinceNs = -1L; faceDownFired = false
    }

    private fun fire(tNs: Long, gesture: HomeGesture) {
        if (tNs - lastFireNs < COOLDOWN_NS) return
        lastFireNs = tNs
        shakeSpikes.clear(); twistPeaks.clear()
        onGesture(gesture)
    }

    private inline fun <T> prune(q: ArrayDeque<T>, now: Long, window: Long, time: (T) -> Long) {
        while (q.isNotEmpty() && now - time(q.first()) > window) q.removeFirst()
    }

    companion object {
        const val GRAVITY = 9.81f
        private const val MS = 1_000_000L
        private const val SHAKE_SPIKES = 4
        private const val SHAKE_WINDOW_NS = 1_200 * MS
        private const val FLICK_PEAKS = 3
        private const val FLICK_WINDOW_NS = 1_100 * MS
        private const val FLIP_TRANSITIONS = 4
        private const val FLIP_WINDOW_NS = 3_000 * MS
        private const val FACE_DOWN_HOLD_NS = 1_000 * MS
        private const val COOLDOWN_NS = 1_500 * MS
        private const val FACE_THRESHOLD = 6.5f
        private const val GRAVITY_ALPHA = 0.25f

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }
}
