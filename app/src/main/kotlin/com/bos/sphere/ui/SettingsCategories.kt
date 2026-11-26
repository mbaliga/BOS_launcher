package com.bos.sphere.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.CornerIconMode
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.CalmStyles
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.data.UiLanguage
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ColorDot
import com.bos.sphere.feature.grid.ColorPicker
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DensityPreset
import com.bos.sphere.core.data.GestureKind
import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PagingAxis
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.grid.ChipRow
import com.bos.sphere.feature.grid.OneUiVisualChoice
import com.bos.sphere.feature.grid.SettingsArtHome
import com.bos.sphere.feature.grid.SettingsArtLook
import com.bos.sphere.feature.grid.asCards
import com.bos.sphere.feature.grid.DensityPreview
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.LabeledSlider
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.disabled
import com.bos.sphere.feature.grid.disabledUnless
import com.bos.sphere.feature.grid.FinishSetup
import com.bos.sphere.feature.grid.SetupState
import com.bos.sphere.feature.grid.SetupStep
import com.bos.sphere.feature.grid.snapshotFirst
import com.bos.sphere.feature.grid.paneEntries
import com.bos.sphere.feature.grid.PaneEntry
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.Stepper
import com.bos.sphere.feature.grid.ToggleRow
import com.bos.sphere.feature.grid.itemLabel
import com.bos.sphere.feature.grid.slotSummary
import com.bos.sphere.feature.grid.keywords
import com.bos.sphere.feature.grid.previewTopic
import com.bos.sphere.core.data.isPaged
import com.bos.sphere.core.data.surface
import kotlin.math.roundToInt

/** Everything the settings space reads and the callbacks it writes through. */
class SettingsModel(
    val prefs: LauncherPrefs,
    val updatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    val pageIndex: Int,
    val pages: List<HomePage>,
    val setPageLayout: (Int, PageLayout) -> Unit,
    val addPageAfter: (Int) -> Unit,
    val deletePage: (Int) -> Unit,
    val setClockTile: (onPage: Int, present: Boolean) -> Unit,
    val corners: Map<Corner, CornerSlot>,
    val gestures: Map<HomeGesture, CornerItem>,
    val setGesture: (HomeGesture, CornerItem?) -> Unit,
    val about: List<Pair<String, String>>,
    val appsByKey: Map<String, AppEntry>,
    val isLandscape: Boolean,
    val chosenApp: (CornerActionId) -> String?,
    val pickLeftWidget: () -> Unit,
    val leftWidgetLabel: String?,
    val pickAppFor: (CornerActionId) -> Unit,
    val motionAvailable: Boolean,
    val editCorner: (Corner) -> Unit,
    val editGesture: (HomeGesture) -> Unit,
    val system: SystemLinks,
    /** CFG-08: every capability with its state on this device. */
    val capabilities: List<com.bos.sphere.core.capabilities.Capability> = emptyList(),
    val copyText: (String) -> Unit = {},
    /** CFG-17: the Lab spikes, each with its last result. */
    val lab: List<LabEntry> = emptyList(),
    /** Wave 26: "Add page" (wave 28: Paginated only, never asks anything) and "Edit home" (turns Pages on first when it is Off). */
    val addHomePage: () -> Unit = {},
    val editHomePages: () -> Unit = {},
    /** Wave 34: "Widgets, folders and icons" (the item picker), moved here from the long-press Home sheet. */
    val addHomeItems: () -> Unit = {},
    /** Wave 30: "Restore previous pages" while the backup of an auto-filled home is still kept (null otherwise). */
    val restorePages: (() -> Unit)? = null,
    /** Wave 29: bring Comms up (on a hub view and/or with the Groups and rules manager open), closing settings; the app wires it to the Comms surface. */
    val openComms: (com.bos.sphere.ui.HubView?, Boolean) -> Unit = { v, g -> com.bos.sphere.ui.LaunchpadHubNav.openComms(v, g) },
    /** Wave 38: the Hidden apps page (null = none, as in tests that do not build it). */
    val hiddenApps: HiddenAppsAccess? = null,
    /** Wave 38 (drawerRing): the shortcut ring's size (6 to 8) and labels toggle, kept in the puck store rather than the prefs, with their setters. */
    val puckSegments: Int = com.bos.sphere.core.data.PuckActions.DEFAULT_SEGMENTS,
    val setPuckSegments: (Int) -> Unit = {},
    val puckLabels: Boolean = false,
    val setPuckLabels: (Boolean) -> Unit = {},
    /** Wave 56 (CFG-32): what is still unfinished (default home, notification and usage access, Do Not Disturb access); [SetupState.Complete] = no Finish setup row. */
    val setup: SetupState = SetupState.Complete,
    /** Wave 57 (CFG-44): the page list of Home > Pages: which page the per-page rows below it edit (the host keeps the index, [pageIndex]). */
    val selectPage: (Int) -> Unit = {},
    /** Wave 57 (CFG-43): the launcher's own vibration strength, the same store as the Comms Sound and vibration sheet. */
    val haptic: com.bos.sphere.core.data.HapticStrength = com.bos.sphere.core.data.LauncherHapticSettings.strength.value,
    val setHaptic: (com.bos.sphere.core.data.HapticStrength) -> Unit = { com.bos.sphere.core.data.LauncherHapticSettings.set(it) },
    /** Wave 58 (CFG-62): the pinch bindings "Pinch changes grid density" replaced, kept so turning it off restores them (the app keeps them in the shortcuts store). */
    val savedPinch: () -> Map<HomeGesture, CornerItem?>? = { null },
    val savePinch: (Map<HomeGesture, CornerItem?>?) -> Unit = {},
    /** Wave 58 (CFG-67): the apps offering a Radar search provider (package to label), so a search for one opens Search providers on its row. */
    val searchProviders: List<Pair<String, String>> = emptyList(),
    /** Wave 59 (glanceTop): both corner sets (the Hotspots editor shows the orientation in view; null = [corners] for both), the display rotation, and an
     *  edit of a corner in a given set (null = [editCorner], the set in use). */
    val portraitCorners: Map<Corner, CornerSlot>? = null,
    val landscapeCorners: Map<Corner, CornerSlot>? = null,
    val rotation: Int = 0,
    val editCornerIn: ((Corner, Boolean) -> Unit)? = null,
    /** Wave 59: the home's live picture (its fixed wallpaper is the editor's backdrop); null = a plain backdrop. */
    val homeCapture: com.bos.sphere.feature.grid.HomeCapture? = null,
)

/**
 * Wave 59 (glanceTop, CFG-77): the Hotspots editor as a row of Home > Corners: the live miniature of the home (a tap turns a corner on or off, a long
 * press or Edit opens the corner editor), the compact row for the selected corner below it. Its value is how many are on.
 */
internal fun hotspotsEditorItem(m: SettingsModel): SettingItem.Choice<String> {
    val p = m.prefs
    val summary = com.bos.sphere.feature.grid.HotspotsEditorModel.summary(p.glanceHotspots)
    return SettingItem.Choice(
        x("Hotspots"), Glyphs.Dial, listOf(summary), summary, { it },
        preview = {
            com.bos.sphere.feature.grid.HotspotsEditorFor(
                prefs = p, update = m.updatePrefs, portraitSlots = m.portraitCorners ?: m.corners, landscapeSlots = m.landscapeCorners ?: m.corners,
                deviceLandscape = m.isLandscape, deviceRotation = m.rotation, appsByKey = m.appsByKey,
                onEdit = { c, land -> m.editCornerIn?.invoke(c, land) ?: m.editCorner(c) }, capture = m.homeCapture,
            )
        },
    ) {}.keywords(x("Corners"), x("Top left"), x("Top right"), x("Bottom left"), x("Bottom right"))
}

/** One row of the Lab page: a spike or live readout, its latest result text, and how to run it. */
class LabEntry(val id: String, val title: String, val result: String, val run: (() -> Unit)?)

class SystemLinks(
    val setDefaultHome: () -> Unit,
    val notificationAccess: () -> Unit,
    val dndAccess: () -> Unit,
    val androidSettings: () -> Unit,
    /** Wave 56 (CFG-32): the usage access page, behind the same explainer the Recents panel uses. */
    val usageAccess: () -> Unit = {},
)

/**
 * Where Settings opens (wave 57: a tab and a row of it, see [settingsTarget]). [CATEGORY_THIS_PAGE]: Home > Pages on the page that was long-pressed;
 * [CATEGORY_HOME]: the Home tab ("Home settings" from a corner, a gesture or the home's long press); [CATEGORY_APP_DRAWER]: Apps > App drawer,
 * on Drawer shortcuts; [CATEGORY_WALLPAPER]: Look > Wallpaper; [CATEGORY_ROOT]: the tab shown last (the Home sheet's Settings row);
 * [CATEGORY_INDEX_BASE] + n: tab n exactly (a Radar settings result; its row comes through [com.bos.sphere.feature.grid.SettingsFocus]).
 */
