package com.bos.sphere.core.data

import kotlin.math.exp

/*
 * Pure (Android-free) model + math for the grid home: prefs, the paged layout, corner slots /
 * palettes, physical-corner rotation mapping and the suggested-app scorer. Kept free of Android
 * types so it is covered by plain JVM unit tests.
 */

/** Which home surface is shown. */
enum class HomeStyle { Grid, List, Psp, Dsi }

/** Pages (swipe between screens) or one continuous scroll. */
/** Which way the Continuous / DSi homes scroll. */
enum class ScrollAxis(val label: String) {
    Long("Along the long side"), Short("Along the short side"), Vertical("Top to bottom"), Horizontal("Left to right"),
}

/**
 * Wave 28 (homeUnify): the "Pages" setting, exactly three values. [Off] = no extra pages (home is Glance only; with Glance also off, home is the one first page),
 * [Paginated] = regular pages in the pager after Glance, [Continuous] = after Glance the horizontal column-by-column scroll surface, as the rest of the same pager.
 */
enum class HomePages(val label: String) { Off("Off"), Paginated("Paginated"), Continuous("Continuous") }

/** Corner look: a bare glyph, or a quarter-circle bubble hugging the screen corner. */
/** Holo = bare glyphs; Quarter = Sony-style quarter discs in the corners; Bubble = round floating bubbles. */
enum class CornerStyle(val label: String) { Glyph("Holo"), Quarter("Sony quarter discs"), Bubble("Bubbles") }

/** Non-app items that can occupy a page slot. Stored in page key lists under a `tile:` prefix. */
object HomeTiles {
    const val PREFIX = "tile:"
    const val CLOCK = "tile:clock"
    /** A "Home settings" icon that can sit on a page like an app. */
    const val SETTINGS = "tile:settings"
    val ALL = listOf(CLOCK, SETTINGS)

    fun isTile(key: String) = key.startsWith(PREFIX)
}

/** The axis pages scroll along. */
enum class PagingAxis(val label: String) { Horizontal("Sideways"), Vertical("Up / down") }

/** Columns × rows of one grid page. */
data class GridSpec(val columns: Int, val rows: Int) {
    val capacity: Int get() = columns * rows

    fun coerced(): GridSpec = GridSpec(
        columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        rows.coerceIn(MIN_ROWS, MAX_ROWS),
    )

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 10
        const val MIN_ROWS = 1
        const val MAX_ROWS = 10
    }
}

/** One-tap density presets: (portrait, landscape). */
enum class DensityPreset(val label: String, val portrait: GridSpec, val landscape: GridSpec) {
    Roomy("Roomy", GridSpec(3, 5), GridSpec(4, 2)),
    Default("Default", GridSpec(3, 6), GridSpec(5, 2)),
    Dense("Dense", GridSpec(4, 7), GridSpec(6, 3)),
    Packed("Packed", GridSpec(5, 8), GridSpec(8, 3)),
}

/** Where steps and the other activity numbers come from. */
enum class HealthSourcePref(val label: String) { Phone("Phone sensor"), HealthConnect("Health Connect") }

/** How the search screen presents results. */
enum class SearchLayout(val label: String) { Filmstrip("Filmstrip"), Orbit("Orbit"), Flat("Flat") }

/** Wave 30 (radarDim): what Radar shows behind its wheel, rings and tiles. Exactly two: the dimmed wallpaper (default) or pure black. */
enum class RadarBackground(val label: String) { Dimmed("Dimmed wallpaper"), TrueBlack("True black") }

/** What sits one swipe to the left of the first home page. */
enum class LeftPage(val label: String) { Off("Off"), Launchpad("Comms"), Widget("A widget") }

/** The backdrop behind Settings. */
/** How strong the background items are drawn (Ground Control > Background > Intensity). */
enum class RadarIntensity(val label: String) { Subtle("Subtle"), Normal("Normal") }

/** PAL-24: the Lab override of the live signal (Live = read the phone). Level 0..4 for the forced ones. */
enum class SignalForce(val label: String, val level: Int) {
    Live("Live", -1), NoSignal("No signal", 0), Weak("Weak", 1), Fair("Fair", 2), Good("Good", 3), Excellent("Excellent", 4),
}

/** How pages change when you swipe between them. */
enum class PageTransition(val label: String) { Slide("Slide"), Fade("Fade"), Zoom("Zoom"), Cube("Cube"), Flip("Flip"), Stack("Stack") }

/** Light or dark Launchpad (System follows the phone). */
enum class LaunchpadTheme(val label: String) { Dark("Dark"), Light("Light"), System("Follow the phone") }

enum class SettingsBackdrop(val label: String) { Wallpaper("Background, dimmed"), Black("Pitch black") }

/**
 * The engines the "Web engine" setting offers. [template] is the results page; [suggest] is the engine's public suggestion endpoint (wave 43,
 * docs/decisions/radarWebSuggest.md), null where none is known (Startpage, Ecosia): those engines show only the single "Search <engine> for ..." row.
 */
