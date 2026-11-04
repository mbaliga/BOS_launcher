package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class BubbleMathTest {
    private val pitch = 76f

    @Test fun ringSizes() {
        assertEquals(0, BubbleMath.ringOf(0))
        for (k in 1..8) {
            val size = BubbleMath.ringStart(k + 1) - BubbleMath.ringStart(k)
            assertEquals(6 * k, size)
            assertEquals(k, BubbleMath.ringOf(BubbleMath.ringStart(k)))
            assertEquals(k, BubbleMath.ringOf(BubbleMath.ringStart(k + 1) - 1))
        }
    }

    @Test fun spiralDistinctAndGapFree() {
        for (n in 1..200) {
            val cells = (0 until n).map { BubbleMath.hexCell(it) }
            assertEquals(n, cells.toSet().size)
            // every cell sits in the ring it was assigned to
            cells.forEachIndexed { i, c ->
                assertEquals(BubbleMath.ringOf(i), BubbleMath.hexDistance(c, BubbleMath.Hex(0, 0)))
            }
        }
        // complete rings cover the whole hexagonal disc
        for (k in 0..6) {
            val n = BubbleMath.ringStart(k + 1)
            val set = (0 until n).map { BubbleMath.hexCell(it) }.toSet()
            for (q in -k..k) for (r in -k..k) {
                val h = BubbleMath.Hex(q, r)
                if (BubbleMath.hexDistance(h, BubbleMath.Hex(0, 0)) <= k) assertTrue(h in set)
            }
        }
    }

    @Test fun neighbourDistancesEqualPitch() {
        val n = 200
        val xy = BubbleMath.positions(n, pitch)
        val cells = (0 until n).map { BubbleMath.hexCell(it) }
        var pairs = 0
        for (a in 0 until n) for (b in a + 1 until n) {
            val d = hypot(xy[2 * a] - xy[2 * b], xy[2 * a + 1] - xy[2 * b + 1])
            if (BubbleMath.hexDistance(cells[a], cells[b]) == 1) {
                assertEquals(pitch, d, 0.01f); pairs++
            } else {
                assertTrue(d > pitch * 1.5f)
            }
        }
        assertTrue(pairs > 400)
        // centre bubble at origin
        assertEquals(0f, xy[0], 0f); assertEquals(0f, xy[1], 0f)
    }

    @Test fun fisheyeMonotonicAndBounded() {
        val r = 170f; val p = 50f
        var ps = 2f; var pa = 2f
        var d = 0f
        while (d < 2.5f * r) {
            val s = BubbleMath.scale(d, r, p); val a = BubbleMath.alpha(d, r, p)
            assertTrue(s in 0f..1f); assertTrue(a in 0f..1f)
            assertTrue(s <= ps + 1e-6f); assertTrue(a <= pa + 1e-6f)
            ps = s; pa = a; d += 1f
        }
        assertEquals(1f, BubbleMath.scale(0f, r, p), 0f)
        assertEquals(1f, BubbleMath.scale(p, r, p), 0f)
        assertEquals(BubbleMath.MIN_SCALE, BubbleMath.scale(r, r, p), 1e-4f)
        assertEquals(BubbleMath.MIN_ALPHA, BubbleMath.alpha(r, r, p), 1e-4f)
        val cull = BubbleMath.cullDistance(r, p)
        assertEquals(BubbleMath.CULL_SCALE, BubbleMath.scale(cull, r, p), 1e-3f)
        assertEquals(0f, BubbleMath.scale(100 * r, r, p), 0f)
    }

    @Test fun nearestFindsClosest() {
        val xy = BubbleMath.positions(60, pitch)
        assertEquals(-1, BubbleMath.nearest(FloatArray(0), 0f, 0f))
        for (t in 0 until 60) {
            assertEquals(t, BubbleMath.nearest(xy, xy[2 * t] + 3f, xy[2 * t + 1] - 4f))
        }
        // brute-force comparison at arbitrary points
        for (px in -300..300 step 37) for (py in -300..300 step 41) {
            val n = BubbleMath.nearest(xy, px.toFloat(), py.toFloat())
            val dn = hypot(xy[2 * n] - px, xy[2 * n + 1] - py)
            for (i in 0 until 60) assertTrue(dn <= hypot(xy[2 * i] - px, xy[2 * i + 1] - py) + 1e-3f)
        }
    }

    @Test fun boundsContainAllCenters() {
        assertTrue(BubbleMath.bounds(FloatArray(0)).all { it == 0f })
        for (n in listOf(1, 2, 7, 50, 500)) {
            val xy = BubbleMath.positions(n, pitch)
            val b = BubbleMath.bounds(xy)
            for (i in 0 until n) {
                assertTrue(xy[2 * i] in b[0]..b[2]); assertTrue(xy[2 * i + 1] in b[1]..b[3])
            }
        }
    }

    @Test fun visibleOnlyNearWindow() {
        val xy = BubbleMath.positions(500, pitch)
        val v = BubbleMath.visible(xy, 0f, 0f, 300f)
        assertTrue(v.isNotEmpty() && v.size < 100)
        assertTrue(v.all { hypot(xy[2 * it], xy[2 * it + 1]) <= 300f })
        assertEquals(500, BubbleMath.visible(xy, 0f, 0f, 1e6f).size)
    }

    @Test fun resistanceAndRubberBand() {
        assertEquals(1f, BubbleMath.dragResistance(5f, -3f, 0f, 10f, 100f), 0f)
        assertEquals(1f, BubbleMath.dragResistance(-20f, 3f, 0f, 10f, 100f), 0f) // moving back
        assertTrue(BubbleMath.dragResistance(-50f, -3f, 0f, 10f, 100f) < 0.3f)
        assertTrue(BubbleMath.dragResistance(1000f, 3f, 0f, 10f, 100f) < 0.05f)
        assertEquals(7f, BubbleMath.rubberBand(7f, 0f, 10f, 50f), 0f)
        val rb = BubbleMath.rubberBand(1000f, 0f, 10f, 50f)
        assertTrue(rb > 10f && rb <= 60f)
        assertTrue(abs(BubbleMath.rubberBand(-1000f, 0f, 10f, 50f) + 50f) < 1f)
    }

    @Test fun hitTestPicksBubbleUnderTouch() {
        val xy = BubbleMath.positions(20, pitch)
        val cx = 540f; val cy = 900f; val size = 68f * 3
        val r = 400f; val pl = 60f
        // pan centred on bubble 3: bubble 3 at screen centre
        val px = xy[6]; val py = xy[7]
        assertEquals(3, BubbleMath.hitTest(xy, px, py, cx, cy, cx + 5f, cy - 5f, size, r, pl))
        assertEquals(-1, BubbleMath.hitTest(xy, px, py, cx, cy, cx, cy - 2000f, size, r, pl))
    }
}
