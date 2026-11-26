package com.bos.sphere.ui

import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.GrantGateLogic
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.PaneEntry
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem

/**
 * Wave 56 (settingsCore), CFG-28 / CFG-29 / CFG-31: the hidden side of the settings rows, kept in one place so the rows themselves (and the
 * blocks other streams edit) stay as they are: stable row ids (the section table names a row by id, not by its English title), search synonyms,
 * the default and the unit of every slider, and the rows each pane holds for the search. [apply] runs once over the built categories.
 *
 * Titles here are English keys; the built rows carry translated titles, so they are matched through [L10n.tr]. A row that is not in a table is
 * left exactly as it is. Tests hold the tables to the real categories (every id and every slider is found, every slider has a default).
 */
internal object SettingsRowMeta {
    /** English key of the block a row is built in (wave 57: the Home block, which the tabs then share out) to (English row title to row id). */
    val IDS: Map<String, Map<String, String>> = mapOf(
        "Home" to mapOf(
            "Calm home screen (Glance)" to "home.glance",
            "Order" to "home.order",
            "Glance wallpaper" to "home.glanceWallpaper",
            "Recent apps shown" to "home.recentsLimit",
            "Clock style" to "home.clockStyle",
            "Style" to "home.style",
            "Default density" to "home.defaultDensity",
            "Page turning · portrait" to "home.pagingPortrait",
            "Always-on keyboard" to "home.alwaysKeyboard",
            "Hide status bar" to "home.hideStatusBar",
        ),
    )

    /** English row title to hidden synonyms. Scored below the hint of a row, above its value and category. */
    val KEYWORDS: Map<String, List<String>> = mapOf(
        "Calm home screen (Glance)" to listOf("glance", "calm", "clock screen", "empty home", "first screen"),
        "Order" to listOf("direction", "right to left", "rtl", "left handed", "mirror", "columns fill"),
        "Right side" to listOf("edge swipe", "recents panel", "dock rail"),
        "Glance wallpaper" to listOf("background", "image", "picture", "gradient", "solid colour", "live wallpaper"),
        "Pages wallpaper" to listOf("background", "image", "picture", "gradient", "solid colour"),
        "Recent apps shown" to listOf("history", "recents", "count", "how many"),
        "Clock style" to listOf("time", "analog", "digital", "watch", "big clock"),
        "Clock format" to listOf("12 hour", "24 hour", "am pm", "military time"),
        "Show date under the clock" to listOf("calendar", "day", "weekday"),
        "Clock colour" to listOf("accent", "ink", "white"),
        "Clock size" to listOf("big", "large", "small", "bigger"),
        "Show music" to listOf("media", "song", "player", "now playing", "track"),
        "Hotspots" to listOf("corners", "taps", "four corners"),
        "Controls hide after (seconds)" to listOf("timeout", "delay", "music controls", "auto hide"),
        "Surface indicator" to listOf("dots", "page dots", "position"),
        "Style" to listOf("launcher style", "psp", "dsi", "list", "grid", "layout"),
        "Pages" to listOf("paginated", "scroll", "continuous", "swipe pages"),
        "Grid density" to listOf("columns", "rows", "denser", "adaptive", "window"),
        "Default density" to listOf("columns", "rows", "compact", "comfortable"),
        "Icon size" to listOf("scale", "bigger", "smaller", "large icons"),
        "Spacing" to listOf("gap", "padding", "space between"),
        "Spacing across" to listOf("gap", "padding", "horizontal"),
        "Spacing down" to listOf("gap", "padding", "vertical"),
        "Height in page" to listOf("position", "vertical", "bias", "higher", "lower"),
        "Side margin" to listOf("inset", "padding", "edge"),
        "Top margin" to listOf("inset", "padding", "clock room"),
        "Bottom margin" to listOf("inset", "padding", "thumb"),
        "Label size" to listOf("text size", "font size", "names"),
        "App labels" to listOf("names", "text under icons", "captions"),
        "Notification badges" to listOf("dot", "unread", "count", "red dot"),
        "Always-on keyboard" to listOf("type to search", "old school", "t9"),
        "Hide status bar" to listOf("fullscreen", "immersive", "notification bar"),
        "Restore open screens" to listOf("reopen", "startup", "after restart"),
        "Light / dark" to listOf("dark", "theme", "mode", "amoled", "night", "black", "light"),
        "Font" to listOf("typeface", "text", "lettering"),
        "Icon shape" to listOf("squircle", "round", "circle", "square"),
        "Icon colour" to listOf("tint", "mono", "monochrome", "recolour"),
        "Icon plate" to listOf("tile", "background behind icon", "backing"),
        "Accent" to listOf("colour", "color", "highlight", "violet"),
        "Settings backdrop" to listOf("background", "dim", "black", "wallpaper"),
        "Backdrop dimming" to listOf("dark", "brightness", "dim"),
        "Calm style" to listOf("vignette", "ambient", "edge glow", "indicator", "battery", "signal"),
        "Glow: intensity" to listOf("brightness", "strength", "glow"),
        "Glow: speed" to listOf("pulse", "animation", "glow"),
        "Corner style" to listOf("holo", "bubbles", "sony", "quarter disc"),
        "Corner icon size" to listOf("hotspot size", "bigger", "smaller"),
        "Hot corners with a mouse" to listOf("pointer", "hover", "cursor", "mouse"),
        "Hot corner delay" to listOf("dwell", "wait", "hover time"),
        "Motion gestures" to listOf("shake", "tilt", "flip", "sensor", "wrist"),
        "Motion sensitivity" to listOf("shake strength", "threshold", "false triggers"),
        "Interface sounds" to listOf("click", "tones", "audio", "sfx", "noise"),
        "Volume" to listOf("loudness", "sound level"),
        "Language" to listOf("locale", "translation", "idioma", "english", "deutsch"),
        "Access and privacy" to listOf("permissions", "grants", "notification access", "usage access", "contacts", "microphone", "do not disturb", "default home", "privacy"),
        "Launcher vibration" to listOf("haptics", "vibrate", "buzz", "feedback"),
        "Open Liftoff Studio" to listOf("studio", "browser", "web editor", "configurator", "computer"),
        "Radar layout" to listOf("filmstrip", "orbit", "flat", "search layout"),
        "Web engine" to listOf("google", "duckduckgo", "browser search", "search provider"),
        "Web results" to listOf("internet", "play store", "online"),
        "Web suggestions" to listOf("autocomplete", "privacy", "network", "online"),
        "Files" to listOf("documents", "photos", "downloads"),
        "Contacts" to listOf("people", "phone book", "call"),
        "Left page" to listOf("widget", "notifications", "swipe right"),
        "Comms size" to listOf("zoom", "text size", "bigger", "smaller"),
        "Comms theme" to listOf("dark", "light", "hub"),
        "Default view" to listOf("first tab", "opens on", "all", "messages"),
        "Unread strip" to listOf("rail", "counts", "edge"),
        "Wheel groups" to listOf("letters", "categories", "spokes", "drawer"),
        "Open with" to listOf("default app", "browser", "music", "email", "maps"),
        "Backup" to listOf("export", "import", "restore", "snapshot"),
    )

