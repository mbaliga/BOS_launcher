package com.bos.sphere

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.catalog.ArrangedFiles
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FileSourceIds
import com.bos.sphere.core.catalog.FilesArrange
import com.bos.sphere.core.catalog.FilesCollectionPrefs
import com.bos.sphere.core.catalog.FilesSources
import com.bos.sphere.core.catalog.FilesViewMode
import com.bos.sphere.core.catalog.HomePanelPrefs
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.palette.FileGrants
import com.bos.sphere.feature.search.palette.FileIndexRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * W53 (filesCollections): the Files section as the owner sees it: a switcher of sources (All, the collections, each folder), sort and group, a list or
 * a grid. The files come from the Radar file index (the folders the owner granted, listed to a depth of eight levels, plus the media listing when the
 * system permission was granted elsewhere); no storage permission is requested here. Everything is read and arranged off the main thread.
 */

/** What the section knows about the device: the granted folders and every indexed file. */
internal class FilesSnapshot(val folders: List<PinnedFolder>, val files: List<RecentFile>) {
    val trees: Set<String> get() = folders.map { it.uri }.toSet()
}

/** The data seam: tests install a fake with [LocalFilesData]. All calls block; they run on the IO pool. */
internal interface FilesData {
    /** What the index holds right now (fast: it is in memory or one small file). */
    fun load(): FilesSnapshot
    /** True when a fresh scan of the folders is due (once per process, or a folder was added). */
    fun needsReindex(): Boolean
    /** Scans the granted folders into the index, then returns [load]. */
    fun reindex(): FilesSnapshot
}

internal val LocalFilesData = staticCompositionLocalOf<FilesData?> { null }

internal class RealFilesData(context: Context) : FilesData {
    private val ctx = context.applicationContext

    override fun load(): FilesSnapshot {
        val trees = FileGrants.trees(ctx)
        val names = trees.associate { it.toString() to folderLabel(it) }
        val rows = FileIndexRows.all(ctx)
        val files = rows.map { r -> FilesSources.fromIndex(r.uri, r.name, r.mime, r.size, r.modified, r.source, r.folder) { t -> names[t] ?: t } }
        return FilesSnapshot(trees.map { PinnedFolder(it.toString(), names.getValue(it.toString())) }, files)
    }

    override fun needsReindex(): Boolean = FileGrants.trees(ctx).isNotEmpty() && (!reindexed.get() || FileIndexRows.unindexedTrees(ctx).isNotEmpty())

    override fun reindex(): FilesSnapshot {
        runCatching { FileIndexRows.reindex(ctx) }
        reindexed.set(true)
        return load()
    }

    private companion object {
        val reindexed = AtomicBoolean(false)
        fun folderLabel(tree: Uri): String =
            runCatching { Uri.decode(tree.lastPathSegment.orEmpty()).substringAfterLast(':').substringAfterLast('/').ifEmpty { tree.toString() } }.getOrDefault(tree.toString())
    }
}

/** What the section needs from the screen around it. */
internal class FilesActions(
    val pickFolder: () -> Unit,
    val openFile: (RecentFile) -> Unit,
    val openFolder: (PinnedFolder) -> Unit,
)

private enum class FilesSheet { None, SortGroup, Chooser }

/**
 * The stateful Files section. [reload] changes when a folder was added (so the data is read again). [clock] is the time used for ages and date
 * buckets (a test clock in tests).
 */
