package com.bos.sphere.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.bos.sphere.core.data.GestureKind
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.SettingsTabs

/**
 * Wave 57 (settingsTabs), CFG-40: where every row of Settings lives. The rows are still built by topic ([buildSettingsCategories] builds a "Home",
 * a "Corners", a "Theme" ... block, as before); this one table places each of them, by the topic it is built in and its English title, into one
 * section of one of the six tabs. A row is placed once: the first spec that matches takes it. A row no spec names is an orphan: it still shows (at
 * the end of System, under "Other", so nothing is ever lost) and a test fails until it gets a place. The section headers of each tab come from the
 * same table, so a header always names rows that are there.
 */
internal object SettingsTabLayout {
    /** A row: the English key of the topic block it is built in and its English title. [title] "*" takes every row of the topic not placed yet (Lab, About: their titles are data). */
    data class Row(val topic: String, val title: String, val shown: (() -> String)? = null) {
        val key: String get() = "$topic/$title"
        fun matches(rowTitle: String): Boolean = title == ALL || rowTitle == (shown?.invoke() ?: L10n.tr(title)) || rowTitle == title
    }
    /** [note]: wave 58 (CFG-65), one short sentence under the header (English key), where the help a row's grey line no longer carries lives. */
    class Section(val header: String, val rows: List<Row>, val note: String? = null)
    class Tab(val key: String, val glyph: ImageVector, val sections: List<Section>)

    const val ALL = "*"
    const val ORPHANS = "Other"

    // Topic keys (the English titles of the blocks the builder makes).
    const val T_PAGES = "Pages"; const val T_HOME = "Home"; const val T_APPS = "Apps"; const val T_CORNERS = "Corners"; const val T_SIDE = "Side panel"
    const val T_GESTURES = "Gestures"; const val T_MOTION = "Motion"; const val T_THEME = "Theme"; const val T_RADAR = "Radar"; const val T_INDICATOR = "Indicator"
    const val T_SOUND = "Sound"; const val T_SYSTEM = "System"; const val T_CAPS = "Capabilities"; const val T_LAB = "Lab"; const val T_HIDDEN = "Hidden apps"
    const val T_BACKUP = "Backup"; const val T_WORKSPACES = "Profiles"; const val T_DESKTOP = "Desktop & dock"; const val T_ABOUT = "About"

    val TOPICS = listOf(T_PAGES, T_HOME, T_APPS, T_CORNERS, T_SIDE, T_GESTURES, T_MOTION, T_THEME, T_RADAR, T_INDICATOR, T_SOUND, T_SYSTEM, T_CAPS, T_LAB, T_HIDDEN, T_BACKUP, T_WORKSPACES, T_DESKTOP, T_ABOUT)

    private fun rows(topic: String, vararg titles: String) = titles.map { Row(topic, it) }

