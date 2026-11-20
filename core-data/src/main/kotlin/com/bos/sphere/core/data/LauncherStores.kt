package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Process-wide persisted launcher state. One instance per process so the home activity and the
 * motion-gesture service observe the same live [StateFlow]s. SharedPreferences-backed (like
 * [HiddenAppsStore]) — no extra dependencies.
 */
class LauncherStores private constructor(context: Context) {
    private val app = context.applicationContext

    /** NFR-18: widget ids and the background picture path belong to one device; they live in a file the platform backup excludes. */
    private val deviceBound = TolerantPrefs.of(app, DeviceBound.FILE)

    val prefs = LauncherPrefsStore(TolerantPrefs.of(app, "launcher_prefs"), deviceBound)
    val layout = HomeLayoutStore(TolerantPrefs.of(app, "home_layout"), deviceBound)
    init { prefs.migratePages(layout.layout.value.pages) } // wave 28: the Pages setting from the old Scroll / Paginated choice, once the layout is known
    val shortcuts = ShortcutStore(TolerantPrefs.of(app, "shortcuts"), deviceBound)
    val history = LaunchHistoryStore(TolerantPrefs.of(app, "launch_history"))
    /** HOM-18: the larger, file-backed log the timeline reads; fed by every [history] record. */
    val timelineLog = TimelineLogStore(java.io.File(app.filesDir, TimelineLogStore.RELATIVE_PATH)).also { log ->
        history.mirror = log::record
        log.seedFrom(history.events.value)
    }
    /** Wave 41: the Timeline's own record of events nothing else keeps (scheduled launch fired, package changes, hide / unhide). */
    val timelineEvents = TimelineEvents.init(app)
    /** HOM-22: what the user removed from the Recent landing; private usage history, never exported or backed up. */
    val recentsState = RecentsStateStore(java.io.File(app.filesDir, RecentsStateStore.RELATIVE_PATH))
    val iconColors = IconColorStore(TolerantPrefs.of(app, "icon_colors"))

    companion object {
        @Volatile private var instance: LauncherStores? = null

        fun get(context: Context): LauncherStores =
            instance ?: synchronized(this) { instance ?: LauncherStores(context).also { instance = it } }
    }
}

class LauncherPrefsStore internal constructor(private val sp: SharedPreferences, private val device: SharedPreferences? = null) {
    private val _prefs = MutableStateFlow(run { DeviceBound.migratePrefs(sp, device); migrateEffects(); read() })
    val prefs: StateFlow<LauncherPrefs> = _prefs.asStateFlow()

    /**
     * Wave 28: settles the Pages setting of an install that has no stored `homePages` yet (it came from the old Scroll / Paginated choice): with nothing chosen,
     * a home that already has built pages becomes Paginated so no page is ever hidden by the update. Runs once at start, when the layout is known.
     */
    fun migratePages(pages: List<HomePage>) {
        if (sp.contains("homePages")) return
        val mode = HomeModelMigration.pagesMode(null, sp.getString("homeScroll", null), if (sp.contains("pagesModeChosen")) sp.getBoolean("pagesModeChosen", false) else null, HomeModelMigration.hasBuiltPages(pages))
        forceWrite = true
        update { it.copy(homePages = mode) }
    }
    private var forceWrite = false

    fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        val next = transform(_prefs.value).let {
            it.copy(
                portraitGrid = it.portraitGrid.coerced(),
                landscapeGrid = it.landscapeGrid.coerced(),
                iconScale = it.iconScale.coerceIn(LauncherPrefs.MIN_ICON_SCALE, LauncherPrefs.MAX_ICON_SCALE),
                motionSensitivity = it.motionSensitivity.coerceIn(0f, 1f),
                uiSoundVolume = it.uiSoundVolume.coerceIn(0f, 1f),
                iconSpacing = it.iconSpacing.coerceIn(0f, 1f),
                cornerIconSize = it.cornerIconSize.coerceIn(LauncherPrefs.MIN_CORNER_ICON, LauncherPrefs.MAX_CORNER_ICON),
                indicatorIntensity = it.indicatorIntensity.coerceIn(0f, 1f),
                indicatorSpeed = it.indicatorSpeed.coerceIn(0f, 1f),
                recentsLimit = HomeRecents.clampLimit(it.recentsLimit),
            ).let { p -> BackgroundChoice.normalised(p.backgroundSlot1, p.backgroundSlot2).let { c -> p.copy(backgroundSlot1 = c.slot1, backgroundSlot2 = c.slot2) } }
            .let { p -> p.withWallpaper(WallpaperSurface.Glance, p.rawWallpaper(WallpaperSurface.Glance)).let { q -> q.withWallpaper(WallpaperSurface.Pages, q.rawWallpaper(WallpaperSurface.Pages)) } }
        }
        if (next == _prefs.value && !forceWrite) return
        forceWrite = false
        _prefs.value = next
        sp.edit()
            .putString("style", next.homeStyle.name).putString("homePages", next.homePages.name).putString("scrollAxis", next.scrollAxis.name).putInt("contCols", next.continuousColumns)
            .putInt("pCols", next.portraitGrid.columns).putInt("pRows", next.portraitGrid.rows)
            .putInt("lCols", next.landscapeGrid.columns).putInt("lRows", next.landscapeGrid.rows)
            .putFloat("iconScale", next.iconScale)
            .putBoolean("labels", next.showLabels)
            .putBoolean("cornersFollow", next.cornersFollowDevice)
            .putString("pPaging", next.portraitPaging.name)
            .putString("lPaging", next.landscapePaging.name)
            .putBoolean("motion", next.motionEnabled)
            .putBoolean("motionBg", next.motionInBackground)
            .putFloat("motionSens", next.motionSensitivity)
            .putString("cornerStyle", next.cornerStyle.name)
            .putBoolean("uiSounds", next.uiSounds)
            .putFloat("uiSoundVolume", next.uiSoundVolume)
            .putFloat("iconSpacing", next.iconSpacing)
            .putString("webEngine", next.webEngine.name)
            .putBoolean("searchWeb", next.searchWeb).putBoolean("webSuggestions", next.webSuggestions).putBoolean("searchContacts", next.searchContacts)
            .putString("leftPage", next.leftPage.name).putBoolean("launchpadRail", next.launchpadRail).putString("lpTheme", next.launchpadTheme.name)
            .putString("settingsBackdrop", next.settingsBackdrop.name).putFloat("settingsDim", next.settingsDim)
            .putFloat("spacingV", next.spacingV).putFloat("gridBias", next.gridBias).putFloat("gridInsetX", next.gridInsetX).putFloat("gridInsetTop", next.gridInsetTop)
            .putFloat("gridInsetBottom", next.gridInsetBottom).putFloat("labelScale", next.labelScale)
            .putString("searchLayout", next.searchLayout.name).putInt("homePage", next.homePage).putString("pageTransition", next.pageTransition.name)
            .putBoolean("searchMotion", next.searchMotion).putBoolean("searchFiles", next.searchFiles)
            .putString("bgSlot1", next.backgroundSlot1.id).putString("bgSlot2", next.backgroundSlot2.id)
            .putString("wpGlanceKind", next.glanceWallpaperKind.id).putLong("wpGlanceSolid", next.glanceSolid)
            .putLong("wpGlanceFrom", next.glanceGradFrom).putLong("wpGlanceTo", next.glanceGradTo).putInt("wpGlanceAngle", next.glanceGradAngle)
            .putBoolean("wpPagesSame", next.pagesSameAsGlance).putString("wpPagesKind", next.pagesWallpaperKind.id).putLong("wpPagesSolid", next.pagesSolid)
            .putLong("wpPagesFrom", next.pagesGradFrom).putLong("wpPagesTo", next.pagesGradTo).putInt("wpPagesAngle", next.pagesGradAngle)
            .putString("wpPagesSlot1", next.pagesSlot1.id).putString("wpPagesSlot2", next.pagesSlot2.id)
            .also { WallpaperPictureKeys.write(it, "wpGlance", next.glancePicture, next.glanceSystemDim); WallpaperPictureKeys.write(it, "wpPages", next.pagesPicture, next.pagesSystemDim) }
            .putString("radarIntensity", next.radarIntensity.name).putString("radarSignalForce", next.radarSignalForce.name).putString("radarBackground", next.radarBackground.name)
            .putBoolean("searchActions", next.searchActions).putBoolean("searchDeep", next.searchDeep)
            .putBoolean("onboardingDone", next.onboardingDone)
            .putBoolean("alwaysKeyboard", next.alwaysKeyboard)
            .putString("clockStyle", next.clockStyle.name)
            .putString("clockFormat", next.clockFormat.name)
            .putBoolean("glanceClockDate", next.glanceClockDate).putBoolean("glanceClockAccent", next.glanceClockAccent).putString("glanceClockSize", next.glanceClockSize.name)
            .putBoolean("clockTileAnalog", next.clockTileAnalog).putString("clockTileFormat", next.clockTileFormat.name).putBoolean("clockTileDate", next.clockTileDate)
            .putBoolean("badgesOn", next.badgesOn).putBoolean("badgeCount", next.badgeCount)
            .putBoolean("adaptiveGrid", next.adaptiveGrid).putFloat("gridDensity", next.gridDensity)
            .putFloat("cornerIconSize", next.cornerIconSize)
            .putString("cornerIconMode", next.cornerIconMode.name)
            .putBoolean("hideStatusBar", next.hideStatusBar)
            .putBoolean("restoreSurfaces", next.restoreOpenSurfaces)
            .putString("appsOnHome", next.appsOnHome.name)
            .putString("pageMode", next.pageMode.name)
            .putString("indMetric", next.indicatorMetric.name)
            .putString("indCalm", IndicatorMigration.store(next.indicatorCalm)).putString("indAlert", IndicatorMigration.store(next.indicatorAlert))
            .putLong("indLow", next.indicatorLow).putLong("indMid", next.indicatorMid)
            .putLong("indHigh", next.indicatorHigh).putLong("indIdle", next.indicatorIdle)
            .putFloat("indIntensity", next.indicatorIntensity)
            .putFloat("indSpeed", next.indicatorSpeed)
            .putString("themeMode", next.themeMode.name)
            .putLong("accent", next.accentColor)
            .putString("iconShape", next.iconShape.name)
            .putString("iconTint", next.iconTint.name)
            .putBoolean("iconPlate", next.iconPlate)
            .putString("language", next.language.name).putBoolean("searchLayoutPinned", next.searchLayoutPinned)
            .putBoolean("kbOpenP", next.keyboardOnOpenPortrait).putBoolean("kbOpenL", next.keyboardOnOpenLandscape)
            .putBoolean("cornersPerOri", next.cornersPerOrientation)
            .putBoolean("hotCornersOn", next.hotCornersOn).putInt("hotCornerDwell", next.hotCornerDwellMs)
            .putString("wheelGrouping", next.wheelGrouping.name)
            .putBoolean("glanceEnabled", next.glanceEnabled).putString("homeOrder", next.homeOrder.name)
            .putBoolean("showMusic", next.showMusic).putString("glanceHotspots", GlanceHotspots.encode(next.glanceHotspots))
            .putInt("glanceControlsSec", next.glanceControlsSeconds).putBoolean("glanceIndicator", next.glanceIndicator)
            .putInt("recentsLimit", next.recentsLimit)
            .putBoolean("recentsUsage", next.recentsUseUsageAccess)
            .putString("rightSide", next.rightSide.name)
            .apply()
        // Wave 26: the removed home-landing keys are deleted once (HomeModelMigration read them).
        if (ClockRetired.KEYS.any { sp.contains(it) }) sp.edit().also { e -> ClockRetired.KEYS.forEach { e.remove(it) } }.apply() // wave 38: the clock position, layout, size, date, weather and the status strip are gone
        if (sp.contains("suggestion")) sp.edit().remove("suggestion").apply() // wave 38: the Suggested app slot is gone
        if (GlanceMusic.RETIRED_KEYS.any { sp.contains(it) }) sp.edit().also { e -> GlanceMusic.RETIRED_KEYS.forEach { e.remove(it) } }.apply() // wave 38: showMusic was just written from them
        if (HomeModelMigration.RETIRED_KEYS.any { sp.contains(it) }) sp.edit().also { e -> HomeModelMigration.RETIRED_KEYS.forEach { e.remove(it) } }.apply()
        if (sp.contains("indEffects") || sp.contains("indInvert")) sp.edit().remove("indEffects").remove("indInvert").apply() // wave 35: the old effect set and the ramp flip
        // Wave 15: the keys of the removed background gallery are deleted once, so nothing of it lingers.
        if (RetiredBackgroundKeys.KEYS.any { it != "bgPicture" && sp.contains(it) }) {
            sp.edit().also { e -> RetiredBackgroundKeys.KEYS.forEach { e.remove(it) } }.apply()
        }
        DeviceBound.writePrefs(sp, device, next.leftWidgetId)
    }

    /** Wave 35: the calm / alert selections; the old `indEffects` set (read through [IndicatorMigration]) only when the new keys were never written. */
    private fun readEffects(d: LauncherPrefs): IndicatorSelection {
        val legacy = sp.getString("indEffects", null) // read (and so accounted for) on every path, so an import never carries it as an unknown field
        return if (sp.contains("indCalm") || sp.contains("indAlert")) {
            IndicatorSelection(
                if (sp.contains("indCalm")) IndicatorMigration.parse<CalmEffect>(sp.getString("indCalm", null)) else d.indicatorCalm,
                IndicatorMigration.parse<AlertEffect>(sp.getString("indAlert", null)),
            )
        } else IndicatorMigration.fromLegacy(legacy) ?: IndicatorSelection(d.indicatorCalm, d.indicatorAlert)
    }

    /** Wave 35: persists the migrated selections once and drops the old `indEffects` / `indInvert` keys, so a later retired-key purge has nothing to lose. */
    private fun migrateEffects() {
        if (!sp.contains("indEffects") && !sp.contains("indInvert")) return
        val sel = readEffects(LauncherPrefs())
        sp.edit().putString("indCalm", IndicatorMigration.store(sel.calm)).putString("indAlert", IndicatorMigration.store(sel.alert))
            .remove("indEffects").remove("indInvert").apply()
    }

    private fun read(): LauncherPrefs {
        val d = LauncherPrefs()
        // Wave 15: any stored choice of the old background gallery is ignored; no stored slots means the default pair.
        val bg = BackgroundChoice.fromStored(sp.getString("bgSlot1", null), sp.getString("bgSlot2", null))
        val pagesBg = BackgroundChoice.fromStored(sp.getString("wpPagesSlot1", null), sp.getString("wpPagesSlot2", null))
        val effects = readEffects(d)
        val migrated = HomeModelMigration.resolve(sp.getString("homeLanding", null), if (sp.contains("glanceEnabled")) sp.getBoolean("glanceEnabled", true) else null)
        // Wave 28: the Pages setting; the old Scroll / Paginated choice is read only when no homePages is stored (LauncherStores.migratePages settles the unchosen case with the layout).
        val pagesMode = HomeModelMigration.pagesMode(
            sp.getString("homePages", null), sp.getString("homeScroll", null), if (sp.contains("pagesModeChosen")) sp.getBoolean("pagesModeChosen", false) else null, hasPages = false,
        )
        return LauncherPrefs(
            glanceWallpaperKind = WallpaperKind.forId(sp.getString("wpGlanceKind", null)) ?: d.glanceWallpaperKind,
            glanceSolid = sp.getLong("wpGlanceSolid", d.glanceSolid) or GradientSpec.OPAQUE,
            glanceGradFrom = sp.getLong("wpGlanceFrom", d.glanceGradFrom) or GradientSpec.OPAQUE,
            glanceGradTo = sp.getLong("wpGlanceTo", d.glanceGradTo) or GradientSpec.OPAQUE,
            glanceGradAngle = GradientSpec(angle = sp.getInt("wpGlanceAngle", d.glanceGradAngle)).normalised().angle,
            pagesSameAsGlance = sp.getBoolean("wpPagesSame", d.pagesSameAsGlance),
            pagesWallpaperKind = WallpaperKind.forId(sp.getString("wpPagesKind", null)) ?: d.pagesWallpaperKind,
            pagesSolid = sp.getLong("wpPagesSolid", d.pagesSolid) or GradientSpec.OPAQUE,
            pagesGradFrom = sp.getLong("wpPagesFrom", d.pagesGradFrom) or GradientSpec.OPAQUE,
            pagesGradTo = sp.getLong("wpPagesTo", d.pagesGradTo) or GradientSpec.OPAQUE,
            pagesGradAngle = GradientSpec(angle = sp.getInt("wpPagesAngle", d.pagesGradAngle)).normalised().angle,
            pagesSlot1 = pagesBg.slot1, pagesSlot2 = pagesBg.slot2,
            glancePicture = WallpaperPictureKeys.readPicture(sp, "wpGlance"), pagesPicture = WallpaperPictureKeys.readPicture(sp, "wpPages"),
            glanceSystemDim = WallpaperPictureKeys.readDim(sp, "wpGlance"), pagesSystemDim = WallpaperPictureKeys.readDim(sp, "wpPages"),
            homeStyle = enumOr(sp.getString("style", null), d.homeStyle),
            homePages = pagesMode,
            scrollAxis = enumOr(sp.getString("scrollAxis", null), d.scrollAxis),
            continuousColumns = sp.getInt("contCols", d.continuousColumns),
            portraitGrid = GridSpec(sp.getInt("pCols", d.portraitGrid.columns), sp.getInt("pRows", d.portraitGrid.rows)).coerced(),
            landscapeGrid = GridSpec(sp.getInt("lCols", d.landscapeGrid.columns), sp.getInt("lRows", d.landscapeGrid.rows)).coerced(),
            iconScale = sp.getFloat("iconScale", d.iconScale)
                .coerceIn(LauncherPrefs.MIN_ICON_SCALE, LauncherPrefs.MAX_ICON_SCALE),
            showLabels = sp.getBoolean("labels", d.showLabels),
            cornersFollowDevice = sp.getBoolean("cornersFollow", d.cornersFollowDevice),
            portraitPaging = enumOr(sp.getString("pPaging", null), d.portraitPaging),
            landscapePaging = enumOr(sp.getString("lPaging", null), d.landscapePaging),
            motionEnabled = sp.getBoolean("motion", d.motionEnabled),
            motionInBackground = sp.getBoolean("motionBg", d.motionInBackground),
            motionSensitivity = sp.getFloat("motionSens", d.motionSensitivity).coerceIn(0f, 1f),
            cornerStyle = enumOr(sp.getString("cornerStyle", null), d.cornerStyle),
            uiSounds = sp.getBoolean("uiSounds", d.uiSounds),
            uiSoundVolume = sp.getFloat("uiSoundVolume", d.uiSoundVolume).coerceIn(0f, 1f),
            iconSpacing = sp.getFloat("iconSpacing", d.iconSpacing).coerceIn(0f, 1f),
            webEngine = enumOr(sp.getString("webEngine", null), d.webEngine),
            searchWeb = sp.getBoolean("searchWeb", d.searchWeb),
            webSuggestions = sp.getBoolean("webSuggestions", d.webSuggestions),
            searchContacts = sp.getBoolean("searchContacts", d.searchContacts),
            backgroundSlot1 = bg.slot1, backgroundSlot2 = bg.slot2,
            leftPage = runCatching { LeftPage.valueOf(sp.getString("leftPage", null) ?: "") }.getOrDefault(d.leftPage),
            leftWidgetId = DeviceBound.leftWidgetOf(sp, device, d.leftWidgetId),
            launchpadRail = sp.getBoolean("launchpadRail", d.launchpadRail),
            launchpadTheme = enumOr(sp.getString("lpTheme", null), d.launchpadTheme),
            settingsBackdrop = runCatching { SettingsBackdrop.valueOf(sp.getString("settingsBackdrop", null) ?: "") }.getOrDefault(d.settingsBackdrop),
            settingsDim = sp.getFloat("settingsDim", d.settingsDim),
            spacingV = sp.getFloat("spacingV", d.spacingV),
            gridBias = sp.getFloat("gridBias", d.gridBias),
            gridInsetX = sp.getFloat("gridInsetX", d.gridInsetX),
            gridInsetTop = sp.getFloat("gridInsetTop", d.gridInsetTop),
            gridInsetBottom = sp.getFloat("gridInsetBottom", d.gridInsetBottom),
            labelScale = sp.getFloat("labelScale", d.labelScale),
            searchLayout = runCatching { SearchLayout.valueOf(sp.getString("searchLayout", null) ?: "") }.getOrDefault(d.searchLayout),
            homePage = sp.getInt("homePage", d.homePage).coerceAtLeast(0),
            pageTransition = enumOr(sp.getString("pageTransition", null), d.pageTransition),
            searchMotion = sp.getBoolean("searchMotion", d.searchMotion),
            radarIntensity = enumOr(sp.getString("radarIntensity", null), d.radarIntensity),
            radarSignalForce = enumOr(sp.getString("radarSignalForce", null), d.radarSignalForce),
            radarBackground = enumOr(sp.getString("radarBackground", null), d.radarBackground),
            searchFiles = sp.getBoolean("searchFiles", d.searchFiles),
            searchActions = sp.getBoolean("searchActions", d.searchActions),
            searchDeep = sp.getBoolean("searchDeep", d.searchDeep),
            // An install that already has settings saved is not a first run.
            onboardingDone = sp.getBoolean("onboardingDone", sp.contains("style") || sp.contains("pCols")),
            alwaysKeyboard = sp.getBoolean("alwaysKeyboard", d.alwaysKeyboard),
            clockStyle = ClockStyle.fromStored(runCatching { sp.getString("clockStyle", null) }.getOrNull()) ?: d.clockStyle,
            clockFormat = enumOr(sp.getString("clockFormat", null), d.clockFormat),
            glanceClockDate = sp.getBoolean("glanceClockDate", d.glanceClockDate),
            glanceClockAccent = sp.getBoolean("glanceClockAccent", d.glanceClockAccent),
            glanceClockSize = enumOr(sp.getString("glanceClockSize", null), d.glanceClockSize),
            clockTileAnalog = sp.getBoolean("clockTileAnalog", d.clockTileAnalog),
            clockTileFormat = enumOr(sp.getString("clockTileFormat", null), d.clockTileFormat),
            clockTileDate = sp.getBoolean("clockTileDate", d.clockTileDate),
            badgesOn = sp.getBoolean("badgesOn", d.badgesOn),
            badgeCount = sp.getBoolean("badgeCount", d.badgeCount),
            adaptiveGrid = sp.getBoolean("adaptiveGrid", d.adaptiveGrid),
            gridDensity = sp.getFloat("gridDensity", d.gridDensity).coerceIn(0.5f, 2f),
            cornerIconSize = sp.getFloat("cornerIconSize", d.cornerIconSize).coerceIn(LauncherPrefs.MIN_CORNER_ICON, LauncherPrefs.MAX_CORNER_ICON),
            cornerIconMode = enumOr(sp.getString("cornerIconMode", null), d.cornerIconMode),
            hideStatusBar = sp.getBoolean("hideStatusBar", d.hideStatusBar),
            restoreOpenSurfaces = sp.getBoolean("restoreSurfaces", d.restoreOpenSurfaces),
            // New installs keep the home nearly empty (the drawer has everything); existing ones keep All apps.
            appsOnHome = enumOr(sp.getString("appsOnHome", null), if (sp.contains("style") || sp.contains("pCols")) AppsOnHome.Everything else AppsOnHome.PinnedOnly),
            pageMode = enumOr(sp.getString("pageMode", null), d.pageMode),
            indicatorMetric = enumOr(sp.getString("indMetric", null), d.indicatorMetric),
            indicatorCalm = effects.calm, indicatorAlert = effects.alert,
            indicatorLow = sp.getLong("indLow", d.indicatorLow), indicatorMid = sp.getLong("indMid", d.indicatorMid),
            indicatorHigh = sp.getLong("indHigh", d.indicatorHigh), indicatorIdle = sp.getLong("indIdle", d.indicatorIdle),
            indicatorIntensity = sp.getFloat("indIntensity", d.indicatorIntensity).coerceIn(0f, 1f),
            indicatorSpeed = sp.getFloat("indSpeed", d.indicatorSpeed).coerceIn(0f, 1f),
            themeMode = enumOr(sp.getString("themeMode", null), d.themeMode),
            accentColor = sp.getLong("accent", d.accentColor),
            iconShape = enumOr(sp.getString("iconShape", null), d.iconShape),
            iconTint = enumOr(sp.getString("iconTint", null), d.iconTint),
            iconPlate = sp.getBoolean("iconPlate", d.iconPlate),
            language = enumOr(sp.getString("language", null), d.language),
            searchLayoutPinned = sp.getBoolean("searchLayoutPinned", d.searchLayoutPinned),
            keyboardOnOpenPortrait = sp.getBoolean("kbOpenP", d.keyboardOnOpenPortrait),
            keyboardOnOpenLandscape = sp.getBoolean("kbOpenL", d.keyboardOnOpenLandscape),
            cornersPerOrientation = sp.getBoolean("cornersPerOri", d.cornersPerOrientation),
            hotCornersOn = sp.getBoolean("hotCornersOn", d.hotCornersOn),
            hotCornerDwellMs = sp.getInt("hotCornerDwell", d.hotCornerDwellMs).coerceIn(0, 1000),
            wheelGrouping = enumOr(sp.getString("wheelGrouping", null), d.wheelGrouping),
            glanceEnabled = migrated.glanceEnabled, homeOrder = HomeOrder.parse(sp.getString("homeOrder", null)),
            showMusic = GlanceMusic.resolve(if (sp.contains("showMusic")) sp.getBoolean("showMusic", true) else null, sp.getString("glanceContent", null)),
            glanceHotspots = GlanceHotspots.decode(sp.getString("glanceHotspots", null)),
            glanceControlsSeconds = GlanceControls.clampSeconds(sp.getInt("glanceControlsSec", d.glanceControlsSeconds)),
            glanceIndicator = sp.getBoolean("glanceIndicator", d.glanceIndicator),
            recentsLimit = HomeRecents.clampLimit(sp.getInt("recentsLimit", d.recentsLimit)),
            recentsUseUsageAccess = sp.getBoolean("recentsUsage", d.recentsUseUsageAccess),
            rightSide = RightSide.parse(sp.getString("rightSide", null)),
        )
    }
}

