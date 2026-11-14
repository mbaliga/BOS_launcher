package com.bos.sphere.feature.desktop

/**
 * Which desktop surface has focus when more than one is open (EXT-03). A surface that has window focus wins; otherwise the
 * surface that became visible last does. The phone's trackpad panel only drives that one, so two displays never both react.
 * Pure state, so it is tested without a device.
 */
class SurfaceFocus {
    private val visible = LinkedHashSet<Any>()
    private var windowFocused: Any? = null

    fun setVisible(token: Any, v: Boolean) {
        if (v) { visible.remove(token); visible.add(token) } else { visible.remove(token); if (windowFocused === token) windowFocused = null }
    }

    /** Called from the activity's `onWindowFocusChanged`. */
    fun setWindowFocus(token: Any, hasFocus: Boolean) {
        if (hasFocus) windowFocused = token else if (windowFocused === token) windowFocused = null
    }

    fun forget(token: Any) { visible.remove(token); if (windowFocused === token) windowFocused = null }

    /** The surface the phone panel drives right now, or null when none is visible. */
    fun focused(): Any? = windowFocused?.takeIf { it in visible } ?: visible.lastOrNull()

    fun anyFocused(): Boolean = focused() != null
    fun isFocused(token: Any): Boolean = focused() === token
}
