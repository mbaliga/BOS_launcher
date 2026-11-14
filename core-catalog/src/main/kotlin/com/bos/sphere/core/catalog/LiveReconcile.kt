package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.AppEntry

/**
 * HOM-10: the live path from a launcher inventory snapshot to what the surface does about it.
 * Each snapshot is diffed against the previous one; the result says which cached icons to drop and
 * which stored keys are gone. [onSnapshot] is cheap enough to run on every callback, so the
 * surface is current as soon as the snapshot arrives.
 */
class LiveReconcile {
    private var previous: List<AppRecord>? = null
    var lastDiff: CatalogDiff = CatalogDiff(emptyList(), emptyList(), emptyList()); private set

    /** The diff against the previous snapshot (empty for the first one). */
    fun onSnapshot(apps: List<AppEntry>): CatalogDiff {
        val now = apps.map(::recordOf)
        val before = previous
        previous = now
        lastDiff = if (before == null) CatalogDiff(emptyList(), emptyList(), emptyList()) else PackageReconcile.diff(before, now)
        return lastDiff
    }

    companion object {
        fun recordOf(a: AppEntry) = AppRecord(
            key = a.key, packageName = a.packageName, label = a.label, profileId = a.user.hashCode(), suspended = a.suspended, versionStamp = a.versionStamp,
        )
    }
}
