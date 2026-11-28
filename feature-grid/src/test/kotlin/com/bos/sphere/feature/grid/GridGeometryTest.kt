package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryTest {
    @Test
    fun `compact cells are squares sized by the tighter axis`() {
        val g = GridGeometry.compute(300f, 900f, columns = 3, rows = 6, verticalBias = 0f, spread = 0f)
        assertEquals(100f, g.base, 0.01f)
        assertEquals(100f, g.pitchX, 0.01f)
        assertEquals(100f, g.pitchY, 0.01f)
    }

    @Test
    fun `spreading grows only the axis with free space and keeps the icon reference`() {
        val g = GridGeometry.compute(300f, 900f, columns = 3, rows = 4, verticalBias = 0f, spread = 1f)
        assertEquals(100f, g.base, 0.01f)
        assertEquals(100f, g.pitchX, 0.01f) // width was already the limit
        assertEquals(225f, g.pitchY, 0.01f) // 900 / 4 rows
        // The block fills the area exactly.
        assertEquals(900f, g.pitchY * 4, 0.01f)
    }

    @Test
    fun `hit testing follows non-square cells`() {
        val g = GridGeometry.compute(300f, 900f, 3, 4, 0f, 1f)
        assertEquals(0, g.cellAt(10f, 10f))
        assertEquals(3, g.cellAt(10f, 230f))
        assertEquals(11, g.cellAt(299f, 899f))
        assertEquals(-1, g.cellAt(10f, 901f))
        assertTrue(g.centreY(3) > g.centreY(0))
    }
}