    /** A slider's default (it may follow another preference, so it is given the current prefs) and the unit its readout carries. */
    class SliderSpec(val unit: String, val default: (defaults: LauncherPrefs, now: LauncherPrefs) -> Float)

    /** English row title to the default and the unit of that slider. A test holds every slider of the settings to this table. */
    val SLIDERS: Map<String, SliderSpec> = mapOf(
        "Grid density" to SliderSpec("%") { d, _ -> d.gridDensity },
        "Icon size" to SliderSpec("%") { d, _ -> d.iconScale },
        "Spacing" to SliderSpec("%") { d, _ -> d.iconSpacing },
        "Spacing across" to SliderSpec("%") { d, _ -> d.iconSpacing },
        // Follows "across" until it is set by hand: the default is wherever the other one is, and the row reads Auto while it follows.
        "Spacing down" to SliderSpec("%") { _, now -> now.iconSpacing },
        "Height in page" to SliderSpec("%") { d, _ -> d.gridBias },
        "Side margin" to SliderSpec("%") { d, _ -> d.gridInsetX },
        "Top margin" to SliderSpec("%") { d, _ -> d.gridInsetTop },
        "Bottom margin" to SliderSpec("%") { d, _ -> d.gridInsetBottom },
        "Label size" to SliderSpec("%") { d, _ -> d.labelScale },
        "Corner icon size" to SliderSpec(" dp") { d, _ -> d.cornerIconSize },
        "Hot corner delay" to SliderSpec(" ms") { d, _ -> d.hotCornerDwellMs.toFloat() },
        "Motion sensitivity" to SliderSpec("%") { d, _ -> d.motionSensitivity },
        "Backdrop dimming" to SliderSpec("%") { d, _ -> d.settingsDim },
        "Glow: intensity" to SliderSpec("%") { d, _ -> d.indicatorIntensity },
        "Glow: speed" to SliderSpec("%") { d, _ -> d.indicatorSpeed },
        "Volume" to SliderSpec("%") { d, _ -> d.uiSoundVolume },
    )

