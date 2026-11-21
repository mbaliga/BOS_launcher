package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 3, home layout stream: stacks (HOM-14), span-aware drag (HOM-07), folder search provider (HOM-04), group items (WSP-05). */
class HomeLayoutW3Test {
    private val cap: (PageLayout) -> Int = { 12 }
    private fun w(id: Int) = ItemKeys.widget(id)
    private fun base(): HomeState {
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1L)))
        s = HomeEdit.add(s, ItemDef(w(1), ItemKind.Widget, "Clock"), 0, cap, WidgetSpan(2, 1))
        s = HomeEdit.add(s, ItemDef(w(2), ItemKind.Widget, "Weather"), 0, cap, WidgetSpan(1, 1))
        s = HomeEdit.add(s, ItemDef(w(3), ItemKind.Widget, "Notes"), 0, cap, WidgetSpan(1, 1))
        return s
    }

    // ---- HOM-14 -------------------------------------------------------------------------

    @Test
    fun HOM_14_stackingTakesOneSlotAndKeepsTheMembers() {
        val s = WidgetStacks.create(base(), w(1), w(2), "s1")
        val key = WidgetStacks.stackKey("s1")
        val keys = s.pages[0].keys
        assertTrue(key in keys)
        assertFalse(w(1) in keys || w(2) in keys)
        assertEquals(listOf(w(1), w(2)), WidgetStacks.stackAt(s, key)!!.keys)
        assertEquals(WidgetSpan(2, 1), s.spanOf(key))
        assertEquals(setOf(w(1), w(2)), WidgetStacks.members(s))
        assertEquals(ItemKind.Widget, ItemKeys.kind(key))
    }

    @Test
    fun HOM_14_membersAreNeverAutoPlacedBackOntoAPage() {
        val s = WidgetStacks.create(base(), w(1), w(2), "s1")
        val avail = HomeResolve.available(listOf("a", "b"), s)
        assertFalse(w(1) in avail || w(2) in avail)
        assertTrue(WidgetStacks.stackKey("s1") in avail && w(3) in avail)
        val held = HomeResolve.layoutFor(HomeLayout(s.pages, emptySet()), s).removed
        assertTrue(w(1) in held && w(2) in held)
    }

    @Test
    fun HOM_14_addActiveAndTakeOutDissolvesALoneStack() {
        var s = WidgetStacks.create(base(), w(1), w(2), "s1")
        val key = WidgetStacks.stackKey("s1")
        s = WidgetStacks.add(s, key, w(3))
        assertEquals(listOf(w(1), w(2), w(3)), WidgetStacks.stackAt(s, key)!!.keys)
        assertEquals(2, WidgetStacks.stackAt(s, key)!!.active)
        s = WidgetStacks.setActive(s, key, WidgetStacks.stackAt(s, key)!!.select(0))
        assertEquals(0, WidgetStacks.stackAt(s, key)!!.active)
        s = WidgetStacks.takeOut(s, key, w(3), 0, cap)
        assertTrue(w(3) in s.pages[0].keys)
        assertEquals(listOf(w(1), w(2)), WidgetStacks.stackAt(s, key)!!.keys)
        s = WidgetStacks.takeOut(s, key, w(2), 0, cap)
        // One widget left: the stack dissolves into it, at the stack's place.
        assertNull(WidgetStacks.stackAt(s, key))
        assertTrue(w(1) in s.pages[0].keys && w(2) in s.pages[0].keys && key !in s.pages[0].keys)
        assertTrue(WidgetStacks.members(s).isEmpty())
    }

    @Test
    fun HOM_14_stackSurvivesTheCodecAndAWidgetRebind() {
        var s = WidgetStacks.create(base(), w(1), w(2), "s1")
        s = WidgetStacks.setActive(s, WidgetStacks.stackKey("s1"), WidgetStack(listOf(w(1), w(2)), 0))
        val back = HomeStateCodec.decode(HomeStateCodec.encode(s))
        assertEquals(WidgetStacks.stackAt(s, WidgetStacks.stackKey("s1")), WidgetStacks.stackAt(back, WidgetStacks.stackKey("s1")))
        val re = HomeRekey.widget(back, w(2), w(9))
        assertEquals(listOf(w(1), w(9)), WidgetStacks.stackAt(re, WidgetStacks.stackKey("s1"))!!.keys)
        assertTrue(w(9) in WidgetStacks.members(re))
    }

    @Test
    fun HOM_14_removingAStackRemovesEveryWidgetInIt() {
        val s = WidgetStacks.create(base(), w(1), w(2), "s1")
        val (n, gone) = WidgetStacks.removeAll(s, WidgetStacks.stackKey("s1"), cap)
        assertEquals(listOf(w(1), w(2)), gone)
        assertTrue(n.items.keys.none { it == w(1) || it == w(2) || WidgetStacks.isStack(it) })
        assertTrue(n.widgets.keys.none { it == w(1) || it == w(2) || WidgetStacks.isStack(it) })
        assertEquals(listOf("a", "b", w(3)), n.pages[0].keys)
    }

    @Test
    fun HOM_14_onlyWidgetsOnAPageCanBeStacked() {
        val s = base()
        assertEquals(s, WidgetStacks.create(s, "a", w(1), "s1"))
        assertEquals(s, WidgetStacks.create(s, w(1), w(1), "s1"))
        assertEquals(s, WidgetStacks.create(s, w(1), w(99), "s1"))
    }

    // ---- HOM-07 -------------------------------------------------------------------------

    @Test
    fun HOM_07_spanAwareDropIndexAnchorsTheWholeFootprintOnTheGrid() {
        val span = { k: String -> if (k == "w") WidgetSpan(2, 2) else WidgetSpan(1, 1) }
        val keys = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        for (cell in 0 until 16) {
            val i = SpanGrid.insertIndexForDrag(keys + "w", "w", span, 4, 4, cell)
            // Index grows with the target cell (row-major), and the widget always fits on the page.
            val trial = keys.toMutableList().also { it.add(i, "w") }
            val packed = TilePacker.pack(trial, span, 4, 4)
            assertTrue("cell $cell index $i", i in 0..keys.size)
            assertTrue(packed.placed.any { it.key == "w" })
            val anchorCol = (cell % 4).coerceAtMost(2); val anchorRow = (cell / 4).coerceAtMost(2)
            assertEquals(SpanGrid.insertIndex(keys, "w", span, 4, 4, anchorRow * 4 + anchorCol), i)
        }
        // A one-cell item behaves exactly as before.
        for (cell in 0 until 16) assertEquals(SpanGrid.insertIndex(keys, "x", { WidgetSpan(1, 1) }, 4, 4, cell), SpanGrid.insertIndexForDrag(keys, "x", { WidgetSpan(1, 1) }, 4, 4, cell))
    }

    @Test
    fun HOM_07_aLoneSpanningWidgetStillCountsItsSpanWhenItIsTheOnlySpanningItem() {
        // Before: only the page without the dragged widget was checked for spans, so a lone widget used the plain slot index.
        val span = { k: String -> if (k == "w") WidgetSpan(2, 1) else WidgetSpan(1, 1) }
        val rest = listOf("a", "b", "c", "d")
        // Cell 3 (row 0, last column of 4): a 2-wide widget cannot start there; its anchor is pulled back to column 2.
        val i = SpanGrid.insertIndexForDrag(rest + "w", "w", span, 4, 3, 3)
        assertEquals(2, i)
        val p = TilePacker.pack(rest.toMutableList().also { it.add(i, "w") }, span, 4, 3).placed.first { it.key == "w" }
        assertEquals(2, p.col); assertEquals(0, p.row)
    }

    // ---- HOM-04 -------------------------------------------------------------------------

    @Test
    fun HOM_04_radarProviderListsFoldersByNameBestFirst() = runBlocking {
        val folders = listOf(Folder("f1", "Work tools", listOf("a", "b")), Folder("f2", "Tools", listOf("c")), Folder("f3", "Games", listOf("d")))
        val p = FolderSearchProvider({ folders }, { "intent:${it.id}" })
        val hits = p.search("tools", 10)
        assertEquals(listOf("folder:f2", "folder:f1"), hits.map { it.id })
        assertEquals("intent:f2", hits[0].intentUri)
        assertEquals(com.bos.sphere.core.data.HitKind.Folder, hits[0].kind)
        assertTrue(p.search("", 10).isEmpty())
        assertTrue(p.search("zzz", 10).isEmpty())
        assertEquals(1, p.search("tools", 1).size)
    }

    @Test
    fun HOM_04_providerReadsFoldersAtQueryTimeAndNeverThrows() = runBlocking {
        var folders = listOf(Folder("f1", "Music", listOf("a")))
        val p = FolderSearchProvider({ folders }, { "i" })
        assertEquals(1, p.search("mus", 5).size)
        folders = emptyList()
        assertTrue(p.search("mus", 5).isEmpty())
        val boom = FolderSearchProvider({ error("store closed") }, { "i" })
        assertTrue(boom.search("x", 5).isEmpty())
    }

    // ---- WSP-05 -------------------------------------------------------------------------

    @Test
    fun WSP_05_aGroupIsAPageGridItemThatPersists() {
        val key = ItemKeys.group("g1")
        assertEquals(ItemKind.Group, ItemKeys.kind(key))
        assertEquals("g1", ItemKeys.groupId(key))
        var s = HomeState(pages = listOf(HomePage(listOf("a"), id = 1L)))
        s = HomeEdit.add(s, ItemDef(key, ItemKind.Group, "Work", "g1"), 0, cap)
        assertEquals(listOf("a", key), s.pages[0].keys)
        assertTrue(key in s.nonAppKeys())
        val back = HomeStateCodec.decode(HomeStateCodec.encode(s))
        assertEquals(ItemKind.Group, back.items[key]!!.kind)
        assertEquals("Work", back.items[key]!!.label)
        // It resolves like any other extra: available, and removable.
        assertTrue(key in HomeResolve.available(listOf("a"), s))
        assertNotNull(HomeEdit.remove(s, key, cap).pages[0].keys.takeIf { key !in it })
    }
}