    val TABS: List<Tab> = listOf(
        Tab(
            SettingsTabs.HOME, Glyphs.Home, listOf(
                Section("Glance", note = "Glance is the calm first screen; your pages follow it.", rows = rows(T_HOME, "Calm home screen (Glance)", "Clock style", "Clock format", "Show date under the clock", "Clock colour", "Clock size", "Show music", "Music line width") +
                    rows(T_HOME, "Controls hide after (seconds)", "Try it: Controls hide after", "Surface indicator")),
                Section("Home pages", rows(T_HOME, "Pages", "Order") +
                    rows(T_PAGES, "Page", "Layout", "Density follows the home default", "Columns · portrait", "Rows · portrait", "Columns · landscape", "Rows · landscape", "Rings", "Clock tile on this page", "Add a page after this", "Delete this page") +
                    rows(T_HOME, "Add page", "Edit home", "Widgets, folders and icons", "Restore previous pages", "Page turning · portrait", "Page turning · landscape", "Home panels")),
                Section("Layout", rows(T_HOME, "Style", "Scroll direction", "Grid follows window size", "Grid density", "Default density", "Icon size", "Same spacing both ways", "Spacing", "Spacing across", "Spacing down",
                    "Height in page", "Side margin", "Top margin", "Bottom margin", "Label size", "App labels", "Notification badges", "Count in badges", "Hide status bar")),
                Section("Corners", rows(T_CORNERS, "Hotspots", "Corner style", "Corner icon size", "Icon type", "Follow device rotation", "Separate corners for landscape")), // wave 59: the Hotspots editor replaced the four corner rows
                Section("Gestures and motion", rows(T_GESTURES, "Pinch changes grid density") + HomeGesture.entries.filter { it.kind == GestureKind.Touch }.map { Row(T_GESTURES, it.label) } +
                    rows(T_GESTURES, "On this home") + rows(T_MOTION, "Motion gestures", "When home isn't showing", "Motion sensitivity", "Try it: Motion sensitivity") +
                    HomeGesture.entries.filter { it.kind == GestureKind.Motion }.map { Row(T_MOTION, it.label) }),
                Section("Keyboard and pointer", rows(T_HOME, "Always-on keyboard", "Keyboard shortcuts") + rows(T_CORNERS, "Hot corners with a mouse", "Hot corner delay", "Try it: Hot corner delay")),
                Section("Dock and displays", rows(T_DESKTOP, "Dock", "Dock and external display")),
            ),
        ),
        Tab(
            SettingsTabs.LOOK, Glyphs.Palette, listOf(
                Section("Theme", note = "A look never replaces your own wallpaper picture.", rows = rows(T_THEME, "Looks", "Light / dark", "Accent", "Themes")),
                Section("Font and icons", rows(T_THEME, "Font", "Icon shape", "Icon colour", "Icon plate")),
                Section("Wallpaper", rows(T_HOME, "Glance wallpaper", "Pages use the same wallpaper as Glance", "Pages wallpaper")),
                Section("Calm styles", note = "Calm styles never cover your wallpaper.", rows = rows(T_INDICATOR, "Calm style", "Also show regular battery and signal", "Show", "Glow: intensity", "Glow: speed", "Glow: preview", "Glow: colours")),
                Section("Settings screen and text", rows(T_THEME, "Settings backdrop", "Backdrop dimming", "Wallpaper palette, density and text size")),
            ),
        ),
        Tab(
            SettingsTabs.APPS, Glyphs.Apps, listOf(
                // Wave 57: the Profiles page (Profiles extend and replace Workspaces).
                Section("Profiles and groups", rows(T_WORKSPACES, "Profiles") + rows(T_DESKTOP, "App groups")),
                Section("App drawer", rows(T_APPS, "Ring segments", "Drawer shortcuts", "Show labels under glyphs", "Wheel groups", "Pages")),
                Section("Hidden and default apps", rows(T_HIDDEN, "Hidden apps") + rows(T_APPS, "Open with")),
            ),
        ),
        Tab(
            SettingsTabs.RADAR, Glyphs.Radar, listOf(
                Section("Layout", rows(T_RADAR, "Radar layout")),
                Section("Appearance", rows(T_RADAR, "Radar background", "Keep moving")),
                Section("Input", rows(T_RADAR, "Keep this layout with a keyboard") + rows(T_HOME, "Keyboard opens with Radar and drawer · portrait", "Keyboard opens with Radar and drawer · landscape")),
                Section("Sources", rows(T_RADAR, "Web results", "Actions", "Files", "Deep results", "Contacts", "Search folders", "Search providers from other apps")),
                Section("Web", note = "Suggestions also send your typing to the Play Store.", rows = rows(T_RADAR, "Web engine", "Web suggestions")),
                Section("Privacy", rows(T_RADAR, "Search history and aliases")),
            ),
        ),
        Tab(
            SettingsTabs.SIDE_PANEL, Glyphs.Bell, listOf(
                Section("Left panel", rows(T_SIDE, "Left page", "Choose widget")),
                Section("Panel theme and size", rows(T_SIDE, "Comms theme", "Comms size")),
                Section("Sections", rows(T_SIDE, "Widgets section", "Widgets position")),
                Section("Comms", note = "Pinned items never count as unread or get dismissed.", rows = rows(T_SIDE, "Default view", "Show pinned at the top", "Clear from the shade when dismissed in Comms", "Unread strip", "Read history")),
                Section("Rules and gestures", rows(T_SIDE, "Manage groups and rules", "Grouping & rules", "Comms gestures")),
                Section("Right edge", rows(T_HOME, "Right side", "Recent apps shown", "Include apps opened from elsewhere")),
                Section("Clean up", rows(T_SIDE, "Mark all as read", "Clear all notifications", "Delete history")),
            ),
        ),
        Tab(
            SettingsTabs.SYSTEM, Glyphs.Gear, listOf(
                Section("General", rows(T_SYSTEM, "Language") + rows(T_HOME, "Restore open screens")),
                Section("Sound and haptics", note = "Android's own sound and vibration settings stay as they are.", rows = rows(T_SOUND, "Interface sounds", "Volume", "Launcher vibration")),
                Section("Permissions", rows(T_SYSTEM, "Access and privacy", "Android settings")),
                Section("Backup and restore", rows(T_BACKUP, "Export, import and restore", "Open Liftoff Studio", "Reset all settings")),
                Section("Help and recovery", rows(T_CAPS, "Capabilities") + rows(T_BACKUP, "Crash reports")),
                Section("Lab", rows(T_LAB, ALL)),
                Section("About", rows(T_ABOUT, ALL)),
            ),
        ),
    )

