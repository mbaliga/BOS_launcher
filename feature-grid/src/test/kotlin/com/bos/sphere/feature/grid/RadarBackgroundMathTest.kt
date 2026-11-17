package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.BgItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** PAL-24: the pure maths of the Radar background: dot grid, scope, glow-line pulses, comic lines. */
class RadarBackgroundMathTest {
    // ----------------------------------------------------------------- dot grid (owner's SVG)

    @Test fun PAL_24_dotGridIsBrightestInTheMiddleAndInvisibleAtTheEdges() {
        val l = DotGridMath.layout(956f, 440f, 1f)
        assertFalse(l.portrait)
        var best = 0; for (i in 0 until l.count) if (l.env[i] > l.env[best]) best = i
        assertTrue("centre dot envelope ${l.env[best]}", l.env[best] > 0.9f)
        assertTrue(hypot(l.xy[best * 2] - 478f, l.xy[best * 2 + 1] - 220f) < 30f)
        // No dot at the far left/right columns, and none in the top and bottom rows (outside the owner's y 31..410 band).
        for (i in 0 until l.count) {
            assertTrue(l.xy[i * 2] > 5f && l.xy[i * 2] < 956f - 5f)
            assertTrue(l.xy[i * 2 + 1] > 31f && l.xy[i * 2 + 1] < 410f)
            assertTrue(l.env[i] >= 0.02f)
        }
        // The outermost dots that are kept are nearly invisible (the owner's linear falloff reaches zero at the ellipse edge).
        val outermost = (0 until l.count).minOf { l.env[it] }
        assertTrue("outermost envelope $outermost", outermost < 0.12f)
    }

    @Test fun PAL_24_dotGridPitchIs24dpAndScalesWithDensity() {
        val a = DotGridMath.layout(960f, 440f, 1f)
        val b = DotGridMath.layout(1920f, 880f, 2f)
        assertEquals(a.count, b.count)
        // Two neighbours in a row sit one pitch apart.
        val row = (0 until a.count - 1).first { a.xy[it * 2 + 1] == a.xy[(it + 1) * 2 + 1] }
        assertEquals(24f, a.xy[(row + 1) * 2] - a.xy[row * 2], 0.01f)
        assertEquals(48f, b.xy[(row + 1) * 2] - b.xy[row * 2], 0.01f)
    }

    @Test fun PAL_24_dotGridPortraitSwapsColumnsAndRowsAndTheEllipseFollowsTheWindow() {
        val land = DotGridMath.layout(956f, 440f, 1f)
        val port = DotGridMath.layout(440f, 956f, 1f)
        assertTrue(port.portrait)
        assertEquals(land.count, port.count)
        val maxX = (0 until port.count).maxOf { port.xy[it * 2] }; val minX = (0 until port.count).minOf { port.xy[it * 2] }
        val maxY = (0 until port.count).maxOf { port.xy[it * 2 + 1] }; val minY = (0 until port.count).minOf { port.xy[it * 2 + 1] }
        // The long axis is vertical now: the ellipse reaches far along y and stays narrow in x.
        assertTrue(maxY - minY > 2 * (maxX - minX))
    }

    @Test fun PAL_24_breathStaysInRangeHasAPeriodAndStillIsMidBreath() {
        var lo = 1f; var hi = 0f
        for (t in 0L until 5500L step 50L) { val b = DotGridMath.breath(t, 0.3f, 0.1f); lo = minOf(lo, b); hi = maxOf(hi, b); assertTrue(b in 0f..1f) }
        assertTrue(lo < 0.05f && hi > 0.95f)
        assertEquals(DotGridMath.breath(1234, 0.3f, 0.1f), DotGridMath.breath(1234 + DotGridMath.PERIOD_MS, 0.3f, 0.1f), 1e-4f)
        assertTrue(DotGridMath.PERIOD_MS in 4000L..7000L)
        // Different distances breathe out of step: a wave, not all at once.
        assertNotEquals(DotGridMath.breath(1000, 0.1f, 0f), DotGridMath.breath(1000, 0.8f, 0f), 0.05f)
    }

