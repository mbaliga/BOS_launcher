package com.bos.sphere.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ImportReport
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.SnapshotStore
import com.bos.sphere.core.data.StateCodec
import com.bos.sphere.core.data.StateDocument
import com.bos.sphere.core.data.StateFormatException
import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.TextButtonPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** CFG-01 / CFG-02 / CFG-04 / CFG-11: the Backup category of Ground Control. */
fun backupCategory(): SettingCategory = SettingCategory(
    L10n.tr("Backup"),
    Glyphs.Info,
    listOf(
        SettingItem.Pane(L10n.tr("Export, import and restore"), Glyphs.Info, L10n.tr("One file, or the last five snapshots")) { BackupPane() },
        crashReportsItem(),
    ),
)

private const val MAX_FILE_BYTES = 8 * 1024 * 1024

private fun readText(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw StateFormatException("Could not open the file")
    if (bytes.size > MAX_FILE_BYTES) throw StateFormatException("The file is too large to be a Liftoff setup")
    return bytes.toString(Charsets.UTF_8)
}

private fun writeText(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw StateFormatException("Could not write the file")
}

private fun describe(r: ImportReport): String {
    val parts = ArrayList<String>()
    parts += L10n.tr("Imported: %s.", r.sections.filter { it !in setOf(StateSchema.K_WIDGETS) }.joinToString(", "))
    if (r.placeholders.isNotEmpty()) parts += L10n.tr("%d apps are not installed here; they return to their place when installed.", r.placeholders.size)
    if (r.pendingWidgets.isNotEmpty()) parts += L10n.tr("%d widgets re-bind when you add them again.", r.pendingWidgets.size)
    if (r.fromNewer) parts += L10n.tr("The file is from a newer version; unknown parts were kept.")
    return parts.joinToString(" ")
}

@Composable
fun ColumnScope.BackupPane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { StateService.get(context) }
    var snapshots by remember { mutableStateOf(service.snapshots.list()) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) } // (file text, summary)

    fun fail(e: Throwable) { message = L10n.tr("That did not work: %s", e.message ?: e.javaClass.simpleName) }

    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { writeText(context, uri, service.exportText()) } }
                .onSuccess { message = L10n.tr("Setup exported. It holds no notifications, usage history or contacts.") }.onFailure(::fail)
        }
    }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { writeText(context, uri, service.exportSettingsText()) } }
                .onSuccess { message = L10n.tr("Settings exported.") }.onFailure(::fail)
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = readText(context, uri)
                    val dec = StateCodec.decode(StateDocument.parse(text)) // validates before anything is touched
                    val what = dec.present.filter { it !in setOf(StateSchema.K_FORMAT, StateSchema.K_VERSION, StateSchema.K_EXPORTED_AT, StateSchema.K_APP) }
                    text to L10n.tr("This file replaces: %s. A snapshot of the current setup is taken first.", what.joinToString(", "))
                }
            }.onSuccess { pending = it }.onFailure(::fail)
        }
    }

    Column(Modifier.padding(horizontal = 4.dp)) {
        note(L10n.tr("Your whole setup is one file: layout, settings, corners, gestures, Launchpad rules. Notifications, usage history, search indexes and contacts are never included."))
        ActionRow(L10n.tr("Export setup…"), L10n.tr("Everything in one file"), Glyphs.Info) { exportAll.launch("liftoff-setup.json") }
        ActionRow(L10n.tr("Export settings only…"), L10n.tr("A separate settings document"), Glyphs.Info) { exportSettings.launch("liftoff-settings.json") }
        ActionRow(L10n.tr("Import setup…"), L10n.tr("From a Liftoff file; snapshots first"), Glyphs.Info) { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }

        pending?.let { (text, summary) ->
            note(summary)
            TextButtonPill(L10n.tr("Import now"), primary = true) {
                pending = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val installed = runCatching { LauncherAppsAppRepository(context).apps.first().mapTo(HashSet()) { it.key } }.getOrDefault(emptySet())
                            service.import(text, installed)
                        }
                    }.onSuccess { message = describe(it); snapshots = service.snapshots.list() }.onFailure(::fail)
                }
            }
            TextButtonPill(L10n.tr("Cancel")) { pending = null }
        }
        message?.let { note(it) }

        Text(L10n.tr("Snapshots"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        note(L10n.tr("The last 5 are kept on this device: before every import, before deleting a page, and after each stable start."))
        ActionRow(L10n.tr("Take a snapshot now"), null, Glyphs.Plus) {
            scope.launch {
                val s = withContext(Dispatchers.IO) { service.snapshot("manual", force = true) }
                snapshots = service.snapshots.list()
                message = if (s != null) L10n.tr("Snapshot taken.") else L10n.tr("Could not take a snapshot.")
            }
        }
        if (snapshots.isEmpty()) note(L10n.tr("No snapshots yet."))
        snapshots.forEach { s ->
            ActionRow(
                L10n.tr("Restore %s", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.takenAt))),
                s.reason.replaceFirstChar { it.uppercase() },
                Glyphs.Bolt,
            ) { restore(scope, service, s, context, { message = it }, { snapshots = service.snapshots.list() }) }
        }
    }
}

private fun restore(
    scope: kotlinx.coroutines.CoroutineScope, service: StateService, s: SnapshotStore.Info, context: Context,
    say: (String) -> Unit, refresh: () -> Unit,
) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { service.restore(s.id) } }
            .onSuccess { say(L10n.tr("Restored. The state before the restore is kept as the newest snapshot.")); refresh() }
            .onFailure { say(L10n.tr("That did not work: %s", it.message ?: "")) }
    }
}

@Composable
private fun note(text: String) {
    Text(text, color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
}
