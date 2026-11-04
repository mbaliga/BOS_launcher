package com.bos.sphere.feature.pages.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.pages.ImportFailure
import com.bos.sphere.feature.pages.ImportResult
import com.bos.sphere.feature.pages.ManifestIssue
import com.bos.sphere.feature.pages.ManifestProblem
import com.bos.sphere.feature.pages.NavMode
import com.bos.sphere.feature.pages.PackageProblem
import com.bos.sphere.feature.pages.Page
import com.bos.sphere.feature.pages.PageEdits
import com.bos.sphere.feature.pages.PageKind
import com.bos.sphere.feature.pages.PageTurn
import com.bos.sphere.feature.pages.R
import com.bos.sphere.feature.pages.RuntimeOrigin
import com.bos.sphere.feature.pages.UrlCheck
import com.bos.sphere.feature.pages.UrlProblem
import com.bos.sphere.feature.pages.UrlRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Types the system file picker offers for an import (a zip package or an html page). */
private val IMPORT_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
    "text/html",
    "application/xhtml+xml",
)

/** The settings rows: "Move between pages" (three modes) and "Manage pages". */
@Composable
fun PagesUi.PagesSettingsSection() {
    val navMode by mode
    Text(
        stringResource(R.string.pages_section),
        color = HyleColors.InkDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    Text(stringResource(R.string.pages_nav_title), color = HyleColors.Ink, fontSize = 11.5.sp)
    NavMode.entries.forEach { m ->
        val label = stringResource(
            when (m) {
                NavMode.TwoFingerDots -> R.string.pages_nav_two_finger
                NavMode.PastEnds -> R.string.pages_nav_past_ends
                NavMode.Vertical -> R.string.pages_nav_vertical
            },
        )
        ChoiceRow(
            label = label,
            selected = m == navMode,
            selectedCd = stringResource(R.string.pages_nav_selected_cd, label),
            chooseCd = stringResource(R.string.pages_nav_choose_cd, label),
            onClick = { store.setMode(m) },
        )
    }
    val manage = stringResource(R.string.pages_manage)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { openManager() }
            .semantics {
                contentDescription = manage
                role = Role.Button
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(manage, color = HyleColors.Ink, fontSize = 12.sp)
        Text("›", color = HyleColors.VioletSoft, fontSize = 16.sp)
    }
}

/** A selectable row: a check mark and an outline mark the choice, never colour alone. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, selectedCd: String, chooseCd: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (selected) selectedCd else chooseCd
                role = Role.RadioButton
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (selected) HyleColors.Violet else HyleColors.Hairline, shape)
                .background(if (selected) HyleColors.Violet.copy(alpha = 0.14f) else HyleColors.Surface.copy(alpha = 0.4f), shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (selected) "✓" else "○", color = if (selected) HyleColors.InkBright else HyleColors.InkDim, fontSize = 12.sp)
            Text(label, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 12.sp)
        }
    }
}

/** Every dialog of the pages: manager, add, address, rename, remove, info, import results. */
@Composable
fun PagesUi.PagesDialogs(hooks: AppWidgetPageHooks?) {
    var reimportId by remember { mutableStateOf<String?>(null) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, folder = false, existingId = reimportId)
        reimportId = null
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runImport(uri, folder = true, existingId = reimportId)
        reimportId = null
    }
    val pickFile = { existing: String? ->
        reimportId = existing
        runCatching { openFile.launch(IMPORT_TYPES) }
        Unit
    }
    val pickFolder = { existing: String? ->
        reimportId = existing
        runCatching { openTree.launch(null) }
        Unit
    }

    when (val d = dialog) {
        PagesDialog.Manager -> ManagerDialog(hooks)
        PagesDialog.Add -> AddDialog(hooks, onFile = { pickFile(null) }, onFolder = { pickFolder(null) })
        PagesDialog.Url -> UrlDialog()
        is PagesDialog.Rename -> RenameDialog(d.id)
        is PagesDialog.Remove -> RemoveDialog(d.id, hooks)
        is PagesDialog.Info -> InfoDialog(d.id, hooks, onReimportFile = { pickFile(d.id) }, onReimportFolder = { pickFolder(d.id) })
        is PagesDialog.ImportFailed -> SimpleDialog(stringResource(R.string.pages_import_failed_title), { dialog = null }) {
            failureLines(d.failure).forEach { Text(it, color = HyleColors.Ink, fontSize = 13.sp) }
        }
        is PagesDialog.ImportNotes -> SimpleDialog(stringResource(R.string.pages_imported_with_notes), { dialog = null }) {
            d.warnings.forEach { Text(stringResource(R.string.pages_warn_opens, it.value), color = HyleColors.Ink, fontSize = 13.sp) }
        }
        null -> Unit
    }
    if (importing) {
        Dialog(onDismissRequest = {}) {
            Box(Modifier.background(HyleColors.SurfaceTranslucent, RoundedCornerShape(16.dp)).padding(24.dp)) {
                Text(stringResource(R.string.pages_importing), color = HyleColors.InkBright, fontSize = 14.sp)
            }
        }
    }
}

