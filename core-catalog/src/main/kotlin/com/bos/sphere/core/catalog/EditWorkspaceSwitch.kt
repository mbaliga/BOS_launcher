package com.bos.sphere.core.catalog

/**
 * HOM-12 (wave 24): a workspace switch while edit mode is on. The switch swaps in the other workspace's pages and dock through the
 * workspace hooks; recorded as an edit, one Undo would have written the left workspace's arrangement into the new one. Like leaving
 * the Selected apps landing, the switch ends the edit session instead: the history goes, the switched-to arrangement is what shows.
 * The pin list and the layout reach the edit state as two separate updates in either order; ending on the first sign of the new
 * workspace (its id on the pin view) covers both orders, since an ended session records nothing.
 */
object EditWorkspaceSwitch {
    /** True when an [active] session that started on [sessionWorkspace] now sees the pins of [seenWorkspace]. */
    fun endsEdit(active: Boolean, sessionWorkspace: String?, seenWorkspace: String): Boolean =
        active && sessionWorkspace != null && sessionWorkspace.isNotEmpty() && seenWorkspace.isNotEmpty() && seenWorkspace != sessionWorkspace
}