    @Test fun PAL_24_bucketsSortEveryVisibleDotAndStillGridIsStatic() {
        val l = DotGridMath.layout(411f, 891f, 1f)
        val buckets = Array(DotGridMath.BUCKETS) { FloatArray(l.count * 2) }; val counts = IntArray(DotGridMath.BUCKETS)
        DotGridMath.fillBuckets(l, 1000, false, buckets, counts)
        assertTrue(counts.sum() > l.count / 2)
        val still1 = counts.clone().also { DotGridMath.fillBuckets(l, 0, true, buckets, it) }
        val still2 = counts.clone().also { DotGridMath.fillBuckets(l, 4321, true, buckets, it) }
        assertTrue(still1.contentEquals(still2))
        assertEquals(2f, DotGridMath.bucketRadiusDp(0), 0f)
        assertEquals(3f, DotGridMath.bucketRadiusDp(DotGridMath.BUCKETS - 1), 0f)
    }

    // ----------------------------------------------------------------- scope

    @Test fun PAL_24_sweepTurnsOnceEverySixSecondsAndPingsEveryFive() {
        assertEquals(0f, ScopeMath.sweepAngle(0), 0f)
        assertEquals(180f, ScopeMath.sweepAngle(3000), 0.01f)
        assertEquals(0f, ScopeMath.sweepAngle(6000), 0.01f)
        assertEquals(0f, ScopeMath.pingProgress(0), 0f)
        assertTrue(ScopeMath.pingProgress(1300) in 0.4f..0.6f)
        assertEquals(-1f, ScopeMath.pingProgress(3000), 0f)
        assertEquals(0f, ScopeMath.pingProgress(5000), 0f)
        assertTrue(ScopeMath.pingRadius(0.5f) > 0.5f && ScopeMath.pingAlpha(0.9f) < ScopeMath.pingAlpha(0.1f))
    }

    // ----------------------------------------------------------------- glow lines

    @Test fun PAL_24_eightCentreLinesRunFromTheClockOutToTheEdges() {
        assertEquals(8, GlowLinesMath.CENTRE_LINES.size)
        GlowLinesMath.CENTRE_LINES.forEachIndexed { i, p ->
            val sx = p.nx.first(); val sy = p.ny.first(); val ex = p.nx.last(); val ey = p.ny.last()
            assertTrue("line $i starts near the clock ($sx,$sy)", abs(sx - 0.5f) < 0.1f && abs(sy - 0.5f) < 0.25f)
            assertTrue("line $i ends toward an edge ($ex,$ey)", abs(ex - 0.5f) > 0.3f)
            assertEquals(0f, p.cum.first(), 0f); assertEquals(1f, p.cum.last(), 1e-4f)
            for (k in 1 until p.size) assertTrue(p.cum[k] >= p.cum[k - 1])
        }
    }

    @Test fun PAL_24_leftShapesMirrorRightShapes() {
        val c = GlowLinesMath.CENTRE_LINES
        // tl/tr, mr1/ml1, mr2/ml2, bl/br are mirror images across x = 0.5.
        for ((a, b) in listOf(0 to 1, 2 to 4, 3 to 5, 6 to 7)) {
            val pa = c[a]; val pb = c[b]
            assertEquals("line $a/$b end x", 1f, pa.nx.last() + pb.nx.last(), 0.03f)
            assertEquals("line $a/$b end y", pa.ny.last(), pb.ny.last(), 0.03f)
        }
    }

    @Test fun PAL_24_noPulseWithoutSignalAndEachLevelAddsLinesSpeedAndBrightness() {
        assertEquals(0, GlowLinesMath.pulseCount(0))
        assertEquals(0, GlowLinesMath.litLines(0))
        var prevCount = 0; var prevBright = 0f; var prevSpeed = 0f
        for (level in 1..4) {
            val p = GlowLinesMath.params(level)!!
            val count = GlowLinesMath.pulseCount(level)
            assertTrue("pulses level $level", count > prevCount)
            assertTrue("brightness level $level", p.brightness > prevBright)
            val speed = 1f / p.periodMs
            assertTrue("speed level $level", speed >= prevSpeed)
            prevCount = count; prevBright = p.brightness; prevSpeed = speed
        }
        assertEquals("weak: one or two pulses", true, GlowLinesMath.pulseCount(1) in 1..2)
        assertEquals(8, GlowLinesMath.litLines(4))
        for (line in 0 until 8) assertTrue(GlowLinesMath.isLit(4, line))
        assertEquals(2, (0 until 8).count { GlowLinesMath.isLit(1, it) })
    }

