package com.bos.sphere.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.SafeMode
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.L10n
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

    com.bos.sphere.feature.grid.SafeModeView(
        apps = apps.map { com.bos.sphere.feature.grid.SafeApp(it.key, it.label) },
        restoreLabel = offer.restoreFrom?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.takenAt)) },
        message = message,
        onOpen = { a -> apps.firstOrNull { it.key == a.key }?.let { repository?.launch(it) } },
        onRestore = {
            offer.restoreFrom?.let { snap ->
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { service.restore(snap.id) } }
                        .onSuccess { reloadAfterStateChange(context); CrashLoopGuard.clear(context); onContinue() }
                        .onFailure { message = L10n.tr("Restore failed: %s", it.message ?: "") }
                }
            }
        },
        onCarryOn = { CrashLoopGuard.clear(context); onContinue() },
    )
}
