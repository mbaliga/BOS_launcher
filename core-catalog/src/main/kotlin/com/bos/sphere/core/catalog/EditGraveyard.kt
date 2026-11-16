package com.bos.sphere.core.catalog

/**
 * HOM-12: what an edit-mode removal takes with it, kept until edit mode ends so that undo can bring it back whole. Removing a
 * link, shortcut or widget drops its definition and size from the extras as well as its key from the pages; the undo stack
 * only restores the pages, so without this a restored key points at nothing and the next reconcile prunes it.
 */
class EditGraveyard {
    private val defs = LinkedHashMap<String, ItemDef>()
    private val spans = LinkedHashMap<String, WidgetSpan>()

    val isEmpty: Boolean get() = defs.isEmpty() && spans.isEmpty()

    private class Extras(val items: Map<String, ItemDef>, val folders: Map<String, Folder>, val widgets: Map<String, WidgetSpan>)

    /** The extras as they were when each page arrangement of this edit session was current (bounded). */
    private val snaps = LinkedHashMap<List<com.bos.sphere.core.data.HomePage>, Extras>()

    /** Notes the extras that go with [pages]; the host calls it whenever the arrangement is observed. */
    fun record(pages: List<com.bos.sphere.core.data.HomePage>, state: HomeState) {
        snaps.remove(pages)
        snaps[pages] = Extras(state.items, state.folders, state.widgets)
        while (snaps.size > MAX_SNAPSHOTS) snaps.remove(snaps.keys.first())
    }

    /**
     * After an undo or redo took the arrangement from [before] to [after]: for what came back (or went away) at the top
     * level, the folders and definitions are taken from what was recorded with [after]. Anything else the person changed
     * meanwhile (a folder renamed, a link relabelled) is left alone.
     */
    fun restore(before: List<com.bos.sphere.core.data.HomePage>, after: List<com.bos.sphere.core.data.HomePage>, state: HomeState): HomeState {
        val snap = snaps[after] ?: return state
        val was = before.flatMap { it.keys }.toSet()
        val now = after.flatMap { it.keys }.toSet()
        val moved = (was - now) + (now - was)
        if (moved.isEmpty()) return state
        var s = state
        (now - was).forEach { k ->
            snap.items[k]?.let { d -> s = s.copy(items = s.items + (k to d)) }
            snap.widgets[k]?.let { w -> s = s.copy(widgets = s.widgets + (k to w)) }
        }
        // Folders that hold or held something that moved, or that are themselves on a moved key, follow the recorded state.
        val ids = (s.folders.values + snap.folders.values).filter { f -> f.key in moved || f.keys.any { it in moved } }.map { it.id }.toSet()
        ids.forEach { id -> s = snap.folders[id]?.let { f -> s.copy(folders = s.folders + (id to f)) } ?: s.copy(folders = s.folders - id) }
        return s
    }

    /** Remembers the definitions of [keys] as they were in [before], the state ahead of the removal. */
    fun stash(before: HomeState, keys: Collection<String>) {
        keys.forEach { k ->
            before.items[k]?.let { defs[k] = it }
            before.widgets[k]?.let { spans[k] = it }
        }
    }

    /** After an undo or redo: puts back what a key on the pages (or in a folder) needs and the extras lost. */
    fun revive(state: HomeState): HomeState {
        val live = state.allKeys() + stackMembers(state)
        var s = state
        defs.forEach { (k, d) -> if (k in live && k !in s.items) s = s.copy(items = s.items + (k to d)) }
        spans.forEach { (k, w) -> if (k in live && k !in s.widgets) s = s.copy(widgets = s.widgets + (k to w)) }
        // A stack that came back brings its members' definitions with it.
        val members = stackMembers(s)
        defs.forEach { (k, d) -> if (k in members && k !in s.items) s = s.copy(items = s.items + (k to d)) }
        spans.forEach { (k, w) -> if (k in members && k !in s.widgets) s = s.copy(widgets = s.widgets + (k to w)) }
        return s
    }

    /** What the end of edit mode settles: [state] without definitions of removed keys that stayed removed, and the widget keys to destroy for good. */
    class Settled(val state: HomeState, val widgetKeys: List<String>)

    fun settle(state: HomeState): Settled {
        val live = state.allKeys() + stackMembers(state)
        val gone = (defs.keys + spans.keys).filter { it !in live }.distinct()
        val s = state.copy(items = state.items - gone.toSet(), widgets = state.widgets - gone.toSet())
        val widgets = gone.filter { ItemKeys.kind(it) == ItemKind.Widget && !WidgetStacks.isStack(it) }
        defs.clear(); spans.clear()
        return Settled(s, widgets)
    }

    private fun stackMembers(state: HomeState): Set<String> = WidgetStacks.members(state)

    private companion object { const val MAX_SNAPSHOTS = 40 }
}
