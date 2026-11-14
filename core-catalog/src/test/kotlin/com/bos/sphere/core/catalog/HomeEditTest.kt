package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditTest {
    private val cap: (PageLayout) -> Int = { 12 }
    private fun state(vararg keys: String) = HomeState(pages = listOf(HomePage(keys.toList(), id = 1L)))

    @Test
    fun HOM_02_itemKindsFromKeys() {
        assertEquals(ItemKind.App, ItemKeys.kind("com.a/.Main#0"))
        assertEquals(ItemKind.Shortcut, ItemKeys.kind(ItemKeys.shortcut("p", "s", 0)))
        assertEquals(ItemKind.Folder, ItemKeys.kind(ItemKeys.folder("f1")))
        assertEquals(ItemKind.Widget, ItemKeys.kind(ItemKeys.widget(7)))
        assertEquals(ItemKind.Link, ItemKeys.kind(ItemKeys.link("https://x")))
        assertEquals(ItemKind.Tile, ItemKeys.kind("tile:clock"))
        assertEquals(7, ItemKeys.widgetId(ItemKeys.widget(7)))
    }

    @Test
    fun HOM_02_everyKindCanLiveOnHomeAndInTheDock() {
        var s = state("a")
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("file:///x.pdf"), ItemKind.Link, "x.pdf", "file:///x.pdf", isFile = true), 0, cap)
        s = HomeEdit.add(s, ItemDef(ItemKeys.shortcut("p", "s", 0), ItemKind.Shortcut, "Compose"), 0, cap)
        s = HomeEdit.add(s, ItemDef(ItemKeys.widget(3), ItemKind.Widget, "Clock"), 0, cap, WidgetSpan(2, 1))
        s = HomeEdit.createFolder(s, listOf("a", "b"), "f", "F", cap)
        assertTrue(ItemKeys.folder("f") in s.topLevelKeys())
        val dockKeys = listOf(ItemKeys.link("file:///x.pdf"), ItemKeys.shortcut("p", "s", 0), ItemKeys.widget(3), ItemKeys.folder("f"))
        for (k in dockKeys) s = HomeEdit.moveToDock(s, k, 99, 8, cap)
        assertEquals(dockKeys, s.dock)
        assertTrue(s.pages.flatMap { it.keys }.none { it in dockKeys })
    }

    @Test
    fun HOM_02_stateSurvivesEncodeDecode() {
        var s = state("a", "b")
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("https://x\ty"), ItemKind.Link, "Link\nname", "https://x\ty"), 0, cap)
        s = HomeEdit.add(s, ItemDef(ItemKeys.widget(3), ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 2))
        s = HomeEdit.createFolder(s, listOf("a", "b"), "f", "My folder", cap)
        s = HomeEdit.moveToDock(s, ItemKeys.widget(3), 0, 5, cap)
        assertEquals(s, HomeStateCodec.decode(HomeStateCodec.encode(s)))
    }

    @Test
    fun HOM_04_dropOneItemOnAnotherCreatesFolderInPlace() {
        val s = HomeEdit.dropOnto(state("a", "b", "c"), "c", "a", "f1", "Folder", cap)
        assertEquals(listOf(ItemKeys.folder("f1"), "b"), s.pages[0].keys)
        assertEquals(listOf("a", "c"), s.folders["f1"]!!.keys)
    }

    @Test
    fun HOM_04_dropOnFolderFilesItAndMenuCreateWorks() {
        var s = HomeEdit.createFolder(state("a", "b", "c"), listOf("a", "b"), "f1", "Work", cap)
        assertEquals(listOf(ItemKeys.folder("f1"), "c"), s.pages[0].keys)
        s = HomeEdit.dropOnto(s, "c", ItemKeys.folder("f1"), "f2", "Folder", cap)
        assertEquals(listOf("a", "b", "c"), s.folders["f1"]!!.keys)
        assertEquals(listOf(ItemKeys.folder("f1")), s.pages[0].keys)
    }

    @Test
    fun HOM_04_foldersDoNotNestAndWidgetsAreNotFiled() {
        val s = HomeEdit.createFolder(state("a", "b", ItemKeys.widget(1)), listOf("a", "b"), "f1", "F", cap)
        assertEquals(s, HomeEdit.dropOnto(s, ItemKeys.folder("f1"), ItemKeys.folder("f1"), "x", "F", cap))
        val s2 = HomeEdit.createFolder(s, listOf("c"), "f2", "G", cap)
        assertEquals(s2.folders.keys, setOf("f1", "f2"))
        assertEquals(s, HomeEdit.dropOnto(s, ItemKeys.widget(1), ItemKeys.folder("f1"), "x", "F", cap))
    }

    @Test
    fun HOM_04_renameReorderAndTakeOut() {
        var s = HomeEdit.createFolder(state("a", "b", "c", "d"), listOf("a", "b", "c"), "f1", "F", cap)
        s = HomeEdit.renameFolder(s, "f1", "  Games ")
        assertEquals("Games", s.folders["f1"]!!.name)
        s = HomeEdit.renameFolder(s, "f1", "   ")
        assertEquals("Games", s.folders["f1"]!!.name)
        s = HomeEdit.reorderInFolder(s, "f1", "c", 0)
        assertEquals(listOf("c", "a", "b"), s.folders["f1"]!!.keys)
        s = HomeEdit.takeOut(s, "f1", "a", 0, 0, cap)
        assertEquals(listOf("c", "b"), s.folders["f1"]!!.keys)
        assertEquals("a", s.pages[0].keys[0])
    }

    @Test
    fun HOM_04_folderWithOneItemLeftDissolvesAndEmptyOneIsDeleted() {
        var s = HomeEdit.createFolder(state("a", "b", "c"), listOf("a", "b"), "f1", "F", cap)
        s = HomeEdit.takeOut(s, "f1", "a", 0, 5, cap)
        assertTrue(s.folders.isEmpty())
        assertEquals(setOf("a", "b", "c"), s.pages.flatMap { it.keys }.toSet())
        assertFalse(s.pages.flatMap { it.keys }.any { ItemKeys.kind(it) == ItemKind.Folder })
    }

    @Test
    fun HOM_04_dissolveKeepsChildrenInPlace() {
        val s = HomeEdit.createFolder(state("x", "a", "b", "y"), listOf("a", "b"), "f1", "F", cap)
        val d = HomeEdit.dissolve(s, "f1")
        assertEquals(listOf("x", "a", "b", "y"), d.pages[0].keys)
    }

    @Test
    fun HOM_04_foldersSearchableByName() {
        val folders = listOf(Folder("1", "Work apps", listOf("a")), Folder("2", "Games", listOf("b")), Folder("3", "Café", listOf("c")))
        assertEquals(listOf("1"), FolderSearch.search(folders, "wor").map { it.folder.id })
        assertEquals(listOf("3"), FolderSearch.search(folders, "cafe").map { it.folder.id })
        assertEquals(listOf("1"), FolderSearch.search(folders, "apps").map { it.folder.id })
        assertTrue(FolderSearch.search(folders, "zzz").isEmpty())
        assertTrue(FolderSearch.search(folders, "").isEmpty())
    }

    @Test
    fun HOM_08_widgetResizeClampsToGridAndProvider() {
        var s = HomeEdit.add(state("a"), ItemDef(ItemKeys.widget(1), ItemKind.Widget, "W"), 0, cap, WidgetSpan(2, 2))
        val k = ItemKeys.widget(1)
        s = HomeEdit.resizeWidget(s, k, 5, 5, max = WidgetSpan(4, 3), gridCols = 4, gridRows = 6)
        assertEquals(WidgetSpan(4, 3), s.widgets[k])
        s = HomeEdit.resizeWidget(s, k, -9, -9, min = WidgetSpan(2, 1), max = WidgetSpan(4, 3), gridCols = 4, gridRows = 6)
        assertEquals(WidgetSpan(2, 1), s.widgets[k])
    }

    @Test
    fun HOM_10_removedAppsLeaveNoGapsInPagesFoldersOrDock() {
        var s = HomeEdit.createFolder(state("a", "b", "c", "d"), listOf("a", "b"), "f1", "F", cap)
        s = HomeEdit.moveToDock(s, "d", 0, 5, cap)
        val after = HomeEdit.pruneMissing(s, setOf("b", "c"), cap)
        // "a" left the folder (one child left, so it dissolved into "b"), "d" left the dock.
        assertEquals(listOf("b", "c"), after.pages[0].keys)
        assertTrue(after.dock.isEmpty())
        assertTrue(after.folders.isEmpty())
        val none = HomeEdit.pruneMissing(after, emptySet(), cap)
        assertTrue(none.pages.all { it.keys.isEmpty() })
    }

    @Test
    fun HOM_10_pruneKeepsNonAppItems() {
        val s = HomeEdit.add(state("a"), ItemDef(ItemKeys.shortcut("p", "s", 0), ItemKind.Shortcut, "S"), 0, cap)
        val after = HomeEdit.pruneMissing(s, emptySet(), cap)
        assertEquals(listOf(ItemKeys.shortcut("p", "s", 0)), after.pages[0].keys)
    }

    @Test
    fun HOM_12_moveAcrossPages() {
        val s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1), HomePage(listOf("c"), id = 2)))
        val m = HomeEdit.move(s, "a", 1, 0, cap)
        assertEquals(listOf("b"), m.pages[0].keys)
        assertEquals(listOf("a", "c"), m.pages[1].keys)
    }

    @Test
    fun HOM_12_moveFromFolderToPage() {
        val s = HomeEdit.createFolder(state("a", "b", "c", "d"), listOf("a", "b", "c"), "f1", "F", cap)
        val m = HomeEdit.move(s, "b", 0, 0, cap)
        assertEquals("b", m.pages[0].keys[0])
        assertEquals(listOf("a", "c"), m.folders["f1"]!!.keys)
        assertNull(m.folders["zz"])
    }
}
