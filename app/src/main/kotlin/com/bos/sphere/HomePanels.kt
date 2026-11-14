package com.bos.sphere

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.bos.sphere.core.catalog.PinnedFolder
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
fun HomePanelsSheet(history: List<LaunchEvent>, appsByKey: Map<String, AppEntry>, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HyleColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Files", "Usage", "Timeline").forEachIndexed { i, t -> PanelTab(L10n.tr(t), tab == i) { tab = i } }
            }
            Box(Modifier.padding(top = 12.dp).height(380.dp)) {
                when (tab) {
                    0 -> FilesPanel()
                    1 -> UsagePanel(appsByKey)
                    else -> TimelinePanel(history, appsByKey)
                }
            }
        }
    }
}

@Composable
private fun PanelTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 13.sp,
        modifier = Modifier.background(if (selected) HyleColors.Violet.copy(alpha = 0.3f) else Color(0x12FFFFFF), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Line(title: String, sub: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 7.dp)) {
        Text(title, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FilesPanel() {
    val context = LocalContext.current
    val source = remember { RecentFilesSource(context) }
    var rev by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<PinnedFolder>>(emptyList()) }
    LaunchedEffect(rev) {
        withContext(Dispatchers.IO) {
            val trees = source.grantedTrees()
            val granted = trees.map { it.toString() }.toSet()
            folders = trees.map { PinnedFolder(it.toString(), runCatching { Uri.decode(it.lastPathSegment.orEmpty()).substringAfterLast(':').ifEmpty { it.toString() } }.getOrDefault(it.toString())) }
            files = RecentFilesModel.withinGrants(source.all(20), granted)
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
            Line(f.name, L10n.tr("Pinned folder")) {
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
        items(files) { f ->
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
private fun UsagePanel(appsByKey: Map<String, AppEntry>) {
    val context = LocalContext.current
    val source = remember { UsageStatsSource(context) }
    var granted by remember { mutableStateOf(source.granted()) }
    var slices by remember { mutableStateOf(emptyList<com.bos.sphere.core.catalog.UsageSlice>()) }
    LaunchedEffect(granted) {
        granted = source.granted()
        if (granted) withContext(Dispatchers.IO) {
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            slices = UsageSplit.build(source.samples(start, System.currentTimeMillis()))
        }
    }
    val labels = remember(appsByKey) { appsByKey.values.associate { it.packageName to it.label } }
    if (!granted) {
        Column {
            Text(L10n.tr("Usage access is off. It lets the launcher read how long each app was open today. The numbers stay on this device."), color = HyleColors.Ink, fontSize = 13.sp)
            Text(L10n.tr("Open usage access settings"), color = HyleColors.Violet, fontSize = 13.sp, modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 10.dp))
        }
        return
    }
    if (slices.isEmpty()) { Text(L10n.tr("No screen time recorded today yet."), color = HyleColors.Ink, fontSize = 13.sp); return }
    Column {
        Text(L10n.tr("Today") + ": " + UsageSplit.duration(UsageSplit.totalMs(slices)), color = HyleColors.InkBright, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        // One proportional bar, then the legend.
        Row(Modifier.fillMaxWidth().height(14.dp).background(Color(0x12FFFFFF), RoundedCornerShape(7.dp))) {
            slices.forEachIndexed { i, s -> Box(Modifier.weight(s.share.coerceAtLeast(0.01f)).height(14.dp).background(sliceColor(i))) }
        }
        slices.forEachIndexed { i, s ->
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 4.dp).height(10.dp).fillMaxWidth(0.03f).background(sliceColor(i)))
                Text("${if (s.packageName.isEmpty()) L10n.tr("Other") else labels[s.packageName] ?: s.packageName}  ${UsageSplit.duration(s.ms)}  ${(s.share * 100).toInt()}%", color = HyleColors.Ink, fontSize = 13.sp)
            }
        }
    }
}

private fun sliceColor(i: Int) = listOf(Color(0xFF7C5CFF), Color(0xFF3FB6A8), Color(0xFFE5A04D), Color(0xFFE5484D), Color(0xFF5B9BF0), Color(0xFF8A90A8))[i % 6]

@Composable
private fun TimelinePanel(history: List<LaunchEvent>, appsByKey: Map<String, AppEntry>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val entries = remember(history, appsByKey) {
        val scheduled = runCatching {
            WorkspaceRepository.get(context).current.schedules.map { TimelineEntry(it.atMillis, "", it.label, scheduled = true, id = it.id) }
        }.getOrDefault(emptyList())
        Timeline.build(history, scheduled, now) { appsByKey[it]?.label }
    }
    val startOfToday = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    if (entries.isEmpty()) { Text(L10n.tr("Nothing yet. Opened apps and scheduled launches appear here."), color = HyleColors.Ink, fontSize = 13.sp); return }
    val rows = remember(entries, startOfToday) {
        entries.mapIndexed { i, e ->
            val day = Timeline.dayLabel(startOfToday, e.timeMs)
            Triple(e, day, i == 0 || day != Timeline.dayLabel(startOfToday, entries[i - 1].timeMs))
        }
    }
    LazyColumn {
        items(rows) { (e, day, header) ->
            if (header) Text(L10n.tr(day), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            Line((if (e.scheduled) L10n.tr("Scheduled") + ": " else "") + e.label, fmt.format(Date(e.timeMs)))
        }
    }
}
