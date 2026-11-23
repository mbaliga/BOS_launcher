package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/**
 * Wave 40 (settingsOneUI): the small grey section titles of the long category pages. The category model is a flat list of rows, so
 * the sections are named here by the row each one starts at.
 *
 * Wave 56 (settingsCore), CFG-31: a section starts at a row **id** ([ItemMeta.id], set where the row is built or by the app's one id table),
 * not at the English title of the row, so a rename or a translation cannot lose a header. A key that is no id still matches a row by its
 * (English or translated) title, which keeps the rows that have no id yet working. Rows that are not usable right now stay on the page
 * dimmed ([disabled]) instead of vanishing, so a section never loses its header because its first row is conditional. An app-level test
 * checks that every row named here exists in the real categories.
 */
object OneUiSections {
    /** Category English name, to (id of the first row of a section, English section title). */
    val TABLE: Map<String, List<Pair<String, String>>> = mapOf(
        "Home screen" to listOf(
            "home.glance" to "Pages",
            "home.order" to "Layout",
            "home.glanceWallpaper" to "Wallpaper",
            "home.recentsLimit" to "Recent apps",
            "home.clockStyle" to "Glance",
            "home.style" to "Home style",
            "home.defaultDensity" to "Icons and grid",
            "home.pagingPortrait" to "Page turning",
            "home.alwaysKeyboard" to "Keyboard",
            "home.hideStatusBar" to "Behaviour",
        ),
        "Look" to listOf(
            "Light / dark" to "Theme",
            "Icon shape" to "Icons",
            "Settings backdrop" to "Settings screen",
            "Calm style" to "Effects",
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
        "Side panel" to listOf(
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

    /** The index in [items] of the row [key] names: by id first, then by (English or translated) title. */
    fun find(items: List<SettingItem>, key: String): Int {
        val byId = items.indexOfFirst { it.meta.id == key }
        if (byId >= 0) return byId
        return items.indexOfFirst { it.title == key || it.title == L10n.tr(key) }
    }

    /** Item index to (translated) section title for the category named [categoryTitle] whose rows are [items]. */
    fun headers(categoryTitle: String, items: List<SettingItem>): Map<Int, String> {
        val key = OneUiModel.key(categoryTitle) ?: return emptyMap()
        val rows = TABLE[key] ?: return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for ((rowKey, header) in rows) {
            val i = find(items, rowKey)
            if (i >= 0) out[i] = L10n.tr(header)
        }
        return out
    }
}
