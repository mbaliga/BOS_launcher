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
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.AppsOnHome
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.CornerIconMode
import com.bos.sphere.core.data.DrawerSort
import com.bos.sphere.core.data.DrawerView
import com.bos.sphere.core.data.IconShape
import com.bos.sphere.core.data.IconTint
import com.bos.sphere.core.data.IndicatorEffect
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.PageMode
import com.bos.sphere.core.data.RailSide
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
import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.StripItem
import com.bos.sphere.core.data.StripZone
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DensityPreset
import com.bos.sphere.core.data.DrawerStyle
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
import com.bos.sphere.feature.grid.VisualChoice
import com.bos.sphere.feature.grid.HomeStylePreview
import com.bos.sphere.feature.grid.PageShapePreview
import com.bos.sphere.feature.grid.PagingPreview
import com.bos.sphere.feature.grid.DrawerViewPreview
import com.bos.sphere.feature.grid.StripPositionPreview
import com.bos.sphere.feature.grid.CornerStylePreview
import com.bos.sphere.feature.grid.ThemeModePreview
import com.bos.sphere.feature.grid.ThemedSampleIcon
import com.bos.sphere.feature.grid.DensityPreview
import com.bos.sphere.feature.grid.GlyphPreview
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.LabeledSlider
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.Stepper
import com.bos.sphere.feature.grid.ToggleRow
import com.bos.sphere.feature.grid.itemLabel
import com.bos.sphere.feature.grid.slotSummary
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
    val requestHealth: () -> Unit,
    val about: List<Pair<String, String>>,
    val healthAvailable: Boolean,
    val appsByKey: Map<String, AppEntry>,
    val isLandscape: Boolean,
    val chosenApp: (CornerActionId) -> String?,
    val pickLeftWidget: () -> Unit,
    val pickBackgroundPhoto: () -> Unit,
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
)

/** One row of the Lab page: a spike or live readout, its latest result text, and how to run it. */
class LabEntry(val id: String, val title: String, val result: String, val run: (() -> Unit)?)

class SystemLinks(
    val setDefaultHome: () -> Unit,
    val notificationAccess: () -> Unit,
    val dndAccess: () -> Unit,
    val androidSettings: () -> Unit,
)

/**
 * Which category Settings opens on. [CATEGORY_THIS_PAGE] is the "Page N" category (long-press on a
 * page of the paged home); [CATEGORY_HOME] is "Home screen". Resolve to a list index with
 * [settingsCategoryIndex]: the page category only exists on the paged home, so indices move.
 */
const val CATEGORY_THIS_PAGE = 0
const val CATEGORY_HOME = 1

private const val HOME_SCREEN_TITLE = "Home screen"

/** The index of [which] (one of the CATEGORY_ constants) in [categories] as built for the active home. */
fun settingsCategoryIndex(categories: List<SettingCategory>, which: Int): Int {
    val home = categories.indexOfFirst { it.title == x(HOME_SCREEN_TITLE) }.coerceAtLeast(0)
    return when (which) {
        CATEGORY_HOME -> home
        // No page category (a scrolling home): fall through to Home screen.
        CATEGORY_THIS_PAGE -> if (home == 0) 0 else CATEGORY_THIS_PAGE
        // 1000 + n: the category at list index n exactly (a Radar settings result opens its own category).
        in CATEGORY_INDEX_BASE until CATEGORY_INDEX_BASE + 1000 -> (which - CATEGORY_INDEX_BASE).coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        else -> which.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
    }
}

const val CATEGORY_INDEX_BASE = 1000

private fun homeStyleName(s: HomeStyle) = when (s) { HomeStyle.Grid -> "Pages"; HomeStyle.List -> "List"; HomeStyle.Psp -> "PSP"; HomeStyle.Dsi -> "DSi" }

