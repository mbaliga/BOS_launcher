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
        assertEquals(24, BackgroundPresets.solids.size)
        assertEquals(20, BackgroundPresets.gradients.size)
        assertEquals(15, BackgroundPresets.textures.size)
        assertEquals(13, BackgroundPresets.lives.size)
        assertEquals(listOf("horizon"), BackgroundPresets.pictures.map { it.id })
    }

    @Test fun idsUniqueWithinLists() {
        for (l in listOf(BackgroundPresets.solids, BackgroundPresets.gradients, BackgroundPresets.textures, BackgroundPresets.lives, BackgroundPresets.pictures)) {
            assertEquals(l.size, l.map { it.id }.toSet().size)
            assertTrue(l.all { it.label.isNotBlank() && it.id != "custom" })
        }
    }

    @Test fun lookupsRoundTrip() {
        for (p in BackgroundPresets.solids) assertNotNull(BackgroundPresets.solidColor(p.id))
        for (p in BackgroundPresets.gradients) {
            val g = BackgroundPresets.gradientStops(p.id)!!
            assertTrue(g.colors.size in 2..4)
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

    @Test fun liveListKeepsOldIdsAndAddsNew() {
        val ids = BackgroundPresets.lives.map { it.id }
        assertEquals(13, ids.size)
        assertEquals(listOf("aurora", "starfield", "waves", "fireflies", "nebula", "rain", "ribbon", "bokeh"), ids.take(8))
        assertEquals(listOf("lava", "snow", "matrix", "orbits", "plasma"), ids.drop(8))
        assertEquals(ids, BackgroundPresets.allIds(BackgroundKind.Live))
        assertTrue(BackgroundPresets.allIds(BackgroundKind.Black).isEmpty())
    }

    @Test fun existingIdsStayStable() {
        assertEquals(listOf("ink", "graphite", "midnight", "navy", "ocean", "forest", "moss", "wine", "plum", "rust", "slate", "steel", "sand", "cloud"),
            BackgroundPresets.allIds(BackgroundKind.Solid).take(14))
        assertEquals(listOf("dusk", "ocean", "ember", "aurora", "mint", "rose", "steel", "nebula"),
            BackgroundPresets.allIds(BackgroundKind.Gradient).take(8))
        assertEquals(listOf("grain", "grid", "dots", "linen", "carbon", "paper", "hex"),
            BackgroundPresets.allIds(BackgroundKind.Texture).take(7))
    }

    @Test fun radialGradientsAndDefaults() {
        assertTrue(!GradientSpec(listOf(1L, 2L), 90f).radial)
        val radial = BackgroundPresets.gradients.map { BackgroundPresets.gradientStops(it.id)!! }.filter { it.radial }
        assertTrue(radial.size >= 4)
        assertEquals(125f, BackgroundMath.radialRadius(0.5f, 0.5f, 200f, 150f), eps)
        assertEquals(Math.hypot(100.0, 150.0).toFloat(), BackgroundMath.radialRadius(0.5f, 1f, 200f, 150f), eps)
    }

    @Test fun newScenesHaveHuesAndPalettes() {
        for (id in listOf("lava", "snow", "matrix", "orbits", "plasma")) {
            assertTrue(BackgroundMath.sceneHues(id).size >= 2)
            assertEquals(BackgroundMath.sceneHues(id).size, BackgroundMath.livePalette(id, 0xFF2040FF.toInt(), 0.7f, 0.7f).size)
        }
        assertEquals(10, BackgroundMath.matrixColumns(100f, 3f))
        assertEquals(56, BackgroundMath.matrixColumns(100000f, 1f))
        assertEquals(30, BackgroundMath.matrixColumns(1260f, 3f))
    }

    @Test fun pictureMath() {
        assertEquals(1, PictureMath.sampleSize(1000, 1000, 1000, 1000))
        assertEquals(4, PictureMath.sampleSize(4000, 3000, 1000, 700))
        assertEquals(2, PictureMath.sampleSize(4000, 3000, 1000, 1100))
        assertEquals(1, PictureMath.sampleSize(0, 3000, 1000, 700))
        assertEquals(0f, PictureMath.clampDim(-1f), 0f)
        assertEquals(1f, PictureMath.clampDim(3f), 0f)
        assertEquals(0f, PictureMath.clampDim(Float.NaN), 0f)
        assertEquals(90, PictureMath.exifRotation(6))
        assertEquals(0, PictureMath.exifRotation(1))
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
