package com.bos.sphere.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * STR-08: remembers which home element had focus when Launchpad opened, so Esc can give it back. Home elements
 * opt in with [Modifier.trackedForLaunchpad]; the panel calls [restore] when it leaves.
 */
class LaunchpadFocusTrail {
    internal var last: FocusRequester? = null

    fun remember(r: FocusRequester) { last = r }

    /** Returns true when focus went back to the element that had it. */
    fun restore(): Boolean = runCatching { last?.requestFocus(); last != null }.getOrDefault(false)
}

val LocalLaunchpadFocusTrail = compositionLocalOf<LaunchpadFocusTrail?> { null }

fun Modifier.trackedForLaunchpad(trail: LaunchpadFocusTrail, requester: FocusRequester): Modifier =
    this.focusRequester(requester).onFocusChanged { if (it.isFocused) trail.remember(requester) }

/** Gives focus back to the home element that had it, when this composable leaves the tree. */
@androidx.compose.runtime.Composable
fun RestoreFocusOnExit() {
    val trail = LocalLaunchpadFocusTrail.current
    DisposableEffect(trail) { onDispose { trail?.restore() } }
}