class HomeLayoutStore internal constructor(private val sp: SharedPreferences, private val device: SharedPreferences? = null) {
    private val _layout = MutableStateFlow(read())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    /**
     * Persists an edited *visual* page list: split pages fold back together ([HomeLayoutMath.toStored])
     * and [preserve]d keys (stored but not drawable now — hidden apps, a paused work profile) keep
     * their places.
     */
    fun setPages(visual: List<HomePage>, preserve: Set<String> = emptySet()) {
        val cur = _layout.value
        val next = HomeLayoutMath.toStored(visual, cur.pages, preserve)
        // CFG-04: an edit that drops an app or a page from the arrangement snapshots first.
        if (cur.pages.size > next.size || cur.pages.any { p -> p.keys.any { k -> next.none { k in it.keys } } }) DestructiveEdits.before("remove from home")
        write(cur.copy(pages = next, seeded = true))
        markExplicit()
    }

    /** Replaces the whole arrangement, used when a workspace is switched (WSP-07). */
    fun replace(pages: List<HomePage>, removed: Set<String>) {
        write(HomeLayout(pages = HomeLayoutMath.normalizeIds(pages), removed = removed, seeded = true))
    }

    /** Takes [key] off the home pages (it stays in the drawer). */
    fun removeFromHome(key: String, visual: List<HomePage>, preserve: Set<String> = emptySet()) {
        DestructiveEdits.before("remove from home")
        val cur = _layout.value
        write(
            cur.copy(
                pages = HomeLayoutMath.toStored(HomeLayoutMath.remove(visual, key), cur.pages, preserve - key),
                removed = cur.removed + key,
                seeded = true,
            ),
        )
        markExplicit()
    }

