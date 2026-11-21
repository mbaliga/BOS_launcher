package com.bos.sphere.core.catalog

/**
 * HOM-10 and HOM-06: how a home icon is drawn for the state of its package and profile. Pure, so
 * the rule is tested without a device; the grid icon reads it.
 */
enum class AppMark { None, Suspended, Archived, ProfilePaused }

data class AppPresentation(val mark: AppMark, val alpha: Float, val launchable: Boolean) {
    companion object {
        const val DIMMED = 0.45f

        /**
         * Paused profile wins (nothing in it can start), then suspended, then archived. An archived
         * app is drawn dimmed with the archive mark but stays launchable: the system restores it.
         */
        fun of(suspended: Boolean, archived: Boolean, profilePaused: Boolean): AppPresentation = when {
            profilePaused -> AppPresentation(AppMark.ProfilePaused, DIMMED, launchable = false)
            suspended -> AppPresentation(AppMark.Suspended, DIMMED, launchable = false)
            archived -> AppPresentation(AppMark.Archived, 0.7f, launchable = true)
            else -> AppPresentation(AppMark.None, 1f, launchable = true)
        }
    }
}
