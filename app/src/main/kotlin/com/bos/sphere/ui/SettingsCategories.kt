package com.bos.sphere.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
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
    val appsByKey: Map<String, AppEntry>,
    val isLandscape: Boolean,
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

private fun gridLabel(g: GridSpec) = "${g.columns} × ${g.rows}"

private fun note(text: String): @Composable () -> Unit = { Text(text, color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp)) }

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) { ChipRow(options, selected, label, onSelect) }
}

fun buildSettingsCategories(m: SettingsModel): List<SettingCategory> {
    val p = m.prefs
    val out = ArrayList<SettingCategory>()

    // --- This page ---------------------------------------------------------------------------
    val page = m.pages.getOrNull(m.pageIndex) ?: HomePage()
    val layout = page.layout
    val pageItems = buildList {
        add(
            SettingItem.Pane("Layout", Glyphs.Apps, layout.shape.label) {
                Chips(PageShape.entries, layout.shape, { it.label }) { m.setPageLayout(m.pageIndex, layout.copy(shape = it)) }
                note(
                    when (layout.shape) {
                        PageShape.Grid -> "Rows and columns of icons."
                        PageShape.Circle -> "A centre app with rings of 6, 12 and 18 around it."
                        PageShape.Coverflow -> "A strip you scrub sideways; tap the centre app to open it."
                    },
                )()
            },
        )
        when (layout.shape) {
            PageShape.Grid -> {
                val custom = layout.portrait != null || layout.landscape != null
                val pg = layout.portrait ?: p.portraitGrid
                val lg = layout.landscape ?: p.landscapeGrid
                add(
                    SettingItem.Pane(
                        "Density",
                        Glyphs.Widget,
                        if (custom) "This page: ${gridLabel(pg)} · ${gridLabel(lg)}" else "Home default: ${gridLabel(pg)} · ${gridLabel(lg)}",
                    ) {
                        ToggleRow("Follow home default", !custom, { follow ->
                            m.setPageLayout(
                                m.pageIndex,
                                if (follow) layout.copy(portrait = null, landscape = null) else layout.copy(portrait = pg, landscape = lg),
                            )
                        })
                        if (custom) {
                            Text("Portrait", color = HyleColors.Ink, fontSize = 12.sp)
                            Stepper("Columns", pg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(columns = it), landscape = lg)) }
                            Stepper("Rows", pg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(portrait = pg.copy(rows = it), landscape = lg)) }
                            Text("Landscape", color = HyleColors.Ink, fontSize = 12.sp)
                            Stepper("Columns", lg.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(columns = it), portrait = pg)) }
                            Stepper("Rows", lg.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { m.setPageLayout(m.pageIndex, layout.copy(landscape = lg.copy(rows = it), portrait = pg)) }
                        }
                        note("Apps that no longer fit move to the next page.")()
                    },
                )
            }
            PageShape.Circle -> add(
                SettingItem.Pane("Rings", Glyphs.Dial, "${layout.rings} · holds ${PageLayout.circleCapacity(layout.rings)}") {
                    Stepper("Rings", layout.rings, PageLayout.MIN_RINGS..PageLayout.MAX_RINGS) { m.setPageLayout(m.pageIndex, layout.copy(rings = it)) }
                },
            )
            PageShape.Coverflow -> Unit
        }
        add(
            SettingItem.Toggle("Clock tile on this page", Glyphs.Clock, HomeTiles.CLOCK in page.keys, "Moves it here if it is on another page") { on ->
                m.setClockTile(m.pageIndex, on)
            },
        )
        add(SettingItem.Action("Add a page after this", Glyphs.Plus, "") { m.addPageAfter(m.pageIndex) })
        if (m.pages.size > 1) add(SettingItem.Action("Delete this page", Glyphs.Close, "Apps move to the previous page") { m.deletePage(m.pageIndex) })
    }
    out += SettingCategory("Page ${m.pageIndex + 1}", Glyphs.Widget, pageItems)

    // --- Home --------------------------------------------------------------------------------
    out += SettingCategory(
        "Home",
        Glyphs.Apps,
        listOf(
            SettingItem.Pane("Style", Glyphs.Apps, if (p.homeStyle == HomeStyle.Grid) "Pages" else "Sphere") {
                Chips(HomeStyle.entries, p.homeStyle, { if (it == HomeStyle.Grid) "Pages" else "Sphere" }) { s -> m.updatePrefs { it.copy(homeStyle = s) } }
            },
            SettingItem.Pane("Default density", Glyphs.Widget, "Portrait ${gridLabel(p.portraitGrid)} · Landscape ${gridLabel(p.landscapeGrid)}") {
                val preset = DensityPreset.entries.firstOrNull { it.portrait == p.portraitGrid && it.landscape == p.landscapeGrid }
                Chips(DensityPreset.entries, preset ?: DensityPreset.Default, { it.label }) { d -> m.updatePrefs { it.copy(portraitGrid = d.portrait, landscapeGrid = d.landscape) } }
                Text("Portrait", color = HyleColors.Ink, fontSize = 12.sp)
                Stepper("Columns", p.portraitGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> m.updatePrefs { it.copy(portraitGrid = it.portraitGrid.copy(columns = v)) } }
                Stepper("Rows", p.portraitGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> m.updatePrefs { it.copy(portraitGrid = it.portraitGrid.copy(rows = v)) } }
                Text("Landscape", color = HyleColors.Ink, fontSize = 12.sp)
                Stepper("Columns", p.landscapeGrid.columns, GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) { v -> m.updatePrefs { it.copy(landscapeGrid = it.landscapeGrid.copy(columns = v)) } }
                Stepper("Rows", p.landscapeGrid.rows, GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) { v -> m.updatePrefs { it.copy(landscapeGrid = it.landscapeGrid.copy(rows = v)) } }
                note("Pages with their own density keep it.")()
            },
            SettingItem.Pane("Icon size", Glyphs.Apps, "${(p.iconScale * 100).roundToInt()}% of the cell") {
                LabeledSlider("Icon size", p.iconScale, LauncherPrefs.MIN_ICON_SCALE..LauncherPrefs.MAX_ICON_SCALE, "${(p.iconScale * 100).roundToInt()}%") { v ->
                    m.updatePrefs { it.copy(iconScale = v) }
                }
            },
            SettingItem.Toggle("App labels", Glyphs.Message, p.showLabels) { v -> m.updatePrefs { it.copy(showLabels = v) } },
            SettingItem.Toggle("Suggested app", Glyphs.Bolt, p.showSuggestion) { v -> m.updatePrefs { it.copy(showSuggestion = v) } },
            SettingItem.Pane("Page turning", Glyphs.Swipe, "Portrait ${p.portraitPaging.label} · Landscape ${p.landscapePaging.label}") {
                Text("Portrait", color = HyleColors.Ink, fontSize = 12.sp)
                Chips(PagingAxis.entries, p.portraitPaging, { it.label }) { a -> m.updatePrefs { it.copy(portraitPaging = a) } }
                Text("Landscape", color = HyleColors.Ink, fontSize = 12.sp)
                Chips(PagingAxis.entries, p.landscapePaging, { it.label }) { a -> m.updatePrefs { it.copy(landscapePaging = a) } }
                note("Swipes across the other axis are free for gestures.")()
            },
        ),
    )

    // --- Corners -----------------------------------------------------------------------------
    out += SettingCategory(
        "Corners",
        Glyphs.Palette,
        buildList {
            add(
                SettingItem.Pane("Corner style", Glyphs.Palette, p.cornerStyle.label) {
                    Chips(CornerStyle.entries, p.cornerStyle, { it.label }) { c -> m.updatePrefs { it.copy(cornerStyle = c) } }
                    note("Bubble draws a quarter-circle at each corner.")()
                },
            )
            add(SettingItem.Toggle("Follow device rotation", Glyphs.Flip, p.cornersFollowDevice) { v -> m.updatePrefs { it.copy(cornersFollowDevice = v) } })
            Corner.entries.forEach { c ->
                add(SettingItem.Action(c.label, Glyphs.Palette, slotSummary(m.corners[c] ?: CornerSlot.Empty, m.appsByKey)) { m.editCorner(c) })
            }
        },
    )

    // --- Gestures ----------------------------------------------------------------------------
    val paging = p.pagingFor(m.isLandscape)
    out += SettingCategory(
        "Gestures",
        Glyphs.Pinch,
        HomeGesture.entries.filter { it.kind == GestureKind.Touch }.map { g ->
            val bound = m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: "Nothing"
            SettingItem.Action(g.label, Glyphs.forGesture(g), if (g.usableWith(paging)) bound else "$bound · pages turn this way now") { m.editGesture(g) }
        },
    )

    // --- Motion ------------------------------------------------------------------------------
    out += SettingCategory(
        "Motion",
        Glyphs.Shake,
        buildList {
            add(SettingItem.Toggle("Motion gestures", Glyphs.Shake, p.motionEnabled) { v -> m.updatePrefs { it.copy(motionEnabled = v) } })
            if (!m.motionAvailable) add(SettingItem.Action("No motion sensor on this device", Glyphs.Close, "") {})
            add(
                SettingItem.Toggle("When home isn't showing", Glyphs.Bolt, p.motionInBackground) { v -> m.updatePrefs { it.copy(motionInBackground = v) } },
            )
            add(
                SettingItem.Pane("Sensitivity", Glyphs.Dial, "${(p.motionSensitivity * 100).roundToInt()}%") {
                    LabeledSlider("Sensitivity", p.motionSensitivity, 0f..1f, "${(p.motionSensitivity * 100).roundToInt()}%") { v -> m.updatePrefs { it.copy(motionSensitivity = v) } }
                    note("Higher reacts to lighter movements (and more false triggers).")()
                },
            )
            HomeGesture.entries.filter { it.kind == GestureKind.Motion }.forEach { g ->
                add(SettingItem.Action(g.label, Glyphs.forGesture(g), m.gestures[g]?.let { itemLabel(it, m.appsByKey) } ?: "Nothing") { m.editGesture(g) })
            }
        },
    )

    // --- Look --------------------------------------------------------------------------------
    out += SettingCategory(
        "Look",
        Glyphs.Image,
        listOf(
            SettingItem.Pane("Clock", Glyphs.Clock, p.clockPosition.label) {
                Chips(ClockPosition.entries, p.clockPosition, { it.label }) { c -> m.updatePrefs { it.copy(clockPosition = c) } }
                note("It can also be a tile on a page (Page → Clock tile) or a corner complication (Corners → Complication → Time).")()
            },
            SettingItem.Toggle("Network dots", Glyphs.Globe, p.networkDots, "Green good · red poor · grey airplane · dark offline") { v -> m.updatePrefs { it.copy(networkDots = v) } },
            SettingItem.Toggle("Show wallpaper", Glyphs.Image, p.showWallpaper) { v -> m.updatePrefs { it.copy(showWallpaper = v) } },
            SettingItem.Pane("All apps", Glyphs.Apps, "${p.drawerStyle.label} · ${p.wheelGrouping.label}") {
                Chips(DrawerStyle.entries, p.drawerStyle, { it.label }) { d -> m.updatePrefs { it.copy(drawerStyle = d) } }
                Text("Wheel groups", color = HyleColors.Ink, fontSize = 12.sp)
                Chips(WheelGrouping.entries, p.wheelGrouping, { it.label }) { g -> m.updatePrefs { it.copy(wheelGrouping = g) } }
                note("The wheel fans out around the corner you open it from.")()
            },
        ),
    )

    // --- Sound -------------------------------------------------------------------------------
    out += SettingCategory(
        "Sound",
        Glyphs.Music,
        listOf(
            SettingItem.Toggle("Interface sounds", Glyphs.Music, p.uiSounds) { v -> m.updatePrefs { it.copy(uiSounds = v) } },
            SettingItem.Pane("Volume", Glyphs.Ring, "${(p.uiSoundVolume * 100).roundToInt()}%") {
                LabeledSlider("Volume", p.uiSoundVolume, 0f..1f, "${(p.uiSoundVolume * 100).roundToInt()}%") { v -> m.updatePrefs { it.copy(uiSoundVolume = v) } }
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
                SettingItem.Pane("Rows", Glyphs.Widget, s.rowMode.name) { Chips(RowMode.entries, s.rowMode, { it.name }, s.onRowMode) },
                SettingItem.Toggle("Tiles", Glyphs.Apps, s.tiles, onChange = s.onTiles),
                SettingItem.Toggle("Per-band spin", Glyphs.Dial, s.rubik, onChange = s.onRubik),
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
        "System",
        Glyphs.Gear,
        listOf(
            SettingItem.Action("Set as default home", Glyphs.Apps, "", m.system.setDefaultHome),
            SettingItem.Action("Notification access", Glyphs.Bell, "For the notifications hub", m.system.notificationAccess),
            SettingItem.Action("Do Not Disturb access", Glyphs.Moon, "Needed for silent mode / DND actions", m.system.dndAccess),
            SettingItem.Action("Android settings", Glyphs.Gear, "", m.system.androidSettings),
        ),
    )
    return out
}
