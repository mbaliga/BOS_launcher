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
import com.bos.sphere.feature.sphere.RowMode
import com.bos.sphere.feature.sphere.SphereParams
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
    val sphere: SphereSettings,
    val system: SystemLinks,
)

class SphereSettings(
    val params: SphereParams,
    val rowMode: RowMode,
    val tiles: Boolean,
    val rubik: Boolean,
    val onParams: (SphereParams) -> Unit,
    val onRowMode: (RowMode) -> Unit,
    val onTiles: (Boolean) -> Unit,
    val onRubik: (Boolean) -> Unit,
)

class SystemLinks(
    val setDefaultHome: () -> Unit,
    val notificationAccess: () -> Unit,
    val dndAccess: () -> Unit,
    val androidSettings: () -> Unit,
)

/** Index of the "This page" category (opened by long-pressing a page). */
const val CATEGORY_THIS_PAGE = 0
const val CATEGORY_HOME = 1

private fun homeStyleName(s: HomeStyle) = when (s) { HomeStyle.Grid -> "Pages"; HomeStyle.Sphere -> "Sphere"; HomeStyle.Bubbles -> "Bubbles" }

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

    // --- This page ---------------------------------------------------------------------------
    val page = m.pages.getOrNull(m.pageIndex) ?: HomePage()
    val layout = page.layout
    val pageItems = buildList {
        add(
            SettingItem.Pane(x("Layout"), Glyphs.Apps, x(layout.shape.label)) {
                VisualChoice(PageShape.entries, layout.shape, { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) }, { x(it.label) }, previewHeight = 110.dp) { PageShapePreview(it) }
                note(
                    when (layout.shape) {
                        PageShape.Grid -> "Rows and columns of icons."
                        PageShape.Circle -> "A centre app with rings of 6, 12 and 18 around it."
                        PageShape.Coverflow -> "A strip you scrub sideways; tap the centre app to open it."
                    },
                )
            },
        )
        when (layout.shape) {
            PageShape.Grid -> {
                val custom = layout.portrait != null || layout.landscape != null
                val pg = layout.portrait ?: p.portraitGrid
                val lg = layout.landscape ?: p.landscapeGrid
                add(
                    SettingItem.Pane(
                        x("Density"),
                        Glyphs.Widget,
                        (if (custom) x("This page") else x("Home default")) + ": ${gridLabel(pg)} · ${gridLabel(lg)}",
                    ) {
                        ToggleRow(L10n.tr("Follow home default"), !custom, { follow ->
                            m.setPageLayout(
                                m.pageIndex,
                                if (follow) layout.copy(portrait = null, landscape = null) else layout.copy(portrait = pg, landscape = lg),
                            )
                        })
                        if (custom) {
                            Sub("Portrait")
                            Stepper(L10n.tr("Columns"), pg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(columns = it), landscape = lg)) }
                            Stepper(L10n.tr("Rows"), pg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(rows = it), landscape = lg)) }
                            Sub("Landscape")
                            Stepper(L10n.tr("Columns"), lg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(columns = it), portrait = pg)) }
                            Stepper(L10n.tr("Rows"), lg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(rows = it), portrait = pg)) }
                        }
                        note("Apps that no longer fit move to the next page.")
                    },
                )
            }
            PageShape.Circle -> add(
                SettingItem.Pane(x("Rings"), Glyphs.Dial, "${layout.rings} · ${PageLayout.circleCapacity(layout.rings)}") {
                    Stepper(L10n.tr("Rings"), layout.rings, PageLayout.MIN_RINGS..PageLayout.MAX_RINGS) { m.setPageLayout(m.pageIndex, layout.copy(rings = it)) }
                },
            )
            PageShape.Coverflow -> Unit
        }
        add(SettingItem.Toggle(x("Clock tile on this page"), Glyphs.Clock, HomeTiles.CLOCK in page.keys, x("Moves it here if it is on another page")) { on -> m.setClockTile(m.pageIndex, on) })
        add(SettingItem.Action(x("Add a page after this"), Glyphs.Plus, "") { m.addPageAfter(m.pageIndex) })
        if (m.pages.size > 1) add(SettingItem.Action(x("Delete this page"), Glyphs.Close, x("Apps move to the previous page")) { m.deletePage(m.pageIndex) })
    }
    out += SettingCategory(x("Page %d", m.pageIndex + 1), Glyphs.Widget, pageItems)

    // --- Home --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Home"),
        Glyphs.Apps,
        listOf(
            SettingItem.Pane(x("Style"), Glyphs.Apps, x(homeStyleName(p.homeStyle))) {
                VisualChoice(HomeStyle.entries, p.homeStyle, { s -> upd { it.copy(homeStyle = s) } }, { x(homeStyleName(it)) }, previewHeight = 120.dp) { HomeStylePreview(it) }
            },
            SettingItem.Pane(x("Default density"), Glyphs.Widget, "${gridLabel(p.portraitGrid)} · ${gridLabel(p.landscapeGrid)}") {
                val preset = DensityPreset.entries.firstOrNull { it.portrait == p.portraitGrid && it.landscape == p.landscapeGrid }
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
            SettingItem.Pane(x("Icon size"), Glyphs.Apps, "${(p.iconScale * 100).roundToInt()}%") {
                DensityPreview(p.portraitGrid.columns, p.portraitGrid.rows, p.iconScale, p.iconSpacing, Modifier.padding(vertical = 8.dp).height(150.dp))
                LabeledSlider(L10n.tr("Icon size"), p.iconScale, LauncherPrefs.MIN_ICON_SCALE..LauncherPrefs.MAX_ICON_SCALE, "${(p.iconScale * 100).roundToInt()}%") { v -> upd { it.copy(iconScale = v) } }
            },
            SettingItem.Pane(x("Spacing"), Glyphs.Widget, "${(p.iconSpacing * 100).roundToInt()}% · ${(vSpacing * 100).roundToInt()}%") {
                DensityPreview(p.portraitGrid.columns, p.portraitGrid.rows, p.iconScale, p.iconSpacing, Modifier.padding(vertical = 8.dp).height(170.dp),
                    spacingV = vSpacing, insetX = p.gridInsetX, insetTop = p.gridInsetTop, insetBottom = p.gridInsetBottom)
                ToggleRow(L10n.tr("Same both ways"), p.spacingV < 0f, { same -> upd { it.copy(spacingV = if (same) -1f else it.iconSpacing) } })
                LabeledSlider(L10n.tr(if (p.spacingV < 0f) "Spacing" else "Across"), p.iconSpacing, 0f..1f, "${(p.iconSpacing * 100).roundToInt()}%") { v -> upd { it.copy(iconSpacing = v) } }
                if (p.spacingV >= 0f) LabeledSlider(L10n.tr("Down"), p.spacingV, 0f..1f, "${(p.spacingV * 100).roundToInt()}%") { v -> upd { it.copy(spacingV = v) } }
                note("Compact keeps square cells; higher spreads the icons into the free space without resizing them.")
            },
            SettingItem.Pane(x("Margins"), Glyphs.Widget, "${(p.gridInsetX * 100).roundToInt()} · ${(p.gridInsetTop * 100).roundToInt()} · ${(p.gridInsetBottom * 100).roundToInt()}") {
                DensityPreview(p.portraitGrid.columns, p.portraitGrid.rows, p.iconScale, p.iconSpacing, Modifier.padding(vertical = 8.dp).height(170.dp),
                    spacingV = vSpacing, insetX = p.gridInsetX, insetTop = p.gridInsetTop, insetBottom = p.gridInsetBottom)
                LabeledSlider(L10n.tr("Height in page"), p.gridBias, 0f..1f, "${(p.gridBias * 100).roundToInt()}%") { v -> upd { it.copy(gridBias = v) } }
                LabeledSlider(L10n.tr("Sides"), p.gridInsetX, 0f..0.25f, "${(p.gridInsetX * 100).roundToInt()}%") { v -> upd { it.copy(gridInsetX = v) } }
                LabeledSlider(L10n.tr("Top"), p.gridInsetTop, 0f..0.4f, "${(p.gridInsetTop * 100).roundToInt()}%") { v -> upd { it.copy(gridInsetTop = v) } }
                LabeledSlider(L10n.tr("Bottom"), p.gridInsetBottom, 0f..0.4f, "${(p.gridInsetBottom * 100).roundToInt()}%") { v -> upd { it.copy(gridInsetBottom = v) } }
                note("Free room around the icons — leave space for the clock, or keep thumbs clear at the bottom.")
            },
            SettingItem.Pane(x("Label size"), Glyphs.Message, "${(p.labelScale * 100).roundToInt()}%") {
                LabeledSlider(L10n.tr("Label size"), p.labelScale, 0.7f..1.5f, "${(p.labelScale * 100).roundToInt()}%") { v -> upd { it.copy(labelScale = v) } }
                ToggleRow(L10n.tr("Show labels"), p.showLabels, { v -> upd { it.copy(showLabels = v) } })
            },
            SettingItem.Toggle(x("App labels"), Glyphs.Message, p.showLabels) { v -> upd { it.copy(showLabels = v) } },
            SettingItem.Toggle(x("Suggested app"), Glyphs.Bolt, p.showSuggestion) { v -> upd { it.copy(showSuggestion = v) } },
            SettingItem.Pane(x("Page turning"), Glyphs.Swipe, "${x(p.portraitPaging.label)} · ${x(p.landscapePaging.label)}") {
                Sub("Portrait")
                VisualChoice(PagingAxis.entries, p.portraitPaging, { a -> upd { it.copy(portraitPaging = a) } }, { x(it.label) }, columns = 2) { PagingPreview(it) }
                Sub("Landscape")
                VisualChoice(PagingAxis.entries, p.landscapePaging, { a -> upd { it.copy(landscapePaging = a) } }, { x(it.label) }, columns = 2) { PagingPreview(it) }
                note("Swipes across the other axis are free for gestures.")
            },
            SettingItem.Toggle(x("Always-on keyboard"), Glyphs.Message, p.alwaysKeyboard, x("A keyboard that's always open: type to find an app, like an old-school phone")) { v -> upd { it.copy(alwaysKeyboard = v) } },
            SettingItem.Toggle(x("Hide status bar"), Glyphs.Up, p.hideStatusBar, x("The clock and corner icons sit flush to the top; swipe down to peek")) { v -> upd { it.copy(hideStatusBar = v) } },
        ),
    )

    // --- Apps --------------------------------------------------------------------------------
    out += SettingCategory(
        x("Apps"),
        Glyphs.Widget,
        listOf(
            SettingItem.Pane(x("Apps on home"), Glyphs.Apps, x(p.appsOnHome.label)) {
                Chips(AppsOnHome.entries, p.appsOnHome, { it.label }) { v -> upd { it.copy(appsOnHome = v) } }
                note("\"Only ones I add\" keeps the pages for what you choose; everything else lives in the drawer. Long-press an app to add or remove it.")
            },
            SettingItem.Pane(x("Pages"), Glyphs.Swipe, x(p.pageMode.label)) {
                Chips(PageMode.entries, p.pageMode, { it.label }) { v -> upd { it.copy(pageMode = v) } }
                note("One page shows just the first page; the rest stay in the drawer.")
            },
            SettingItem.Pane(x("All apps opens as"), Glyphs.Apps, x(p.drawerStyle.label)) {
                Chips(DrawerStyle.entries, p.drawerStyle, { it.label }) { v -> upd { it.copy(drawerStyle = v) } }
                Sub("Wheel groups")
                Chips(WheelGrouping.entries, p.wheelGrouping, { it.label }) { g -> upd { it.copy(wheelGrouping = g) } }
                note("The wheel fans out around the corner you open it from.")
            },
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
            SettingItem.Pane(x("Drawer layout"), Glyphs.Widget, "${x(p.drawerView.label)} · ${x(p.drawerSort.label)}") {
                Sub("View")
                VisualChoice(DrawerView.entries, p.drawerView, { v -> upd { it.copy(drawerView = v) } }, { x(it.label) }, columns = 2, previewHeight = 110.dp) { DrawerViewPreview(it) }
                Sub("Sort by")
                Chips(DrawerSort.entries, p.drawerSort, { it.label }) { v -> upd { it.copy(drawerSort = v) } }
                Sub("Quick scrub")
                Chips(RailSide.entries, p.drawerRail, { it.label }) { v -> upd { it.copy(drawerRail = v) } }
                note("Run a finger down the edge to jump through the list.")
            },
        ),
    )

    // --- Clock -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Clock"),
        Glyphs.Clock,
        listOf(
            SettingItem.Pane(x("Position"), Glyphs.Clock, x(p.clockPosition.label)) {
                VisualChoice(ClockPosition.entries, p.clockPosition, { c -> upd { it.copy(clockPosition = c) } }, { x(it.label) }, previewHeight = 96.dp) { StripPositionPreview(it) }
                note("It can also be a tile on a page, or a corner complication.")
            },
            SettingItem.Pane(x("Health data"), Glyphs.Steps, x(p.healthSource.label)) {
                Chips(com.bos.sphere.core.data.HealthSourcePref.entries, p.healthSource, { it.label }) { v -> upd { it.copy(healthSource = v) } }
                note("Steps, floors climbed, elevation, distance and active calories can come from Health Connect, where Fitbit, Samsung Health, Garmin and the Google Fit app write their data.")
                if (!m.healthAvailable) note("Health Connect isn't available on this phone yet — install or update it from the Play Store.")
                com.bos.sphere.feature.grid.TextButtonPill(L10n.tr("Allow access"), primary = true, onClick = m.requestHealth)
            },
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
            SettingItem.Pane(x("Size"), Glyphs.Dial, "${p.clockSize.roundToInt()}") {
                LabeledSlider(L10n.tr("Size"), p.clockSize, LauncherPrefs.MIN_CLOCK_SIZE..LauncherPrefs.MAX_CLOCK_SIZE, "${p.clockSize.roundToInt()}") { v -> upd { it.copy(clockSize = v) } }
            },
            SettingItem.Pane(x("Format"), Glyphs.Clock, x(p.clockFormat.label)) {
                Chips(ClockFormat.entries, p.clockFormat, { it.label }) { c -> upd { it.copy(clockFormat = c) } }
            },
            SettingItem.Toggle(x("Day"), Glyphs.Calendar, p.clockShowDay) { v -> upd { it.copy(clockShowDay = v) } },
            SettingItem.Toggle(x("Date"), Glyphs.Calendar, p.clockShowDate) { v -> upd { it.copy(clockShowDate = v) } },
            SettingItem.Toggle(x("Weather"), Glyphs.Globe, p.clockShowWeather, x("Uses your approximate location and open-meteo.com")) { v -> upd { it.copy(clockShowWeather = v) } },
            SettingItem.Toggle(x("Fahrenheit"), Glyphs.Globe, p.weatherFahrenheit) { v -> upd { it.copy(weatherFahrenheit = v) } },
        ),
    )

    // --- Corners -----------------------------------------------------------------------------
    out += SettingCategory(
        x("Corners"),
        Glyphs.Palette,
        buildList {
            add(
                SettingItem.Pane(x("Corner style"), Glyphs.Palette, x(p.cornerStyle.label)) {
                    VisualChoice(CornerStyle.entries, p.cornerStyle, { c -> upd { it.copy(cornerStyle = c) } }, { x(it.label) }, columns = 2, previewHeight = 96.dp) { CornerStylePreview(it) }
                    note("Bubble draws a quarter-circle at each corner.")
                },
            )
            add(
                SettingItem.Pane(x("Size"), Glyphs.Dial, "${p.cornerIconSize.roundToInt()}") {
                    LabeledSlider(L10n.tr("Size"), p.cornerIconSize, LauncherPrefs.MIN_CORNER_ICON..LauncherPrefs.MAX_CORNER_ICON, "${p.cornerIconSize.roundToInt()}") { v -> upd { it.copy(cornerIconSize = v) } }
                },
            )
            add(
                SettingItem.Pane(x("Icon type"), Glyphs.Apps, x(p.cornerIconMode.label)) {
                    Chips(CornerIconMode.entries, p.cornerIconMode, { it.label }) { c -> upd { it.copy(cornerIconMode = c) } }
                    note("App icon shows the real icon of the app an action opens (Music → your music player).")
                },
            )
            add(SettingItem.Toggle(x("Follow device rotation"), Glyphs.Flip, p.cornersFollowDevice, x("Each corner stays under the same physical corner when you rotate")) { v -> upd { it.copy(cornersFollowDevice = v) } })
            Corner.entries.forEach { c ->
                add(SettingItem.Action(x(c.label), Glyphs.Palette, slotSummary(m.corners[c] ?: CornerSlot.Empty, m.appsByKey)) { m.editCorner(c) })
            }
        },
    )

    // --- Launchpad ---------------------------------------------------------------------------
    out += SettingCategory(
        "Launchpad",
        Glyphs.Bell,
        listOf(
            SettingItem.Pane(x("Left page"), Glyphs.Bell, if (p.leftPage == com.bos.sphere.core.data.LeftPage.Widget) (m.leftWidgetLabel ?: x("A widget")) else x(p.leftPage.label)) {
                VisualChoice(com.bos.sphere.core.data.LeftPage.entries, p.leftPage, { v ->
                    if (v == com.bos.sphere.core.data.LeftPage.Widget && p.leftWidgetId < 0) m.pickLeftWidget() else upd { it.copy(leftPage = v) }
                }, { x(it.label) }, columns = 3, previewHeight = 80.dp) { v ->
                    GlyphPreview(when (v) { com.bos.sphere.core.data.LeftPage.Off -> Glyphs.Close; com.bos.sphere.core.data.LeftPage.Launchpad -> Glyphs.Bell; else -> Glyphs.Widget })
                }
                if (p.leftPage == com.bos.sphere.core.data.LeftPage.Widget || p.leftWidgetId >= 0) {
                    com.bos.sphere.feature.grid.ActionRow(label = x("Choose widget"), summary = m.leftWidgetLabel ?: x("None yet"), onClick = m.pickLeftWidget)
                }
                note("One swipe right from the first page. Pick Launchpad for your notifications, or any app's widget full-screen — Orrery, Nooz, the Google feed, anything with a widget.")
            },
            SettingItem.Pane(x("Grouping & rules"), Glyphs.Message, x("Per app, sender, conversation")) {
                com.bos.sphere.ui.LaunchpadRulesPane()
            },
            SettingItem.Toggle(x("Unread strip"), Glyphs.Bell, p.launchpadRail, x("A slim strip of unread counts at the left edge")) { v -> upd { it.copy(launchpadRail = v) } },
        ),
    )

    // --- Gestures ----------------------------------------------------------------------------
    val paging = p.pagingFor(m.isLandscape)
    val pinchDensity = (m.gestures[HomeGesture.PinchIn] as? CornerItem.Action)?.id == CornerActionId.GridDenser &&
        (m.gestures[HomeGesture.PinchOut] as? CornerItem.Action)?.id == CornerActionId.GridSparser
    out += SettingCategory(
        x("Gestures"),
        Glyphs.Pinch,
        listOf<SettingItem>(
            SettingItem.Toggle(x("Pinch changes grid density"), Glyphs.Pinch, pinchDensity, x("Pinch in packs more icons in; spread opens them up")) { on ->
                if (on) {
                    m.setGesture(HomeGesture.PinchIn, CornerItem.Action(CornerActionId.GridDenser))
                    m.setGesture(HomeGesture.PinchOut, CornerItem.Action(CornerActionId.GridSparser))
                } else {
                    m.setGesture(HomeGesture.PinchIn, CornerItem.Action(CornerActionId.Search))
                    m.setGesture(HomeGesture.PinchOut, null)
                }
            },
        ) + HomeGesture.entries.filter { it.kind == GestureKind.Touch }.map { g ->
            val bound = m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: x("Nothing")
            SettingItem.Action(x(g.label), Glyphs.forGesture(g), if (g.usableWith(paging)) bound else "$bound · ${x("pages turn this way now")}") { m.editGesture(g) }
        },
    )

    // --- Motion ------------------------------------------------------------------------------
    out += SettingCategory(
        x("Motion"),
        Glyphs.Shake,
        buildList {
            add(SettingItem.Toggle(x("Motion gestures"), Glyphs.Shake, p.motionEnabled) { v -> upd { it.copy(motionEnabled = v) } })
            if (!m.motionAvailable) add(SettingItem.Action(x("No motion sensor on this device"), Glyphs.Close, "") {})
            add(SettingItem.Toggle(x("When home isn't showing"), Glyphs.Bolt, p.motionInBackground) { v -> upd { it.copy(motionInBackground = v) } })
            add(
                SettingItem.Pane(x("Sensitivity"), Glyphs.Dial, "${(p.motionSensitivity * 100).roundToInt()}%") {
                    LabeledSlider(L10n.tr("Sensitivity"), p.motionSensitivity, 0f..1f, "${(p.motionSensitivity * 100).roundToInt()}%") { v -> upd { it.copy(motionSensitivity = v) } }
                    note("Higher reacts to lighter movements (and more false triggers).")
                },
            )
            HomeGesture.entries.filter { it.kind == GestureKind.Motion }.forEach { g ->
                add(SettingItem.Action(x(g.label), Glyphs.forGesture(g), m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: x("Nothing")) { m.editGesture(g) })
            }
        },
    )

    // --- Theme -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Theme"),
        Glyphs.Palette,
        listOf(
            SettingItem.Pane(x("Mode"), Glyphs.Moon, x(p.themeMode.label)) {
                VisualChoice(ThemeMode.entries, p.themeMode, { v -> upd { it.copy(themeMode = v) } }, { x(it.label) }, previewHeight = 96.dp) { ThemeModePreview(it, androidx.compose.ui.graphics.Color(p.accentColor)) }
            },
            SettingItem.Pane(x("Accent"), Glyphs.Palette, "") {
                ColorPicker(p.accentColor, { v -> upd { it.copy(accentColor = v) } })
            },
            SettingItem.Pane(x("Icon shape"), Glyphs.Apps, x(p.iconShape.label)) {
                VisualChoice(IconShape.entries, p.iconShape, { v -> upd { it.copy(iconShape = v) } }, { x(it.label) }, columns = 3, previewHeight = 64.dp) { ThemedSampleIcon(52.dp, 1, shape = it) }
            },
            SettingItem.Pane(x("Icon colour"), Glyphs.Image, x(p.iconTint.label)) {
                VisualChoice(IconTint.entries, p.iconTint, { v -> upd { it.copy(iconTint = v) } }, { x(it.label) }, columns = 3, previewHeight = 64.dp) { ThemedSampleIcon(52.dp, 2, tint = it) }
                note("Long-press an app and choose Icon colour to tint just that app.")
            },
            SettingItem.Toggle(x("Icon plate"), Glyphs.Widget, p.iconPlate, x("A tile behind transparent icons")) { v -> upd { it.copy(iconPlate = v) } },
            SettingItem.Pane(x("Settings backdrop"), Glyphs.Moon, x(p.settingsBackdrop.label)) {
                VisualChoice(com.bos.sphere.core.data.SettingsBackdrop.entries, p.settingsBackdrop, { v -> upd { it.copy(settingsBackdrop = v) } }, { x(it.label) }, columns = 2, previewHeight = 110.dp) { b ->
                    if (b == com.bos.sphere.core.data.SettingsBackdrop.Black) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
                    else androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        com.bos.sphere.feature.grid.BackgroundLayer(p.backgroundKind, p.backgroundPreset, p.backgroundColorA, p.backgroundColorB, Modifier.fillMaxSize(), animate = false, picturePath = p.backgroundPicturePath)
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = p.settingsDim)))
                    }
                }
                if (p.settingsBackdrop == com.bos.sphere.core.data.SettingsBackdrop.Wallpaper) {
                    LabeledSlider(L10n.tr("Dimming"), p.settingsDim, 0.2f..0.95f, "${(p.settingsDim * 100).roundToInt()}%") { v -> upd { it.copy(settingsDim = v) } }
                }
                note("Settings use your wallpaper, dimmed — or pitch black.")
            },
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
        ),
    )

    // --- Search ------------------------------------------------------------------------------
    out += SettingCategory(
        "Radar",
        Glyphs.Search,
        listOf(
            SettingItem.Pane(x("Layout"), Glyphs.Apps, x(p.searchLayout.label)) {
                VisualChoice(com.bos.sphere.core.data.SearchLayout.entries, p.searchLayout, { v -> upd { it.copy(searchLayout = v) } }, { x(it.label) }, previewHeight = 140.dp) {
                    com.bos.sphere.feature.grid.SearchLayoutPreview(it)
                }
                ToggleRow(L10n.tr("Backdrop picture"), p.searchBackdrop, { v -> upd { it.copy(searchBackdrop = v) } })
                ToggleRow(L10n.tr("Keep moving"), p.searchMotion, { v -> upd { it.copy(searchMotion = v) } }, hint = L10n.tr("Filmstrip and Orbit drift slowly; off keeps them still until you move them"))
            },
            SettingItem.Pane(x("Web engine"), Glyphs.Globe, p.webEngine.label) {
                Chips(com.bos.sphere.core.data.WebEngine.entries, p.webEngine, { it.label }) { v -> upd { it.copy(webEngine = v) } }
                note("DuckDuckGo understands !bangs: type !g for Google, !gi for images, !yt for YouTube.")
            },
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
        listOf(
            SettingItem.Pane(x("Measurement"), Glyphs.Dial, x(p.indicatorMetric.label)) {
                Chips(IndicatorMetric.entries, p.indicatorMetric, { it.label }) { v ->
                    upd { it.copy(indicatorMetric = v, indicatorInvert = v == IndicatorMetric.Storage || v == IndicatorMetric.Memory) }
                }
                note("What the background reads out: low to high is red → amber → green.")
            },
            SettingItem.Pane(x("Effects"), Glyphs.Bolt, p.indicatorEffects.size.toString()) {
                IndicatorEffect.entries.forEach { e ->
                    val on = e in p.indicatorEffects
                    ToggleRow(L10n.tr(e.label), on, { v -> upd { it.copy(indicatorEffects = if (v) it.indicatorEffects + e else it.indicatorEffects - e) } })
                }
                note("Combine as many as you like.")
            },
            SettingItem.Pane(x("Colours"), Glyphs.Palette, "") {
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
                ToggleRow(L10n.tr("Flip the ramp"), p.indicatorInvert, { v -> upd { it.copy(indicatorInvert = v) } }, hint = L10n.tr("For measurements where low is good (storage used)"))
            },
            SettingItem.Pane(x("Intensity"), Glyphs.Dial, "${(p.indicatorIntensity * 100).roundToInt()}%") {
                LabeledSlider(L10n.tr("Intensity"), p.indicatorIntensity, 0f..1f, "${(p.indicatorIntensity * 100).roundToInt()}%") { v -> upd { it.copy(indicatorIntensity = v) } }
            },
            SettingItem.Pane(x("Speed"), Glyphs.Dial, "${(p.indicatorSpeed * 100).roundToInt()}%") {
                LabeledSlider(L10n.tr("Speed"), p.indicatorSpeed, 0f..1f, "${(p.indicatorSpeed * 100).roundToInt()}%") { v -> upd { it.copy(indicatorSpeed = v) } }
            },
        ),
    )

    // --- Sound -------------------------------------------------------------------------------
    out += SettingCategory(
        x("Sound"),
        Glyphs.Music,
        listOf(
            SettingItem.Toggle(x("Interface sounds"), Glyphs.Music, p.uiSounds) { v -> upd { it.copy(uiSounds = v) } },
            SettingItem.Pane(x("Volume"), Glyphs.Ring, "${(p.uiSoundVolume * 100).roundToInt()}%") {
                LabeledSlider(L10n.tr("Volume"), p.uiSoundVolume, 0f..1f, "${(p.uiSoundVolume * 100).roundToInt()}%") { v -> upd { it.copy(uiSoundVolume = v) } }
            },
        ),
    )

    // --- Sphere (only when that style is active) -------------------------------------------
    if (p.homeStyle == HomeStyle.Sphere) {
        val s = m.sphere
        fun slider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, decimals: Int, set: (Float) -> SphereParams) =
            SettingItem.Pane(title, Glyphs.Dial, if (decimals == 0) "${value.roundToInt()}" else "%.${decimals}f".format(value)) {
                LabeledSlider(title, value, range, if (decimals == 0) "${value.roundToInt()}" else "%.${decimals}f".format(value)) { s.onParams(set(it)) }
            }
        out += SettingCategory(
            "Sphere",
            Glyphs.Globe,
            listOf(
                SettingItem.Pane(x("Rows"), Glyphs.Widget, s.rowMode.name) { Chips(RowMode.entries, s.rowMode, { it.name }, s.onRowMode) },
                SettingItem.Toggle(x("Tiles"), Glyphs.Apps, s.tiles, onChange = s.onTiles),
                SettingItem.Toggle(x("Per-band spin"), Glyphs.Dial, s.rubik, onChange = s.onRubik),
                slider("Perspective", s.params.perspective, 600f..2400f, 0) { s.params.copy(perspective = it) },
                slider("Spacing", s.params.radius, 380f..980f, 0) { s.params.copy(radius = it) },
                slider("Turn", s.params.maxRotation, 0f..80f, 0) { s.params.copy(maxRotation = it) },
                slider("Depth", s.params.depth, 0f..1.6f, 2) { s.params.copy(depth = it) },
                slider("Min scale", s.params.minScale, 0.4f..1f, 2) { s.params.copy(minScale = it) },
                slider("Focus", s.params.focus, 1f..2f, 2) { s.params.copy(focus = it) },
                slider("Arc", s.params.arc, 0f..160f, 0) { s.params.copy(arc = it) },
                slider("Tilt", s.params.tilt, 0f..24f, 0) { s.params.copy(tilt = it) },
            ),
        )
    }

    // --- System ------------------------------------------------------------------------------
    out += SettingCategory(
        x("System"),
        Glyphs.Gear,
        listOf(
            SettingItem.Pane(x("Language"), Glyphs.Globe, p.language.label.let { if (p.language == UiLanguage.System) x("System") else it }) {
                UiLanguage.entries.forEach { l ->
                    ChipRowItem(if (l == UiLanguage.System) L10n.tr("System") else l.label, l == p.language) { upd { it.copy(language = l) } }
                }
            },
            SettingItem.Action(x("Set as default home"), Glyphs.Apps, "", m.system.setDefaultHome),
            SettingItem.Action(x("Notification access"), Glyphs.Bell, x("For the notifications hub"), m.system.notificationAccess),
            SettingItem.Action(x("Do Not Disturb access"), Glyphs.Moon, x("Needed for silent mode / DND actions"), m.system.dndAccess),
            SettingItem.Action(x("Android settings"), Glyphs.Gear, "", m.system.androidSettings),
        ),
    )
    // --- About -------------------------------------------------------------------------------
    out += SettingCategory(
        x("About"),
        Glyphs.Info,
        m.about.map { (k, v) -> SettingItem.Action(x(k), Glyphs.Info, v) {} },
    )
    return out
}

@Composable
private fun ChipRowItem(label: String, selected: Boolean, onClick: () -> Unit) {
    com.bos.sphere.feature.grid.ActionRow(label = label, selected = selected, onClick = onClick)
}
