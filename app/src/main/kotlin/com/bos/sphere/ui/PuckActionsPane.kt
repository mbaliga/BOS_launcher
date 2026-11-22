package com.bos.sphere.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.PuckSlot
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.AppIcon
import com.bos.sphere.feature.grid.AppPickerDialog
import com.bos.sphere.feature.grid.OneUiRadioPage
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.asCards
import com.bos.sphere.feature.grid.TextButtonPill

/**
 * CFG-15 (wave 38, drawerRing): the Ground Control > App drawer rows for the white shortcut ring around the Back hub: how many segments it has
 * (6, 7 or 8), the editor with one row per segment, and the labels toggle. The size and the toggle live in the puck store ([SettingsModel] carries
 * the current values and the setters), so the list rebuilds when they change.
 */
fun drawerRingItems(m: SettingsModel): List<SettingItem> = listOf(
    SettingItem.Choice(
        L10n.tr("Ring segments"), Glyphs.Dial, PuckActions.SEGMENT_CHOICES, m.puckSegments, { it.toString() },
        hint = L10n.tr("How many segments the white ring around the Back button has. Turn the ring with a finger to reach the others."),
    ) { m.setPuckSegments(it) }.asCards({ com.bos.sphere.feature.grid.SettingsArtHome.ringSegments.art(it) }, "cfg.ringSegments"),
    SettingItem.Pane(L10n.tr("Drawer shortcuts"), Glyphs.Dial, L10n.tr("%d segments", m.puckSegments)) { PuckActionsPane(m.appsByKey) },
    SettingItem.Toggle(
        L10n.tr("Show labels under glyphs"), Glyphs.Message, m.puckLabels, L10n.tr("A small name under each shortcut in the visible segments"),
    ) { m.setPuckLabels(it) },
)

/** The words for one slot, as the editor rows and the disc's labels show them. */
internal fun slotLabel(slot: PuckSlot, appsByKey: Map<String, AppEntry>): String = when (slot) {
    PuckSlot.Empty -> L10n.tr("Empty")
    is PuckSlot.Action -> L10n.tr(slot.id.label)
    is PuckSlot.App -> appsByKey[slot.key]?.label ?: L10n.tr("App not installed")
}

@Composable
fun ColumnScope.PuckActionsPane(appsByKey: Map<String, AppEntry>) {
    val context = LocalContext.current
    val store = remember { PuckActionsStore.get(context) }
    val slots by store.slots.collectAsState()
    val count by store.segments.collectAsState()
    var editing by remember { mutableIntStateOf(-1) }
    var pickingApp by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("Each segment of the ring around the hub of the all-apps disc runs a shortcut: an action, Radar, an app, or nothing."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        slots.take(count).forEachIndexed { i, slot ->
            val app = (slot as? PuckSlot.App)?.let { appsByKey[it.key] }
            ActionRow(
                label = "${i + 1}. ${slotLabel(slot, appsByKey)}",
                summary = when (slot) { PuckSlot.Empty -> L10n.tr("Tap to choose"); is PuckSlot.Action -> L10n.tr("Action"); is PuckSlot.App -> L10n.tr("App") },
                glyph = (slot as? PuckSlot.Action)?.let { Glyphs.forAction(it.id) },
                leading = app?.let { a -> { AppIcon(a, 24.dp) } },
                selected = false,
            ) { editing = i }
        }
        Row(Modifier.padding(top = 10.dp)) { TextButtonPill(L10n.tr("Reset to default")) { store.reset() } }
    }

    if (editing in 0 until count && !pickingApp) {
        // Wave 48: a choice from many is a full One UI radio page (title, one row per option), not a popup editor.
        val i = editing
        val options: List<PuckChoice> = listOf(PuckChoice.Empty, PuckChoice.PickApp) + PuckActions.AVAILABLE.map { PuckChoice.Act(it) }
        val current: PuckChoice = when (val sl = slots[i]) { PuckSlot.Empty -> PuckChoice.Empty; is PuckSlot.App -> PuckChoice.PickApp; is PuckSlot.Action -> PuckChoice.Act(sl.id) }
        OneUiRadioPage(
            title = L10n.tr("Drawer shortcut %d", i + 1), options = options, selected = current,
            label = { c -> when (c) { PuckChoice.Empty -> L10n.tr("Empty"); PuckChoice.PickApp -> L10n.tr("App..."); is PuckChoice.Act -> L10n.tr(c.id.label) } },
            detail = { c -> if (c == PuckChoice.PickApp) L10n.tr("Pick an installed app") else null },
            onSelect = { c -> when (c) { PuckChoice.Empty -> store.set(i, PuckSlot.Empty); PuckChoice.PickApp -> pickingApp = true; is PuckChoice.Act -> store.set(i, PuckSlot.Action(c.id)) } },
            onDismiss = { if (!pickingApp) editing = -1 },
        )
    }
    if (editing in 0 until count && pickingApp) {
        val i = editing
        AppPickerDialog(
            title = L10n.tr("Drawer shortcut %d", i + 1),
            apps = appsByKey.values.toList(), suggested = emptySet(),
            onPick = { a -> store.set(i, PuckSlot.App(a.key)); pickingApp = false; editing = -1 },
            onDismiss = { pickingApp = false },
        )
    }
}

/** Wave 48: what a drawer shortcut page offers (a row each). */
private sealed interface PuckChoice {
    data object Empty : PuckChoice
    data object PickApp : PuckChoice
    data class Act(val id: CornerActionId) : PuckChoice
}