const val CATEGORY_THIS_PAGE = 0
const val CATEGORY_HOME = 1
/** Wave 30: the App drawer (where the Drawer shortcuts editor lives); the disc's empty sections open it. */
const val CATEGORY_APP_DRAWER = 900
/** Wave 57: Look > Wallpaper (the Home sheet's Wallpaper row). */
const val CATEGORY_WALLPAPER = 901
/** Wave 40: Settings home; wave 57: on the tab shown last. */
const val CATEGORY_ROOT = -1

/** Wave 57: a tab (a category index, or -1 for the remembered one) and the row of it to scroll to and flash (-1 = none). */
data class SettingsTarget(val category: Int, val row: Int = -1)

/** The rows the deep links land on, by row id (the ids the tab placement gives, see [SettingsTabLayout.place]). */
internal object SettingsLinks {
    const val HOME = "home.glance"
    const val THIS_PAGE = "Pages/Layout"
    const val APP_DRAWER = "Apps/Drawer shortcuts"
    const val WALLPAPER = "home.glanceWallpaper"
}

/** The tab and row [which] (one of the CATEGORY_ constants) opens in [categories] as built for the active home. */
fun settingsTarget(categories: List<SettingCategory>, which: Int): SettingsTarget {
    fun find(id: String, fallbackTab: String): SettingsTarget {
        categories.forEachIndexed { ci, c -> val r = c.items.indexOfFirst { it.meta.id == id }; if (r >= 0) return SettingsTarget(ci, r) }
        return SettingsTarget(categories.indexOfFirst { com.bos.sphere.feature.grid.SettingsTabs.key(it.title) == fallbackTab }.coerceAtLeast(0))
    }
    val tabs = com.bos.sphere.feature.grid.SettingsTabs
    return when (which) {
        CATEGORY_ROOT -> SettingsTarget(-1)
        CATEGORY_HOME -> find(SettingsLinks.HOME, tabs.HOME)
        // No page list (a scrolling home): the Pages section of Home all the same, on its first row.
        CATEGORY_THIS_PAGE -> find(SettingsLinks.THIS_PAGE, tabs.HOME).let { if (it.row >= 0) it else find("Home/Pages", tabs.HOME) }
        CATEGORY_APP_DRAWER -> find(SettingsLinks.APP_DRAWER, tabs.APPS)
        CATEGORY_WALLPAPER -> find(SettingsLinks.WALLPAPER, tabs.LOOK)
        in CATEGORY_INDEX_BASE until CATEGORY_INDEX_BASE + 1000 -> SettingsTarget((which - CATEGORY_INDEX_BASE).coerceIn(0, (categories.size - 1).coerceAtLeast(0)))
        else -> SettingsTarget(which.coerceIn(0, (categories.size - 1).coerceAtLeast(0)))
    }
}

/** The tab index of [which]; kept for the callers that only need the tab. */
fun settingsCategoryIndex(categories: List<SettingCategory>, which: Int): Int = settingsTarget(categories, which).category

const val CATEGORY_INDEX_BASE = 1000

private fun homeStyleName(s: HomeStyle) = when (s) { HomeStyle.Grid -> "Pages"; HomeStyle.List -> "List"; HomeStyle.Psp -> "PSP"; HomeStyle.Dsi -> "DSi" }

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

private fun gridLabel(g: GridSpec) = "${g.columns} × ${g.rows}"

private fun x(english: String) = L10n.tr(english)
private fun x(english: String, vararg args: Any?) = L10n.tr(english, *args)

