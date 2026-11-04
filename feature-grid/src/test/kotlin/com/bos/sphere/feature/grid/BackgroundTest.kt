package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.BackgroundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTest {
    private val eps = 1e-3f
    private fun ends(a: Float) = BackgroundMath.gradientEndpoints(a, 200f, 100f)

    @Test fun minimumCounts() {
        assertTrue(BackgroundPresets.solids.size >= 12)
        assertTrue(BackgroundPresets.gradients.size >= 8)
        assertTrue(BackgroundPresets.textures.size >= 6)
        assertTrue(BackgroundPresets.lives.size >= 6)
    }

    @Test fun idsUniqueWithinLists() {
        for (l in listOf(BackgroundPresets.solids, BackgroundPresets.gradients, BackgroundPresets.textures, BackgroundPresets.lives)) {
            assertEquals(l.size, l.map { it.id }.toSet().size)
            assertTrue(l.all { it.label.isNotBlank() && it.id != "custom" })
        }
    }

    @Test fun lookupsRoundTrip() {
        for (p in BackgroundPresets.solids) assertNotNull(BackgroundPresets.solidColor(p.id))
        for (p in BackgroundPresets.gradients) {
            val g = BackgroundPresets.gradientStops(p.id)!!
            assertTrue(g.colors.size in 2..3)
        }
        assertNull(BackgroundPresets.solidColor("nope"))
        assertNull(BackgroundPresets.gradientStops("nope"))
        for (k in BackgroundKind.entries) {
            val list = BackgroundPresets.of(k)
            if (k == BackgroundKind.Black || k == BackgroundKind.System) {
                assertTrue(list.isEmpty()); assertEquals("", BackgroundPresets.default(k))
            } else assertEquals(list.first().id, BackgroundPresets.default(k))
        }
    }

    @Test fun gradientEndpointsCardinalAngles() {
        fun check(a: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
            val e = ends(a)
            assertEquals(x0, e[0], eps); assertEquals(y0, e[1], eps)
            assertEquals(x1, e[2], eps); assertEquals(y1, e[3], eps)
        }
        check(0f, 100f, 100f, 100f, 0f)
        check(90f, 0f, 50f, 200f, 50f)
        check(180f, 100f, 0f, 100f, 100f)
        check(270f, 200f, 50f, 0f, 50f)
    }

    @Test fun prngIsDeterministic() {
        val a = BackgroundMath.Prng(42); val b = BackgroundMath.Prng(42); val c = BackgroundMath.Prng(43)
        var differs = false
        repeat(50) {
            val x = a.nextFloat()
            assertEquals(x, b.nextFloat(), 0f)
            assertTrue(x >= 0f && x < 1f)
            if (x != c.nextFloat()) differs = true
        }
        assertTrue(differs)
        assertEquals(BackgroundMath.hash01(5, 9), BackgroundMath.hash01(5, 9), 0f)
    }

    @Test fun seedingIsDeterministicAndInRange() {
        val n = 64
        val a = Triple(FloatArray(n), FloatArray(n), FloatArray(n))
        val b = Triple(FloatArray(n), FloatArray(n), FloatArray(n))
        BackgroundMath.seedParticles(n, 7, a.first, a.second, a.third)
        BackgroundMath.seedParticles(n, 7, b.first, b.second, b.third)
        assertTrue(a.first.contentEquals(b.first) && a.second.contentEquals(b.second) && a.third.contentEquals(b.third))
        assertTrue(a.first.all { it in 0f..1f } && a.third.all { it in 0.25f..1f })
    }

    @Test fun particleCountClamps() {
        assertEquals(40, BackgroundMath.particleCount(10f, 10f, 1f, 100f, 40, 260))
        assertEquals(260, BackgroundMath.particleCount(5000f, 5000f, 1f, 10f, 40, 260))
    }

    @Test fun liveListHasEightIncludingPrototypes() {
        val ids = BackgroundPresets.lives.map { it.id }
        assertEquals(8, ids.size)
        assertTrue(ids.containsAll(listOf("aurora", "starfield", "waves", "fireflies", "nebula", "rain", "ribbon", "bokeh")))
    }

    @Test fun prototypeFormulas() {
        val n = PrototypeMath.RIBBON_LINES
        assertEquals(-(n - 1) / n.toFloat(), PrototypeMath.ribbonQ(0, n), eps)
        assertEquals(0.07f, PrototypeMath.ribbonAlpha(1f), eps)
        assertEquals(0.37f, PrototypeMath.ribbonAlpha(0f), eps)
        assertEquals(6f, PrototypeMath.bokehRadius(0f), eps)
        assertEquals(68f, PrototypeMath.bokehRadius(1f), eps)
        assertEquals(0.18f, PrototypeMath.bokehAlpha(1f), eps)
        assertEquals(1f, PrototypeMath.pushFactor(0f, 100f), eps)
        assertEquals(0.25f, PrototypeMath.pushFactor(50f, 100f), eps)
        assertEquals(0f, PrototypeMath.pushFactor(100f, 100f), eps)
        assertEquals(PrototypeMath.ribbonCentre(10f, 1f, 800f), PrototypeMath.ribbonCentre(10f, 1f, 800f), 0f)
    }

    @Test fun tintShiftsPalette() {
        val grey = BackgroundMath.livePalette("aurora", 0xFF0A0B10.toInt(), 0.7f, 0.7f)
        val red = BackgroundMath.livePalette("aurora", 0xFFFF2020.toInt(), 0.7f, 0.7f)
        assertTrue(!BackgroundMath.hasTint(0xFF0A0B10.toInt()))
        assertTrue(BackgroundMath.hasTint(0xFFFF2020.toInt()))
        assertEquals(grey.size, red.size)
        assertTrue(!grey.contentEquals(red))
        assertEquals(0f, BackgroundMath.hueOf(0xFFFF0000.toInt()), 0.5f)
        assertEquals(120f, BackgroundMath.hueOf(0xFF00FF00.toInt()), 0.5f)
    }
}
