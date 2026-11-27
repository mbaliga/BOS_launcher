package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AddCarouselModel
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Wave 36 (pageEditor): the tabs beside (or under) the page strip. */
enum class EditorTab(val label: String) { Apps("Apps"), Widgets("Widgets"), Folders("Folders"), Settings("Settings") }

/**
 * Wave 45 (pageEditorLayout): the tab titles as text tabs in one row: the selected one bold, full ink and underlined with the accent, the others
 * regular and dim. The row scrolls sideways when the titles are wider than the room (large font). Real tabs: role Tab with the selected state,
 * at least 48 dp tall.
 */
@Composable
fun PageEditorTabRow(selected: EditorTab, onSelect: (EditorTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = L10n.tr("Page editor tabs") }, verticalAlignment = Alignment.CenterVertically) {
        EditorTab.entries.forEach { t ->
            val on = t == selected
            Column(
                Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp).testTag("pe.tab.${t.name}")
                    .clickable(role = Role.Tab, onClickLabel = L10n.tr(t.label)) { onSelect(t) }
                    .padding(horizontal = 12.dp)
                    .semantics { role = Role.Tab; this.selected = on; contentDescription = L10n.tr(t.label) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    L10n.tr(t.label), fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) HyleColors.InkBright else HyleColors.InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (on) HyleColors.Violet else Color.Transparent))
            }
        }
    }
}

/** What the Settings tab can change, as one callback: the pages' wallpaper switch and the Glance's content and hotspots. Deleting a page lives in its card's menu only. */
class PageEditorSettingsActions(
    val onPrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    /** Wave 60 (testFlake, CFG-77): opens the Hotspots editor as a full page (the same component as Settings > Home > Corners and the Home sheet tile). Null hides the row (no caller wired). */
    val onEditHotspots: (() -> Unit)? = null,
)

/** The content under the tabs. [page] is the page being edited (null while the Glance card is the focus). */
@Composable
fun PageEditorContent(
    tab: EditorTab,
    page: Int?,
    pageCount: Int,
    prefs: LauncherPrefs,
    apps: List<AppEntry>,
    onHome: Set<String>,
    hasWork: Boolean,
    isWork: (AppEntry) -> Boolean,
    drag: AddCarouselDrag,
    focusRequester: FocusRequester?,
    onAddApp: (AppEntry) -> Unit,
    onWidget: (WidgetChoice) -> Unit,
    onFolder: () -> Unit,
    onShortcut: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onGroup: (() -> Unit)?,
    settings: PageEditorSettingsActions,
    modifier: Modifier = Modifier,
    /** W51: where the Widgets tab gets its providers; the default loads the device's (tests pass a fixed list). */
    widgetSource: @Composable () -> List<WidgetChoice>? = { rememberWidgetChoices() },
) {
    Box(modifier.semantics { contentDescription = L10n.tr("Add to home") }) {
        if (page == null && tab != EditorTab.Settings) {
            Text(L10n.tr("The Glance has no apps or widgets. Pick a page."), color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
            return@Box
        }
        when (tab) {
            EditorTab.Apps -> AppsContent(apps, onHome, hasWork, isWork, drag, focusRequester, onAddApp)
            EditorTab.Widgets -> WidgetBrowser(onPick = onWidget, choices = widgetSource()) // W51: the previews are the tab; a tap adds to the page being edited
            EditorTab.Folders -> Column(Modifier.padding(horizontal = 12.dp).verticalScrollable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(L10n.tr("New folder"), onFolder)
                ActionTile(L10n.tr("App shortcut"), onShortcut)
                ActionTile(L10n.tr("Web link"), onLink)
                ActionTile(L10n.tr("File"), onFile)
                if (onGroup != null) ActionTile(L10n.tr("App group"), onGroup)
            }
            EditorTab.Settings -> SettingsContent(page, pageCount, prefs, settings)
        }
    }
}

@Composable
private fun Modifier.verticalScrollable(): Modifier = this.verticalScroll(rememberScrollState())

@Composable
private fun ActionTile(label: String, onClick: () -> Unit, summary: String? = null, tag: String? = null) {
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).background(HyleColors.Surface.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .border(1.dp, HyleColors.InkDim.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .let { if (tag != null) it.testTag(tag) else it }
            .clickable(onClick = onClick).semantics { role = Role.Button; contentDescription = if (summary == null) label else "$label: $summary" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (summary != null) Text(summary, color = HyleColors.InkDim, fontSize = 13.sp)
        }
    }
}

/**
 * The Apps tab and the tray's Apps panel (W51): a filter field that never takes focus on its own (the keyboard rises only when it is tapped, and the host lifts
 * the panel over the keyboard), and the apps as a sideways carousel of large icons, two rows when [AppsContent]'s height allows. [focusRequester] goes to the
 * first icon (keyboard or controller focus on open), never to the field. A null [drag] means no drag-out (the tray: the page is edited directly).
 */
@Composable
internal fun AppsContent(
    apps: List<AppEntry>, onHome: Set<String>, hasWork: Boolean, isWork: (AppEntry) -> Boolean,
    drag: AddCarouselDrag?, focusRequester: FocusRequester?, onAddApp: (AppEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val shown = remember(apps, onHome, query, scope) { AddCarouselModel.apps(apps, onHome, query, scope, isWork) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().testTag("apps.browser")) {
        val fieldH = 44.dp
        val gridH = maxHeight - fieldH
        val rows = if (gridH >= APP_TILE_H * 2 + 4.dp) 2 else 1
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.Violet),
                    modifier = Modifier.weight(1f).height(40.dp).background(HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp)
                        .testTag("apps.filter").semantics { contentDescription = L10n.tr("Filter apps") },
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text(L10n.tr("Filter apps"), color = HyleColors.Ink, fontSize = 14.sp); inner() } },
                )
                if (hasWork) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<Boolean?, String>>(null to "All", false to "Personal", true to "Work").forEach { (v, l) ->
                        val on = scope == v
                        Text(
                            L10n.tr(l), fontSize = 12.sp, color = if (on) HyleColors.OnAccent else HyleColors.Ink,
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(50)).background(if (on) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f))
                                .clickable(role = Role.Tab) { scope = v }.padding(horizontal = 12.dp, vertical = 10.dp).semantics { role = Role.Tab; selected = on },
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(L10n.tr(if (apps.isEmpty()) "No apps to add" else "Nothing to add here"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            } else {
                androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                    androidx.compose.foundation.lazy.grid.GridCells.Fixed(rows), Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(shown, key = { _, a -> a.key }) { i, app -> EditorAppTile(app, onAddApp, drag, if (i == 0) focusRequester else null) }
                }
            }
        }
    }
}