private fun PagesUi.runImport(uri: Uri, folder: Boolean, existingId: String?) {
    importing = true
    launch {
        try {
            val id = existingId ?: store.newId()
            // PageFiles turns every import error into ImportResult.Failed; nothing is thrown here.
            val result = withContext(Dispatchers.IO) {
                if (folder) runtime.files.importFolder(id, uri) else runtime.files.importFile(id, uri)
            }
            importing = false
            when (result) {
                is ImportResult.Ok -> {
                    if (existingId == null) {
                        store.update { PageEdits.add(it, Page(id, PageKind.Html, isPackage = result.record.isPackage)) }
                        turnToId(id)
                    } else {
                        store.update { PageEdits.reimported(it, id, result.record.isPackage) }
                    }
                    dialog = if (result.record.warnings.isNotEmpty()) PagesDialog.ImportNotes(result.record.warnings) else null
                }
                is ImportResult.Failed -> dialog = PagesDialog.ImportFailed(result.failure)
            }
        } finally {
            importing = false
        }
    }
}

@Composable
private fun failureLines(f: ImportFailure): List<String> = when (f) {
    ImportFailure.Unreadable -> listOf(stringResource(R.string.pages_import_unreadable))
    ImportFailure.TooLarge -> listOf(stringResource(R.string.pages_import_too_large))
    ImportFailure.NotHtml -> listOf(stringResource(R.string.pages_import_not_html))
    is ImportFailure.Layout -> listOf(
        when (f.problem) {
            PackageProblem.Empty -> stringResource(R.string.pages_layout_empty)
            PackageProblem.UnsafePath -> stringResource(R.string.pages_layout_unsafe, f.detail)
            PackageProblem.NoManifest -> stringResource(R.string.pages_layout_no_manifest)
            PackageProblem.NoHtml -> stringResource(R.string.pages_layout_no_html)
            PackageProblem.TooManyFiles -> stringResource(R.string.pages_layout_too_many)
            PackageProblem.TooLarge -> stringResource(R.string.pages_import_too_large)
        },
    )
    is ImportFailure.Manifest -> f.issues.map { issueText(it) }
}

