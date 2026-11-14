package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomeTiles

/*
 * HOM-02: every kind of thing that can sit on home or in the dock, as plain data. Items keep the
 * existing string-key convention of the page model (apps are their component key, tiles are
 * "tile:..."), and add one prefix per new kind so a stored page is still just a list of strings.
 */

enum class ItemKind { App, Shortcut, Folder, Widget, Link, Tile }

object ItemKeys {
    const val SHORTCUT = "shortcut:"
    const val FOLDER = "folder:"
    const val WIDGET = "widget:"
    const val LINK = "link:"

    fun kind(key: String): ItemKind = when {
        key.startsWith(SHORTCUT) -> ItemKind.Shortcut
        key.startsWith(FOLDER) -> ItemKind.Folder
        key.startsWith(WIDGET) -> ItemKind.Widget
        key.startsWith(LINK) -> ItemKind.Link
        HomeTiles.isTile(key) -> ItemKind.Tile
        else -> ItemKind.App
    }

    fun shortcut(packageName: String, shortcutId: String, userId: Int) = "$SHORTCUT$packageName/$shortcutId#$userId"
    fun folder(id: String) = FOLDER + id
    fun widget(appWidgetId: Int) = WIDGET + appWidgetId
    fun link(uri: String) = LINK + uri

    fun folderId(key: String): String? = key.takeIf { it.startsWith(FOLDER) }?.removePrefix(FOLDER)
    fun widgetId(key: String): Int? = key.takeIf { it.startsWith(WIDGET) }?.removePrefix(WIDGET)?.toIntOrNull()
}

/**
 * What an item is, for the kinds that carry data beyond their key. [target] is the shortcut id,
 * the link or file URI; [isFile] separates a file shortcut from a web / deep link.
 */
data class ItemDef(
    val key: String,
    val kind: ItemKind,
    val label: String,
    val target: String = "",
    val isFile: Boolean = false,
)

data class Folder(val id: String, val name: String, val keys: List<String>) {
    val key: String get() = ItemKeys.folder(id)
}

/** Columns × rows a widget occupies on the grid surface (it shares the tile layout model, HOM-08). */
data class WidgetSpan(val cols: Int, val rows: Int) {
    init { require(cols >= 1 && rows >= 1) }
}

/**
 * Everything that makes up the arrangement, as one immutable value, so edit mode can snapshot it
 * for undo (HOM-12) and surfaces can swap without touching it (HOM-01).
 */
data class HomeState(
    val pages: List<HomePage> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val items: Map<String, ItemDef> = emptyMap(),
    val widgets: Map<String, WidgetSpan> = emptyMap(),
    val dock: List<String> = emptyList(),
) {
    /** Every key shown at top level (pages and dock), in order. */
    fun topLevelKeys(): List<String> = pages.flatMap { it.keys } + dock

    /** Keys the page resolver must treat as available: apps plus every non-app item defined here. */
    fun nonAppKeys(): List<String> = (items.keys + folders.values.map { it.key } + widgets.keys).toList()

    /** Every key reachable on home: top level plus folder contents. */
    fun allKeys(): Set<String> = buildSet {
        addAll(topLevelKeys())
        folders.values.forEach { addAll(it.keys) }
    }

    fun spanOf(key: String): WidgetSpan = widgets[key] ?: WidgetSpan(1, 1)
}
