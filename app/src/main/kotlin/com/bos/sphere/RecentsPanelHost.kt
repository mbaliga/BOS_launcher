package com.bos.sphere

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeRecents
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RecentCandidate
import com.bos.sphere.core.data.RecentEntry
import com.bos.sphere.core.data.RecentsProfile
import com.bos.sphere.core.signals.UsageRecents
import com.bos.sphere.feature.grid.RecentRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * STR-14: the live rows of the Recents panel, newest first, from the ONE recents source of the Recent apps landing
 * ([HomeRecents], so the same removals, hidden apps, work badge rules and privacy class apply) plus the background events of
 * [UsageRecents] when usage access is on. Collected only while [active]; computed off the main thread.
 */
@Composable
fun rememberRecentRows(context: Context, stores: LauncherStores, apps: List<AppEntry>, hidden: Set<String>, prefs: LauncherPrefs, active: Boolean): State<List<RecentRow>> {
    val appsIn = remember { MutableStateFlow(emptyList<RecentCandidate>()) }
    val hiddenIn = remember { MutableStateFlow(emptySet<String>()) }
    val limitIn = remember { MutableStateFlow(HomeRecents.DEFAULT_LIMIT) }
    val usageIn = remember { MutableStateFlow(true) }
    val tick = remember { MutableStateFlow(0) }
    val profileIn = remember { MutableStateFlow(RecentsProfile.Both) }
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
    val entries by (if (active) {
        remember(stores, usage) {
            HomeRecents.entriesFlow(
                own = stores.timelineLog.events, usageTick = tick.map { }, usageReader = { usage.sightings() }, apps = appsIn, hidden = hiddenIn,
                state = stores.recentsState.state, limit = limitIn, profile = profileIn, useUsage = usageIn, selfPackage = context.packageName,
            )
        }
    } else remember { emptyFlow<List<RecentEntry>>() }).collectAsStateWithLifecycle(initialValue = emptyList())
    val byKey = remember(apps) { apps.associateBy { it.key } }
    return remember(entries, byKey) { derivedStateOf { entries.mapNotNull { e -> byKey[e.key]?.let { RecentRow(it, e.timeMillis) } } } }
}

/**
 * STR-14: with Right side set to Dock rail, a swipe in from the right edge reveals the existing dock rail ([onSwipe]); it never turns the
 * dock on. Pure pointer observation on the Initial pass: it consumes nothing, so a drag that is not this swipe reaches the content.
 * [rtl] puts the edge on the left (the mirror of the Recents panel).
 */
fun Modifier.rightEdgeSwipe(enabled: Boolean, rtl: Boolean = false, onSwipe: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(rtl) {
        val edgePx = 28f * density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val fromEdge = if (rtl) down.position.x else size.width - down.position.x
            if (fromEdge !in 0f..edgePx) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var dx = 0f; var dy = 0f
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.positionChange()
                dx += if (rtl) d.x else -d.x; dy += d.y
                if (dx > slop * 2 && dx > abs(dy) * 1.5f) { onSwipe(); break }
                if (abs(dy) > slop * 3 && abs(dy) > dx) break
            }
        }
    }
