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

    @Test
    fun INP_11_aLinkDroppedOnAFolderTileIsDefinedAndFiledInsideItWithoutAPageCell() {
        val link = ItemDef(ItemKeys.link("https://example.com"), ItemKind.Link, "example.com", "https://example.com")
        val s0 = HomeEdit.createFolder(state("a", "b", "c"), listOf("a", "b"), "f", "F", cap)
        val s1 = HomeEdit.addIntoFolder(s0, link, "f", 0, cap)
        assertEquals(listOf("a", "b", link.key), s1.folders.getValue("f").keys)
        assertEquals(link, s1.items[link.key])
        assertTrue("the link keeps no cell on the page", s1.pages.flatMap { it.keys }.none { it == link.key })
        assertEquals("the other items did not move", s0.pages, s1.pages)
    }

    @Test
    fun INP_11_aLinkAlreadyOnThePageMovesIntoTheFolderAndAnUnknownFolderChangesNothing() {
        val link = ItemDef(ItemKeys.link("https://example.com"), ItemKind.Link, "example.com", "https://example.com")
        val onPage = HomeEdit.add(state("a", "b"), link, 0, cap)
        val withFolder = HomeEdit.createFolder(onPage, listOf("a", "b"), "f", "F", cap)
        val moved = HomeEdit.addIntoFolder(withFolder, link, "f", 0, cap)
        assertTrue(link.key in moved.folders.getValue("f").keys)
        assertTrue(moved.pages.flatMap { it.keys }.none { it == link.key })
        assertEquals(withFolder, HomeEdit.addIntoFolder(withFolder, link, "nope", 0, cap))
    }

    @Test
    fun INP_11_anAppDroppedOnAFolderTileIsFiledByTheSameEditADragUses() {
        val s0 = HomeEdit.createFolder(state("a", "b", "c"), listOf("a", "b"), "f", "F", cap)
        val s1 = HomeEdit.dropOnto(s0, "c", ItemKeys.folder("f"), "new", "Folder", cap)
        assertEquals(listOf("a", "b", "c"), s1.folders.getValue("f").keys)
        // an app that is not on the home yet (dropped from the drawer or Radar) files in as well
        val s2 = HomeEdit.dropOnto(s0, "z", ItemKeys.folder("f"), "new", "Folder", cap)
        assertEquals(listOf("a", "b", "z"), s2.folders.getValue("f").keys)
    }
}

class EditGraveyardTest {
    private val cap: (com.bos.sphere.core.data.PageLayout) -> Int = { 12 }
    private fun base(): HomeState {
        var s = HomeState(pages = listOf(HomePage(listOf("a", "b"), id = 1L)))
        s = HomeEdit.add(s, ItemDef(ItemKeys.link("https://x"), ItemKind.Link, "X", "https://x"), 0, cap)
        s = HomeEdit.add(s, ItemDef(ItemKeys.widget(3), ItemKind.Widget, "Clock"), 0, cap, WidgetSpan(2, 1))
        return s
    }

    @Test
    fun HOM_12_undoOfARemovedLinkOrWidgetBringsItsDefinitionAndSizeBack() {
        val before = base()
        val g = EditGraveyard()
        var s = before
        for (k in listOf(ItemKeys.link("https://x"), ItemKeys.widget(3))) { g.stash(s, listOf(k)); s = HomeEdit.remove(s, k, cap) }
        assertTrue(ItemKeys.widget(3) !in s.items && ItemKeys.widget(3) !in s.widgets)
        // The undo stack brings the pages back, nothing else.
        val undone = s.copy(pages = before.pages)
        val revived = g.revive(undone)
        assertEquals(before.items, revived.items)
        assertEquals(before.widgets, revived.widgets)
    }

    @Test
    fun HOM_12_whatStaysRemovedIsSettledAtTheEndAndTheWidgetIsDestroyedOnlyThen() {
        val before = base()
        val g = EditGraveyard()
        g.stash(before, listOf(ItemKeys.widget(3)))
        val removed = HomeEdit.remove(before, ItemKeys.widget(3), cap)
        val done = g.settle(removed)
        assertEquals(listOf(ItemKeys.widget(3)), done.widgetKeys)
        assertTrue(g.isEmpty)
        // A widget that came back by undo is kept and not destroyed.
        val g2 = EditGraveyard()
        g2.stash(before, listOf(ItemKeys.widget(3)))
        val kept = g2.settle(before)
        assertTrue(kept.widgetKeys.isEmpty())
        assertEquals(before.items, kept.state.items)
    }

    @Test
    fun HOM_12_redoAfterUndoLeavesNoOrphanDefinitionAfterSettling() {
        val before = base()
        val g = EditGraveyard()
        g.stash(before, listOf(ItemKeys.link("https://x")))
        val removed = HomeEdit.remove(before, ItemKeys.link("https://x"), cap)
        val redone = g.revive(removed.copy(pages = removed.pages)) // redo: the key is off the pages, nothing to revive
        assertTrue(ItemKeys.link("https://x") !in redone.items)
        assertTrue(g.settle(redone).state.items.keys.none { it == ItemKeys.link("https://x") })
    }

    @Test
    fun HOM_12_undoOfFolderCreationAndOfDissolveBringsTheFolderStateBackWithThePages() {
        val start = HomeState(pages = listOf(HomePage(listOf("a", "b", "c"), id = 1L)))
        val g = EditGraveyard()
        g.record(start.pages, start)
        val made = HomeEdit.createFolder(start, listOf("a", "b"), "f", "F", cap)
        g.record(made.pages, made)
        assertTrue("f" in made.folders)
        // Undo of the creation: the pages come back, the folder must go with them.
        val undone = g.restore(made.pages, start.pages, made.copy(pages = start.pages))
        assertTrue(undone.folders.isEmpty())
        // Redo: the folder returns.
        val redone = g.restore(start.pages, made.pages, undone.copy(pages = made.pages))
        assertEquals(made.folders, redone.folders)
        // Dissolve, then undo it: the folder returns with its members.
        val dissolved = HomeEdit.dissolve(made, "f")
        g.record(dissolved.pages, dissolved)
        assertTrue(dissolved.folders.isEmpty())
        val back = g.restore(dissolved.pages, made.pages, dissolved.copy(pages = made.pages))
        assertEquals(made.folders, back.folders)
    }

    @Test
    fun HOM_12_undoLeavesAFolderRenameMadeMeanwhileAlone() {
        val start = HomeState(pages = listOf(HomePage(listOf("a", "b", "c", "d"), id = 1L)))
        val g = EditGraveyard()
        val made = HomeEdit.createFolder(start, listOf("a", "b"), "f", "F", cap)
        g.record(start.pages, start); g.record(made.pages, made)
        val next = HomeEdit.createFolder(made, listOf("c", "d"), "g", "G", cap)
        g.record(next.pages, next)
        val renamed = next.copy(folders = next.folders + ("f" to next.folders.getValue("f").copy(name = "Renamed")))
        val undone = g.restore(next.pages, made.pages, renamed.copy(pages = made.pages))
        assertEquals("Renamed", undone.folders.getValue("f").name)
        assertTrue("g" !in undone.folders)
    }
}
