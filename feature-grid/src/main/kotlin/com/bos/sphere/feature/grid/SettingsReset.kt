package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wave 56 (settingsCore), CFG-30: what a reset needs from the app: a snapshot of the whole state before anything changes, and a way back to it.
 * The app backs this with the state service (`StateService.snapshot` and `restore`), the same snapshots the Backup page lists; a test backs it with a fake.
 */
interface SettingsSnapshots {
    /** Takes a snapshot of the live state and returns its id; null when it could not be taken. Blocking file work: the flow calls it off the main thread. */
    fun take(reason: String): String?
    /** Puts the state of snapshot [id] back; false when that snapshot is gone or the restore failed. */
    fun restore(id: String): Boolean
}

/** The line shown after a reset: what happened, and the snapshot that Undo restores (null = nothing to undo to). */
data class ResetResult(val message: String, val snapshotId: String?)

/** The rules of one reset, pure over [SettingsSnapshots] so they run on the JVM. */
object SettingsResetFlow {
    /** English keys. */
    const val PAGE_TITLE = "Reset this page to defaults"
    const val PAGE_BODY = "Puts the settings on this page back to their defaults. Your home layout, hidden apps, shortcuts and Comms rules are not touched. A snapshot is taken first, so you can undo it."
    const val ALL_TITLE = "Reset all settings"
    const val ALL_BODY = "Puts every setting back to its default. Your home layout, hidden apps, shortcuts, Comms rules and workspaces are not touched. A snapshot is taken first, so you can undo it."
    const val CONFIRM = "Reset"
    const val DONE = "Settings were reset to their defaults."
    const val FAILED = "No snapshot could be taken, so nothing was reset."
    const val UNDONE = "Settings restored."
    const val UNDO_FAILED = "Could not restore the snapshot."

    /** The synthetic action row a page reset runs through: the same confirm dialog and the same snapshot-then-undo path as every other reset. */
    fun pageAction(category: SettingCategory): SettingItem.Action? = category.reset?.let { r ->
        SettingItem.Action(PAGE_TITLE, Glyphs.Sliders, "", confirm = PAGE_BODY, confirmLabel = CONFIRM, onClick = r).snapshotFirst()
    }

    /**
     * Snapshot first, then [apply]. When [snapshots] is null (not wired) the change just happens. When the snapshot cannot be taken nothing is changed
     * and the result says so.
     */
    suspend fun run(snapshots: SettingsSnapshots?, reason: String, apply: () -> Unit): ResetResult {
        if (snapshots == null) { apply(); return ResetResult(L10n.tr(DONE), null) }
        val id = withContext(Dispatchers.IO) { snapshots.take(reason) } ?: return ResetResult(L10n.tr(FAILED), null)
        apply()
        return ResetResult(L10n.tr(DONE), id)
    }

    /** Undo: restores the snapshot of [r]; the new message says what happened. Null when there is nothing to undo. */
    suspend fun undo(snapshots: SettingsSnapshots?, r: ResetResult): ResetResult? {
        val id = r.snapshotId ?: return null
        val ok = withContext(Dispatchers.IO) { snapshots?.restore(id) == true }
        return ResetResult(L10n.tr(if (ok) UNDONE else UNDO_FAILED), null)
    }
}

/** The line at the bottom of a page that closes a category: "Reset this page to defaults", a flat text row in the accent colour. */
@Composable
internal fun OneUiResetRow(onClick: () -> Unit) {
    val label = L10n.tr(SettingsResetFlow.PAGE_TITLE)
    Box(
        Modifier.fillMaxWidth().padding(top = OneUi.GroupGap).heightIn(min = 56.dp).testTag(OneUiTags.RESET_ROW)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = OneUi.RowPad).semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) { Text(label, color = HyleColors.Violet, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

/** The snackbar-like line after a reset: flat, a hairline above it, the message at the start and "Undo" as accent text at the end (nothing to undo = no button). */
@Composable
internal fun OneUiUndoRow(result: ResetResult, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(HyleColors.Surface).testTag(OneUiTags.UNDO_ROW).semantics { liveRegion = LiveRegionMode.Polite }) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = OneUi.RowPad), verticalAlignment = Alignment.CenterVertically) {
            Text(result.message, color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (result.snapshotId != null) Text(
                L10n.tr("Undo"), color = HyleColors.Violet, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag(OneUiTags.UNDO_ROW + ".undo").heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onUndo).padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            )
        }
    }
}
