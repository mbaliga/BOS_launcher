package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wave 52 (panelWidgets): what the Widgets section needs from the app. The panel's module draws the column, the cards and the menu; the app owns the
 * real widget host (feature-grid's `LauncherWidgetHost`), the picker and the bind and configure flows, so none of that is duplicated here.
 */
class PanelWidgetsApi(
    /** True when the provider of [appWidgetId] is still installed and bound (a removed or disabled provider is false). */
    val available: (appWidgetId: Int) -> Boolean,
    /** The card's widget height in dp, from the provider's limits ([PanelWidgetSizing.heightDp]). */
    val heightDp: (appWidgetId: Int) -> Float,
    val canConfigure: (appWidgetId: Int) -> Boolean = { false },
    val configure: (appWidgetId: Int) -> Unit = {},
    /** Opens the widget picker (its bind permission and configure steps are the home's); a chosen widget comes back through [PanelWidgetStore.add]. */
    val addWidget: () -> Unit,
    /** The widget left the section: the app deletes its id from the host. */
    val released: (appWidgetId: Int) -> Unit = {},
    /** The focus-graph node of a card or the add tile (the app supplies `launcherItem`); the default is a plain focusable that takes Enter and the Menu key. */
    val itemModifier: @Composable (id: String, onActivate: () -> Unit, onMenu: () -> Unit) -> Modifier = { _, a, m -> Modifier.plainItem(a, m) },
    /** The live widget in [Modifier]'s box. */
    val render: @Composable (appWidgetId: Int, modifier: Modifier) -> Unit,
)

private fun Modifier.plainItem(onActivate: () -> Unit, onMenu: () -> Unit): Modifier = this.focusable().onKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) false else when (e.key) {
        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onActivate(); true }
        Key.Menu -> { onMenu(); true }
        else -> false
    }
}

/** Calls [onLongPress] after the long-press time without moving, and never consumes anything: the widget under the finger still gets its touches. */
private fun Modifier.observeLongPress(onLongPress: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val fired = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
            }
        }
        if (fired == null) onLongPress()
        // Let the gesture finish so a new one starts clean.
        while (true) { val ev = awaitPointerEvent(PointerEventPass.Initial); if (ev.changes.none { it.pressed }) break }
    }
}

/**
 * The Widgets section: a vertical scrolling column of full-width cards, each a real app widget at the provider's size, then an Add widget tile.
 * A long press on a card (or its three-dot button, or the Menu key or Enter) opens Remove, Move up, Move down and Widget settings. The same four are TalkBack custom actions.
 * Tags: `panelWidgets.list`, `panelWidgets.card.<id>`, `panelWidgets.menu.<id>`, `panelWidgets.add`, `panelWidgets.unavailable.<id>`.
 */
@Composable
fun PanelWidgetsSection(store: PanelWidgetStore, api: PanelWidgetsApi, modifier: Modifier = Modifier) {
    val lp = LocalLp.current
    // A provider can disappear or come back while Liftoff is in the background (an update, an uninstall): look again when the panel resumes.
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose { } }
    val list = store.widgets
    LazyColumn(
        modifier.fillMaxSize().navigationBarsPadding().testTag("panelWidgets.list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = CommsStyle.H_PAD_DP.dp, end = CommsStyle.END_PAD_DP.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (list.isEmpty()) item(key = "empty") {
            Text(L10n.tr("No widgets yet. Add one below."), color = lp.dim, fontSize = CommsStyle.EMPTY_SP.sp, fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("panelWidgets.empty"))
        }
        items(list, key = { it.appWidgetId }) { w ->
            val ok = remember(w.appWidgetId, rev) { api.available(w.appWidgetId) }
            WidgetCard(w, ok, store, api)
        }
        item(key = "add") { AddTile(api) }
    }
}

