package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWiringTest {
    private val cap: (PageLayout) -> Int = { 12 }
    private val w = ItemKeys.widget(5)
    private fun spans(k: String) = if (k == w) WidgetSpan(2, 2) else WidgetSpan(1, 1)

    // ---- HOM-08: widgets share the tile layout on the grid
    @Test
    fun HOM_08_keyAtCellFindsTheWidgetOnEveryCellItCovers() {
        val packed = TilePacker.pack(listOf("a", w, "b"), ::spans, 4, 4)
        val cells = (0 until 16).associateWith { SpanGrid.keyAtCell(packed.placed, 4, it) }
        val widgetCells = cells.filterValues { it == w }.keys
        assertEquals(setOf(1, 2, 5, 6), widgetCells)
        assertEquals("a", cells[0])
    }

    @Test
    fun HOM_08_insertIndexFollowsTheCellOrderAroundAWidget() {
        val keys = listOf("a", w, "b", "c")
        // Dropping on the cell right after the widget's block lands after "a" and the widget, before "b".
        assertEquals(2, SpanGrid.insertIndex(keys, "x", ::spans, 4, 4, 3))
        assertEquals(0, SpanGrid.insertIndex(keys, "x", ::spans, 4, 4, 0))
        assertEquals(4, SpanGrid.insertIndex(keys, "x", ::spans, 4, 4, 15))
    }

    @Test
    fun HOM_08_reflowSplitsAPageSoNothingIsPackedOffIt() {
        val keys = listOf("a", "b", "c", w, "d", "e")
        val page = HomePage(keys, PageLayout(), id = 7)
        val out = SpanReflow.reflow(listOf(page), ::spans) { 3 to 2 }
        assertEquals(keys, out.flatMap { it.keys })
        assertTrue(out.size > 1)
        for (p in out) {
            assertEquals(7L, p.id)
            assertTrue(TilePacker.pack(p.keys, ::spans, 3, 2).overflow.isEmpty())
        }
    }

    @Test
    fun HOM_08_reflowLeavesPagesWithoutWidgetsAlone() {
        val page = HomePage(listOf("a", "b"), PageLayout(), id = 1)
        assertEquals(listOf(page), SpanReflow.reflow(listOf(page), ::spans) { 3 to 2 })
    }

    @Test
    fun HOM_08_foldedBackPagesMergeToOneStoredPage() {
        val keys = listOf("a", "b", "c", w, "d", "e")
        val out = SpanReflow.reflow(listOf(HomePage(keys, PageLayout(), id = 7)), ::spans) { 3 to 2 }
        assertEquals(keys, HomeLayoutMath.toStored(out, emptyList(), emptySet()).single().keys)
    }

    // ---- HOM-04: drop onto an item
    @Test
    fun HOM_04_onlyTheCentreOfAnItemMeansDropOnto() {
        val s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1)))
        assertEquals("b", DropOntoIntent.target("a", "b", 3f, 3f, 100f, s))
        assertNull(DropOntoIntent.target("a", "b", 40f, 0f, 100f, s))
        assertNull(DropOntoIntent.target("a", "a", 0f, 0f, 100f, s))
        assertNull(DropOntoIntent.target("a", null, 0f, 0f, 100f, s))
    }

    @Test
    fun HOM_04_widgetsAreNeverDropTargetsOrFileable() {
        val s = HomeState(pages = listOf(HomePage(listOf("a", w), id = 1)), widgets = mapOf(w to WidgetSpan(2, 2)))
        assertNull(DropOntoIntent.target("a", w, 0f, 0f, 100f, s))
        assertNull(DropOntoIntent.target(w, "a", 0f, 0f, 100f, s))
    }

    @Test
    fun HOM_04_dropOntoAFolderFilesIn() {
        var s = HomeEdit.createFolder(HomeState(pages = listOf(HomePage(listOf("a", "b", "c"), id = 1))), listOf("a", "b"), "f", "F", cap)
        assertEquals(ItemKeys.folder("f"), DropOntoIntent.target("c", ItemKeys.folder("f"), 0f, 0f, 100f, s))
        s = HomeEdit.dropOnto(s, "c", ItemKeys.folder("f"), "n", "F", cap)
        assertEquals(listOf("a", "b", "c"), s.folders.getValue("f").keys)
    }

    // ---- HOM-02: non-app items on the pages and surviving a save
    @Test
    fun HOM_02_nonAppItemsResolveAndSurviveVisualAndStoredRoundTrip() {
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1)))
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("https://x.org"), ItemKind.Link, "x"), 0, cap)
        s = HomeEdit.add(s, ItemDef(w, ItemKind.Widget, "W", "p/c"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.createFolder(s, listOf("a", "b"), "f", "F", cap)
        val extras = s.copy(pages = emptyList())
        val layout = HomeLayout(pages = s.pages, seeded = true)
        val visual = HomeLayoutMath.visualPages(HomeResolve.layoutFor(layout, extras), HomeResolve.available(listOf("a", "b"), extras), cap, autoPlace = true)
        val keys = visual.flatMap { it.keys }
        assertTrue(ItemKeys.folder("f") in keys && ItemKeys.link("https://x.org") in keys && w in keys)
        // Apps in the folder are not auto-placed back onto a page.
        assertFalse("a" in keys || "b" in keys)
        val stored = HomeLayoutMath.toStored(visual, layout.pages, HomeResolve.preserve(layout.pages.flatMap { it.keys }.toSet(), extras))
        assertEquals(keys.toSet(), stored.flatMap { it.keys }.toSet())
    }

    @Test
    fun HOM_02_aRemovedNonAppItemIsNotPreservedBack() {
        val extras = HomeState(items = mapOf(ItemKeys.link("u") to ItemDef(ItemKeys.link("u"), ItemKind.Link, "u")))
        val base = setOf(ItemKeys.link("u"), "hidden-app")
        assertEquals(setOf("hidden-app"), HomeResolve.preserve(base, extras))
        // keys on hidden pages (one-page mode) are kept even when they are non-app items
        assertEquals(setOf("hidden-app", ItemKeys.link("u")), HomeResolve.preserve(base, extras, setOf(ItemKeys.link("u"))))
    }

    @Test
    fun HOM_02_extrasPersistThroughTheCodec() {
        var s = HomeState()
        s = HomeEdit.add(s, ItemDef(ItemKeys.shortcut("p", "id", 3), ItemKind.Shortcut, "Compose", "id"), 0, cap)
        s = HomeEdit.add(s, ItemDef(w, ItemKind.Widget, "Clock", "p/.W"), 0, cap, WidgetSpan(3, 1))
        val back = HomeStateCodec.decode(HomeStateCodec.encode(s))
        assertEquals(s.items, back.items)
        assertEquals(WidgetSpan(3, 1), back.spanOf(w))
        assertEquals(s.pages.map { it.keys }, back.pages.map { it.keys })
    }

    // ---- HOM-07: restore at startup
    @Test
    fun HOM_07_startupPlanKeepsRebindsAndPlaceholders() {
        val k1 = ItemKeys.widget(1); val k2 = ItemKeys.widget(2); val k3 = ItemKeys.widget(3)
        val s = HomeState(items = mapOf(
            k1 to ItemDef(k1, ItemKind.Widget, "a", "p/.A"),
            k2 to ItemDef(k2, ItemKind.Widget, "b", "p/.B"),
            k3 to ItemDef(k3, ItemKind.Widget, "c", "gone/.C"),
        ))
        val plan = WidgetStartup.plan(s, hostedIds = setOf(1), installedProviders = setOf("p/.A", "p/.B"))
        assertTrue(plan[0] is RestoreAction.Keep)
        assertTrue(plan[1] is RestoreAction.Rebind)
        assertTrue(plan[2] is RestoreAction.Placeholder)
    }

    @Test
    fun HOM_07_rekeyKeepsThePlaceSpanAndDefinition() {
        val old = ItemKeys.widget(2); val nw = ItemKeys.widget(9)
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1)))
        s = HomeEdit.add(s, ItemDef(old, ItemKind.Widget, "b", "p/.B"), 0, cap, WidgetSpan(2, 1))
        val r = HomeRekey.widget(s, old, nw)
        assertEquals(listOf("a", "b", nw), r.pages.single().keys)
        assertEquals(WidgetSpan(2, 1), r.spanOf(nw))
        assertEquals(nw, r.items.getValue(nw).key)
        assertNull(r.items[old])
        assertEquals(nw, WidgetStartup.saved(r.copy()).single().key)
    }

    // ---- HOM-03: icon sources
    @Test
    fun HOM_03_iconSpecRoundTripsAndRejectsGarbage() {
        val spec = IconSpec.encode("org.pack", "ic_phone")
        assertEquals("org.pack" to "ic_phone", IconSpec.decode(spec))
        assertNull(IconSpec.decode("nonsense"))
        assertNull(IconSpec.decode("pack:/x"))
        assertNull(IconSpec.decode("pack:p/"))
    }

    @Test
    fun HOM_03_signatureChangesWithTheSource() {
        val sigs = listOf(
            IconSource.Override("pack:a/b"), IconSource.Pack("a", "b"), IconSource.Themed, IconSource.Adaptive, IconSource.Legacy,
        ).map(IconSpec::signature)
        assertEquals(sigs.size, sigs.toSet().size)
    }

    @Test
    fun HOM_03_packListsItsDistinctDrawablesSorted() {
        val m = IconPackMap.parse("pk", """<resources><item component="ComponentInfo{a/a.A}" drawable="z"/><item component="ComponentInfo{b/b.B}" drawable="y"/><item component="ComponentInfo{c/c.C}" drawable="y"/></resources>""")
        assertEquals(listOf("y", "z"), m.drawables())
        assertNotNull(m.drawableFor("a", "a.A"))
    }

    // ---- HOM-05: hidden apps view
    @Test
    fun HOM_05_hiddenAppsViewListsOnlyHiddenInNameOrder() {
        val all = listOf("b" to "Beta", "a" to "alpha", "c" to "Gamma")
        val v = HiddenAppsView.hiddenOnly(all, { it.first }, { it.second }, setOf("b", "a"))
        assertEquals(listOf("a", "b"), v.map { it.first })
    }
}
