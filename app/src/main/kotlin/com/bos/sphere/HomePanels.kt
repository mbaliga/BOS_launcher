package com.bos.sphere

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.FilesView
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.TimelineDepth
import com.bos.sphere.core.catalog.TimelineView
import com.bos.sphere.core.catalog.UsageRange
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.catalog.RecentFilesModel
import com.bos.sphere.core.catalog.RecentFilesSource
import com.bos.sphere.core.catalog.Timeline
import com.bos.sphere.core.catalog.TimelineEntry
import com.bos.sphere.core.catalog.UsageSplit
import com.bos.sphere.core.catalog.UsageStatsSource
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * HOM-15, HOM-17 and HOM-18: a home panel with three tabs. Files lists recent files from folders
 * the user granted and the pinned (granted) folders; Usage splits today's screen time by app;
 * Timeline lists scheduled launches and app opens in time order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePanelsSheet(
    history: List<LaunchEvent>,
    appsByKey: Map<String, AppEntry>,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    onLaunch: (AppEntry) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val ctx = LocalContext.current
    val prefs = remember { HomePanelPrefs(ctx) }
    // A tall sheet (most of the screen) so the timeline reads as a view of its own.
    val tall = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HyleColors.Surface, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.bos.sphere.core.catalog.PanelTab.entries.forEachIndexed { i, t -> PanelTab(L10n.tr(t.title), tab == i) { tab = i } }
            }
            Box(Modifier.padding(top = 12.dp).height(tall)) {
                when (tab) {
                    0 -> FilesPanel(prefs)
                    1 -> UsagePanel(appsByKey, prefs, onLaunch)
                    else -> TimelinePanel(history, appsByKey, prefs, onLaunch)
                }
            }
        }
    }
}