enum class WebEngine(val label: String, private val template: String, private val suggest: String? = null) {
    Google("Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
    Bing("Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
    Brave("Brave", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
    Ecosia("Ecosia", "https://www.ecosia.org/search?q=%s"),
    Startpage("Startpage", "https://www.startpage.com/do/search?q=%s");

    /** The results URL for [query] (URL-encoded). The text is sent as typed. */
    fun url(query: String): String = template.replace("%s", java.net.URLEncoder.encode(query.trim(), "UTF-8"))

    /** The suggestion request for [query] (HTTPS, only the typed text in it), or null when this engine has no known public endpoint. */
    fun suggestUrl(query: String): String? = suggest?.replace("%s", java.net.URLEncoder.encode(query.trim(), "UTF-8"))

    val hasSuggestions: Boolean get() = suggest != null
}

/** Clock number format. */
enum class ClockFormat(val label: String) { System("System"), Hour12("12-hour"), Hour24("24-hour") }

/**
 * Wave 41 (glanceClock): the styles of the big wallpaper clock on the Glance page (it replaces the small wave 38 clock of the fixed chrome). The stored name of
 * an older install ("Analog", "Digital") is mapped by [fromStored]: Analog -> AnalogClassic, Digital -> BigDigital.
 */
enum class ClockStyle(val label: String) {
    BigDigital("Big digital"), BigDigitalLine("Big digital, one line"), AnalogClassic("Analog classic"), AnalogMinimal("Analog minimal"), Word("Word clock"), Off("Off");

    val digital: Boolean get() = this == BigDigital || this == BigDigitalLine

    companion object {
        fun fromStored(name: String?): ClockStyle? = when (name) {
            null -> null
            "Analog" -> AnalogClassic
            "Digital" -> BigDigital
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/** Wave 41: the size of the Glance clock; Large is the default. [factor] scales the style's largest size. */
enum class ClockSize(val label: String, val factor: Float) { Small("Small", 0.6f), Medium("Medium", 0.8f), Large("Large", 1f) }

/** Whether corner actions show a drawn glyph or the real icon of the app they open. */
enum class CornerIconMode(val label: String) { Glyph("Glyph"), AppIcon("App icon") }

/** What lives on the home pages. */
enum class AppsOnHome(val label: String) { Everything("All apps"), PinnedOnly("Only ones I add") }

/** One page or many. */
enum class PageMode(val label: String) { Multiple("Multiple pages"), Single("One page") }

/** What the signal effects read out (wave 35): the connectivity level or the battery level; every other old measurement migrates to [Signal]. */
enum class IndicatorMetric(val label: String) {
    Signal("Signal"),
    Battery("Battery"),
}

/** Launcher UI palette. */
enum class ThemeMode(val label: String) { Dark("Dark"), Amoled("AMOLED"), Light("Light") }

/** Shape every app icon is drawn in. */
enum class IconShape(val label: String) { Original("Original"), Rounded("Rounded"), Squircle("Squircle"), Circle("Circle"), Square("Square") }

/** Tint applied to every app icon (a per-app colour overrides it). */
enum class IconTint(val label: String) { None("None"), Accent("Accent"), Mono("Monochrome") }

/** UI languages for the launcher's own screens (empty code = follow the system). */
enum class UiLanguage(val code: String, val label: String) {
    System("", "System"),
    English("en", "English"),
    Spanish("es", "Español"),
    French("fr", "Français"),
    German("de", "Deutsch"),
    Portuguese("pt", "Português"),
    Hindi("hi", "हिन्दी"),
    Chinese("zh", "中文"),
    Japanese("ja", "日本語"),
    Arabic("ar", "العربية"),
}

data class LauncherPrefs(
    val homeStyle: HomeStyle = HomeStyle.Grid,
    val homePages: HomePages = HomePages.Off,
    val scrollAxis: ScrollAxis = ScrollAxis.Long,
    /** Icons across the short side in the Continuous / DSi homes. */
    val continuousColumns: Int = 4,
    val portraitGrid: GridSpec = DensityPreset.Default.portrait,
    val landscapeGrid: GridSpec = DensityPreset.Default.landscape,
    /** Icon edge as a fraction of the cell pitch. */
    val iconScale: Float = DEFAULT_ICON_SCALE,
    val showLabels: Boolean = false,
    /** true = corner slots stay pinned to the device's physical corners as it rotates. */
    val cornersFollowDevice: Boolean = true,
    /** Which way pages turn, per orientation. Swipes on the other axis are free for gestures. */
    val portraitPaging: PagingAxis = PagingAxis.Horizontal,
    val landscapePaging: PagingAxis = PagingAxis.Horizontal,
    /** Motion gestures (shake / flick / flip …) are listened for while home is showing. */
    val motionEnabled: Boolean = true,
    /** Keep listening for motion gestures from a foreground service when home is not showing. */
    val motionInBackground: Boolean = false,
    /** 0 = needs a firm motion … 1 = triggers on a light one. */
    val motionSensitivity: Float = 0.5f,
    /** How the four corners are drawn. */
    val cornerStyle: CornerStyle = CornerStyle.Glyph,
    /** Console-style interface tones in the settings space. */
    val uiSounds: Boolean = true,
    val uiSoundVolume: Float = 0.4f,
    val wheelGrouping: WheelGrouping = WheelGrouping.Letter,
    /** Wave 48: the disc lists only the work profile's apps (the Work scope label); ignored while there is no work profile. */
    val wheelWorkOnly: Boolean = false,

    // --- home landing (HOM-22)
    /** What the home shows: the user's pages, the recent apps, every app, or only the wallpaper and the four hotspots. */
    /** Wave 26: the Glance is the first surface of the home; off = the first page (or the scroll) is what home shows. */
    val glanceEnabled: Boolean = true,
    /** Wave 26: Left to right (default) or Right to left: the Launchpad edge, the column / page order and the hotspot roles. */
    val homeOrder: HomeOrder = HomeOrder.LeftToRight,
    /** Wave 38: the music line (title and pop-in controls) at the top of every home page while something plays; default on. */
    val showMusic: Boolean = true,
    /** HOM-23: which hotspots are on, by physical corner (default all four). */
    val glanceHotspots: Set<Corner> = GlanceHotspots.ALL,
    /** HOM-23: seconds of inactivity before the pop-in music controls pop out (2 to 10). */
    val glanceControlsSeconds: Int = GlanceControls.DEFAULT_SECONDS,
    /** HOM-23: the small surface indicator at the bottom centre. */
    val glanceIndicator: Boolean = true,
    /** How many recent apps the Recent landing keeps, [HomeRecents.MIN_LIMIT]..[HomeRecents.MAX_LIMIT]. */
    val recentsLimit: Int = HomeRecents.DEFAULT_LIMIT,
    /** Add apps opened elsewhere (usage access, when granted) to the Recent landing. */
    val recentsUseUsageAccess: Boolean = true,
    /** STR-14: what the right-hand side holds: the Recents panel (default), the dock rail's edge reveal, or nothing. */
    val rightSide: RightSide = RightSide.RecentsPanel,

    /** An always-open keyboard that searches apps as you type — the old-school-phone mode. */
    val alwaysKeyboard: Boolean = false,

    // --- background (wave 15): two signal-driven slots, see BackgroundChoice
    val backgroundSlot1: BgItem = BackgroundChoice.DEFAULT.slot1,
    val backgroundSlot2: BgItem = BackgroundChoice.DEFAULT.slot2,
    // --- wallpaper per surface (wave 26): the slots above are the Glance's live wallpaper; see Wallpaper.kt
    val glanceWallpaperKind: WallpaperKind = WallpaperKind.Live,
    val glanceSolid: Long = Wallpaper.DEFAULT_SOLID,
    val glanceGradFrom: Long = GradientSpec.DEFAULT_FROM,
    val glanceGradTo: Long = GradientSpec.DEFAULT_TO,
    val glanceGradAngle: Int = GradientSpec.DEFAULT_ANGLE,
    /** The pages (paginated or scroll) use the Glance wallpaper; when off they use the pages* values below. */
    val pagesSameAsGlance: Boolean = true,
    val pagesWallpaperKind: WallpaperKind = WallpaperKind.Live,
    val pagesSolid: Long = Wallpaper.DEFAULT_SOLID,
    val pagesGradFrom: Long = GradientSpec.DEFAULT_FROM,
    val pagesGradTo: Long = GradientSpec.DEFAULT_TO,
    val pagesGradAngle: Int = GradientSpec.DEFAULT_ANGLE,
    val pagesSlot1: BgItem = BackgroundChoice.DEFAULT.slot1,
    val pagesSlot2: BgItem = BackgroundChoice.DEFAULT.slot2,
    // --- wave 34 (wallpaper): the picture (gallery still or animated GIF / WebP) and the dim over the system wallpaper, per surface; see WallpaperPicture.kt
    val glancePicture: PictureSpec = PictureSpec(),
    val pagesPicture: PictureSpec = PictureSpec(),
    val glanceSystemDim: Int = 0,
    val pagesSystemDim: Int = 0,

    // --- search
    val webEngine: WebEngine = WebEngine.DuckDuckGo,
    val searchWeb: Boolean = true,
    /** Wave 43 (radarWeb): send what is typed in Radar's Web scope (and Apps for the Play Store) to the engine to show suggestions; Off = no network at all. */
    val webSuggestions: Boolean = true,
    val searchContacts: Boolean = true,
    val leftPage: LeftPage = LeftPage.Launchpad,
    /** Appwidget id hosted full-screen on the left page when [leftPage] is [LeftPage.Widget]; -1 = none. */
    val leftWidgetId: Int = -1,
    /** The narrow strip of unread counts at the left edge of page one. */
    val launchpadRail: Boolean = true,
    val launchpadTheme: LaunchpadTheme = LaunchpadTheme.Dark,
    val settingsBackdrop: SettingsBackdrop = SettingsBackdrop.Wallpaper,
    /** How much the wallpaper is dimmed behind Settings, 0..1. */
    val settingsDim: Float = 0.7f,
    /** Vertical icon spacing 0..1; negative = same as [iconSpacing]. */
    val spacingV: Float = -1f,
    /** Free margins around the grid, as fractions of the page (sides / top / bottom). */
    /** Where spare height sits around the grid on portrait pages: 0 = all below it (grid hugs the clock), 1 = all above. */
    val gridBias: Float = 0.4f,
    val gridInsetX: Float = 0f,
    val gridInsetTop: Float = 0f,
    val gridInsetBottom: Float = 0f,
    /** App label text scale. */
    val labelScale: Float = 1f,
    val searchLayout: SearchLayout = SearchLayout.Filmstrip,
    /** The page the Home button returns to. */
    val homePage: Int = 0,
    val pageTransition: PageTransition = PageTransition.Slide,
    /** Filmstrip / orbit keep drifting slowly; off = still until you move them. */
    val searchMotion: Boolean = true,
    val searchFiles: Boolean = true,
    /** Wave 15: the Intensity of the background items (Subtle, Normal). Named for the wave 14 Radar setting it replaces. */
    val radarIntensity: RadarIntensity = RadarIntensity.Normal,
    /** PAL-24: Lab override of the signal state (Live = the phone's real state). */
    val radarSignalForce: SignalForce = SignalForce.Live,
    /** Wave 30 (radarDim): Radar's own background, Dimmed wallpaper (default) or True black. */
    val radarBackground: RadarBackground = RadarBackground.Dimmed,
    val searchActions: Boolean = true,
    /** Ask installed Fylz / Fotoz for deep (text-in-image, content) matches. */
    val searchDeep: Boolean = true,

    // --- first run
    val onboardingDone: Boolean = false,

    /** 0 = compact square cells, 1 = icons spread to fill the free space (rows and columns separately). */
    val iconSpacing: Float = 0f,

    // --- clock (wave 38: one clock, on the Glance page only: Analog or Digital, and for Digital 12 or 24 hour; System follows the device)
    val clockStyle: ClockStyle = ClockStyle.BigDigital,
    val clockFormat: ClockFormat = ClockFormat.System,
    /** Wave 41: the date under the Glance clock (small, locale format; default off), the accent colour instead of the legibility ink, and the size. */
    val glanceClockDate: Boolean = false,
    val glanceClockAccent: Boolean = false,
    val glanceClockSize: ClockSize = ClockSize.Large,
    /** Wave 50 (glanceClockEdit): where the user put the Glance clock, per orientation, as fractions of the usable area (centre x, top y); the defaults are the factory place. */
    val glanceClockX: Float = ClockPlacement.DEFAULT_X,
    val glanceClockY: Float = ClockPlacement.DEFAULT_Y,
    val glanceClockXLand: Float = ClockPlacement.DEFAULT_X,
    val glanceClockYLand: Float = ClockPlacement.DEFAULT_Y,
    /** Wave 45 (editSheet): the options of the clock TILE on a page (its item sheet): analog face instead of digital, 12/24 hour for the digital face, and the day line. Defaults are the look it always had. */
    val clockTileAnalog: Boolean = false,
    val clockTileFormat: ClockFormat = ClockFormat.System,
    val clockTileDate: Boolean = true,

    // --- badges and adaptive grid (HOM-09, HOM-11)
    val badgesOn: Boolean = true,
    val badgeCount: Boolean = true,
    /** Derive the grid from the window size instead of the fixed portrait and landscape grids. */
    val adaptiveGrid: Boolean = false,
    /** Density for the adaptive grid: 1 is the default, above 1 packs more. */
    val gridDensity: Float = 1f,

    // --- corners
    /** Corner icon edge in dp. */
    val cornerIconSize: Float = 32f,
    val cornerIconMode: CornerIconMode = CornerIconMode.Glyph,

    // --- screen
    /** Hide the system status bar on home so corner icons and the clock sit flush to the top. */
    val hideStatusBar: Boolean = true,
    /** NFR-11: after a restart, reopen the drawer or search screen that was open. Off by default (see [OpenSurfaceStore]). */
    val restoreOpenSurfaces: Boolean = false,

    // --- apps and pages
    val appsOnHome: AppsOnHome = AppsOnHome.Everything,
    val pageMode: PageMode = PageMode.Multiple,

    // --- indicator (the background dots and effects)
    val indicatorMetric: IndicatorMetric = IndicatorMetric.Signal,
    /** Wave 35: the calm effect (healthy = lit) and the alert effect (inverse: appears when low, urgent when lost); each may be off, so an invalid pair cannot be stored. */
    val indicatorCalm: CalmEffect? = CalmEffect.AnimatedDots,
    val indicatorAlert: AlertEffect? = null,
    /** ARGB colours for low / middle / high readings, plus idle (airplane / fixed). */
    val indicatorLow: Long = 0xFFFF4D4D,
    val indicatorMid: Long = 0xFFFFB347,
    val indicatorHigh: Long = 0xFF6BFF8F,
    val indicatorIdle: Long = 0xFF8A8F99,
    val indicatorIntensity: Float = 0.6f,
    val indicatorSpeed: Float = 0.5f,

    // --- theme
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accentColor: Long = 0xFF8E7BFF,
    val iconShape: IconShape = IconShape.Rounded,
    val iconTint: IconTint = IconTint.None,
    /** Draw a plate behind transparent icons. */
    val iconPlate: Boolean = true,
    /** Wave 52: the font of the launcher's own UI text, by id (`space_grotesk` default, `system`, `inter`, ...; the families live in core-design `FontCatalog`). */
    val uiFont: String = UiFonts.DEFAULT,

    // --- language
    val language: UiLanguage = UiLanguage.System,

    // --- Radar (PAL-18)
    /** The user chose a Radar layout on purpose: it is kept even when a keyboard or pointer is in use (which otherwise means Flat). */
    val searchLayoutPinned: Boolean = false,

    // --- input (INP-12, INP-15, INP-16); added at the end so other streams' fields merge cleanly
    /** Raise the on-screen keyboard when the palette or drawer opens, per orientation (INP-12). */
    val keyboardOnOpenPortrait: Boolean = false,
    val keyboardOnOpenLandscape: Boolean = false,
    /** A separate set of corner assignments for landscape (INP-15). */
    val cornersPerOrientation: Boolean = false,
    /** Hot corners for a pointer (INP-16): on or off, and the dwell before the action shows. */
    val hotCornersOn: Boolean = true,
    val hotCornerDwellMs: Int = 150,
) {
    fun gridFor(landscape: Boolean): GridSpec = if (landscape) landscapeGrid else portraitGrid
    /** INP-12: whether the keyboard should rise on open for this orientation. */
    fun keyboardOnOpen(landscape: Boolean): Boolean = if (landscape) keyboardOnOpenLandscape else keyboardOnOpenPortrait
    fun pagingFor(landscape: Boolean): PagingAxis = if (landscape) landscapePaging else portraitPaging

    companion object {
        const val DEFAULT_ICON_SCALE = 0.82f
        const val MIN_ICON_SCALE = 0.5f
        const val MAX_ICON_SCALE = 0.95f
        const val MIN_CORNER_ICON = 20f
        const val MAX_CORNER_ICON = 72f
    }
}

// ---------------------------------------------------------------------------------------------
// Corners
// ---------------------------------------------------------------------------------------------

/** The four corners, in clockwise order (the ordinal is the clockwise index). */
enum class Corner(val label: String) {
    TopLeft("Top left"),
    TopRight("Top right"),
    BottomRight("Bottom right"),
    BottomLeft("Bottom left"),
}

/**
 * Maps between *physical* corners (named in the device's natural orientation) and *screen*
 * corners for a display rotation given in quarter turns (`Surface.ROTATION_*` values 0..3).
 *
 * `ROTATION_90` means the content is drawn rotated 90° to compensate for the device being turned
 * counter-clockwise, so the physical top-right corner becomes the on-screen top-left:
 * physical index `i` shows at screen index `(i - r) mod 4`.
 */
object CornerMapping {
    fun screenCornerOf(physical: Corner, rotation: Int): Corner =
        Corner.entries[Math.floorMod(physical.ordinal - rotation, 4)]

    fun physicalCornerAt(screen: Corner, rotation: Int): Corner =
        Corner.entries[Math.floorMod(screen.ordinal + rotation, 4)]
}

/** Built-in corner actions. [internal] ones are handled by the launcher itself. */
enum class CornerActionId(val label: String, val internal: Boolean = false) {
    Music("Music"),
    Camera("Camera"),
    Search("Radar", internal = true),
    AllApps("All apps", internal = true),
    Notifications("Notifications", internal = true),
    LauncherSettings("Home settings", internal = true),
    Phone("Phone"),
    Messages("Messages"),
    Browser("Browser"),
    Email("Email"),
    Maps("Maps"),
    Calendar("Calendar"),
    Clock("Clock"),
    Gallery("Gallery"),
    Contacts("Contacts"),
    Calculator("Calculator"),
    Assistant("Assistant"),
    Flashlight("Flashlight"),
    GridDenser("Denser grid", internal = true),
    GridSparser("Sparser grid", internal = true),
    RingerCycle("Cycle ring / vibrate / silent"),
    RingerNormal("Ring mode"),
    RingerVibrate("Vibrate mode"),
    RingerSilent("Silent mode"),
    DoNotDisturb("Do not disturb"),
    SystemSettings("Settings"),
    /** HOM-22: opens Launchpad (the left card); the default top-right hotspot of the Minimal landing. */
    OpenLaunchpad("Comms", internal = true),
    /** STR-14: opens the Recents panel (the right-hand card); assignable to a corner or a gesture. */
    OpenRecents("Open recents", internal = true),
}

/** One thing a corner (or a palette entry) can do. */
sealed interface CornerItem {
    data class Action(val id: CornerActionId) : CornerItem
    data class App(val key: String) : CornerItem
}

/** What is assigned to a corner. */
sealed interface CornerSlot {
    data object Empty : CornerSlot
    data class Single(val item: CornerItem) : CornerSlot

    /** A folder whose items fan out radially from the corner. */
    data class Palette(val name: String, val items: List<CornerItem>) : CornerSlot

    /** One watch-style complication (a live readout) sitting in the corner. */
    data class Complication(val kind: ComplicationKind) : CornerSlot

    /** Several complications arranged on an arc around the corner. */
    data class ComplicationCluster(val kinds: List<ComplicationKind>) : CornerSlot

    /** A hosted app widget that peeks out of the corner and pops out to full size when touched. */
    data class Widget(val appWidgetId: Int, val label: String) : CornerSlot

    companion object {
        const val MAX_PALETTE_ITEMS = 40
        const val MAX_CLUSTER = 4
    }
}

/** Watch-style complications: small live readouts. [action] is what tapping one does. */
enum class ComplicationKind(val label: String, val action: CornerActionId?) {
    Battery("Battery", null),
    Date("Date", CornerActionId.Calendar),
    Time("Time", CornerActionId.Clock),
    NextAlarm("Next alarm", CornerActionId.Clock),
    Ringer("Ring mode", CornerActionId.RingerCycle),
    Flashlight("Flashlight", CornerActionId.Flashlight),
    Storage("Storage", null),
    Notifications("Notifications", CornerActionId.Notifications),
}

/** How the wheel drawer groups apps into spokes. */
enum class WheelGrouping(val label: String) { Letter("A–Z"), Category("Category"), Recent("Recent") }

/** Defaults match the reference layout (portrait natural orientation). */
val DefaultCorners: Map<Corner, CornerSlot> = mapOf(
    Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)),
    Corner.TopRight to CornerSlot.Single(CornerItem.Action(CornerActionId.Music)),
    Corner.BottomRight to CornerSlot.Single(CornerItem.Action(CornerActionId.Search)),
    Corner.BottomLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.AllApps)),
)

// ---------------------------------------------------------------------------------------------
// Gestures
// ---------------------------------------------------------------------------------------------

enum class GestureKind { Touch, Motion }

/** Bindable gestures. Touch ones fire on the grid home; motion ones from the device sensors. */
enum class HomeGesture(val label: String, val kind: GestureKind, val hint: String) {
    PinchIn("Pinch in", GestureKind.Touch, "Two fingers together"),
    PinchOut("Pinch out", GestureKind.Touch, "Two fingers apart"),
    SwipeUp("Swipe up", GestureKind.Touch, "Only when pages turn sideways"),
    SwipeDown("Swipe down", GestureKind.Touch, "Only when pages turn sideways"),
    SwipeLeft("Swipe left", GestureKind.Touch, "Only when pages turn up / down"),
    SwipeRight("Swipe right", GestureKind.Touch, "Only when pages turn up / down"),
    DoubleTap("Double tap", GestureKind.Touch, "On empty space"),
    /** INP-08: unbound, the long press on empty space opens the quick sheet as before. */
    LongPress("Long press", GestureKind.Touch, "On empty space"),
    Shake("Shake", GestureKind.Motion, "A few quick shakes"),
    DoubleFlick("Flick twice", GestureKind.Motion, "Twist the wrist back and forth twice"),
    DoubleFlip("Flip twice", GestureKind.Motion, "Turn face down and back up, twice"),
    FaceDown("Place face down", GestureKind.Motion, "Lay the phone screen-down"),
    ;

    /** Swipes along the paging axis turn pages instead of firing. */
    fun usableWith(paging: PagingAxis): Boolean =
        usableOn(HomeSurface(if (paging == PagingAxis.Horizontal) SurfaceScroll.Horizontal else SurfaceScroll.Vertical, paged = true, hasDensity = true))

    /** Whether this gesture can fire at all on [surface] (see [unavailableReason]). */
    fun usableOn(surface: HomeSurface): Boolean = unavailableReason(surface) == null

    /**
     * Why this gesture cannot fire on [surface], as a short English phrase for Settings, or null
     * when it can. The rule: a one-finger swipe is only a gesture on an axis the home does not
     * already scroll along; swipe right is the Launchpad's when a left page is on and the home
     * turns pages up / down; density changes need a grid with a density.
     */
    fun unavailableReason(surface: HomeSurface): String? {
        if (kind == GestureKind.Motion) return null
        val s = surface.scroll
        return when (this) {
            SwipeUp, SwipeDown -> if (s == SurfaceScroll.Vertical || s == SurfaceScroll.Both) "the home scrolls this way" else null
            SwipeLeft -> if (s == SurfaceScroll.Horizontal || s == SurfaceScroll.Both) "the home scrolls this way" else null
            SwipeRight -> when {
                s == SurfaceScroll.Horizontal || s == SurfaceScroll.Both -> "the home scrolls this way"
                surface.paged && surface.leftPageOn -> "opens the left page"
                else -> null
            }
            else -> null
        }
    }
}

/** The axes a one-finger drag already moves the home along. */
enum class SurfaceScroll { Horizontal, Vertical, Both }

/**
 * What the active home surface does with a finger, derived from the prefs by [LauncherPrefs.surface]:
 * which way it scrolls, whether it has pages, whether it has a grid density, and whether a left
 * page (Launchpad / widget) claims the rightward swipe.
 */
data class HomeSurface(
    val scroll: SurfaceScroll,
    val paged: Boolean,
    val hasDensity: Boolean,
    val leftPageOn: Boolean = false,
)

/** True when the home is the paged grid (pages, per-page layouts, page dots, drag-to-rearrange). */
val LauncherPrefs.isPaged: Boolean get() = homeStyle == HomeStyle.Grid && homePages != HomePages.Continuous

/** Whether the Continuous / DSi homes scroll sideways in this orientation. */
fun LauncherPrefs.scrollsHorizontally(landscape: Boolean): Boolean = when (scrollAxis) {
    ScrollAxis.Long -> landscape
    ScrollAxis.Short -> !landscape
    ScrollAxis.Vertical -> false
    ScrollAxis.Horizontal -> true
}

/** The gesture surface the current prefs put on screen. */
fun LauncherPrefs.surface(landscape: Boolean): HomeSurface = when {
    isPaged -> HomeSurface(
        if (pagingFor(landscape) == PagingAxis.Horizontal) SurfaceScroll.Horizontal else SurfaceScroll.Vertical,
        paged = true, hasDensity = true, leftPageOn = leftPage != LeftPage.Off,
    )
    homeStyle == HomeStyle.Grid -> HomeSurface(
        if (scrollsHorizontally(landscape)) SurfaceScroll.Horizontal else SurfaceScroll.Vertical, paged = false, hasDensity = true,
    )
    homeStyle == HomeStyle.Dsi -> HomeSurface(
        if (scrollsHorizontally(landscape)) SurfaceScroll.Horizontal else SurfaceScroll.Vertical, paged = false, hasDensity = false,
    )
    homeStyle == HomeStyle.List -> HomeSurface(SurfaceScroll.Vertical, paged = false, hasDensity = false)
    else -> HomeSurface(SurfaceScroll.Both, paged = false, hasDensity = false) // PSP: sideways for categories, up / down for apps
}

/** Defaults: the requested pinch-in → search, plus the three motion examples. */
val DefaultGestures: Map<HomeGesture, CornerItem> = mapOf(
    HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Search),
    HomeGesture.SwipeDown to CornerItem.Action(CornerActionId.Notifications),
    HomeGesture.SwipeUp to CornerItem.Action(CornerActionId.AllApps),
    HomeGesture.SwipeLeft to CornerItem.Action(CornerActionId.Search),
    HomeGesture.Shake to CornerItem.Action(CornerActionId.RingerVibrate),
    HomeGesture.DoubleFlick to CornerItem.Action(CornerActionId.RingerSilent),
    HomeGesture.DoubleFlip to CornerItem.Action(CornerActionId.Flashlight),
)

// ---------------------------------------------------------------------------------------------
// Paged layout
// ---------------------------------------------------------------------------------------------

/** How one page arranges its apps. */
enum class PageShape(val label: String) { Grid("Grid"), Circle("Circle"), Coverflow("Coverflow") }

/**
 * Per-page arrangement. A null grid spec means "follow the global density from [LauncherPrefs]".
 * [rings] is the circular page's density: a centre slot plus rings of 6, 12, 18 … slots.
 */
data class PageLayout(
    val shape: PageShape = PageShape.Grid,
    val portrait: GridSpec? = null,
    val landscape: GridSpec? = null,
    val rings: Int = 2,
) {
    fun gridFor(landscape: Boolean, prefs: LauncherPrefs): GridSpec =
        (if (landscape) this.landscape else portrait) ?: prefs.gridFor(landscape)

    fun capacity(landscape: Boolean, prefs: LauncherPrefs): Int = when (shape) {
        PageShape.Grid -> gridFor(landscape, prefs).capacity
        PageShape.Circle -> circleCapacity(rings)
        PageShape.Coverflow -> COVERFLOW_CAPACITY
    }

    companion object {
        const val MIN_RINGS = 1
        const val MAX_RINGS = 3

        /** A coverflow page scrolls, so its limit is about usability, not space. */
        const val COVERFLOW_CAPACITY = 40

        /** Slots on ring [ring] (0 = the centre). */
        fun ringSlots(ring: Int): Int = if (ring == 0) 1 else 6 * ring

        fun circleCapacity(rings: Int): Int = (0..rings.coerceIn(MIN_RINGS, MAX_RINGS)).sumOf(::ringSlots)
    }
}

/**
 * One home page: its item keys in order, and how it lays them out. [id] identifies the *stored*
 * (logical) page: when a page holds more than one screen's worth — after a rotation or a density
 * change — it is drawn as several visual pages sharing one id, and they merge back into a single
 * stored page on save ([HomeLayoutMath.toStored]), so rotating never fragments the layout.
 */
data class HomePage(
    val keys: List<String> = emptyList(),
    val layout: PageLayout = PageLayout(),
    val id: Long = 0L,
)

/**
 * The persisted home arrangement. [seeded] is false until the first arrangement is written. [removed] holds apps the user
 * took off the home pages (still in the drawer). Wave 30: apps are never placed for the user, so [removed] only records what was taken off.
 */
data class HomeLayout(
    val pages: List<HomePage> = emptyList(),
    val removed: Set<String> = emptySet(),
    val seeded: Boolean = false,
)

/**
 * Layout math over pages whose capacity varies per page ([capacityOf] maps a [PageLayout] to its
 * slot count for the current orientation and global density). Edits operate on *visual* pages
 * (what is on screen); [toStored] folds them back for persistence.
 */
object HomeLayoutMath {

