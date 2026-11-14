package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout

/**
 * HOM-04 / HOM-12: the edit operations as pure functions from one [HomeState] to the next. The
 * input stream calls these for drag and keyboard moves; edit mode wraps them in an [EditSession]
 * so each is one undo step.
 */
object HomeEdit {

    private fun find(state: HomeState, key: String): Int = HomeLayoutMath.pageOf(state.pages, key)

    private fun withoutTopLevel(state: HomeState, key: String, cap: (PageLayout) -> Int): HomeState =
        state.copy(
            pages = if (find(state, key) >= 0) HomeLayoutMath.remove(state.pages, key) else state.pages,
            dock = state.dock - key,
        )

    // ---- placement ----------------------------------------------------------------------

    /** Adds a new item (app, shortcut, widget, link) to the end of [page], defining it when it carries data. */
    fun add(state: HomeState, def: ItemDef, page: Int, cap: (PageLayout) -> Int, span: WidgetSpan? = null): HomeState {
        if (def.key in state.allKeys()) return state
        val items = if (def.kind == ItemKind.App || def.kind == ItemKind.Tile) state.items else state.items + (def.key to def)
        val widgets = if (span != null) state.widgets + (def.key to span) else state.widgets
        return state.copy(pages = HomeLayoutMath.add(state.pages, def.key, page, cap), items = items, widgets = widgets)
    }

    /** Moves a top-level item (or one taken out of a folder or the dock) to ([page], [index]) on the pages. */
    fun move(state: HomeState, key: String, page: Int, index: Int, cap: (PageLayout) -> Int): HomeState {
        if (find(state, key) >= 0) return state.copy(pages = HomeLayoutMath.move(state.pages, key, page, index, cap))
        val inFolder = state.folders.values.firstOrNull { key in it.keys }
        val base = if (inFolder != null) removeFromFolderKeys(state, inFolder.id, key) else state.copy(dock = state.dock - key)
        val placed = HomeLayoutMath.add(base.pages, key, page, cap)
        return base.copy(pages = HomeLayoutMath.move(placed, key, page.coerceIn(0, placed.lastIndex), index, cap))
    }

    /** Reorders inside the page where [key] lives. */
    fun reorderOnPage(state: HomeState, key: String, toIndex: Int, cap: (PageLayout) -> Int): HomeState {
        val p = find(state, key)
        if (p < 0) return state
        return state.copy(pages = HomeLayoutMath.move(state.pages, key, p, toIndex, cap))
    }

    fun remove(state: HomeState, key: String, cap: (PageLayout) -> Int): HomeState {
        val inFolder = state.folders.values.firstOrNull { key in it.keys }
        val s = if (inFolder != null) removeFromFolderKeys(state, inFolder.id, key) else withoutTopLevel(state, key, cap)
        return s.copy(items = s.items - key, widgets = s.widgets - key)
    }

    // ---- dock (HOM-02) -----------------------------------------------------------------

    fun moveToDock(state: HomeState, key: String, index: Int, capacity: Int, cap: (PageLayout) -> Int): HomeState {
        val fromDock = key in state.dock
        if (!fromDock && state.dock.size >= capacity) return state
        val inFolder = state.folders.values.firstOrNull { key in it.keys }
        val base = if (inFolder != null) removeFromFolderKeys(state, inFolder.id, key) else withoutTopLevel(state, key, cap)
        val list = base.dock.toMutableList()
        list.add(index.coerceIn(0, list.size), key)
        return base.copy(dock = list)
    }

    // ---- folders (HOM-04) --------------------------------------------------------------

