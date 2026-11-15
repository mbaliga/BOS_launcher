package com.bos.sphere.feature.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