    /**
     * Puts [key] back on the home pages. Wave 30: false (and nothing written) when no page has a free slot: the caller says "Page full"; a
     * page is never made for it. The preferred page is tried first, then the others.
     */
    fun addToHome(key: String, visual: List<HomePage>, preferredPage: Int, capacityOf: (PageLayout) -> Int, preserve: Set<String> = emptySet()): Boolean {
        if (!HomeLayoutMath.hasRoom(visual, capacityOf)) return false
        val cur = _layout.value
        write(
            cur.copy(
                pages = HomeLayoutMath.toStored(HomeLayoutMath.add(visual, key, preferredPage, capacityOf), cur.pages, preserve),
                removed = cur.removed - key,
                seeded = true,
            ),
        )
        markExplicit()
        return true
    }

    // Wave 30 (pagesExplicit): the markers that keep the one-time legacy-layout migration from ever touching a layout the user edited.
    /** True once the user added, removed or moved something on the pages (every edit path calls [markExplicit]). */
    fun explicitEdit(): Boolean = sp.getBoolean("explicitEdit", false)
    fun markExplicit() { if (!explicitEdit()) sp.edit().putBoolean("explicitEdit", true).apply() }
    fun pagesExplicitVersion(): Int = sp.getInt("pagesExplicitVersion", 0)
    fun markPagesExplicitDone() { sp.edit().putInt("pagesExplicitVersion", PagesExplicit.VERSION).apply() }
    /** The one-time "Home pages were reset" notice has not been dealt with yet. */
    fun noticePending(): Boolean = sp.getBoolean("resetNoticePending", false)
    fun setNoticePending(v: Boolean) { sp.edit().putBoolean("resetNoticePending", v).apply() }

