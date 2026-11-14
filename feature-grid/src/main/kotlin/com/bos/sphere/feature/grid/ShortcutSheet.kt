package com.bos.sphere.feature.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.bos.sphere.core.input.KeyChord
import com.bos.sphere.core.input.Keys
import com.bos.sphere.core.input.ShortcutEditor
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

    /** The line shown after a change. */
    fun message(o: ShortcutEditor.Outcome): String = when (o) {
        is ShortcutEditor.Outcome.Saved -> L10n.tr("%s is now %s", L10n.tr(o.command.label), KeyLabels.describe(o.chord))
        is ShortcutEditor.Outcome.Conflict -> L10n.tr("That is already used by %s", L10n.tr(o.with.label))
        is ShortcutEditor.Outcome.Refused -> L10n.tr(o.reason)
        is ShortcutEditor.Outcome.Unbound -> L10n.tr("%s has no shortcut now", L10n.tr(o.command.label))
        is ShortcutEditor.Outcome.Reset -> L10n.tr("%s is back to its default", L10n.tr(o.command.label))
    }

    val extraKeys: List<Pair<String, String>> = listOf(
        "Arrow keys" to "Move between items",
        "Tab, Shift + Tab" to "Next or previous in reading order",
        "Enter" to "Open the focused item",
        "Menu key, Shift + F10" to "Open its menu (same as long-press)",
        "Esc" to "Close the top surface",
    )
}

/** The shortcut sheet: opened with F1 and from settings. Lists the bindings, lets each be changed, unbound or reset (INP-10), and says Meta shortcuts are the system's. */
@Composable
fun ShortcutSheet(registry: ShortcutRegistry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val editor = remember(registry) { ShortcutEditor(registry) }
    var capturing by remember { mutableStateOf<LauncherCommand?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { LauncherShortcuts.reload(context); kotlinx.coroutines.delay(200); version++ }
    fun done(o: ShortcutEditor.Outcome) {
        message = ShortcutSheetModel.message(o)
        capturing = null
        version++
        if (o !is ShortcutEditor.Outcome.Conflict && o !is ShortcutEditor.Outcome.Refused) LauncherShortcuts.save(context)
    }
    GlassDialog(title = L10n.tr("Keyboard shortcuts"), onDismiss = onDismiss, width = 640.dp) {
        Text(L10n.tr(KeyLabels.META_NOTE), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        message?.let { Text(it, color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
        capturing?.let { c ->
            val focus = remember { FocusRequester() }
            LaunchedEffect(c) { runCatching { focus.requestFocus() } }
            Text(
                L10n.tr("Press the new keys for %s. Esc cancels.", L10n.tr(c.label)), color = HyleColors.InkBright, fontSize = 14.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        val n = e.nativeKeyEvent
                        if (n.action != android.view.KeyEvent.ACTION_DOWN || Keys.isModifier(n.keyCode)) return@onPreviewKeyEvent n.action == android.view.KeyEvent.ACTION_DOWN
                        if (n.keyCode == Keys.ESCAPE) { capturing = null; message = null; return@onPreviewKeyEvent true }
                        done(editor.propose(c, KeyChord(n.keyCode, n.isCtrlPressed, n.isAltPressed, n.isShiftPressed, n.isMetaPressed)))
                        true
                    }
                    .focusable(),
            )
        }
        version.let { }
        for (r in ShortcutSheetModel.rows(registry)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.tr(r.label), color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(r.binding, color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.widthIn(min = 120.dp))
                SheetAction(L10n.tr("Change")) { capturing = r.command; message = null }
                SheetAction(L10n.tr("Unbind")) { done(editor.unbind(r.command)) }
                SheetAction(L10n.tr("Reset")) { done(editor.reset(r.command)) }
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

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = HyleColors.Ink, fontSize = 12.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}
