package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {
    @Test fun parsesHex() {
        assertEquals(0xFF8E7BFFL, ColorMath.parseHex("#8E7BFF"))
        assertEquals(0xFF8E7BFFL, ColorMath.parseHex("8e7bff"))
        assertEquals(0xFFFFAA00L, ColorMath.parseHex("#fa0"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("zzzzzz"))
        assertNull(ColorMath.parseHex(""))
    }

    @Test fun formatsHex() {
        assertEquals("#8E7BFF", ColorMath.formatHex(0xFF8E7BFFL))
        assertEquals("#000000", ColorMath.formatHex(0xFF000000L))
    }

    @Test fun primariesToHsv() {
        val red = ColorMath.rgbToHsv(255, 0, 0)
        assertEquals(0f, red[0], 0.01f); assertEquals(1f, red[1], 0.01f); assertEquals(1f, red[2], 0.01f)
        val green = ColorMath.rgbToHsv(0, 255, 0)
        assertEquals(120f, green[0], 0.01f)
        val blue = ColorMath.rgbToHsv(0, 0, 255)
        assertEquals(240f, blue[0], 0.01f)
        val grey = ColorMath.rgbToHsv(128, 128, 128)
        assertEquals(0f, grey[1], 0.001f)
    }

    @Test fun roundTrips() {
        for (argb in listOf(0xFF8E7BFFL, 0xFF123456L, 0xFFFFFFFFL, 0xFF000000L, 0xFFAB0000L, 0xFF00CC77L)) {
            val rgb = ColorMath.argbToRgb(argb)
            val hsv = ColorMath.rgbToHsv(rgb[0], rgb[1], rgb[2])
            val back = ColorMath.hsvToArgb(hsv[0], hsv[1], hsv[2])
            val b = ColorMath.argbToRgb(back)
            for (i in 0..2) assertEquals("$argb ch$i", rgb[i].toDouble(), b[i].toDouble(), 1.0)
            assertEquals(0xFFL, back ushr 24)
        }
    }

    @Test fun hueWraps() {
        assertEquals(ColorMath.hsvToArgb(0f, 1f, 1f), ColorMath.hsvToArgb(360f, 1f, 1f))
        assertEquals(true, abs(ColorMath.hsvToArgb(-60f, 1f, 1f) - ColorMath.hsvToArgb(300f, 1f, 1f)) == 0L)
    }
}
