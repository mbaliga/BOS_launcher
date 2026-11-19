package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** CFG-15, INP-19 (wave 30, puckSections): pure geometry of the four sections around the hub. */
class PuckSectionsGeometryTest {
    private val ring = PuckSectionsGeometry.ring(hubDp = 122f, chipDp = 108f) // a 360 dp wide phone

    @Test fun CFG_15_thereAreFourWedgesFillingTheQuadrantInOrderWithGapsBetween() {
        for (c in Corner.entries) {
            val q = PuckSectionsGeometry.quadrantStart(c)
            val w = PuckSectionsGeometry.wedges(c, ring).sortedBy { it.startDeg }
            assertEquals(4, w.size)
            assertEquals("$c starts at the quadrant edge", q, w.first().startDeg, 0.001f)
            assertEquals("$c ends at the other edge", q + 90f, w.last().endDeg, 0.001f)
            for (i in 0 until 3) assertEquals("gap between wedges ${i} and ${i + 1}", PuckSectionsGeometry.gapDeg(ring), w[i + 1].startDeg - w[i].endDeg, 0.001f)
            w.forEach { assertEquals(w[0].sweep, it.sweep, 0.5f) }
        }
    }

    @Test fun CFG_15_theGapIsOneDpWideAtTheMidRadius() {
        val w = PuckSectionsGeometry.wedges(Corner.BottomLeft, ring).sortedBy { it.startDeg }
        val gapDp = Math.toRadians((w[1].startDeg - w[0].endDeg).toDouble()) * ring.mid
        assertEquals(1.0, gapDp, 0.01)
    }

    @Test fun CFG_15_theRightHandCornerIsTheExactMirrorOfTheLeftHandOne() {
        val left = PuckSectionsGeometry.wedges(Corner.BottomLeft, ring)
        val right = PuckSectionsGeometry.wedges(Corner.BottomRight, ring)
        for (i in 0 until 4) {
            val l = PuckSectionsGeometry.centroid(left[i], ring); val r = PuckSectionsGeometry.centroid(right[i], ring)
            assertEquals("slot $i mirrored in x", -l.first, r.first, 0.01f)
            assertEquals("slot $i same y", l.second, r.second, 0.01f)
        }
    }

    @Test fun CFG_15_slotOneIsNextToTheHorizontalEdgeInEveryCorner() {
        for (c in Corner.entries) {
            val (x, y) = PuckSectionsGeometry.centroid(PuckSectionsGeometry.wedges(c, ring)[0], ring)
            assertTrue("$c slot 1 hugs the horizontal edge: $x,$y", kotlin.math.abs(y) < kotlin.math.abs(x))
            val (x3, y3) = PuckSectionsGeometry.centroid(PuckSectionsGeometry.wedges(c, ring)[3], ring)
            assertTrue("$c slot 4 hugs the vertical edge: $x3,$y3", kotlin.math.abs(x3) < kotlin.math.abs(y3))
        }
    }

    @Test fun CFG_15_everyPointOfAWedgeHitsItsSlotAndTheGapBelongsToANeighbourNotToNothing() {
        for (c in Corner.entries) for (w in PuckSectionsGeometry.wedges(c, ring)) {
            for (f in listOf(0.1f, 0.5f, 0.9f)) {
                val a = Math.toRadians((w.startDeg + (w.endDeg - w.startDeg) * f).toDouble())
                val r = ring.inner + ring.thickness * 0.5f
                val hit = PuckSectionsGeometry.hit(c, ring, (r * cos(a)).toFloat(), (r * sin(a)).toFloat())
                assertEquals("$c slot ${w.slot} at $f", PuckSectionsGeometry.Hit.Slot(w.slot), hit)
            }
        }
        val w = PuckSectionsGeometry.wedges(Corner.BottomLeft, ring).sortedBy { it.startDeg }
        val gapAngle = Math.toRadians(((w[0].endDeg + w[1].startDeg) / 2f).toDouble())
        val hit = PuckSectionsGeometry.hit(Corner.BottomLeft, ring, (ring.mid * cos(gapAngle)).toFloat(), (ring.mid * sin(gapAngle)).toFloat())
        assertTrue("the gap still hits a wedge: $hit", hit is PuckSectionsGeometry.Hit.Slot)
    }

    @Test fun CFG_15_theCentreIsBackAndOutsideTheRingOrTheQuadrantIsNothing() {
        assertEquals(PuckSectionsGeometry.Hit.Back, PuckSectionsGeometry.hit(Corner.BottomLeft, ring, 0f, 0f))
        assertEquals(PuckSectionsGeometry.Hit.Back, PuckSectionsGeometry.hit(Corner.BottomLeft, ring, ring.backRadius - 1f, 0f))
        assertEquals(PuckSectionsGeometry.Hit.None, PuckSectionsGeometry.hit(Corner.BottomLeft, ring, ring.outer + 5f, -1f))
        // BottomLeft's visible quadrant is up and to the right; a point down-left of the centre is off screen
        assertEquals(PuckSectionsGeometry.Hit.None, PuckSectionsGeometry.hit(Corner.BottomLeft, ring, -ring.mid, ring.mid))
    }

    @Test fun INP_19_theRingIsDeepEnoughForA48dpTouchOnAPhoneAndItsRadiiAreOrdered() {
        for (side in listOf(320f, 360f, 411f, 600f, 800f)) {
            val hub = com.bos.sphere.core.input.PuckLayout.hubRadius(side)
            val r = PuckSectionsGeometry.ring(hub, hub - com.bos.sphere.core.input.PuckLayout.LABEL_BAND)
            assertTrue("$side: back < inner < outer", r.backRadius < r.inner && r.inner < r.outer)
            assertTrue("$side: the Back button is at least 48 dp across", r.backRadius * 2f >= 48f)
            assertTrue("$side: the ring is ${r.thickness} dp deep, at least 48", PuckSectionsGeometry.deepEnough(r))
            assertTrue("$side: the labels clear the ring", r.outer <= hub - com.bos.sphere.core.input.PuckLayout.LABEL_BAND)
        }
    }

    @Test fun INP_19_theBoundingBoxHoldsEveryCornerOfTheWedge() {
        for (c in Corner.entries) for (w in PuckSectionsGeometry.wedges(c, ring)) {
            val b = PuckSectionsGeometry.bounds(w, ring)
            for (a in listOf(w.startDeg, w.endDeg)) for (rad in listOf(ring.inner, ring.outer)) {
                val x = rad * cos(Math.toRadians(a.toDouble())).toFloat(); val y = rad * sin(Math.toRadians(a.toDouble())).toFloat()
                assertTrue("$c ${w.slot}: ($x,$y) in $b", x >= b.left - 0.01f && x <= b.right + 0.01f && y >= b.top - 0.01f && y <= b.bottom + 0.01f)
            }
        }
    }
}
