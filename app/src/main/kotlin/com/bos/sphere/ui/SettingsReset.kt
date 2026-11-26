package com.bos.sphere.ui

import android.content.Context
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PrefsReset
import com.bos.sphere.core.data.StateService
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingsSnapshots

/**
 * Wave 56 (settingsCore), CFG-30: which preferences "Reset this page to defaults" and "Reset all settings" put back.
 *
 * Only preferences, only those of [LauncherPrefs] (see [PrefsReset]). **User data is never reset from here**: the home layout and its pages,
 * the hidden apps, the corner and gesture assignments (shortcuts), the Comms groups and rules, the workspaces, themes and the dock's own
 * stores are not touched by any page reset; they have their own screens (delete a page, unhide, Hide rules ...). So the Home tab puts back
 * the corner style, size and behaviour but keeps what each corner does, and the motion settings, not the gesture bindings.
 *
 * The per-page lists below are written out, and a test derives the fields each page's rows really change (it calls every row's setter against a
 * recording model) and requires them to be inside the page's list; a second test requires every field of [LauncherPrefs] to be in a page list or in
 * [EXCLUDED] with its reason, so a preference added later cannot be forgotten.
 */
internal object SettingsReset {
    /**
     * English tab key to the [LauncherPrefs] fields its "Reset this page to defaults" puts back (wave 57: a page is one of the six tabs; the
     * wallpaper moved to Look, the keyboard-with-Radar rows to Radar, the right side and the recents panel to Side panel, Restore open screens to System).
     */
    val PAGES: Map<String, Set<String>> = mapOf(
        "Home" to setOf(
            "glanceEnabled", "homeOrder", "showMusic", "glanceHotspots", "glanceControlsSeconds", "glanceIndicator",
            "clockStyle", "clockFormat", "glanceClockDate", "glanceClockAccent", "glanceClockSize",
            "homeStyle", "homePages", "scrollAxis", "continuousColumns", "adaptiveGrid", "gridDensity", "portraitGrid", "landscapeGrid",
            "iconScale", "iconSpacing", "spacingV", "gridBias", "gridInsetX", "gridInsetTop", "gridInsetBottom", "labelScale", "showLabels",
            "badgesOn", "badgeCount", "portraitPaging", "landscapePaging", "pageTransition",
            "alwaysKeyboard", "hideStatusBar",
            "cornerStyle", "cornerIconSize", "cornerIconMode", "cornersFollowDevice", "cornersPerOrientation", "hotCornersOn", "hotCornerDwellMs",
            "motionEnabled", "motionInBackground", "motionSensitivity",
        ),
        "Look" to setOf(
            "themeMode", "accentColor", "uiFont", "iconShape", "iconTint", "iconPlate", "settingsBackdrop", "settingsDim",
            "calmStyle", "calmAlsoRegular", "indicatorMetric", "indicatorIntensity", "indicatorSpeed", "indicatorLow", "indicatorMid", "indicatorHigh", "indicatorIdle",
            "glanceWallpaperKind", "glanceSolid", "glanceGradFrom", "glanceGradTo", "glanceGradAngle", "backgroundSlot1", "backgroundSlot2", "glanceSystemDim",
            "pagesSameAsGlance", "pagesWallpaperKind", "pagesSolid", "pagesGradFrom", "pagesGradTo", "pagesGradAngle", "pagesSlot1", "pagesSlot2", "pagesSystemDim",
        ),
        "Apps" to setOf("wheelGrouping", "wheelWorkOnly", "pageMode"),
        "Radar" to setOf(
            "searchLayout", "searchLayoutPinned", "radarBackground", "radarIntensity", "searchMotion", "webEngine", "searchWeb", "webSuggestions",
            "searchActions", "searchFiles", "searchDeep", "searchContacts", "keyboardOnOpenPortrait", "keyboardOnOpenLandscape",
        ),
        "Side panel" to setOf("leftPage", "launchpadRail", "launchpadTheme", "rightSide", "recentsLimit", "recentsUseUsageAccess"),
        "System" to setOf("uiSounds", "uiSoundVolume", "language", "restoreOpenSurfaces"),
    )

    /** [LauncherPrefs] fields no page puts back, each with the reason. A reset never touches them, "Reset all settings" included. */
    val EXCLUDED: Map<String, String> = mapOf(
        "onboardingDone" to "first-run flag: resetting it would replay the introduction",
        "leftWidgetId" to "device-bound: an app widget id of this device",
        "glancePicture" to "device-bound: the user's own picture file",
        "pagesPicture" to "device-bound: the user's own picture file",
        "homePage" to "state, not a setting: the page the Home button returns to",
        "glanceClockX" to "the user's placement of the clock (edit mode), part of the home layout",
        "glanceClockY" to "the user's placement of the clock (edit mode), part of the home layout",
        "glanceClockXLand" to "the user's placement of the clock (edit mode), part of the home layout",
        "glanceClockYLand" to "the user's placement of the clock (edit mode), part of the home layout",
        "clockTileAnalog" to "options of one clock tile, set on its item sheet: part of the home layout",
        "clockTileFormat" to "options of one clock tile, set on its item sheet: part of the home layout",
        "clockTileDate" to "options of one clock tile, set on its item sheet: part of the home layout",
        "radarSignalForce" to "Lab override for testing, not a setting",
    )

    /** The page resets by English tab key (every one of the six tabs has one). */
    fun fieldsFor(categoryKey: String?): Set<String>? = categoryKey?.let { PAGES[it] }

    /** Every field "Reset all settings" puts back: the union of the pages. */
    val ALL: Set<String> by lazy { PAGES.values.flatten().toSet() }

    /** The fields of [LauncherPrefs] that no page lists and that are not excluded: must be empty (a test). */
    fun unaccounted(): Set<String> = PrefsReset.names.toSet() - ALL - EXCLUDED.keys

    /** [categories] with each page's reset action filled in; [update] writes through the prefs store. */
    fun withResets(categories: List<SettingCategory>, update: ((LauncherPrefs) -> LauncherPrefs) -> Unit): List<SettingCategory> =
        categories.map { c ->
            val fields = fieldsFor(OneUiModel.key(c.title))
            if (fields == null) c else c.copy(reset = { update { PrefsReset.reset(it, fields) } })
        }

    /** The action behind "Reset all settings" in Backup. */
    fun resetAll(update: ((LauncherPrefs) -> LauncherPrefs) -> Unit) { update { PrefsReset.reset(it, ALL) } }
}

/** The snapshots a reset takes first, on the app's state service: the same files the Backup page lists and restores. */
class StateSnapshots(context: Context) : SettingsSnapshots {
    private val app = context.applicationContext
    override fun take(reason: String): String? = runCatching { StateService.get(app).snapshot(reason, force = true)?.id }.getOrNull()
    override fun restore(id: String): Boolean = runCatching {
        StateService.get(app).restore(id)
        reloadAfterStateChange(app)
    }.isSuccess
}
