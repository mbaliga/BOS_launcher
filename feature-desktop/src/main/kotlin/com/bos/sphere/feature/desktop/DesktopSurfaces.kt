package com.bos.sphere.feature.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide registry of what is open, so the offer logic never opens a duplicate and the phone panel
 * knows whether a desktop surface is focused. Rebuilt by the activities themselves after process death.
 */
object DesktopSurfaces {
    private val _surfaces = MutableStateFlow<Map<Any, SurfaceInfo>>(emptyMap())
    val surfaces: StateFlow<Map<Any, SurfaceInfo>> = _surfaces
    private val _homeDisplays = MutableStateFlow<Map<Any, Int>>(emptyMap())
    val homeDisplays: StateFlow<Map<Any, Int>> = _homeDisplays

    fun register(token: Any, info: SurfaceInfo) { _surfaces.value = _surfaces.value + (token to info) }
    fun unregister(token: Any) { _surfaces.value = _surfaces.value - token; focus.forget(token); refreshFocus() }
    fun moved(token: Any, currentDisplayId: Int) {
        _surfaces.value[token]?.let { _surfaces.value = _surfaces.value + (token to it.copy(currentDisplayId = currentDisplayId)) }
    }
    fun list(): List<SurfaceInfo> = _surfaces.value.values.toList()
    fun openOn(displayId: Int) = list().any { it.currentDisplayId == displayId || it.ownerDisplayId == displayId }

    /** One surface at a time is the focused one: window focus first, then the one shown last (EXT-03). */
    val focus = SurfaceFocus()
    fun setVisible(token: Any, v: Boolean) { focus.setVisible(token, v); refreshFocus() }
    fun setWindowFocus(token: Any, hasFocus: Boolean) { focus.setWindowFocus(token, hasFocus); refreshFocus() }
    /** True when [token] is the surface the phone panel drives; a surface with no token is never filtered out. */
    fun isFocused(token: Any?): Boolean = token == null || focus.isFocused(token)
    private fun refreshFocus() { DesktopInputBus.setFocused(focus.focused()?.let { it in _surfaces.value } == true) }

    /** Reports this launcher's own home activity as running on [displayId] (the SPK-01 positive case). */
    fun homeStarted(token: Any, displayId: Int) { _homeDisplays.value = _homeDisplays.value + (token to displayId) }
    fun homeStopped(token: Any) { _homeDisplays.value = _homeDisplays.value - token }
    fun homeDisplayIds(): Set<Int> = _homeDisplays.value.values.toSet()
}
