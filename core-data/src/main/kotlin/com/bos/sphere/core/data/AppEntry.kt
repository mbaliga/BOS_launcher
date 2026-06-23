package com.bos.sphere.core.data

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

/**
 * A launchable app on the surface.
 *
 * Icons are loaded lazily through [loadIcon] rather than held eagerly: an inventory can run
 * to hundreds of entries and we only ever paint the handful near the focal point. Keeps the
 * model cheap to diff in a `Flow`. [user] carries the profile so work-profile / secondary-user
 * apps round-trip correctly to [LauncherAppsAppRepository.launch].
 */
data class AppEntry(
    val packageName: String,
    val componentName: ComponentName,
    val label: String,
    val user: UserHandle,
    private val iconLoader: () -> Drawable?,
) {
    /** Stable identity across reorders / refreshes (component + profile). */
    val key: String get() = "${componentName.flattenToShortString()}#${user.hashCode()}"

    /** Loads the (badged) icon on demand. May be called off the main thread. */
    fun loadIcon(): Drawable? = iconLoader()
}