@Composable
private fun note(text: String) {
    Text(L10n.tr(text), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    ChipRow(options, selected, { L10n.tr(label(it)) }, onSelect)
}

@Composable
private fun Sub(text: String) {
    Text(L10n.tr(text), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** Wave 56 (CFG-31): why a row that only the paged home uses is off: the home style, or Pages is Continuous. Null while the home is paged. */
private fun pagedReason(p: LauncherPrefs): String? = when {
    p.isPaged -> null
    p.homeStyle != HomeStyle.Grid -> x("Needs the Pages home style")
    else -> x("Pages is Continuous")
}

/** Wave 56 (CFG-31): why "Add page" is off: the pages are Off or Continuous (it adds a page to the paginated pages). */
private fun paginatedReason(p: LauncherPrefs): String? = when {
    p.homeStyle != HomeStyle.Grid -> x("Needs the Pages home style")
    p.homePages == com.bos.sphere.core.data.HomePages.Paginated -> null
    p.homePages == com.bos.sphere.core.data.HomePages.Off -> x("Pages is Off")
    else -> x("Pages is Continuous")
}

/**
 * Wave 57 (settingsTabs): Settings as the six tabs (Home, Look, Apps, Radar, Side panel, System), each row placed by [SettingsTabLayout], then
 * the Finish setup page last while something is unfinished.
 */
fun buildSettingsCategories(m: SettingsModel): List<SettingCategory> {
    val ordered = SettingsReset.withResets(SettingsTabLayout.place(buildSettingsTopics(m)).tabs, m.updatePrefs)
    val finish = FinishSetup.category(m.setup) { step ->
        when (step) {
            SetupStep.DefaultHome -> m.system.setDefaultHome()
            SetupStep.Notifications -> m.system.notificationAccess()
            SetupStep.Usage -> m.system.usageAccess()
            SetupStep.DoNotDisturb -> m.system.dndAccess()
        }
    }
    return if (finish != null) ordered + finish else ordered
}

/** The rows as they are built, by topic ("Home", "Corners", "Theme" ...), with their ids, synonyms and slider defaults; [SettingsTabLayout] places them. */
internal fun buildSettingsTopics(m: SettingsModel): List<SettingCategory> {
    val p = m.prefs
    val out = ArrayList<SettingCategory>()
    fun upd(f: (LauncherPrefs) -> LauncherPrefs) = m.updatePrefs(f)
    // What the active home style can use. Items that only mean something on the paged grid (pages,
    // per-page layout, density, page turning, the suggested app …) are left out elsewhere rather
    // than shown doing nothing.
    val paged = p.isPaged
    val continuous = p.homeStyle == HomeStyle.Grid && !paged
    val styleName = x(homeStyleName(p.homeStyle))

    // --- This page (paged home only) -------------------------------------------------------
    if (paged) {
    val page = m.pages.getOrNull(m.pageIndex) ?: HomePage()
    val layout = page.layout
    val pageItems = buildList {
        // Wave 57 (CFG-44): the list of pages; picking one makes the rows below edit it (the long press on a page still opens here on that page).
        if (m.pages.size > 1) add(
            SettingItem.Choice(
                x("Page"), Glyphs.Widget, m.pages.indices.toList(), m.pageIndex.coerceIn(0, m.pages.size - 1), { x("Page %d", it + 1) },
                hint = x("Settings below change this page"),
            ) { i -> m.selectPage(i) }.asCards({ i -> SettingsArtApp.pageOf(i, m.pages.size) }, "cfg.pagePick", id = { "page$it" }),
        )
        add(
            SettingItem.Choice(
                x("Layout"), Glyphs.Apps, PageShape.entries, layout.shape, { x(it.label) },
                hint = null, // wave 58 (CFG-65): the cards show the three layouts
                preview = {
                    OneUiVisualChoice(PageShape.entries, layout.shape, { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) }, { x(it.label) }, { SettingsArtHome.pageShape.art(it) }, tag = "cfg.pageLayout")
                },
            ) { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) },
        )
        // Wave 56 (CFG-31): the rows of the other layouts stay on the page, dimmed, with the reason, instead of vanishing.
        val isGrid = layout.shape == PageShape.Grid
        val custom = layout.portrait != null || layout.landscape != null
        val pg = layout.portrait ?: p.portraitGrid
        val lg = layout.landscape ?: p.landscapeGrid
        add(
            SettingItem.Toggle(x("Density follows the home default"), Glyphs.Widget, !custom, x("Home default: %s · %s", gridLabel(p.portraitGrid), gridLabel(p.landscapeGrid))) { follow ->
                m.setPageLayout(m.pageIndex, if (follow) layout.copy(portrait = null, landscape = null) else layout.copy(portrait = pg, landscape = lg))
            }.disabledUnless(isGrid, x("Needs Grid layout")),
        )
        if (isGrid) {
            val noCustom = x("Follows the home default")
            add(SettingItem.Stepper(x("Columns · portrait"), Glyphs.Widget, pg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(columns = it), landscape = lg)) }.disabledUnless(custom, noCustom))
            add(SettingItem.Stepper(x("Rows · portrait"), Glyphs.Widget, pg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(rows = it), landscape = lg)) }.disabledUnless(custom, noCustom))
            add(SettingItem.Stepper(x("Columns · landscape"), Glyphs.Widget, lg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(columns = it), portrait = pg)) }.disabledUnless(custom, noCustom))
            add(SettingItem.Stepper(x("Rows · landscape"), Glyphs.Widget, lg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS, x("Extra apps move to the next page")) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(rows = it), portrait = pg)) }.disabledUnless(custom, noCustom))
        }
        add(
            SettingItem.Stepper(x("Rings"), Glyphs.Dial, layout.rings, PageLayout.MIN_RINGS..PageLayout.MAX_RINGS, x("%d apps fit", PageLayout.circleCapacity(layout.rings))) { m.setPageLayout(m.pageIndex, layout.copy(rings = it)) }
                .disabledUnless(layout.shape == PageShape.Circle, x("Needs Circle layout")),
        )
        add(SettingItem.Toggle(x("Clock tile on this page"), Glyphs.Clock, HomeTiles.CLOCK in page.keys, x("Moves it from another page")) { on -> m.setClockTile(m.pageIndex, on) })
        add(SettingItem.Action(x("Add a page after this"), Glyphs.Plus, "") { m.addPageAfter(m.pageIndex) })
        if (m.pages.size > 1 || page.keys.isNotEmpty()) add(SettingItem.Action(x("Delete this page"), Glyphs.Close, x("Its items stay in the drawer")) { m.deletePage(m.pageIndex) })
    }
    out += SettingCategory(x("Pages"), Glyphs.Widget, pageItems) // wave 52: "Pages", not "Page 1" (the row opens the settings of the page you are on)
    }

    // --- Home --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Home"),
        Glyphs.Apps,
        buildList {
            // Wave 26: no landing selector. The home is Glance (the calm screen) followed by the pages the user builds; Glance can be switched off.
            add(
                SettingItem.Toggle(
                    x("Calm home screen (Glance)"), Glyphs.Home, p.glanceEnabled,
                    if (p.glanceEnabled) null else x("Home opens on the first page"),
                ) { v -> upd { it.copy(glanceEnabled = v) } },
            )
            add(SettingItem.Action(x("Add page"), Glyphs.Plus, x("After the last page")) { m.addHomePage() }.disabled(paginatedReason(p)))
            add(SettingItem.Action(x("Edit home"), Glyphs.Apps, x("Arrange your pages")) { m.editHomePages() })
            add(SettingItem.Action(x("Widgets, folders and icons"), Glyphs.Plus, x("Add them to your pages")) { m.addHomeItems() })
            // Wave 30 (pagesExplicit): after the one-time reset of an auto-filled home, the old pages come back from here for 30 days.
            m.restorePages?.let { restore -> add(SettingItem.Action(x("Restore previous pages"), Glyphs.Home, x("Kept 30 days after the reset")) { restore() }) }
            add(
                SettingItem.Choice(
                    x("Order"), Glyphs.Swipe, com.bos.sphere.core.data.HomeOrder.entries, p.homeOrder, { x(it.label) },
                    hint = x("Mirrors Comms, Recents and columns"),
                ) { v -> upd { it.copy(homeOrder = v) } }.asCards({ SettingsArtHome.order.art(it) }, "cfg.order"),
            )
            // STR-14: what a swipe from the right edge does. The dock stays optional: "Dock rail" only reveals it, it never turns it on.
            add(
                SettingItem.Choice(
                    x("Right side"), Glyphs.Apps, com.bos.sphere.core.data.RightSide.entries, p.rightSide, { x(it.label) },
                                    ) { v -> upd { it.copy(rightSide = v) } }.asCards({ SettingsArtHome.rightSide.art(it) }, "cfg.rightSide"),
            )
            addAll(wallpaperSettings(p) { f -> upd(f) }) // HOM-16 (wave 26): Glance and Pages wallpaper, always listed
            run {
                add(SettingItem.Stepper(x("Recent apps shown"), Glyphs.Apps, p.recentsLimit, com.bos.sphere.core.data.HomeRecents.MIN_LIMIT..com.bos.sphere.core.data.HomeRecents.MAX_LIMIT, x("Newest first")) { v -> upd { it.copy(recentsLimit = v) } })
                add(SettingItem.Toggle(x("Include apps opened from elsewhere"), Glyphs.Clock, p.recentsUseUsageAccess, x("Needs usage access")) { v -> upd { it.copy(recentsUseUsageAccess = v) } })
            }
            addAll(glanceSettings(p) { f -> upd(f) })
            add(
                SettingItem.Choice(
                    x("Style"), Glyphs.Apps, HomeStyle.entries, p.homeStyle, { x(homeStyleName(it)) },
                    // Wave 58 (CFG-65): the cards show the styles; the line keeps only how to reach Settings from a home that has no empty space to hold.
                    hint = when (p.homeStyle) {
                        HomeStyle.Grid -> null
                        HomeStyle.List -> x("Long-press the margin for Settings")
                        HomeStyle.Psp -> x("Hold the clock band for Settings")
                        HomeStyle.Dsi -> x("Long-press empty space for Settings")
                    },
                    preview = {
                        OneUiVisualChoice(HomeStyle.entries, p.homeStyle, { s -> upd { it.copy(homeStyle = s) } }, { x(homeStyleName(it)) }, { SettingsArtHome.homeStyle.art(it) }, tag = "cfg.homeStyle")
                    },
                ) { s -> upd { it.copy(homeStyle = s) } },
            )
            add(
                SettingItem.Choice(
                    x("Pages"), Glyphs.Swipe, com.bos.sphere.core.data.HomePages.entries, p.homePages, { x(it.label) },
                    preview = {
                        OneUiVisualChoice(com.bos.sphere.core.data.HomePages.entries, p.homePages, { v -> upd { it.copy(homePages = v) } }, { x(it.label) }, { SettingsArtHome.pages.art(it) }, tag = "cfg.pages")
                    },
                ) { v -> upd { it.copy(homePages = v) } },
            )
            add(
                SettingItem.Choice(
                    x("Scroll direction"), Glyphs.Swipe, com.bos.sphere.core.data.ScrollAxis.entries, p.scrollAxis, { x(it.label) },
                    hint = x("Long side follows your grip"),
                ) { v -> upd { it.copy(scrollAxis = v) } }.asCards({ SettingsArtHome.scrollAxis.art(it) }, "cfg.scrollAxis").disabledUnless(p.homeStyle == HomeStyle.Dsi, x("Needs the DSi home style")),
            )
            add(SettingItem.Toggle(x("Grid follows window size"), Glyphs.Widget, p.adaptiveGrid, x("Columns and rows follow the window")) { v -> upd { it.copy(adaptiveGrid = v) } })
            add(SettingItem.Pane(x("Home panels"), Glyphs.Apps, x("Files, usage, timeline"), { com.bos.sphere.HomePanelSettings() })) // HOM-15, HOM-17, HOM-18
            add(SettingItem.Slider(x("Grid density"), Glyphs.Widget, p.gridDensity, 0.5f..2f, pct(p.gridDensity)) { v -> upd { it.copy(gridDensity = v) } }.disabledUnless(p.adaptiveGrid, x("Needs Grid follows window size")))
            run {
                val preset = DensityPreset.entries.firstOrNull { it.portrait == p.portraitGrid && it.landscape == p.landscapeGrid }
                val densityOptions: List<DensityPreset?> = if (preset == null) listOf<DensityPreset?>(null) + DensityPreset.entries else DensityPreset.entries
                add(
                    SettingItem.Choice(
                        x("Default density"), Glyphs.Widget, densityOptions, preset,
                        { if (it == null) x("Custom") + " ${gridLabel(p.portraitGrid)}" else x(it.label) },
                        hint = x("%s portrait · %s landscape", gridLabel(p.portraitGrid), gridLabel(p.landscapeGrid)),
                        preview = {
                            OneUiVisualChoice<DensityPreset?>(DensityPreset.entries, preset, { d -> if (d != null) upd { it.copy(portraitGrid = d.portrait, landscapeGrid = d.landscape) } }, { x(it?.label ?: "Custom") }, { SettingsArtHome.density.art(it ?: DensityPreset.Default) }, tag = "cfg.density")
                            DensityPreview(p.portraitGrid.columns, p.portraitGrid.rows, p.iconScale, p.iconSpacing, Modifier.padding(vertical = 8.dp).height(150.dp))
                            Sub("Portrait")
                            Stepper(L10n.tr("Columns"), p.portraitGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> upd { it.copy(portraitGrid = it.portraitGrid.copy(columns = v)) } }
                            Stepper(L10n.tr("Rows"), p.portraitGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> upd { it.copy(portraitGrid = it.portraitGrid.copy(rows = v)) } }
                            Sub("Landscape")
                            Stepper(L10n.tr("Columns"), p.landscapeGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> upd { it.copy(landscapeGrid = it.landscapeGrid.copy(columns = v)) } }
                            Stepper(L10n.tr("Rows"), p.landscapeGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> upd { it.copy(landscapeGrid = it.landscapeGrid.copy(rows = v)) } }
                            note("Pages with their own density keep it. After changing it you can choose to fill the empty space.")
                        },
                    ) { d -> if (d != null) upd { it.copy(portraitGrid = d.portrait, landscapeGrid = d.landscape) } }.disabled(pagedReason(p)),
                )
            }
            // Icon size: the paged / continuous grids, DSi tiles and List rows scale; the PSP bar is fixed.
            add(
                SettingItem.Slider(x("Icon size"), Glyphs.Apps, p.iconScale, LauncherPrefs.MIN_ICON_SCALE..LauncherPrefs.MAX_ICON_SCALE, pct(p.iconScale)) { v -> upd { it.copy(iconScale = v) } }
                    .disabledUnless(p.homeStyle != HomeStyle.Psp, x("Fixed on the PSP bar")),
            )
            if (paged) {
                add(SettingItem.Toggle(x("Same spacing both ways"), Glyphs.Widget, p.spacingV < 0f, null) { same -> upd { it.copy(spacingV = if (same) -1f else it.iconSpacing) } })
                add(SettingItem.Slider(x(if (p.spacingV < 0f) "Spacing" else "Spacing across"), Glyphs.Widget, p.iconSpacing, 0f..1f, pct(p.iconSpacing)) { v -> upd { it.copy(iconSpacing = v) } })
                // Wave 56 (CFG-29): while "Same spacing both ways" is on this row follows "Spacing" and reads Auto; moving it sets the vertical spacing by hand.
                val follows = p.spacingV < 0f
                add(SettingItem.Slider(x("Spacing down"), Glyphs.Widget, if (follows) p.iconSpacing else p.spacingV, 0f..1f, if (follows) x("Auto") else pct(p.spacingV), auto = follows) { v -> upd { it.copy(spacingV = v) } })
            } else {
                // The scrolling grids only have a gap between rows; the other homes have no icon spacing at all.
                add(SettingItem.Slider(x("Spacing"), Glyphs.Widget, p.iconSpacing, 0f..1f, pct(p.iconSpacing), x("Room between the rows")) { v -> upd { it.copy(iconSpacing = v) } }.disabledUnless(continuous || p.homeStyle == HomeStyle.Dsi, x("Needs Pages, Continuous or DSi")))
            }
            // Wave 56 (CFG-31): the page margins stay on the page of every home; only the scrolling homes lose the vertical ones, with the reason shown.
            add(SettingItem.Slider(x("Height in page"), Glyphs.Widget, p.gridBias, 0f..1f, pct(p.gridBias)) { v -> upd { it.copy(gridBias = v) } }.disabled(pagedReason(p)))
            add(SettingItem.Slider(x("Side margin"), Glyphs.Widget, p.gridInsetX, 0f..0.25f, pct(p.gridInsetX), if (paged) null else x("Room at the left and right")) { v -> upd { it.copy(gridInsetX = v) } })
            add(SettingItem.Slider(x("Top margin"), Glyphs.Widget, p.gridInsetTop, 0f..0.4f, pct(p.gridInsetTop), x("Room for the clock")) { v -> upd { it.copy(gridInsetTop = v) } }.disabled(pagedReason(p)))
            add(SettingItem.Slider(x("Bottom margin"), Glyphs.Widget, p.gridInsetBottom, 0f..0.4f, pct(p.gridInsetBottom), x("Room for your thumbs")) { v -> upd { it.copy(gridInsetBottom = v) } }.disabled(pagedReason(p)))
            add(SettingItem.Slider(x("Label size"), Glyphs.Message, p.labelScale, 0.7f..1.5f, pct(p.labelScale)) { v -> upd { it.copy(labelScale = v) } }.disabled(pagedReason(p)))
            add(SettingItem.Toggle(x("App labels"), Glyphs.Message, p.showLabels, if (p.homeStyle == HomeStyle.Psp) x("Off: only the focused app") else null) { v -> upd { it.copy(showLabels = v) } })
            add(SettingItem.Toggle(x("Notification badges"), Glyphs.Bell, p.badgesOn, x("Needs notification access")) { v -> upd { it.copy(badgesOn = v) } })
            add(SettingItem.Toggle(x("Count in badges"), Glyphs.Bell, p.badgeCount, x("Off shows a plain dot")) { v -> upd { it.copy(badgeCount = v) } }.disabledUnless(p.badgesOn, x("Notification badges are off")))
            run {
                add(
                    SettingItem.Choice(
                        x("Page turning · portrait"), Glyphs.Swipe, PagingAxis.entries, p.portraitPaging, { x(it.label) },
                        preview = { OneUiVisualChoice(PagingAxis.entries, p.portraitPaging, { a -> upd { it.copy(portraitPaging = a) } }, { x(it.label) }, { SettingsArtHome.paging.art(it) }, tag = "cfg.pagingPortrait") },
                    ) { a -> upd { it.copy(portraitPaging = a) } }.disabled(pagedReason(p)),
                )
                add(
                    SettingItem.Choice(
                        x("Page turning · landscape"), Glyphs.Swipe, PagingAxis.entries, p.landscapePaging, { x(it.label) },
                        preview = { OneUiVisualChoice(PagingAxis.entries, p.landscapePaging, { a -> upd { it.copy(landscapePaging = a) } }, { x(it.label) }, { SettingsArtHome.paging.art(it) }, tag = "cfg.pagingLandscape") },
                    ) { a -> upd { it.copy(landscapePaging = a) } }.disabled(pagedReason(p)),
                )
            }
            add(SettingItem.Toggle(x("Always-on keyboard"), Glyphs.Message, p.alwaysKeyboard, x("Type to find an app")) { v -> upd { it.copy(alwaysKeyboard = v) } })
            add(SettingItem.Toggle(x("Keyboard opens with Radar and drawer · portrait"), Glyphs.Message, p.keyboardOnOpenPortrait) { v -> upd { it.copy(keyboardOnOpenPortrait = v) } })
            add(SettingItem.Toggle(x("Keyboard opens with Radar and drawer · landscape"), Glyphs.Message, p.keyboardOnOpenLandscape) { v -> upd { it.copy(keyboardOnOpenLandscape = v) } })
            add(com.bos.sphere.feature.grid.keyboardShortcutsRow())
            add(SettingItem.Toggle(x("Hide status bar"), Glyphs.Up, p.hideStatusBar, x("Swipe down to peek")) { v -> upd { it.copy(hideStatusBar = v) } })
            add(SettingItem.Toggle(x("Restore open screens"), Glyphs.Up, p.restoreOpenSurfaces, if (p.restoreOpenSurfaces) x("Reopens the drawer or Radar") else x("Always starts on the home")) { v -> upd { it.copy(restoreOpenSurfaces = v) } })
        },
    )

    // --- Apps --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Apps"),
        Glyphs.Widget,
        buildList {
            // Wave 38: the white shortcut ring (segments, editor, labels) leads the App drawer, then the open-the-drawer settings, then what lives on the home screen.
            addAll(com.bos.sphere.ui.drawerRingItems(m))
            add(SettingItem.Choice(x("Wheel groups"), Glyphs.Dial, WheelGrouping.entries, p.wheelGrouping, { x(it.label) }, hint = null) { g -> upd { it.copy(wheelGrouping = g) } }.asCards({ SettingsArtHome.wheelGrouping.art(it) }, "cfg.wheelGroups"))
            // Only the paged home has pages to curate; the scrolling homes always show every app.
            add(SettingItem.Choice(x("Pages"), Glyphs.Swipe, PageMode.entries, p.pageMode, { x(it.label) }, hint = x("The rest stay in the drawer")) { v -> upd { it.copy(pageMode = v) } }.asCards({ SettingsArtHome.pageMode.art(it) }, "cfg.drawerPages").disabled(pagedReason(p)))
            add(
                SettingItem.Pane(x("Open with"), Glyphs.Apps, x("Music, Browser, Maps…")) {
                    listOf(
                        CornerActionId.Music, CornerActionId.Messages, CornerActionId.Browser, CornerActionId.Email, CornerActionId.Maps,
                        CornerActionId.Calendar, CornerActionId.Gallery, CornerActionId.Contacts, CornerActionId.Calculator, CornerActionId.Camera,
                    ).forEach { id ->
                        com.bos.sphere.feature.grid.ActionRow(
                            label = x(id.label), summary = m.chosenApp(id) ?: x("Ask each time"), selected = false, flashId = SettingsRowMeta.PaneIds.openWith(id), onClick = { m.pickAppFor(id) },
                        )
                    }
                    note("Pick any installed app — the list isn't limited to what the system suggests.")
                },
            )
        },
    )

    // --- Corners -----------------------------------------------------------------------------
    out += SettingCategory(
        x("Corners"),
        Glyphs.Palette,
        buildList {
            add(hotspotsEditorItem(m)) // wave 59 (glanceTop): the Hotspots editor is the first block of Home > Corners
            add(
                SettingItem.Choice(
                    x("Corner style"), Glyphs.Palette, CornerStyle.entries, p.cornerStyle, { x(it.label) },
                    preview = { OneUiVisualChoice(CornerStyle.entries, p.cornerStyle, { c -> upd { it.copy(cornerStyle = c) } }, { x(it.label) }, { SettingsArtHome.cornerStyle.art(it) }, tag = "cfg.cornerStyle") },
                ) { c -> upd { it.copy(cornerStyle = c) } },
            )
            add(SettingItem.Slider(x("Corner icon size"), Glyphs.Dial, p.cornerIconSize, LauncherPrefs.MIN_CORNER_ICON..LauncherPrefs.MAX_CORNER_ICON, "${p.cornerIconSize.roundToInt()}", step = 1f) { v -> upd { it.copy(cornerIconSize = v) } })
            add(SettingItem.Choice(x("Icon type"), Glyphs.Apps, CornerIconMode.entries, p.cornerIconMode, { x(it.label) }, hint = x("App icon shows the real app")) { c -> upd { it.copy(cornerIconMode = c) } }.asCards({ SettingsArtHome.cornerIcon.art(it) }, "cfg.cornerIcon"))
            add(SettingItem.Toggle(x("Follow device rotation"), Glyphs.Flip, p.cornersFollowDevice, x("Corners stay put when you rotate")) { v -> upd { it.copy(cornersFollowDevice = v) } })
            add(SettingItem.Toggle(x("Separate corners for landscape"), Glyphs.Flip, p.cornersPerOrientation, x("Edits apply to this orientation")) { v -> upd { it.copy(cornersPerOrientation = v) } })
            add(SettingItem.Toggle(x("Hot corners with a mouse"), Glyphs.Pinch, p.hotCornersOn, x("Rest the pointer, click to run")) { v -> upd { it.copy(hotCornersOn = v) } })
            add(SettingItem.Slider(x("Hot corner delay"), Glyphs.Dial, p.hotCornerDwellMs.toFloat(), 50f..600f, "${p.hotCornerDwellMs} ms", step = 10f) { v -> upd { it.copy(hotCornerDwellMs = v.roundToInt()) } }.disabledUnless(p.hotCornersOn, x("Hot corners are off")))
            if (p.hotCornersOn) add(hotCornerTryItem(p, m.updatePrefs)) // W56: Try it
        },
    )

    // --- Launchpad ---------------------------------------------------------------------------
    out += SettingCategory(
        x("Side panel"), // wave 52: the right-hand panel holds Comms and widgets, so the row is named by what it is; the sections inside keep their names
        Glyphs.Bell,
        buildList {
            val leftPick: (com.bos.sphere.core.data.LeftPage) -> Unit = { v ->
                if (v == com.bos.sphere.core.data.LeftPage.Widget && p.leftWidgetId < 0) m.pickLeftWidget() else upd { it.copy(leftPage = v) }
            }
            add(
                SettingItem.Choice(
                    x("Left page"), Glyphs.Bell, com.bos.sphere.core.data.LeftPage.entries, p.leftPage,
                    { if (it == com.bos.sphere.core.data.LeftPage.Widget) (m.leftWidgetLabel ?: x("A widget")) else x(it.label) },
                    preview = {
                        OneUiVisualChoice(com.bos.sphere.core.data.LeftPage.entries, p.leftPage, leftPick, { x(it.label) }, { SettingsArtHome.leftPage.art(it) }, tag = "cfg.leftPage")
                    },
                    onSelect = leftPick,
                ),
            )
            if (p.leftPage == com.bos.sphere.core.data.LeftPage.Widget || p.leftWidgetId >= 0) {
                add(SettingItem.Action(x("Choose widget"), Glyphs.Widget, m.leftWidgetLabel ?: x("None yet"), navigates = true, onClick = m.pickLeftWidget))
            }
            add(
                SettingItem.Choice(
                    x("Comms theme"), Glyphs.Moon, com.bos.sphere.core.data.LaunchpadTheme.entries, p.launchpadTheme, { x(it.label) },
                    preview = {
                        OneUiVisualChoice(com.bos.sphere.core.data.LaunchpadTheme.entries, p.launchpadTheme, { v -> upd { it.copy(launchpadTheme = v) } }, { x(it.label) }, { com.bos.sphere.ui.SettingsArtApp.commsTheme.art(it) }, tag = "cfg.commsTheme")
                    },
                ) { v -> upd { it.copy(launchpadTheme = v) } },
            )
            // Wave 28: the size of the Comms content, 75 to 125 percent in 5s (wave 29: the only place with the stepper, the Comms menu has no Options; Ctrl+Plus and Ctrl+Minus on the list step it too; the pinch filters read/unread).
            add(
                SettingItem.Stepper(
                    x("Comms size"), Glyphs.Pinch, com.bos.sphere.core.data.CommsScaleSettings.percent.value,
                    com.bos.sphere.core.data.CommsScale.MIN..com.bos.sphere.core.data.CommsScale.MAX,
                    hint = x("Ctrl+Plus and Ctrl+Minus too"),
                    step = com.bos.sphere.core.data.CommsScale.STEP, valueText = "${com.bos.sphere.core.data.CommsScaleSettings.percent.value}%",
                ) { v -> com.bos.sphere.core.data.CommsScaleSettings.set(v) },
            )
            // Wave 29: the options that used to be in the Comms menu panel. The same stores as before (CommsOptions), read live by the hub when Comms opens.
            val comms = com.bos.sphere.ui.CommsOptions
            add(
                SettingItem.Choice(
                    x("Default view"), Glyphs.Bell, comms.defaultChoices(), comms.defaultChoice(), { x(it.label) },
                    hint = x("What Comms opens on"),
                ) { v -> comms.setDefaultChoice(v) }.asCards({ com.bos.sphere.ui.SettingsArtApp.defaultView.art(it) }, "cfg.defaultView", id = { "${it.view.id}${it.category?.let { c -> "." + c.id } ?: ""}" }),
            )
            // Wave 53 (commsCategories): the pinned block (ongoing notifications and Pin-by-rule rows) at the top of Comms; stored with the exported Comms prefs.
            add(SettingItem.Toggle(x("Show pinned at the top"), Glyphs.Bell, com.bos.sphere.core.data.LaunchpadSettings.prefs.value.pinnedAtTop, x("Ongoing and pinned stay on top")) { v -> com.bos.sphere.core.data.LaunchpadSettings.setPinnedAtTop(v) })
            // Wave 52 (panelWidgets): the fifth section, off until chosen; its place in the tab row is a visual card choice.
            add(SettingItem.Toggle(x("Widgets section"), Glyphs.Bell, comms.widgetsSection.value, x("Real widgets inside this panel")) { v -> comms.setWidgetsSection(v) })
            add(
                SettingItem.Choice(
                    x("Widgets position"), Glyphs.Bell, com.bos.sphere.ui.WidgetsSlot.entries, comms.widgetsSlot.value, { x(it.label) },
                    preview = {
                        OneUiVisualChoice(com.bos.sphere.ui.WidgetsSlot.entries, comms.widgetsSlot.value, { v -> comms.setWidgetsSlot(v) }, { x(it.label) }, { com.bos.sphere.ui.SettingsArtApp.widgetsSlot.art(it) }, tag = "cfg.widgetsSlot")
                    },
                ) { v -> comms.setWidgetsSlot(v) }.disabledUnless(comms.widgetsSection.value, x("Widgets section is off")),
            )
            add(SettingItem.Toggle(x("Clear from the shade when dismissed in Comms"), Glyphs.Bell, comms.clearFromShade.value, x("Off: it stays in the shade")) { v -> comms.setClearFromShade(v) })
            add(SettingItem.Action(x("Read history"), Glyphs.Message, x("Open"), navigates = true, hint = x("Notifications already read")) { m.openComms(com.bos.sphere.ui.HubView.Read, false) })
            add(SettingItem.Action(x("Groups and rules"), Glyphs.Message, x("Open"), navigates = true, hint = x("The manager in Comms")) { m.openComms(null, true) })
            add(SettingItem.Toggle(x("Unread strip"), Glyphs.Bell, p.launchpadRail, x("Unread counts at the left edge")) { v -> upd { it.copy(launchpadRail = v) } })
            add(
                SettingItem.Pane(x("Comms gestures"), Glyphs.Pinch, x("Pinch, slide, long-press")) {
                    val bound by com.bos.sphere.core.data.LaunchpadSettings.gestures.collectAsState()
                    // Wave 28: a pinch sets the Comms size now, so the two pinch bindings are not offered (the stored values stay, unused).
                    com.bos.sphere.core.data.LaunchpadGesture.entries.filter { it != com.bos.sphere.core.data.LaunchpadGesture.PinchIn && it != com.bos.sphere.core.data.LaunchpadGesture.PinchOut }.forEach { g ->
                        Sub(g.label)
                        Text(L10n.tr(g.hint), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                        Chips(com.bos.sphere.core.data.LaunchpadGestures.allowed(g), bound[g] ?: com.bos.sphere.core.data.LaunchpadAction.None, { it.label }) { a ->
                            com.bos.sphere.core.data.LaunchpadSettings.setGesture(g, a)
                        }
                    }
                    com.bos.sphere.feature.grid.TextButtonPill(L10n.tr("Reset to defaults"), onClick = { com.bos.sphere.core.data.LaunchpadSettings.resetGestures() })
                },
            )
            add(SettingItem.Pane(x("Grouping & rules"), Glyphs.Message, x("Per app, sender, conversation")) { com.bos.sphere.ui.LaunchpadRulesPane() })
            add(SettingItem.Action(x("Mark all as read"), Glyphs.Bell, confirm = "Mark every notification, in the list and in the history, as read?", confirmLabel = "Mark all as read", hint = x("The list and the history")) { comms.markAllRead() })
            add(SettingItem.Action(x("Clear all notifications"), Glyphs.Close, confirm = "Clear all notifications from the list? The history stays.", confirmLabel = "Clear all", hint = x("The history stays")) { comms.clearAll() })
            add(SettingItem.Action(x("Delete history"), Glyphs.Close, confirm = "Delete all notification history? Notifications in the shade stay.", confirmLabel = "Delete history", hint = x("Notifications in the shade stay")) { comms.deleteHistory() })
        },
    )

    // --- Gestures ----------------------------------------------------------------------------
    // Touch gestures are the same on every home; what differs is which swipes the home itself
    // already scrolls with (and whether there is a grid density to pinch). Those are annotated.
    val surface = p.surface(m.isLandscape)
    val pinchDensity = (m.gestures[HomeGesture.PinchIn] as? CornerItem.Action)?.id == CornerActionId.GridDenser &&
        (m.gestures[HomeGesture.PinchOut] as? CornerItem.Action)?.id == CornerActionId.GridSparser
    out += SettingCategory(
        x("Gestures"),
        Glyphs.Pinch,
        buildList<SettingItem> {
            // Wave 58 (CFG-66): a home without a grid density keeps the row, dimmed, with the reason.
            add(
            SettingItem.Toggle(
                x("Pinch changes grid density"), Glyphs.Pinch, pinchDensity,
                if (paged) x("Pinch in packs more icons") else x("Pinch in adds a column"),
            ) { on ->
                // Wave 58 (CFG-62): turning it on remembers the two pinch bindings it replaces; turning it off puts them back (or the defaults, when none was remembered).
                val next = if (on) com.bos.sphere.core.data.PinchDensityToggle.on(m.gestures).also { m.savePinch(it.saved) } else com.bos.sphere.core.data.PinchDensityToggle.off(m.savedPinch()).also { m.savePinch(null) }
                next.bindings.forEach { (g, item) -> m.setGesture(g, item) }
            }.disabledUnless(surface.hasDensity, x("No grid density on %s", styleName)),
            )
            HomeGesture.entries.filter { it.kind == GestureKind.Touch }.forEach { g ->
                val bound = m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: x("Nothing")
                val why = g.unavailableReason(surface)
                add(SettingItem.Action(x(g.label), Glyphs.forGesture(g), if (why == null) bound else "$bound · ${x(gestureWhy(why))}", navigates = true) { m.editGesture(g) })
            }
            add(SettingItem.Action(x("On this home"), Glyphs.Info, x(gestureSummary(p.homeStyle, surface))) {})
        },
    )

    // --- Motion ------------------------------------------------------------------------------
    out += SettingCategory(
        x("Motion"),
        Glyphs.Shake,
        buildList {
            add(SettingItem.Toggle(x("Motion gestures"), Glyphs.Shake, p.motionEnabled, if (!m.motionAvailable) x("No motion sensor on this device") else null) { v -> upd { it.copy(motionEnabled = v) } })
            add(SettingItem.Toggle(x("When home isn't showing"), Glyphs.Bolt, p.motionInBackground) { v -> upd { it.copy(motionInBackground = v) } })
            add(SettingItem.Slider(x("Motion sensitivity"), Glyphs.Dial, p.motionSensitivity, 0f..1f, pct(p.motionSensitivity), x("Higher reacts to lighter shakes")) { v -> upd { it.copy(motionSensitivity = v) } })
            add(motionTryItem(p, m.updatePrefs)) // W56: Try it
            HomeGesture.entries.filter { it.kind == GestureKind.Motion }.forEach { g ->
                add(SettingItem.Action(x(g.label), Glyphs.forGesture(g), m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: x("Nothing"), navigates = true) { m.editGesture(g) })
            }
        },
    )

    // --- Theme -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Theme"),
        Glyphs.Palette,
        buildList {
            add(looksRailItem(p)) // W56 (looksPresets): whole looks in one tap
            add(
                SettingItem.Choice(
                    x("Light / dark"), Glyphs.Moon, ThemeMode.entries, p.themeMode, { x(it.label) },
                    preview = { OneUiVisualChoice(ThemeMode.entries, p.themeMode, { v -> upd { it.copy(themeMode = v) } }, { x(it.label) }, { SettingsArtLook.themeMode.art(it) }, tag = "cfg.themeMode") },
                ) { v -> upd { it.copy(themeMode = v) } },
            )
            add(SettingItem.Pane(x("Accent"), Glyphs.Palette, "") { ColorPicker(p.accentColor, { v -> upd { it.copy(accentColor = v) } }) })
            add(themesSettingItem()) // W39 (dockThemes): import, export, reset
            add(SettingItem.Pane(x("Wallpaper palette, density and text size"), Glyphs.Dial, "") { ThemingPane() })
            add(
                SettingItem.Choice(
                    x("Font"), Glyphs.Dial, com.bos.sphere.core.design.FontCatalog.entries, com.bos.sphere.core.design.FontCatalog.entry(p.uiFont), { x(it.label) },
                    hint = x("Titles stay in Space Grotesk"),
                    preview = { com.bos.sphere.feature.grid.FontChoice(p.uiFont) { v -> upd { it.copy(uiFont = v) } } },
                ) { v -> upd { it.copy(uiFont = v.id) } },
            )
            add(
                SettingItem.Choice(
                    x("Icon shape"), Glyphs.Apps, IconShape.entries, p.iconShape, { x(it.label) },
                    preview = { OneUiVisualChoice(IconShape.entries, p.iconShape, { v -> upd { it.copy(iconShape = v) } }, { x(it.label) }, { SettingsArtLook.iconShape.art(it) }, tag = "cfg.iconShape") },
                ) { v -> upd { it.copy(iconShape = v) } },
            )
            add(SettingItem.Choice(x("Icon colour"), Glyphs.Image, IconTint.entries, p.iconTint, { x(it.label) }, hint = x("Long-press an app to tint one")) { v -> upd { it.copy(iconTint = v) } }.asCards({ SettingsArtLook.iconTint.art(it) }, "cfg.iconTint"))
            add(SettingItem.Toggle(x("Icon plate"), Glyphs.Widget, p.iconPlate, x("A tile behind transparent icons")) { v -> upd { it.copy(iconPlate = v) } })
            add(
                SettingItem.Choice(
                    x("Settings backdrop"), Glyphs.Moon, com.bos.sphere.core.data.SettingsBackdrop.entries, p.settingsBackdrop, { x(it.label) },
                    preview = {
                        OneUiVisualChoice(com.bos.sphere.core.data.SettingsBackdrop.entries, p.settingsBackdrop, { v -> upd { it.copy(settingsBackdrop = v) } }, { x(it.label) }, { SettingsArtLook.backdrop.art(it) }, tag = "cfg.backdrop")
                    },
                ) { v -> upd { it.copy(settingsBackdrop = v) } },
            )
            // Wave 58 (CFG-66): over a pitch-black backdrop there is nothing to dim; the row stays, dimmed, with the reason.
            add(SettingItem.Slider(x("Backdrop dimming"), Glyphs.Moon, p.settingsDim, 0.2f..0.95f, pct(p.settingsDim)) { v -> upd { it.copy(settingsDim = v) } }
                .disabledUnless(p.settingsBackdrop == com.bos.sphere.core.data.SettingsBackdrop.Wallpaper, x("Backdrop is Pitch black")))
        },
    )

    // --- Search ------------------------------------------------------------------------------
    out += SettingCategory(
        "Radar",
        Glyphs.Radar,
        listOf(
            SettingItem.Choice(
                x("Radar layout"), Glyphs.Apps, com.bos.sphere.core.data.SearchLayout.entries, p.searchLayout, { x(it.label) },
                preview = { OneUiVisualChoice(com.bos.sphere.core.data.SearchLayout.entries, p.searchLayout, { v -> upd { it.copy(searchLayout = v) } }, { x(it.label) }, { SettingsArtHome.searchLayout.art(it) }, tag = "cfg.radarLayout") },
            ) { v -> upd { it.copy(searchLayout = v) } },
            SettingItem.Toggle(x("Keep this layout with a keyboard"), Glyphs.Apps, p.searchLayoutPinned, x("Off: flat list with a keyboard")) { v -> upd { it.copy(searchLayoutPinned = v) } },
            SettingItem.Choice(x("Radar background"), Glyphs.Moon, com.bos.sphere.core.data.RadarBackground.entries, p.radarBackground, { x(it.label) }, hint = null) { v -> upd { it.copy(radarBackground = v) } }.asCards({ SettingsArtHome.radarBackground.art(it) }, "cfg.radarBackground"),
            SettingItem.Toggle(x("Keep moving"), Glyphs.Swipe, p.searchMotion, x("Filmstrip and Orbit drift slowly")) { v -> upd { it.copy(searchMotion = v) } },
            SettingItem.Choice(x("Web engine"), Glyphs.Globe, com.bos.sphere.core.data.WebEngine.entries, p.webEngine, { it.label }, hint = x("Gets only what you send")) { v -> upd { it.copy(webEngine = v) } }.asCards({ SettingsArtLook.webEngine.art(it) }, "cfg.webEngine"),
            SettingItem.Toggle(x("Web results"), Glyphs.Globe, p.searchWeb, x("The web and the Play Store")) { v -> upd { it.copy(searchWeb = v) } },
            // Wave 43 (radarWeb): the privacy switch of the launcher's only network use. Off = nothing leaves the phone.
            SettingItem.Toggle(x("Web suggestions"), Glyphs.Globe, p.webSuggestions, x("Sends what you type to %s", p.webEngine.label)) { v -> upd { it.copy(webSuggestions = v) } }
                .disabledUnless(p.searchWeb, x("Web results is off")), // wave 58 (CFG-62): suggestions do nothing without web results
            SettingItem.Toggle(x("Actions"), Glyphs.Bolt, p.searchActions, x("Things to do, like “flashlight”")) { v -> upd { it.copy(searchActions = v) } },
            SettingItem.Toggle(x("Files"), Glyphs.Image, p.searchFiles, x("Documents, photos and downloads")) { v -> upd { it.copy(searchFiles = v) } },
            SettingItem.Toggle(x("Deep results"), Glyphs.Search, p.searchDeep, x("Inside files, with Fylz or Fotoz")) { v -> upd { it.copy(searchDeep = v) } },
            SettingItem.Toggle(x("Contacts"), Glyphs.Message, p.searchContacts, x("Call, message or email people")) { v -> upd { it.copy(searchContacts = v) } },
            SettingItem.Pane(x("Search folders"), Glyphs.Image, "") { com.bos.sphere.feature.grid.FolderGrantsPane() },
            SettingItem.Pane(x("Search providers from other apps"), Glyphs.Search, "") { com.bos.sphere.feature.grid.SearchProvidersPane() }
                .paneEntries(*m.searchProviders.map { (pkg, label) -> PaneEntry(SettingsRowMeta.PaneIds.provider(pkg), label, listOf("search provider", "results from apps")) }.toTypedArray()),
            SettingItem.Pane(x("Search history and aliases"), Glyphs.Search, "") { com.bos.sphere.feature.grid.SearchHistoryPane() },
        ),
    )

    // --- Indicator ---------------------------------------------------------------------------
    out += SettingCategory(
        x("Indicator"),
        Glyphs.Dial,
        buildList {
            // Wave 55: one calm style (four quiet ambient accents, Regular = the ordinary battery and signal, or Off) and, beside an ambient one, the regular indicators.
            val shownStyle = CalmStyles.resolve(p.calmStyle, p.wallpaper(WallpaperSurface.Glance).kind)
            add(
                SettingItem.Choice(
                    x("Calm style"), Glyphs.Dial, CalmStyle.entries, shownStyle, { x(it.label) },
                ) { v -> upd { it.copy(calmStyle = v) } }.asCards({ SettingsArtLook.calm.art(it) }, "cfg.calm"),
            )
            // Wave 58 (CFG-66): beside Off or Regular there is no calm style to add the indicators to; the row stays, dimmed, with the reason.
            add(SettingItem.Toggle(x("Also show regular battery and signal"), Glyphs.Dial, p.calmAlsoRegular, x("Battery and signal bars too")) { v -> upd { it.copy(calmAlsoRegular = v) } }
                .disabledUnless(shownStyle.ambient, x("Calm style is %s", x(shownStyle.label))))
            add(
                SettingItem.Choice(
                    x("Show"), Glyphs.Dial, IndicatorMetric.entries, p.indicatorMetric, { x(it.label) },
                    hint = x("Charging counts as healthy"),
                ) { v -> upd { it.copy(indicatorMetric = v) } }.asCards({ SettingsArtLook.metric.art(it) }, "cfg.metric"),
            )
            add(SettingItem.Slider(x("Glow: intensity"), Glyphs.Dial, p.indicatorIntensity, 0f..1f, pct(p.indicatorIntensity)) { v -> upd { it.copy(indicatorIntensity = v) } })
            add(SettingItem.Slider(x("Glow: speed"), Glyphs.Dial, p.indicatorSpeed, 0f..1f, pct(p.indicatorSpeed)) { v -> upd { it.copy(indicatorSpeed = v) } })
            add(SettingItem.Pane(x("Glow: preview"), Glyphs.Dial, "") { com.bos.sphere.feature.grid.IndicatorPreview(p) })
            add(
                SettingItem.Pane(x("Glow: colours"), Glyphs.Palette, "") {
                    var which by remember { mutableIntStateOf(0) }
                    val names = listOf("Low", "Middle", "High", "Idle")
                    val values = listOf(p.indicatorLow, p.indicatorMid, p.indicatorHigh, p.indicatorIdle)
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        names.forEachIndexed { i, n ->
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ColorDot(values[i], 34.dp, onClick = { which = i }, selected = which == i)
                                Text(L10n.tr(n), color = HyleColors.InkDim, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                    ColorPicker(values[which], { v ->
                        upd {
                            when (which) {
                                0 -> it.copy(indicatorLow = v); 1 -> it.copy(indicatorMid = v)
                                2 -> it.copy(indicatorHigh = v); else -> it.copy(indicatorIdle = v)
                            }
                        }
                    })
                },
            )
        },
    )

    // --- Sound -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Sound"),
        Glyphs.Music,
        listOf(
            SettingItem.Toggle(x("Interface sounds"), Glyphs.Music, p.uiSounds) { v -> upd { it.copy(uiSounds = v) } },
            SettingItem.Slider(x("Volume"), Glyphs.Ring, p.uiSoundVolume, 0f..1f, pct(p.uiSoundVolume)) { v -> upd { it.copy(uiSoundVolume = v) } },
            // Wave 57 (CFG-43): the launcher's own vibration, the same store and values as the Comms Sound and vibration sheet.
            SettingItem.Choice(
                x("Launcher vibration"), Glyphs.Bolt, com.bos.sphere.core.data.HapticStrength.entries, m.haptic, { x(it.name) },
            ) { v -> m.setHaptic(v) }.asCards({ SettingsArtApp.haptic.art(it) }, "cfg.haptic"),
        ),
    )

    // --- System ------------------------------------------------------------------------------
    out += SettingCategory(
        x("System"),
        Glyphs.Gear,
        listOf(
            SettingItem.Pane(x("Language"), Glyphs.Globe, if (p.language == UiLanguage.System) x("System") else p.language.label) { LanguagePane(p.language) { l -> upd { it.copy(language = l) } } },
            // Wave 57 (CFG-42): one page listing every grant (it replaces the default home, notification access and Do Not Disturb rows and the Comms access page).
            SettingItem.Pane(x("Access and privacy"), Glyphs.Gear, x("What Liftoff may read")) { AccessPrivacyPane(m.system) },
            SettingItem.Action(x("Android settings"), Glyphs.Gear, "", navigates = true, onClick = m.system.androidSettings),
        ),
    )
    // --- Capabilities (CFG-08) and Lab (CFG-17) ----------------------------------------------------
    if (m.capabilities.isNotEmpty()) {
        val report = m.capabilities.joinToString("\n") { c -> "${c.title}: ${c.state}" + (c.reason?.let { " - $it" } ?: "") + (c.fix?.let { " ($it)" } ?: "") }
        // Wave 57: one row of System > Help and recovery; its page lists every capability (each is still found by the search).
        val on = m.capabilities.count { it.state == com.bos.sphere.core.capabilities.CapState.On }
        out += SettingCategory(
            x("Capabilities"), Glyphs.Info,
            listOf<SettingItem>(
                SettingItem.Pane(x("Capabilities"), Glyphs.Info, x("%d of %d on", on, m.capabilities.size)) {
                    com.bos.sphere.feature.grid.ActionRow(x("Copy report"), "", Glyphs.Info, flashId = "caps.copy") { m.copyText(report) }
                    m.capabilities.forEach { c ->
                        com.bos.sphere.feature.grid.ActionRow(
                            c.title, listOfNotNull(c.state.name, c.reason, c.fix?.let { "→ $it" }).joinToString(" · "),
                            if (c.state == com.bos.sphere.core.capabilities.CapState.On) Glyphs.Bolt else Glyphs.Info, flashId = "caps.${c.title}",
                        ) {}
                    }
                }.paneEntries(*(listOf(PaneEntry("caps.copy", x("Copy report"))) + m.capabilities.map { c -> PaneEntry("caps.${c.title}", c.title, summary = c.state.name) }).toTypedArray()),
            ),
        )
    }
    if (m.lab.isNotEmpty() && LabGate.visible()) { // CFG-17
        out += SettingCategory(
            x("Lab"), Glyphs.Sliders,
            listOf<SettingItem>(SettingItem.Action(x("Copy all results"), Glyphs.Info, "", onClick = { m.copyText(m.lab.joinToString("\n\n") { "${it.title}\n${it.result}" }) }), deviceChecksItem()) + // wave 23: the Device checks page (frame timing, cold start, checklist, report)
                m.lab.map { e -> SettingItem.Action(e.title, Glyphs.Bolt, e.result, navigates = e.run != null, onClick = { e.run?.invoke() }) },
        )
    }
    // --- Backup (CFG-01..04, CFG-11) ---------------------------------------------------------------
    m.hiddenApps?.let { out += hiddenAppsCategory(it) } // wave 38
    out += com.bos.sphere.ui.backupCategory { SettingsReset.resetAll(m.updatePrefs) }
    out += com.bos.sphere.ui.profilesCategory() // wave 57: Profiles replace Workspaces
    out += com.bos.sphere.ui.desktopCategory()
    // --- About -------------------------------------------------------------------------------
    out += SettingCategory(
        x("About"),
        Glyphs.Info,
        m.about.map { (k, v) -> SettingItem.Action(x(k), Glyphs.Info, v) {} },
    )
    // Wave 56: the hidden side of the rows (ids, synonyms, slider defaults and units, pane rows); wave 57: every row placed into one of the six tabs,
    // each tab's reset, and the Finish setup page (last, so the indices before it stay put).
    return SettingsRowMeta.apply(out, p)
}