    /** Forgets "removed from home" for apps no longer installed, so a reinstall lands on home again. */
    fun pruneRemoved(installed: Set<String>) {
        val cur = _layout.value
        val kept = cur.removed.filterTo(HashSet()) { it in installed }
        if (kept.size != cur.removed.size) write(cur.copy(removed = kept))
    }

    /** Replaces the whole arrangement (import or snapshot restore, CFG-02 / CFG-04). */
    fun replace(next: HomeLayout) = write(next)

    private fun write(next: HomeLayout) {
        _layout.value = next
        // NFR-18: widget ids in the keys go to the excluded device file, the backed-up file holds re-add markers.
        WidgetIdSplit.putString(sp, device, "home_layout", "pages", HomeLayoutCodec.encode(next.pages))
        WidgetIdSplit.putStringSet(sp, device, "home_layout", "removed", next.removed)
        sp.edit().putBoolean("seeded", next.seeded).apply()
    }

    private fun read(): HomeLayout = HomeLayout(
        pages = HomeLayoutMath.normalizeIds(HomeLayoutCodec.decode(WidgetIdSplit.getString(sp, device, "home_layout", "pages"))),
        removed = WidgetIdSplit.getStringSet(sp, device, "home_layout", "removed"),
        seeded = sp.getBoolean("seeded", false),
    )
}

