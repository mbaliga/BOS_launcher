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
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.UsageRange
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.catalog.RecentFilesModel
import com.bos.sphere.core.catalog.RecentFilesSource
import com.bos.sphere.ui.AppTime
import com.bos.sphere.ui.UsageWeekRepo
import com.bos.sphere.ui.UsageWeekSource
import com.bos.sphere.core.catalog.UsageStatsSource
import com.bos.sphere.core.data.AppEntry
import android.app.AlarmManager
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationHistory
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.TimelineEvent
import com.bos.sphere.core.data.TimelineEvents
import com.bos.sphere.core.data.TimelineInputs
import com.bos.sphere.core.data.TimelineKind
import com.bos.sphere.core.data.TimelineModel
import com.bos.sphere.core.data.UpcomingLaunch
import com.bos.sphere.core.data.UsageSessionIndex
import com.bos.sphere.feature.dock.RunKind
import com.bos.sphere.ui.NextAlarm
import com.bos.sphere.ui.TimelineActions
import com.bos.sphere.ui.TimelineEventsList
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
    onHide: ((AppEntry) -> Unit)? = null,
    onLaunch: (AppEntry) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { HomePanelPrefs(ctx) }
    // Files is drawn with the dark panel palette, so it sits on its own surface whichever Launchpad theme is on. Wave 28: Timeline sits on the Comms
    // page itself (its usage block is drawn in the page's ink on the page's ground), so it follows the dark and light looks.
    val modifier = if (view == com.bos.sphere.ui.HubView.Timeline) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(HyleColors.Surface)
    // Wave 52: the Widgets section draws its own margins (the Comms page's), so it takes the whole body.
    if (view == com.bos.sphere.ui.HubView.Widgets) { Box(Modifier.fillMaxSize()) { PanelWidgetsBody() }; return }
    // W54: Files keeps its 18 dp side margin itself (inset), so its chip row can run to the page edges; Timeline keeps it here.
    val timeline = view == com.bos.sphere.ui.HubView.Timeline
    Box(modifier.padding(horizontal = if (timeline) 18.dp else 0.dp, vertical = 12.dp)) {
        when (view) {
            com.bos.sphere.ui.HubView.Timeline -> TimelinePanel(history, appsByKey, onLaunch, onHide)
            else -> FilesPanel(prefs, 18.dp)
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
    var rev by remember { mutableIntStateOf(0) }
    var trees by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(rev) { trees = withContext(Dispatchers.IO) { source.grantedTrees() } }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(L10n.tr("Usage panel"), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        SettingChips(UsageRange.entries, range, { L10n.tr(it.label) }) { range = it; prefs.usageRange = it }
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
private fun FilesPanel(prefs: HomePanelPrefs, inset: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val store = remember { FilesCollectionPrefs(context) }
    var rev by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            rev++
        }
    }
    val thumbs = remember { RealFileThumbs(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalFileThumbs provides thumbs) {
        FilesSection(
            prefs, store,
            FilesActions(
                pickFolder = { pick.launch(null) },
                openFile = { openRecentFile(context, it) },
                openFolder = { f ->
                    runCatching {
                        val tree = Uri.parse(f.uri)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), DocumentsContract.Document.MIME_TYPE_DIR)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                },
            ),
            reload = rev, inset = inset,
        )
    }
}

@Composable
private fun TimelinePanel(history: List<LaunchEvent>, appsByKey: Map<String, AppEntry>, onLaunch: (AppEntry) -> Unit, onHide: ((AppEntry) -> Unit)?) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    // Wave 41: one list of everything the launcher recorded. The sources are read where they live (launch log, notification history, the Timeline's
    // own event store, the schedules); the merge itself runs off the main thread inside TimelineEventsList.
    val notes by remember(context) { NotificationHistory.init(context).entries }.collectAsStateWithLifecycle()
    val stored by remember(context) { TimelineEvents.init(context).events }.collectAsStateWithLifecycle()
    val sessions by produceState<UsageSessionIndex?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { UsageStatsSource(context).sessions(System.currentTimeMillis() - 7 * 86_400_000L, System.currentTimeMillis()) }.getOrNull() }
    }
    val nextAlarm by produceState<NextAlarm?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull() }?.let { NextAlarm(it.triggerTime) }
    }
    val alarmShow = remember(context) { runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent }.getOrNull() }
    val upcoming = remember(context) {
        runCatching {
            WorkspaceRepository.get(context).current.schedules.map { UpcomingLaunch(it.id, it.label, it.atMillis, if (it.target.kind == RunKind.App) TimelineModel.packageOfKey(it.target.id) else "") }
        }.getOrDefault(emptyList())
    }
    val byPackage = remember(appsByKey) { appsByKey.values.groupBy { it.packageName }.mapValues { (_, l) -> l.first() } }
    val inputs = remember(history, notes, stored, upcoming, appsByKey, sessions) {
        TimelineInputs(history, notes, stored, upcoming, appsByKey.mapValues { it.value.label }, byPackage.mapValues { it.value.label }, sessions)
    }
    val usage = remember(context) { UsageWeekHost.repo(context) }
    val labelOf: (String) -> String = remember(appsByKey) { val labels = appsByKey.values.associate { it.packageName to it.label }; { pkg -> labels[pkg] ?: pkg } }
    val actions = remember(appsByKey, onLaunch, onHide, alarmShow) {
        fun appOf(e: TimelineEvent): AppEntry? = (if (e.kind == TimelineKind.AppOpened) appsByKey[e.key] else null) ?: byPackage[e.packageName]
        TimelineActions(
            open = { e ->
                if (e.kind.family == TimelineKind.Family.Notification || e.kind.family == TimelineKind.Family.Alert) {
                    // Comms' own hand-off: the live notification's content intent when it is still posted, else the app; it marks the notification read.
                    val live = NotificationProvider.snapshot.firstOrNull { it.key == e.key }
                    LaunchpadSettings.markRead(e.key)
                    val sent = live?.contentIntent?.let { PendingIntentLauncher.open(it, context) { false }.ok } == true
                    if (!sent) appOf(e)?.let(onLaunch)
                } else appOf(e)?.takeIf { e.kind != TimelineKind.PackageRemoved }?.let(onLaunch)
            },
            hideApp = onHide?.let { hide -> { e -> appOf(e)?.let(hide) } },
            openNextAlarm = alarmShow?.let { pi -> { runCatching { pi.send() } } },
        )
    }
    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    TimelineEventsList(inputs, now, is24, actions, Modifier, nextAlarm) { com.bos.sphere.ui.UsageTimelineBlock(usage, labelOf) }
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
