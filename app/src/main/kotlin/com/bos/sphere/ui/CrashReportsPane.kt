package com.bos.sphere.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.CrashRecoveryHost
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.quality.CrashFreeLedger
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem
import dev.aarso.crashrecovery.CrashRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NFR-17 / NFR-09 (wave 18): the thin Ground Control pane. The crash reports themselves, and every way of reading, copying, saving or
 * sharing one, belong to the shared crash-recovery utility's own screen; this pane shows only whether a report is waiting, the local
 * crash-free percentage, and (debug and alpha Lab builds only) a button that previews that screen with sample content.
 */
fun crashReportsItem(): SettingItem = SettingItem.Pane(
    L10n.tr("Crash reports"), Glyphs.Info, L10n.tr("Kept on this device"),
) { CrashReportsPane() }

private class PaneState(val pending: String?, val percent: Double)

private fun load(context: android.content.Context): PaneState = PaneState(
    runCatching { CrashRecovery.pending(context)?.headline }.getOrNull(),
    runCatching { CrashFreeLedger(File(context.filesDir, "crash-reports")).crashFreePercent() }.getOrDefault(100.0),
)

@Composable
fun ColumnScope.CrashReportsPane() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PaneState?>(null) }
    LaunchedEffect(Unit) { state = withContext(Dispatchers.IO) { load(context) } }

    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("Crash reports stay on this device. Liftoff never uploads them; you can share one from here, and you choose where it goes."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        val s = state
        Text(CrashFreeLedger.line(s?.percent ?: 100.0), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        if (s != null) {
            Text(
                if (s.pending != null) L10n.tr("A crash report is waiting: %s", s.pending) else L10n.tr("No crash report is waiting. After a crash, Liftoff shows its recovery screen on the next start."),
                color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        // The library documents the preview as a debug affordance: it is listed only where the Lab is (debug builds and the alpha default).
        if (LabGate.visible()) {
            ActionRow(L10n.tr("Preview recovery screen"), L10n.tr("Shows the screen with sample content; nothing crashes and nothing is sent"), Glyphs.Info) {
                runCatching { context.startActivity(CrashRecovery.previewIntent(context, CrashRecoveryHost.APP_LABEL)) }
            }
        }
    }
}
