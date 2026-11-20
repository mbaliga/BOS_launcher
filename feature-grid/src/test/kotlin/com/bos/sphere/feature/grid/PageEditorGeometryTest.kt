package com.bos.sphere.feature.grid

import com.bos.sphere.feature.grid.PageEditorGeometry.Axis
import com.bos.sphere.feature.grid.PageEditorGeometry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 36 (pageEditor), redone in wave 45 (pageEditorLayout): the pure layout, swing, reorder and drop maths of the page editor. */
class PageEditorGeometryTest {
    private val d = 2.625f
    private fun lay(wDp: Float, hDp: Float, mirrored: Boolean = false, font: Float = 1f, density: Float = d, top: Float = 24f, bottom: Float = 24f, bottomBar: Float = PageEditorGeometry.BOTTOM_BAR_DP) =
        PageEditorGeometry.layout(wDp * density, hDp * density, density, font, mirrored, top * density, bottom * density, bottomBarDp = bottomBar)
    private fun portrait(mirrored: Boolean = false) = lay(411f, 891f, mirrored)
    private fun landscape(mirrored: Boolean = false) = lay(891f, 411f, mirrored, top = 0f)

    // sizes from a small phone to a large one, plus a tablet and the foldable-ish middle
    private val phones = listOf(320f to 568f, 360f to 640f, 360f to 740f, 393f to 852f, 411f to 891f, 412f to 915f, 384f to 800f)

    @Test fun HOM_12_pe01_portraitIsTopBarStripLiveCardTabsContentFromTopToBottom() {
        val l = portrait()
        assertEquals(PageEditorGeometry.Axis.Horizontal, l.axis); assertFalse(l.side)
        assertEquals("the top bar slot is 56 dp below the status bar", 56 * d, l.topBar.height, 0.5f)
        assertEquals(24 * d, l.topBar.top, 0.5f)
        assertTrue("the strip is under the top bar", l.strip.top >= l.topBar.bottom - 0.5f)
        assertTrue("the live card is under the strip", l.card.top > l.strip.bottom)
        assertTrue("the tab row is under the live card", l.tabRow.top > l.card.bottom)
        assertTrue("the content is under the tab row", l.content.top >= l.tabRow.bottom - 0.5f)
        assertTrue("and inside the window", l.content.bottom <= 891 * d)
    }

    @Test fun HOM_12_pe01b_theLiveCardIsNeverCroppedOnAnyPhoneSizeOrFontScale() {
        for ((w, h) in phones) for (font in listOf(1f, 1.3f, 2f)) for (bar in listOf(PageEditorGeometry.BOTTOM_BAR_DP, 0f)) {
            val l = lay(w, h, font = font, bottomBar = bar)
            val c = l.card
            val tag = "${w}x$h font $font bar $bar"
            assertTrue("$tag: left", c.left >= 0f); assertTrue("$tag: top", c.top >= l.strip.bottom - 0.5f)
            assertTrue("$tag: right", c.right <= w * d + 0.5f); assertTrue("$tag: bottom", c.bottom <= l.tabRow.top + 0.5f)
            assertEquals("$tag: centred", (w * d - c.width) / 2f, c.left, 0.5f)
            assertEquals("$tag: aspect of the window", w / h, c.width / c.height, 0.002f)
            assertTrue("$tag: scale at most 0.88", l.target.scale <= PageEditorGeometry.LIVE_SCALE_MAX + 1e-4f)
            assertTrue("$tag: content inside the window", l.content.bottom <= h * d - 24 * d + 0.5f)
            assertTrue("$tag: the card is big enough to see (>= 0.2)", l.target.scale >= PageEditorGeometry.LIVE_SCALE_FLOOR - 1e-4f)
        }
    }

    @Test fun HOM_12_pe01c_theContentPanelIsAboutThirtyPercentWhenTheLiveCardKeepsItsShareAndNeverBelowItsFloor() {
        // 30 percent of the window, unless the live card would then drop below 0.38: the panel gives way first.
        val l = lay(411f, 891f)
        assertTrue("with the bottom bar's room reserved the panel is about a fifth", l.content.height / d in 150f..200f)
        assertTrue("the live card keeps at least 0.38 where the panel can give way", l.target.scale >= 0.37f)
        val free = lay(411f, 891f, bottomBar = 0f)
        assertTrue("without the bar (the chrome's own top bar replaces it) the panel is about 30 percent", free.content.height >= 0.27f * 891 * d)
        assertTrue(free.target.scale >= 0.37f)
        assertEquals("a window tall enough gets the full 30 percent", 0.30f * 1400 * d, lay(500f, 1400f, bottomBar = 0f).content.height, 1f)
        for ((w, h) in phones) assertTrue("$w x $h", lay(w, h).content.height >= PageEditorGeometry.CONTENT_FLOOR_DP * d - 0.5f)
        assertTrue("the font grows the tab row", lay(411f, 891f, font = 1.3f).tabRow.height >= 1.3f * 48 * d - 0.5f)
        assertEquals("up to 1.5 times", 1.5f * 48 * d, lay(411f, 891f, font = 2f).tabRow.height, 0.5f)
    }

