package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.SafeMode
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.TextButtonPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * CFG-09: what Liftoff shows after it crashed twice within 30 seconds of starting. A plain alphabetical grid of app
 * names: no widgets, no wallpaper layers, no Radar, no Launchpad, no motion service. From here the user can open any
 * app, restore the last snapshot, or carry on into the normal home.
 */
@Composable
fun SafeModeScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { runCatching { LauncherAppsAppRepository(context) }.getOrNull() }
    val apps by (repository?.apps ?: emptyFlow()).collectAsState(initial = emptyList())
    val service = remember { StateService.get(context) }
    val offer = remember { SafeMode.plan(true, service.snapshots.list()) }
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(HyleColors.Background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Text(L10n.tr("Safe mode"), color = HyleColors.InkBright, fontSize = 24.sp)
            Text(
                L10n.tr("Liftoff stopped twice in a row right after starting, so widgets and extras are off. Your apps still open from here."),
                color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                offer.restoreFrom?.let { snap ->
                    TextButtonPill(
                        L10n.tr("Restore last snapshot (%s)", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snap.takenAt))),
                        primary = true,
                    ) {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { service.restore(snap.id) } }
                                .onSuccess { CrashLoopGuard.clear(context); onContinue() }
                                .onFailure { message = L10n.tr("Restore failed: %s", it.message ?: "") }
                        }
                    }
                }
                TextButtonPill(L10n.tr("Carry on")) { CrashLoopGuard.clear(context); onContinue() }
            }
            if (offer.restoreFrom == null) {
                Text(L10n.tr("There is no snapshot to restore yet."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            message?.let { Text(it, color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.sortedBy { it.label.lowercase() }, key = { it.key }) { app ->
                    Text(
                        app.label, color = HyleColors.InkBright, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { repository?.launch(app) }.padding(vertical = 14.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