/**
 * One line for the Gestures category saying which swipes are free on the active home. Wave 58 (CFG-65): a few words (the help diet); how to reach
 * Settings from each home is on the home Style row's line.
 */
internal fun gestureSummary(style: HomeStyle, surface: com.bos.sphere.core.data.HomeSurface): String = when {
    style == HomeStyle.Psp && !surface.paged -> "Only pinch and double tap"
    surface.scroll == com.bos.sphere.core.data.SurfaceScroll.Horizontal -> "Up / down swipes are free"
    else -> "Sideways swipes are free"
}

/** Wave 58 (CFG-65): the short reason a touch gesture does nothing on this home (the gesture rows show it after the binding). */
internal fun gestureWhy(why: String): String = if (why == "opens the left page") "Opens the left page" else "Home scrolls this way"

/**
 * HOM-16 (wave 26): the wallpaper rows of the Home screen category. They are listed in every state (Glance on or off, signal
 * permitted or not, pages present or not): the live wallpaper choices live inside each pane, under the "Live wallpaper" type.
 */
internal fun wallpaperSettings(p: LauncherPrefs, upd: ((LauncherPrefs) -> LauncherPrefs) -> Unit): List<SettingItem> = buildList {
    val tr = { s: String -> com.bos.sphere.core.design.L10n.tr(s) }
    add(SettingItem.Pane(x("Glance wallpaper"), Glyphs.Image, com.bos.sphere.feature.grid.wallpaperSummary(p.wallpaper(WallpaperSurface.Glance), tr)) { com.bos.sphere.feature.grid.BackgroundPane(p, WallpaperSurface.Glance, upd) })
    add(SettingItem.Toggle(x("Pages use the same wallpaper as Glance"), Glyphs.Image, p.pagesSameAsGlance, x("Pages and the scroll alike")) { v -> upd { it.copy(pagesSameAsGlance = v) } })
    add(SettingItem.Pane(x("Pages wallpaper"), Glyphs.Image, com.bos.sphere.feature.grid.wallpaperSummary(p.wallpaper(WallpaperSurface.Pages), tr)) { com.bos.sphere.feature.grid.BackgroundPane(p, WallpaperSurface.Pages, upd) }.disabledUnless(!p.pagesSameAsGlance, x("Same as Glance")))
}

