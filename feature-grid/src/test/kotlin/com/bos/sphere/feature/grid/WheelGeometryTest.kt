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

    private fun assertPoint(expectedX: Float, expectedY: Float, p: WheelPoint) {
        assertEquals("x", expectedX, p.x, eps)
        assertEquals("y", expectedY, p.y, eps)
    }

    @Test
    fun quadrantStartsRunClockwise() {
        assertEquals(0f, WheelGeometry.quadrantStart(Corner.TopLeft))
        assertEquals(90f, WheelGeometry.quadrantStart(Corner.TopRight))
        assertEquals(180f, WheelGeometry.quadrantStart(Corner.BottomRight))
        assertEquals(270f, WheelGeometry.quadrantStart(Corner.BottomLeft))
    }

    @Test
    fun quadrantMiddlePointsIntoTheScreenForEveryAnchor() {
        val r = 300f
        val d = r / sqrt(2f)
        fun mid(c: Corner) = WheelGeometry.polarToScreen(c, w, h, r, WheelGeometry.quadrantStart(c) + 45f)
        assertPoint(d, d, mid(Corner.TopLeft))
        assertPoint(w - d, d, mid(Corner.TopRight))
        assertPoint(w - d, h - d, mid(Corner.BottomRight))
        assertPoint(d, h - d, mid(Corner.BottomLeft))
    }

    @Test
    fun quadrantLeadingEdgeRunsAlongAScreenEdge() {
        val r = 200f
        fun edge(c: Corner) = WheelGeometry.polarToScreen(c, w, h, r, WheelGeometry.quadrantStart(c))
        assertPoint(r, 0f, edge(Corner.TopLeft)) // along the top edge
        assertPoint(w, r, edge(Corner.TopRight)) // down the right edge
        assertPoint(w - r, h, edge(Corner.BottomRight)) // along the bottom edge
        assertPoint(0f, h - r, edge(Corner.BottomLeft)) // up the left edge
    }

    @Test
    fun wholeQuadrantWithinShortSideStaysOnScreen() {
        val r = minOf(w, h) - 1f
        for (c in Corner.entries) {
            for (a in 1..89) {
                val p = WheelGeometry.polarToScreen(c, w, h, r, WheelGeometry.quadrantStart(c) + a)
                assertTrue("$c @ $a: $p", p.x in 0f..w && p.y in 0f..h)
            }
        }
    }

    @Test
    fun screenToPolarRoundTripsForEveryAnchor() {
        for (c in Corner.entries) {
            for (a in listOf(5f, 30f, 45f, 77f)) {
                val angle = WheelGeometry.quadrantStart(c) + a
                val p = WheelGeometry.polarToScreen(c, w, h, 412f, angle)
                val polar = WheelGeometry.screenToPolar(c, w, h, p.x, p.y)
                assertEquals(412f, polar.radius, 0.05f)
                assertEquals(WheelGeometry.normalize360(angle), polar.angleDeg, 0.01f)
            }
        }
    }

    @Test
    fun wheelAngleInvertsScreenAngleForEveryAnchor() {
        for (c in Corner.entries) {
            for (rot in listOf(-250f, -10f, 0f, 30f)) {
                val wheel = 45f - rot + 12f // a point 12° past the quadrant middle
                val screen = WheelGeometry.normalize360(WheelGeometry.screenAngle(c, wheel, rot))
                assertEquals("$c rot=$rot", wheel, WheelGeometry.wheelAngleAt(c, screen, rot), 0.01f)
            }
        }
    }

    @Test
    fun normalizeRanges() {
        assertEquals(350f, WheelGeometry.normalize360(-10f), eps)
        assertEquals(10f, WheelGeometry.normalize360(370f), eps)
        assertEquals(-170f, WheelGeometry.normalize180(190f), eps)
        assertEquals(-180f, WheelGeometry.normalize180(180f), eps)
        assertEquals(5f, WheelGeometry.normalize180(365f), eps)
    }

    @Test
    fun layoutSegmentsAreContiguousAndProportionalToSpokes() {
        val l = WheelGeometry.layout(listOf(3, 9, 1), perSpoke = 4, spokeStep = 10f, gap = 2f)
        assertEquals(3, l.segments.size)
        assertEquals(listOf(1, 3, 1), l.segments.map { it.spokeAngles.size })
        assertEquals(0f, l.segments[0].start, eps)
        assertEquals(12f, l.segments[0].width, eps)
        assertEquals(32f, l.segments[1].width, eps)
        assertEquals(l.segments[0].end, l.segments[1].start, eps)
        assertEquals(l.segments[1].end, l.segments[2].start, eps)
        assertEquals(56f, l.span, eps)
        assertEquals(listOf(18f, 28f, 38f), l.segments[1].spokeAngles)
    }

    @Test
    fun spokeCapacityAndSubSpokes() {
        assertEquals(4, WheelGeometry.spokeCapacity(100f, 400f, 100f))
        assertEquals(1, WheelGeometry.spokeCapacity(100f, 50f, 100f))
        assertEquals(1, WheelGeometry.subSpokes(0, 4))
        assertEquals(1, WheelGeometry.subSpokes(4, 4))
        assertEquals(2, WheelGeometry.subSpokes(5, 4))
    }

    @Test
    fun rotationClampLetsFirstAndLastSpokeReachTheMiddle() {
        val l = WheelGeometry.layout(List(20) { 2 }, perSpoke = 4, spokeStep = 15f, gap = 5f)
        val b = WheelGeometry.rotationBounds(l)
        assertEquals(45f - l.lastSpoke!!, b.start, eps)
        assertEquals(45f - l.firstSpoke!!, b.endInclusive, eps)
        assertEquals(b.endInclusive, WheelGeometry.clampRotation(1e6f, l), eps)
        assertEquals(b.start, WheelGeometry.clampRotation(-1e6f, l), eps)
        // A long wheel starts just past the leading edge; a short one is centred.
        assertEquals(4f, WheelGeometry.initialRotation(l, 4f), eps)
        val short = WheelGeometry.layout(listOf(1, 1), perSpoke = 4, spokeStep = 15f, gap = 5f)
        assertEquals(45f - short.span / 2f, WheelGeometry.initialRotation(short, 4f), eps)
        assertEquals(0f..0f, WheelGeometry.rotationBounds(WheelLayout(emptyList())))
    }

    @Test
    fun rotateToCenterPutsSegmentAtQuadrantMiddle() {
        val l = WheelGeometry.layout(List(10) { 3 }, perSpoke = 4, spokeStep = 15f, gap = 5f)
        val r = WheelGeometry.rotationToCenter(l, 5)
        assertEquals(45f, l.segments[5].center + r, eps)
        assertEquals(5, WheelGeometry.focusedSegment(l, r))
        assertEquals(-1, WheelGeometry.segmentAt(l, -1f))
        assertEquals(0, WheelGeometry.segmentAt(l, 0f))
    }

    @Test
    fun cullingWindow() {
        val angles = List(40) { it * 10f }
        assertTrue(WheelGeometry.inView(50f, -10f, 0f))
        assertFalse(WheelGeometry.inView(150f, -10f, 0f))
        assertEquals(10..19, WheelGeometry.visibleRange(angles, -100f, 0f))
        assertEquals(7..22, WheelGeometry.visibleRange(angles, -100f, 30f))
        assertTrue(WheelGeometry.visibleRange(angles, -1000f, 0f).isEmpty())
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
    fun tangentTextNeverUpsideDown() {
        for (a in 0 until 360 step 5) {
            val r = WheelGeometry.tangentTextRotation(a.toFloat())
            assertTrue("$a -> $r", r in -90f..90f)
        }
        assertEquals(-45f, WheelGeometry.tangentTextRotation(45f), eps) // top-left middle
        assertEquals(45f, WheelGeometry.tangentTextRotation(135f), eps) // top-right middle
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