@Composable
internal fun FilesSection(
    homePrefs: HomePanelPrefs, store: FilesCollectionPrefs, actions: FilesActions, reload: Int = 0, clock: () -> Long = System::currentTimeMillis,
    bright: Color = HyleColors.InkBright, ink: Color = HyleColors.Ink, dim: Color = HyleColors.InkDim, accent: Color = HyleColors.Violet, ground: Color = HyleColors.Surface,
    inset: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
) {
    val context = LocalContext.current
    val data = LocalFilesData.current ?: remember { RealFilesData(context) }
    var snapshot by remember { mutableStateOf<FilesSnapshot?>(null) }
    var indexing by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        snapshot = withContext(Dispatchers.IO) { data.load() }
        if (withContext(Dispatchers.IO) { data.needsReindex() }) {
            indexing = true
            snapshot = withContext(Dispatchers.IO) { data.reindex() }
            indexing = false
        }
    }
    var collections by remember { mutableStateOf(store.collections) }
    var selectedRaw by remember { mutableStateOf(store.source) }
    var optRev by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(FilesSheet.None) }
    var chooserStart by remember { mutableStateOf<ChooserStart>(ChooserStart.List) }
    var mode by remember { mutableStateOf(homePrefs.filesView) }

    var folderOrder by remember { mutableStateOf(store.folderOrder) }
    // W54: the folder chips follow the order the owner dragged them into; a folder not named there goes after the named ones.
    val folders = FilesChipReorder.orderBy(snapshot?.folders.orEmpty(), folderOrder) { it.uri }
    val shown = collections.resolved()
    // A source that is gone (a removed collection, a folder whose grant was taken back) falls back to All; nothing is written for it.
    val selected = when {
        selectedRaw == FileSourceIds.ALL -> FileSourceIds.ALL
        FileSourceIds.isFolder(selectedRaw) -> if (snapshot == null || folders.any { FileSourceIds.folder(it.uri) == selectedRaw }) selectedRaw else FileSourceIds.ALL
        shown.any { it.id == selectedRaw } -> selectedRaw
        else -> FileSourceIds.ALL
    }
    val options = remember(selected, optRev) { store.optionsFor(selected) }
    val now = remember(snapshot) { clock() }

    val arranged by produceState<ArrangedFiles?>(null, snapshot, selected, collections, options) {
        val snap = snapshot ?: return@produceState
        value = withContext(Dispatchers.Default) {
            FilesArrange.arrange(FilesSources.select(snap.files, selected, collections, now, snap.trees), options, now)
        }
    }

    val chips = buildList {
        add(FilesChip(FileSourceIds.ALL, L10n.tr("All"), Glyph.Grid))
        shown.forEach { c -> add(FilesChip(c.id, if (c.builtIn != null) L10n.tr(c.name) else c.name, glyphOfCollection(c.builtIn))) }
        folders.forEach { f -> add(FilesChip(FileSourceIds.folder(f.uri), f.name, Glyph.Folder)) }
    }
    val selectedFolder = if (FileSourceIds.isFolder(selected)) folders.firstOrNull { FileSourceIds.folder(it.uri) == selected } else null
    val selectedCustom = collections.custom.firstOrNull { it.id == selected }

    FilesPanelContent(
        chips, selected, arranged, indexing, folders.isNotEmpty(), options, mode, now, bright, ink, dim, accent,
        onSelect = { selectedRaw = it; store.source = it; optRev++ },
        onAdd = { chooserStart = ChooserStart.List; sheet = FilesSheet.Chooser },
        onToggle = { mode = mode.other(); homePrefs.filesView = mode },
        onSortGroup = { sheet = FilesSheet.SortGroup },
        onChoose = actions.pickFolder,
        onOpenFolder = selectedFolder?.let { f -> { actions.openFolder(f) } },
        onEdit = selectedCustom?.let { c -> { chooserStart = ChooserStart.Edit(c.id); sheet = FilesSheet.Chooser } },
        onFileOpen = actions.openFile, ground = ground, inset = inset,
        onMoveChip = { id, to ->
            // A chip moves among its own kind: the collections keep their order in the collection store, the folders in the folder order.
            val at = chips.indexOfFirst { it.id == id }
            val range = if (at >= 0) movableRange(chips, at) else null
            if (range != null && to in range) {
                val ids = chips.slice(range).map { it.id }
                val next = FilesChipReorder.move(ids, at - range.first, to - range.first)
                if (FileSourceIds.isFolder(id)) {
                    val uris = next.map { FileSourceIds.treeOf(it) }
                    folderOrder = uris; store.folderOrder = uris
                } else {
                    val c = collections.copy(shown = FilesChipReorder.replaceResolved(collections.shown, ids.toSet(), next))
                    collections = c; store.collections = c
                }
            }
        },
    )
    when (sheet) {
        FilesSheet.SortGroup -> SortGroupSheet(
            options, showAdded = snapshot?.files?.any { it.addedMs >= 0 } == true, ownChoice = store.ownOptions(selected) != null, accent = accent,
            onChange = { store.setOptions(selected, it); optRev++ },
            onUseForAll = { store.setDefaultForAll(options); optRev++ },
            onReset = { store.clearOptions(selected); optRev++ },
            onDone = { sheet = FilesSheet.None },
        )
        FilesSheet.Chooser -> FilesChooser(
            collections, folders, accent, chooserStart,
            onChange = { next ->
                // A collection that was removed also loses what it had chosen for itself.
                collections.custom.map { it.id }.filter { id -> next.custom.none { it.id == id } }.forEach { store.forget(it) }
                collections = next; store.collections = next
            },
            onAddFolder = actions.pickFolder, onClose = { sheet = FilesSheet.None },
        )
        FilesSheet.None -> Unit
    }
}