/** HOM-23: the Glance settings rows (the clock style rows of wave 41, the music toggle, hotspot presets and corners, controls timeout, indicator; wave 38 removed the old clock, date and weather settings). */
internal fun glanceSettings(p: LauncherPrefs, upd: ((LauncherPrefs) -> LauncherPrefs) -> Unit): List<SettingItem> = buildList {
    // Wave 41: the clock settings of the big Glance wallpaper clock: style (Analog/Digital of wave 38 became these styles), 12/24 hour for the digital styles, date, colour, size.
    add(SettingItem.Choice(x("Clock style"), Glyphs.Clock, com.bos.sphere.core.data.ClockStyle.entries, p.clockStyle, { x(it.label) }, hint = null) { v -> upd { it.copy(clockStyle = v) } }.asCards({ SettingsArtLook.clockStyle.art(it) }, "cfg.clockStyle"))
    // Wave 56 (CFG-31): the clock options stay on the page; with the clock Off they are dimmed ("Clock style is Off"), and the format also needs a digital clock.
    val clockOff = p.clockStyle == com.bos.sphere.core.data.ClockStyle.Off
    val clockOffReason = if (clockOff) x("Clock style is Off") else null
    run {
        val fmt = com.bos.sphere.core.data.ClockFormat.entries // wave 58 (CFG-61): System (12/24 by device) is the default and is offered first
        add(SettingItem.Choice(x("Clock format"), Glyphs.Clock, fmt, p.clockFormat, { x(it.label) }) { v -> upd { it.copy(clockFormat = v) } }.asCards({ SettingsArtLook.clockFormat.art(it) }, "cfg.clockFormat").disabled(clockOffReason ?: if (p.clockStyle.digital) null else x("Needs a digital clock")))
        add(SettingItem.Toggle(x("Show date under the clock"), Glyphs.Clock, p.glanceClockDate, null) { on -> upd { it.copy(glanceClockDate = on) } }.disabled(clockOffReason))
        add(SettingItem.Choice(x("Clock colour"), Glyphs.Clock, listOf(false, true), p.glanceClockAccent, { x(if (it) "Accent" else "Automatic") }, hint = x("Automatic reads on any wallpaper")) { v -> upd { it.copy(glanceClockAccent = v) } }.asCards({ SettingsArtLook.clockColour.art(it) }, "cfg.clockColour").disabled(clockOffReason))
        add(SettingItem.Choice(x("Clock size"), Glyphs.Clock, com.bos.sphere.core.data.ClockSize.entries, p.glanceClockSize, { x(it.label) }) { v -> upd { it.copy(glanceClockSize = v) } }.asCards({ SettingsArtLook.clockSize.art(it) }, "cfg.clockSize").disabled(clockOffReason))
    }
    add(SettingItem.Toggle(x("Show music"), Glyphs.Music, p.showMusic, x("Song and controls on every page")) { on -> upd { it.copy(showMusic = on) } })
    // Wave 59 (glanceTop): the Hotspots presets and the four hotspot switches are gone (the Hotspots editor in Home > Corners replaces them); the music
    // line gets its width: around the drawn top corners (default) or always edge to edge.
    add(
        SettingItem.Choice(x("Music line width"), Glyphs.Music, com.bos.sphere.core.data.MusicLineWidth.entries, p.musicLineWidth, { x(it.label) }) { v -> upd { it.copy(musicLineWidth = v) } }
            .asCards({ com.bos.sphere.feature.grid.SettingsArtHome.musicWidth.art(it) }, "cfg.musicWidth").disabledUnless(p.showMusic, x("Show music is off"))
            .previewTopic(com.bos.sphere.feature.grid.PreviewTopic.Glance, music = true), // the Preview draws the line (a sample title when nothing plays)
    )
    add(SettingItem.Stepper(x("Controls hide after (seconds)"), Glyphs.Clock, p.glanceControlsSeconds, com.bos.sphere.core.data.GlanceControls.MIN_SECONDS..com.bos.sphere.core.data.GlanceControls.MAX_SECONDS, null) { v -> upd { it.copy(glanceControlsSeconds = com.bos.sphere.core.data.GlanceControls.clampSeconds(v)) } })
    add(controlsTryItem(p, upd)) // W56: Try it
    add(SettingItem.Toggle(x("Surface indicator"), Glyphs.Dial, p.glanceIndicator, x("Comms, home and panel dots")) { v -> upd { it.copy(glanceIndicator = v) } })
}
