package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthFormatTest {
    @Test
    fun `metric and imperial elevation and distance`() {
        assertEquals("34 m", HealthFormat.elevation(34.2, imperial = false))
        assertEquals("112 ft", HealthFormat.elevation(34.2, imperial = true))
        assertEquals("3.2 km", HealthFormat.distance(3200.0, imperial = false))
        assertEquals("2.0 mi", HealthFormat.distance(3218.7, imperial = true))
    }

    @Test
    fun `missing data shows a dash`() {
        assertEquals("—", HealthFormat.steps(null))
        assertEquals("—", HealthFormat.floors(null))
        assertEquals("—", HealthFormat.kcal(null))
        assertEquals("—", HealthFormat.distance(null, false))
    }

    @Test
    fun `floors and kcal round`() {
        assertEquals("12", HealthFormat.floors(11.6))
        assertEquals("245 kcal", HealthFormat.kcal(244.6))
        assertTrue(HealthFormat.usesImperial("us") && !HealthFormat.usesImperial("IN"))
    }
}
