package com.bos.sphere.core.catalog

/**
 * W48 (editGaps, HOM-12): what Cancel puts back besides the page layout and the dock pins. The extras of [HomeState] (folder contents and names, the
 * definitions of links, files, shortcuts, widget stacks and their members, and the widget sizes) are noted when edit mode starts; Cancel returns them,
 * and the widgets that were added meanwhile (their host ids are bound) are named so the host can free them.
 */
class EditExtrasEntry(state: HomeState) {
    private val items = state.items
    private val folders = state.folders
    private val widgets = state.widgets

    /** True when the extras of [now] differ from the entry (the Cancel confirm counts this as a change). */
    fun changed(now: HomeState): Boolean = now.items != items || now.folders != folders || now.widgets != widgets

    class Restored(val state: HomeState, val widgetKeysToFree: List<String>)

    /** [now] with the entry's extras; the pages and the dock of [now] are kept (their own session puts them back). */
    fun restore(now: HomeState): Restored {
        val added = (now.items.keys + now.widgets.keys).filter { it !in items && it !in widgets && ItemKeys.kind(it) == ItemKind.Widget && !WidgetStacks.isStack(it) }.distinct()
        return Restored(now.copy(items = items, folders = folders, widgets = widgets), added)
    }
}
