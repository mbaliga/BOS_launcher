package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.AppEntry

/**
 * Wave 48 (workProfile): the work-profile rules of the disc app drawer as pure functions. The disc has a "Work" scope label next to its grouping
 * labels (only while a work profile exists); with it on, the disc lists the work apps only, and a pause pill turns the work profile off or on
 * through the system's quiet mode ([WorkProfileState]). A paused work app stays listed (dimmed, marked) so the profile can be resumed from the
 * drawer, as the flat drawer's Work tab did; it cannot be launched until then.
 */
object WorkDrawerRules {
    /** The Work scope label is offered only while a work profile exists ([workPaused] is null without one). */
    fun offered(workPaused: Boolean?): Boolean = workPaused != null

    /** Apps the disc lists: only the work profile's when the scope is on (and offered), else all of them. */
    fun scoped(apps: List<AppEntry>, workUsers: Set<Int>, on: Boolean, workPaused: Boolean?): List<AppEntry> =
        if (on && offered(workPaused)) apps.filter { it.user.hashCode() in workUsers } else apps

    /** The scope really applies only while offered; a stored "on" without a work profile shows everything and no label. */
    fun effective(on: Boolean, workPaused: Boolean?): Boolean = on && offered(workPaused)

    /** Whether [app] is dimmed and cannot start: its profile is in quiet mode. */
    fun paused(app: AppEntry, quietUsers: Set<Int>): Boolean = app.user.hashCode() in quietUsers

    /** The label of the pause pill, or null when it is not shown (scope off or no work profile). */
    fun pillLabel(on: Boolean, workPaused: Boolean?): String? = if (effective(on, workPaused)) WorkProfileState.chipLabel(workPaused) else null
}