    /**
     * Resolves the stored [layout] into what is drawn: unknown / hidden keys dropped, duplicates
     * removed, and every stored page split into as many visual pages (same layout and id) as it
     * needs, so a density change never hides an app. Wave 30 (pagesExplicit): no APP is ever placed
     * on a page for the user: an app that is installed but not on a page (a new install, a first run,
     * an app taken off the home) is not appended anywhere, it lives in the drawer. Only [strays] are
     * appended to the last page: items the user made themselves (folders, widgets, shortcuts, links)
     * that another window size class's arrangement does not list yet, so a widget made in portrait is
     * not missing in landscape. A page stored empty
     * (a deliberate blank page) is kept; a page that only *became* empty because its apps are hidden or
     * unavailable is not drawn. Always returns at least one page.
     */
    fun visualPages(
        layout: HomeLayout,
        available: List<String>,
        capacityOf: (PageLayout) -> Int,
        strays: Collection<String> = emptyList(),
    ): List<HomePage> {
        val known = available.toHashSet().apply { addAll(HomeTiles.ALL) }
        val placed = HashSet<String>()
        val stored = normalizeIds(layout.pages)
        val pages = stored.map { page ->
            page to page.copy(keys = page.keys.filter { it in known && placed.add(it) })
        }
        val extras = strays.filter { it in known && it !in placed && it !in layout.removed }
        val withStrays = if (extras.isEmpty()) pages else {
            val base = pages.ifEmpty { listOf(HomePage(id = 1L).let { it to it }) }
            base.dropLast(1) + base.last().let { (orig, last) -> orig to last.copy(keys = last.keys + extras) }
        }
        val out = withStrays.flatMap { (orig, page) ->
            when {
                page.keys.isNotEmpty() ->
                    page.keys.chunked(capacityOf(page.layout).coerceAtLeast(1)).map { HomePage(it, page.layout, page.id) }
                orig.keys.isEmpty() -> listOf(page) // a deliberate blank page
                else -> emptyList()
            }
        }
        return out.ifEmpty { listOf(HomePage(id = 1L)) }
    }