@Composable
private fun issueText(i: ManifestIssue): String = when (i.problem) {
    ManifestProblem.NotJson -> stringResource(R.string.pages_manifest_not_json)
    ManifestProblem.MissingField -> stringResource(R.string.pages_manifest_missing, i.field)
    ManifestProblem.WrongType -> stringResource(R.string.pages_manifest_wrong_type, i.field)
    ManifestProblem.BadId -> stringResource(R.string.pages_manifest_bad_id, i.field, i.value)
    ManifestProblem.BadName -> stringResource(R.string.pages_manifest_bad_name)
    ManifestProblem.BadVersion -> stringResource(R.string.pages_manifest_bad_version)
    ManifestProblem.BadSize -> stringResource(R.string.pages_manifest_bad_size, i.field, i.value)
    ManifestProblem.NoFill -> stringResource(R.string.pages_manifest_no_fill)
    ManifestProblem.BadChannel -> stringResource(R.string.pages_manifest_bad_channel, i.field, i.value)
    ManifestProblem.LauncherChannelWrite -> stringResource(R.string.pages_manifest_launcher_write, i.field, i.value)
    ManifestProblem.NetworkRequested -> stringResource(R.string.pages_manifest_network)
    ManifestProblem.BadOpens -> stringResource(R.string.pages_manifest_bad_opens)
}

