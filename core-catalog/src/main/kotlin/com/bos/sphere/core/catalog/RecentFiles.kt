package com.bos.sphere.core.catalog

/**
 * HOM-15: recent files from granted sources (folder grants the user gave through the system
 * picker) and pinned folders. Pure merge and ranking; [RecentFilesSource] reads the real files.
 */
data class RecentFile(
    val uri: String,
    val name: String,
    val mime: String = "",
    val modifiedMs: Long,
    /** The granted tree this came from. */
    val source: String,
)

data class PinnedFolder(val uri: String, val name: String)

object RecentFilesModel {
    /** Newest first across all sources, one entry per URI, at most [limit]. */
    fun merge(perSource: List<List<RecentFile>>, limit: Int = 20): List<RecentFile> =
        perSource.flatten().groupBy { it.uri }.map { (_, v) -> v.maxBy { it.modifiedMs } }
            .sortedWith(compareByDescending<RecentFile> { it.modifiedMs }.thenBy { it.name.lowercase() })
            .take(limit)

    /** Hides entries from sources whose grant is gone, so a revoked folder leaves nothing behind. */
    fun withinGrants(files: List<RecentFile>, grantedSources: Set<String>): List<RecentFile> = files.filter { it.source in grantedSources }

    /** "2 h ago", "yesterday" ... short age text for the panel. */
    fun age(nowMs: Long, thenMs: Long): String {
        val d = (nowMs - thenMs).coerceAtLeast(0) / 1000
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86_400 -> "${d / 3600} h ago"
            d < 172_800 -> "yesterday"
            else -> "${d / 86_400} days ago"
        }
    }
}
