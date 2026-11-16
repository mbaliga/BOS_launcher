package com.bos.sphere.feature.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-13: hosted widgets on the desktop surface: ids, sizes, snapping and non-overlap (the second-display proof needs a device). */
class DesktopWidgetsTest {
    private val container = FRect(0f, 0f, 800f, 450f)

    @Test
    fun HOM_13_aHostedWidgetItemIdRoundTripsAndTheBuiltInClockIsNotHosted() {
        assertEquals("aw:42", DesktopWidgets.id(42))
        assertEquals(42, DesktopWidgets.appWidgetId("aw:42"))
        assertNull(DesktopWidgets.appWidgetId("clock"))
        assertTrue(DesktopWidgets.isHosted(PlacedItem(PlacedItem.KIND_WIDGET, "aw:7", 0f, 0f, 10f, 10f)))
        assertFalse(DesktopWidgets.isHosted(PlacedItem(PlacedItem.KIND_WIDGET, "clock", 0f, 0f, 10f, 10f)))
        assertFalse(DesktopWidgets.isHosted(PlacedItem(PlacedItem.KIND_APP, "aw:7", 0f, 0f, 10f, 10f)))
    }

    @Test
    fun HOM_13_aHostedWidgetItemSurvivesTheProfileCodec() {
        val p = DesktopWidgets.add(DesktopProfile(), 5, 160f, 80f, container)!!
        val back = ProfileCodec.decode(ProfileCodec.encode(p))!!
        assertEquals(listOf(5), DesktopWidgets.hostedIds(back))
        assertEquals(p.items, back.items)
    }

    @Test
    fun HOM_13_theProvidersMinimumBecomesWholeCellsAndFitsTheWorkArea() {
        val (w, h) = DesktopWidgets.sizeFor(110f, 40f, 80f, container)
        assertEquals(160f, w, 0.001f)
        assertEquals(80f, h, 0.001f)
        val (bw, bh) = DesktopWidgets.sizeFor(5000f, 5000f, 80f, container)
        assertTrue(bw <= container.width && bh <= container.height)
        assertEquals(0f, bw % 80f, 0.001f)
        val (sw, sh) = DesktopWidgets.sizeFor(0f, 0f, 80f, container)
        assertEquals(80f to 80f, sw to sh)
    }

    @Test
    fun HOM_13_twoWidgetsNeverOverlapWhenAdded() {
        var p = DesktopProfile()
        for (id in 1..6) p = DesktopWidgets.add(p, id, 240f, 160f, container)!!
        val rects = p.items.map { it.rect() }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("widgets $i and $j overlap", rects[i].overlaps(rects[j]))
        assertTrue(rects.all { it.inside(container) })
    }

    @Test
    fun HOM_13_aWidgetThatFitsNowhereIsRefusedSoTheIdCanBeReleased() {
        var p = DesktopProfile()
        p = DesktopWidgets.add(p, 1, 800f, 450f, container)!!
        assertNull(DesktopWidgets.add(p, 2, 160f, 160f, container))
        assertNull(DesktopWidgets.add(DesktopProfile(), 3, 900f, 100f, container))
    }

    @Test
    fun HOM_13_addingTheSameWidgetTwiceChangesNothing() {
        val p = DesktopWidgets.add(DesktopProfile(), 9, 160f, 160f, container)!!
        assertEquals(p, DesktopWidgets.add(p, 9, 160f, 160f, container))
    }

    @Test
    fun HOM_13_draggingAWidgetNearAnotherSnapsToItWithAGuideAndNeverOverlapsIt() {
        var p = DesktopWidgets.add(DesktopProfile(), 1, 200f, 120f, container)!!
        p = DesktopWidgets.add(p, 2, 200f, 120f, container)!!
        val second = p.items.first { it.id == "aw:2" }
        val first = p.items.first { it.id == "aw:1" }
        // Drop the second one 3 dp from the first one's right edge, overlapping it in height.
        val target = FRect(first.x + first.w - 20f, first.y + 2f, first.x + first.w - 20f + second.w, first.y + 2f + second.h)
        val (moved, guides) = DesktopWidgets.move(p, "aw:2", target, container)
        val a = moved.items.first { it.id == "aw:1" }.rect()
        val b = moved.items.first { it.id == "aw:2" }.rect()
        assertFalse(a.overlaps(b))
        assertTrue(b.inside(container))
        assertNotNull(guides)
    }

    @Test
    fun HOM_13_removingAnItemKeepsTheOthers() {
        var p = DesktopWidgets.add(DesktopProfile(), 1, 160f, 100f, container)!!
        p = DesktopWidgets.add(p, 2, 160f, 100f, container)!!
        val after = DesktopWidgets.remove(p, "aw:1")
        assertEquals(listOf(2), DesktopWidgets.hostedIds(after))
    }

    @Test
    fun HOM_13_theWidgetSlotIsEmptyUntilTheAppPlugsItsHostIn() {
        assertNull(DesktopWidgetSlot.provider)
    }
}

