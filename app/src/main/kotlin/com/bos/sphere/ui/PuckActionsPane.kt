package com.bos.sphere.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.PuckLayout
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.TextButtonPill

/** CFG-15: Ground Control > Puck, where the user chooses the actions on the puck ring and their order. */
fun puckCategory(): SettingCategory = SettingCategory(
    L10n.tr("Puck"),
    Glyphs.Dial,
    listOf(
        SettingItem.Pane(L10n.tr("Puck actions"), Glyphs.Dial, L10n.tr("Which actions sit on the ring, and in what order")) { PuckActionsPane() },
    ),
)

@Composable
fun ColumnScope.PuckActionsPane() {
    val context = LocalContext.current
    val store = remember { PuckActionsStore.get(context) }
    val actions by store.actions.collectAsState()
    var selected by remember { mutableIntStateOf(-1) }
    val cfg = LocalConfiguration.current
    // INP-19: how many buttons this window's puck can draw without overlap.
    val fit = PuckLayout.compute(minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat(), PuckActions.MAX).capacity

    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("The actions on the all-apps puck's ring, in order. This window fits %d of up to %d.", fit, PuckActions.MAX),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        if (actions.isEmpty()) {
            Text(L10n.tr("No actions: the ring is empty."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        actions.forEachIndexed { i, id ->
            ActionRow(
                label = "${i + 1}. ${L10n.tr(id.label)}",
                summary = if (i >= fit) L10n.tr("Does not fit in this window") else null,
                glyph = Glyphs.forAction(id),
                selected = i == selected,
            ) { selected = if (selected == i) -1 else i }
        }
        if (selected in actions.indices) {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButtonPill(L10n.tr("Move up"), enabled = selected > 0) {
                    store.set(PuckActions.move(actions, selected, -1)); selected -= 1
                }
                TextButtonPill(L10n.tr("Move down"), enabled = selected < actions.lastIndex) {
                    store.set(PuckActions.move(actions, selected, +1)); selected += 1
                }
                TextButtonPill(L10n.tr("Remove")) { store.set(PuckActions.remove(actions, selected)); selected = -1 }
            }
        }
        Text(L10n.tr("Add an action"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        if (actions.size >= PuckActions.MAX) {
            Text(L10n.tr("The puck holds at most %d actions.", PuckActions.MAX), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            PuckActions.candidates(actions).forEach { id ->
                ActionRow(L10n.tr(id.label), null, Glyphs.forAction(id)) { store.set(PuckActions.add(actions, id)) }
            }
        }
        Row(Modifier.padding(top = 10.dp)) { TextButtonPill(L10n.tr("Reset to default")) { store.reset(); selected = -1 } }
    }
}