    @Test fun PAL_24_fullStrengthPulsesAreEvenlySpacedOnEveryLine() {
        val out = FloatArray(2)
        for (line in 0 until 8) {
            GlowLinesMath.pulse(4, line, 0, 2222, false, out); val a = out[0]
            GlowLinesMath.pulse(4, line, 1, 2222, false, out); val b = out[0]
            val gap = abs(a - b)
            assertEquals(0.5f, if (gap > 0.5f) 1f - gap else gap, 0.01f)
        }
    }

    @Test fun PAL_24_pulsesTravelInTowardTheClockAndNeverFasterThanCalmComputingAllows() {
        val out = FloatArray(2)
        GlowLinesMath.pulse(3, 0, 0, 0, false, out); val p0 = out[0]
        GlowLinesMath.pulse(3, 0, 0, 500, false, out); val p1 = out[0]
        assertTrue("moves toward 0 (the clock end)", p1 < p0 || (p0 < 0.1f))
        for (level in 1..4) {
            val p = GlowLinesMath.params(level)!!
            // A point on a line meets at most one pulse per (period / pulsesPerLine): never more than 1 per 3 s, far below 3 Hz.
            assertTrue("level $level pulse every ${p.periodMs / p.pulses} ms", p.periodMs / p.pulses >= 3000)
        }
    }

    @Test fun PAL_24_stillPulsesAreFrozenEvenlySpacedAndStillEncodeTheLevel() {
        val out = FloatArray(2)
        for (level in 1..4) {
            val before = (0 until 8).map { l -> if (GlowLinesMath.isLit(level, l)) { GlowLinesMath.pulse(level, l, 0, 0, true, out); out[0] } else -1f }
            val after = (0 until 8).map { l -> if (GlowLinesMath.isLit(level, l)) { GlowLinesMath.pulse(level, l, 0, 99999, true, out); out[0] } else -1f }
            assertEquals(before, after)
            assertEquals(GlowLinesMath.litLines(level), before.count { it >= 0f })
        }
    }

    @Test fun PAL_24_calmEllipseSilencesTheMiddleAndNotTheOuterRuns() {
        assertEquals(0f, GlowLinesMath.calm(0.5f, 0.5f), 0f)
        assertEquals(1f, GlowLinesMath.calm(0.1f, 0.2f), 0f)
        assertTrue(GlowLinesMath.calm(0.5f + 0.1f, 0.5f) in 0f..1f)
    }

    @Test fun PAL_24_sliceReturnsTheRequestedPartOfALineWithinTheWindow() {
        val line = GlowLinesMath.CENTRE_LINES[0]
        val buf = FloatArray(line.size * 4)
        assertEquals(0, GlowLinesMath.slice(line, 0.5f, 0.5f, 100f, 100f, false, buf))
        val n = GlowLinesMath.slice(line, 0.2f, 0.4f, 1000f, 500f, false, buf)
        assertTrue(n > 0 && n % 4 == 0)
        for (i in 0 until n step 2) { assertTrue(buf[i] in -1f..1001f); assertTrue(buf[i + 1] in -1f..501f) }
        val full = GlowLinesMath.slice(line, -1f, 2f, 1000f, 500f, false, buf)
        assertEquals((line.size - 1) * 4, full)
        // Portrait transposes: the same line spans the window's long axis vertically.
        val port = GlowLinesMath.slice(line, 0f, 1f, 500f, 1000f, true, buf)
        assertEquals(full, port)
        assertTrue("first segment starts near the clock (window centre)", abs(buf[1] - 500f) < 100f && abs(buf[0] - 250f) < 100f)
    }

    // ----------------------------------------------------------------- comic lines

    @Test fun PAL_24_comicLinesAreDeterministicBySeedAndKnowNothingOfLevel() {
        val a = ComicLines(7); val b = ComicLines(7); val c = ComicLines(8)
        a.fill(400f, 800f, 1234, false); b.fill(400f, 800f, 1234, false); c.fill(400f, 800f, 1234, false)
        for (k in 0 until ComicLines.CLASSES) {
            assertEquals(a.counts[k], b.counts[k])
            assertTrue(a.buffers[k].contentEquals(b.buffers[k]))
        }
        assertFalse((0 until ComicLines.CLASSES).all { a.buffers[it].contentEquals(c.buffers[it]) })
        assertEquals(72, a.counts.sum() / 4)
    }

