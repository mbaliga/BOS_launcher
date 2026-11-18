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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
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
import com.bos.sphere.ui.AppTime
import com.bos.sphere.ui.UsageWeekRepo
import com.bos.sphere.ui.UsageWeekSource
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
 * HOM-15, HOM-17 and HOM-18, wave 25 (hubData): the Files and Timeline panels. They are views of the Launchpad hub (one place, no sheet of their
 * own); the hub renders this for its Files and Timeline views. Files lists recent files from folders the user granted and the pinned (granted)
 * folders; Timeline lists scheduled launches and app opens. Wave 28 (usageTimeline): the Usage view is gone; Timeline carries a small usage
 * visualization above its list ([UsageTimelineBlock]; the data is the same [UsageStatsSource]).
 */
@Composable
fun HomePanelsBody(
    view: com.bos.sphere.ui.HubView,
    history: List<LaunchEvent>,
    appsByKey: Map<String, AppEntry>,
    onLaunch: (AppEntry) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { HomePanelPrefs(ctx) }
    // Files is drawn with the dark panel palette, so it sits on its own surface whichever Launchpad theme is on. Wave 28: Timeline sits on the Comms
    // page itself (its usage block is drawn in the page's ink on the page's ground), so it follows the dark and light looks.
    val modifier = if (view == com.bos.sphere.ui.HubView.Timeline) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(HyleColors.Surface)
    Box(modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        when (view) {
            com.bos.sphere.ui.HubView.Timeline -> TimelinePanel(history, appsByKey, prefs, onLaunch)
            else -> FilesPanel(prefs)
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

/** The ink of a panel's text and chips: the dark panel palette by default, the Comms page's own colours for Timeline (wave 28). */
private class PanelInk(val ink: Color, val bright: Color, val dim: Color, val chip: Color) {
    companion object {
        val Dark = PanelInk(HyleColors.Ink, HyleColors.InkBright, HyleColors.InkDim, Color(0x12FFFFFF))
        @Composable fun comms() = com.bos.sphere.ui.commsPanelColors().let { PanelInk(it.ink2, it.ink, it.dim, it.ink.copy(alpha = 0.07f)) }
    }
}

@Composable
private fun <T> SettingChips(options: List<T>, selected: T, label: (T) -> String, ink: PanelInk = PanelInk.Dark, onSelect: (T) -> Unit) {
    Row(Modifier.padding(top = 6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> PanelTab(label(o), o == selected, ink) { onSelect(o) } }
    }
}

private fun folderName(tree: Uri): String =
    runCatching { Uri.decode(tree.lastPathSegment.orEmpty()).substringAfterLast(':').ifEmpty { tree.toString() } }.getOrDefault(tree.toString())

@Composable
private fun PanelTab(text: String, selected: Boolean, ink: PanelInk = PanelInk.Dark, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) ink.bright else ink.ink, fontSize = 13.sp,
        // Wave 26 (audit): a 48 dp touch row, a radio role and the selected state (it was a bare clickable text, 31 dp high, announced as nothing).
        modifier = Modifier.heightIn(min = 48.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .wrapContentHeight(androidx.compose.ui.Alignment.CenterVertically)
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.3f) else ink.chip, shape).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Line(title: String, sub: String, onLong: (() -> Unit)? = null, ink: PanelInk = PanelInk.Dark, tag: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).then(if (onClick != null) Modifier.combinedClickable(onLongClick = onLong, onClick = onClick) else Modifier).padding(vertical = 7.dp)) {
        Text(title, color = ink.bright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = ink.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(L10n.tr("Choose a folder"), color = HyleColors.Violet, fontSize = 13.sp, modifier = Modifier.heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { pick.launch(null) }.wrapContentHeight(androidx.compose.ui.Alignment.CenterVertically))
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
private fun TimelinePanel(history: List<LaunchEvent>, appsByKey: Map<String, AppEntry>, prefs: HomePanelPrefs, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val ink = PanelInk.comms()
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
    val rows = remember(entries, startOfToday) { TimelineView.rows(entries, startOfToday, java.util.TimeZone.getDefault()) }
    val usage = remember(context) { UsageWeekHost.repo(context) }
    val labelOf: (String) -> String = remember(appsByKey) { val labels = appsByKey.values.associate { it.packageName to it.label }; { pkg -> labels[pkg] ?: pkg } }
    // Wave 28: the usage block is the first item of the list, so it scrolls away with it and the list opens scrolled to the top.
    LazyColumn(Modifier.testTag("timeline.list")) {
        item { com.bos.sphere.ui.UsageTimelineBlock(usage, labelOf) }
        item {
            // Wave 29: one flat scrolling row (range, then kind), not two rows of pills; the same persisted prefs.
            val depthText = mapOf(TimelineDepth.Short to ("30" to "Last 30 opens"), TimelineDepth.Medium to ("100" to "Last 100 opens"), TimelineDepth.Full to (L10n.tr("All") to "Everything recorded, up to 5000"))
            com.bos.sphere.ui.CommsFilterRow(
                listOf(
                    TimelineDepth.entries.map { d -> com.bos.sphere.ui.FilterOpt(depthText.getValue(d).first, L10n.tr(depthText.getValue(d).second), d == depth, "timeline.filter.range.${d.name}") { depth = d; prefs.timelineDepth = d } },
                    listOf(true, false).map { s -> com.bos.sphere.ui.FilterOpt(if (s) L10n.tr("Scheduled") else L10n.tr("Opens only"), if (s) L10n.tr("With scheduled launches") else L10n.tr("Opens only"), s == withScheduled, "timeline.filter.kind.${if (s) "scheduled" else "opens"}") { withScheduled = s; prefs.timelineScheduled = s } },
                ),
                Modifier.padding(top = 4.dp),
            )
        }
        if (entries.isEmpty()) item { Text(L10n.tr("Nothing yet. Opened apps and scheduled launches appear here."), color = ink.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        itemsIndexed(rows) { i, r ->
            val e = r.entry
            if (r.header) Text(if (r.dayArg == null) L10n.tr(r.dayKey) else L10n.tr(r.dayKey, r.dayArg), color = ink.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            val app = if (e.key.isNotEmpty()) appsByKey[e.key] else null
            Line((if (e.scheduled) L10n.tr("Scheduled") + ": " else "") + e.label, fmt.format(Date(e.timeMs)), ink = ink, tag = "timeline.row.$i", onClick = app?.let { a -> { onLaunch(a) } })
        }
    }
}

/**
 * Wave 28: the usage statistics source the Timeline's usage block reads: the same [UsageStatsSource] the Usage panel and the Usage tile use.
 * One repository for the process, so the week it read (and cached) survives the panel closing and opening.
 */
internal object UsageWeekHost {
    @Volatile private var repo: UsageWeekRepo? = null
    /** Tests supply their own source; null restores the real one. */
    internal fun setForTest(r: UsageWeekRepo?) { repo = r }

    fun repo(context: android.content.Context): UsageWeekRepo = repo ?: synchronized(this) {
        repo ?: run {
            val src = UsageStatsSource(context.applicationContext)
            UsageWeekRepo(object : UsageWeekSource {
                override fun granted() = src.granted()
                override fun samples(fromMs: Long, toMs: Long) = src.samples(fromMs, toMs).map { AppTime(it.packageName, it.foregroundMs) }
            }).also { repo = it }
        }
    }
}
