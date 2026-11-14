package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HubNotification

/**
 * HOM-09: notification badges. A badge is a dot with an optional count; the count is the number of
 * unread, non-ongoing notifications of the package. Conversation-group summaries must already be
 * filtered out by the provider.
 */
data class NoteRef(val packageName: String, val unread: Boolean = true, val ongoing: Boolean = false, val profileId: Int = 0)

data class Badge(val count: Int) {
    /** Text inside the dot, or empty for a bare dot. */
    fun label(showCount: Boolean, max: Int = 99): String = when {
        !showCount -> ""
        count > max -> "$max+"
        else -> count.toString()
    }
}

object Badges {
    /** Badge per package (profile-qualified key "pkg#profile" is returned by [keyFor]). */
    fun compute(notes: List<NoteRef>): Map<String, Badge> =
        notes.filter { it.unread && !it.ongoing }
            .groupingBy { keyFor(it.packageName, it.profileId) }.eachCount()
            .mapValues { Badge(it.value) }

    fun keyFor(packageName: String, profileId: Int) = "$packageName#$profileId"

    fun forHub(list: List<HubNotification>, profileId: Int = 0): List<NoteRef> =
        list.map { NoteRef(it.packageName, it.unread, it.isOngoing, profileId) }

    /** Badge for a folder: the sum of its children. */
    fun forFolder(folder: Folder, badgeOf: (String) -> Badge?): Badge? =
        folder.keys.sumOf { badgeOf(it)?.count ?: 0 }.takeIf { it > 0 }?.let(::Badge)

    /** Newest first, at most [limit], for the long-press / right-click preview of one app. */
    fun preview(list: List<HubNotification>, packageName: String, limit: Int = 5): List<HubNotification> =
        list.filter { it.packageName == packageName && !it.isOngoing }.sortedByDescending { it.timestamp }.take(limit)
}