    @Test fun PAL_24_comicLinesRedrawAtEightFramesPerSecondAndOnlyOnTheStep() {
        assertEquals(8, ComicLines.FPS)
        assertEquals(0, ComicLines.frameOf(124)); assertEquals(1, ComicLines.frameOf(125)); assertEquals(8, ComicLines.frameOf(1000))
        val l = ComicLines(); val l2 = ComicLines()
        l.fill(400f, 800f, 10, false); l2.fill(400f, 800f, 120, false)
        assertTrue("same frame, same lines", l.buffers[0].contentEquals(l2.buffers[0]))
        l2.fill(400f, 800f, 130, false)
        assertFalse("next frame jitters", l.buffers[0].contentEquals(l2.buffers[0]))
        // Still: one static composition whatever the time.
        l.fill(400f, 800f, 0, true); l2.fill(400f, 800f, 99999, true)
        assertTrue(l.buffers[1].contentEquals(l2.buffers[1]))
    }

    @Test fun PAL_24_comicLinesStayInThePeripheryAndStartAtTheEdge() {
        val w = 400f; val h = 800f
        val l = ComicLines(); l.fill(w, h, 5000, false)
        val cx = w / 2; val cy = h / 2
        for (k in 0 until ComicLines.CLASSES) for (i in 0 until l.counts[k] step 4) {
            val b = l.buffers[k]
            val outer = hypot(b[i] - cx, b[i + 1] - cy); val inner = hypot(b[i + 2] - cx, b[i + 3] - cy)
            assertTrue(inner < outer)
            assertTrue("inner end ${inner / outer} of the way", inner / outer >= ComicLines.MIN_INNER - 0.05f && inner / outer <= ComicLines.MAX_INNER + 0.05f)
            assertTrue(b[i] in -w * 0.03f..w * 1.03f && b[i + 1] in -h * 0.03f..h * 1.03f)
        }
    }

    // ----------------------------------------------------------------- fade

    @Test fun PAL_24_fadeTakesAbout400msAndIsInstantWhenStill() {
        val f = Fade(1f)
        f.step(0f, 1000, 400, false)           // first call after init: starts from `last`
        assertEquals(0f, f.value, 0f)          // first step snaps (no earlier time)
        val g = Fade(0f); g.step(1f, 0, 400, true); assertEquals(1f, g.value, 0f)
        val h = Fade(1f); h.step(1f, 0, 400, false); h.step(0f, 200, 400, false)
        assertEquals(0.5f, h.value, 0.01f); h.step(0f, 400, 400, false); assertEquals(0f, h.value, 0.01f)
    }

    // ----------------------------------------------------------------- registry

    @Test fun HOM_16_signalViewRegistryHasOneViewPerItemAndNoneHasNoView() {
        assertEquals(BgItem.entries.filter { it != BgItem.None }.map { it.id }, SignalViews.views.map { it.id })
        BgItem.entries.filter { it != BgItem.None }.forEach { assertEquals(it.id, SignalViews.forItem(it)!!.id) }
        assertTrue(SignalViews.forItem(BgItem.None) == null)
    }

    @Test fun PAL_24_aSecondViewPlugsInThroughTheSameInterfaceWithoutTouchingTheHost() {
        var prepared = 0
        val dummy = object : SignalView {
            override val id = "dummy"; override val label = "Dummy"
            override fun prepare(geo: RadarGeometry): SignalPainter { prepared++; return object : SignalPainter { override fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, signal: com.bos.sphere.core.signals.SignalState, p: SignalDrawParams) {} } }
        }
        val registry = SignalViews.views + dummy
        assertEquals(BgItem.Spire.id, SignalViews.forItem(BgItem.Spire, registry)!!.id)
        assertTrue("an item missing from a registry has no view", SignalViews.forItem(BgItem.Beam, listOf(dummy)) == null)
        dummy.prepare(RadarGeometry(100f, 200f, 1f))
        assertEquals(1, prepared)
    }
}