    /** Stable ids of the rows inside panes (a pane's own code draws them with `paneFlash` / `flashId` under the same id). */
    object PaneIds {
        const val BACKUP_EXPORT = "backup.export"
        const val BACKUP_EXPORT_SETTINGS = "backup.exportSettings"
        const val BACKUP_IMPORT = "backup.import"
        const val BACKUP_IMPORT_LAYOUT = "backup.importLayout"
        const val BACKUP_SNAPSHOT = "backup.snapshot"
        /** Wave 57 (CFG-43): Import a Studio file (the same import as Import setup, named for the browser editor). */
        const val BACKUP_IMPORT_STUDIO = "backup.importStudio"
        /** Wave 57 (CFG-42): the two rows of Access and privacy that are not grants of [GrantKind]. */
        const val ACCESS_DND = "access.dnd"
        const val ACCESS_DEFAULT_HOME = "access.defaultHome"
        /** Wave 58 (CFG-67): the rows of the Themes, Drawer shortcuts, Profiles and Radar panes. */
        const val THEMES_IMPORT = "themes.import"
        const val THEMES_EXPORT = "themes.export"
        const val THEMES_RESET = "themes.reset"
        const val THEMES_UNDO = "themes.undo"
        fun drawerSegment(n: Int) = "drawer.segment$n"
        const val DRAWER_RESET = "drawer.reset"
        const val PROFILES_NEW = com.bos.sphere.feature.dock.ProfilePaneIds.NEW
        const val PROFILES_LIST = com.bos.sphere.feature.dock.ProfilePaneIds.LIST
        const val PROFILES_SUGGESTIONS = com.bos.sphere.feature.dock.ProfilePaneIds.SUGGESTIONS
        const val PROFILES_CALM = com.bos.sphere.feature.dock.ProfilePaneIds.CALM
        const val PROFILES_RADAR_ACTIONS = com.bos.sphere.feature.dock.ProfilePaneIds.RADAR_ACTIONS
        fun provider(pkg: String) = "providers.$pkg"
        const val FOLDERS_ADD = "folders.add"
        const val FOLDERS_REINDEX = "folders.reindex"
        const val HISTORY_FORGET = "history.forget"
        fun access(k: GrantKind) = "access.${k.name}"
        fun openWith(id: com.bos.sphere.core.data.CornerActionId) = "openwith.${id.name}"
    }

    private val ACCESS_WORDS = mapOf(
        GrantKind.Notifications to listOf("notification access", "listener", "comms", "media strip"),
        GrantKind.UsageAccess to listOf("usage access", "recent apps", "history"),
        GrantKind.Contacts to listOf("contacts", "people", "phone book"),
        GrantKind.Folders to listOf("files", "folders", "storage", "documents"),
        GrantKind.Microphone to listOf("microphone", "mic", "voice", "speak"),
    )

