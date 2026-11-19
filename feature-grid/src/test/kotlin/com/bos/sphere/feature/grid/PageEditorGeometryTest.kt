package com.bos.sphere.feature.grid

import com.bos.sphere.feature.grid.PageEditorGeometry.Axis
import com.bos.sphere.feature.grid.PageEditorGeometry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 36 (pageEditor): the pure layout, swing and drop maths of the page editor. */
class PageEditorGeometryTest {
    private val d = 2.625f
    private fun portrait(continuous: Boolean, mirrored: Boolean = false) =
        PageEditorGeometry.layout(411 * d, 891 * d, continuous, mirrored, 76 * d, 92 * d, 56 * d, 12 * d)
    private fun landscape(continuous: Boolean, mirrored: Boolean = false) =
        PageEditorGeometry.layout(891 * d, 411 * d, continuous, mirrored, 76 * d, 92 * d, 56 * d, 12 * d)

    @Test fun HOM_12_pe01_paginatedPortraitPutsTheStripOnTheRightAndTheTabsOnTheLeft() {
        val l = portrait(continuous = false)
        assertEquals(Axis.Vertical, l.axis)
        assertEquals(CardSide.Right, l.target.side)
        assertTrue("the card is pushed against the right edge, 4 percent off screen", l.card.right > 411 * d)
        assertTrue("the tabs are left of the strip", l.tabs.right <= l.strip.left + 0.01f)
        assertTrue("the tabs keep at least a third of the width for the app list", l.tabs.width >= 411 * d * 0.33f)
        assertTrue("the card is centred in the strip's height", kotlin.math.abs((l.card.top + l.card.bottom) / 2f - (l.strip.top + l.strip.bottom) / 2f) < 1f)
        assertTrue("neighbours peek: the strip is taller than the card", l.strip.height > l.card.height + 2 * 40 * d)
    }

