package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ArcShortcuts
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * W28 (arcShortcuts): the shortcut picker of Ground Control. 0 to 4 slots showing the chosen apps as plain launcher icons, with move
 * earlier / later and remove for the selected one, and the list of apps to add from (disabled once [ArcShortcuts.MAX] are chosen).
 * [onChange] gets the new list; the caller persists it.
 */
@Composable
fun ArcShortcutsPicker(apps: List<AppEntry>, keys: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val chosen = remember(keys) { ArcShortcuts.clamp(keys).filter { it in byKey } }
    var selected by remember { mutableStateOf<String?>(null) }
    val sorted = remember(apps) { apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
    Column(modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("Up to %d apps next to the four squares. Tap one to move or remove it.", ArcShortcuts.MAX),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (slot in 0 until ArcShortcuts.MAX) {
                val a = chosen.getOrNull(slot)?.let { byKey[it] }
                ActionRow(
                    label = a?.label ?: L10n.tr("Empty slot %d", slot + 1),
                    summary = null,
                    leading = if (a != null) ({ AppIcon(a, 40.dp) }) else null,
                    selected = a != null && a.key == selected,
                ) { if (a != null) selected = if (selected == a.key) null else a.key }
            }
        }
        val sel = selected?.takeIf { it in chosen }
        if (sel != null) {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButtonPill(L10n.tr("Move earlier"), enabled = chosen.indexOf(sel) > 0) { onChange(ArcShortcuts.move(chosen, sel, -1)) }
                TextButtonPill(L10n.tr("Move later"), enabled = chosen.indexOf(sel) < chosen.lastIndex) { onChange(ArcShortcuts.move(chosen, sel, +1)) }
                TextButtonPill(L10n.tr("Remove")) { onChange(ArcShortcuts.remove(chosen, sel)); selected = null }
            }
        }
        Text(L10n.tr("Add an app"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        if (chosen.size >= ArcShortcuts.MAX) {
            Text(L10n.tr("The wheel holds at most %d shortcuts.", ArcShortcuts.MAX), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp).semantics { contentDescription = L10n.tr("Apps to add") }) {
                items(sorted.filter { it.key !in chosen }, key = { it.key }) { a ->
                    ActionRow(label = a.label, leading = { AppIcon(a, 36.dp) }) { onChange(ArcShortcuts.add(chosen, a.key)) }
                }
            }
        }
    }
}
