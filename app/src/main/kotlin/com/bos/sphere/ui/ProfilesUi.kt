package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherHapticSettings
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.SuggestApp
import com.bos.sphere.feature.dock.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wave 57 (profiles, CFG-47): what the offline app group suggester knows about each launcher app: the category the app declares
 * (`ApplicationInfo.category`, read through the launcher's own package queries) and whether it belongs to the work profile.
 * Read off the main thread; until it is read the apps are offered with their names only.
 */
@Composable
fun rememberSuggestFacts(apps: List<AppEntry>): List<SuggestApp> {
    val context = LocalContext.current
    val plain = apps.map { SuggestApp(it.key, it.packageName, it.label) }
    val facts by produceState(plain, apps) { value = withContext(Dispatchers.IO) { suggestFacts(context, apps) } }
    return facts
}

fun suggestFacts(context: android.content.Context, apps: List<AppEntry>): List<SuggestApp> {
    val me = android.os.Process.myUserHandle()
    val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
    return apps.map { a ->
        val category = runCatching { la?.getApplicationInfo(a.packageName, 0, a.user)?.category }.getOrNull() ?: -1
        SuggestApp(a.key, a.packageName, a.label, category, work = a.user != me)
    }
}

/**
 * CFG-48 precedence, the write-through half: while a profile is active, a change to a setting it carries is saved to that profile
 * (the switch captures it too, this keeps the stored profile current for an export or a process death). Every other change stays
 * global, which needs nothing. Once per process, from whichever surface needs it first.
 */
object ProfileSettingsSync {
    private var bound: Any? = null

    @Synchronized fun ensure(context: android.content.Context) {
        val manager = WorkspaceRepository.get(context)
        if (bound === manager) return
        bound = manager
        val stores = LauncherStores.get(context)
        LauncherHapticSettings.init(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            combine(stores.prefs.prefs, stores.shortcuts.gestures, LauncherHapticSettings.strength, manager.state.map { it.dock }.distinctUntilChanged()) { a, b, c, d -> listOf(a, b, c, d) }
                .distinctUntilChanged()
                .collect { manager.syncActiveSettings() }
        }
    }
}

/** "Switched to Work": a toast, which TalkBack also reads. */
internal fun announceSwitch(context: android.content.Context, name: String) {
    android.widget.Toast.makeText(context, com.bos.sphere.core.design.L10n.tr("Switched to %s", name), android.widget.Toast.LENGTH_SHORT).show()
}