    /**
     * Folds visual pages back into stored pages: consecutive pages with the same id merge (keys
     * concatenated, first layout wins). Then [preserve]d keys — on the previous stored layout but
     * not drawable right now (hidden, or a work profile paused) — are put back on the page with
     * their old id at their old position, so an edit never silently drops them.
     */
    fun toStored(visual: List<HomePage>, previous: List<HomePage>, preserve: Set<String>): List<HomePage> {
        val merged = ArrayList<HomePage>()
        for (p in visual) {
            val last = merged.lastOrNull()
            if (last != null && last.id == p.id && p.id != 0L) merged[merged.lastIndex] = last.copy(keys = last.keys + p.keys)
            else merged += p
        }
        if (preserve.isEmpty()) return merged
        val present = merged.flatMap { it.keys }.toHashSet()
        val out = merged.map { it.keys.toMutableList() }
        for (old in previous) {
            old.keys.forEachIndexed { idx, key ->
                if (key !in preserve || key in present) return@forEachIndexed
                val target = merged.indexOfFirst { it.id == old.id }.takeIf { it >= 0 } ?: merged.lastIndex
                if (target < 0) return@forEachIndexed
                val list = out[target]
                // Anchor after its nearest earlier neighbour that is still on this page; else its old index.
                val anchor = (idx - 1 downTo 0).map { old.keys[it] }.firstOrNull { it in list }
                val at = if (anchor != null) list.indexOf(anchor) + 1 else idx.coerceAtMost(list.size)
                list.add(at, key)
                present += key
            }
        }
        return merged.mapIndexed { i, p -> p.copy(keys = out[i].toList()) }
    }

