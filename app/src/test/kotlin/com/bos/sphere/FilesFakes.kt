package com.bos.sphere

import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile

/** A stand-in for the Radar file index: the Files section reads these instead of real folders (tests never touch real files). */
internal class FakeFilesData(var snap: FilesSnapshot, private val afterReindex: FilesSnapshot? = null) : FilesData {
    var reindexed = false
    var loads = 0
    override fun load(): FilesSnapshot { loads++; return if (reindexed && afterReindex != null) afterReindex else snap }
    override fun needsReindex(): Boolean = afterReindex != null && !reindexed
    override fun reindex(): FilesSnapshot { reindexed = true; return afterReindex ?: snap }
}

internal fun snapshotOf(vararg folders: PinnedFolder, files: List<RecentFile>) = FilesSnapshot(folders.toList(), files)
