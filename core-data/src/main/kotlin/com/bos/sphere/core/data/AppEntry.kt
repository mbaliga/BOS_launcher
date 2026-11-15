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
    /** HOM-10: the package is suspended (shown dimmed). */
    val suspended: Boolean = false,
    /** Bumped when the package is updated so cached icons are noticed as stale. */
    val versionStamp: Long = 0L,
    /** HOM-10: the app is archived (Android 15): still listed, its code is not on the device, launching restores it. */
    val archived: Boolean = false,
    private val iconLoader: () -> Drawable?,
) {
    /** Stable identity across reorders / refreshes (component + profile). */
    val key: String get() = "${componentName.flattenToShortString()}#${user.hashCode()}"

    /** Loads the (badged) icon on demand. May be called off the main thread. */
    fun loadIcon(): Drawable? = iconLoader()
}

/** HOM-10: what the public application flags say about a listed launcher entry. */
object AppFlags {
    /**
     * An archived app stays in the launcher list but is not installed for the user: the public
     * `FLAG_INSTALLED` is clear. (No hidden archive flag is read. Whether every build of Android 15
     * clears it for archived apps is a device check, see docs/status/w3-homeInfo.md.)
     */
    fun archived(applicationFlags: Int): Boolean = applicationFlags and android.content.pm.ApplicationInfo.FLAG_INSTALLED == 0

    fun suspended(applicationFlags: Int): Boolean = applicationFlags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED != 0
}
