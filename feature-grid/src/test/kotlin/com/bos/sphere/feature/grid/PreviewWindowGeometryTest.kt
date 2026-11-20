package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-18 (wave 39): the pure placement, clamp, snap and resize rules of the floating preview window. */
class PreviewWindowGeometryTest {
    private val G = PreviewWindowGeometry
    private val aw = 411f
    private val ah = 891f

    @Test fun CFG_18_theDefaultSizeIsTheOldDockSizeAndTheAnchorIsTopEndMirroredInRtl() {
        val long = G.defaultLong(aw, ah, 822, 1644)
        val old = DockGeometry.size(aw, ah, 822, 1644)
        assertEquals(maxOf(old.widthDp, old.heightDp), long, 0.001f)
        val w = G.windowSize(long, 0.5f, false)
        val ltr = G.defaultPos(aw, w.w, false)
        assertEquals(aw - w.w - 12f, ltr.x, 0.001f)
        assertEquals(64f, ltr.y, 0f)
        val rtl = G.defaultPos(aw, w.w, true)
        assertEquals(12f, rtl.x, 0f)
    }

    @Test fun CFG_18_theWindowIsABarOnTopOfThePictureAndNeverNarrowerThanTheBar() {
        val w = G.windowSize(72f, 0.46f, false)
        assertEquals(G.BAR_MIN_W, w.w, 0f)
        assertEquals(G.BAR_DP + 72f, w.h, 0.001f)
        val wide = G.windowSize(170f, 2f, false)
        assertEquals(170f, wide.w, 0.001f)
        assertEquals(G.BAR_DP + 85f, wide.h, 0.001f)
        assertEquals(PreviewWindowGeometry.Size(G.PILL_W, G.PILL_H), G.windowSize(170f, 2f, true))
    }

    @Test fun CFG_18_resizeKeepsTheAspectAndStaysBetweenTheMinimumAndSeventyPercentOfTheShortSide() {
        assertEquals(72f, G.clampLong(10f, aw, ah), 0f)
        assertEquals(aw * 0.7f, G.clampLong(9999f, aw, ah), 0.001f)
        assertEquals(72f, G.clampLong(Float.NaN, aw, ah), 0f)
        assertEquals("a tiny window still allows the minimum", 72f, G.maxLong(60f, 60f), 0f)
        // the corner follows the finger: dragging along the diagonal scales, aspect unchanged because only the long side is stored
        val grown = G.resizedLong(100f, 0.5f, 20f, 40f, false, aw, ah)
        assertTrue(grown > 100f)
        val c = G.contentSize(grown, 0.5f)
        assertEquals(0.5f, c.w / c.h, 0.001f)
        assertTrue(G.resizedLong(100f, 0.5f, -500f, -500f, false, aw, ah) == 72f)
        assertTrue(G.resizedLong(100f, 0.5f, 5000f, 5000f, false, aw, ah) == aw * 0.7f)
    }

    @Test fun CFG_18_inRtlTheHandleIsAtTheLeftSoDraggingLeftGrowsAndTheRightEdgeStays() {
        assertTrue(G.resizedLong(100f, 2f, -30f, 0f, true, aw, ah) > 100f)
        assertTrue(G.resizedLong(100f, 2f, -30f, 0f, false, aw, ah) < 100f)
        assertEquals(50f - 30f, G.keepAnchor(50f, 100f, 130f, true), 0f)
        assertEquals(50f, G.keepAnchor(50f, 100f, 130f, false), 0f)
    }

    @Test fun CFG_18_aWindowThatFitsStaysFullyInsideTheArea() {
        val c = G.clamp(-50f, -20f, 112f, 168f, aw, ah)
        assertEquals(0f, c.x, 0f); assertEquals(0f, c.y, 0f)
        val d = G.clamp(9999f, 9999f, 112f, 168f, aw, ah)
        assertEquals(aw - 112f, d.x, 0f); assertEquals(ah - 168f, d.y, 0f)
        val ok = G.clamp(100f, 200f, 112f, 168f, aw, ah)
        assertEquals(100f, ok.x, 0f); assertEquals(200f, ok.y, 0f)
    }

    @Test fun CFG_18_aWindowBiggerThanTheAreaKeepsAtLeast48dpOfItsBarReachable() {
        // area 100 x 100, window 300 x 300: it may overhang but 48 dp stays inside on each axis
        val lo = G.clamp(-9999f, -9999f, 300f, 300f, 100f, 100f)
        assertEquals(-(300f - 48f), lo.x, 0f)
        val hi = G.clamp(9999f, 9999f, 300f, 300f, 100f, 100f)
        assertEquals(100f - 48f, hi.x, 0f)
        assertTrue("at least 48dp inside at the start side", lo.x + 300f >= 48f)
        assertTrue("at least 48dp inside at the end side", 100f - hi.x >= 48f)
        val mid = G.clamp(-9999f, 0f, 130f, 50f, 100f, 100f)
        assertEquals(-(130f - 48f), mid.x, 0f)
    }

    @Test fun CFG_18_snapPullsAnAxisToTheNearestEdgeMarginOnlyWhenCloseAndKeepsTheRestAsIs() {
        val s = G.snap(20f, 600f, 112f, 168f, aw, ah)
        assertEquals("near the start edge", 12f, s.x, 0f)
        assertEquals("far from every edge: unchanged", 600f, s.y, 0f)
        val e = G.snap(aw - 112f - 20f, ah - 168f - 30f, 112f, 168f, aw, ah)
        assertEquals(aw - 112f - 12f, e.x, 0.001f)
        assertEquals(ah - 168f - 12f, e.y, 0.001f)
        val mid = G.snap(150f, 300f, 112f, 168f, aw, ah)
        assertEquals(150f, mid.x, 0f); assertEquals(300f, mid.y, 0f)
        // the default top (64) is more than 32 dp from the top margin: a window left at the default does not jump
        assertEquals(64f, G.snap(150f, 64f, 112f, 168f, aw, ah).y, 0f)
    }

    @Test fun CFG_18_theCodecRoundTripsAndReadsGarbageAsTheDefault() {
        val saved = PreviewWindowSaved(12.5f, 80f, 120f)
        assertEquals(saved, PreviewWindowCodec.decode(PreviewWindowCodec.encode(saved)))
        val partial = PreviewWindowCodec.decode(PreviewWindowCodec.encode(PreviewWindowSaved(Float.NaN, Float.NaN, 100f)))
        assertTrue(partial.x.isNaN() && partial.y.isNaN()); assertEquals(100f, partial.long, 0f)
        for (junk in listOf(null, "", "1,2", "a,b,c", "1,2,3,4", "Infinity,NaN,-")) assertTrue("'$junk'", PreviewWindowCodec.decode(junk).isDefault)
        assertTrue(PreviewWindowCodec.decode("garbage").isDefault)
        assertTrue(PreviewWindowCodec.decode("1,2").isDefault)
        assertTrue("a non-finite number is dropped, the rest is kept", PreviewWindowCodec.decode("Infinity,2,3").let { it.x.isNaN() && it.y == 2f })
    }
}
