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
    private var lastId: String? = null

    /**
     * The home's own focus registry (the input stream's graph): which element has focus now, and how to give
     * focus to one by id. Set by the app, so this module does not depend on the grid module.
     */
    var currentId: (() -> String?)? = null
    var requestId: ((String) -> Boolean)? = null

    fun remember(r: FocusRequester) { last = r }

    /** Notes the registered home element that has focus right now (call as Launchpad opens). */
    fun capture() { lastId = currentId?.invoke() }

    /** Returns true when focus went back to the element that had it. */
    fun restore(): Boolean {
        val id = lastId
        if (id != null && requestId?.invoke(id) == true) return true
        return runCatching { last?.requestFocus(); last != null }.getOrDefault(false)
    }
}

val LocalLaunchpadFocusTrail = compositionLocalOf<LaunchpadFocusTrail?> { null }

fun Modifier.trackedForLaunchpad(trail: LaunchpadFocusTrail, requester: FocusRequester): Modifier =
    this.focusRequester(requester).onFocusChanged { if (it.isFocused) trail.remember(requester) }

/** Gives focus back to the home element that had it, when this composable leaves the tree. */
@androidx.compose.runtime.Composable
fun RestoreFocusOnExit() {
    val trail = LocalLaunchpadFocusTrail.current
    androidx.compose.runtime.remember(trail) { trail?.capture() }
    DisposableEffect(trail) { onDispose { trail?.restore() } }
}
