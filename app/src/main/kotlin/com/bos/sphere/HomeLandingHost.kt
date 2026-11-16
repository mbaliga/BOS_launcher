package com.bos.sphere

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.HomeRecents
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RecentCandidate
import com.bos.sphere.core.data.RecentsProfile
import com.bos.sphere.core.signals.UsageRecents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * HOM-22: the live Recent apps list for the home, newest first. It is only collected while the landing is Recent apps, so the other
 * landings pay nothing. The merge runs on a background dispatcher (see [HomeRecents.flow]); the usage log is read there too and again
 * whenever the home resumes. The newest app is already first, so "the running app first" (a rule of the flow) is not used here: it
 * could only repeat the order. Hidden apps never appear; the list is bounded by Settings (8 to 48).
 */
@Composable
fun rememberRecentApps(context: Context, stores: LauncherStores, apps: List<AppEntry>, hidden: Set<String>, prefs: LauncherPrefs): State<List<AppEntry>> {
    val active = prefs.homeLanding == HomeLanding.Recents
    val appsIn = remember { MutableStateFlow(emptyList<RecentCandidate>()) }
    val hiddenIn = remember { MutableStateFlow(emptySet<String>()) }
    val limitIn = remember { MutableStateFlow(HomeRecents.DEFAULT_LIMIT) }
    val usageIn = remember { MutableStateFlow(true) }
    val tick = remember { MutableStateFlow(0) }
    SideEffect {
        val me = android.os.Process.myUserHandle()
        appsIn.value = apps.map { RecentCandidate(it.key, it.packageName, it.user != me) }
        hiddenIn.value = hidden
        limitIn.value = prefs.recentsLimit
        usageIn.value = prefs.recentsUseUsageAccess
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick.value = tick.value + 1 }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val usage = remember(context) { UsageRecents(context) }
    val keys by (if (active) {
        remember(stores, usage) {
            HomeRecents.flow(
                own = stores.history.events, usageTick = tick.map { }, usageReader = { usage.sightings() }, apps = appsIn, hidden = hiddenIn,
                state = stores.recentsState.state, limit = limitIn, profile = flowOf(RecentsProfile.Both), useUsage = usageIn, selfPackage = context.packageName,
            )
        }
    } else remember { emptyFlow<List<String>>() }).collectAsStateWithLifecycle(initialValue = emptyList())
    val byKey = remember(apps) { apps.associateBy { it.key } }
    return remember(keys, byKey) { derivedStateOf { keys.mapNotNull(byKey::get) } }
}

/** HOM-22: whether the usage-access grant is on, read off the main thread and again after the home resumes (the user may have just granted it). */
@Composable
fun rememberUsageAccess(context: Context, active: Boolean): State<Boolean> {
    val resumes = remember { MutableStateFlow(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes.value = resumes.value + 1 }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val n by resumes.collectAsStateWithLifecycle()
    return produceState(initialValue = false, context, n, active) { if (active) value = withContext(Dispatchers.IO) { UsageRecents(context).hasAccess() } }
}