    /**
     * Moves [key] to ([toPage], [toIndex]). `toPage == pages.size` creates a new page (default
     * layout, new id). A full target page refuses the drop (pages come back unchanged). Pages are never dropped when emptied (wave 30), so [dropEmpty]
     * no longer changes anything; it stays for the callers.
     */
    fun move(
        pages: List<HomePage>,
        key: String,
        toPage: Int,
        toIndex: Int,
        capacityOf: (PageLayout) -> Int,
        dropEmpty: Boolean = true,
    ): List<HomePage> {
        val src = pages.indexOfFirst { key in it.keys }
        val work = pages.map { p -> Work(p.keys.filter { it != key }.toMutableList(), p.layout, p.id) }.toMutableList()
        val target = toPage.coerceIn(0, work.size)
        if (target == work.size) work += Work(mutableListOf(), PageLayout(), nextId(pages))
        // Wave 30 (pagesExplicit): a drop on a page that is full is refused ("page full"), it never pushes an item onto the next page or creates a page.
        // Moving inside the page the item already is on never overflows.
        else if (src != target && work[target].keys.size >= capacityOf(work[target].layout).coerceAtLeast(1)) return pages
        val page = work[target].keys
        page.add(toIndex.coerceIn(0, page.size), key)
        cascade(work, capacityOf)
        return finish(work)
    }

