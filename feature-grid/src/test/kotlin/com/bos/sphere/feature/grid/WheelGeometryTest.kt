package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class WheelGeometryTest {

    private val w = 1080f
    private val h = 2340f
    private val eps = 0.01f

    @Test
    fun normalizeRanges() {
        assertEquals(350f, WheelGeometry.normalize360(-10f), eps)
        assertEquals(10f, WheelGeometry.normalize360(370f), eps)
        assertEquals(-170f, WheelGeometry.normalize180(190f), eps)
        assertEquals(-180f, WheelGeometry.normalize180(180f), eps)
        assertEquals(5f, WheelGeometry.normalize180(365f), eps)
    }

    @Test
    fun angularVelocityIsClockwisePositive() {
        // Point to the right of the centre moving down (y-down screen) = clockwise.
        assertTrue(WheelGeometry.angularVelocityDeg(100f, 0f, 0f, 100f) > 0f)
        assertEquals(Math.toDegrees(1.0).toFloat(), WheelGeometry.angularVelocityDeg(100f, 0f, 0f, 100f), 0.01f)
        assertTrue(WheelGeometry.angularVelocityDeg(0f, 100f, 100f, 0f) < 0f)
        assertEquals(0f, WheelGeometry.angularVelocityDeg(0f, 0f, 5f, 5f))
    }

    @Test
    fun letterBuckets() {
        assertEquals("A", wheelLetterOf("android"))
        assertEquals("E", wheelLetterOf("Éclair"))
        assertEquals("#", wheelLetterOf("2048"))
        assertEquals("#", wheelLetterOf("  "))
        assertEquals(listOf("A", "B", "Z", "#"), listOf("#", "Z", "A", "B").sortedWith(WheelLetterOrder))
    }

    @Test
    fun categoryNames() {
        assertEquals("Games", wheelCategoryName(android.content.pm.ApplicationInfo.CATEGORY_GAME))
        assertEquals("Other", wheelCategoryName(-1))
        assertEquals("Other", WheelCategoryOrder.last())
    }
}