    /** The English titles of the panes and the rows each one holds; the row titles are translated here. */
    private fun paneEntries(): Map<String, List<PaneEntry>> = mapOf(
        "Export, import and restore" to listOf(
            PaneEntry(PaneIds.BACKUP_EXPORT, L10n.tr("Export setup…"), listOf("backup", "save", "file", "download")),
            PaneEntry(PaneIds.BACKUP_EXPORT_SETTINGS, L10n.tr("Export settings only…"), listOf("backup", "save", "file")),
            PaneEntry(PaneIds.BACKUP_IMPORT, L10n.tr("Import setup…"), listOf("restore", "load", "file", "open")),
            PaneEntry(PaneIds.BACKUP_IMPORT_LAYOUT, L10n.tr("Import layout from another launcher…"), listOf("migrate", "launcher3", "move", "switch launcher")),
            PaneEntry(PaneIds.BACKUP_SNAPSHOT, L10n.tr("Take a snapshot now"), listOf("restore point", "backup", "undo", "history")),
            PaneEntry(PaneIds.BACKUP_IMPORT_STUDIO, L10n.tr("Import a Studio file…"), listOf("studio", "browser", "web editor", "configurator")),
        ),
        "Access and privacy" to GrantKind.entries.map { k ->
            PaneEntry(PaneIds.access(k), L10n.tr(GrantGateLogic.copy(k).title), ACCESS_WORDS[k].orEmpty(), L10n.tr(GrantGateLogic.copy(k).button))
        } + listOf(
            PaneEntry(PaneIds.ACCESS_DND, L10n.tr("Do Not Disturb access"), listOf("dnd", "silent", "permission", "policy")),
            PaneEntry(PaneIds.ACCESS_DEFAULT_HOME, L10n.tr("Set as default home"), listOf("default launcher", "home app", "make default")),
        ),
        // Wave 58 (CFG-67): the Themes page and the Radar panes. The Drawer shortcuts, Profiles and Search providers rows are declared where those rows are built.
        "Themes" to listOf(
            PaneEntry(PaneIds.THEMES_IMPORT, L10n.tr("Import theme"), listOf("liftofftheme", "theme file", "load")),
            PaneEntry(PaneIds.THEMES_EXPORT, L10n.tr("Export current look"), listOf("share theme", "save look", "theme file")),
            PaneEntry(PaneIds.THEMES_RESET, L10n.tr("Reset to default look"), listOf("default theme", "original look")),
            PaneEntry(PaneIds.THEMES_UNDO, L10n.tr("Undo last theme change"), listOf("previous look", "revert theme")),
        ),
        "Search folders" to listOf(
            PaneEntry(PaneIds.FOLDERS_ADD, L10n.tr("Add a folder"), listOf("folder picker", "directory", "index")),
            PaneEntry(PaneIds.FOLDERS_REINDEX, L10n.tr("Update the file index now"), listOf("reindex", "refresh", "scan")),
        ),
        "Search history and aliases" to listOf(
            PaneEntry(PaneIds.HISTORY_FORGET, L10n.tr("Forget what Radar learned"), listOf("clear history", "ranking", "learning")),
        ),
        "Open with" to listOf(
            com.bos.sphere.core.data.CornerActionId.Music, com.bos.sphere.core.data.CornerActionId.Messages, com.bos.sphere.core.data.CornerActionId.Browser,
            com.bos.sphere.core.data.CornerActionId.Email, com.bos.sphere.core.data.CornerActionId.Maps, com.bos.sphere.core.data.CornerActionId.Calendar,
            com.bos.sphere.core.data.CornerActionId.Gallery, com.bos.sphere.core.data.CornerActionId.Contacts, com.bos.sphere.core.data.CornerActionId.Calculator,
            com.bos.sphere.core.data.CornerActionId.Camera,
        ).map { PaneEntry(PaneIds.openWith(it), L10n.tr(it.label), listOf("default app", "open with", "choose app")) },
    )

    /** The unit of [valueText] when it is a bare number: "32" becomes "32 dp". Text that already has a unit, or words ("Auto"), stays. */
    fun withUnit(valueText: String, unit: String): String =
        if (valueText.isNotEmpty() && valueText.all { it.isDigit() || it == '.' || it == '-' }) valueText + unit else valueText

    /**
     * [categories] with the ids, keywords, slider defaults with units and pane rows of the tables filled in. The categories keep their order and
     * their reset action; a row not in any table is untouched.
     */
    fun apply(categories: List<SettingCategory>, current: LauncherPrefs): List<SettingCategory> {
        val defaults = LauncherPrefs()
        val english = HashMap<String, String>()
        for (k in KEYWORDS.keys + SLIDERS.keys + IDS.values.flatMap { it.keys } + paneEntries().keys) { english[k] = k; english[L10n.tr(k)] = k }
        val panes = paneEntries()
        return categories.map { c ->
            val catKey = OneUiModel.key(c.title)
            c.copy(items = c.items.map { item ->
                val en = english[item.title] ?: return@map item
                val out = if (item is SettingItem.Slider) SLIDERS[en]?.let { spec ->
                    item.with(
                        default = item.default ?: spec.default(defaults, current),
                        valueText = withUnit(item.valueText, spec.unit),
                    )
                } ?: item else item
                catKey?.let { IDS[it]?.get(en) }?.let { out.meta.id = it }
                KEYWORDS[en]?.let { out.meta.keywords = (out.meta.keywords + it).distinct() }
                if (item is SettingItem.Pane) panes[en]?.let { out.meta.entries = out.meta.entries + it }
                out
            })
        }
    }

    /** True when [valueText] reads with a unit, a word or a percent sign: the check of the slider test. */
    fun hasUnit(valueText: String): Boolean = valueText.isNotBlank() && !valueText.all { it.isDigit() || it == '.' || it == '-' }

}
