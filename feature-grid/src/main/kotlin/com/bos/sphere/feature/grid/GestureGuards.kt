package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.input.SystemGestureZones

/**
 * INP-08: a one-finger swipe that starts inside a band the system uses (bottom edge = home and recents, side edges =
 * back, top edge = shade) is never a launcher gesture, so the defaults cannot clash with the system's. Documented in
 * docs/status/input.md.
 */
internal fun Density.swipeStartsInSystemZone(start: Offset, size: IntSize, g: HomeGesture): Boolean {
    val swipe = when (g) {
        HomeGesture.SwipeUp -> SystemGestureZones.Swipe.Up
        HomeGesture.SwipeDown -> SystemGestureZones.Swipe.Down
        HomeGesture.SwipeLeft -> SystemGestureZones.Swipe.Left
        HomeGesture.SwipeRight -> SystemGestureZones.Swipe.Right
        else -> return false
    }
    return SystemGestureZones.conflicts(
        start.x, start.y, size.width.toFloat(), size.height.toFloat(), swipe,
        side = SystemGestureZones.SIDE_DP * density, bottom = SystemGestureZones.BOTTOM_DP * density, top = SystemGestureZones.TOP_DP * density,
    )
}