    /** The English topic key of a built block from its (translated) title; "Hidden apps · 2" is the Hidden apps block. */
    fun topicOf(c: SettingCategory): String? = TOPICS.firstOrNull { t ->
        c.title == t || c.title == L10n.tr(t) || (t == T_HIDDEN && (c.title.startsWith(L10n.tr(T_HIDDEN) + " · ") || c.title.startsWith("$T_HIDDEN · ")))
    }

    /** What placing produced: the six tabs, and the rows no spec took (each "topic/title"). */
    class Placement(val tabs: List<SettingCategory>, val orphans: List<String>, val placedKeys: Map<String, List<String>>)

    /**
     * Places the rows of the [topics] blocks into the six tabs. Each row is taken once; the tab carries the headers of the sections that have
     * rows. A placed row without an id gets its key as its id ("Home/Order"), so a deep link or a test names it the same way in every language.
     */
    fun place(topics: List<SettingCategory>): Placement {
        class Slot(val topic: String, val item: SettingItem, var taken: Boolean = false)
        val pool = topics.flatMap { c -> val t = topicOf(c) ?: c.title; c.items.map { Slot(t, it) } }
        val placedKeys = LinkedHashMap<String, MutableList<String>>()
        val out = TABS.map { tab ->
            val items = ArrayList<SettingItem>()
            val headers = LinkedHashMap<Int, String>()
            val notes = LinkedHashMap<Int, String>()
            for (s in tab.sections) {
                val first = items.size
                for (r in s.rows) {
                    for (slot in pool) {
                        if (slot.taken || slot.topic != r.topic || !r.matches(slot.item.title)) continue
                        slot.taken = true
                        if (slot.item.meta.id == null) slot.item.meta.id = if (r.title == ALL) "${r.topic}/${slot.item.title}" else r.key
                        items += slot.item
                        placedKeys.getOrPut(tab.key) { ArrayList() } += if (r.title == ALL) "${r.topic}/${slot.item.title}" else r.key
                        if (r.title != ALL) break
                    }
                }
                if (items.size > first) { headers[first] = L10n.tr(s.header); s.note?.let { notes[first] = L10n.tr(it) } }
            }
            Triple(tab, items, headers to notes)
        }
        val orphans = pool.filter { !it.taken }
        val tabs = out.map { (tab, items, hn) ->
            val (headers, notes) = hn
            if (tab.key == SettingsTabs.SYSTEM && orphans.isNotEmpty()) {
                headers[items.size] = L10n.tr(ORPHANS)
                items += orphans.map { it.item }
            }
            SettingCategory(L10n.tr(tab.key), tab.glyph, items, headers = headers, notes = notes)
        }
        return Placement(tabs, orphans.map { "${it.topic}/${it.item.title}" }, placedKeys)
    }

    /** Every explicit row key of the table (not the "*" ones), in table order: a test requires each to appear once. */
    fun keys(): List<String> = TABS.flatMap { t -> t.sections.flatMap { s -> s.rows.filter { it.title != ALL }.map { it.key } } }

    /** The tab and section a key is listed in. */
    fun whereIs(key: String): Pair<String, String>? {
        for (t in TABS) for (s in t.sections) if (s.rows.any { it.key == key }) return t.key to s.header
        return null
    }
}