    /** Removes [key] from the pages (an emptied page stays: only the user deletes a page). */
    fun remove(pages: List<HomePage>, key: String): List<HomePage> {
        val work = pages.map { p -> Work(p.keys.filter { it != key }.toMutableList(), p.layout, p.id) }.toMutableList()
        return finish(work)
    }

    /**
     * Adds [key] at the end of [preferredPage] when it has a free slot, else on the first other page with room (later pages first). Wave 30
     * (pagesExplicit): it never creates a page and never pushes an item off another one; with every page full the pages come back unchanged
     * ([hasRoom] says so beforehand, the caller shows "Page full"). With no page at all one empty page is made (the first page is the user's).
     */
    fun add(pages: List<HomePage>, key: String, preferredPage: Int, capacityOf: (PageLayout) -> Int): List<HomePage> {
        val work = pages.map { p -> Work(p.keys.filter { it != key }.toMutableList(), p.layout, p.id) }.toMutableList()
        if (work.isEmpty()) work += Work(mutableListOf(), PageLayout(), 1L)
        val pref = preferredPage.coerceIn(0, work.lastIndex)
        val order = (pref..work.lastIndex) + (0 until pref)
        val dest = order.firstOrNull { work[it].keys.size < capacityOf(work[it].layout).coerceAtLeast(1) } ?: return pages
        work[dest].keys.add(key)
        return finish(work)
    }