@Composable
private fun WidgetCard(w: PanelWidget, available: Boolean, store: PanelWidgetStore, api: PanelWidgetsApi) {
    val lp = LocalLp.current
    var menu by remember { mutableStateOf(false) }
    val id = w.appWidgetId
    val shape = RoundedCornerShape(20.dp)
    val canUp = store.canMove(id, -1); val canDown = store.canMove(id, +1)
    val canConfigure = available && api.canConfigure(id)
    fun remove() { menu = false; store.remove(id); api.released(id) }
    val removeLabel = L10n.tr("Remove"); val upLabel = L10n.tr("Move up"); val downLabel = L10n.tr("Move down"); val settingsLabel = L10n.tr("Widget settings")
    val actions = buildList {
        if (canUp) add(CustomAccessibilityAction(upLabel) { store.move(id, -1); true })
        if (canDown) add(CustomAccessibilityAction(downLabel) { store.move(id, +1); true })
        if (canConfigure) add(CustomAccessibilityAction(settingsLabel) { api.configure(id); true })
        add(CustomAccessibilityAction(removeLabel) { remove(); true })
    }
    val name = w.label.ifBlank { L10n.tr("Widget") }
    Column(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).clip(shape).background(lp.surface).border(0.75.dp, lp.line, shape)
            .then(api.itemModifier("panel.widget.$id", { menu = true }, { menu = true }))
            .observeLongPress { menu = true }
            .semantics { contentDescription = if (available) name else name + ", " + L10n.tr("Widget unavailable"); customActions = actions }
            .testTag("panelWidgets.card.$id"),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = lp.ink2, fontSize = CommsStyle.NOTE_SP.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(maxOf(48.dp, commsTarget())).clickable(role = Role.Button) { menu = true }
                    .semantics { contentDescription = L10n.tr("Widget options") + ": " + name }.testTag("panelWidgets.more.$id"),
                Alignment.Center,
            ) {
                Text("⋮", color = lp.dim, fontSize = 20.sp)
                DropdownMenu(menu, { menu = false }, modifier = Modifier.testTag("panelWidgets.menu.$id")) {
                    DropdownMenuItem(text = { Text(upLabel) }, enabled = canUp, onClick = { menu = false; store.move(id, -1) }, modifier = Modifier.testTag("panelWidgets.menu.up.$id"))
                    DropdownMenuItem(text = { Text(downLabel) }, enabled = canDown, onClick = { menu = false; store.move(id, +1) }, modifier = Modifier.testTag("panelWidgets.menu.down.$id"))
                    if (canConfigure) DropdownMenuItem(text = { Text(settingsLabel) }, onClick = { menu = false; api.configure(id) }, modifier = Modifier.testTag("panelWidgets.menu.settings.$id"))
                    DropdownMenuItem(text = { Text(removeLabel) }, onClick = { remove() }, modifier = Modifier.testTag("panelWidgets.menu.remove.$id"))
                }
            }
        }
        if (available) {
            api.render(id, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth().height(api.heightDp(id).coerceIn(PanelWidgetSizing.MIN_DP, PanelWidgetSizing.MAX_DP).dp).clip(RoundedCornerShape(14.dp)).testTag("panelWidgets.widget.$id"))
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).testTag("panelWidgets.unavailable.$id")) {
                Text(L10n.tr("Widget unavailable"), color = lp.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(L10n.tr("Its app was removed or turned off. Remove the card, or add the widget again."), color = lp.dim, fontSize = CommsStyle.NOTE_SP.sp)
                Box(Modifier.heightIn(min = maxOf(48.dp, commsTarget())).clickable(role = Role.Button) { remove() }.testTag("panelWidgets.unavailable.remove.$id"), Alignment.CenterStart) {
                    Text(removeLabel, color = BlueLight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun AddTile(api: PanelWidgetsApi) {
    val lp = LocalLp.current
    val shape = RoundedCornerShape(20.dp)
    val label = L10n.tr("Add widget")
    Row(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).heightIn(min = maxOf(56.dp, commsTarget())).clip(shape).border(1.dp, lp.line, shape)
            .then(api.itemModifier("panel.widget.add", { api.addWidget() }, { api.addWidget() }))
            .pointerInput(Unit) { detectTapGestures { api.addWidget() } }
            .semantics { role = Role.Button; contentDescription = label; onClick(label) { api.addWidget(); true } }
            .padding(horizontal = 16.dp).testTag("panelWidgets.add"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text("+", color = BlueLight, fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
        Text(label, color = lp.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
