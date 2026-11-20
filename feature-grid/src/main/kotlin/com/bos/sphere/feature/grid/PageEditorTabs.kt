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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * The tab titles drawn like the reference: the selected one large and in full ink, the others small and dim. [vertical] stacks them (portrait,
 * paginated); otherwise they are one row that scrolls sideways when the titles are wider than the room (large font). Real tabs: role Tab
 * with the selected state, at least 48 dp tall.
 */
@Composable
fun PageEditorTabRow(selected: EditorTab, onSelect: (EditorTab) -> Unit, vertical: Boolean, modifier: Modifier = Modifier) {
    @Composable fun Title(t: EditorTab) {
        val on = t == selected
        Text(
            L10n.tr(t.label), fontSize = if (on) 24.sp else 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) HyleColors.InkBright else HyleColors.InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                .clickable(role = Role.Tab, onClickLabel = L10n.tr(t.label)) { onSelect(t) }
                .padding(horizontal = if (vertical) 0.dp else 10.dp, vertical = 8.dp)
                .semantics { role = Role.Tab; this.selected = on; contentDescription = L10n.tr(t.label) },
        )
    }
    if (vertical) Column(modifier.semantics { contentDescription = L10n.tr("Page editor tabs") }) { EditorTab.entries.forEach { Title(it) } }
    else Row(modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = L10n.tr("Page editor tabs") }, verticalAlignment = Alignment.CenterVertically) { EditorTab.entries.forEach { Title(it) } }
}

/** What the Settings tab can change, as one callback: the page's wallpaper switch and the Glance's content and hotspots. */
class PageEditorSettingsActions(
    val onPrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    val onDeletePage: (Int) -> Unit,
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
    onWidget: () -> Unit,
    onFolder: () -> Unit,
    onShortcut: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onGroup: (() -> Unit)?,
    settings: PageEditorSettingsActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.semantics { contentDescription = L10n.tr("Add to home") }) {
        if (page == null && tab != EditorTab.Settings) {
            Text(L10n.tr("The Glance has no apps or widgets. Pick a page."), color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
            return@Box
        }
        when (tab) {
            EditorTab.Apps -> AppsContent(apps, onHome, hasWork, isWork, drag, focusRequester, onAddApp)
            EditorTab.Widgets -> Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.tr("Widgets go on the page being edited."), color = HyleColors.InkDim, fontSize = 13.sp)
                ActionTile(L10n.tr("Choose a widget"), onWidget)
            }
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
private fun ActionTile(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).background(HyleColors.Surface.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .border(1.dp, HyleColors.InkDim.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) { Text(label, color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
}

@Composable
private fun AppsContent(
    apps: List<AppEntry>, onHome: Set<String>, hasWork: Boolean, isWork: (AppEntry) -> Boolean,
    drag: AddCarouselDrag, focusRequester: FocusRequester?, onAddApp: (AppEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.Violet),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).background(HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp)
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                    .semantics { contentDescription = L10n.tr("Filter apps") },
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(L10n.tr("Filter apps"), color = HyleColors.Ink, fontSize = 14.sp); inner() } },
            )
        }
        if (hasWork) Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<Boolean?, String>>(null to "All", false to "Personal", true to "Work").forEach { (v, l) ->
                val on = scope == v
                Text(
                    L10n.tr(l), fontSize = 12.sp, color = if (on) HyleColors.OnAccent else HyleColors.Ink,
                    modifier = Modifier.background(if (on) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .clickable { scope = v }.padding(horizontal = 12.dp, vertical = 8.dp).semantics { role = Role.Tab; selected = on },
                )
            }
        }
        val shown = remember(apps, onHome, query, scope) { AddCarouselModel.apps(apps, onHome, query, scope, isWork) }
        if (shown.isEmpty()) {
            Text(L10n.tr(if (apps.isEmpty()) "No apps to add" else "Nothing to add here"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
        } else {
            LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(shown, key = { it.key }) { app -> EditorAppTile(app, onAddApp, drag) }
            }
        }
    }
}

/** An app in the Apps tab: a tap adds it to the page being edited; a long press drags it onto a page card (the host hit-tests the drop). */
@Composable
private fun EditorAppTile(app: AppEntry, onAdd: (AppEntry) -> Unit, drag: AddCarouselDrag) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.defaultMinSize(minHeight = 48.dp)
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p -> drag.app = app; drag.pos = origin + p },
                    onDrag = { change, amount -> change.consume(); drag.pos += amount },
                    onDragEnd = { val at = drag.pos; drag.app = null; drag.onDrop?.invoke(app, at) },
                    onDragCancel = { drag.app = null },
                )
            }
            .clickable(onClickLabel = L10n.tr("Add to home")) { onAdd(app) }
            .padding(4.dp)
            .semantics { role = Role.Button; contentDescription = L10n.tr("Add to home") + ": " + app.label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(app, 44.dp)
        Text(app.label, color = HyleColors.InkBright, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsContent(page: Int?, pageCount: Int, prefs: LauncherPrefs, actions: PageEditorSettingsActions) {
    Column(Modifier.padding(horizontal = 12.dp).verticalScrollable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (page != null) {
            Text(L10n.tr("Page %d of %d", page + 1, pageCount), color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            ToggleRow(
                L10n.tr("Pages use the same wallpaper as Glance"), prefs.pagesSameAsGlance, { v -> actions.onPrefs { it.copy(pagesSameAsGlance = v) } },
                hint = L10n.tr("Applies to the pages and the scroll alike"),
            )
            ActionTile(L10n.tr("Delete page %d", page + 1), { actions.onDeletePage(page) })
        } else {
            Text(L10n.tr("Glance"), color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            ToggleRow(L10n.tr("Show music"), prefs.showMusic, { v -> actions.onPrefs { it.copy(showMusic = v) } }) // wave 38: the clock, date and weather are gone; the music line is the one toggle
            Text(L10n.tr("Hotspots"), color = HyleColors.InkDim, fontSize = 12.sp)
            Corner.entries.forEach { c ->
                ToggleRow(L10n.tr(c.label), c in prefs.glanceHotspots, { v -> actions.onPrefs { it.copy(glanceHotspots = if (v) it.glanceHotspots + c else it.glanceHotspots - c) } })
            }
        }
    }
}