    /** True when [add] can place a new item: some page has a free slot (or there is no page yet). */
    fun hasRoom(pages: List<HomePage>, capacityOf: (PageLayout) -> Int): Boolean =
        pages.isEmpty() || pages.any { it.keys.size < capacityOf(it.layout).coerceAtLeast(1) }

    /** True when page [index] has a free slot. */
    fun hasRoomOn(pages: List<HomePage>, index: Int, capacityOf: (PageLayout) -> Int): Boolean =
        pages.getOrNull(index)?.let { it.keys.size < capacityOf(it.layout).coerceAtLeast(1) } ?: pages.isEmpty()

    /** Inserts a blank page after [afterPage]. */
    fun insertPage(pages: List<HomePage>, afterPage: Int, layout: PageLayout = PageLayout()): List<HomePage> =
        pages.toMutableList().apply { add((afterPage + 1).coerceIn(0, size), HomePage(emptyList(), layout, nextId(pages))) }

    /**
     * Deletes page [index] and every placement on it (wave 30, pagesExplicit: the items do not move to a neighbour page, they leave the home and
     * stay in the drawer). The last remaining page is emptied rather than removed. Callers that need the removed keys read them off [pages] first.
     */
    fun deletePage(pages: List<HomePage>, index: Int): List<HomePage> {
        if (index !in pages.indices) return pages
        if (pages.size == 1) return listOf(pages[0].copy(keys = emptyList(), id = if (pages[0].id == 0L) 1L else pages[0].id))
        return pages.filterIndexed { i, _ -> i != index }
    }

