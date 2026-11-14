package com.bos.sphere.feature.desktop

import com.bos.sphere.core.launch.LaunchCaps
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget

/** Launch helpers for the desktop surface; every launch is a core-launch [LaunchRequest] (EXT-07, WSP-01). */
object DesktopLaunch {
    const val PRIMARY = 0

    fun request(packageName: String, className: String?, userId: Int, displayId: Int): LaunchRequest =
        LaunchRequest(LaunchTarget(packageName, className, userId), displayId = displayId)

    /** Launch capabilities from the tracked displays: hosting displays are the eligible external ones plus the primary. */
    fun caps(state: TrackerState, boundsHonoured: Boolean, adjacentSupported: Boolean? = null): LaunchCaps {
        val primary = state.primary?.id ?: PRIMARY
        return LaunchCaps(
            displayIds = state.displays.keys + primary,
            hostingDisplayIds = state.eligible.map { it.id }.toSet() + primary,
            boundsHonoured = boundsHonoured,
            adjacentSupported = adjacentSupported,
            defaultDisplayId = primary,
        )
    }

    /** The other display to open on from [current]: the phone from the desktop, the first eligible external one from the phone. */
    fun otherDisplay(state: TrackerState, current: Int): Int? {
        val primary = state.primary?.id ?: PRIMARY
        return if (current != primary) primary else state.eligible.firstOrNull()?.id
    }
}
