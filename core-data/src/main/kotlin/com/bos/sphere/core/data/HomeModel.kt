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

enum class HomeScroll(val label: String) { Paged("Pages"), Continuous("Continuous") }

/** Corner look: a bare glyph, or a quarter-circle bubble hugging the screen corner. */
/** Holo = bare glyphs; Quarter = Sony-style quarter discs in the corners; Bubble = round floating bubbles. */
enum class CornerStyle(val label: String) { Glyph("Holo"), Quarter("Sony quarter discs"), Bubble("Bubbles") }

/** Where the home clock sits. It can also be a page tile ([HomeTiles.CLOCK]) or a corner Time complication. */
enum class ClockPosition(val label: String) { Top("Top"), Bottom("Bottom"), Hidden("Hidden") }

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

/** Small readouts that can sit in the status strip (the clock itself is one of them). */
enum class StripItem(val label: String) {
    Time("Time"),
    Battery("Battery"),
    Signal("Signal"),
    Steps("Steps"),
    NextAlarm("Next alarm"),
    Ringer("Ring mode"),
    Storage("Storage"),
    Memory("Memory"),
    Notifications("Notifications"),
    /** The items below need Health Connect (a Fitbit / Samsung Health / Garmin / Google Fit app writing into it). */
    Floors("Floors climbed"),
    Elevation("Elevation gained"),
    Distance("Distance"),
    Calories("Active calories"),
}

/** Where steps and the other activity numbers come from. */
enum class HealthSourcePref(val label: String) { Phone("Phone sensor"), HealthConnect("Health Connect") }

/** Where in the strip an item sits. */
enum class StripZone(val label: String) { Left("Left"), Centre("Centre"), Right("Right") }

/** What sits behind everything on the home screen. */
enum class BackgroundKind(val label: String) {
    Black("Black"), System("Wallpaper"), Solid("Solid"), Gradient("Gradient"), Texture("Texture"), Live("Live"), Picture("Picture"),
}

/** Web search engine for the "search the web" rows. DuckDuckGo is the default so !bangs work (!g, !gi, !yt, !w …). */
/** How the search screen presents results. */
enum class SearchLayout(val label: String) { Filmstrip("Filmstrip"), Orbit("Orbit"), Flat("Flat") }

/** What sits one swipe to the left of the first home page. */
enum class LeftPage(val label: String) { Off("Off"), Launchpad("Launchpad"), Widget("A widget") }

/** The backdrop behind Settings. */
/** The clock as one line ("23:02   18°C") or time over a second line. */
enum class ClockLayout(val label: String) { Inline("One line"), Stacked("Two lines") }

/** What fills the background of Radar (search). */
enum class SearchBackdropStyle(val label: String) { Picture("Picture"), None("Plain black"), Radar("Radar sweep") }

/** How pages change when you swipe between them. */
enum class PageTransition(val label: String) { Slide("Slide"), Fade("Fade"), Zoom("Zoom"), Cube("Cube"), Flip("Flip"), Stack("Stack") }

/** How a small segmented switcher labels its options. */
enum class SwitchStyle(val label: String) { IconText("Icons + text"), TextOnly("Text only"), IconsOnly("Icons only") }

/** Light or dark Launchpad (System follows the phone). */
enum class LaunchpadTheme(val label: String) { Dark("Dark"), Light("Light"), System("Follow the phone") }

enum class SettingsBackdrop(val label: String) { Wallpaper("Wallpaper, dimmed"), Black("Pitch black") }

enum class WebEngine(val label: String, private val template: String) {
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    Google("Google", "https://www.google.com/search?q=%s"),
    Brave("Brave", "https://search.brave.com/search?q=%s"),
    Bing("Bing", "https://www.bing.com/search?q=%s"),
    Startpage("Startpage", "https://www.startpage.com/do/search?q=%s");

    /** The results URL for [query] (URL-encoded). Bangs are just text; DuckDuckGo interprets them. */
    fun url(query: String): String = template.replace("%s", java.net.URLEncoder.encode(query.trim(), "UTF-8"))
}

/** Clock number format. */
enum class ClockFormat(val label: String) { System("System"), Hour12("12-hour"), Hour24("24-hour") }

/** Whether corner actions show a drawn glyph or the real icon of the app they open. */
enum class CornerIconMode(val label: String) { Glyph("Glyph"), AppIcon("App icon") }

/** What lives on the home pages. */
enum class AppsOnHome(val label: String) { Everything("All apps"), PinnedOnly("Only ones I add") }

/** One page or many. */
enum class PageMode(val label: String) { Multiple("Multiple pages"), Single("One page") }

/** App drawer presentation. */
enum class DrawerView(val label: String) { Grid("Grid"), List("List"), Details("Details") }