/** JSON codec for the page list: `[{"k":[keys],"s":"Grid","p":[c,r],"l":[c,r],"r":2,"id":1}, …]`. */
object HomeLayoutCodec {
    fun encode(pages: List<HomePage>): String = JSONArray().apply {
        pages.forEach { page ->
            put(
                JSONObject()
                    .put("k", JSONArray(page.keys))
                    .put("s", page.layout.shape.name)
                    .put("r", page.layout.rings)
                    .put("id", page.id)
                    .apply {
                        page.layout.portrait?.let { put("p", JSONArray(listOf(it.columns, it.rows))) }
                        page.layout.landscape?.let { put("l", JSONArray(listOf(it.columns, it.rows))) }
                    },
            )
        }
    }.toString()

    fun decode(raw: String?): List<HomePage> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val k = o.getJSONArray("k")
            fun spec(name: String) = o.optJSONArray(name)?.let { GridSpec(it.getInt(0), it.getInt(1)).coerced() }
            HomePage(
                keys = List(k.length()) { k.getString(it) },
                layout = PageLayout(
                    shape = enumOr(o.optString("s"), PageShape.Grid),
                    portrait = spec("p"),
                    landscape = spec("l"),
                    rings = o.optInt("r", 2).coerceIn(PageLayout.MIN_RINGS, PageLayout.MAX_RINGS),
                ),
                id = o.optLong("id", 0L),
            )
        }
    }.getOrDefault(emptyList())
}

