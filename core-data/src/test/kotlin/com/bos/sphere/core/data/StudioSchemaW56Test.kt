package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Wave 56 (studio), CFG-26: the Liftoff Studio (web/liftoff-studio, a static page that edits a configuration off the device) reads
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
        val key: String, val label: String, val tab: String, val group: String, val type: String,
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
    )
    private companion object { val UNSET = Any() }

    private inline fun <reified E : Enum<E>> byName(vararg skip: E, label: (E) -> String): List<Opt> = enumValues<E>().filter { it !in skip }.map { Opt(it.name, label(it)) }

    private fun fl(f: Float) = f.toString().toDouble()

    private val uiFontLabels = mapOf(
        "space_grotesk" to "Space Grotesk", "system" to "System font", "inter" to "Inter", "manrope" to "Manrope", "dm_sans" to "DM Sans",
        "jetbrains_mono" to "JetBrains Mono", "atkinson" to "Atkinson Hyperlegible", "lexend" to "Lexend",
    )
    private fun pic(p: String, surface: String, kindKey: String): List<Fd> {
        val tab = "look"; val g = "Wallpaper: $surface"
        val showPic = kindKey to listOf("picture")
        return listOf(
            Fd(p + "PicFit", "Picture fit", tab, g, "enum", options = PictureFit.entries.map { Opt(it.id, it.label) }, showWhen = showPic, advanced = true),
            Fd(p + "PicDim", "Picture dim", tab, g, "int", min = 0.0, max = 80.0, step = 10.0, unit = "%", showWhen = showPic, advanced = true),
            Fd(p + "PicGif", "Animated picture", tab, g, "enum", options = GifMode.entries.map { Opt(it.id, it.label) }, showWhen = showPic, advanced = true),
            Fd(p + "SysDim", "Dim over the system wallpaper", tab, g, "int", min = 0.0, max = 80.0, step = 10.0, unit = "%", showWhen = kindKey to listOf("system"), advanced = true),
            // The file itself and what describes it belong to this device (StateSchema.WALLPAPER_PICTURE_KEYS).
            Fd(p + "Pic", "Picture file", tab, g, "string", deviceBound = true, advanced = true),
            Fd(p + "PicAnim", "Picture is animated", tab, g, "bool", deviceBound = true, advanced = true),
            Fd(p + "PicVid", "Picture is a video", tab, g, "bool", deviceBound = true, advanced = true),
            Fd(p + "PicMs", "Picture duration", tab, g, "int", deviceBound = true, advanced = true, unit = "ms"),
            Fd(p + "PicOk", "Picture power check", tab, g, "bool", deviceBound = true, advanced = true),
            Fd(p + "PicBounce", "Picture bounce support", tab, g, "string", deviceBound = true, advanced = true),
        )
    }

    private val cornerActionOpts: List<Opt> = listOf(Opt("", "Nothing")) + CornerActionId.entries.map { Opt("action:" + it.name, it.label) }

    private fun fields(): List<Fd> {
        val home = "home"; val look = "look"; val apps = "apps"; val radar = "radar"; val side = "side"; val sys = "system"
        val clockOn = "clockStyle" to ClockStyle.entries.filter { it != ClockStyle.Off }.map { it.name }
        val digital = "clockStyle" to ClockStyle.entries.filter { it.digital }.map { it.name }
        val l = ArrayList<Fd>()
        fun add(vararg f: Fd) { l += f }

        // ---- HOME > Glance
        add(
            Fd("glanceEnabled", "Glance", home, "Glance", "bool"),
            Fd("homeOrder", "Order", home, "Glance", "enum", options = byName<HomeOrder> { it.label }),
            Fd("showMusic", "Music line", home, "Glance", "bool"),
            Fd("glanceControlsSec", "Music controls stay", home, "Glance", "int", min = GlanceControls.MIN_SECONDS.toDouble(), max = GlanceControls.MAX_SECONDS.toDouble(), step = 1.0, unit = "s"),
            Fd("glanceIndicator", "Surface indicator", home, "Glance", "bool"),
        )
        // ---- HOME > Clock
        add(
            Fd("clockStyle", "Clock style", home, "Clock", "enum", options = byName<ClockStyle> { it.label }),
            Fd("clockFormat", "Hours", home, "Clock", "enum", options = byName<ClockFormat> { it.label }, showWhen = digital),
            Fd("glanceClockDate", "Show the date", home, "Clock", "bool", showWhen = clockOn),
            Fd("glanceClockAccent", "Accent colour", home, "Clock", "bool", showWhen = clockOn),
            Fd("glanceClockSize", "Size", home, "Clock", "enum", options = byName<ClockSize> { it.label }, showWhen = clockOn),
            Fd("glanceClockX", "Position across (portrait)", home, "Clock", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", showWhen = clockOn),
            Fd("glanceClockY", "Position down (portrait)", home, "Clock", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", showWhen = clockOn),
            Fd("glanceClockXLand", "Position across (landscape)", home, "Clock", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", advanced = true, showWhen = clockOn),
            Fd("glanceClockYLand", "Position down (landscape)", home, "Clock", "float", min = 0.0, max = 1.0, step = 0.01, unit = "%", advanced = true, showWhen = clockOn),
            Fd("clockTileAnalog", "Clock tile: analog face", home, "Clock tile", "bool", scene = "pages"),
            Fd("clockTileFormat", "Clock tile: hours", home, "Clock tile", "enum", options = byName<ClockFormat> { it.label }, scene = "pages"),
            Fd("clockTileDate", "Clock tile: day line", home, "Clock tile", "bool", scene = "pages"),
        )
        // ---- HOME > Pages
        add(
            Fd("homePages", "Pages after Glance", home, "Pages", "enum", options = byName<HomePages> { it.label }, scene = "pages"),
            Fd("scrollAxis", "Scroll direction", home, "Pages", "enum", options = byName<ScrollAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Continuous")),
            Fd("contCols", "Icons across", home, "Pages", "int", min = 2.0, max = 8.0, step = 1.0, scene = "pages", showWhen = "homePages" to listOf("Continuous")),
            Fd("pPaging", "Pages turn (portrait)", home, "Pages", "enum", options = byName<PagingAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("lPaging", "Pages turn (landscape)", home, "Pages", "enum", options = byName<PagingAxis> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("pageTransition", "Page transition", home, "Pages", "enum", options = byName<PageTransition> { it.label }, scene = "pages", showWhen = "homePages" to listOf("Paginated")),
            Fd("homePage", "Home button returns to page", home, "Pages", "int", min = 0.0, max = 20.0, step = 1.0, scene = "pages", advanced = true),
            Fd("style", "Home style", home, "Pages", "enum", options = byName<HomeStyle> { it.name }, scene = "pages", advanced = true),
            Fd("pageMode", "Page mode (older)", home, "Pages", "enum", options = byName<PageMode> { it.label }, scene = "pages", advanced = true),
        )
        // ---- HOME > Grid
        add(
            Fd("pCols", "Columns (portrait)", home, "Page grid", "int", min = GridSpec.MIN_COLUMNS.toDouble(), max = GridSpec.MAX_COLUMNS.toDouble(), step = 1.0, scene = "pages"),
            Fd("pRows", "Rows (portrait)", home, "Page grid", "int", min = GridSpec.MIN_ROWS.toDouble(), max = GridSpec.MAX_ROWS.toDouble(), step = 1.0, scene = "pages"),
            Fd("lCols", "Columns (landscape)", home, "Page grid", "int", min = GridSpec.MIN_COLUMNS.toDouble(), max = GridSpec.MAX_COLUMNS.toDouble(), step = 1.0, scene = "pages", advanced = true),
            Fd("lRows", "Rows (landscape)", home, "Page grid", "int", min = GridSpec.MIN_ROWS.toDouble(), max = GridSpec.MAX_ROWS.toDouble(), step = 1.0, scene = "pages", advanced = true),
            Fd("adaptiveGrid", "Grid from the window size", home, "Page grid", "bool", scene = "pages"),
            Fd("gridDensity", "Adaptive density", home, "Page grid", "float", min = 0.5, max = 2.0, step = 0.05, scene = "pages", showWhen = "adaptiveGrid" to listOf("true")),
            Fd("iconScale", "Icon size", home, "Page grid", "float", min = fl(LauncherPrefs.MIN_ICON_SCALE), max = fl(LauncherPrefs.MAX_ICON_SCALE), step = 0.01, unit = "%", scene = "pages"),
            Fd("iconSpacing", "Spacing", home, "Page grid", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "pages"),
            Fd("spacingV", "Spacing down (-1 = same)", home, "Page grid", "float", min = -1.0, max = 1.0, step = 0.05, scene = "pages", advanced = true),
            Fd("gridBias", "Height in page", home, "Page grid", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "pages"),
            Fd("gridInsetX", "Side margin", home, "Page grid", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages"),
            Fd("gridInsetTop", "Top margin", home, "Page grid", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages", advanced = true),
            Fd("gridInsetBottom", "Bottom margin", home, "Page grid", "float", min = 0.0, max = 0.25, step = 0.01, unit = "%", scene = "pages", advanced = true),
            Fd("labels", "App names", home, "Page grid", "bool", scene = "pages"),
            Fd("labelScale", "Name size", home, "Page grid", "float", min = 0.7, max = 1.5, step = 0.05, unit = "%", scene = "pages", showWhen = "labels" to listOf("true")),
        )
        // ---- HOME > Corners and hotspots
        add(
            Fd("glanceHotspots", "Hotspots on", home, "Corners and hotspots", "corners", scene = "corners"),
            Fd("cornerStyle", "Corner style", home, "Corners and hotspots", "enum", options = byName<CornerStyle> { it.label }, scene = "corners"),
            Fd("cornerIconMode", "Corner icons", home, "Corners and hotspots", "enum", options = byName<CornerIconMode> { it.label }, scene = "corners"),
            Fd("cornerIconSize", "Corner icon size", home, "Corners and hotspots", "float", min = fl(LauncherPrefs.MIN_CORNER_ICON), max = fl(LauncherPrefs.MAX_CORNER_ICON), step = 2.0, unit = "dp", scene = "corners"),
            Fd("hotCornersOn", "Hot corners", home, "Corners and hotspots", "bool", scene = "corners"),
            Fd("hotCornerDwell", "Hot corner delay", home, "Corners and hotspots", "int", min = 50.0, max = 600.0, step = 10.0, unit = "ms", scene = "corners", showWhen = "hotCornersOn" to listOf("true")),
            Fd("cornersFollow", "Corners follow the device", home, "Corners and hotspots", "bool", scene = "corners", advanced = true),
            Fd("cornersPerOri", "Separate corners in landscape", home, "Corners and hotspots", "bool", scene = "corners", advanced = true),
        )
        for (c in Corner.entries) add(Fd("corners." + c.name, c.label, home, "Corner actions", "enum", scope = "shortcuts", options = cornerActionOpts, scene = "corners",
            default = (DefaultCorners[c] as? CornerSlot.Single)?.let { ShortcutCodec.encodeItem(it.item) } ?: "",
            note = "A corner that holds an app, a palette or a widget is kept as it is."))
        // ---- HOME > Gestures
        for (g in HomeGesture.entries) add(Fd("gestures." + g.name, g.label, home, "Gestures", "enum", scope = "shortcuts", options = cornerActionOpts, scene = "glance",
            default = DefaultGestures[g]?.let(ShortcutCodec::encodeItem) ?: "", note = g.hint, advanced = g.kind == GestureKind.Motion))
        // ---- HOME > Keyboard and motion
        add(
            Fd("alwaysKeyboard", "Always-open keyboard", home, "Keyboard", "bool", scene = "radar"),
            Fd("kbOpenP", "Keyboard on open (portrait)", home, "Keyboard", "bool", scene = "radar"),
            Fd("kbOpenL", "Keyboard on open (landscape)", home, "Keyboard", "bool", scene = "radar", advanced = true),
            Fd("motion", "Motion gestures", home, "Motion", "bool"),
            Fd("motionBg", "Also when home is hidden", home, "Motion", "bool", showWhen = "motion" to listOf("true")),
            Fd("motionSens", "Sensitivity", home, "Motion", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", showWhen = "motion" to listOf("true")),
        )
        // ---- HOME > Dock (workspace file, `dock.settings`)
        add(
            Fd("dock.enabled", "Dock", home, "Dock", "enum", scope = "dock", scene = "dock", optional = true, default = "auto",
                options = listOf(Opt("auto", "Automatic"), Opt("on", "On"), Opt("off", "Off")), note = "Automatic: off on a small tier, on otherwise."),
            Fd("dock.style", "Dock style", home, "Dock", "enum", scope = "dock", scene = "dock", options = DockStyle.entries.map { Opt(it.id, it.label) }),
            Fd("dock.mode", "Dock content", home, "Dock", "enum", scope = "dock", scene = "dock", options = dockEnum("DockMode")),
            Fd("dock.edge", "Dock position", home, "Dock", "enum", scope = "dock", scene = "dock", options = dockEnum("DockEdge"), note = "Landscape only; portrait is always the bottom."),
            Fd("dock.groupShelf", "App group chips", home, "Dock", "bool", scope = "dock", scene = "dock"),
        )

        // ---- LOOK > Theme
        add(
            Fd("themeMode", "Theme", look, "Theme", "enum", options = byName<ThemeMode> { it.label }),
            Fd("accent", "Accent colour", look, "Theme", "colour"),
            Fd("iconShape", "Icon shape", look, "Icons", "enum", options = byName<IconShape> { it.label }, scene = "pages"),
            Fd("iconTint", "Icon tint", look, "Icons", "enum", options = byName<IconTint> { it.label }, scene = "pages"),
            Fd("iconPlate", "Plate behind icons", look, "Icons", "bool", scene = "pages"),
            Fd("uiFont", "Font", look, "Theme", "enum", options = UiFonts.IDS.map { Opt(it, uiFontLabels.getValue(it)) }),
        )
        // ---- LOOK > Wallpaper
        val kinds = WallpaperKind.entries.map { Opt(it.id, it.label) }
        val bgSlot1 = BgItem.entries.filter { it == BgItem.None || it.living }.map { Opt(it.id, it.label) }
        val bgSlot2 = BgItem.entries.filter { it == BgItem.None || !it.living }.map { Opt(it.id, it.label) }
        add(
            Fd("wpGlanceKind", "Wallpaper", look, "Wallpaper: Glance", "enum", options = kinds),
            Fd("wpGlanceSolid", "Colour", look, "Wallpaper: Glance", "colour", showWhen = "wpGlanceKind" to listOf("solid")),
            Fd("wpGlanceFrom", "Gradient from", look, "Wallpaper: Glance", "colour", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("wpGlanceTo", "Gradient to", look, "Wallpaper: Glance", "colour", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("wpGlanceAngle", "Gradient angle", look, "Wallpaper: Glance", "int", min = 0.0, max = 359.0, step = 15.0, unit = "deg", showWhen = "wpGlanceKind" to listOf("gradient")),
            Fd("bgSlot1", "Signal wallpaper", look, "Wallpaper: Glance", "enum", options = bgSlot1, showWhen = "wpGlanceKind" to listOf("live")),
            Fd("bgSlot2", "Signal effect", look, "Wallpaper: Glance", "enum", options = bgSlot2, showWhen = "wpGlanceKind" to listOf("live")),
        )
        l += pic("wpGlance", "Glance", "wpGlanceKind")
        add(
            Fd("wpPagesSame", "Pages use the Glance wallpaper", look, "Wallpaper: Pages", "bool", scene = "pages"),
            Fd("wpPagesKind", "Wallpaper", look, "Wallpaper: Pages", "enum", options = kinds, scene = "pages", showWhen = "wpPagesSame" to listOf("false")),
            Fd("wpPagesSolid", "Colour", look, "Wallpaper: Pages", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("solid")),
            Fd("wpPagesFrom", "Gradient from", look, "Wallpaper: Pages", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesTo", "Gradient to", look, "Wallpaper: Pages", "colour", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesAngle", "Gradient angle", look, "Wallpaper: Pages", "int", min = 0.0, max = 359.0, step = 15.0, unit = "deg", scene = "pages", showWhen = "wpPagesKind" to listOf("gradient")),
            Fd("wpPagesSlot1", "Signal wallpaper", look, "Wallpaper: Pages", "enum", options = bgSlot1, scene = "pages", showWhen = "wpPagesKind" to listOf("live")),
            Fd("wpPagesSlot2", "Signal effect", look, "Wallpaper: Pages", "enum", options = bgSlot2, scene = "pages", showWhen = "wpPagesKind" to listOf("live")),
        )
        l += pic("wpPages", "Pages", "wpPagesKind").map { Fd(it.key, it.label, it.tab, it.group, it.type, scene = "pages", options = it.options, min = it.min, max = it.max, step = it.step, unit = it.unit, advanced = it.advanced, deviceBound = it.deviceBound, showWhen = it.showWhen) }
        // ---- LOOK > Calm style (signal and battery)
        val styleOpts = listOf(Opt("auto", "Automatic")) + CalmStyle.entries.map { Opt(it.name, it.label) }
        add(
            Fd("calmStyle", "Signal and battery style", look, "Signal and battery", "enum", options = styleOpts, optional = true, default = "auto", note = "Automatic lets the wallpaper decide."),
            Fd("calmRegular", "Also show battery and signal", look, "Signal and battery", "bool", showWhen = "calmStyle" to listOf("EdgeDots", "CornerBloom", "BottomGlow", "Beam")),
            Fd("indMetric", "Reads", look, "Signal and battery", "enum", options = byName<IndicatorMetric> { it.label }),
            Fd("indIntensity", "Intensity", look, "Signal and battery", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%"),
            Fd("indSpeed", "Speed", look, "Signal and battery", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%"),
            Fd("indLow", "Low colour", look, "Signal and battery", "colour", advanced = true),
            Fd("indMid", "Middle colour", look, "Signal and battery", "colour", advanced = true),
            Fd("indHigh", "High colour", look, "Signal and battery", "colour", advanced = true),
            Fd("indIdle", "Idle colour", look, "Signal and battery", "colour", advanced = true),
            Fd("settingsBackdrop", "Behind Settings", look, "Settings backdrop", "enum", options = byName<SettingsBackdrop> { it.label }, scene = "look"),
            Fd("settingsDim", "Backdrop dimming", look, "Settings backdrop", "float", min = 0.2, max = 0.95, step = 0.05, unit = "%", scene = "look", showWhen = "settingsBackdrop" to listOf("Wallpaper")),
        )

        // ---- APPS
        add(
            Fd("wheelGrouping", "Group the disc by", apps, "App drawer disc", "enum", options = byName<WheelGrouping> { it.label }, scene = "disc"),
            Fd("wheelWorkOnly", "Work apps only", apps, "App drawer disc", "bool", scene = "disc"),
            Fd("puckSegments", "Ring segments", apps, "App drawer disc", "int", scope = "prefFile", file = "puck_actions", fileKey = "segments", fileType = "i", default = PuckActions.DEFAULT_SEGMENTS, min = PuckActions.MIN_SEGMENTS.toDouble(), max = PuckActions.MAX_SEGMENTS.toDouble(), step = 1.0, scene = "disc"),
            Fd("puckLabels", "Names on the ring", apps, "App drawer disc", "bool", scope = "prefFile", file = "puck_actions", fileKey = "labels", fileType = "b", default = false, scene = "disc"),
            Fd("puckSlots", "Ring shortcuts", apps, "App drawer disc", "slots", scope = "prefFile", file = "puck_actions", fileKey = "slots", fileType = "s", count = PuckActions.SLOTS, scene = "disc",
                options = listOf(Opt("-", "Nothing")) + PuckActions.AVAILABLE.map { Opt("a:" + it.name, it.label) }, default = PuckActions.encode(PuckActions.DEFAULT),
                note = "A segment that holds an app is kept as it is."),
            Fd("appsOnHome", "Apps on the home pages", apps, "Home apps", "enum", options = byName<AppsOnHome> { it.label }, scene = "pages"),
            Fd("badgesOn", "Badges", apps, "Badges", "bool", scene = "pages"),
            Fd("badgeCount", "Badge numbers", apps, "Badges", "bool", scene = "pages", showWhen = "badgesOn" to listOf("true")),
        )

        // ---- RADAR
        add(
            Fd("searchLayout", "Radar layout", radar, "Layout", "enum", options = byName<SearchLayout> { it.label }, scene = "radar"),
            Fd("searchLayoutPinned", "Keep this layout with a keyboard", radar, "Layout", "bool", scene = "radar", advanced = true),
            Fd("searchMotion", "Slow drift", radar, "Layout", "bool", scene = "radar", showWhen = "searchLayout" to listOf("Filmstrip", "Orbit")),
            Fd("radarBackground", "Background", radar, "Background", "enum", options = byName<RadarBackground> { it.label }, scene = "radar"),
            Fd("radarIntensity", "Background items", radar, "Background", "enum", options = byName<RadarIntensity> { it.label }, scene = "radar"),
            Fd("searchWeb", "Web search", radar, "Sources", "bool", scene = "radar"),
            Fd("webEngine", "Web engine", radar, "Sources", "enum", options = WebEngine.entries.map { Opt(it.name, it.label) }, scene = "radar", showWhen = "searchWeb" to listOf("true")),
            Fd("webSuggestions", "Web suggestions", radar, "Sources", "bool", scene = "radar", showWhen = "searchWeb" to listOf("true")),
            Fd("searchContacts", "Contacts", radar, "Sources", "bool", scene = "radar"),
            Fd("searchFiles", "Files", radar, "Sources", "bool", scene = "radar"),
            Fd("searchActions", "Actions", radar, "Sources", "bool", scene = "radar"),
            Fd("searchDeep", "Deep file matches", radar, "Sources", "bool", scene = "radar", advanced = true),
            Fd("radarSignalForce", "Signal override (lab)", radar, "Lab", "enum", options = byName<SignalForce> { it.label }, scene = "radar", advanced = true),
        )

        // ---- SIDE PANEL
        add(
            Fd("leftPage", "Left of home", side, "Comms", "enum", options = byName<LeftPage> { it.label }, scene = "comms"),
            Fd("launchpadRail", "Unread strip", side, "Comms", "bool", scene = "comms", showWhen = "leftPage" to listOf("Launchpad")),
            Fd("lpTheme", "Comms theme", side, "Comms", "enum", options = byName<LaunchpadTheme> { it.label }, scene = "comms", showWhen = "leftPage" to listOf("Launchpad")),
            Fd("commsScale", "Comms text size", side, "Comms", "int", scope = "prefFile", file = CommsScaleSettings.FILE, fileKey = CommsScaleSettings.KEY, fileType = "i", default = CommsScale.DEFAULT, scene = "comms",
                min = CommsScale.MIN.toDouble(), max = CommsScale.MAX.toDouble(), step = 5.0, unit = "%"),
            Fd("rightSide", "Right of home", side, "Right side", "enum", options = byName<RightSide> { it.label }, scene = "recents"),
            Fd("recentsLimit", "Recent apps kept", side, "Right side", "int", min = HomeRecents.MIN_LIMIT.toDouble(), max = HomeRecents.MAX_LIMIT.toDouble(), step = 4.0, scene = "recents", showWhen = "rightSide" to listOf("RecentsPanel")),
            Fd("recentsUsage", "Add apps opened elsewhere", side, "Right side", "bool", scene = "recents", showWhen = "rightSide" to listOf("RecentsPanel")),
            Fd("leftWidgetId", "Left page widget", side, "Comms", "int", deviceBound = true, advanced = true, scene = "comms"),
        )

        // ---- SYSTEM
        add(
            Fd("language", "Language", sys, "Language", "enum", options = UiLanguage.entries.map { Opt(it.name, it.label) }, scene = "look"),
            Fd("uiSounds", "Interface sounds", sys, "Sound and touch", "bool", scene = "look"),
            Fd("uiSoundVolume", "Sound volume", sys, "Sound and touch", "float", min = 0.0, max = 1.0, step = 0.05, unit = "%", scene = "look", showWhen = "uiSounds" to listOf("true")),
            Fd("hapticStrength", "Launcher vibration", sys, "Sound and touch", "enum", scope = "prefFile", file = LauncherHapticSettings.FILE, fileKey = LauncherHapticSettings.KEY, fileType = "s",
                options = HapticStrength.entries.map { Opt(it.id, it.name) }, default = HapticStrength.DEFAULT.id, scene = "look"),
            Fd("hideStatusBar", "Hide the status bar", sys, "Screen", "bool"),
            Fd("restoreSurfaces", "Reopen Radar or the drawer after a restart", sys, "Screen", "bool", scene = "look"),
            Fd("onboardingDone", "First-run tour done", sys, "This device only", "bool", deviceBound = true, scene = "look", advanced = true),
        )
        return l
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

    private val tabs = listOf(
        Triple("home", "HOME", "The Glance, the clock, the pages and their grid, the four corners, gestures, keyboard, motion and the dock."),
        Triple("look", "LOOK", "Theme, icons, font, wallpaper, and how signal and battery show."),
        Triple("apps", "APPS", "The app drawer disc, which apps the home holds, badges. Workspaces and app groups are made on the phone."),
        Triple("radar", "RADAR", "Search: layout, background and sources."),
        Triple("side", "SIDE PANEL", "Comms on one side, the Recents panel or the dock rail on the other."),
        Triple("system", "SYSTEM", "Language, sound, the screen, and what stays on the phone."),
    )

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
            "groupShelf" -> lit.toBooleanStrict()
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
            "tabs" to tabs.map { mapOf("id" to it.first, "label" to it.second, "blurb" to it.third) },
            "fields" to outFields,
        )
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

    @Test fun CFG_26_schemaJsonAndSchemaJsAreExactlyWhatTheCodeGenerates() {
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

    @Test fun CFG_26_everyKeyTheSettingsExportCarriesIsInTheSchema() {
        val exported = exportedDefaults().keys + "calmStyle"
        val inSchema = fields().filter { it.scope == "settings" }.map { it.key }.toSet()
        assertEquals("exported by the app but missing from the Studio schema", emptyList<String>(), (exported - inSchema).sorted())
    }

    @Test fun CFG_26_everySchemaKeyOfTheSettingsDocumentIsARealKeyOrADeviceBoundOne() {
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

    @Test fun CFG_26_theSchemaDefaultsAreTheAppDefaultsAndEveryValueIsInsideItsRangeOrOptions() {
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

    @Test fun CFG_26_theEnumOptionsAreTheIdsTheAppStoresAndTheDockKeysAreTheDockSettingsFields() {
        assertEquals(setOf("enabled", "mode", "edge", "groupShelf", "style"), dockFields().keys)
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

    @Test fun CFG_26_thePrefFileAndShortcutKeysAreTheOnesTheExportAllowsAndSixTabsHoldEveryField() {
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

    @Test fun CFG_26_theSampleFilesTheNodeTestsReadAreWhatTheAppsOwnCodecWrites() {
        regenOr("sample-settings.json", sampleSettings())
        regenOr("sample-backup.json", sampleBackup())
    }

    @Test fun CFG_26_theFilesStudioWritesAreReadByTheAppsOwnCodec() {
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