/** HOM-15, HOM-17, HOM-18: the settings of the three panels (Settings, Home, Home panels). */
@Composable
fun HomePanelSettings() {
    val context = LocalContext.current
    val prefs = remember { HomePanelPrefs(context) }
    val source = remember { RecentFilesSource(context) }
    var range by remember { mutableStateOf(prefs.usageRange) }
    var depth by remember { mutableStateOf(prefs.timelineDepth) }
    var sched by remember { mutableStateOf(prefs.timelineScheduled) }
    var count by remember { mutableIntStateOf(prefs.filesPerFolder) }
    var rev by remember { mutableIntStateOf(0) }
    var trees by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(rev) { trees = withContext(Dispatchers.IO) { source.grantedTrees() } }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(L10n.tr("Usage panel"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        SettingChips(UsageRange.entries, range, { L10n.tr(it.label) }) { range = it; prefs.usageRange = it }
        Text(L10n.tr("Timeline panel"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        SettingChips(TimelineDepth.entries, depth, { L10n.tr(it.label) }) { depth = it; prefs.timelineDepth = it }
        SettingChips(listOf(true, false), sched, { if (it) L10n.tr("With scheduled launches") else L10n.tr("Opens only") }) { sched = it; prefs.timelineScheduled = it }
        Text(L10n.tr("Files panel"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        SettingChips(listOf(10, 20, 50), count, { L10n.tr("%d files", it) }) { count = it; prefs.filesPerFolder = it }
        Text(L10n.tr("Folders the launcher may read"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        if (trees.isEmpty()) Text(L10n.tr("No folder chosen yet. Pick one and its newest files show here; nothing else is read."), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        trees.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(folderName(t), color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(L10n.tr("Remove"), color = HyleColors.Violet, fontSize = 13.sp, modifier = Modifier.clickable { source.release(t); rev++ }.padding(8.dp))
            }
        }
        Text(L10n.tr("Panels appear on home as tiles: Home quick sheet, Widgets, folders and icons."), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun <T> SettingChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.padding(top = 6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> PanelTab(label(o), o == selected) { onSelect(o) } }
    }
}

private fun folderName(tree: Uri): String =
    runCatching { Uri.decode(tree.lastPathSegment.orEmpty()).substringAfterLast(':').ifEmpty { tree.toString() } }.getOrDefault(tree.toString())

@Composable
private fun PanelTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 13.sp,
        modifier = Modifier.background(if (selected) HyleColors.Violet.copy(alpha = 0.3f) else Color(0x12FFFFFF), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Line(title: String, sub: String, onLong: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.combinedClickable(onLongClick = onLong, onClick = onClick) else Modifier).padding(vertical = 7.dp)) {
        Text(title, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FilesPanel(prefs: HomePanelPrefs) {
    val context = LocalContext.current
    val source = remember { RecentFilesSource(context) }
    var rev by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<PinnedFolder>>(emptyList()) }
    var only by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rev) {
        withContext(Dispatchers.IO) {
            val trees = source.grantedTrees()
            val granted = trees.map { it.toString() }.toSet()
            folders = trees.map { PinnedFolder(it.toString(), runCatching { Uri.decode(it.lastPathSegment.orEmpty()).substringAfterLast(':').ifEmpty { it.toString() } }.getOrDefault(it.toString())) }
            files = RecentFilesModel.withinGrants(source.all(prefs.filesPerFolder), granted)
            if (only !in granted) only = null
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            rev++
        }
    }
    val now = System.currentTimeMillis()
    LazyColumn {
        item {
            Text(L10n.tr("Folders"), color = HyleColors.InkDim, fontSize = 11.sp)
            if (folders.isEmpty()) Text(L10n.tr("No folder chosen yet. Pick one and its newest files show here; nothing else is read."), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(folders) { f ->
            Line(f.name, if (only == f.uri) L10n.tr("Pinned folder") + " · " + L10n.tr("Showing only this folder (long-press it again for all)") else L10n.tr("Pinned folder"), onLong = { only = if (only == f.uri) null else f.uri }) {
                runCatching {
                    val tree = Uri.parse(f.uri)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), DocumentsContract.Document.MIME_TYPE_DIR)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        }
        item {
            Text(L10n.tr("Choose a folder"), color = HyleColors.Violet, fontSize = 13.sp, modifier = Modifier.clickable { pick.launch(null) }.padding(vertical = 8.dp))
            Text(L10n.tr("Recent files"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        items(FilesView.filter(files, only)) { f ->
            Line(f.name, RecentFilesModel.age(now, f.modifiedMs)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(f.uri), f.mime.ifEmpty { "*/*" })
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        }
    }
}

@Composable
private fun UsagePanel(appsByKey: Map<String, AppEntry>, prefs: HomePanelPrefs, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val source = remember { UsageStatsSource(context) }
    var granted by remember { mutableStateOf(source.granted()) }
    var slices by remember { mutableStateOf(emptyList<com.bos.sphere.core.catalog.UsageSlice>()) }
    var range by remember { mutableStateOf(prefs.usageRange) }
    // Coming back from the system usage-access screen re-checks the grant.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumes by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    LaunchedEffect(resumes, range) {
        granted = source.granted()
        if (granted) withContext(Dispatchers.IO) {
            val midnight = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val start = range.startMs(midnight)
            slices = UsageSplit.build(source.samples(start, System.currentTimeMillis()))
        }
    }
    val labels = remember(appsByKey) { appsByKey.values.associate { it.packageName to it.label } }
    val appsByPackage = remember(appsByKey) { appsByKey.values.filter { it.user == android.os.Process.myUserHandle() }.associateBy { it.packageName } }
    if (!granted) {
        Column {
            Text(L10n.tr("Usage access is off. It lets the launcher read how long each app was open today. The numbers stay on this device."), color = HyleColors.Ink, fontSize = 13.sp)
            Text(L10n.tr("Open usage access settings"), color = HyleColors.Violet, fontSize = 13.sp, modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 10.dp))
        }
        return
    }
    val rangeChips: @Composable () -> Unit = { SettingChips(UsageRange.entries, range, { L10n.tr(it.label) }) { range = it; prefs.usageRange = it } }
    if (slices.isEmpty()) { Column { rangeChips(); Text(L10n.tr("No screen time recorded yet."), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }; return }
    Column {
        rangeChips()
        Text(L10n.tr(range.label) + ": " + UsageSplit.duration(UsageSplit.totalMs(slices)), color = HyleColors.InkBright, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
        // One proportional bar, then the legend.
        Row(Modifier.fillMaxWidth().height(14.dp).background(Color(0x12FFFFFF), RoundedCornerShape(7.dp))) {
            slices.forEachIndexed { i, s -> Box(Modifier.weight(s.share.coerceAtLeast(0.01f)).height(14.dp).background(sliceColor(i))) }
        }
        slices.forEachIndexed { i, s ->
            val app = appsByPackage[s.packageName]
            Row(Modifier.padding(top = 8.dp).then(if (app != null) Modifier.clickable { onLaunch(app) } else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 4.dp).height(10.dp).fillMaxWidth(0.03f).background(sliceColor(i)))
                Text("${if (s.packageName.isEmpty()) L10n.tr("Other") else labels[s.packageName] ?: s.packageName}  ${UsageSplit.duration(s.ms)}  ${(s.share * 100).toInt()}%", color = HyleColors.Ink, fontSize = 13.sp)
            }
        }
    }
}

private fun sliceColor(i: Int) = listOf(Color(0xFF7C5CFF), Color(0xFF3FB6A8), Color(0xFFE5A04D), Color(0xFFE5484D), Color(0xFF5B9BF0), Color(0xFF8A90A8))[i % 6]

@Composable
private fun TimelinePanel(history: List<LaunchEvent>, appsByKey: Map<String, AppEntry>, prefs: HomePanelPrefs, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var depth by remember { mutableStateOf(prefs.timelineDepth) }
    var withScheduled by remember { mutableStateOf(prefs.timelineScheduled) }
    val entries = remember(history, appsByKey, depth, withScheduled) {
        val scheduled = runCatching {
            WorkspaceRepository.get(context).current.schedules.map { TimelineEntry(it.atMillis, "", it.label, scheduled = true, id = it.id) }
        }.getOrDefault(emptyList())
        TimelineView.build(history, scheduled, now, depth, withScheduled) { appsByKey[it]?.label }
    }
    val startOfToday = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val chips: @Composable () -> Unit = {
        SettingChips(TimelineDepth.entries, depth, { L10n.tr(it.label) }) { depth = it; prefs.timelineDepth = it }
        SettingChips(listOf(true, false), withScheduled, { if (it) L10n.tr("With scheduled launches") else L10n.tr("Opens only") }) { withScheduled = it; prefs.timelineScheduled = it }
    }
    if (entries.isEmpty()) { Column { chips(); Text(L10n.tr("Nothing yet. Opened apps and scheduled launches appear here."), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }; return }
    val rows = remember(entries, startOfToday) { TimelineView.rows(entries, startOfToday) }
    LazyColumn {
        item { chips() }
        items(rows) { r ->
            val e = r.entry
            if (r.header) Text(L10n.tr(r.day), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            val app = if (e.key.isNotEmpty()) appsByKey[e.key] else null
            Line((if (e.scheduled) L10n.tr("Scheduled") + ": " else "") + e.label, fmt.format(Date(e.timeMs)), onClick = app?.let { a -> { onLaunch(a) } })
        }
    }
}