private val APP_TILE_H = 84.dp

/** An app in the Apps tab: a tap adds it to the page being edited; a long press drags it onto a page card (the host hit-tests the drop). */
@Composable
private fun EditorAppTile(app: AppEntry, onAdd: (AppEntry) -> Unit, drag: AddCarouselDrag?, focusRequester: FocusRequester?) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.width(76.dp).height(APP_TILE_H).clip(RoundedCornerShape(14.dp))
            .onGloballyPositioned { origin = it.positionInWindow() }
            .let { m -> if (drag == null) m else m.pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p -> drag.app = app; drag.pos = origin + p },
                    onDrag = { change, amount -> change.consume(); drag.pos += amount },
                    onDragEnd = { val at = drag.pos; drag.app = null; drag.onDrop?.invoke(app, at) },
                    onDragCancel = { drag.app = null },
                )
            } }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .launcherItem("addbrowse.app.${app.key}", focusable = false, onActivate = { onAdd(app) }, ringRadiusDp = 14f)
            .clickable(onClickLabel = L10n.tr("Add to home")) { onAdd(app) }
            .padding(4.dp)
            .semantics { role = Role.Button; contentDescription = L10n.tr("Add to home") + ": " + app.label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(app, 48.dp)
        Text(app.label, color = HyleColors.InkBright, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsContent(page: Int?, pageCount: Int, prefs: LauncherPrefs, actions: PageEditorSettingsActions) {
    Column(Modifier.padding(horizontal = 12.dp).verticalScrollable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (page != null) {
            // Page identity: which page this is. Its name is its number (pages have no names of their own).
            Text(L10n.tr("Page %d", page + 1), color = HyleColors.InkBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("pe.settings.name"))
            Text(L10n.tr("Page %d of %d", page + 1, pageCount), color = HyleColors.InkDim, fontSize = 12.sp)
            // The wallpaper switch is global: it is shown as such, under its own heading, not as a setting of this page.
            Text(L10n.tr("All pages"), color = HyleColors.InkDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp).testTag("pe.settings.global"))
            ToggleRow(
                L10n.tr("Pages use the same wallpaper as Glance"), prefs.pagesSameAsGlance, { v -> actions.onPrefs { it.copy(pagesSameAsGlance = v) } },
                hint = L10n.tr("Applies to every page and the scroll, not only this one"),
            )
        } else {
            Text(L10n.tr("Glance"), color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            ToggleRow(L10n.tr("Show music"), prefs.showMusic, { v -> actions.onPrefs { it.copy(showMusic = v) } }) // wave 38: the clock, date and weather are gone; the music line is the one toggle
            // Wave 59 (glanceTop): the hotspot switches left this tab for the one Hotspots editor; wave 60 (testFlake, CFG-77) adds the link to it here too.
            if (actions.onEditHotspots != null) {
                Text(L10n.tr("Hotspots"), color = HyleColors.InkDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                ActionTile(
                    L10n.tr("Edit hotspots"), actions.onEditHotspots, summary = HotspotsEditorModel.summary(prefs.glanceHotspots), tag = "pe.settings.editHotspots",
                )
            }
        }
    }
}
