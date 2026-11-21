package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarScaleTest {
    @Test
    fun PAL_20_pinchAndCtrlScrollResizeWithinBounds() {
        assertEquals(1.5f, RadarScaleMath.pinch(1f, 1.5f), 0.001f)
        assertEquals(RadarScaleMath.MAX, RadarScaleMath.pinch(2f, 5f), 0f)
        assertEquals(RadarScaleMath.MIN, RadarScaleMath.pinch(1f, 0.1f), 0f)
        // Scrolling up (negative y) grows the text; down shrinks it; a notch is a few percent.
        val up = RadarScaleMath.ctrlScroll(1f, -1f)
        val down = RadarScaleMath.ctrlScroll(1f, 1f)
        assertTrue(up > 1f && up < 1.15f)
        assertTrue(down < 1f && down > 0.85f)
        assertEquals(RadarScaleMath.MAX, RadarScaleMath.ctrlScroll(2.19f, -20f), 0f)
    }
}