    /**
     * Dropping [dragKey] on [targetKey]. Onto a folder: files it in. Onto another app / shortcut /
     * link: both go into a new folder where the target was. Widgets, tiles and folder-into-folder
     * drops do nothing (returns the state unchanged).
     */
    fun dropOnto(state: HomeState, dragKey: String, targetKey: String, newId: String, defaultName: String, cap: (PageLayout) -> Int): HomeState {
        if (dragKey == targetKey) return state
        val dragKind = ItemKeys.kind(dragKey)
        val targetKind = ItemKeys.kind(targetKey)
        if (dragKind in NOT_FILEABLE) return state
        return when (targetKind) {
            ItemKind.Folder -> fileInto(state, ItemKeys.folderId(targetKey) ?: return state, dragKey, cap)
            ItemKind.App, ItemKind.Shortcut, ItemKind.Link -> createFolder(state, listOf(targetKey, dragKey), newId, defaultName, cap)
            else -> state
        }
    }

    /** Creates a folder holding [keys] where the first one that is on a page sits (the menu path and the drop path). */
    fun createFolder(state: HomeState, keys: List<String>, id: String, name: String, cap: (PageLayout) -> Int): HomeState {
        val fileable = keys.distinct().filter { ItemKeys.kind(it) !in NOT_FILEABLE }
        if (fileable.isEmpty() || id in state.folders) return state
        val folder = Folder(id, name, fileable)
        var s = state
        for (k in fileable) {
            val other = s.folders.values.firstOrNull { k in it.keys }
            if (other != null) s = removeFromFolderKeys(s, other.id, k)
        }
        val pageAnchor = fileable.firstOrNull { k -> s.pages.any { k in it.keys } }
        val dockAnchor = if (pageAnchor == null) fileable.firstOrNull { it in s.dock } else null
        val others = fileable.filter { it != pageAnchor && it != dockAnchor }.toSet()
        s = s.copy(dock = s.dock.map { if (it == dockAnchor) folder.key else it }.filter { it !in others })
        s = when {
            pageAnchor != null ->
                s.copy(pages = s.pages.map { p -> p.copy(keys = p.keys.mapNotNull { if (it == pageAnchor) folder.key else if (it in others) null else it }) })
            dockAnchor != null -> s
            else -> s.copy(pages = HomeLayoutMath.add(s.pages, folder.key, 0, cap))
        }
        return s.copy(pages = s.pages.map { p -> p.copy(keys = p.keys.filter { it !in others }) }, folders = s.folders + (id to folder))
    }

    fun fileInto(state: HomeState, folderId: String, key: String, cap: (PageLayout) -> Int): HomeState {
        val folder = state.folders[folderId] ?: return state
        if (key in folder.keys || ItemKeys.kind(key) in NOT_FILEABLE) return state
        var s = state
        val other = s.folders.values.firstOrNull { key in it.keys }
        s = if (other != null) removeFromFolderKeys(s, other.id, key) else withoutTopLevel(s, key, cap)
        val f = s.folders[folderId] ?: return state
        return s.copy(folders = s.folders + (folderId to f.copy(keys = f.keys + key)))
    }

    fun renameFolder(state: HomeState, id: String, name: String): HomeState {
        val f = state.folders[id] ?: return state
        val n = name.trim().ifEmpty { f.name }
        return state.copy(folders = state.folders + (id to f.copy(name = n)))
    }

    fun reorderInFolder(state: HomeState, id: String, key: String, toIndex: Int): HomeState {
        val f = state.folders[id] ?: return state
        if (key !in f.keys) return state
        val list = f.keys.toMutableList().also { it.remove(key) }
        list.add(toIndex.coerceIn(0, list.size), key)
        return state.copy(folders = state.folders + (id to f.copy(keys = list)))
    }

    /** Takes [key] out of the folder and puts it on [page] at [index]. */
    fun takeOut(state: HomeState, folderId: String, key: String, page: Int, index: Int, cap: (PageLayout) -> Int): HomeState {
        val f = state.folders[folderId] ?: return state
        if (key !in f.keys) return state
        val s = removeFromFolderKeys(state, folderId, key)
        val pages = HomeLayoutMath.add(s.pages, key, page, cap)
        return s.copy(pages = HomeLayoutMath.move(pages, key, page.coerceIn(0, pages.lastIndex), index, cap))
    }

