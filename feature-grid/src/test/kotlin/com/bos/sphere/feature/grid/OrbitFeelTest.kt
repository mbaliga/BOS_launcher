package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitFeelTest {
    @Test
    fun PAL_23_defaultsEqualTheOldConstants() {
        val d = OrbitFeelValues()
        assertEquals(2.5f, d.creepDp, 0f)
        assertEquals(3f, d.flingDecay, 0f)
        assertEquals(700f, d.flingMax, 0f)
        // The previous formula: extra*0.8 + dx/0.016*0.2*0.3, clamped to +-700.
        for ((extra, dx) in listOf(0f to 10f, 100f to -30f, 650f to 400f, -300f to -900f)) {
            val old = (extra * 0.8f + dx / 0.016f * 0.2f * 0.3f).coerceIn(-700f, 700f)
            assertEquals(old, OrbitMotion.flingSpeed(extra, dx, d), 0.01f)
        }
        // Previous decay: extra *= (1 - 3dt), zero below 1 px/s.
        assertEquals(100f * (1f - 3f * 0.016f), OrbitMotion.decay(100f, 0.016f, d), 0.001f)
        assertEquals(0f, OrbitMotion.decay(1.01f, 0.05f, d), 0f)
    }

    @Test
    fun PAL_23_tunablesChangeTheMotionAndAreBounded() {
        val slow = OrbitFeelValues(flingDecay = 8f)
        val slippery = OrbitFeelValues(flingDecay = 1f)
        assertTrue(OrbitMotion.decay(100f, 0.1f, slow) < OrbitMotion.decay(100f, 0.1f, slippery))
        assertEquals(20f, OrbitFeelValues(creepDp = 99f).coerced().creepDp, 0f)
        assertEquals(0f, OrbitFeelValues(creepDp = -1f).coerced().creepDp, 0f)
    }
}