/** App drawer ordering. */
enum class DrawerSort(val label: String) { Alphabetical("A–Z"), Category("Type"), Recent("Recent"), Frequent("Most used") }

/** Which screen edge(s) carry the quick-scrub rail. */
enum class RailSide(val label: String) { Left("Left"), Right("Right"), Both("Both") }

/** The measurement the background indicator shows. */
enum class IndicatorMetric(val label: String) {
    Network("Signal"),
    Battery("Battery"),
    Storage("Storage used"),
    Memory("Memory used"),
    Notifications("Notifications"),
    Volume("Volume"),
    DayProgress("Time of day"),
    Fixed("Fixed colour"),
}

/** Visual effects the indicator can paint. Several can be on at once. */
enum class IndicatorEffect(val label: String) {
    DotsBreathe("Breathing dots"),
    DotsBloomCentre("Dot bloom from centre"),
    DotsBloomEdges("Dot bloom from edges"),
    Vignette("Vignette"),
    ComicLines("Comic-book lines"),
    SnakeOutline("Snake outline"),
    EdgeMeter("Edge meter"),
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
    val homeScroll: HomeScroll = HomeScroll.Paged,
    /** Show the A–Z letters of the drawer's quick-scrub strip (it still works when hidden). */
    val drawerRailVisible: Boolean = true,
    /** Grid / List / Details switcher in the app drawer. */
    val drawerViewSwitch: SwitchStyle = SwitchStyle.IconText,
    val scrollAxis: ScrollAxis = ScrollAxis.Long,
    /** Icons across the short side in the Continuous / DSi homes. */
    val continuousColumns: Int = 4,
    val portraitGrid: GridSpec = DensityPreset.Default.portrait,
    val landscapeGrid: GridSpec = DensityPreset.Default.landscape,
    /** Icon edge as a fraction of the cell pitch. */
    val iconScale: Float = DEFAULT_ICON_SCALE,
    val showLabels: Boolean = false,
    val showSuggestion: Boolean = true,
    /** false = opaque Hyle substrate (the reference look); true = the system wallpaper shows through. */
    val showWallpaper: Boolean = false,
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
    val drawerStyle: DrawerStyle = DrawerStyle.Wheel,
    val clockPosition: ClockPosition = ClockPosition.Top,
    /** The substrate dots breathe in the network's colour (green good … red poor, grey airplane, dark offline). */
    val networkDots: Boolean = true,
    /** Console-style interface tones in the settings space. */
    val uiSounds: Boolean = true,
    val uiSoundVolume: Float = 0.4f,
    val wheelGrouping: WheelGrouping = WheelGrouping.Letter,

    val healthSource: HealthSourcePref = HealthSourcePref.Phone,
    /** An always-open keyboard that searches apps as you type — the old-school-phone mode. */
    val alwaysKeyboard: Boolean = false,
    // --- background
    val backgroundKind: BackgroundKind = BackgroundKind.Black,
    /** Preset id within the kind (e.g. "aurora", "grain", "dusk"); ignored for Black / System. */
    val backgroundPreset: String = "",
    /** App-private copy of the photo chosen as the background (Picture → "photo"); empty = none. */
    val backgroundPicturePath: String = "",
    /** Custom colours: Solid uses A; Gradient uses A → B; textures and live scenes tint with A. */
    val backgroundColorA: Long = 0xFF0A0B10,
    val backgroundColorB: Long = 0xFF2A1B5E,

    // --- search
    val webEngine: WebEngine = WebEngine.DuckDuckGo,
    val searchWeb: Boolean = true,
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
    /** The bundled backdrop picture behind search (off = plain black). */
    val searchBackdrop: Boolean = true,
    /** Picture / plain black / animated radar sweep behind Radar. [searchBackdrop] = false still forces plain black. */
    val searchBackdropStyle: SearchBackdropStyle = SearchBackdropStyle.Picture,
    /** The page the Home button returns to. */
    val homePage: Int = 0,
    val pageTransition: PageTransition = PageTransition.Slide,
    /** A track + prev / play / next strip on the home while something is playing. */
    val mediaControls: Boolean = false,
    /** Filmstrip / orbit keep drifting slowly; off = still until you move them. */
    val searchMotion: Boolean = true,
    val searchFiles: Boolean = true,
    val searchActions: Boolean = true,
    /** Ask installed Fylz / Fotoz for deep (text-in-image, content) matches. */
    val searchDeep: Boolean = true,

    // --- first run
    val onboardingDone: Boolean = false,

    // --- status strip: which readouts sit where (absent = not shown)
    val strip: Map<StripItem, StripZone> = mapOf(StripItem.Time to StripZone.Centre),
    /** 0 = compact square cells, 1 = icons spread to fill the free space (rows and columns separately). */
    val iconSpacing: Float = 0f,