    @Test fun HOM_12_pe01d_stripCardsAre64By112DpWithEightDpGapsAtDefaultSize() {
        val l = portrait()
        assertEquals(64 * d, l.cardW, 0.5f); assertEquals(112 * d, l.cardH, 0.5f)
        assertEquals(72 * d, l.pitch, 0.5f)
        assertEquals("the focus slot is the middle of the strip", 411 * d / 2f, l.slotX, 0.5f)
        assertEquals((l.strip.top + l.strip.bottom) / 2f, l.slotY, 0.5f)
        assertTrue("the strip holds the card with room for the outline", l.strip.height >= l.cardH + 2 * 8 * d - 0.5f)
        assertEquals(0.9f, PageEditorGeometry.NEIGHBOUR_SCALE, 0f)
        assertTrue("a small window scales the cards down a little, not below 0.8", lay(320f, 568f).cardW in 0.8f * 64 * d - 0.5f..64 * d)
    }

    @Test fun HOM_12_pe02_mirroredPutsTheSideStripOnTheLeftAndTheTabsOnTheRight() {
        val l = landscape(mirrored = true)
        assertTrue(l.side); assertTrue(l.strip.left <= 0.5f)
        assertTrue("the card is right of the strip", l.card.left >= l.strip.right)
        assertTrue("the tabs are right of the card", l.tabs.left >= l.card.right)
        val r = landscape()
        assertTrue(r.strip.right >= 891 * d - 0.5f); assertTrue(r.card.right <= r.strip.left); assertTrue(r.tabs.right <= r.card.left)
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = true, orderRtl = false))
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = false, orderRtl = true))
        assertTrue(PageEditorGeometry.mirrored(layoutRtl = true, orderRtl = true))
        assertFalse(PageEditorGeometry.mirrored(layoutRtl = false, orderRtl = false))
    }

    @Test fun HOM_12_pe03_aMirroredPortraitStripRunsTheOtherWayAlongTheRow() {
        val l = portrait(mirrored = true)
        assertEquals(PageEditorGeometry.Axis.Horizontal, l.axis)
        val (dx0, _) = PageEditorGeometry.offsetOf(2, 1, l)
        val (dx1, _) = PageEditorGeometry.offsetOf(2, 1, portrait())
        assertEquals(-dx1 + 2 * (l.slotX - (l.card.left + l.card.width / 2f)), dx0, 0.5f)
        assertTrue("mirrored: a later page is to the left of the live card", dx0 < 0f && dx1 > 0f)
    }

    @Test fun HOM_12_pe04_landscapeAndTabletsPutTheStripOnTheSideAndKeepTheLiveCardWhole() {
        val sizes = listOf(891f to 411f, 640f to 360f, 800f to 1280f, 600f to 960f, 1280f to 800f)
        for ((w, h) in sizes) {
            val l = lay(w, h, top = 24f, bottom = 0f)
            val tag = "${w}x$h"
            assertTrue("$tag side", l.side); assertEquals(PageEditorGeometry.Axis.Vertical, l.axis)
            val c = l.card
            assertTrue("$tag inside", c.left >= 0f && c.right <= w * d + 0.5f && c.top >= l.topBar.bottom - 0.5f && c.bottom <= h * d + 0.5f)
            assertTrue("$tag the tabs keep room", l.tabs.width >= 200 * d)
            assertTrue("$tag the tabs and the card do not overlap", l.tabs.right <= c.left + 0.5f)
            assertTrue("$tag the strip is a column at the end", l.strip.height > l.strip.width)
            assertTrue("$tag the strip does not overlap the card", c.right <= l.strip.left + 0.5f)
        }
        assertFalse("599 dp wide portrait is still a phone", lay(599f, 900f).side)
        assertTrue("600 dp wide is a tablet", lay(600f, 900f).side)
    }

    @Test fun HOM_12_pe05_theTargetMovesTheFullScreenLayerOntoTheLiveCard() {
        for (l in listOf(portrait(), landscape(), portrait(mirrored = true), lay(800f, 1280f))) {
            val t = l.target
            val w = if (t.landscape) 891 * d else if (l.side) 800 * d else 411 * d; val h = if (t.landscape) 411 * d else if (l.side) 1280 * d else 891 * d
            if (!t.landscape && l.side && l.card.width > 0 && w != 800 * d) continue
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

    @Test fun HOM_12_pe07_offsetsAreFromTheLiveCardCentreAlongTheRowOrTheColumn() {
        val top = portrait(); val side = landscape()
        val cx = top.card.left + top.card.width / 2f; val cy = top.card.top + top.card.height / 2f
        assertEquals(top.slotX + top.pitch * 2 - cx, PageEditorGeometry.offsetOf(3, 1, top).first, 0.5f)
        assertEquals("a row card sits above the live card: negative dy", top.slotY - cy, PageEditorGeometry.offsetOf(3, 1, top).second, 0.5f)
        assertTrue(PageEditorGeometry.offsetOf(3, 1, top).second < 0f)
        assertEquals(side.slotY + side.pitch - (side.card.top + side.card.height / 2f), PageEditorGeometry.offsetOf(2, 1, side).second, 0.5f)
        assertTrue("the column is right of the live card", PageEditorGeometry.offsetOf(2, 1, side).first > 0f)
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
        val diag = PageEditorGeometry.swing(0f, 20f, -400f, false)
        assertTrue("the dominant offset picks the one tilt axis", diag.rotationX != 0f && diag.rotationY == 0f)
        assertEquals("a custom start scale (the strip card's size relative to the live card)", 0.4f, PageEditorGeometry.swing(0f, 0f, 100f, false, startScale = 0.4f).scale, 0.001f)
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
        val l = portrait()
        val live = PageEditorGeometry.DropTarget(1, l.card)
        val sc = EdgeRect(l.slotX + l.pitch - l.cardW / 2f, l.slotY - l.cardH / 2f, l.slotX + l.pitch + l.cardW / 2f, l.slotY + l.cardH / 2f)
        val peek = EdgeRect(l.strip.right - l.cardW / 2f, sc.top, l.strip.right + l.cardW / 2f, sc.bottom)
        val clipped = PageEditorGeometry.visible(peek, l.strip)
        assertNotNull(clipped)
        assertTrue("a card half off the strip is cut at its end", clipped!!.right <= l.strip.right)
        val targets = listOf(live, PageEditorGeometry.DropTarget(2, sc), PageEditorGeometry.DropTarget(3, clipped))
        assertEquals(1, PageEditorGeometry.hit(targets, l.card.left + 10, l.card.top + 10))
        assertEquals(2, PageEditorGeometry.hit(targets, sc.left + 5, sc.top + 5))
        assertNull("the gap, the tabs and the far edge are not targets", PageEditorGeometry.hit(targets, 1f, 1f))
        assertNull(PageEditorGeometry.visible(EdgeRect(0f, 0f, 10f, 10f), EdgeRect(20f, 20f, 30f, 30f)))
    }

    @Test fun HOM_12_pe11_everyPageHasAHue() {
        assertEquals(PageEditorGeometry.hue(0), PageEditorGeometry.hue(6), 0f)
        assertTrue(PageEditorGeometry.hue(0) != PageEditorGeometry.hue(1))
    }

    // ---- wave 37 (editorDebt) ----

    // ---- wave 45: reorder maths ----

    @Test fun HOM_12_pl01_dropIndexRoundsTheTravelToTheNearestSlotAndClamps() {
        val pitch = 72f
        assertEquals(2, PageEditorGeometry.dropIndex(2, 0f, pitch, 5))
        assertEquals(2, PageEditorGeometry.dropIndex(2, 35f, pitch, 5)); assertEquals(3, PageEditorGeometry.dropIndex(2, 37f, pitch, 5))
        assertEquals(1, PageEditorGeometry.dropIndex(2, -37f, pitch, 5)); assertEquals(2, PageEditorGeometry.dropIndex(2, -35f, pitch, 5))
        assertEquals(4, PageEditorGeometry.dropIndex(2, 1000f, pitch, 5)); assertEquals(0, PageEditorGeometry.dropIndex(2, -1000f, pitch, 5))
        assertEquals(0, PageEditorGeometry.dropIndex(0, 50f, pitch, 1))
        assertEquals("two slots", 4, PageEditorGeometry.dropIndex(2, 144f, pitch, 6))
    }

    @Test fun HOM_12_pl02_theInsertionLineIsAfterTheTargetWhenMovingLaterAndBeforeItWhenMovingEarlier() {
        assertEquals(4, PageEditorGeometry.insertionGap(1, 3)); assertEquals(1, PageEditorGeometry.insertionGap(3, 1))
        assertEquals("one step later", 3, PageEditorGeometry.insertionGap(1, 2)); assertEquals(0, PageEditorGeometry.insertionGap(2, 0))
    }

    @Test fun HOM_12_pl03_stepsStopAtTheEndsAndArrowsFollowTheStripDirection() {
        assertNull(PageEditorGeometry.stepTarget(0, -1, 4)); assertNull(PageEditorGeometry.stepTarget(3, 1, 4))
        assertEquals(2, PageEditorGeometry.stepTarget(1, 1, 4)); assertEquals(0, PageEditorGeometry.stepTarget(1, -1, 4))
        assertEquals("right is later in a row", 1, PageEditorGeometry.stepOf(true, false, right = true, down = false))
        assertEquals("left is earlier", -1, PageEditorGeometry.stepOf(true, false, right = false, down = false))
        assertEquals("a mirrored row: right is earlier", -1, PageEditorGeometry.stepOf(true, true, right = true, down = false))
        assertEquals("down is later in a column", 1, PageEditorGeometry.stepOf(false, false, right = false, down = true))
        assertEquals(-1, PageEditorGeometry.stepOf(false, true, right = false, down = false))
    }

    private val cells = listOf("a", "b", "c", "d", "e").mapIndexed { i, k -> com.bos.sphere.core.catalog.ColumnFill.Cell(k, i / 2, i % 2, com.bos.sphere.core.catalog.WidgetSpan(1, 1)) }

    @Test fun HOM_12_ed02_aPageStartsAtTheColumnOfItsFirstItemAndEmptyPagesShareTheNextStart() {
        // columns: a,b = 0; c,d = 1; e = 2
        assertEquals(listOf(0, 1, 2), PageEditorGeometry.pageStartColumns(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), cells, 3))
        assertEquals("an empty page starts where the next page with items does, trailing ones at the last column", listOf(0, 1, 1, 2, 2),
            PageEditorGeometry.pageStartColumns(listOf(listOf("a", "b"), listOf(), listOf("c", "d", "zz"), listOf("e"), listOf()), cells, 3))
        assertEquals(listOf(0), PageEditorGeometry.pageStartColumns(listOf(listOf()), emptyList(), 0))
    }

    @Test fun HOM_12_ed03_theScrollOffsetPutsTheColumnAtTheStartEdgeAndClampsAtTheEnd() {
        assertEquals(0, PageEditorGeometry.scrollForColumn(0, 100f, 500, rtl = false))
        assertEquals(200, PageEditorGeometry.scrollForColumn(2, 100f, 500, rtl = false))
        assertEquals("clamped at the end of the scroll", 500, PageEditorGeometry.scrollForColumn(9, 100f, 500, rtl = false))
        assertEquals("right to left: the start edge is the right edge", 500, PageEditorGeometry.scrollForColumn(0, 100f, 500, rtl = true))
        assertEquals(300, PageEditorGeometry.scrollForColumn(2, 100f, 500, rtl = true))
        assertEquals(0, PageEditorGeometry.scrollForColumn(3, 100f, 0, rtl = false))
    }

    @Test fun HOM_12_ed04_scrollingTheSurfaceSelectsThePageAtTheStartEdge() {
        val starts = listOf(0, 3, 3, 6)
        fun at(px: Int, sel: Int, rtl: Boolean = false, max: Int = 1000) = PageEditorGeometry.pageInView(starts, px, max, 100f, rtl, sel)
        assertEquals(0, at(0, 2)); assertEquals(0, at(120, 0))
        assertEquals("column 3 is where page 1 and 2 start: the selected one of the two stays", 2, at(300, 2))
        assertEquals("else the first of them", 1, at(300, 0))
        assertEquals(3, at(610, 0))
        assertEquals("the selected page near the end that cannot reach the start edge stays selected", 3, at(450, 3, max = 450))
        assertEquals("right to left reads from the right edge", 1, at(700, 0, rtl = true))
        assertEquals(0, PageEditorGeometry.pageInView(emptyList(), 10, 100, 100f, false, 0))
        // the round trip: scrolling to the start of a page with its own column selects that page
        assertEquals(3, at(PageEditorGeometry.scrollForColumn(6, 100f, 1000, false), 0))
        assertEquals(3, at(PageEditorGeometry.scrollForColumn(6, 100f, 1000, true), 0, rtl = true))
    }
}