    /**
     * Changes the layout of page [index] — and of every visual page sharing its id, since they are
     * one stored page. Apps that no longer fit overflow forward.
     */
    fun setLayout(pages: List<HomePage>, index: Int, layout: PageLayout, capacityOf: (PageLayout) -> Int): List<HomePage> {
        if (index !in pages.indices) return pages
        val id = pages[index].id
        val work = pages.mapIndexed { i, p ->
            val mine = i == index || (id != 0L && p.id == id)
            Work(p.keys.toMutableList(), if (mine) layout else p.layout, p.id)
        }.toMutableList()
        cascade(work, capacityOf)
        return finish(work)
    }

    /** True if some page has free slots while a later page still has items (a density change leaves these). */
    fun hasGaps(pages: List<HomePage>, capacityOf: (PageLayout) -> Int): Boolean {
        for (i in 0 until pages.lastIndex) {
            if (pages[i].keys.size < capacityOf(pages[i].layout) && pages.drop(i + 1).any { it.keys.isNotEmpty() }) return true
        }
        return false
    }

    /**
     * Packs items forward so every page is full before the next starts (pages keep their layouts;
     * pages left empty are dropped, deliberate blank trailing pages are kept).
     */
    fun reflow(pages: List<HomePage>, capacityOf: (PageLayout) -> Int): List<HomePage> {
        val work = pages.map { Work(it.keys.toMutableList(), it.layout, it.id) }
        for (i in 0 until work.lastIndex) {
            val cap = capacityOf(work[i].layout).coerceAtLeast(1)
            var j = i + 1
            while (work[i].keys.size < cap && j < work.size) {
                if (work[j].keys.isEmpty()) { j++; continue }
                work[i].keys.add(work[j].keys.removeAt(0))
            }
        }
        // Drop pages emptied by the pull-forward, but keep a blank page that was already blank.
        val out = work.mapIndexedNotNull { i, w ->
            if (w.keys.isEmpty() && pages[i].keys.isNotEmpty()) null else HomePage(w.keys.toList(), w.layout, w.id)
        }
        return out.ifEmpty { listOf(HomePage(id = 1L)) }
    }

    /** Index of the page holding [key], or -1. */
    fun pageOf(pages: List<HomePage>, key: String): Int = pages.indexOfFirst { key in it.keys }

    /** Gives every stored page a unique, non-zero id (legacy data, duplicates after edits). */
    fun normalizeIds(pages: List<HomePage>): List<HomePage> {
        val seen = HashSet<Long>()
        var next = (pages.maxOfOrNull { it.id } ?: 0L) + 1L
        return pages.map { p ->
            if (p.id != 0L && seen.add(p.id)) p else p.copy(id = next++).also { seen += it.id }
        }
    }

    private fun nextId(pages: List<HomePage>): Long = (pages.maxOfOrNull { it.id } ?: 0L) + 1L

    private class Work(val keys: MutableList<String>, val layout: PageLayout, val id: Long)

    private fun cascade(work: MutableList<Work>, capacityOf: (PageLayout) -> Int) {
        var i = 0
        while (i < work.size) {
            val cap = capacityOf(work[i].layout).coerceAtLeast(1)
            while (work[i].keys.size > cap) {
                val spill = work[i].keys.removeAt(work[i].keys.lastIndex)
                // Spill-over is a continuation of the same stored page unless the next page already is.
                if (i + 1 == work.size || work[i + 1].id != work[i].id && work[i + 1].keys.size >= capacityOf(work[i + 1].layout)) {
                    work.add(i + 1, Work(mutableListOf(), work[i].layout, work[i].id))
                }
                work[i + 1].keys.add(0, spill)
            }
            i++
        }
    }

    /**
     * Freezes [work]. Wave 30 (pagesExplicit): a page the user added stays until the user deletes it, so a page emptied by moving or removing its
     * last item is kept.
     */
    private fun finish(work: List<Work>): List<HomePage> {
        val out = work.map { w -> HomePage(w.keys.toList(), w.layout, w.id) }
        return out.ifEmpty { listOf(HomePage(id = 1L)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Launch history events (the suggested app was removed in wave 38)
// ---------------------------------------------------------------------------------------------

/** One recorded launch. [hourOfDay] is local time, captured at record time. */
data class LaunchEvent(val key: String, val timeMillis: Long, val hourOfDay: Int)


/** Wave 52 (settingsFont): the ids the stored `uiFont` can hold, kept here so the preference, the settings file and theme packs agree; core-design's `FontCatalog` maps each to a family (a test keeps the two lists equal). */
object UiFonts {
    const val DEFAULT = "space_grotesk"
    val IDS: List<String> = listOf("space_grotesk", "system", "inter", "manrope", "dm_sans", "jetbrains_mono", "atkinson", "lexend")
    fun normalize(id: String?): String = if (id != null && id in IDS) id else DEFAULT
}