    // --- clock
    /** Clock text size in sp. */
    val clockSize: Float = 26f,
    val clockLayout: ClockLayout = ClockLayout.Inline,
    val clockFormat: ClockFormat = ClockFormat.System,
    val clockShowDate: Boolean = false,
    val clockShowDay: Boolean = false,
    val clockShowWeather: Boolean = false,
    val weatherFahrenheit: Boolean = false,

    // --- corners
    /** Corner icon edge in dp. */
    val cornerIconSize: Float = 32f,
    val cornerIconMode: CornerIconMode = CornerIconMode.Glyph,

    // --- screen
    /** Hide the system status bar on home so corner icons and the clock sit flush to the top. */
    val hideStatusBar: Boolean = true,

    // --- apps and pages
    val appsOnHome: AppsOnHome = AppsOnHome.Everything,
    val pageMode: PageMode = PageMode.Multiple,
    val drawerView: DrawerView = DrawerView.Grid,
    val drawerSort: DrawerSort = DrawerSort.Alphabetical,
    val drawerRail: RailSide = RailSide.Right,

    // --- indicator (the background dots and effects)
    val indicatorMetric: IndicatorMetric = IndicatorMetric.Network,
    val indicatorEffects: Set<IndicatorEffect> = setOf(IndicatorEffect.DotsBreathe),
    /** ARGB colours for low / middle / high readings, plus idle (airplane / fixed). */
    val indicatorLow: Long = 0xFFFF4D4D,
    val indicatorMid: Long = 0xFFFFB347,
    val indicatorHigh: Long = 0xFF6BFF8F,
    val indicatorIdle: Long = 0xFF8A8F99,
    /** true = low readings are the good ones (storage / memory used), flipping the colour ramp. */
    val indicatorInvert: Boolean = false,
    val indicatorIntensity: Float = 0.6f,
    val indicatorSpeed: Float = 0.5f,

    // --- theme
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accentColor: Long = 0xFF8E7BFF,
    val iconShape: IconShape = IconShape.Rounded,
    val iconTint: IconTint = IconTint.None,
    /** Draw a plate behind transparent icons. */
    val iconPlate: Boolean = true,

