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
    fun unregister(token: Any) { _surfaces.value = _surfaces.value - token; refreshFocus() }
    fun moved(token: Any, currentDisplayId: Int) {
        _surfaces.value[token]?.let { _surfaces.value = _surfaces.value + (token to it.copy(currentDisplayId = currentDisplayId)) }
    }
    fun list(): List<SurfaceInfo> = _surfaces.value.values.toList()
    fun openOn(displayId: Int) = list().any { it.currentDisplayId == displayId || it.ownerDisplayId == displayId }

    private val visible = mutableSetOf<Any>()
    /** A surface counts as focused while it is visible; the platform's own focus rules decide the rest on device. */
    fun setVisible(token: Any, v: Boolean) { if (v) visible += token else visible -= token; refreshFocus() }
    private fun refreshFocus() { DesktopInputBus.setFocused(visible.any { it in _surfaces.value }) }

    /** Reports this launcher's own home activity as running on [displayId] (the SPK-01 positive case). */
    fun homeStarted(token: Any, displayId: Int) { _homeDisplays.value = _homeDisplays.value + (token to displayId) }
    fun homeStopped(token: Any) { _homeDisplays.value = _homeDisplays.value - token }
    fun homeDisplayIds(): Set<Int> = _homeDisplays.value.values.toSet()
}