/** HOM-13: resizing a hosted widget after it is placed: whole cells, provider limits, container, non-overlap, persisted. */
class DesktopWidgetResizeTest {
    private val container = FRect(0f, 0f, 800f, 450f)
    private val cell = 80f

    private fun two(): DesktopProfile {
        var p = DesktopWidgets.add(DesktopProfile(), 1, 160f, 80f, container)!!   // top-left
        p = p.copy(items = p.items + PlacedItem(PlacedItem.KIND_WIDGET, "aw:2", 400f, 8f, 160f, 160f))
        return p
    }

    private fun item(p: DesktopProfile, id: String) = DesktopWidgets.widgetItem(p, id)!!

    @Test fun HOM_13_resizeSnapsToWholeCellsAndKeepsTheTopLeftCorner() {
        val p = two()
        val before = item(p, "aw:1")
        val r = DesktopWidgets.resize(p, "aw:1", 237f, 110f, cell, container)
        val a = item(r, "aw:1")
        assertEquals(before.x, a.x, 0.001f); assertEquals(before.y, a.y, 0.001f)
        assertEquals(240f, a.w, 0.001f)   // 237 rounds to 3 cells
        assertEquals(80f, a.h, 0.001f)    // 110 rounds to 1 cell
    }

    @Test fun HOM_13_resizeNeverGoesBelowOneCellOrOutsideTheWorkArea() {
        val p = two()
        val tiny = item(DesktopWidgets.resize(p, "aw:2", 1f, 1f, cell, container), "aw:2")
        assertEquals(80f, tiny.w, 0.001f); assertEquals(80f, tiny.h, 0.001f)
        val big = item(DesktopWidgets.resize(p, "aw:2", 5000f, 5000f, cell, container), "aw:2")
        assertTrue(big.rect().inside(container))
    }

    @Test fun HOM_13_aWidgetCannotBeGrownOverAnotherWidgetItStopsAtTheLargestFreeSize() {
        val p = two() // aw:1 is at x=8..168; aw:2 starts at x=400, so at most 4 cells (320dp) fit to its left
        val grown = item(DesktopWidgets.resize(p, "aw:1", 800f, 80f, cell, container), "aw:1")
        assertEquals(320f, grown.w, 0.001f)
        assertFalse(grown.rect().overlaps(item(p, "aw:2").rect()))
        val tall = item(DesktopWidgets.resize(p, "aw:1", 560f, 400f, cell, container), "aw:1")
        assertFalse(tall.rect().overlaps(item(p, "aw:2").rect()))
        assertTrue(tall.w * tall.h >= grown.w * grown.h)
    }

    @Test fun HOM_13_theProvidersMinimumSizeAndAxesAreHonoured() {
        val p = two()
        val limits = ResizeLimits(minWidthDp = 150f, minHeightDp = 150f, horizontal = true, vertical = false)
        val r = item(DesktopWidgets.resize(p, "aw:1", 80f, 400f, cell, container, limits), "aw:1")
        assertEquals(160f, r.w, 0.001f)           // 80 would be below the 150 minimum: two cells
        assertEquals(80f, r.h, 0.001f)            // vertical resizing is not allowed, height is unchanged
        val none = ResizeLimits(0f, 0f, horizontal = false, vertical = false)
        assertSame(p, DesktopWidgets.resize(p, "aw:1", 400f, 400f, cell, container, none))
    }

    @Test fun HOM_13_theSizeMenuStepsOneCellAndStopsAtTheLimits() {
        var p = two()
        p = DesktopWidgets.resizeByCells(p, "aw:2", 1, 0, cell, container)
        assertEquals(240f, item(p, "aw:2").w, 0.001f)
        p = DesktopWidgets.resizeByCells(p, "aw:2", 0, -1, cell, container)
        assertEquals(80f, item(p, "aw:2").h, 0.001f)
        repeat(5) { p = DesktopWidgets.resizeByCells(p, "aw:2", -1, 0, cell, container) }
        assertEquals(80f, item(p, "aw:2").w, 0.001f)
    }

    @Test fun HOM_13_aResizedWidgetSurvivesTheProfileCodecAndNothingElseMoves() {
        val p = two()
        val r = DesktopWidgets.resize(p, "aw:2", 320f, 240f, cell, container)
        val back = ProfileCodec.decode(ProfileCodec.encode(r))!!
        assertEquals(r.items, back.items)
        assertEquals(item(p, "aw:1"), item(back, "aw:1"))
    }

    @Test fun HOM_13_resizingAnUnknownOrNonWidgetItemChangesNothing() {
        val p = two().copy(items = two().items + PlacedItem(PlacedItem.KIND_APP, "com.x/.A#0", 600f, 300f, 80f, 80f))
        assertSame(p, DesktopWidgets.resize(p, "com.x/.A#0", 400f, 400f, cell, container))
        assertSame(p, DesktopWidgets.resize(p, "aw:99", 400f, 400f, cell, container))
    }
}
