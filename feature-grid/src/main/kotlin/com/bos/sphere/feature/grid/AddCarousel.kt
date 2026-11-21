package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.defaultMinSize
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
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

enum class AddTab(val label: String) { Apps("Apps"), Widgets("Widgets"), Folders("Folders"), Shortcuts("Shortcuts") }

/** Which app is being dragged out of the add carousel and where the finger is, in window px (the host draws the ghost icon). */
class AddCarouselDrag {
    var app by mutableStateOf<AppEntry?>(null)
    var pos by mutableStateOf(Offset.Zero)
    /** Wave 36: the page editor sets this; it is told where (window px) a dragged app was let go. Also the seam the tests use to drop without a gesture. */
    var onDrop: ((AppEntry, Offset) -> Unit)? = null
}

/**
 * HOM-04 / HOM-05: the add carousel. Apps hidden behind it by default: a horizontally scrolling strip under the edit
 * card with the tabs Apps, Widgets, Folders and Shortcuts. Apps come from the full visible inventory (hidden apps stay
 * hidden), alphabetical, with type-to-filter and Personal / Work chips; a tap adds the app to the first free cell of the
 * current page and a long-press drag onto the card does the same. The other tabs open the same pickers the home items
 * sheet uses.
 */
@Composable
fun AddCarousel(
    apps: List<AppEntry>,
    onHome: Set<String>,
    hasWork: Boolean,
    isWork: (AppEntry) -> Boolean,
    onAddApp: (AppEntry) -> Unit,
    onWidget: () -> Unit,
    onFolder: () -> Unit,
    onShortcut: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    cardRect: () -> EdgeRect,
    drag: AddCarouselDrag,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    /** WSP-05: a saved app group becomes a home item from the Shortcuts tab. */
    onGroup: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(AddTab.Apps) }
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf<Boolean?>(null) } // null all, false personal, true work
    Column(modifier.semantics { contentDescription = L10n.tr("Add to home") }, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AddTab.entries.forEach { t ->
                val on = t == tab
                Text(
                    L10n.tr(t.label), fontSize = if (on) 16.sp else 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) HyleColors.InkBright else HyleColors.InkDim,
                    modifier = Modifier.clickable { tab = t }.padding(horizontal = 6.dp, vertical = 6.dp)
                        .semantics { role = Role.Tab; selected = on; contentDescription = L10n.tr(t.label) },
                )
            }
        }
        when (tab) {
            AddTab.Apps -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.Violet),
                        modifier = Modifier.weight(1f).background(HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp)
                            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                            .semantics { contentDescription = L10n.tr("Filter apps") },
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(L10n.tr("Filter apps"), color = HyleColors.Ink, fontSize = 14.sp); inner() } },
                    )
                    if (hasWork) {
                        listOf<Pair<Boolean?, String>>(null to "All", false to "Personal", true to "Work").forEach { (v, l) ->
                            val on = scope == v
                            Text(
                                L10n.tr(l), fontSize = 12.sp, color = if (on) HyleColors.OnAccent else HyleColors.Ink, // the normal ink: the dim ink on its own tint is under 4.5 to 1 in the dark theme (NFR-15)
                                modifier = Modifier.background(if (on) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50))
                                    .clickable { scope = v }.padding(horizontal = 10.dp, vertical = 6.dp)
                                    .semantics { role = Role.Tab; selected = on },
                            )
                        }
                    }
                }
                val shown = remember(apps, onHome, query, scope) { AddCarouselModel.apps(apps, onHome, query, scope, isWork) }
                if (shown.isEmpty()) {
                    Text(L10n.tr(if (apps.isEmpty()) "No apps to add" else "Nothing to add here"), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown, key = { it.key }) { app -> AppCard(app, onAddApp, cardRect, drag, if (compact) 40.dp else 48.dp) }
                    }
                }
            }
            AddTab.Widgets -> ActionRow(listOf(L10n.tr("Choose a widget") to onWidget))
            AddTab.Folders -> ActionRow(listOf(L10n.tr("New folder") to onFolder))
            AddTab.Shortcuts -> ActionRow(listOf(L10n.tr("App shortcut") to onShortcut, L10n.tr("Web link") to onLink, L10n.tr("File") to onFile) + listOfNotNull(onGroup?.let { L10n.tr("App group") to it }))
        }
    }
}

@Composable
private fun ActionRow(entries: List<Pair<String, () -> Unit>>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries) { (label, run) ->
            Box(
                Modifier.size(width = 120.dp, height = 72.dp).background(HyleColors.Surface.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                    .border(1.dp, HyleColors.InkDim.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .clickable(onClick = run).semantics { role = Role.Button; contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = HyleColors.InkBright, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun AppCard(app: AppEntry, onAdd: (AppEntry) -> Unit, cardRect: () -> EdgeRect, drag: AddCarouselDrag, iconSize: androidx.compose.ui.unit.Dp) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.width(72.dp)
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p -> drag.app = app; drag.pos = origin + p },
                    onDrag = { change, amount -> change.consume(); drag.pos += amount },
                    onDragEnd = {
                        val r = cardRect(); val p = drag.pos
                        drag.app = null
                        if (p.x in r.left..r.right && p.y in r.top..r.bottom) onAdd(app)
                    },
                    onDragCancel = { drag.app = null },
                )
            }
            .clickable(onClickLabel = L10n.tr("Add to home")) { onAdd(app) }
            .padding(4.dp)
            .semantics { role = Role.Button; contentDescription = L10n.tr("Add to home") + ": " + app.label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(app, iconSize)
        Text(app.label, color = HyleColors.InkBright, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * HOM-01: the quiet hint an empty home shows in its middle. Tapping it opens edit mode with the add carousel focused.
 * HOM-22: with [onShowRecents] a second line, "Or show recent apps", switches the home landing to Recent apps (one tap away).
 */
@Composable
fun EmptyHomeHint(onOpen: () -> Unit, modifier: Modifier = Modifier, onShowRecents: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Text(
                L10n.tr("Long press to add apps"), color = HyleColors.Ink, fontSize = 15.sp,
                modifier = Modifier.background(HyleColors.InkDim.copy(alpha = 0.10f), RoundedCornerShape(50)).clickable(onClickLabel = L10n.tr("Add to home"), onClick = onOpen)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .semantics { role = Role.Button; contentDescription = L10n.tr("Long press to add apps") },
            )
            if (onShowRecents != null) {
                Text(
                    L10n.tr("Or show recent apps"), color = HyleColors.InkDim, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = L10n.tr("Show recent apps"), role = Role.Button, onClick = onShowRecents)
                        .defaultMinSize(minHeight = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp)
                        .semantics { role = Role.Button; contentDescription = L10n.tr("Or show recent apps") },
                )
            }
        }
    }
}
