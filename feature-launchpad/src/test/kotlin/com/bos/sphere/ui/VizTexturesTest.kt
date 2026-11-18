package com.bos.sphere.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.ui.viz.VizTexture
import com.bos.sphere.ui.viz.VizTextures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 28 (usageTimeline): the texture painters of the ASOC viz port, judged by the pixels they paint. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VizTexturesTest {
    private val W = 72; private val H = 72

    private class Stats(val coverage: Double, val h: Int, val v: Int, val topMean: Double, val bottomMean: Double)

    private fun paint(kind: VizTexture, density: Float = 1f, scale: Float = 1f): Stats {
        val bmp = ImageBitmap(W, H)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(W.toFloat(), H.toFloat())) {
            drawRect(VizTextures.brush(kind, Color.White, scale, density, 0f, H.toFloat()), size = Size(W.toFloat(), H.toFloat()))
        }
        val a = bmp.asAndroidBitmap()
        val alpha = Array(H) { y -> IntArray(W) { x -> (a.getPixel(x, y) ushr 24) } }
        var sum = 0.0; var h = 0; var v = 0; var top = 0.0; var bottom = 0.0
        for (y in 0 until H) for (x in 0 until W) {
            val on = alpha[y][x] > 127
            sum += alpha[y][x] / 255.0
            if (x > 0 && on != (alpha[y][x - 1] > 127)) h++
            if (y > 0 && on != (alpha[y - 1][x] > 127)) v++
            if (y < H / 4) top += alpha[y][x] / 255.0
            if (y >= H - H / 4) bottom += alpha[y][x] / 255.0
        }
        val q = (H / 4) * W.toDouble()
        return Stats(sum / (W * H), h, v, top / q, bottom / q)
    }

    @Test fun USG_20_eachTexturePaintsANonEmptyPatternAndNoTwoLookAlike() {
        val s = VizTexture.ORDER.associateWith { paint(it) }
        s.forEach { (k, st) -> assertTrue("$k paints something", st.coverage > 0.05) }
        assertEquals("solid is the full ink", 1.0, s.getValue(VizTexture.Solid).coverage, 0.001)
        VizTexture.ORDER.filter { it != VizTexture.Solid && it != VizTexture.Fade }.forEach { k ->
            val st = s.getValue(k)
            assertTrue("$k is a pattern, not a fill (coverage ${st.coverage})", st.coverage < 0.75)
            assertTrue("$k has edges both ways or is a line pattern", st.h + st.v > 40)
        }
        val keys = VizTexture.ORDER.map { k -> s.getValue(k).let { Triple(Math.round(it.coverage * 100), it.h, it.v) } }
        assertEquals("the eight textures have eight different pixel statistics: $keys", 8, keys.toSet().size)
    }

    @Test fun USG_20_fadeRunsFromFaintAtTheBottomToSolidAtTheTop() {
        val f = paint(VizTexture.Fade)
        assertTrue("top ${f.topMean} stronger than bottom ${f.bottomMean}", f.topMean > f.bottomMean + 0.4)
        assertTrue(f.bottomMean < 0.3 && f.topMean > 0.7)
    }

    @Test fun USG_20_hatchRunsDiagonallyLinesRunAcrossAndDotsAreSparse() {
        val hatch = paint(VizTexture.Hatch); val lines = paint(VizTexture.Lines); val dots = paint(VizTexture.Dots); val stipple = paint(VizTexture.Stipple)
        assertTrue("hatch has edges in both directions", hatch.h > 20 && hatch.v > 20)
        assertEquals("horizontal lines have no sideways edges", 0, lines.h); assertTrue(lines.v > 20)
        assertTrue("dots cover less than hatch", dots.coverage < hatch.coverage)
        assertTrue("stipple is fainter than dots", stipple.coverage < dots.coverage)
    }

    @Test fun USG_20_tilesAreCachedPerSizeAndScale() {
        VizTextures.clear()
        paint(VizTexture.Hatch); paint(VizTexture.Hatch); paint(VizTexture.Hatch)
        assertEquals("one tile for one size", 1, VizTextures.cached())
        paint(VizTexture.Hatch, density = 2f)
        assertEquals("another density is another tile", 2, VizTextures.cached())
        paint(VizTexture.Hatch, scale = 0.62f)
        assertEquals(3, VizTextures.cached())
        VizTexture.ORDER.forEach { paint(it) }
        assertTrue("never more tiles than textures times sizes", VizTextures.cached() <= 3 + 6)
        assertNotEquals(paint(VizTexture.Hatch, scale = 0.62f).h, paint(VizTexture.Hatch).h) // the smaller scale is a finer pattern
    }
}
