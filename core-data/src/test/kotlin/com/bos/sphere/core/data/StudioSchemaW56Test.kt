package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Wave 56 (studio), CFG-34: the Liftoff Studio (web/liftoff-studio, a static page that edits a configuration off the device) reads
 * `schema.json` / `schema.js`. This test is both the GENERATOR and the GUARD of those two files:
 *
 *  - the field list below carries only the editorial parts (label, tab, group, preview scene, ranges that the settings screens use);
 *    the keys, the defaults, the enum option ids and the device-bound set are read from the real code (LauncherPrefs, SettingsCodec,
 *    StateSchema, the enums, the dock source), so a new or removed key, a changed default or a renamed option fails the build here;
 *  - run with the environment variable STUDIO_REGEN=1 to rewrite web/liftoff-studio/schema.json and schema.js from the code
 *    (`STUDIO_REGEN=1 ./gradlew :core-data:testDebugUnitTest --tests '*StudioSchemaW56Test*' --offline --max-workers=2`), then commit the result.
 */
class StudioSchemaW56Test {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }
    private val studio get() = File(root(), "web/liftoff-studio")

    // ---------------------------------------------------------------------------------------------------------------
    // What the export really carries
    // ---------------------------------------------------------------------------------------------------------------

    /** Every key of `settings.prefs` in an "Export settings only" document, with the value a default install writes (calmStyle is absent by default, so one with it set is merged). */
    private fun exportedDefaults(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (p in listOf(LauncherPrefs(calmStyle = CalmStyle.Beam), LauncherPrefs())) {
            val prefs = SettingsCodec.encode(p).getJSONObject("prefs")
            prefs.keys().forEach { k -> if (k !in out || p.calmStyle == null) out[k] = prefs.get(k) }
        }
        out.remove("calmStyle")
        return out.toSortedMap()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The editorial field table
    // ---------------------------------------------------------------------------------------------------------------

    private class Opt(val id: String, val label: String)
    private class Fd(
        val key: String, val label: String, val type: String,
        val scope: String = "settings",
        val options: List<Opt> = emptyList(),
        val min: Double? = null, val max: Double? = null, val step: Double? = null, val unit: String? = null,
        val scene: String = "glance",
        val advanced: Boolean = false, val deviceBound: Boolean = false, val optional: Boolean = false,
        val showWhen: Pair<String, List<String>>? = null,
        val default: Any? = UNSET,
        val note: String? = null,
        val file: String? = null, val fileKey: String? = null, val fileType: String? = null,
        val count: Int? = null,
    ) {
        // Wave 58 (studioPreview, CFG-70): set from the app's own Settings layout (appRowOf + SettingsTabLayout.kt), never by hand.
        var row = ""; var tab = ""; var group = ""
    }
    private companion object { val UNSET = Any() }

    private inline fun <reified E : Enum<E>> byName(vararg skip: E, label: (E) -> String): List<Opt> = enumValues<E>().filter { it !in skip }.map { Opt(it.name, label(it)) }

    private fun fl(f: Float) = f.toString().toDouble()

    private val uiFontLabels = mapOf(
        "space_grotesk" to "Space Grotesk", "system" to "System font", "inter" to "Inter", "manrope" to "Manrope", "dm_sans" to "DM Sans",
        "jetbrains_mono" to "JetBrains Mono", "atkinson" to "Atkinson Hyperlegible", "lexend" to "Lexend",
    )
    private fun pic(p: String, surface: String, kindKey: String): List<Fd> {
        val showPic = kindKey to listOf("picture")
        return listOf(
            Fd(p + "PicFit", "Picture fit", "enum", options = PictureFit.entries.map { Opt(it.id, it.label) }, showWhen = showPic, advanced = true),
            Fd(p + "PicDim", "Picture dim", "int", min = 0.0, max = 80.0, step = 10.0, unit = "%", showWhen = showPic, advanced = true),
            Fd(p + "PicGif", "Animated picture", "enum", options = GifMode.entries.map { Opt(it.id, it.label) }, showWhen = showPic, advanced = true),
            Fd(p + "SysDim", "Dim over the system wallpaper", "int", min = 0.0, max = 80.0, step = 10.0, unit = "%", showWhen = kindKey to listOf("system"), advanced = true),
            // The file itself and what describes it belong to this device (StateSchema.WALLPAPER_PICTURE_KEYS).
            Fd(p + "Pic", "Picture file", "string", deviceBound = true, advanced = true),
            Fd(p + "PicAnim", "Picture is animated", "bool", deviceBound = true, advanced = true),
            Fd(p + "PicVid", "Picture is a video", "bool", deviceBound = true, advanced = true),
            Fd(p + "PicMs", "Picture duration", "int", deviceBound = true, advanced = true, unit = "ms"),
            Fd(p + "PicOk", "Picture power check", "bool", deviceBound = true, advanced = true),
            Fd(p + "PicBounce", "Picture bounce support", "string", deviceBound = true, advanced = true),
        )
    }

    private val cornerActionOpts: List<Opt> = listOf(Opt("", "Nothing")) + CornerActionId.entries.map { Opt("action:" + it.name, it.label) }

    private fun rawFields(): List<Fd> {
        val clockOn = "clockStyle" to ClockStyle.entries.filter { it != ClockStyle.Off }.map { it.name }
        val digital = "clockStyle" to ClockStyle.entries.filter { it.digital }.map { it.name }
        val l = ArrayList<Fd>()
        fun add(vararg f: Fd) { l += f }

        // ---- HOME > Glance
        add(
            Fd("glanceEnabled", "Glance", "bool"),
            Fd("homeOrder", "Order", "enum", options = byName<HomeOrder> { it.label }),
            Fd("showMusic", "Music line", "bool"),
            Fd("glanceControlsSec", "Music controls stay", "int", min = GlanceControls.MIN_SECONDS.toDouble(), max = GlanceControls.MAX_SECONDS.toDouble(), step = 1.0, unit = "s"),
            Fd("glanceIndicator", "Surface indicator", "bool"),
        )
        // ---- HOME > Clock
        add(
            Fd("clockStyle", "Clock style", "enum", options = byName<ClockStyle> { it.label }),
            Fd("clockFormat", "Hours", "enum", options = byName<ClockFormat> { it.label }, showWhen = digital),
            Fd("glanceClockDate", "Show the date", "bool", showWhen = clockOn),
            Fd("glanceClockAccent", "Accent colour", "bool", showWhen = clockOn),
            Fd("glanceClockSize", "Size", "enum", options = byName<ClockSize> { it.label }, showWhen = clockOn),
            Fd("glanceClockX", "Position across (portrait)", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", showWhen = clockOn),
            Fd("glanceClockY", "Position down (portrait)", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", showWhen = clockOn),
            Fd("glanceClockXLand", "Position across (landscape)", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", advanced = true, showWhen = clockOn),
            Fd("glanceClockYLand", "Position down (landscape)", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", advanced = true, showWhen = clockOn),
            Fd("clockTileAnalog", "Clock tile: analog face", "bool", scene = "pages"),
            Fd("clockTileFormat", "Clock tile: hours", "enum", options = byName<ClockFormat> { it.label }, scene = "pages"),
            Fd("clockTileDate", "Clock tile: day line", "bool", scene = "pages"),
        )
        // ---- HOME > Pages
        add(
            Fd("homePages", "Pages after Glance", "enum", options = byName<HomePages> { it.label }, scene = "pages"),
            Fd("scrollAxis", "Scroll direction", "enum", options = byName<ScrollAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Continuous")),
            Fd("contCols", "Icons across", "int", min = 2.0, max = 8.0, step = 1.0, scene = "pages", showWhen = "homePages" to listOf("Continuous")),
            Fd("pPaging", "Pages turn (portrait)", "enum", options = byName<PagingAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("lPaging", "Pages turn (landscape)", "enum", options = byName<PagingAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("pageTransition", "Page transition", "enum", options = byName<PageTransition> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("homePage", "Home button returns to page", "int", min = 0.0, max = 20.0, step = 1.0, scene = "pages", advanced = true),
            Fd("style", "Home style", "enum", options = byName<HomeStyle> { it.name }, scene = "pages", advanced = true),
            Fd("pageMode", "Page mode (older)", "enum", options = byName<PageMode> { it.label }, scene = "pages", advanced = true),
        )
        // ---- HOME > Grid
        add(
            Fd("pCols", "Columns (portrait)", "int", min = GridSpec.MIN_COLUMNS.toDouble(), max = GridSpec.MAX_COLUMNS.toDouble(), step = 1.0, scene = "pages"),
            Fd("pRows", "Rows (portrait)", "int", min = GridSpec.MIN_ROWS.toDouble(), max = GridSpec.MAX_ROWS.toDouble(), step = 1.0, scene = "pages"),
            Fd("lCols", "Columns (landscape)", "int", min = GridSpec.MIN_COLUMNS.toDouble(), max = GridSpec.MAX_COLUMNS.toDouble(), step = 1.0, scene = "pages", advanced = true),
            Fd("lRows", "Rows (landscape)", "int", min = GridSpec.MIN_ROWS.toDouble(), max = GridSpec.MAX_ROWS.toDouble(), step = 1.0, scene = "pages", advanced = true),
            Fd("adaptiveGrid", "Grid from the window size", "bool", scene = "pages"),
            Fd("gridDensity", "Adaptive density", "float", min = 0.5, max = 2.0, step = 0.05, scene = "pages", showWhen = "adaptiveGrid" to listOf("true")),
            Fd("iconScale", "Icon size", "float", min = fl(LauncherPrefs.MIN_ICON_SCALE), max = fl(LauncherPrefs.MAX_ICON_SCALE), step = 0.01, unit = "%", scene = "pages"),
            Fd("iconSpacing", "Spacing", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "pages"),
            Fd("spacingV", "Spacing down (-1 = same)", "float", min = -1.0, max = 1.0, step = 0.05, scene = "pages", advanced = true),
            Fd("gridBias", "Height in page", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "pages"),
            Fd("gridInsetX", "Side margin", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages"),
            Fd("gridInsetTop", "Top margin", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages", advanced = true),
            Fd("gridInsetBottom", "Bottom margin", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages", advanced = true),
            Fd("labels", "App names", "bool", scene = "pages"),
            Fd("labelScale", "Name size", "float", min = 0.7, max = 1.5, step = 0.05, unit = "%", scene = "pages", showWhen = "labels" to listOf("true")),
        )
        // ---- HOME > Corners and hotspots
        add(
            Fd("glanceHotspots", "Hotspots on", "corners", scene = "corners"),
            Fd("cornerStyle", "Corner style", "enum", options = byName<CornerStyle> { it.label }, scene = "corners"),
            Fd("cornerIconMode", "Corner icons", "enum", options = byName<CornerIconMode> { it.label }, scene = "corners"),
            Fd("cornerIconSize", "Corner icon size", "float", min = fl(LauncherPrefs.MIN_CORNER_ICON), max = fl(LauncherPrefs.MAX_CORNER_ICON), step = 2.0, unit = "dp", scene = "corners"),
            Fd("hotCornersOn", "Hot corners", "bool", scene = "corners"),
            Fd("hotCornerDwell", "Hot corner delay", "int", min = 50.0, max = 600.0, step = 10.0, unit = "ms", scene = "corners", showWhen = "hotCornersOn" to listOf("true")),
            Fd("cornersFollow", "Corners follow the device", "bool", scene = "corners", advanced = true),
            Fd("cornersPerOri", "Separate corners in landscape", "bool", scene = "corners", advanced = true),
        )
        for (c in Corner.entries) add(Fd("corners." + c.name, c.label, "enum", scope = "shortcuts", options = cornerActionOpts, scene = "corners",
            default = (DefaultCorners[c] as? CornerSlot.Single)?.let { ShortcutCodec.encodeItem(it.item) } ?: "",
            note = "A corner that holds an app, a palette or a widget is kept as it is."))
        // ---- HOME > Gestures
        for (g in HomeGesture.entries) add(Fd("gestures." + g.name, g.label, "enum", scope = "shortcuts", options = cornerActionOpts, scene = "glance",
            default = DefaultGestures[g]?.let(ShortcutCodec::encodeItem) ?: "", note = g.hint, advanced = g.kind == GestureKind.Motion))
        // ---- HOME > Keyboard and motion
        add(
            Fd("alwaysKeyboard", "Always-open keyboard", "bool", scene = "radar"),
            Fd("kbOpenP", "Keyboard on open (portrait)", "bool", scene = "radar"),
            Fd("kbOpenL", "Keyboard on open (landscape)", "bool", scene = "radar", advanced = true),
            Fd("motion", "Motion gestures", "bool"),
            Fd("motionBg", "Also when home is hidden", "bool", showWhen = "motion" to listOf("true")),
            Fd("motionSens", "Sensitivity", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", showWhen = "motion" to listOf("true")),
        )
        // ---- HOME > Dock (workspace file, `dock.settings`)
        add(
            Fd("dock.enabled", "Dock", "enum", scope = "dock", scene = "dock", optional = true, default = "auto",
                options = listOf(Opt("auto", "Automatic"), Opt("on", "On"), Opt("off", "Off")), note = "Automatic: off on a small tier, on otherwise."),
            Fd("dock.style", "Dock style", "enum", scope = "dock", scene = "dock", options = DockStyle.entries.map { Opt(it.id, it.label) }),
            Fd("dock.mode", "Dock content", "enum", scope = "dock", scene = "dock", options = dockEnum("DockMode")),
            Fd("dock.edge", "Dock position", "enum", scope = "dock", scene = "dock", options = dockEnum("DockEdge"), note = "Landscape only; portrait is always the bottom."),
            Fd("dock.groupShelf", "App group chips", "bool", scope = "dock", scene = "dock"),
            Fd("dock.magnify", "Magnify icons", "bool", scope = "dock", scene = "dock", note = "Icons grow under the finger or pointer, like the classic Mac dock."),
            Fd("dock.magnifyStrength", "Magnify strength", "enum", scope = "dock", scene = "dock",
                options = listOf(Opt("subtle", "Subtle (1.3x)"), Opt("medium", "Medium (1.6x)"), Opt("large", "Large (2.0x)")), note = "Only read while Magnify icons is on."),
        )

        // ---- LOOK > Theme
        add(
            Fd("themeMode", "Theme", "enum", options = byName<ThemeMode> { it.label }),
            Fd("accent", "Accent colour", "colour"),
            Fd("iconShape", "Icon shape", "enum", options = byName<IconShape> { it.label }, scene = "pages"),
            Fd("iconTint", "Icon tint", "enum", options = byName<IconTint> { it.label }, scene = "pages"),
            Fd("iconPlate", "Plate behind icons", "bool", scene = "pages"),
            Fd("uiFont", "Font", "enum", options = UiFonts.IDS.map { Opt(it, uiFontLabels.getValue(it)) }),
        )
        // ---- LOOK > Wallpaper
        val kinds = WallpaperKind.entries.map { Opt(it.id, it.label) }
        val bgSlot1 = BgItem.entries.filter { it == BgItem.None || it.living }.map { Opt(it.id, it.label) }
        val bgSlot2 = BgItem.entries.filter { it == BgItem.None || !it.living }.map { Opt(it.id, it.label) }
        add(
            Fd("wpGlanceKind", "Wallpaper", "enum", options = kinds),
            Fd("wpGlanceSolid", "Colour", "colour", showWhen = "wpGlanceKind" to listOf("solid")),
            Fd("wpGlanceFrom", "Gradient from", "colour", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("wpGlanceTo", "Gradient to", "colour", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("wpGlanceAngle", "Gradient angle", "int", min = 0.0, max = 359.0, step = 15.0, unit = "deg", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("bgSlot1", "Signal wallpaper", "enum", options = bgSlot1, showWhen = "wpGlanceKind" to listOf("live")),
            Fd("bgSlot2", "Signal effect", "enum", options = bgSlot2, showWhen = "wpGlanceKind" to listOf("live")),
        )
        l += pic("wpGlance", "Glance", "wpGlanceKind")
        add(
            Fd("wpPagesSame", "Pages use the Glance wallpaper", "bool", scene = "pages"),
            Fd("wpPagesKind", "Wallpaper", "enum", options = kinds, scene = "pages", showWhen = "wpPagesSame" to listOf("false")),
            Fd("wpPagesSolid", "Colour", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("solid")),
            Fd("wpPagesFrom", "Gradient from", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesTo", "Gradient to", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesAngle", "Gradient angle", "int", min = 0.0, max = 359.0, step = 15.0, unit = "deg", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesSlot1", "Signal wallpaper", "enum", options = bgSlot1, scene = "pages", showWhen = "wpPagesKind" to listOf("live")),
            Fd("wpPagesSlot2", "Signal effect", "enum", options = bgSlot2, scene = "pages", showWhen = "wpPagesKind" to listOf("live")),
        )
        l += pic("wpPages", "Pages", "wpPagesKind").map { Fd(it.key, it.label, it.type, scene = "pages", options = it.options, min = it.min, max = it.max, step = it.step, unit = it.unit, advanced = it.advanced, deviceBound = it.deviceBound, showWhen = it.showWhen) }
        // ---- LOOK > Calm style (signal and battery)
        val styleOpts = listOf(Opt("auto", "Automatic")) + CalmStyle.entries.map { Opt(it.name, it.label) }
        add(
            Fd("calmStyle", "Signal and battery style", "enum", options = styleOpts, optional = true, default = "auto", note = "Automatic lets the wallpaper decide."),
            Fd("calmRegular", "Also show battery and signal", "bool", showWhen = "calmStyle" to listOf("EdgeDots", "CornerBloom", "BottomGlow", "Beam")),
            Fd("indMetric", "Reads", "enum", options = byName<IndicatorMetric> { it.label }),
            Fd("indIntensity", "Intensity", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%"),
            Fd("indSpeed", "Speed", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%"),
            Fd("indLow", "Low colour", "colour", advanced = true),
            Fd("indMid", "Middle colour", "colour", advanced = true),
            Fd("indHigh", "High colour", "colour", advanced = true),
            Fd("indIdle", "Idle colour", "colour", advanced = true),
            Fd("settingsBackdrop", "Behind Settings", "enum", options = byName<SettingsBackdrop> { it.label }, scene = "look"),
            Fd("settingsDim", "Backdrop dimming", "float", min = 0.2, max = 0.95, step = 0.05, unit = "%", scene = "look", showWhen = "settingsBackdrop" to listOf("Wallpaper")),
        )

        // ---- APPS
        add(
            Fd("wheelGrouping", "Group the disc by", "enum", options = byName<WheelGrouping> { it.label }, scene = "disc"),
            Fd("wheelWorkOnly", "Work apps only", "bool", scene = "disc"),
            Fd("puckSegments", "Ring segments", "int", scope = "prefFile", file = "puck_actions", fileKey = "segments", fileType = "i", default = PuckActions.DEFAULT_SEGMENTS, min = PuckActions.MIN_SEGMENTS.toDouble(), max = PuckActions.MAX_SEGMENTS.toDouble(), step = 1.0, scene = "disc"),
            Fd("puckLabels", "Names on the ring", "bool", scope = "prefFile", file = "puck_actions", fileKey = "labels", fileType = "b", default = false, scene = "disc"),
            Fd("puckSlots", "Ring shortcuts", "slots", scope = "prefFile", file = "puck_actions", fileKey = "slots", fileType = "s", count = PuckActions.SLOTS, scene = "disc",
                options = listOf(Opt("-", "Nothing")) + PuckActions.AVAILABLE.map { Opt("a:" + it.name, it.label) }, default = PuckActions.encode(PuckActions.DEFAULT),
                note = "A segment that holds an app is kept as it is."),
            Fd("badgesOn", "Badges", "bool", scene = "pages"),
            Fd("badgeCount", "Badge numbers", "bool", scene = "pages", showWhen = "badgesOn" to listOf("true")),
        )

        // ---- RADAR
        add(
            Fd("searchLayout", "Radar layout", "enum", options = byName<SearchLayout> { it.label }, scene = "radar"),
            Fd("searchLayoutPinned", "Keep this layout with a keyboard", "bool", scene = "radar", advanced = true),
            Fd("searchMotion", "Slow drift", "bool", scene = "radar", showWhen = "searchLayout" to listOf("Filmstrip", "Orbit")),
            Fd("radarBackground", "Background", "enum", options = byName<RadarBackground> { it.label }, scene = "radar"),
            Fd("radarIntensity", "Background items", "enum", options = byName<RadarIntensity> { it.label }, scene = "radar"),
            Fd("searchWeb", "Web search", "bool", scene = "radar"),
            Fd("webEngine", "Web engine", "enum", options = WebEngine.entries.map { Opt(it.name, it.label) }, scene = "radar", showWhen = "searchWeb" to listOf("true")),
            Fd("webSuggestions", "Web suggestions", "bool", scene = "radar", showWhen = "searchWeb" to listOf("true")),
            Fd("searchContacts", "Contacts", "bool", scene = "radar"),
            Fd("searchFiles", "Files", "bool", scene = "radar"),
            Fd("searchActions", "Actions", "bool", scene = "radar"),
            Fd("searchDeep", "Deep file matches", "bool", scene = "radar", advanced = true),
            Fd("radarSignalForce", "Signal override (lab)", "enum", options = byName<SignalForce> { it.label }, scene = "radar", advanced = true),
        )

        // ---- SIDE PANEL
        add(
            Fd("leftPage", "Left of home", "enum", options = byName<LeftPage> { it.label }, scene = "comms"),
            Fd("launchpadRail", "Unread strip", "bool", scene = "comms", showWhen = "leftPage" to listOf("Launchpad")),
            Fd("lpTheme", "Comms theme", "enum", options = byName<LaunchpadTheme> { it.label }, scene = "comms", showWhen = "leftPage" to listOf("Launchpad")),
            Fd("commsScale", "Comms text size", "int", scope = "prefFile", file = CommsScaleSettings.FILE, fileKey = CommsScaleSettings.KEY, fileType = "i", default = CommsScale.DEFAULT, scene = "comms",
                min = CommsScale.MIN.toDouble(), max = CommsScale.MAX.toDouble(), step = 5.0, unit = "%"),
            Fd("rightSide", "Right of home", "enum", options = byName<RightSide> { it.label }, scene = "recents"),
            Fd("recentsLimit", "Recent apps kept", "int", min = HomeRecents.MIN_LIMIT.toDouble(), max = HomeRecents.MAX_LIMIT.toDouble(), step = 4.0, scene = "recents", showWhen = "rightSide" to listOf("RecentsPanel")),
            Fd("recentsUsage", "Add apps opened elsewhere", "bool", scene = "recents", showWhen = "rightSide" to listOf("RecentsPanel")),
            Fd("leftWidgetId", "Left page widget", "int", deviceBound = true, advanced = true, scene = "comms"),
        )

        // ---- SYSTEM
        add(
            Fd("language", "Language", "enum", options = UiLanguage.entries.map { Opt(it.name, it.label) }, scene = "look"),
            Fd("uiSounds", "Interface sounds", "bool", scene = "look"),
            Fd("uiSoundVolume", "Sound volume", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "look", showWhen = "uiSounds" to listOf("true")),
            Fd("hapticStrength", "Launcher vibration", "enum", scope = "prefFile", file = LauncherHapticSettings.FILE, fileKey = LauncherHapticSettings.KEY, fileType = "s",
                options = HapticStrength.entries.map { Opt(it.id, it.name) }, default = HapticStrength.DEFAULT.id, scene = "look"),
            Fd("hideStatusBar", "Hide the status bar", "bool"),
            Fd("restoreSurfaces", "Reopen Radar or the drawer after a restart", "bool", scene = "look"),
            Fd("onboardingDone", "First-run tour done", "bool", deviceBound = true, scene = "look", advanced = true),
        )
        return l
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Wave 58 (studioPreview, CFG-70): the tab and section of every field are the app's own
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The Settings row of the app ("topic/English title", the key `SettingsTabLayout` uses) that edits each field. The tab and the section header
     * are NOT written here: they are read from the app's placement table (`app/.../ui/SettingsTabLayout.kt`), so Studio's six tabs and their
     * sections are the app's. A field with no row of its own (the clock's edited place, a clock tile's options, the page the Home button returns to)
     * names the row next to which the app keeps it. `StudioTabsW58Test` (app) checks every row here against the real built Settings and against the
     * rows' own setters.
     */
    private fun appRowOf(key: String): String {
        Corner.entries.firstOrNull { key == "corners." + it.name }?.let { return "Corners/${it.label}" }
        HomeGesture.entries.firstOrNull { key == "gestures." + it.name }?.let { return (if (it.kind == GestureKind.Motion) "Motion/" else "Gestures/") + it.label }
        if (key.startsWith("dock.")) return "Desktop & dock/Dock"
        if (key.startsWith("wpGlance") || key == "bgSlot1" || key == "bgSlot2") return "Home/Glance wallpaper"
        if (key == "wpPagesSame") return "Home/Pages use the same wallpaper as Glance"
        if (key.startsWith("wpPages")) return "Home/Pages wallpaper"
        return APP_ROWS[key] ?: error("$key has no app Settings row in appRowOf: name the row that edits it")
    }

    private val APP_ROWS: Map<String, String> = mapOf(
        // HOME > Glance
        "glanceEnabled" to "Home/Calm home screen (Glance)", "clockStyle" to "Home/Clock style", "clockFormat" to "Home/Clock format",
        "glanceClockDate" to "Home/Show date under the clock", "glanceClockAccent" to "Home/Clock colour", "glanceClockSize" to "Home/Clock size",
        // the clock's place is set by dragging it on the Glance (edit mode); the app keeps it beside the size
        "glanceClockX" to "Home/Clock size", "glanceClockY" to "Home/Clock size", "glanceClockXLand" to "Home/Clock size", "glanceClockYLand" to "Home/Clock size",
        "showMusic" to "Home/Show music", "glanceHotspots" to "Home/Hotspots", "glanceControlsSec" to "Home/Controls hide after (seconds)", "glanceIndicator" to "Home/Surface indicator",
        // HOME > Pages
        "homePages" to "Home/Pages", "homeOrder" to "Home/Order", "contCols" to "Home/Pages", "homePage" to "Home/Pages",
        "clockTileAnalog" to "Pages/Clock tile on this page", "clockTileFormat" to "Pages/Clock tile on this page", "clockTileDate" to "Pages/Clock tile on this page",
        "pPaging" to "Home/Page turning · portrait", "lPaging" to "Home/Page turning · landscape", "pageTransition" to "Home/Page turning · portrait",
        // HOME > Layout
        "style" to "Home/Style", "scrollAxis" to "Home/Scroll direction", "adaptiveGrid" to "Home/Grid follows window size", "gridDensity" to "Home/Grid density",
        "pCols" to "Home/Default density", "pRows" to "Home/Default density", "lCols" to "Home/Default density", "lRows" to "Home/Default density",
        "iconScale" to "Home/Icon size", "spacingV" to "Home/Same spacing both ways", "iconSpacing" to "Home/Spacing", "gridBias" to "Home/Height in page",
        "gridInsetX" to "Home/Side margin", "gridInsetTop" to "Home/Top margin", "gridInsetBottom" to "Home/Bottom margin", "labelScale" to "Home/Label size",
        "labels" to "Home/App labels", "badgesOn" to "Home/Notification badges", "badgeCount" to "Home/Count in badges", "hideStatusBar" to "Home/Hide status bar",
        // HOME > Corners
        "cornerStyle" to "Corners/Corner style", "cornerIconSize" to "Corners/Corner icon size", "cornerIconMode" to "Corners/Icon type",
        "cornersFollow" to "Corners/Follow device rotation", "cornersPerOri" to "Corners/Separate corners for landscape",
        // HOME > Gestures and motion
        "motion" to "Motion/Motion gestures", "motionBg" to "Motion/When home isn't showing", "motionSens" to "Motion/Motion sensitivity",
        // HOME > Keyboard and pointer
        "alwaysKeyboard" to "Home/Always-on keyboard", "hotCornersOn" to "Corners/Hot corners with a mouse", "hotCornerDwell" to "Corners/Hot corner delay",
        // LOOK
        "themeMode" to "Theme/Light / dark", "accent" to "Theme/Accent", "uiFont" to "Theme/Font",
        "iconShape" to "Theme/Icon shape", "iconTint" to "Theme/Icon colour", "iconPlate" to "Theme/Icon plate",
        "calmStyle" to "Indicator/Calm style", "calmRegular" to "Indicator/Also show regular battery and signal", "indMetric" to "Indicator/Show",
        "indIntensity" to "Indicator/Glow: intensity", "indSpeed" to "Indicator/Glow: speed",
        "indLow" to "Indicator/Glow: colours", "indMid" to "Indicator/Glow: colours", "indHigh" to "Indicator/Glow: colours", "indIdle" to "Indicator/Glow: colours",
        "settingsBackdrop" to "Theme/Settings backdrop", "settingsDim" to "Theme/Backdrop dimming",
        // APPS
        "puckSegments" to "Apps/Ring segments", "puckSlots" to "Apps/Drawer shortcuts", "puckLabels" to "Apps/Show labels under glyphs",
        "wheelGrouping" to "Apps/Wheel groups", "wheelWorkOnly" to "Apps/Wheel groups", "pageMode" to "Apps/Pages",
        // RADAR
        "searchLayout" to "Radar/Radar layout", "radarBackground" to "Radar/Radar background", "radarIntensity" to "Radar/Radar background", "searchMotion" to "Radar/Keep moving",
        "searchLayoutPinned" to "Radar/Keep this layout with a keyboard", "kbOpenP" to "Home/Keyboard opens with Radar and drawer · portrait", "kbOpenL" to "Home/Keyboard opens with Radar and drawer · landscape",
        "searchWeb" to "Radar/Web results", "searchActions" to "Radar/Actions", "searchFiles" to "Radar/Files", "searchDeep" to "Radar/Deep results", "searchContacts" to "Radar/Contacts",
        "webEngine" to "Radar/Web engine", "webSuggestions" to "Radar/Web suggestions",
        "radarSignalForce" to "Lab/*",
        // SIDE PANEL
        "leftPage" to "Side panel/Left page", "leftWidgetId" to "Side panel/Choose widget", "lpTheme" to "Side panel/Comms theme", "commsScale" to "Side panel/Comms size",
        "launchpadRail" to "Side panel/Unread strip", "rightSide" to "Home/Right side", "recentsLimit" to "Home/Recent apps shown", "recentsUsage" to "Home/Include apps opened from elsewhere",
        // SYSTEM
        "language" to "System/Language", "restoreSurfaces" to "Home/Restore open screens",
        "uiSounds" to "Sound/Interface sounds", "uiSoundVolume" to "Sound/Volume", "hapticStrength" to "Sound/Launcher vibration",
        "onboardingDone" to "Backup/Export, import and restore",
    )

    /** One section of one tab of the app's Settings, as `SettingsTabLayout.kt` lists it: its header and its rows (exact "topic/title" keys, or patterns for rows built from an enum). */
    private class AppSection(val header: String, val rows: List<String>, val patterns: List<Regex>)
    private class AppTab(val key: String, val sections: List<AppSection>)

    /** The arguments of the call whose '(' is at [open], as raw text (string literals may hold parentheses: "Calm home screen (Glance)"). */
    private fun callArgs(t: String, open: Int): String {
        var depth = 0; var i = open; var inStr = false
        while (i < t.length) {
            val c = t[i]
            if (inStr) { if (c == '\\') i++ else if (c == '"') inStr = false }
            else when (c) { '"' -> inStr = true; '(' -> depth++; ')' -> { depth--; if (depth == 0) return t.substring(open + 1, i) } }
            i++
        }
        error("unbalanced call in SettingsTabLayout.kt at $open")
    }
    private fun literals(args: String) = Regex(""""((?:[^"\\]|\\.)*)"""").findAll(args).map { it.groupValues[1] }.toList()

    /** The app's six tabs and their sections, read from the source of `SettingsTabLayout` (the app module is not a dependency of this one). */
    private fun appLayout(): List<AppTab> {
        val src = File(root(), "app/src/main/kotlin/com/bos/sphere/ui/SettingsTabLayout.kt").readText()
        val tabKeys = Regex("""const val (\w+) = "([^"]+)"""").findAll(File(root(), "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SettingsTabs.kt").readText()).associate { it.groupValues[1] to it.groupValues[2] }
        val topics = Regex("""const val (T_\w+) = "([^"]+)"""").findAll(src).associate { it.groupValues[1] to it.groupValues[2] }
        val all = Regex("""const val ALL = "([^"]+)"""").find(src)!!.groupValues[1]
        val body = src.substringAfter("val TABS: List<Tab> = listOf(").substringBefore("\n    /**")
        val tabStarts = Regex("""Tab\(\s*SettingsTabs\.(\w+),""").findAll(body).toList()
        return tabStarts.mapIndexed { ti, tm ->
            val chunk = body.substring(tm.range.first, if (ti + 1 < tabStarts.size) tabStarts[ti + 1].range.first else body.length)
            val secStarts = Regex("""Section\("([^"]+)",""").findAll(chunk).toList()
            AppTab(tabKeys.getValue(tm.groupValues[1]), secStarts.mapIndexed { si, sm ->
                val sec = chunk.substring(sm.range.first, if (si + 1 < secStarts.size) secStarts[si + 1].range.first else chunk.length)
                val rows = ArrayList<String>(); val patterns = ArrayList<Regex>()
                for (call in Regex("""\b(rows|Row)\((T_\w+),""").findAll(sec)) {
                    val topic = topics.getValue(call.groupValues[2])
                    val args = callArgs(sec, call.range.first + call.groupValues[1].length).substringAfter(",")
                    if (call.groupValues[1] == "rows") {
                        if (args.trim() == "ALL") rows += "$topic/$all"
                        literals(args).forEach { rows += "$topic/$it" }
                    } else {
                        // Row(T_CORNERS, it.label) or Row(T_HOME, "${c.label} hotspot"): one row per enum entry
                        val lit = literals(args).firstOrNull()
                        patterns += Regex(Regex.escape("$topic/") + (lit?.split(Regex("""\$\{[^}]*\}"""))?.joinToString(".+") { Regex.escape(it) } ?: ".+"))
                    }
                }
                AppSection(sm.groupValues[1], rows, patterns)
            })
        }
    }

    /** Where the app lists [row]: (tab index, section index, row index in the section). */
    private fun placeOf(layout: List<AppTab>, row: String): Triple<Int, Int, Int>? {
        for ((ti, t) in layout.withIndex()) for ((si, s) in t.sections.withIndex()) { val r = s.rows.indexOf(row); if (r >= 0) return Triple(ti, si, r) }
        for ((ti, t) in layout.withIndex()) for ((si, s) in t.sections.withIndex()) if (s.patterns.any { it.matches(row) }) return Triple(ti, si, s.rows.size)
        return null
    }

    /** Studio's ids of the app's six tabs, in the app's order (the ids are kept from wave 56 so a saved "last tab" still opens). */
    private val tabIds = listOf("home", "look", "apps", "radar", "side", "system")

    /** Every field, its row, tab and section from the app's layout, in the app's order (tab, section, row; fields of one row in the order above). */
    private fun fields(): List<Fd> {
        val layout = appLayout()
        check(layout.size == tabIds.size) { "the app has ${layout.size} Settings tabs, Studio knows ${tabIds.size}" }
        val raw = rawFields()
        val placed = raw.mapIndexed { i, f ->
            f.row = appRowOf(f.key)
            val (ti, si, ri) = placeOf(layout, f.row) ?: error("${f.key}: the row ${f.row} is not in SettingsTabLayout")
            f.tab = tabIds[ti]; f.group = layout[ti].sections[si].header
            Triple(f, listOf(ti, si, ri), i)
        }
        return placed.sortedWith(compareBy<Triple<Fd, List<Int>, Int>>({ it.second[0] }, { it.second[1] }, { it.second[2] }, { it.third })).map { it.first }
    }

    /** Option ids and labels of an enum declared in feature-dock's DockModel.kt, read from the source (that module is not a dependency of this one). */
    private fun dockSource() = File(root(), "feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/DockModel.kt").readText()
    private fun dockEnum(name: String): List<Opt> {
        val m = Regex("""enum class $name\(val label: String\) \{([^}]*)\}""").find(dockSource()) ?: error("enum $name not found in DockModel.kt")
        return Regex("""(\w+)\("([^"]+)"\)""").findAll(m.groupValues[1]).map { Opt(it.groupValues[1], it.groupValues[2]) }.toList()
    }
    /** Field names and default literals of `data class DockSettings`. */
    private fun dockFields(): Map<String, String> {
        val body = dockSource().substringAfter("data class DockSettings(").substringBefore("\n) {")
        return Regex("""val (\w+): ([^=\n]+?) = ([^,\n]+),?""").findAll(body).associate { it.groupValues[1] to it.groupValues[3].trim() }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Building the document
    // ---------------------------------------------------------------------------------------------------------------

    /** The six tabs: the app's English key in capitals, its sections in the app's order, and a line of what Studio edits there. */
    private val tabBlurbs = mapOf(
        "home" to "The Glance and its clock, the pages and their layout, the four corners, gestures and motion, keyboard and pointer, the dock.",
        "look" to "Theme, font, icons, wallpaper, the calm styles of signal and battery, and the Settings screen.",
        "apps" to "Profiles, the app drawer disc and which apps the pages hold. App groups and hidden apps are made on the phone.",
        "radar" to "Search: layout, appearance, input, sources, the web engine.",
        "side" to "The left panel and Comms, and what the right side of home shows.",
        "system" to "Language, sound and vibration, restoring open screens, and what stays on the phone.",
    )
    private val tabs: List<Triple<String, String, String>> get() = appLayout().mapIndexed { i, t -> Triple(tabIds[i], t.key.uppercase(), tabBlurbs.getValue(tabIds[i])) }

    private fun defaultOf(f: Fd, d: Map<String, Any?>): Any? = when {
        f.default !== UNSET -> f.default
        f.scope == "dock" -> dockDefault(f.key.removePrefix("dock."))
        f.key == "glanceHotspots" -> d[f.key]
        else -> d[f.key]
    }

    private fun dockDefault(name: String): Any? {
        val lit = dockFields()[name] ?: error("DockSettings has no $name")
        return when (name) {
            "enabled" -> if (lit == "null") "auto" else if (lit == "true") "on" else "off"
            "groupShelf", "magnify" -> lit.toBooleanStrict()
            "magnifyStrength" -> lit.substringAfterLast('.').lowercase()
            "style" -> DockStyle.valueOf(lit.substringAfterLast('.')).id
            else -> lit.substringAfterLast('.')
        }
    }

    private fun document(): Map<String, Any?> {
        val d = exportedDefaults()
        val fs = fields()
        val outFields = fs.map { f ->
            val m = LinkedHashMap<String, Any?>()
            m["key"] = f.key
            m["label"] = f.label
            m["tab"] = f.tab
            m["group"] = f.group
            m["row"] = f.row
            m["type"] = f.type
            m["scope"] = f.scope
            if (f.file != null) { m["file"] = f.file; m["fileKey"] = f.fileKey; m["fileType"] = f.fileType }
            f.count?.let { m["count"] = it }
            if (f.options.isNotEmpty()) m["options"] = f.options.map { mapOf("id" to it.id, "label" to it.label) }
            f.min?.let { m["min"] = it }; f.max?.let { m["max"] = it }; f.step?.let { m["step"] = it }; f.unit?.let { m["unit"] = it }
            m["default"] = if (f.deviceBound) null else defaultOf(f, d)
            m["scene"] = f.scene
            if (f.optional) m["optional"] = true
            if (f.advanced) m["advanced"] = true
            if (f.deviceBound) m["deviceBound"] = true
            f.showWhen?.let { m["showWhen"] = mapOf("key" to it.first, "in" to it.second) }
            f.note?.let { m["note"] = it }
            m
        }
        return linkedMapOf(
            "about" to "Generated by core-data StudioSchemaW56Test (STUDIO_REGEN=1). Do not edit by hand: the keys, defaults and option ids come from the code.",
            "format" to StateSchema.SETTINGS_FORMAT,
            "settingsVersion" to StateSchema.SETTINGS_CURRENT,
            "stateFormat" to StateSchema.FORMAT,
            "stateSchemaVersion" to StateSchema.CURRENT,
            "tabs" to appLayout().let { lay -> tabs.mapIndexed { i, it -> linkedMapOf("id" to it.first, "label" to it.second, "blurb" to it.third, "sections" to lay[i].sections.map { s -> s.header }) } },
            "fields" to outFields,
            // Wave 57 (profiles, CFG-49): which schema keys each settings group of a phone profile carries (ProfileGroup), so Studio reads a profile's subset.
            "profileGroups" to ProfileGroup.entries.map { mapOf("id" to it.id, "label" to it.label, "keys" to it.keys) },
            // Wave 58 (CFG-71): the phone's Profiles page, as Studio's Profiles panel shows it: the starters and the app group suggester's own tables.
            "profileStarts" to ProfileStart.entries.map { st ->
                linkedMapOf("id" to st.name, "label" to st.label, "detail" to st.detail, "carries" to ProfileStarters.carries(st).map { it.id }, "settings" to ProfileStarters.overrides(st).toSortedMap())
            },
            "appSuggest" to linkedMapOf(
                "min" to AppGroupSuggester.GROUP_MIN, "max" to AppGroupSuggester.GROUP_MAX,
                "kinds" to SuggestionKind.entries.map { k -> linkedMapOf("id" to k.name, "label" to k.label, "packages" to suggestTable(k)) },
                // the Calm starter's calm-centre apps: messaging and alert apps (AppGroupSuggester.calmPackages)
                "calm" to (AppGroupSuggester.MESSAGING_PACKAGES + AppGroupSuggester.ALERT_PACKAGES).distinct(),
            ),
        )
    }

    /** The suggester's package table of [k] (the same lists, in the same order, as `AppGroupSuggester`'s TABLES; a test classifies every entry). */
    private fun suggestTable(k: SuggestionKind): List<String> = when (k) {
        SuggestionKind.Work -> AppGroupSuggester.WORK_PACKAGES
        SuggestionKind.Personal -> AppGroupSuggester.MESSAGING_PACKAGES + AppGroupSuggester.SOCIAL_PACKAGES
        SuggestionKind.Media -> AppGroupSuggester.MEDIA_PACKAGES
        SuggestionKind.Travel -> AppGroupSuggester.TRAVEL_PACKAGES
        SuggestionKind.Money -> AppGroupSuggester.MONEY_PACKAGES
        SuggestionKind.Games -> AppGroupSuggester.GAME_PACKAGES
        SuggestionKind.Utilities -> AppGroupSuggester.UTILITY_PACKAGES
    }

    // A small ordered JSON writer (org.json's object does not keep the order).
    private fun num(n: Number): String {
        val dv = n.toDouble()
        return if (dv == Math.floor(dv) && abs(dv) < 1e15) dv.toLong().toString() else {
            val s = (if (n is Float) n.toString().toDouble() else dv).toString()
            s
        }
    }
    private fun esc(s: String) = buildString {
        append('"'); for (c in s) when { c == '"' -> append("\\\""); c == '\\' -> append("\\\\"); c == '\n' -> append("\\n"); c.code < 0x20 -> append("\\u%04x".format(c.code)); else -> append(c) }; append('"')
    }
    private fun write(v: Any?, sb: StringBuilder, ind: Int) {
        val pad = "  ".repeat(ind + 1); val end = "  ".repeat(ind)
        when (v) {
            null -> sb.append("null")
            is String -> sb.append(esc(v))
            is Boolean -> sb.append(v)
            is Number -> sb.append(num(v))
            is Map<*, *> -> if (v.isEmpty()) sb.append("{}") else {
                // A short flat object stays on one line so the file is readable.
                val flat = v.values.all { it == null || it is String || it is Boolean || it is Number || (it is List<*> && it.all { x -> x is String }) }
                if (flat && v.size <= 4) { sb.append("{"); v.entries.forEachIndexed { i, e -> if (i > 0) sb.append(", "); sb.append(esc(e.key.toString())).append(": "); write(e.value, sb, ind + 1) }; sb.append("}") }
                else { sb.append("{\n"); v.entries.forEachIndexed { i, e -> sb.append(pad).append(esc(e.key.toString())).append(": "); write(e.value, sb, ind + 1); sb.append(if (i < v.size - 1) ",\n" else "\n") }; sb.append(end).append("}") }
            }
            is List<*> -> if (v.isEmpty()) sb.append("[]") else {
                if (v.all { it is String || it is Number }) { sb.append("["); v.forEachIndexed { i, x -> if (i > 0) sb.append(", "); write(x, sb, ind + 1) }; sb.append("]") }
                else { sb.append("[\n"); v.forEachIndexed { i, x -> sb.append(pad); write(x, sb, ind + 1); sb.append(if (i < v.size - 1) ",\n" else "\n") }; sb.append(end).append("]") }
            }
            else -> error("unsupported ${v::class}")
        }
    }
    private fun json(): String = StringBuilder().also { write(document(), it, 0); it.append('\n') }.toString()
    private fun js(json: String) = "/* Generated with schema.json by core-data StudioSchemaW56Test. Loaded as a plain script so the page also works from a file:// URL. */\nwindow.LIFTOFF_SCHEMA = " + json.trimEnd() + ";\n"

    // ---------------------------------------------------------------------------------------------------------------
    // Fixtures: real output of the app's own codec (for the node tests), and the app's reading of what Studio writes
    // ---------------------------------------------------------------------------------------------------------------

    private fun sampleSettings(): JSONObject = SettingsCodec.encode(
        LauncherPrefs(
            themeMode = ThemeMode.Amoled, accentColor = 0xFF39FF88, glanceHotspots = setOf(Corner.BottomRight, Corner.BottomLeft), calmStyle = CalmStyle.Beam, uiFont = "jetbrains_mono",
            clockStyle = ClockStyle.BigDigitalLine, glanceClockDate = true, homePages = HomePages.Paginated, iconShape = IconShape.Squircle, searchLayout = SearchLayout.Orbit,
            onboardingDone = true, leftWidgetId = 12,
        ),
    )

    private fun sampleBackup(): JSONObject {
        val corners = DefaultCorners.toMutableMap().also {
            it[Corner.TopLeft] = CornerSlot.Single(CornerItem.Action(CornerActionId.Phone))
            it[Corner.TopRight] = CornerSlot.Single(CornerItem.App("com.example.notes/.Main"))
        }
        val gestures = HomeGesture.entries.associateWith { DefaultGestures[it] }.toMutableMap<HomeGesture, CornerItem?>().also {
            it[HomeGesture.DoubleTap] = null
            it[HomeGesture.Shake] = CornerItem.App("com.example.torch")
        }
        val dock = JSONObject()
            .put("settings", JSONObject().put("enabled", JSONObject.NULL).put("mode", "PinnedPlusRecent").put("edge", "Bottom").put("groupShelf", true).put("style", "transparent"))
            .put("activeId", "w1").put("groups", JSONArray().put(JSONObject().put("id", "g1").put("name", "Work").put("layout", "Grid").put("members", JSONArray())))
        val files = JSONObject()
            .put("comms_scale", JSONObject().put("pct", JSONObject().put("t", "i").put("v", 110)))
            .put("puck_actions", JSONObject().put("segments", JSONObject().put("t", "i").put("v", 7)).put("labels", JSONObject().put("t", "b").put("v", true))
                .put("slots", JSONObject().put("t", "s").put("v", PuckActions.encode(PuckActions.DEFAULT))))
            .put("home_state", JSONObject().put("dock", JSONObject().put("t", "set").put("v", JSONArray())))
        val parts = StateParts(
            prefs = LauncherPrefs(themeMode = ThemeMode.Light, homePages = HomePages.Continuous), corners = corners, gestures = gestures,
            extras = StateExtras(
                opaque = mapOf("workspaces" to JSONArray().put(JSONObject().put("id", "w1").put("name", "Main"))),
                unknownTop = JSONObject().put("futureSection", JSONObject().put("a", 1)), unknownPrefs = JSONObject().put("futureInSettings", 1),
                objects = mapOf("dock" to dock, "prefFiles" to files),
            ),
        )
        return StateCodec.export(parts, 1700000000000L, "Liftoff test").json
    }

    private fun regenOr(name: String, expected: JSONObject) {
        val f = File(studio, "tests/$name")
        if (System.getenv("STUDIO_REGEN") == "1") { f.parentFile.mkdirs(); f.writeText(expected.toString(2) + "\n") }
        assertTrue("web/liftoff-studio/tests/$name is missing: run with STUDIO_REGEN=1", f.isFile)
        assertTrue("web/liftoff-studio/tests/$name no longer matches what the app's codec writes: run with STUDIO_REGEN=1", expected.similar(JSONObject(f.readText())))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun CFG_34_schemaJsonAndSchemaJsAreExactlyWhatTheCodeGenerates() {
        val j = json()
        if (System.getenv("STUDIO_REGEN") == "1") {
            studio.mkdirs()
            File(studio, "schema.json").writeText(j)
            File(studio, "schema.js").writeText(js(j))
        }
        val onDisk = File(studio, "schema.json")
        assertTrue("web/liftoff-studio/schema.json is missing: run with STUDIO_REGEN=1", onDisk.isFile)
        assertEquals("schema.json is out of date with the code: run with STUDIO_REGEN=1 and commit it", j, onDisk.readText())
        assertEquals("schema.js is out of date with the code: run with STUDIO_REGEN=1 and commit it", js(j), File(studio, "schema.js").readText())
    }

    @Test fun CFG_34_everyKeyTheSettingsExportCarriesIsInTheSchema() {
        val exported = exportedDefaults().keys + "calmStyle"
        val inSchema = fields().filter { it.scope == "settings" }.map { it.key }.toSet()
        assertEquals("exported by the app but missing from the Studio schema", emptyList<String>(), (exported - inSchema).sorted())
    }

    @Test fun CFG_34_everySchemaKeyOfTheSettingsDocumentIsARealKeyOrADeviceBoundOne() {
        val exported = exportedDefaults().keys + "calmStyle"
        val bound = StateSchema.DEVICE_BOUND_PREF_KEYS
        for (f in fields().filter { it.scope == "settings" }) {
            if (f.deviceBound) assertTrue("${f.key} is flagged device-bound but StateSchema does not say so", f.key in bound)
            else assertTrue("${f.key} is in the schema but the export does not carry it", f.key in exported)
        }
        // And the other way: what the export keeps off the file is exactly the flagged set.
        val flagged = fields().filter { it.deviceBound }.map { it.key }.toSet()
        val writtenBound = bound.filter { k -> k in setOf("leftWidgetId", "onboardingDone") || k.startsWith("wp") }.toSet()
        assertEquals("a device-bound key is missing its flag in the schema", emptyList<String>(), (writtenBound - flagged).sorted())
        for (k in flagged) assertTrue("$k never reaches the file", k !in exported)
    }

    @Test fun CFG_34_theSchemaDefaultsAreTheAppDefaultsAndEveryValueIsInsideItsRangeOrOptions() {
        val d = exportedDefaults()
        for (f in fields().filter { !it.deviceBound }) {
            val v = defaultOf(f, d)
            when (f.type) {
                "enum" -> assertTrue("${f.key} default $v is not one of its options", f.options.any { it.id == v })
                "bool" -> assertTrue("${f.key} default must be a boolean", v is Boolean)
                "int", "float" -> {
                    val n = (v as? Number)?.toDouble() ?: error("${f.key} default is not a number: $v")
                    if (f.min != null && f.max != null) assertTrue("${f.key} default $n is outside ${f.min}..${f.max}", n >= f.min - 1e-9 && n <= f.max + 1e-9)
                }
                "colour" -> assertTrue("${f.key} default must be an ARGB number", (v as? Number)?.toLong()?.let { it in 0..0xFFFFFFFFL } == true)
                "corners", "slots", "string" -> assertTrue("${f.key} default must be a string", v is String)
            }
        }
    }

    @Test fun CFG_34_theEnumOptionsAreTheIdsTheAppStoresAndTheDockKeysAreTheDockSettingsFields() {
        assertEquals(setOf("enabled", "mode", "edge", "groupShelf", "style", "magnify", "magnifyStrength"), dockFields().keys)
        assertEquals(dockFields().keys.map { "dock.$it" }.toSet(), fields().filter { it.scope == "dock" }.map { it.key }.toSet())
        assertEquals(listOf("Collapsed", "PinnedPlusRecent"), dockEnum("DockMode").map { it.id })
        assertEquals(listOf("Bottom", "Left", "Right"), dockEnum("DockEdge").map { it.id })
        // The ids the app writes for the id-based enums (a theme pack and a settings file spell them this way).
        assertEquals(listOf("solid", "gradient", "live", "system", "picture"), WallpaperKind.entries.map { it.id })
        assertEquals(listOf("standard", "transparent", "3d"), DockStyle.entries.map { it.id })
        // Every key the exported default document would write for an enum parses back to one of the schema options.
        val d = exportedDefaults()
        for (f in fields().filter { it.scope == "settings" && it.type == "enum" && !it.deviceBound && !it.optional }) assertTrue("${f.key} = ${d[f.key]}", f.options.any { it.id == d[f.key] })
    }

    @Test fun CFG_34_thePrefFileAndShortcutKeysAreTheOnesTheExportAllowsAndSixTabsHoldEveryField() {
        for (f in fields().filter { it.scope == "prefFile" }) assertTrue("${f.file} is not in the export allow-list", f.file in PrefFilesSource.ALLOWED)
        assertEquals(Corner.entries.map { "corners." + it.name }, fields().filter { it.key.startsWith("corners.") }.map { it.key })
        assertEquals(HomeGesture.entries.map { "gestures." + it.name }, fields().filter { it.key.startsWith("gestures.") }.map { it.key })
        assertEquals(listOf("home", "look", "apps", "radar", "side", "system"), tabs.map { it.first })
        assertEquals(listOf("HOME", "LOOK", "APPS", "RADAR", "SIDE PANEL", "SYSTEM"), tabs.map { it.second })
        val ids = tabs.map { it.first }.toSet()
        for (f in fields()) assertTrue("${f.key} names the unknown tab ${f.tab}", f.tab in ids)
        for (t in ids) assertTrue("tab $t is empty", fields().any { it.tab == t && !it.deviceBound })
        assertEquals("duplicate keys", emptyList<String>(), fields().groupBy { it.key }.filterValues { it.size > 1 }.keys.toList())
    }

    @Test fun CFG_70_studioTabsAndSectionsAreTheAppsSettingsTabsAndSectionsInTheAppsOrder() {
        val layout = appLayout()
        assertEquals("the app's tab keys", listOf("Home", "Look", "Apps", "Radar", "Side panel", "System"), layout.map { it.key })
        val doc = JSONObject(json())
        val tabsOut = doc.getJSONArray("tabs")
        for (i in layout.indices) {
            val t = tabsOut.getJSONObject(i)
            assertEquals(layout[i].key.uppercase(), t.getString("label"))
            val secs = t.getJSONArray("sections"); assertEquals("sections of ${t.getString("id")}", layout[i].sections.map { it.header }, List(secs.length()) { secs.getString(it) })
        }
        // every field sits in its row's tab and section, and the fields of a tab follow the app's section order
        var last = Pair(-1, -1)
        for (f in fields()) {
            val (ti, si, _) = placeOf(layout, f.row)!!
            assertEquals(f.key, tabIds[ti], f.tab); assertEquals(f.key, layout[ti].sections[si].header, f.group)
            assertTrue("${f.key} is out of the app's order", ti > last.first || (ti == last.first && si >= last.second)); last = ti to si
        }
        // the parser sees the rows a reader of SettingsTabLayout.kt sees
        assertEquals(Triple(0, 0, 0), placeOf(layout, "Home/Calm home screen (Glance)"))
        assertEquals("Input", layout[3].sections[placeOf(layout, "Home/Keyboard opens with Radar and drawer · portrait")!!.second].header)
        assertEquals("Gestures and motion", layout[0].sections[placeOf(layout, "Gestures/Swipe up")!!.second].header)
        assertEquals("Glance", layout[0].sections[placeOf(layout, "Home/Top left hotspot")!!.second].header)
        assertEquals("Lab", layout[5].sections[placeOf(layout, "Lab/*")!!.second].header)
    }

    @Test fun CFG_71_theSuggesterTablesAndStartersInTheSchemaAreTheAppsOwn() {
        for (k in SuggestionKind.entries) for (pkg in suggestTable(k))
            assertEquals(pkg, k, AppGroupSuggester.classify(SuggestApp("$pkg/.Main#0", pkg, pkg)))
        val calm = (AppGroupSuggester.MESSAGING_PACKAGES + AppGroupSuggester.ALERT_PACKAGES).distinct()
        assertEquals(calm.toSet(), AppGroupSuggester.calmPackages(calm.map { SuggestApp("$it/.Main#0", it, it) }))
        assertEquals(ProfileStart.entries.map { it.name }, listOf("Current", "Empty", "Work", "Calm", "Desk"))
        for (st in ProfileStart.entries) for (k in ProfileStarters.overrides(st).keys) assertTrue("$k of ${st.name} is a profile key", k in ProfileSettings.ALL_KEYS)
    }

    @Test fun CFG_34_theSampleFilesTheNodeTestsReadAreWhatTheAppsOwnCodecWrites() {
        regenOr("sample-settings.json", sampleSettings())
        regenOr("sample-backup.json", sampleBackup())
    }

    @Test fun CFG_34_theFilesStudioWritesAreReadByTheAppsOwnCodec() {
        // Written by web/liftoff-studio/tests/make-fixtures.js from a profile with known values; the app's decoder must see exactly those values.
        val settingsText = File(studio, "tests/fixtures/studio-settings.json").readText()
        val backupText = File(studio, "tests/fixtures/studio-backup.json").readText()
        val a = StateCodec.decode(StateDocument.parse(settingsText)).parts
        assertEquals(ThemeMode.Light, a.prefs.themeMode)
        assertEquals(0xFF112233L, a.prefs.accentColor)
        assertEquals(ClockStyle.AnalogMinimal, a.prefs.clockStyle)
        assertEquals(0.35f, a.prefs.glanceClockX, 1e-6f)
        assertEquals(setOf(Corner.BottomRight, Corner.BottomLeft), a.prefs.glanceHotspots)
        assertEquals(CalmStyle.Beam, a.prefs.calmStyle)
        assertEquals("lexend", a.prefs.uiFont)
        assertEquals(WallpaperKind.Gradient, a.prefs.glanceWallpaperKind)
        assertEquals(HomePages.Continuous, a.prefs.homePages)
        assertTrue("an unknown field survives the app's import", a.extras.unknownPrefs.has("studioKeptField"))
        // the settings-only file carries no dock, corners or files
        assertEquals(DefaultCorners[Corner.TopLeft], a.corners[Corner.TopLeft])

        val b = StateCodec.decode(StateDocument.parse(backupText))
        assertEquals(ThemeMode.Light, b.parts.prefs.themeMode)
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.Flashlight)), b.parts.corners[Corner.TopLeft])
        assertEquals(CornerItem.Action(CornerActionId.Search), b.parts.gestures[HomeGesture.PinchOut])
        assertEquals(null, b.parts.gestures[HomeGesture.DoubleTap])
        assertEquals(setOf("settings", "dock", "shortcuts", "prefFiles"), b.present - setOf("format", "schemaVersion", "exportedAt", "app"))
        val dock = b.parts.extras.objects.getValue("dock").getJSONObject("settings")
        assertEquals("3d", dock.getString("style")); assertEquals(false, dock.getBoolean("enabled"))
        assertEquals("Left", dock.getString("edge"))
        val pf = b.parts.extras.objects.getValue("prefFiles")
        assertEquals(115, pf.getJSONObject("comms_scale").getJSONObject("pct").getInt("v"))
        assertEquals("strong", pf.getJSONObject("launcher_haptics").getJSONObject("strength").getString("v"))
        assertEquals(6, pf.getJSONObject("puck_actions").getJSONObject("segments").getInt("v"))
        // the dock settings Studio wrote parse with the dock's own enum names
        assertTrue(DockStyle.forId(dock.getString("style")) == DockStyle.ThreeD)
        assertTrue(dockEnum("DockEdge").any { it.id == dock.getString("edge") } && dockEnum("DockMode").any { it.id == dock.getString("mode") })
        // and the settings-only decode of the Backup agrees with the settings file
        assertEquals(a.prefs.copy(), SettingsCodec.decode(JSONObject(backupText).getJSONObject("settings")).prefs.copy())
    }
}