/** A miniature status strip showing what is placed where. */
@Composable
private fun StripPreview(strip: Map<StripItem, StripZone>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).background(androidx.compose.ui.graphics.Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(1.dp, HyleColors.Hairline, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        StripZone.entries.forEach { z ->
            val items = strip.filterValues { it == z }.keys.joinToString("  ") { L10n.tr(it.label) }
            Text(if (items.isEmpty()) "·" else items, color = HyleColors.InkBright, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f),
                textAlign = when (z) { StripZone.values().first() -> androidx.compose.ui.text.style.TextAlign.Start; StripZone.values().last() -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
        }
    }
}

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

fun buildSettingsCategories(m: SettingsModel): List<SettingCategory> {
    val p = m.prefs
    val vSpacing = if (p.spacingV >= 0f) p.spacingV else p.iconSpacing
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
        add(
            SettingItem.Choice(
                x("Layout"), Glyphs.Apps, PageShape.entries, layout.shape, { x(it.label) },
                hint = when (layout.shape) {
                    PageShape.Grid -> x("Rows and columns of icons.")
                    PageShape.Circle -> x("A centre app with rings of 6, 12 and 18 around it.")
                    PageShape.Coverflow -> x("A strip you scrub sideways; tap the centre app to open it.")
                },
                preview = {
                    VisualChoice(PageShape.entries, layout.shape, { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) }, { x(it.label) }, previewHeight = 110.dp) { PageShapePreview(it) }
                },
            ) { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) },
        )
        when (layout.shape) {
            PageShape.Grid -> {
                val custom = layout.portrait != null || layout.landscape != null
                val pg = layout.portrait ?: p.portraitGrid
                val lg = layout.landscape ?: p.landscapeGrid
                add(
                    SettingItem.Toggle(x("Density follows the home default"), Glyphs.Widget, !custom, x("Home default: %s portrait · %s landscape", gridLabel(p.portraitGrid), gridLabel(p.landscapeGrid))) { follow ->
                        m.setPageLayout(m.pageIndex, if (follow) layout.copy(portrait = null, landscape = null) else layout.copy(portrait = pg, landscape = lg))
                    },
                )
                if (custom) {
                    add(SettingItem.Stepper(x("Columns · portrait"), Glyphs.Widget, pg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(columns = it), landscape = lg)) })
                    add(SettingItem.Stepper(x("Rows · portrait"), Glyphs.Widget, pg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(rows = it), landscape = lg)) })
                    add(SettingItem.Stepper(x("Columns · landscape"), Glyphs.Widget, lg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(columns = it), portrait = pg)) })
                    add(SettingItem.Stepper(x("Rows · landscape"), Glyphs.Widget, lg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS, x("Apps that no longer fit move to the next page.")) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(rows = it), portrait = pg)) })
                }
            }
            PageShape.Circle -> add(
                SettingItem.Stepper(x("Rings"), Glyphs.Dial, layout.rings, PageLayout.MIN_RINGS..PageLayout.MAX_RINGS, x("%d apps fit", PageLayout.circleCapacity(layout.rings))) { m.setPageLayout(m.pageIndex, layout.copy(rings = it)) },
            )
            PageShape.Coverflow -> Unit
        }
        add(SettingItem.Toggle(x("Clock tile on this page"), Glyphs.Clock, HomeTiles.CLOCK in page.keys, x("Moves it here if it is on another page")) { on -> m.setClockTile(m.pageIndex, on) })
        add(SettingItem.Action(x("Add a page after this"), Glyphs.Plus, "") { m.addPageAfter(m.pageIndex) })
        if (m.pages.size > 1) add(SettingItem.Action(x("Delete this page"), Glyphs.Close, x("Apps move to the previous page")) { m.deletePage(m.pageIndex) })
    }
    out += SettingCategory(x("Page %d", m.pageIndex + 1), Glyphs.Widget, pageItems)
    }

    // --- Home --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Home"),
        Glyphs.Apps,
        buildList {
            add(
                SettingItem.Choice(
                    x("Style"), Glyphs.Apps, HomeStyle.entries, p.homeStyle, { x(homeStyleName(it)) },
                    hint = x(
                        when (p.homeStyle) {
                            HomeStyle.Grid -> "Pages of icons you arrange yourself — or one long scroll (see Scrolling)."
                            HomeStyle.List -> "Every app in an A–Z list with a letter rail. Long-press an app for its menu; long-press a letter header or the margin for Settings."
                            HomeStyle.Psp -> "A cross media bar: swipe sideways for categories, up / down for apps. Hold the clock band for Settings."
                            HomeStyle.Dsi -> "Every app on a glossy tile, three across, one long scroll. Long-press the empty space for Settings."
                        },
                    ),
                    preview = {
                        VisualChoice(HomeStyle.entries, p.homeStyle, { s -> upd { it.copy(homeStyle = s) } }, { x(homeStyleName(it)) }, previewHeight = 120.dp) { HomeStylePreview(it) }
                    },
                ) { s -> upd { it.copy(homeStyle = s) } },
            )
            if (p.homeStyle == HomeStyle.Grid) add(
                SettingItem.Choice(
                    x("Scrolling"), Glyphs.Swipe, com.bos.sphere.core.data.HomeScroll.entries, p.homeScroll, { x(it.label) },
                    hint = x("Pages: swipe between screens. Continuous: one long scroll of every app."),
                    preview = {
                        VisualChoice(com.bos.sphere.core.data.HomeScroll.entries, p.homeScroll, { v -> upd { it.copy(homeScroll = v) } }, { x(it.label) }, columns = 2, previewHeight = 110.dp) { v ->
                            GlyphPreview(if (v == com.bos.sphere.core.data.HomeScroll.Paged) Glyphs.Swipe else Glyphs.Down, x(v.label))
                        }
                    },
                ) { v -> upd { it.copy(homeScroll = v) } },
            )
            if (continuous) add(SettingItem.Stepper(x("Icons across"), Glyphs.Apps, p.continuousColumns, 2..8) { v -> upd { it.copy(continuousColumns = v) } })
            if (continuous || p.homeStyle == HomeStyle.Dsi) add(
                SettingItem.Choice(
                    x("Scroll direction"), Glyphs.Swipe, com.bos.sphere.core.data.ScrollAxis.entries, p.scrollAxis, { x(it.label) },
                    hint = x("\"Long side\" keeps scrolling along the phone's longer edge however you hold it. Swipes across the other way are free for gestures."),
                ) { v -> upd { it.copy(scrollAxis = v) } },
            )
            if (paged) {
                val preset = DensityPreset.entries.firstOrNull { it.portrait == p.portraitGrid && it.landscape == p.landscapeGrid }
                val densityOptions: List<DensityPreset?> = if (preset == null) listOf<DensityPreset?>(null) + DensityPreset.entries else DensityPreset.entries
                add(
                    SettingItem.Choice(
                        x("Default density"), Glyphs.Widget, densityOptions, preset,
                        { if (it == null) x("Custom") + " ${gridLabel(p.portraitGrid)}" else x(it.label) },
                        hint = x("%s portrait · %s landscape", gridLabel(p.portraitGrid), gridLabel(p.landscapeGrid)),
                        preview = {
                            VisualChoice(DensityPreset.entries, preset ?: DensityPreset.Default, { d -> upd { it.copy(portraitGrid = d.portrait, landscapeGrid = d.landscape) } }, { x(it.label) }, previewHeight = 96.dp) {
                                DensityPreview(it.portrait.columns, it.portrait.rows, p.iconScale, p.iconSpacing)
                            }
                            DensityPreview(p.portraitGrid.columns, p.portraitGrid.rows, p.iconScale, p.iconSpacing, Modifier.padding(vertical = 8.dp).height(150.dp))
                            Sub("Portrait")
                            Stepper(L10n.tr("Columns"), p.portraitGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> upd { it.copy(portraitGrid = it.portraitGrid.copy(columns = v)) } }
                            Stepper(L10n.tr("Rows"), p.portraitGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> upd { it.copy(portraitGrid = it.portraitGrid.copy(rows = v)) } }
                            Sub("Landscape")
                            Stepper(L10n.tr("Columns"), p.landscapeGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> upd { it.copy(landscapeGrid = it.landscapeGrid.copy(columns = v)) } }
                            Stepper(L10n.tr("Rows"), p.landscapeGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> upd { it.copy(landscapeGrid = it.landscapeGrid.copy(rows = v)) } }
                            note("Pages with their own density keep it. After changing it you can choose to fill the empty space.")
                        },
                    ) { d -> if (d != null) upd { it.copy(portraitGrid = d.portrait, landscapeGrid = d.landscape) } },
                )
            }
            // Icon size: the paged / continuous grids, DSi tiles and List rows scale; the PSP bar is fixed.
            if (p.homeStyle != HomeStyle.Psp) add(
                SettingItem.Slider(x("Icon size"), Glyphs.Apps, p.iconScale, LauncherPrefs.MIN_ICON_SCALE..LauncherPrefs.MAX_ICON_SCALE, pct(p.iconScale)) { v -> upd { it.copy(iconScale = v) } },
            )
            if (paged) {
                add(SettingItem.Toggle(x("Same spacing both ways"), Glyphs.Widget, p.spacingV < 0f, x("Compact keeps square cells; higher spreads the icons without resizing them")) { same -> upd { it.copy(spacingV = if (same) -1f else it.iconSpacing) } })
                add(SettingItem.Slider(x(if (p.spacingV < 0f) "Spacing" else "Spacing across"), Glyphs.Widget, p.iconSpacing, 0f..1f, pct(p.iconSpacing)) { v -> upd { it.copy(iconSpacing = v) } })
                if (p.spacingV >= 0f) add(SettingItem.Slider(x("Spacing down"), Glyphs.Widget, p.spacingV, 0f..1f, pct(p.spacingV)) { v -> upd { it.copy(spacingV = v) } })
            } else if (continuous || p.homeStyle == HomeStyle.Dsi) {
                // The scrolling grids only have a gap between rows.
                add(SettingItem.Slider(x("Spacing"), Glyphs.Widget, p.iconSpacing, 0f..1f, pct(p.iconSpacing), x("Room between the rows of icons.")) { v -> upd { it.copy(iconSpacing = v) } })
            }
            if (paged) {
                add(SettingItem.Slider(x("Height in page"), Glyphs.Widget, p.gridBias, 0f..1f, pct(p.gridBias)) { v -> upd { it.copy(gridBias = v) } })
                add(SettingItem.Slider(x("Side margin"), Glyphs.Widget, p.gridInsetX, 0f..0.25f, pct(p.gridInsetX)) { v -> upd { it.copy(gridInsetX = v) } })
                add(SettingItem.Slider(x("Top margin"), Glyphs.Widget, p.gridInsetTop, 0f..0.4f, pct(p.gridInsetTop), x("Leave room for the clock")) { v -> upd { it.copy(gridInsetTop = v) } })
                add(SettingItem.Slider(x("Bottom margin"), Glyphs.Widget, p.gridInsetBottom, 0f..0.4f, pct(p.gridInsetBottom), x("Keep thumbs clear at the bottom")) { v -> upd { it.copy(gridInsetBottom = v) } })
            } else {
                // The scrolling homes only take a side margin (the strip and corners set top and bottom).
                add(SettingItem.Slider(x("Side margin"), Glyphs.Widget, p.gridInsetX, 0f..0.25f, pct(p.gridInsetX), x("Extra room at the left and right edges.")) { v -> upd { it.copy(gridInsetX = v) } })
            }
            if (paged) add(SettingItem.Slider(x("Label size"), Glyphs.Message, p.labelScale, 0.7f..1.5f, pct(p.labelScale)) { v -> upd { it.copy(labelScale = v) } })
            add(SettingItem.Toggle(x("App labels"), Glyphs.Message, p.showLabels, if (p.homeStyle == HomeStyle.Psp) x("Off shows only the focused app's name") else null) { v -> upd { it.copy(showLabels = v) } })
            if (paged) add(SettingItem.Toggle(x("Suggested app"), Glyphs.Bolt, p.showSuggestion, x("A predicted app in the first free slot of page one")) { v -> upd { it.copy(showSuggestion = v) } })
            if (paged) {
                add(
                    SettingItem.Choice(
                        x("Page turning · portrait"), Glyphs.Swipe, PagingAxis.entries, p.portraitPaging, { x(it.label) },
                        hint = x("Swipes across the other axis are free for gestures."),
                        preview = { VisualChoice(PagingAxis.entries, p.portraitPaging, { a -> upd { it.copy(portraitPaging = a) } }, { x(it.label) }, columns = 2) { PagingPreview(it) } },
                    ) { a -> upd { it.copy(portraitPaging = a) } },
                )
                add(
                    SettingItem.Choice(
                        x("Page turning · landscape"), Glyphs.Swipe, PagingAxis.entries, p.landscapePaging, { x(it.label) },
                        preview = { VisualChoice(PagingAxis.entries, p.landscapePaging, { a -> upd { it.copy(landscapePaging = a) } }, { x(it.label) }, columns = 2) { PagingPreview(it) } },
                    ) { a -> upd { it.copy(landscapePaging = a) } },
                )
            }
            add(SettingItem.Toggle(x("Always-on keyboard"), Glyphs.Message, p.alwaysKeyboard, x("A keyboard that's always open: type to find an app, like an old-school phone")) { v -> upd { it.copy(alwaysKeyboard = v) } })
            add(SettingItem.Toggle(x("Keyboard opens with Radar and drawer · portrait"), Glyphs.Message, p.keyboardOnOpenPortrait, x("Raises the on-screen keyboard when Radar or the drawer opens")) { v -> upd { it.copy(keyboardOnOpenPortrait = v) } })
            add(SettingItem.Toggle(x("Keyboard opens with Radar and drawer · landscape"), Glyphs.Message, p.keyboardOnOpenLandscape) { v -> upd { it.copy(keyboardOnOpenLandscape = v) } })
            add(SettingItem.Action(x("Keyboard shortcuts"), Glyphs.Info, x("Every shortcut and its keys; Meta shortcuts stay with the system"), navigates = true) { com.bos.sphere.feature.grid.LauncherInput.sheetOpen.value = true })
            add(SettingItem.Toggle(x("Hide status bar"), Glyphs.Up, p.hideStatusBar, x("The clock and corner icons sit flush to the top; swipe down to peek")) { v -> upd { it.copy(hideStatusBar = v) } })
        },
    )

    // --- Apps --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Apps"),
        Glyphs.Widget,
        buildList {
            // Open-the-drawer settings first, then what lives on the home screen.
            add(SettingItem.Choice(x("All apps opens as"), Glyphs.Apps, DrawerStyle.entries, p.drawerStyle, { x(it.label) }, hint = x("The wheel fans out around the corner you open it from.")) { v -> upd { it.copy(drawerStyle = v) } })
            add(SettingItem.Choice(x("Wheel groups"), Glyphs.Dial, WheelGrouping.entries, p.wheelGrouping, { x(it.label) }, hint = x("How the wheel groups apps into spokes.")) { g -> upd { it.copy(wheelGrouping = g) } })
            add(SettingItem.Choice(x("View switcher shows"), Glyphs.Apps, com.bos.sphere.core.data.SwitchStyle.entries, p.drawerViewSwitch, { x(it.label) }) { v -> upd { it.copy(drawerViewSwitch = v) } })
            add(
                SettingItem.Choice(
                    x("Drawer view"), Glyphs.ViewList, DrawerView.entries, p.drawerView, { x(it.label) },
                    preview = { VisualChoice(DrawerView.entries, p.drawerView, { v -> upd { it.copy(drawerView = v) } }, { x(it.label) }, columns = 2, previewHeight = 110.dp) { DrawerViewPreview(it) } },
                ) { v -> upd { it.copy(drawerView = v) } },
            )
            add(SettingItem.Choice(x("Sort by"), Glyphs.Menu, DrawerSort.entries, p.drawerSort, { x(it.label) }) { v -> upd { it.copy(drawerSort = v) } })
            add(SettingItem.Choice(x("Quick scrub side"), Glyphs.Swipe, RailSide.entries, p.drawerRail, { x(it.label) }, hint = x("Run a finger down the edge to jump through the list.")) { v -> upd { it.copy(drawerRail = v) } })
            add(SettingItem.Toggle(x("Show the A–Z letters"), Glyphs.Message, p.drawerRailVisible, x("Off hides the letters; the strip still scrubs")) { v -> upd { it.copy(drawerRailVisible = v) } })
            // Only the paged home has pages to curate; the scrolling homes always show every app.
            if (paged) add(SettingItem.Choice(x("Apps on home"), Glyphs.Apps, AppsOnHome.entries, p.appsOnHome, { x(it.label) }, hint = x("\"Only ones I add\" keeps the pages for what you choose; everything else lives in the drawer.")) { v -> upd { it.copy(appsOnHome = v) } })
            if (paged) add(SettingItem.Choice(x("Pages"), Glyphs.Swipe, PageMode.entries, p.pageMode, { x(it.label) }, hint = x("One page shows just the first page; the rest stay in the drawer.")) { v -> upd { it.copy(pageMode = v) } })
            add(
                SettingItem.Pane(x("Open with"), Glyphs.Apps, x("Choose the app for Music, Browser…")) {
                    listOf(
                        CornerActionId.Music, CornerActionId.Messages, CornerActionId.Browser, CornerActionId.Email, CornerActionId.Maps,
                        CornerActionId.Calendar, CornerActionId.Gallery, CornerActionId.Contacts, CornerActionId.Calculator, CornerActionId.Camera,
                    ).forEach { id ->
                        com.bos.sphere.feature.grid.ActionRow(
                            label = x(id.label), summary = m.chosenApp(id) ?: x("Ask each time"), selected = false, onClick = { m.pickAppFor(id) },
                        )
                    }
                    note("Pick any installed app — the list isn't limited to what the system suggests.")
                },
            )
        },
    )

    // --- Clock -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Clock"),
        Glyphs.Clock,
        listOf(
            SettingItem.Choice(
                x("Clock position"), Glyphs.Clock, ClockPosition.entries, p.clockPosition, { x(it.label) },
                hint = x("It can also be a tile on a page, or a corner complication."),
                preview = { VisualChoice(ClockPosition.entries, p.clockPosition, { c -> upd { it.copy(clockPosition = c) } }, { x(it.label) }, previewHeight = 96.dp) { StripPositionPreview(it) } },
            ) { c -> upd { it.copy(clockPosition = c) } },
            SettingItem.Choice(x("Clock layout"), Glyphs.Clock, com.bos.sphere.core.data.ClockLayout.entries, p.clockLayout, { x(it.label) }, hint = x("One line: time, date and temperature side by side. Two lines: time over the details.")) { v -> upd { it.copy(clockLayout = v) } },
            SettingItem.Slider(x("Clock size"), Glyphs.Dial, p.clockSize, LauncherPrefs.MIN_CLOCK_SIZE..LauncherPrefs.MAX_CLOCK_SIZE, "${p.clockSize.roundToInt()}", step = 1f) { v -> upd { it.copy(clockSize = v) } },
            SettingItem.Choice(x("Clock format"), Glyphs.Clock, ClockFormat.entries, p.clockFormat, { x(it.label) }) { c -> upd { it.copy(clockFormat = c) } },
            SettingItem.Toggle(x("Day"), Glyphs.Calendar, p.clockShowDay) { v -> upd { it.copy(clockShowDay = v) } },
            SettingItem.Toggle(x("Date"), Glyphs.Calendar, p.clockShowDate) { v -> upd { it.copy(clockShowDate = v) } },
            SettingItem.Toggle(x("Weather"), Glyphs.Globe, p.clockShowWeather, x("Uses your approximate location and open-meteo.com")) { v -> upd { it.copy(clockShowWeather = v) } },
            SettingItem.Toggle(x("Fahrenheit"), Glyphs.Globe, p.weatherFahrenheit) { v -> upd { it.copy(weatherFahrenheit = v) } },
            SettingItem.Pane(x("Status strip"), Glyphs.Dial, "${p.strip.size}") {
                note("Put readouts on the left, centre or right of the strip — like the top of a Nintendo 3DS.")
                StripPreview(p.strip)
                StripItem.entries.forEach { item ->
                    Sub(item.label)
                    ChipRow(listOf<StripZone?>(null) + StripZone.entries, p.strip[item], { z -> if (z == null) L10n.tr("Off") else L10n.tr(z.label) }) { z ->
                        upd { it.copy(strip = if (z == null) it.strip - item else it.strip + (item to z)) }
                    }
                }
            },
            SettingItem.Choice(x("Health data"), Glyphs.Steps, com.bos.sphere.core.data.HealthSourcePref.entries, p.healthSource, { x(it.label) }, hint = x("Steps, floors, distance and calories can come from Health Connect (Fitbit, Samsung Health, Garmin …).")) { v -> upd { it.copy(healthSource = v) } },
            SettingItem.Action(
                x("Allow Health Connect access"), Glyphs.Steps,
                if (m.healthAvailable) "" else x("Health Connect isn't available on this phone yet — install or update it from the Play Store."),
                navigates = true, onClick = m.requestHealth,
            ),
        ),
    )

    // --- Corners -----------------------------------------------------------------------------
    out += SettingCategory(
        x("Corners"),
        Glyphs.Palette,
        buildList {
            add(
                SettingItem.Choice(
                    x("Corner style"), Glyphs.Palette, CornerStyle.entries, p.cornerStyle, { x(it.label) },
                    hint = x("Holo: just the icons. Sony: a quarter-disc in each corner. Bubbles: a round bubble behind each icon."),
                    preview = { VisualChoice(CornerStyle.entries, p.cornerStyle, { c -> upd { it.copy(cornerStyle = c) } }, { x(it.label) }, columns = 3, previewHeight = 96.dp) { CornerStylePreview(it) } },
                ) { c -> upd { it.copy(cornerStyle = c) } },
            )
            add(SettingItem.Slider(x("Corner icon size"), Glyphs.Dial, p.cornerIconSize, LauncherPrefs.MIN_CORNER_ICON..LauncherPrefs.MAX_CORNER_ICON, "${p.cornerIconSize.roundToInt()}", step = 1f) { v -> upd { it.copy(cornerIconSize = v) } })
            add(SettingItem.Choice(x("Icon type"), Glyphs.Apps, CornerIconMode.entries, p.cornerIconMode, { x(it.label) }, hint = x("App icon shows the real icon of the app an action opens (Music → your music player).")) { c -> upd { it.copy(cornerIconMode = c) } })
            add(SettingItem.Toggle(x("Follow device rotation"), Glyphs.Flip, p.cornersFollowDevice, x("Each corner stays under the same physical corner when you rotate")) { v -> upd { it.copy(cornersFollowDevice = v) } })
            add(SettingItem.Toggle(x("Separate corners for landscape"), Glyphs.Flip, p.cornersPerOrientation, x("Landscape keeps its own set; edits below apply to the orientation you are in")) { v -> upd { it.copy(cornersPerOrientation = v) } })
            add(SettingItem.Toggle(x("Hot corners with a mouse"), Glyphs.Pinch, p.hotCornersOn, x("Rest the pointer in a corner to reveal its action, click to run it")) { v -> upd { it.copy(hotCornersOn = v) } })
            if (p.hotCornersOn) add(SettingItem.Slider(x("Hot corner delay"), Glyphs.Dial, p.hotCornerDwellMs.toFloat(), 50f..600f, "${p.hotCornerDwellMs} ms", step = 10f) { v -> upd { it.copy(hotCornerDwellMs = v.roundToInt()) } })
            Corner.entries.forEach { c ->
                add(SettingItem.Action(x(c.label), Glyphs.Palette, slotSummary(m.corners[c] ?: CornerSlot.Empty, m.appsByKey), navigates = true) { m.editCorner(c) })
            }
        },
    )

    // --- Launchpad ---------------------------------------------------------------------------
    out += SettingCategory(
        "Launchpad",
        Glyphs.Bell,
        buildList {
            val leftPick: (com.bos.sphere.core.data.LeftPage) -> Unit = { v ->
                if (v == com.bos.sphere.core.data.LeftPage.Widget && p.leftWidgetId < 0) m.pickLeftWidget() else upd { it.copy(leftPage = v) }
            }
            add(
                SettingItem.Choice(
                    x("Left page"), Glyphs.Bell, com.bos.sphere.core.data.LeftPage.entries, p.leftPage,
                    { if (it == com.bos.sphere.core.data.LeftPage.Widget) (m.leftWidgetLabel ?: x("A widget")) else x(it.label) },
                    hint = x("One swipe right from the first page: your notifications, or any app's widget full-screen."),
                    preview = {
                        VisualChoice(com.bos.sphere.core.data.LeftPage.entries, p.leftPage, leftPick, { x(it.label) }, columns = 3, previewHeight = 80.dp) { v ->
                            GlyphPreview(when (v) { com.bos.sphere.core.data.LeftPage.Off -> Glyphs.Close; com.bos.sphere.core.data.LeftPage.Launchpad -> Glyphs.Bell; else -> Glyphs.Widget })
                        }
                    },
                    onSelect = leftPick,
                ),
            )
            if (p.leftPage == com.bos.sphere.core.data.LeftPage.Widget || p.leftWidgetId >= 0) {
                add(SettingItem.Action(x("Choose widget"), Glyphs.Widget, m.leftWidgetLabel ?: x("None yet"), navigates = true, onClick = m.pickLeftWidget))
            }
            add(
                SettingItem.Choice(
                    x("Launchpad theme"), Glyphs.Moon, com.bos.sphere.core.data.LaunchpadTheme.entries, p.launchpadTheme, { x(it.label) },
                    hint = x("Dark suits the rest of the launcher; Light is the classic hub look."),
                    preview = {
                        VisualChoice(com.bos.sphere.core.data.LaunchpadTheme.entries, p.launchpadTheme, { v -> upd { it.copy(launchpadTheme = v) } }, { x(it.label) }, columns = 3, previewHeight = 96.dp) { t ->
                            val dark = t == com.bos.sphere.core.data.LaunchpadTheme.Dark
                            val both = t == com.bos.sphere.core.data.LaunchpadTheme.System
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                                for (isDark in if (both) listOf(true, false) else listOf(dark)) {
                                    androidx.compose.foundation.layout.Column(
                                        Modifier.weight(1f).fillMaxSize().background(if (isDark) androidx.compose.ui.graphics.Color(0xFF0B0D12) else androidx.compose.ui.graphics.Color(0xFFF7F8FA)),
                                    ) {
                                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(14.dp).background(androidx.compose.ui.graphics.Color(0xFF0A7CBF)))
                                        repeat(3) {
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.padding(horizontal = 6.dp, vertical = 5.dp).fillMaxWidth().height(6.dp)
                                                    .background(if (isDark) androidx.compose.ui.graphics.Color(0x66FFFFFF) else androidx.compose.ui.graphics.Color(0x66000000), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) { v -> upd { it.copy(launchpadTheme = v) } },
            )
            add(SettingItem.Toggle(x("Unread strip"), Glyphs.Bell, p.launchpadRail, x("A slim strip of unread counts at the left edge")) { v -> upd { it.copy(launchpadRail = v) } })
            add(
                SettingItem.Pane(x("Launchpad gestures"), Glyphs.Pinch, x("Pinch, slide, long-press")) {
                    val bound by com.bos.sphere.core.data.LaunchpadSettings.gestures.collectAsState()
                    com.bos.sphere.core.data.LaunchpadGesture.entries.forEach { g ->
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
            if (surface.hasDensity) add(
            SettingItem.Toggle(
                x("Pinch changes grid density"), Glyphs.Pinch, pinchDensity,
                if (paged) x("Pinch in packs more icons in; spread opens them up") else x("Pinch in adds a column; spread removes one"),
            ) { on ->
                if (on) {
                    m.setGesture(HomeGesture.PinchIn, CornerItem.Action(CornerActionId.GridDenser))
                    m.setGesture(HomeGesture.PinchOut, CornerItem.Action(CornerActionId.GridSparser))
                } else {
                    m.setGesture(HomeGesture.PinchIn, CornerItem.Action(CornerActionId.Search))
                    m.setGesture(HomeGesture.PinchOut, null)
                }
            },
            )
            HomeGesture.entries.filter { it.kind == GestureKind.Touch }.forEach { g ->
                val bound = m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: x("Nothing")
                val why = g.unavailableReason(surface)
                add(SettingItem.Action(x(g.label), Glyphs.forGesture(g), if (why == null) bound else "$bound · ${x("not on %s: %s", styleName, x(why))}", navigates = true) { m.editGesture(g) })
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
            add(SettingItem.Slider(x("Motion sensitivity"), Glyphs.Dial, p.motionSensitivity, 0f..1f, pct(p.motionSensitivity), x("Higher reacts to lighter movements (and more false triggers).")) { v -> upd { it.copy(motionSensitivity = v) } })
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
            add(
                SettingItem.Choice(
                    x("Light / dark"), Glyphs.Moon, ThemeMode.entries, p.themeMode, { x(it.label) },
                    preview = { VisualChoice(ThemeMode.entries, p.themeMode, { v -> upd { it.copy(themeMode = v) } }, { x(it.label) }, previewHeight = 96.dp) { ThemeModePreview(it, androidx.compose.ui.graphics.Color(p.accentColor)) } },
                ) { v -> upd { it.copy(themeMode = v) } },
            )
            add(SettingItem.Pane(x("Accent"), Glyphs.Palette, "") { ColorPicker(p.accentColor, { v -> upd { it.copy(accentColor = v) } }) })
            add(SettingItem.Pane(x("Wallpaper palette, density and text size"), Glyphs.Dial, "") { ThemingPane() })
            add(
                SettingItem.Choice(
                    x("Icon shape"), Glyphs.Apps, IconShape.entries, p.iconShape, { x(it.label) },
                    preview = { VisualChoice(IconShape.entries, p.iconShape, { v -> upd { it.copy(iconShape = v) } }, { x(it.label) }, columns = 3, previewHeight = 64.dp) { ThemedSampleIcon(52.dp, 1, shape = it) } },
                ) { v -> upd { it.copy(iconShape = v) } },
            )
            add(SettingItem.Choice(x("Icon colour"), Glyphs.Image, IconTint.entries, p.iconTint, { x(it.label) }, hint = x("Long-press an app and choose Icon colour to tint just that app.")) { v -> upd { it.copy(iconTint = v) } })
            add(SettingItem.Toggle(x("Icon plate"), Glyphs.Widget, p.iconPlate, x("A tile behind transparent icons")) { v -> upd { it.copy(iconPlate = v) } })
            add(
                SettingItem.Choice(
                    x("Settings backdrop"), Glyphs.Moon, com.bos.sphere.core.data.SettingsBackdrop.entries, p.settingsBackdrop, { x(it.label) },
                    hint = x("Settings use your wallpaper, dimmed — or pitch black."),
                    preview = {
                        VisualChoice(com.bos.sphere.core.data.SettingsBackdrop.entries, p.settingsBackdrop, { v -> upd { it.copy(settingsBackdrop = v) } }, { x(it.label) }, columns = 2, previewHeight = 110.dp) { b ->
                            if (b == com.bos.sphere.core.data.SettingsBackdrop.Black) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
                            else androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                                com.bos.sphere.feature.grid.BackgroundLayer(p.backgroundKind, p.backgroundPreset, p.backgroundColorA, p.backgroundColorB, Modifier.fillMaxSize(), animate = false, picturePath = p.backgroundPicturePath)
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = p.settingsDim)))
                            }
                        }
                    },
                ) { v -> upd { it.copy(settingsBackdrop = v) } },
            )
            if (p.settingsBackdrop == com.bos.sphere.core.data.SettingsBackdrop.Wallpaper) {
                add(SettingItem.Slider(x("Backdrop dimming"), Glyphs.Moon, p.settingsDim, 0.2f..0.95f, pct(p.settingsDim)) { v -> upd { it.copy(settingsDim = v) } })
            }
            add(
                SettingItem.Pane(x("Background"), Glyphs.Image, x(p.backgroundKind.name)) {
                    val kinds = com.bos.sphere.core.data.BackgroundKind.entries
                    VisualChoice(kinds, p.backgroundKind, { k ->
                        upd { it.copy(backgroundKind = k, backgroundPreset = com.bos.sphere.feature.grid.BackgroundPresets.default(k)) }
                    }, { x(it.name) }, columns = 3, previewHeight = 90.dp) { k ->
                        com.bos.sphere.feature.grid.BackgroundLayer(k, com.bos.sphere.feature.grid.BackgroundPresets.default(k), p.backgroundColorA, p.backgroundColorB, Modifier.fillMaxWidth().height(90.dp), animate = false, picturePath = p.backgroundPicturePath)
                    }
                    val presets = com.bos.sphere.feature.grid.BackgroundPresets.of(p.backgroundKind).let { base ->
                        if (p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.Picture && p.backgroundPicturePath.isNotEmpty()) base + com.bos.sphere.feature.grid.BackgroundPreset("photo", x("My photo")) else base
                    }
                    if (presets.isNotEmpty()) {
                        Sub(p.backgroundKind.name)
                        VisualChoice(presets, presets.firstOrNull { it.id == p.backgroundPreset } ?: presets.first(), { pr -> upd { it.copy(backgroundPreset = pr.id) } }, { it.label }, columns = 3, previewHeight = 90.dp) { pr ->
                            com.bos.sphere.feature.grid.BackgroundLayer(p.backgroundKind, pr.id, p.backgroundColorA, p.backgroundColorB, Modifier.fillMaxWidth().height(90.dp), animate = false, picturePath = p.backgroundPicturePath)
                        }
                    }
                    if (p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.Solid || p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.Gradient) {
                        Sub("Your own colour")
                        ColorPicker(p.backgroundColorA, { v -> upd { it.copy(backgroundColorA = v, backgroundPreset = "custom") } })
                        if (p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.Gradient) {
                            Sub("Second colour")
                            ColorPicker(p.backgroundColorB, { v -> upd { it.copy(backgroundColorB = v, backgroundPreset = "custom") } })
                        }
                    }
                    if (p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.Picture) {
                        com.bos.sphere.feature.grid.ActionRow(label = x("Choose a photo…"), summary = if (p.backgroundPicturePath.isEmpty()) null else x("Replace"), onClick = m.pickBackgroundPhoto)
                    }
                    if (p.backgroundKind == com.bos.sphere.core.data.BackgroundKind.System) note("Your phone's wallpaper shows through.")
                },
            )
        },
    )

    // --- Search ------------------------------------------------------------------------------
    out += SettingCategory(
        "Radar",
        Glyphs.Radar,
        listOf(
            SettingItem.Choice(
                x("Radar layout"), Glyphs.Apps, com.bos.sphere.core.data.SearchLayout.entries, p.searchLayout, { x(it.label) },
                preview = {
                    VisualChoice(com.bos.sphere.core.data.SearchLayout.entries, p.searchLayout, { v -> upd { it.copy(searchLayout = v, searchLayoutPinned = true) } }, { x(it.label) }, previewHeight = 140.dp) {
                        com.bos.sphere.feature.grid.SearchLayoutPreview(it)
                    }
                },
            ) { v -> upd { it.copy(searchLayout = v, searchLayoutPinned = true) } },
            SettingItem.Toggle(x("Keep this layout with a keyboard"), Glyphs.Apps, p.searchLayoutPinned, x("Off: Radar opens as a flat list while a keyboard or mouse is in use")) { v -> upd { it.copy(searchLayoutPinned = v) } },
            SettingItem.Choice(
                x("Background"), Glyphs.Image, com.bos.sphere.core.data.SearchBackdropStyle.entries,
                if (p.searchBackdrop) p.searchBackdropStyle else com.bos.sphere.core.data.SearchBackdropStyle.None, { x(it.label) },
                hint = x("A picture, plain black, or a radar scope with a turning sweep"),
            ) { v -> upd { it.copy(searchBackdropStyle = v, searchBackdrop = true) } },
            SettingItem.Toggle(x("Keep moving"), Glyphs.Swipe, p.searchMotion, x("Filmstrip and Orbit drift slowly; off keeps them still until you move them")) { v -> upd { it.copy(searchMotion = v) } },
            SettingItem.Choice(x("Web engine"), Glyphs.Globe, com.bos.sphere.core.data.WebEngine.entries, p.webEngine, { it.label }, hint = x("DuckDuckGo understands !bangs: type !g for Google, !gi for images, !yt for YouTube.")) { v -> upd { it.copy(webEngine = v) } },
            SettingItem.Toggle(x("Web results"), Glyphs.Globe, p.searchWeb, x("Search the web and the Play Store from here")) { v -> upd { it.copy(searchWeb = v) } },
            SettingItem.Toggle(x("Actions"), Glyphs.Bolt, p.searchActions, x("Find things to do, like “flashlight”")) { v -> upd { it.copy(searchActions = v) } },
            SettingItem.Toggle(x("Files"), Glyphs.Image, p.searchFiles, x("Documents, photos and downloads on this phone")) { v -> upd { it.copy(searchFiles = v) } },
            SettingItem.Toggle(x("Deep results"), Glyphs.Search, p.searchDeep, x("With Fylz or Fotoz installed: text inside documents and images")) { v -> upd { it.copy(searchDeep = v) } },
            SettingItem.Toggle(x("Contacts"), Glyphs.Message, p.searchContacts, x("Call, message or email people straight from results")) { v -> upd { it.copy(searchContacts = v) } },
        ),
    )

    // --- Indicator ---------------------------------------------------------------------------
    out += SettingCategory(
        x("Indicator"),
        Glyphs.Dial,
        buildList {
            add(
                SettingItem.Choice(
                    x("Glow: measurement"), Glyphs.Dial, IndicatorMetric.entries, p.indicatorMetric, { x(it.label) },
                    hint = x("What the background reads out: low to high is red → amber → green."),
                ) { v -> upd { it.copy(indicatorMetric = v, indicatorInvert = v == IndicatorMetric.Storage || v == IndicatorMetric.Memory) } },
            )
            IndicatorEffect.entries.forEach { e ->
                add(SettingItem.Toggle("${x("Glow")}: ${x(e.label)}", Glyphs.Bolt, e in p.indicatorEffects) { v -> upd { it.copy(indicatorEffects = if (v) it.indicatorEffects + e else it.indicatorEffects - e) } })
            }
            add(SettingItem.Slider(x("Glow: intensity"), Glyphs.Dial, p.indicatorIntensity, 0f..1f, pct(p.indicatorIntensity)) { v -> upd { it.copy(indicatorIntensity = v) } })
            add(SettingItem.Slider(x("Glow: speed"), Glyphs.Dial, p.indicatorSpeed, 0f..1f, pct(p.indicatorSpeed)) { v -> upd { it.copy(indicatorSpeed = v) } })
            add(SettingItem.Toggle(x("Glow: flip the ramp"), Glyphs.Flip, p.indicatorInvert, x("For measurements where low is good (storage used)")) { v -> upd { it.copy(indicatorInvert = v) } })
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
        ),
    )

    // --- System ------------------------------------------------------------------------------
    out += SettingCategory(
        x("System"),
        Glyphs.Gear,
        listOf(
            SettingItem.Choice(x("Language"), Glyphs.Globe, UiLanguage.entries, p.language, { if (it == UiLanguage.System) x("System") else it.label }) { l -> upd { it.copy(language = l) } },
            SettingItem.Action(x("Set as default home"), Glyphs.Apps, "", navigates = true, onClick = m.system.setDefaultHome),
            SettingItem.Action(x("Notification access"), Glyphs.Bell, x("For the notifications hub"), navigates = true, onClick = m.system.notificationAccess),
            SettingItem.Action(x("Do Not Disturb access"), Glyphs.Moon, x("Needed for silent mode / DND actions"), navigates = true, onClick = m.system.dndAccess),
            SettingItem.Action(x("Android settings"), Glyphs.Gear, "", navigates = true, onClick = m.system.androidSettings),
        ),
    )
    // --- Capabilities (CFG-08) and Lab (CFG-17) ----------------------------------------------------
    if (m.capabilities.isNotEmpty()) {
        val report = m.capabilities.joinToString("\n") { c -> "${c.title}: ${c.state}" + (c.reason?.let { " - $it" } ?: "") + (c.fix?.let { " ($it)" } ?: "") }
        out += SettingCategory(
            x("Capabilities"), Glyphs.Info,
            listOf<SettingItem>(SettingItem.Action(x("Copy report"), Glyphs.Info, "", onClick = { m.copyText(report) })) +
                m.capabilities.map { c ->
                    SettingItem.Action(
                        c.title, if (c.state == com.bos.sphere.core.capabilities.CapState.On) Glyphs.Bolt else Glyphs.Info,
                        listOfNotNull(c.state.name, c.reason, c.fix?.let { "→ $it" }).joinToString(" · "),
                    ) {}
                },
        )
    }
    if (m.lab.isNotEmpty()) {
        out += SettingCategory(
            x("Lab"), Glyphs.Sliders,
            listOf<SettingItem>(SettingItem.Action(x("Copy all results"), Glyphs.Info, "", onClick = { m.copyText(m.lab.joinToString("\n\n") { "${it.title}\n${it.result}" }) })) +
                m.lab.map { e -> SettingItem.Action(e.title, Glyphs.Bolt, e.result, navigates = e.run != null, onClick = { e.run?.invoke() }) },
        )
    }
    // --- Backup (CFG-01..04, CFG-11) ---------------------------------------------------------------
    out += com.bos.sphere.ui.backupCategory()
    out += com.bos.sphere.ui.puckCategory()
    // --- About -------------------------------------------------------------------------------
    out += SettingCategory(
        x("About"),
        Glyphs.Info,
        m.about.map { (k, v) -> SettingItem.Action(x(k), Glyphs.Info, v) {} },
    )
    return reorganise(out, paged)
}

/** One line for the Gestures category saying how touch works on the active home. */
internal fun gestureSummary(style: HomeStyle, surface: com.bos.sphere.core.data.HomeSurface): String = when {
    surface.paged -> if (surface.scroll == com.bos.sphere.core.data.SurfaceScroll.Horizontal)
        "Pages turn sideways, so up / down swipes, pinch and double tap on empty space are free; long-press empty space for page settings."
    else "Pages turn up / down, so sideways swipes, pinch and double tap on empty space are free; long-press empty space for page settings."
    style == HomeStyle.Psp -> "The bar scrolls both ways, so only pinch and double tap on empty space fire here; hold the clock band for Settings."
    surface.scroll == com.bos.sphere.core.data.SurfaceScroll.Horizontal ->
        "This home scrolls sideways, so up / down swipes, pinch and double tap on empty space are free; long-press empty space for Settings."
    else -> "This home scrolls up / down, so sideways swipes, pinch and double tap on empty space are free; long-press empty space for Settings."
}

/**
 * The information architecture, as a launcher user expects it: what's on the home screen, how it
 * looks, the clock, corners, gestures, the app drawer, search, the left page, then system.
 * (The categories above are built by topic; this regroups them.) [paged] = the first built
 * category is the "Page N" one, which only the paged home has.
 */
private fun reorganise(built: List<SettingCategory>, paged: Boolean): List<SettingCategory> {
    fun cat(title: String) = built.firstOrNull { it.title == title || it.title == x(title) }
    fun items(vararg titles: String) = titles.flatMap { cat(it)?.items.orEmpty() }
    val out = ArrayList<SettingCategory>()
    if (paged) built.firstOrNull()?.let { out += it } // This page
    out += SettingCategory(x(HOME_SCREEN_TITLE), Glyphs.Apps, items("Home"))
    out += SettingCategory(x("Look"), Glyphs.Palette, items("Theme", "Indicator"))
    out += SettingCategory(x("Clock & status"), Glyphs.Clock, items("Clock"))
    out += SettingCategory(x("Corners"), Glyphs.Plus, items("Corners"))
    out += SettingCategory(x("Gestures"), Glyphs.Pinch, items("Gestures", "Motion"))
    out += SettingCategory(x("App drawer"), Glyphs.Widget, cat("Apps")?.items.orEmpty())
    cat("Radar")?.let { out += it }
    cat("Launchpad")?.let { out += it }
    out += SettingCategory(x("System & sound"), Glyphs.Gear, items("System", "Sound"))
    cat("Backup")?.let { out += it }
    cat("Capabilities")?.let { out += it }
    cat("Lab")?.let { out += it }
    cat("About")?.let { out += it }
    return out
}

@Composable
private fun ChipRowItem(label: String, selected: Boolean, onClick: () -> Unit) {
    com.bos.sphere.feature.grid.ActionRow(label = label, selected = selected, onClick = onClick)
}
