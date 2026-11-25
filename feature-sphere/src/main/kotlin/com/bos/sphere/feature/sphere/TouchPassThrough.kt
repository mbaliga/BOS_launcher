package com.bos.sphere.feature.sphere

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Regions of the home surface whose touches belong to their own content rather than to the
 * surface's gestures, e.g. the hub's widget stack, where hosted widgets take taps and scroll their
 * own lists. A gesture that *starts* inside a registered region is left entirely alone by
 * [LauncherSurface]: no scrub, no hub pull or close, no long-press.
 *
 * Regions are kept as live [LayoutCoordinates] and tested at touch-down, so they follow layout
 * and layer transforms (the hub slides in through a `graphicsLayer` translation).
 */
@Stable
class TouchPassThrough {
    private val regions = HashMap<Any, LayoutCoordinates>()

    fun register(key: Any, coordinates: LayoutCoordinates) {
        regions[key] = coordinates
    }

    fun unregister(key: Any) {
        regions.remove(key)
    }

    /** True when [position] (local to [source]) falls inside any attached region. */
    internal fun contains(source: LayoutCoordinates?, position: Offset): Boolean {
        if (source == null || !source.isAttached) return false
        return regions.values.any { region ->
            if (!region.isAttached) return@any false
            val p = runCatching { region.localPositionOf(source, position) }.getOrNull()
                ?: return@any false
            p.x >= 0f && p.y >= 0f &&
                p.x <= region.size.width.toFloat() && p.y <= region.size.height.toFloat()
        }
    }
}

/**
 * Registers this element as a [TouchPassThrough] region under [key]. The caller unregisters the
 * key when the element leaves composition (a `DisposableEffect`), so stale regions never linger.
 */
fun Modifier.touchPassThrough(registry: TouchPassThrough, key: Any): Modifier =
    onGloballyPositioned { registry.register(key, it) }