/** Corner slots (by physical corner) and gesture bindings. */
class ShortcutStore internal constructor(private val sp: SharedPreferences, private val device: SharedPreferences? = null) {
    private val _corners = MutableStateFlow(run { DeviceBound.migrateSlots(sp, device); readCorners() })
    val corners: StateFlow<Map<Corner, CornerSlot>> = _corners.asStateFlow()

    private val _gestures = MutableStateFlow(readGestures())
    val gestures: StateFlow<Map<HomeGesture, CornerItem>> = _gestures.asStateFlow()

    fun setCorner(corner: Corner, slot: CornerSlot) {
        if (slot == CornerSlot.Empty && _corners.value[corner].let { it != null && it != CornerSlot.Empty }) DestructiveEdits.before("clear corner")
        val next = _corners.value + (corner to slot)
        _corners.value = next
        DeviceBound.putSlot(sp, device, "corner.${corner.name}", slot)
    }

    /** Landscape-only assignments (INP-15), used when the prefs ask for a separate set per orientation. Unset corners follow portrait. */
    private val _landscapeCorners = MutableStateFlow(readLandscapeCorners())
    val landscapeCorners: StateFlow<Map<Corner, CornerSlot>> = _landscapeCorners.asStateFlow()

    fun setLandscapeCorner(corner: Corner, slot: CornerSlot) {
        _landscapeCorners.value = _landscapeCorners.value + (corner to slot)
        DeviceBound.putSlot(sp, device, "lcorner.${corner.name}", slot)
    }

    /** Only the landscape slots the user set (the [landscapeCorners] flow also holds portrait fallbacks); this is what a state export carries. */
    fun explicitLandscapeCorners(): Map<Corner, CornerSlot> =
        Corner.entries.mapNotNull { c -> DeviceBound.getSlot(sp, device, "lcorner.${c.name}")?.let { c to it } }.toMap()

    private fun readLandscapeCorners(): Map<Corner, CornerSlot> = Corner.entries.associateWith { c ->
        DeviceBound.getSlot(sp, device, "lcorner.${c.name}") ?: readCorners()[c] ?: CornerSlot.Empty
    }

    fun setGesture(gesture: HomeGesture, item: CornerItem?) {
        if (item == null && gesture in _gestures.value) DestructiveEdits.before("clear gesture")
        val next = if (item == null) _gestures.value - gesture else _gestures.value + (gesture to item)
        _gestures.value = next
        // An explicit "none" is stored as an empty string so it survives over the defaults.
        sp.edit().putString("gesture.${gesture.name}", item?.let(ShortcutCodec::encodeItem) ?: "").apply()
    }

    private fun readCorners(): Map<Corner, CornerSlot> = Corner.entries.associateWith { c ->
        DeviceBound.getSlot(sp, device, "corner.${c.name}")
            ?: DefaultCorners[c] ?: CornerSlot.Empty
    }

    private fun readGestures(): Map<HomeGesture, CornerItem> = buildMap {
        HomeGesture.entries.forEach { g ->
            val raw = sp.getString("gesture.${g.name}", null)
            val item = if (raw == null) DefaultGestures[g] else ShortcutCodec.decodeItem(raw)
            if (item != null) put(g, item)
        }
    }
}

