package com.bos.sphere.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.quality.CrashReports
import com.bos.sphere.core.quality.CrashShare
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** NFR-17 / NFR-09: the crash reports saved on this device; the user may share one, nothing is uploaded. */
fun crashReportsItem(): SettingItem = SettingItem.Pane(
    L10n.tr("Crash reports"), Glyphs.Info, L10n.tr("Saved on this device; you decide whether to share one"),
) { CrashReportsPane() }

private class ReportRow(val file: File, val title: String, val line: String)

private fun store(context: Context) = CrashReports(File(context.filesDir, "crash-reports"))

private fun load(context: Context): Pair<List<ReportRow>, Double> {
    val s = store(context)
    val rows = s.list().map { f ->
        val ms = f.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull()
        ReportRow(f, ms?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: f.name, CrashShare.summary(f))
    }
    return rows to s.crashFreePercent()
}

/** The share sheet is the only way a report leaves the device. */
private fun share(context: Context, f: File) {
    val text = CrashShare.body(store(context).textForSharing(f))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, CrashShare.SUBJECT).putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, L10n.tr("Share crash report")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ColumnScope.CrashReportsPane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ReportRow>?>(null) }
    var percent by remember { mutableStateOf(100.0) }
    fun refresh() { scope.launch { val (r, p) = withContext(Dispatchers.IO) { load(context) }; rows = r; percent = p } }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("Crash reports stay on this device. Liftoff never uploads them; you can share one from here, and you choose where it goes."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(CrashShare.ledgerLine(percent), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        val list = rows
        when {
            list == null -> Unit
            list.isEmpty() -> Text(L10n.tr("No crash reports."), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
            else -> {
                list.forEach { r -> ActionRow(L10n.tr("Share %s", r.title), r.line, Glyphs.Info) { share(context, r.file) } }
                ActionRow(L10n.tr("Delete all reports"), null, Glyphs.Info) {
                    scope.launch { withContext(Dispatchers.IO) { store(context).clear() }; refresh() }
                }
            }
        }
    }
}