// --- manager -----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagesUi.ManagerDialog(hooks: AppWidgetPageHooks?) {
    val list by pages
    val current = PageTurn.current(position.value, list.size)
    BigDialog(stringResource(R.string.pages_manager_title), onDismiss = { dialog = null }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.pages.forEachIndexed { index, page ->
                val title = titleOf(page, hooks)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, HyleColors.Hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    val goTo = stringResource(R.string.pages_go_to_cd, title)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                dialog = null
                                turnTo(index)
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = goTo
                                role = Role.Button
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CurrentMark(index == current)
                        Text("${index + 1}", color = HyleColors.InkDim, fontSize = 12.sp)
                        Column(Modifier.weight(1f)) {
                            Text(title, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(kindLine(page), color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (page.kind != PageKind.Home) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PillButton("▲", stringResource(R.string.pages_move_up_cd, title)) {
                                store.update { PageEdits.move(it, page.id, -1) }
                            }
                            PillButton("▼", stringResource(R.string.pages_move_down_cd, title)) {
                                store.update { PageEdits.move(it, page.id, 1) }
                            }
                            PillButton(stringResource(R.string.pages_rename), stringResource(R.string.pages_rename_cd, title)) {
                                dialog = PagesDialog.Rename(page.id)
                            }
                            if (page.kind == PageKind.AppWidget && hooks?.canReconfigure(page.widgetSlot) == true) {
                                PillButton(stringResource(R.string.pages_reconfigure), stringResource(R.string.pages_reconfigure_cd, title)) {
                                    dialog = null
                                    hooks.reconfigure(page.widgetSlot)
                                }
                            }
                            if (page.kind != PageKind.AppWidget) {
                                PillButton(stringResource(R.string.pages_info), stringResource(R.string.pages_info_cd, title)) {
                                    dialog = PagesDialog.Info(page.id)
                                }
                            }
                            PillButton(stringResource(R.string.pages_remove), stringResource(R.string.pages_remove_cd, title)) {
                                dialog = PagesDialog.Remove(page.id)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PillButton("+ " + stringResource(R.string.pages_add), stringResource(R.string.pages_add)) { dialog = PagesDialog.Add }
            PillButton(stringResource(R.string.pages_done), stringResource(R.string.pages_done)) { dialog = null }
        }
    }
}

@Composable
private fun PagesUi.kindLine(page: Page): String = when (page.kind) {
    PageKind.Home -> stringResource(R.string.pages_manager_home_note)
    PageKind.AppWidget -> stringResource(R.string.pages_kind_appwidget)
    PageKind.Web -> stringResource(R.string.pages_kind_web) + " · " + (UrlRules.hostOf(page.url) ?: page.url) +
        if (UrlRules.isSecure(page.url)) "" else " · " + stringResource(R.string.pages_not_secure)
    PageKind.Html -> stringResource(if (page.isPackage) R.string.pages_kind_html_package else R.string.pages_kind_html_page) +
        if (page.network && !page.isPackage) " · " + stringResource(R.string.pages_network_on) else ""
}

// --- add ---------------------------------------------------------------------------------------

@Composable
private fun PagesUi.AddDialog(hooks: AppWidgetPageHooks?, onFile: () -> Unit, onFolder: () -> Unit) {
    val list by pages
    SimpleDialog(stringResource(R.string.pages_add), onDismiss = { dialog = PagesDialog.Manager }, confirm = stringResource(R.string.pages_cancel)) {
        if (hooks != null) {
            AddOption(stringResource(R.string.pages_add_appwidget), stringResource(R.string.pages_add_appwidget_desc)) {
                dialog = null
                hooks.add(PageEdits.nextWidgetSlot(list, hooks.placedSlots))
            }
        }
        AddOption(stringResource(R.string.pages_add_html_file), stringResource(R.string.pages_add_html_file_desc)) {
            dialog = null
            onFile()
        }
        AddOption(stringResource(R.string.pages_add_html_folder), stringResource(R.string.pages_add_html_folder_desc)) {
            dialog = null
            onFolder()
        }
        AddOption(stringResource(R.string.pages_add_web), stringResource(R.string.pages_add_web_desc)) {
            dialog = PagesDialog.Url
        }
    }
}

@Composable
private fun AddOption(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 8.dp),
    ) {
        Text(title, color = HyleColors.InkBright, fontSize = 14.sp)
        Text(description, color = HyleColors.InkDim, fontSize = 12.sp)
    }
}

// --- website address ---------------------------------------------------------------------------

@Composable
private fun PagesUi.UrlDialog() {
    var text by remember { mutableStateOf("") }
    val check = remember(text) { UrlRules.check(text) }
    val add: (UrlCheck.Ok) -> Unit = { ok ->
        val id = store.newId()
        store.update { PageEdits.add(it, Page(id, PageKind.Web, url = ok.url)) }
        dialog = null
        turnToId(id)
    }
    SimpleDialog(
        stringResource(R.string.pages_url_title),
        onDismiss = { dialog = PagesDialog.Add },
        confirm = stringResource(R.string.pages_cancel),
        extraButton = (check as? UrlCheck.Ok)?.let { ok -> stringResource(R.string.pages_url_add) to { add(ok) } },
    ) {
        // An address keyboard, no autocorrect, and Go adds the page once the address is valid.
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.pages_url_hint), fontSize = 14.sp) },
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { (check as? UrlCheck.Ok)?.let(add) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.pages_url_https_note), color = HyleColors.InkDim, fontSize = 12.sp)
        when (check) {
            is UrlCheck.Ok -> {
                Text(stringResource(R.string.pages_url_preview, check.url), color = HyleColors.Ink, fontSize = 12.sp)
                if (!check.secure) {
                    SecurityMark(false)
                    Text(stringResource(R.string.pages_url_not_https), color = HyleColors.Ink, fontSize = 12.sp)
                }
            }
            is UrlCheck.Rejected -> if (text.isNotBlank()) {
                Text(
                    "✕ " + stringResource(
                        when (check.problem) {
                            UrlProblem.Empty -> R.string.pages_url_empty
                            UrlProblem.TooLong -> R.string.pages_url_too_long
                            UrlProblem.Scheme -> R.string.pages_url_scheme
                            UrlProblem.NoHost -> R.string.pages_url_no_host
                            UrlProblem.Credentials -> R.string.pages_url_credentials
                            UrlProblem.BadPort -> R.string.pages_url_bad_port
                            UrlProblem.Malformed -> R.string.pages_url_malformed
                        },
                    ),
                    color = HyleColors.InkBright,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// --- rename / remove ---------------------------------------------------------------------------

@Composable
private fun PagesUi.RenameDialog(id: String) {
    val page = store.pages.collectAsState().value.byId(id) ?: return
    var text by remember(id) { mutableStateOf(page.title) }
    SimpleDialog(
        stringResource(R.string.pages_rename_title),
        onDismiss = { dialog = PagesDialog.Manager },
        confirm = stringResource(R.string.pages_cancel),
        extraButton = stringResource(R.string.pages_save) to {
            store.update { PageEdits.rename(it, id, text) }
            dialog = PagesDialog.Manager
        },
    ) {
        OutlinedTextField(value = text, onValueChange = { text = it.take(PageEdits.MAX_TITLE) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.pages_rename_note), color = HyleColors.InkDim, fontSize = 12.sp)
    }
}

@Composable
private fun PagesUi.RemoveDialog(id: String, hooks: AppWidgetPageHooks?) {
    val page = store.pages.collectAsState().value.byId(id) ?: return
    val title = titleOf(page, hooks)
    SimpleDialog(
        stringResource(R.string.pages_remove_title, title),
        onDismiss = { dialog = PagesDialog.Manager },
        confirm = stringResource(R.string.pages_cancel),
        extraButton = stringResource(R.string.pages_remove) to {
            removePage(page, hooks)
            dialog = PagesDialog.Manager
        },
    ) {
        Text(
            stringResource(
                when (page.kind) {
                    PageKind.Html -> R.string.pages_remove_html
                    PageKind.Web -> R.string.pages_remove_web
                    else -> R.string.pages_remove_widget
                },
            ),
            color = HyleColors.Ink,
            fontSize = 13.sp,
        )
    }
}

// --- info --------------------------------------------------------------------------------------

@Composable
private fun PagesUi.InfoDialog(id: String, hooks: AppWidgetPageHooks?, onReimportFile: () -> Unit, onReimportFolder: () -> Unit) {
    val page = store.pages.collectAsState().value.byId(id) ?: return
    val context = LocalContext.current
    val net by runtime.net.collectAsState()
    val title = titleOf(page, hooks)
    BigDialog(title, onDismiss = { dialog = null }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page.kind) {
                PageKind.Web -> {
                    Text(page.url, color = HyleColors.InkBright, fontSize = 13.sp)
                    SecurityMark(UrlRules.isSecure(page.url))
                    if (!UrlRules.isSecure(page.url)) Text(stringResource(R.string.pages_url_not_https), color = HyleColors.Ink, fontSize = 12.sp)
                    Text(stringResource(R.string.pages_safe_browsing_note), color = HyleColors.Ink, fontSize = 12.sp)
                    Text(stringResource(R.string.pages_web_cookies_note), color = HyleColors.Ink, fontSize = 12.sp)
                    Text(stringResource(R.string.pages_info_limits), color = HyleColors.InkDim, fontSize = 12.sp)
                }
                PageKind.Html -> {
                    val record = runtime.files.record(page.id)
                    if (record == null) {
                        Text(stringResource(R.string.pages_files_missing_body), color = HyleColors.Ink, fontSize = 13.sp)
                    } else {
                        Text(
                            if (record.isPackage) stringResource(R.string.pages_info_package, record.manifest.version.toInt())
                            else stringResource(R.string.pages_info_page),
                            color = HyleColors.InkBright,
                            fontSize = 13.sp,
                        )
                        Text(
                            stringResource(
                                R.string.pages_info_source,
                                record.sourceName ?: record.name,
                                Formatter.formatShortFileSize(context, record.bytes),
                            ),
                            color = HyleColors.Ink,
                            fontSize = 12.sp,
                        )
                        Text(stringResource(R.string.pages_info_origin, RuntimeOrigin.originFor(page.id)), color = HyleColors.InkDim, fontSize = 12.sp)
                        if (record.isPackage) {
                            val none = stringResource(R.string.pages_info_nothing)
                            Text(stringResource(R.string.pages_info_reads, record.manifest.reads.joinToString().ifEmpty { none }), color = HyleColors.Ink, fontSize = 12.sp)
                            Text(stringResource(R.string.pages_info_writes, record.manifest.writes.joinToString().ifEmpty { none }), color = HyleColors.Ink, fontSize = 12.sp)
                            Text(stringResource(R.string.pages_info_opens, record.manifest.opens.joinToString().ifEmpty { none }), color = HyleColors.Ink, fontSize = 12.sp)
                            Text(stringResource(R.string.pages_info_package_network), color = HyleColors.Ink, fontSize = 12.sp)
                        } else {
                            NetworkToggle(page.network) { on -> store.update { PageEdits.setNetwork(it, page.id, on) } }
                            Text(
                                stringResource(
                                    when {
                                        page.network -> R.string.pages_network_on_note
                                        record.isOrrery -> R.string.pages_network_off_orrery
                                        else -> R.string.pages_network_off_note
                                    },
                                ),
                                color = HyleColors.Ink,
                                fontSize = 12.sp,
                            )
                            val noneYet = stringResource(R.string.pages_hosts_none)
                            if (record.mentionedHosts.isNotEmpty()) {
                                Text(stringResource(R.string.pages_hosts_mentioned, record.mentionedHosts.joinToString()), color = HyleColors.InkDim, fontSize = 12.sp)
                            }
                            val log = net[page.id]
                            Text(stringResource(R.string.pages_hosts_reached, log?.reached?.sorted()?.joinToString()?.ifEmpty { null } ?: noneYet), color = HyleColors.Ink, fontSize = 12.sp)
                            Text(stringResource(R.string.pages_hosts_blocked, log?.blocked?.sorted()?.joinToString()?.ifEmpty { null } ?: noneYet), color = HyleColors.Ink, fontSize = 12.sp)
                        }
                    }
                    Text(stringResource(R.string.pages_safe_browsing_note), color = HyleColors.InkDim, fontSize = 12.sp)
                    Text(stringResource(R.string.pages_info_not_backed_up), color = HyleColors.InkDim, fontSize = 12.sp)
                    Text(stringResource(R.string.pages_info_limits), color = HyleColors.InkDim, fontSize = 12.sp)
                }
                PageKind.AppWidget, PageKind.Home -> Unit
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (page.kind == PageKind.Html) {
                PillButton(stringResource(R.string.pages_reimport), stringResource(R.string.pages_reimport)) {
                    dialog = null
                    if (page.isPackage && runtime.files.record(page.id)?.sourceName?.endsWith(".zip", ignoreCase = true) == false) {
                        onReimportFolder()
                    } else {
                        onReimportFile()
                    }
                }
            }
            PillButton(stringResource(R.string.pages_remove), stringResource(R.string.pages_remove_cd, title)) {
                dialog = PagesDialog.Remove(page.id)
            }
            PillButton(stringResource(R.string.pages_close), stringResource(R.string.pages_close)) { dialog = null }
        }
    }
}

/**
 * "Allow this page to reach the network": a word and a filled / outlined pill, never colour alone. A
 * real switch for accessibility services (toggleable state, so TalkBack reads "on" / "off" in the
 * user's language).
 */
@Composable
private fun NetworkToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.pages_network_switch)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .border(1.dp, if (on) HyleColors.Violet else HyleColors.Hairline, shape)
                .background(if (on) HyleColors.Violet else HyleColors.Surface.copy(alpha = 0.5f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(if (on) R.string.pages_switch_on else R.string.pages_switch_off),
                color = if (on) HyleColors.Background else HyleColors.InkDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

// --- dialog frames -----------------------------------------------------------------------------

@Composable
private fun BigDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(1.dp, HyleColors.Hairline, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable
private fun SimpleDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String = stringResource(R.string.pages_ok),
    extraButton: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(1.dp, HyleColors.Hairline, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                PillButton(confirm, confirm, onClick = onDismiss)
                extraButton?.let { (label, run) -> PillButton(label, label, selected = true, onClick = run) }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HyleColors.InkBright,
    unfocusedTextColor = HyleColors.InkBright,
    focusedBorderColor = HyleColors.Violet,
    unfocusedBorderColor = HyleColors.Hairline,
    cursorColor = HyleColors.Violet,
    focusedPlaceholderColor = HyleColors.InkDim,
    unfocusedPlaceholderColor = HyleColors.InkDim,
)