    @Test fun HOM_12_pe02_mirroredPutsTheStripOnTheLeft() {
        val l = portrait(continuous = false, mirrored = true)
        assertEquals(CardSide.Left, l.target.side)
        assertTrue(l.card.left < 0f)
        assertTrue(l.tabs.left >= l.strip.right - 0.01f)
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = true, orderRtl = false))
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = false, orderRtl = true))
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = true, orderRtl = true))
        assertFalse(PageEditorGeometry.mirrored(layoutRtl = false, orderRtl = false))
    }

    @Test fun HOM_12_pe03_continuousPortraitPutsTheStripAcrossTheTopAndTheTabsBelow() {
        val l = portrait(continuous = true)
        assertEquals(Axis.Horizontal, l.axis)
        assertTrue("the tabs are below the strip", l.tabs.top >= l.strip.bottom)
        assertEquals("the live card is centred across", (411 * d - l.card.width) / 2f, l.card.left, 0.5f)
        assertEquals(76 * d, l.card.top, 0.5f)
        assertTrue("neighbours peek left and right", l.card.left > 0f && l.strip.width > l.card.width)
        assertTrue("the content has room: more than half of the free height", l.tabs.height > (891 - 76 - 92) * d * 0.5f)
        assertTrue("the rail (move, delete) sits under the card inside the strip", l.rail.top >= l.card.bottom - 0.01f && l.rail.bottom <= l.strip.bottom + 0.01f)
    }

    @Test fun HOM_12_pe04_landscapeKeepsTheStripOnTheSideInBothModes() {
        for (continuous in listOf(false, true)) {
            val l = landscape(continuous)
            assertEquals(Axis.Vertical, l.axis)
            assertTrue(l.target.landscape)
            assertTrue("the card fits the free height", l.card.height <= (411 - 76 - 92) * d + 0.5f)
            assertTrue("the tabs keep a third of the width", l.tabs.width >= 891 * d * 0.33f)
        }
        assertFalse("tabs are a row in landscape and in continuous", PageEditorGeometry.tabsVertical(continuous = false, landscape = true))
        assertFalse(PageEditorGeometry.tabsVertical(continuous = true, landscape = false))
        assertTrue(PageEditorGeometry.tabsVertical(continuous = false, landscape = false))
    }

    @Test fun HOM_12_pe05_theTargetMovesTheFullScreenLayerOntoTheFocusSlot() {
        for (l in listOf(portrait(false), portrait(true), landscape(false), portrait(false, mirrored = true))) {
            val t = l.target
            val w = if (t.landscape) 891 * d else 411 * d; val h = if (t.landscape) 411 * d else 891 * d
            // The layer scales about its centre and is then translated: its on-screen rectangle is the card.
            val cx = w / 2f + t.translationX; val cy = h / 2f + t.translationY
            assertEquals(l.card.left + l.card.width / 2f, cx, 0.5f)
            assertEquals(l.card.top + l.card.height / 2f, cy, 0.5f)
            assertEquals(w * t.scale, l.card.width, 0.5f)
        }
    }

    @Test fun HOM_12_pe06_entriesPutGlanceFirstAndAddLast() {
        val e = PageEditorGeometry.entries(glance = true, pageCount = 3)
        assertEquals(listOf(Kind.Glance, Kind.Page, Kind.Page, Kind.Page, Kind.Add), e.map { it.kind })
        assertEquals(listOf(-1, 0, 1, 2, -1), e.map { it.page })
        assertEquals(listOf(Kind.Page, Kind.Add), PageEditorGeometry.entries(false, 1).map { it.kind })
        assertEquals(2, PageEditorGeometry.focusPosition(glance = true, glanceFocus = false, current = 1))
        assertEquals(0, PageEditorGeometry.focusPosition(glance = true, glanceFocus = true, current = 1))
        assertEquals(1, PageEditorGeometry.focusPosition(glance = false, glanceFocus = true, current = 1))
    }

    @Test fun HOM_12_pe07_offsetsRunDownTheSideStripAndAcrossTheTopStripMirroredInRtl() {
        val side = portrait(false)
        assertEquals(0f to side.pitch * 2, PageEditorGeometry.offsetOf(3, 1, side))
        assertEquals(0f to -side.pitch, PageEditorGeometry.offsetOf(0, 1, side))
        val top = portrait(true); val topRtl = portrait(true, mirrored = true)
        assertEquals(top.pitch to 0f, PageEditorGeometry.offsetOf(2, 1, top))
        assertEquals(-topRtl.pitch to 0f, PageEditorGeometry.offsetOf(2, 1, topRtl))
    }

    @Test fun HOM_12_pe08_theSwingStartsAtTheTappedCardAndEndsAtRest() {
        val start = PageEditorGeometry.swing(0f, 0f, 480f, reduced = false)
        assertEquals(480f, start.translationY, 0.001f)
        assertTrue("a card below tilts about the horizontal axis", start.rotationX != 0f && start.rotationY == 0f)
        assertEquals(PageEditorGeometry.SWING_START_SCALE, start.scale, 0.001f)
        val mid = PageEditorGeometry.swing(0.5f, 0f, 480f, reduced = false)
        assertEquals(240f, mid.translationY, 0.001f)
        assertEquals(start.rotationX / 2f, mid.rotationX, 0.001f)
        assertTrue(PageEditorGeometry.swing(1f, 0f, 480f, false).atRest)
        assertTrue(PageEditorGeometry.REST.atRest)
        // The opposite side tilts the other way; a card on the side of a top strip tilts about the vertical axis.
        assertEquals(-start.rotationX, PageEditorGeometry.swing(0f, 0f, -480f, false).rotationX, 0.001f)
        val side = PageEditorGeometry.swing(0f, 300f, 0f, false)
        assertTrue(side.rotationY != 0f && side.rotationX == 0f)
    }

    @Test fun HOM_12_pe09_reducedMotionCrossFadesWithoutMovementOrTilt() {
        val s = PageEditorGeometry.swing(0.25f, 0f, 480f, reduced = true)
        assertEquals(0f, s.translationY, 0f); assertEquals(0f, s.rotationX, 0f); assertEquals(1f, s.scale, 0f)
        assertEquals(0.25f, s.alpha, 0.0001f)
        assertTrue(PageEditorGeometry.swing(1f, 0f, 480f, true).atRest)
        assertTrue("the quick path is shorter", PageEditorGeometry.SWING_REDUCED_MS < PageEditorGeometry.SWING_MS)
        assertEquals("about 350 ms", 350, PageEditorGeometry.SWING_MS)
    }

    @Test fun HOM_12_pe10_dropHitTestingPicksThePageUnderTheFinger() {
        val l = portrait(false)
        val live = PageEditorGeometry.DropTarget(1, l.card)
        val below = PageEditorGeometry.DropTarget(2, EdgeRect(l.card.left, l.card.bottom + 12 * d, l.card.right, l.card.bottom + 12 * d + l.card.height))
        val clipped = PageEditorGeometry.visible(below.rect, l.strip)
        assertNotNull(clipped)
        assertTrue("the peeking card is cut at the strip's end", clipped!!.bottom <= l.strip.bottom)
        val targets = listOf(live, PageEditorGeometry.DropTarget(2, clipped))
        assertEquals(1, PageEditorGeometry.hit(targets, l.card.left + 10, l.card.top + 10))
        assertEquals(2, PageEditorGeometry.hit(targets, l.card.left + 10, l.card.bottom + 20 * d))
        assertNull("the gap, the tabs and the far edge are not targets", PageEditorGeometry.hit(targets, 10f, 10f))
        assertNull(PageEditorGeometry.hit(targets, l.card.left + 10, l.card.bottom + 2 * d))
        assertNull(PageEditorGeometry.visible(EdgeRect(0f, 0f, 10f, 10f), EdgeRect(20f, 20f, 30f, 30f)))
    }

    @Test fun HOM_12_pe11_everyPageHasAHue() {
        assertEquals(PageEditorGeometry.hue(0), PageEditorGeometry.hue(6), 0f)
        assertTrue(PageEditorGeometry.hue(0) != PageEditorGeometry.hue(1))
    }
}
