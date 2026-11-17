package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent

/** Wave 18 test support: a launch log in which the n-th app is opened (n + 1 downTo 1) times, so the first app is the most used. */
internal object RadarTestHabits {
    const val NOW = 1_800_000_000_000L
    private const val DAY = 86_400_000L

    /** [apps] in the order of use: the first is opened most, the last least, each at different days. */
    fun history(apps: List<AppEntry>): List<LaunchEvent> = apps.withIndex().flatMap { (i, app) ->
        List(apps.size - i) { k -> LaunchEvent(app.key, NOW - (i + k) * DAY / 2, 12) }
    }.sortedBy { it.timeMillis }
}
