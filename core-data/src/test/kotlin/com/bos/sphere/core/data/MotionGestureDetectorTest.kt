package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic 50 Hz traces through [MotionGestureDetector]. */
class MotionGestureDetectorTest {

    private val dtNs = 20_000_000L // 50 Hz
    private val g = MotionGestureDetector.GRAVITY

    private class Rig {
        val fired = mutableListOf<HomeGesture>()
        val detector = MotionGestureDetector(0.5f) { fired += it }
        var t = 1_000_000_000L
    }

    /** Feeds [ms] of samples; [accel]/[gyro] give values at elapsed seconds within the segment. */
    private fun Rig.feed(
        ms: Int,
        accel: (Double) -> Triple<Float, Float, Float> = { Triple(0f, 0f, g) },
        gyro: (Double) -> Triple<Float, Float, Float> = { Triple(0f, 0f, 0f) },
    ) {
        val n = ms * 1_000_000L / dtNs
        for (i in 0 until n) {
            val s = i * dtNs / 1e9
            val (ax, ay, az) = accel(s)
            val (gx, gy, gz) = gyro(s)
            detector.onAccelerometer(t, ax, ay, az)
            detector.onGyroscope(t, gx, gy, gz)
            t += dtNs
        }
    }

    private fun Rig.rest(ms: Int = 2_000) = feed(ms)

    @Test
    fun `resting phone fires nothing`() {
        val r = Rig()
        r.rest(10_000)
        assertEquals(emptyList<HomeGesture>(), r.fired)
    }

    @Test
    fun `vigorous shake fires Shake once`() {
        val r = Rig()
        r.rest()
        // 4 Hz side-to-side shake, ±22 m/s² on X, for one second.
        r.feed(1_000, accel = { s -> Triple((22 * sin(2 * PI * 4 * s)).toFloat(), 0f, g) })
        r.rest()
        assertEquals(listOf(HomeGesture.Shake), r.fired)
    }

    @Test
    fun `two back-and-forth wrist twists fire DoubleFlick`() {
        val r = Rig()
        r.rest()
        // ~2.5 Hz rotation about Y, ±9 rad/s, for 0.8 s → +,−,+,− excursions; gravity stays face-up-ish.
        r.feed(800, gyro = { s -> Triple(0f, (9 * sin(2 * PI * 2.5 * s)).toFloat(), 0f) })
        r.rest()
        assertEquals(listOf(HomeGesture.DoubleFlick), r.fired)
    }

    @Test
    fun `single slow turn does not fire`() {
        val r = Rig()
        r.rest()
        r.feed(600, gyro = { s -> Triple(0f, (2 * sin(PI * s / 0.6)).toFloat(), 0f) })
        r.rest()
        assertEquals(emptyList<HomeGesture>(), r.fired)
    }

    /** Turns the phone face-down and back up over [ms], with the matching twist on the gyro. */
    private fun Rig.flipOnce(ms: Int = 700) {
        val dur = ms / 1000.0
        feed(
            ms,
            accel = { s -> Triple(0f, 0f, (g * cos(2 * PI * s / dur)).toFloat()) },
            gyro = { s -> Triple(0f, (9 * sin(2 * PI * s / dur)).toFloat(), 0f) },
        )
    }

    @Test
    fun `flipping over twice fires DoubleFlip and not DoubleFlick`() {
        val r = Rig()
        r.rest()
        r.flipOnce()
        r.flipOnce()
        r.rest()
        assertEquals(listOf(HomeGesture.DoubleFlip), r.fired)
    }

    @Test
    fun `laying the phone face down fires FaceDown once`() {
        val r = Rig()
        r.rest()
        // Turn over over 0.5 s, then stay face down.
        r.feed(500, accel = { s -> Triple(0f, 0f, (g * cos(PI * s / 0.5)).toFloat()) })
        r.feed(3_000, accel = { Triple(0f, 0f, -g) })
        assertEquals(listOf(HomeGesture.FaceDown), r.fired)
    }

    @Test
    fun `low sensitivity ignores a gentle shake that high sensitivity catches`() {
        val gentle: (Double) -> Triple<Float, Float, Float> = { s -> Triple((17 * sin(2 * PI * 4 * s)).toFloat(), 0f, g) }
        val firm = Rig().apply { detector.sensitivity = 0f; rest(); feed(1_000, accel = gentle); rest() }
        val light = Rig().apply { detector.sensitivity = 1f; rest(); feed(1_000, accel = gentle); rest() }
        assertEquals(emptyList<HomeGesture>(), firm.fired)
        assertEquals(listOf(HomeGesture.Shake), light.fired)
    }

    @Test
    fun `jogging footsteps do not register as a shake`() {
        val r = Rig().apply { detector.sensitivity = 1f }
        r.rest()
        // 2.8 Hz one-sided vertical impacts up to +2.4 g.
        r.feed(6_000, accel = { s ->
            val ph = (s * 2.8) % 1.0
            val pulse = if (ph < 0.18) 24 * sin(PI * ph / 0.18) else 0.0
            Triple(0f, 0f, (g + pulse).toFloat())
        })
        r.rest()
        assertEquals(emptyList<HomeGesture>(), r.fired)
    }

    @Test
    fun `turning face down long after being face up does not fire FaceDown`() {
        val r = Rig()
        r.rest()
        // Propped upright for a while (screen facing you), then laid face down.
        r.feed(5_000, accel = { Triple(0f, 9.3f, 3f) })
        r.feed(500, accel = { s -> Triple(0f, (9.3 * cos(PI * s / 0.5 / 2)).toFloat(), (3 - 12.8 * s / 0.5).toFloat()) })
        r.feed(3_000, accel = { Triple(0f, 0f, -g) })
        assertEquals(emptyList<HomeGesture>(), r.fired)
    }

    @Test
    fun `flick still works after the phone has been face down`() {
        val r = Rig()
        r.rest()
        r.feed(500, accel = { s -> Triple(0f, 0f, (g * cos(PI * s / 0.5)).toFloat()) })
        r.feed(3_000, accel = { Triple(0f, 0f, -g) })
        // Picked up and held upright.
        r.feed(3_000, accel = { Triple(0f, g, 0f) })
        r.feed(800, accel = { Triple(0f, g, 0f) }, gyro = { s -> Triple(0f, (9 * sin(2 * PI * 2.5 * s)).toFloat(), 0f) })
        r.feed(2_000, accel = { Triple(0f, g, 0f) })
        assertEquals(listOf(HomeGesture.FaceDown, HomeGesture.DoubleFlick), r.fired)
    }
}
