package com.bos.sphere.feature.grid

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
import com.bos.sphere.core.data.LauncherImport
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PendingWidget
import com.bos.sphere.core.data.WidgetRebind
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.SnapshotStore
import com.bos.sphere.core.data.StateCodec
import com.bos.sphere.core.data.StateDocument
import com.bos.sphere.core.data.StateFormatException
import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val MAX_FILE_BYTES = 8 * 1024 * 1024
private const val MAX_LAUNCHER_BACKUP_BYTES = 64 * 1024 * 1024

/** Where a pending widget will go, for the list. */
private fun widgetPlace(w: PendingWidget): String = when (val t = WidgetRebind.parseSlot(w.slot)) {
    is WidgetRebind.Target.CornerTarget -> if (t.landscape) L10n.tr("Corner %s, landscape", t.corner.name) else L10n.tr("Corner %s", t.corner.name)
    WidgetRebind.Target.LeftPageTarget -> L10n.tr("Left page")
    null -> w.slot
}

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

/**
 * The body of the Backup pane. [onStateChanged] runs after an import or restore so the parts that keep their own in-memory copy
 * (workspaces, shortcuts) re-read what was written; the app passes its reload function. It lives here, not in `app`, so the
 * pane is tested with Robolectric like the rest of Ground Control (CFG-02).
 */
@Composable
fun ColumnScope.BackupPane(onStateChanged: (Context) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { StateService.get(context) }
    var snapshots by remember { mutableStateOf(service.snapshots.list()) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) } // (file text, summary)
    var widgetsLeft by remember { mutableStateOf(service.pendingWidgets()) }
    var needsReload by remember { mutableStateOf(false) }
    var launcherImport by remember { mutableStateOf<LauncherImport.Result?>(null) }
    val widgetHost = remember { LauncherWidgetHost(context) }
    val stores = remember { LauncherStores.get(context) }

    /** After an import or restore: tell the parts that keep their own in-memory copy to re-read what was written (CFG-02). */
    fun reloaded() {
        onStateChanged(context)
        widgetsLeft = service.pendingWidgets()
        snapshots = service.snapshots.list()
        needsReload = true
    }

    val rebinder = rememberWidgetRebinder(widgetHost) { w, outcome, id, label ->
        message = when (outcome) {
            RebindOutcome.Done -> {
                WidgetRebind.place(w, id, label, stores.shortcuts, stores.prefs)
                service.clearPendingWidget(w.slot)
                widgetsLeft = service.pendingWidgets()
                L10n.tr("Widget added: %s.", label.ifBlank { w.label })
            }
            RebindOutcome.ProviderMissing -> L10n.tr("%s: the widget's app is not installed here yet.", w.label)
            else -> L10n.tr("The widget was not added.")
        }
    }

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

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw StateFormatException("Could not open the file")
                    if (bytes.size > MAX_LAUNCHER_BACKUP_BYTES) throw StateFormatException("The file is too large to be a launcher backup")
                    val installed = runCatching { LauncherAppsAppRepository(context).apps.first().mapTo(HashSet()) { it.key } }.getOrDefault(emptySet())
                    LauncherImport.parse(bytes, installed, context.cacheDir)
                }
            }.onSuccess { launcherImport = it }.onFailure(::fail)
        }
    }

    Column(Modifier.padding(horizontal = 4.dp)) {
        note(L10n.tr("Your whole setup is one file: layout, settings, corners, gestures, Comms rules, workspaces, sessions, shortcuts and palette aliases. Notifications, usage history, search indexes and contacts are never included."))
        ActionRow(L10n.tr("Export setup…"), L10n.tr("Everything in one file"), Glyphs.Info, flashId = "backup.export") { exportAll.launch("liftoff-setup.json") }
        ActionRow(L10n.tr("Export settings only…"), L10n.tr("A separate settings document"), Glyphs.Info, flashId = "backup.exportSettings") { exportSettings.launch("liftoff-settings.json") }
        ActionRow(L10n.tr("Import setup…"), L10n.tr("From a Liftoff file; snapshots first"), Glyphs.Info, flashId = "backup.import") { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }
        // Wave 57 (CFG-43): Liftoff Studio writes the same settings and setup files, so this is the same import, named for it.
        ActionRow(L10n.tr("Import a Studio file…"), L10n.tr("A file saved by Liftoff Studio in a browser; snapshots first"), Glyphs.Globe, flashId = "backup.importStudio") { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }

        ActionRow(L10n.tr("Import layout from another launcher…"), L10n.tr("A Launcher3-style database or a backup zip that holds one; apps only"), Glyphs.Info, flashId = "backup.importLayout") {
            pickLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/x-sqlite3", "*/*"))
        }
        launcherImport?.let { r ->
            OneUiConfirmDialog(
                L10n.tr("Replace home layout"),
                L10n.tr("Layout found: %d apps on %d pages. %d folders flattened, %d widgets and %d shortcuts left out, %d apps not installed here.", r.apps, r.pages, r.foldersFlattened, r.widgetsSkipped, r.shortcutsSkipped, r.placeholders.size),
                L10n.tr("Replace home layout"),
                onConfirm = {
                    launcherImport = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { service.importLauncherLayout(r.layout) } }
                            .onSuccess { message = L10n.tr("Layout imported. A snapshot of the previous one was taken first."); reloaded() }.onFailure(::fail)
                    }
                },
                onDismiss = { launcherImport = null }, destructive = true,
            )
        }

        pending?.let { (text, summary) ->
            OneUiConfirmDialog(
                L10n.tr("Import now"), summary, L10n.tr("Import now"),
                onConfirm = {
                    pending = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val installed = runCatching { LauncherAppsAppRepository(context).apps.first().mapTo(HashSet()) { it.key } }.getOrDefault(emptySet())
                                service.import(text, installed)
                            }
                        }.onSuccess { message = describe(it); reloaded() }.onFailure(::fail)
                    }
                },
                onDismiss = { pending = null }, destructive = true,
            )
        }
        message?.let { note(it) }
        if (needsReload) {
            note(L10n.tr("Reload Liftoff so workspaces and options from the file take effect."))
            TextButtonPill(L10n.tr("Reload now"), primary = true) { needsReload = false; (context as? android.app.Activity)?.recreate() }
        }
        if (widgetsLeft.isNotEmpty()) {
            Text(L10n.tr("Widgets to add again"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            note(L10n.tr("These widgets were in the file. Each needs one confirmation from Android."))
            widgetsLeft.forEach { w ->
                // A platform restore (NFR-18) removes the device-bound id and cannot say which provider it was: nothing to bind, so say so.
                ActionRow(w.label.ifBlank { widgetPlace(w) }, widgetPlace(w) + if (w.work) " · " + L10n.tr("work profile") else "", Glyphs.Widget) {
                    if (w.provider.isBlank()) message = L10n.tr("Add it again from the widget picker. This setup was restored without the widget's app.") else rebinder.start(w)
                }
                ActionRow(L10n.tr("Dismiss"), w.label.ifBlank { widgetPlace(w) }, Glyphs.Plus) {
                    service.clearPendingWidget(w.slot); widgetsLeft = service.pendingWidgets()
                }
            }
        }

        Text(L10n.tr("Snapshots"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        note(L10n.tr("The last 5 are kept on this device: before every import, before deleting a page, and after each stable start."))
        ActionRow(L10n.tr("Take a snapshot now"), null, Glyphs.Plus, flashId = "backup.snapshot") {
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
            ) { restore(scope, service, s, context, { message = it }, { reloaded() }) }
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