    // --- language
    val language: UiLanguage = UiLanguage.System,
) {
    fun gridFor(landscape: Boolean): GridSpec = if (landscape) landscapeGrid else portraitGrid
    fun pagingFor(landscape: Boolean): PagingAxis = if (landscape) landscapePaging else portraitPaging

    companion object {
        const val DEFAULT_ICON_SCALE = 0.82f
        const val MIN_ICON_SCALE = 0.5f
        const val MAX_ICON_SCALE = 0.95f
        const val MAX_ICON_SPACING = 1f
        const val MIN_CLOCK_SIZE = 14f
        const val MAX_CLOCK_SIZE = 96f
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

/** How "All apps" opens. */
enum class DrawerStyle(val label: String) {
    /** A Concepts-style wheel of app spokes fanned out around the corner it was opened from. */
    Wheel("Wheel"),
    /** The separate app-drawer space (grid / list / details, sortable, with edge scrubbing). */
    List("Drawer"),
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
val LauncherPrefs.isPaged: Boolean get() = homeStyle == HomeStyle.Grid && homeScroll == HomeScroll.Paged

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
 * The persisted home arrangement. [seeded] is false until the first arrangement is written
 * (before that, every app flows onto the pages alphabetically). [removed] holds apps the user
 * took off the home pages (still in the drawer).
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
     * removed, apps not yet placed (new installs, first run) appended to the last page, and every
     * stored page split into as many visual pages (same layout and id) as it needs, so a density
     * change never hides an app. A page stored empty (a deliberate blank page) is kept; a page that
     * only *became* empty because its apps are hidden or unavailable is not drawn. Always returns
     * at least one page.
     */
    fun visualPages(
        layout: HomeLayout,
        available: List<String>,
        capacityOf: (PageLayout) -> Int,
        /** false = only apps the user placed appear (new installs are not appended). */
        autoPlace: Boolean = true,
    ): List<HomePage> {
        val known = available.toHashSet().apply { addAll(HomeTiles.ALL) }
        val placed = HashSet<String>()
        val stored = normalizeIds(layout.pages)
        val pages = stored.map { page ->
            page to page.copy(keys = page.keys.filter { it in known && placed.add(it) })
        }.toMutableList()
        val extras = if (autoPlace) available.filter { it !in placed && it !in layout.removed } else emptyList()
        if (extras.isNotEmpty()) {
            if (pages.isEmpty()) {
                val fresh = HomePage(id = 1L)
                pages += fresh to fresh
            }
            val (orig, last) = pages.last()
            pages[pages.lastIndex] = orig to last.copy(keys = last.keys + extras)
        }
        val out = pages.flatMap { (orig, page) ->
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
     * layout, new id). Overflow cascades into following pages; a page created by overflow belongs
     * to the page it spilled from (same layout and id). With [dropEmpty] false the page count is
     * preserved (the live drag preview, so page indices don't shift under the finger).
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
        val page = work[target].keys
        page.add(toIndex.coerceIn(0, page.size), key)
        cascade(work, capacityOf)
        // Only the page the item was dragged out of is dropped when emptied; deliberate blank pages stay.
        return finish(work, dropIndex = if (dropEmpty && src != target) src else -1)
    }

    /** Removes [key] from the pages (its page is dropped if that empties it). */
    fun remove(pages: List<HomePage>, key: String): List<HomePage> {
        val src = pages.indexOfFirst { key in it.keys }
        val work = pages.map { p -> Work(p.keys.filter { it != key }.toMutableList(), p.layout, p.id) }.toMutableList()
        return finish(work, dropIndex = src)
    }

    /** Adds [key] at the end of [preferredPage] if it has room, else at the end of the last page. */
    fun add(pages: List<HomePage>, key: String, preferredPage: Int, capacityOf: (PageLayout) -> Int): List<HomePage> {
        val src = pages.indexOfFirst { key in it.keys }
        val work = pages.map { p -> Work(p.keys.filter { it != key }.toMutableList(), p.layout, p.id) }.toMutableList()
        if (work.isEmpty()) work += Work(mutableListOf(), PageLayout(), 1L)
        val pref = preferredPage.coerceIn(0, work.lastIndex)
        val dest = if (work[pref].keys.size < capacityOf(work[pref].layout)) pref else work.lastIndex
        work[dest].keys.add(key)
        cascade(work, capacityOf)
        return finish(work, dropIndex = if (src != dest) src else -1)
    }

    /** Inserts a blank page after [afterPage]. */
    fun insertPage(pages: List<HomePage>, afterPage: Int, layout: PageLayout = PageLayout()): List<HomePage> =
        pages.toMutableList().apply { add((afterPage + 1).coerceIn(0, size), HomePage(emptyList(), layout, nextId(pages))) }

    /** Deletes page [index]; its apps move to the end of the previous (or next) page. */
    fun deletePage(pages: List<HomePage>, index: Int, capacityOf: (PageLayout) -> Int): List<HomePage> {
        if (index !in pages.indices || pages.size == 1) return pages
        val orphans = pages[index].keys
        val work = pages.filterIndexed { i, _ -> i != index }
            .map { Work(it.keys.toMutableList(), it.layout, it.id) }.toMutableList()
        val dest = (index - 1).coerceAtLeast(0)
        work[dest].keys.addAll(orphans)
        cascade(work, capacityOf)
        return finish(work, dropIndex = -1)
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
        return finish(work, dropIndex = -1)
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

    /** Freezes [work]; page [dropIndex] is dropped if it ended up empty (-1 = keep all). */
    private fun finish(work: List<Work>, dropIndex: Int): List<HomePage> {
        val out = work.mapIndexedNotNull { i, w ->
            if (i == dropIndex && w.keys.isEmpty()) null else HomePage(w.keys.toList(), w.layout, w.id)
        }
        return out.ifEmpty { listOf(HomePage(id = 1L)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Suggested app
// ---------------------------------------------------------------------------------------------

/** One recorded launch. [hourOfDay] is local time, captured at record time. */
data class LaunchEvent(val key: String, val timeMillis: Long, val hourOfDay: Int)

object AppSuggester {
    private const val DAY_MS = 86_400_000.0
    private const val HALF_LIFE_DAYS = 10.0

    /**
     * Picks the app most likely to be wanted now: recency-decayed launch count, boosted for
     * launches made around the same hour of day. Returns null when nothing qualifies.
     */
    fun suggest(
        events: List<LaunchEvent>,
        nowMillis: Long,
        nowHour: Int,
        candidates: Set<String>,
        exclude: Set<String>,
    ): String? {
        val scores = HashMap<String, Double>()
        for (e in events) {
            if (e.key !in candidates || e.key in exclude) continue
            val ageDays = ((nowMillis - e.timeMillis).coerceAtLeast(0L)) / DAY_MS
            val decay = exp(-ageDays * Math.log(2.0) / HALF_LIFE_DAYS)
            val dh = Math.floorMod(e.hourOfDay - nowHour, 24).let { minOf(it, 24 - it) }
            val hourBoost = when (dh) { 0 -> 3.0; 1 -> 2.0; 2 -> 1.3; else -> 1.0 }
            scores[e.key] = (scores[e.key] ?: 0.0) + decay * hourBoost
        }
        return scores.maxWithOrNull(compareBy<Map.Entry<String, Double>> { it.value }.thenBy { it.key })?.key
    }
}