/** String codec for corner items / slots (JSON via the platform's org.json). */
object ShortcutCodec {
    fun encodeItem(item: CornerItem): String = when (item) {
        is CornerItem.Action -> "action:${item.id.name}"
        is CornerItem.App -> "app:${item.key}"
    }

    fun decodeItem(raw: String): CornerItem? = when {
        raw.startsWith("action:") ->
            runCatching { CornerActionId.valueOf(raw.removePrefix("action:")) }.getOrNull()?.let(CornerItem::Action)
        raw.startsWith("app:") -> raw.removePrefix("app:").takeIf { it.isNotEmpty() }?.let(CornerItem::App)
        else -> null
    }

    fun encodeSlot(slot: CornerSlot): String = when (slot) {
        CornerSlot.Empty -> JSONObject().put("t", "empty").toString()
        is CornerSlot.Single -> JSONObject().put("t", "single").put("i", encodeItem(slot.item)).toString()
        is CornerSlot.Palette -> JSONObject()
            .put("t", "palette")
            .put("n", slot.name)
            .put("i", JSONArray(slot.items.map(::encodeItem)))
            .toString()
        is CornerSlot.Complication -> JSONObject().put("t", "complication").put("k", slot.kind.name).toString()
        is CornerSlot.ComplicationCluster -> JSONObject()
            .put("t", "cluster")
            .put("k", JSONArray(slot.kinds.map { it.name }))
            .toString()
        is CornerSlot.Widget -> JSONObject().put("t", "widget").put("id", slot.appWidgetId).put("n", slot.label).toString()
    }

    fun decodeSlot(raw: String): CornerSlot? = runCatching {
        val o = JSONObject(raw)
        when (o.getString("t")) {
            "empty" -> CornerSlot.Empty
            "single" -> decodeItem(o.getString("i"))?.let(CornerSlot::Single)
            "palette" -> {
                val arr = o.getJSONArray("i")
                CornerSlot.Palette(
                    name = o.optString("n", "Palette"),
                    items = List(arr.length()) { arr.getString(it) }.mapNotNull(::decodeItem)
                        .take(CornerSlot.MAX_PALETTE_ITEMS),
                )
            }
            "complication" -> kindOf(o.getString("k"))?.let(CornerSlot::Complication)
            "cluster" -> {
                val arr = o.getJSONArray("k")
                CornerSlot.ComplicationCluster(
                    List(arr.length()) { arr.getString(it) }.mapNotNull(::kindOf).take(CornerSlot.MAX_CLUSTER),
                )
            }
            "widget" -> CornerSlot.Widget(o.getInt("id"), o.optString("n", "Widget"))
            else -> null
        }
    }.getOrNull()

    private fun kindOf(name: String): ComplicationKind? = ComplicationKind.entries.firstOrNull { it.name == name }
}

/** Bounded log of launches (recents and history). */
class LaunchHistoryStore internal constructor(private val sp: SharedPreferences) {
    /** HOM-18: called with every recorded launch so a larger log can keep more than [MAX_EVENTS]. */
    var mirror: ((LaunchEvent) -> Unit)? = null
    private val _events = MutableStateFlow(read())
    val events: StateFlow<List<LaunchEvent>> = _events.asStateFlow()

    fun record(key: String, nowMillis: Long = System.currentTimeMillis()) {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        val event = LaunchEvent(key, nowMillis, hour)
        val next = (_events.value + event).takeLast(MAX_EVENTS)
        _events.value = next
        mirror?.invoke(event)
        val arr = JSONArray()
        next.forEach { arr.put(JSONArray().put(it.key).put(it.timeMillis).put(it.hourOfDay)) }
        sp.edit().putString("events", arr.toString()).apply()
    }

    private fun read(): List<LaunchEvent> = runCatching {
        val arr = JSONArray(sp.getString("events", "[]"))
        List(arr.length()) { i ->
            val e = arr.getJSONArray(i)
            LaunchEvent(e.getString(0), e.getLong(1), e.getInt(2))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_EVENTS = 400
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback


/** Per-app icon tint overrides (ARGB), keyed by [AppEntry.key]. */
class IconColorStore internal constructor(private val sp: SharedPreferences) {
    private val _colors = MutableStateFlow(sp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap())
    val colors: StateFlow<Map<String, Long>> = _colors.asStateFlow()

    fun set(key: String, argb: Long?) {
        _colors.value = if (argb == null) _colors.value - key else _colors.value + (key to argb)
        sp.edit().apply { if (argb == null) remove(key) else putLong(key, argb) }.apply()
    }
}

/** Launch statistics derived from the history log, for drawer sorting. */
object UsageStats {
    data class Usage(val count: Int, val lastMillis: Long)

    fun of(events: List<LaunchEvent>): Map<String, Usage> {
        val out = HashMap<String, Usage>()
        for (e in events) {
            val cur = out[e.key]
            out[e.key] = Usage((cur?.count ?: 0) + 1, maxOf(cur?.lastMillis ?: 0L, e.timeMillis))
        }
        return out
    }
}
