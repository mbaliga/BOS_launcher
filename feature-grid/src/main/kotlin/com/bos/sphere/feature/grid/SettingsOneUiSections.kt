package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/**
 * Wave 40 (settingsOneUI): the small grey section titles of the long category pages. The category model is a flat list of rows, so
 * the sections are named here by the English title of the row each one starts at; a row that is not found (renamed, hidden by a
 * state) simply starts no section, and the page still splits into cards where the kind of row changes. An app-level test checks
 * that every row named here still exists in the real categories.
 */
object OneUiSections {
    /** Category English name, to (English title of the first row of a section, English section title). */
    val TABLE: Map<String, List<Pair<String, String>>> = mapOf(
        "Home screen" to listOf(
            "Calm home screen (Glance)" to "Pages",
            "Order" to "Layout",
            "Glance wallpaper" to "Wallpaper",
            "Recent apps shown" to "Recent apps",
            "Clock" to "Glance",
            "Style" to "Home style",
            "Default density" to "Icons and grid",
            "Page turning · portrait" to "Page turning",
            "Always-on keyboard" to "Keyboard",
            "Hide status bar" to "Behaviour",
        ),
        "Look" to listOf(
            "Light / dark" to "Theme",
            "Icon shape" to "Icons",
            "Settings backdrop" to "Settings screen",
            "Calm effect" to "Effects",
            "Glow: intensity" to "Glow",
        ),
        "Corners" to listOf(
            "Corner style" to "Appearance",
            "Follow device rotation" to "Behaviour",
            "Top left" to "Shortcuts",
        ),
        "Gestures" to listOf(
            "Pinch changes grid density" to "Home gestures",
            "Motion gestures" to "Motion",
        ),
        "App drawer" to listOf(
            "Ring segments" to "Shortcut ring",
            "Show labels under glyphs" to "Shortcut ring",
            "Open with" to "Shortcuts",
        ),
        "Radar" to listOf(
            "Radar layout" to "Layout",
            "Web engine" to "Search",
            "Web results" to "Show in results",
            "Search folders" to "Sources",
        ),
        "Comms" to listOf(
            "Left page" to "Appearance",
            "Default view" to "Reading",
            "Comms gestures" to "Rules and access",
            "Mark all as read" to "Clean up",
        ),
        "System & sound" to listOf(
            "Language" to "General",
            "Set as default home" to "Android",
            "Interface sounds" to "Sound",
        ),
    )

    /** Item index to (translated) section title for the category named [categoryTitle] whose rows are [items]. */
    fun headers(categoryTitle: String, items: List<SettingItem>): Map<Int, String> {
        val key = OneUiModel.key(categoryTitle) ?: return emptyMap()
        val rows = TABLE[key] ?: return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for ((rowTitle, header) in rows) {
            val i = items.indexOfFirst { it.title == rowTitle || it.title == L10n.tr(rowTitle) }
            if (i >= 0) out[i] = L10n.tr(header)
        }
        return out
    }
}
