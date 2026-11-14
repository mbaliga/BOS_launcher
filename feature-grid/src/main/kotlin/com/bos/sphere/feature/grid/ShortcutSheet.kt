package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.KeyLabels
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry

/** One printed row of the sheet. */
data class ShortcutRow(val command: LauncherCommand, val label: String, val binding: String)

/** The rows the sheet lists: every registry entry with its current binding (INP-04). */
object ShortcutSheetModel {
    fun rows(registry: ShortcutRegistry): List<ShortcutRow> =
        registry.all().map { (c, chord) -> ShortcutRow(c, c.label, KeyLabels.describe(chord)) }

    val extraKeys: List<Pair<String, String>> = listOf(
        "Arrow keys" to "Move between items",
        "Tab, Shift + Tab" to "Next or previous in reading order",
        "Enter" to "Open the focused item",
        "Menu key, Shift + F10" to "Open its menu (same as long-press)",
        "Esc" to "Close the top surface",
    )
}

/** The shortcut sheet: opened with F1 and from settings. Lists the bindings and says Meta shortcuts are the system's. */
@Composable
fun ShortcutSheet(registry: ShortcutRegistry, onDismiss: () -> Unit) {
    GlassDialog(title = L10n.tr("Keyboard shortcuts"), onDismiss = onDismiss, width = 640.dp) {
        Text(L10n.tr(KeyLabels.META_NOTE), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        for (r in ShortcutSheetModel.rows(registry)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L10n.tr(r.label), color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(r.binding, color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.widthIn(min = 120.dp))
            }
        }
        Text(L10n.tr("Moving around"), color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        for ((k, v) in ShortcutSheetModel.extraKeys) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L10n.tr(v), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(k, color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.widthIn(min = 120.dp))
            }
        }
    }
}