    /** Removes the folder; its contents take its place on the page (or in the dock). */
    fun dissolve(state: HomeState, folderId: String): HomeState {
        val f = state.folders[folderId] ?: return state
        return state.copy(
            pages = state.pages.map { p -> p.copy(keys = p.keys.flatMap { if (it == f.key) f.keys else listOf(it) }) },
            dock = state.dock.flatMap { if (it == f.key) f.keys else listOf(it) },
            folders = state.folders - folderId,
        )
    }

    /**
     * Drops [key] from the folder. A folder left with one item is dissolved onto the page (unless
     * [dissolveSingle] is false while a multi-step change is in progress); an empty one is deleted.
     */
    private fun removeFromFolderKeys(state: HomeState, folderId: String, key: String, dissolveSingle: Boolean = true): HomeState {
        val f = state.folders[folderId] ?: return state
        val left = f.keys - key
        return when {
            left.isEmpty() -> deleteFolderSlot(state, f)
            left.size == 1 && dissolveSingle -> {
                // Replace the folder slot with its last child.
                val only = left.first()
                val pages = state.pages.map { p -> p.copy(keys = p.keys.map { if (it == f.key) only else it }) }
                state.copy(pages = pages, dock = state.dock.map { if (it == f.key) only else it }, folders = state.folders - folderId)
            }
            else -> state.copy(folders = state.folders + (folderId to f.copy(keys = left)))
        }
    }

    private fun deleteFolderSlot(state: HomeState, f: Folder): HomeState =
        state.copy(
            pages = if (find(state, f.key) >= 0) HomeLayoutMath.remove(state.pages, f.key) else state.pages,
            dock = state.dock - f.key,
            folders = state.folders - f.id,
        )

    // ---- widgets (HOM-07 / HOM-12) ------------------------------------------------------

    /** Grows or shrinks a widget's span by the given steps, clamped to [min, max] and the grid. */
    fun resizeWidget(
        state: HomeState, key: String, dCols: Int, dRows: Int,
        min: WidgetSpan = WidgetSpan(1, 1), max: WidgetSpan, gridCols: Int, gridRows: Int,
    ): HomeState {
        val cur = state.widgets[key] ?: return state
        val c = (cur.cols + dCols).coerceIn(min.cols, minOf(max.cols, gridCols.coerceAtLeast(1)))
        val r = (cur.rows + dRows).coerceIn(min.rows, minOf(max.rows, gridRows.coerceAtLeast(1)))
        if (c == cur.cols && r == cur.rows) return state
        return state.copy(widgets = state.widgets + (key to WidgetSpan(c, r)))
    }

    // ---- package changes (HOM-10) -------------------------------------------------------

    /**
     * Drops every app key that is no longer installed, from pages, dock and folders, so a removal
     * leaves no gap and no dangling reference. Non-app items (shortcuts, folders, widgets, links,
     * tiles) are never touched here. Folders left with a single item dissolve; empty ones go.
     */
    fun pruneMissing(state: HomeState, liveApps: Set<String>, cap: (PageLayout) -> Int): HomeState {
        fun gone(k: String) = ItemKeys.kind(k) == ItemKind.App && k !in liveApps
        var s = state
        for (f in state.folders.values) for (k in f.keys.filter(::gone)) {
            val cur = s.folders[f.id] ?: break
            if (k in cur.keys) s = removeFromFolderKeys(s, f.id, k)
        }
        for (k in s.pages.flatMap { it.keys }.filter(::gone)) s = s.copy(pages = HomeLayoutMath.remove(s.pages, k))
        return s.copy(dock = s.dock.filterNot(::gone))
    }

    private val NOT_FILEABLE = setOf(ItemKind.Folder, ItemKind.Widget, ItemKind.Tile)
}
