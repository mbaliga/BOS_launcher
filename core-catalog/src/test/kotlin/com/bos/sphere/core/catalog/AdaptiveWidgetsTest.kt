package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWidgetsTest {

    @Test
    fun HOM_11_sizeClassesFromWindow() {
        assertEquals(SizeClass(SizeBucket.Compact, SizeBucket.Medium), SizeClass.of(400, 800))
        assertEquals(SizeClass(SizeBucket.Medium, SizeBucket.Compact), SizeClass.of(800, 400))
        assertEquals(SizeClass(SizeBucket.Expanded, SizeBucket.Expanded), SizeClass.of(1280, 960))
    }

    @Test
    fun HOM_11_gridGrowsWithAreaAndDensity() {
        val phone = AdaptiveGrid.dims(300f, 500f)
        val tablet = AdaptiveGrid.dims(900f, 600f)
        assertTrue(tablet.columns > phone.columns)
        assertTrue(AdaptiveGrid.dims(300f, 500f, 1.5f).columns > phone.columns)
        assertTrue(AdaptiveGrid.dims(300f, 500f, 0.6f).columns < phone.columns || phone.columns == 3)
        assertTrue(AdaptiveGrid.dims(10f, 10f).columns >= 3)
    }

    @Test
    fun HOM_11_eachSizeClassKeepsItsOwnArrangement() {
        val small = SizeClass.of(400, 800)
        val big = SizeClass.of(1200, 900)
        val sA = HomeState(dock = listOf("a"))
        val sB = HomeState(dock = listOf("b"))
        var arr = SizeClassArrangements().with(small, sA)
        val (seeded, arr2) = arr.resolve(big, HomeState())
        assertEquals(sA, seeded) // new class starts from the nearest one
        arr = arr2.with(big, sB)
        assertEquals(sA, arr.stateFor(small))
        assertEquals(sB, arr.stateFor(big))
        assertEquals(sB, arr.resolve(big, HomeState()).first)
    }

    @Test
    fun HOM_11_noKnownClassFallsBackToGivenState() {
        val f = HomeState(dock = listOf("z"))
        assertEquals(f, SizeClassArrangements().resolve(SizeClass.of(400, 800), f).first)
    }

    @Test
    fun HOM_08_widgetsAndTilesShareOneLayoutWithoutOverlap() {
        val keys = listOf("a", "w1", "b", "w2", "c", "d")
        val spans = mapOf("w1" to WidgetSpan(2, 2), "w2" to WidgetSpan(3, 1))
        val r = TilePacker.pack(keys, { spans[it] ?: WidgetSpan(1, 1) }, 4, 4)
        assertTrue(r.overflow.isEmpty())
        val cells = HashSet<Pair<Int, Int>>()
        for (p in r.placed) for (y in p.row until p.row + p.span.rows) for (x in p.col until p.col + p.span.cols) {
            assertTrue("overlap at $x,$y", cells.add(x to y))
            assertTrue(x < 4 && y < 4)
        }
    }

    @Test
    fun HOM_08_overflowWhenFullAndCanGrowRespectsNeighbours() {
        val r = TilePacker.pack(listOf("w", "a", "b"), { if (it == "w") WidgetSpan(2, 2) else WidgetSpan(1, 1) }, 2, 2)
        assertEquals(listOf("a", "b"), r.overflow)
        val r2 = TilePacker.pack(listOf("w", "a"), { if (it == "w") WidgetSpan(1, 1) else WidgetSpan(1, 1) }, 3, 1)
        assertTrue(TilePacker.canGrow(r2.placed, "w", WidgetSpan(1, 1), 3, 1))
        assertFalse(TilePacker.canGrow(r2.placed, "w", WidgetSpan(2, 1), 3, 1))
    }

    @Test
    fun HOM_08_widgetBandNeverOverlapsTiles() {
        val area = Box(0f, 0f, 1000f, 600f)
        for (edge in WidgetBand.Edge.entries) for (n in 1..4) {
            val l = WidgetBand.layout(area, n, edge)
            assertNotNull(l.band)
            assertFalse(l.tiles.intersects(l.band!!))
            assertEquals(n, l.widgets.size)
            l.widgets.forEach { assertTrue(l.band!!.contains(it)); assertFalse(l.tiles.intersects(it)) }
            for (i in l.widgets.indices) for (j in i + 1 until l.widgets.size) assertFalse(l.widgets[i].intersects(l.widgets[j]))
        }
        assertNull(WidgetBand.layout(area, 0, WidgetBand.Edge.Bottom).band)
    }

    @Test
    fun HOM_07_resizeByHandlesAndKeys() {
        assertEquals(2 to 0, WidgetSizing.deltaForDrag(WidgetSizing.Handle.East, 190f, 5f, 100f, 100f))
        assertEquals(1 to 1, WidgetSizing.deltaForDrag(WidgetSizing.Handle.SouthEast, 100f, 110f, 100f, 100f))
        assertEquals(1 to 0, WidgetSizing.deltaForDrag(WidgetSizing.Handle.West, -100f, 0f, 100f, 100f))
        assertEquals(1 to 0, WidgetSizing.deltaForKey(WidgetSizing.Arrow.Right))
        assertEquals(0 to -1, WidgetSizing.deltaForKey(WidgetSizing.Arrow.Up))
        assertEquals(WidgetSpan(3, 2), WidgetSizing.spanFor(250, 110, 90f))
    }

    @Test
    fun HOM_07_restoreAfterRebootAndProviderUpdate() {
        val saved = listOf(SavedWidget("widget:1", 1, "p/A"), SavedWidget("widget:2", 2, "p/B"), SavedWidget("widget:3", 3, "gone/C"))
        val plan = WidgetRestore.plan(saved, hostedIds = setOf(1), installedProviders = setOf("p/A", "p/B"))
        assertTrue(plan[0] is RestoreAction.Keep)
        assertTrue(plan[1] is RestoreAction.Rebind)
        assertTrue(plan[2] is RestoreAction.Placeholder)
    }

    @Test
    fun HOM_07_pickerSearchMatchesAppAndWidgetNames() {
        val all = listOf(WidgetChoiceRef("a/1", "Clock", "Analog"), WidgetChoiceRef("b/1", "Weather", "Forecast clock"), WidgetChoiceRef("c/1", "Notes", "Quick note"))
        assertEquals(listOf("a/1", "b/1"), WidgetPickerSearch.filter(all, "clock").map { it.provider })
        assertEquals(3, WidgetPickerSearch.filter(all, "").size)
        assertTrue(WidgetPickerSearch.filter(all, "xyz").isEmpty())
    }

    @Test
    fun HOM_14_stackSwitchesBySwipeWheelAndKeys() {
        var s = WidgetStack(listOf("a", "b", "c"))
        s = s.next(); assertEquals("b", s.current)
        s = s.scroll(1f).scroll(1f); assertEquals("a", s.current)
        s = s.previous(); assertEquals("c", s.current)
        s = s.forKey(WidgetSizing.Arrow.Left); assertEquals("b", s.current)
        s = s.remove("a"); assertEquals("b", s.current)
        s = s.add("d"); assertEquals("d", s.current)
        assertEquals(WidgetStack(emptyList()).next(), WidgetStack(emptyList()))
    }

    @Test
    fun HOM_13_snapGuidesToEdgesCentresAndNeighbours() {
        val area = Box(0f, 0f, 1000f, 800f)
        val other = Box(100f, 100f, 300f, 300f)
        val s = FreeForm.snap(Box(305f, 100f, 405f, 150f), area, listOf(other))
        assertEquals(300f, s.box.left, 0.01f)
        assertEquals(100f, s.box.top, 0.01f)
        assertEquals(2, s.guides.size)
        val far = FreeForm.snap(Box(450f, 450f, 510f, 500f), area, listOf(other), threshold = 3f)
        assertEquals(450f, far.box.left, 0.01f)
    }

    @Test
    fun HOM_13_widgetsMayNotOverlapEachOther() {
        val area = Box(0f, 0f, 1000f, 800f)
        val w = Box(100f, 100f, 400f, 300f)
        assertFalse(FreeForm.canPlaceWidget(Box(350f, 250f, 600f, 400f), area, listOf(w)))
        assertTrue(FreeForm.canPlaceWidget(Box(400f, 100f, 600f, 300f), area, listOf(w)))
        assertFalse(FreeForm.canPlaceWidget(Box(900f, 700f, 1100f, 900f), area, emptyList()))
        assertEquals(Box(800f, 600f, 1000f, 800f), FreeForm.clamp(Box(900f, 700f, 1100f, 900f), area))
    }

    @Test
    fun HOM_15_recentFilesMergeNewestFirstDedupeAndRespectGrants() {
        val a = listOf(RecentFile("u1", "a.pdf", "", 100, "s1"), RecentFile("u2", "b.txt", "", 300, "s1"))
        val b = listOf(RecentFile("u1", "a.pdf", "", 200, "s2"), RecentFile("u3", "c.png", "", 50, "s2"))
        val m = RecentFilesModel.merge(listOf(a, b), 3)
        assertEquals(listOf("u2", "u1", "u3"), m.map { it.uri })
        assertEquals(listOf("u2"), RecentFilesModel.withinGrants(m, setOf("s1")).map { it.uri })
        assertEquals(1, RecentFilesModel.merge(listOf(a, b), 1).size)
        assertEquals("2 h ago", RecentFilesModel.age(10_000_000, 10_000_000 - 7_300_000))
    }

    @Test
    fun HOM_16_wallpaperOffsetsAndAccent() {
        assertEquals(0.5f, WallpaperMath.xOffset(0f, 1), 0f)
        assertEquals(0.5f, WallpaperMath.xOffset(1f, 3), 0f)
        assertEquals(1f, WallpaperMath.xOffset(9f, 3), 0f)
        assertEquals(0.5f, WallpaperMath.xStep(3), 0f)
        val vivid = 0xFF2040E0.toInt()
        val got = WallpaperMath.accent(listOf(0xFF808080.toInt(), vivid), 0xFFFFFFFF.toInt())
        assertEquals(vivid, got)
        assertEquals(0xFF123456.toInt(), WallpaperMath.accent(listOf(0xFF808080.toInt()), 0xFF123456.toInt()))
        val dark = WallpaperMath.accent(listOf(0xFF100030.toInt()), 0)
        assertTrue((dark shr 16 and 0xFF) + (dark shr 8 and 0xFF) + (dark and 0xFF) > 300 || (dark and 0xFF) >= 150)
    }
}
