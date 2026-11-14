package com.bos.sphere.feature.search.palette

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns as Cols
import dev.aarso.search.palette.FileEntry
import dev.aarso.search.palette.FileIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** One shared in-memory index, loaded from and saved to app-private storage (survives reboot, PAL-11). */
object FileIndexHolder {
    @Volatile private var index: FileIndex? = null

    private fun file(c: Context) = File(c.filesDir, "palette-files.idx")

    fun get(context: Context): FileIndex = index ?: synchronized(this) {
        index ?: FileIndex.decode(runCatching { file(context.applicationContext).readText() }.getOrNull()).also { index = it }
    }

    fun save(context: Context) {
        val f = file(context.applicationContext)
        val tmp = File(f.parentFile, f.name + ".tmp")
        runCatching { tmp.writeText(get(context).encode()); tmp.renameTo(f) }
    }
}

/** Folders the user granted through the system picker; persisted by the system, so they survive reboot. No all-files access. */
object FileGrants {
    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    /** Call with the picker result so the grant outlives this process. */
    fun persist(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun trees(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }.map { it.uri }

    fun release(context: Context, tree: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        FileIndexHolder.get(context).dropSource("tree:$tree")
        FileIndexHolder.save(context)
    }
}

/** Lists MediaStore and granted folders and reconciles them into the index. Runs off the main thread. */
object FileIndexer {
    private const val MAX_PER_SOURCE = 20_000
    private const val MAX_DEPTH = 8

    fun hasAccess(c: Context): Boolean =
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE)
            .any { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } || FileGrants.trees(c).isNotEmpty()

    /** Full incremental pass; [isActive] lets the job stop early when the system cancels it. */
    fun reconcile(context: Context, isActive: () -> Boolean = { true }): FileIndex.Diff {
        val idx = FileIndexHolder.get(context)
        var added = 0; var removed = 0; var changed = 0
        fun add(d: FileIndex.Diff) { added += d.added; removed += d.removed; changed += d.changed }
        val mediaGranted = listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (mediaGranted && isActive()) runCatching { val l = scanMedia(context); if (isActive()) add(idx.reconcile("media", l)) }
        val trees = FileGrants.trees(context)
        for (t in trees) { if (!isActive()) return FileIndex.Diff(added, removed, changed); runCatching { val l = scanTree(context, t, isActive); if (isActive()) add(idx.reconcile("tree:$t", l)) } }
        // Revoked folders disappear from the index.
        val live = trees.map { "tree:$it" }.toSet()
        idx.sources().filter { it.startsWith("tree:") && it !in live }.forEach { removed += idx.dropSource(it) }
        FileIndexHolder.save(context)
        return FileIndex.Diff(added, removed, changed)
    }

    private fun scanMedia(c: Context): List<FileEntry> {
        val base = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val out = ArrayList<FileEntry>()
        c.contentResolver.query(base, arrayOf(Cols._ID, Cols.DISPLAY_NAME, Cols.MIME_TYPE, Cols.SIZE, Cols.DATE_MODIFIED, Cols.RELATIVE_PATH), "${Cols.MIME_TYPE} IS NOT NULL", null, "${Cols.DATE_MODIFIED} DESC")?.use { q ->
            while (q.moveToNext() && out.size < MAX_PER_SOURCE) {
                val name = q.getString(1) ?: continue
                val uri = android.content.ContentUris.withAppendedId(base, q.getLong(0)).toString()
                val rel = q.getString(5)?.trimEnd('/')?.substringAfterLast('/').orEmpty()
                out += FileEntry(uri, name, q.getString(2), q.getLong(3), q.getLong(4), "media", rel)
            }
        }
        return out
    }

    private fun scanTree(c: Context, tree: Uri, isActive: () -> Boolean): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        val source = "tree:$tree"
        val queue = ArrayDeque<Triple<String, String, Int>>()
        queue.add(Triple(DocumentsContract.getTreeDocumentId(tree), "", 0))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        while (queue.isNotEmpty() && out.size < MAX_PER_SOURCE && isActive()) {
            val (docId, folder, depth) = queue.removeFirst()
            c.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { q ->
                while (q.moveToNext() && out.size < MAX_PER_SOURCE) {
                    val id = q.getString(0); val name = q.getString(1) ?: continue; val mime = q.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) queue.add(Triple(id, name, depth + 1))
                    } else {
                        out += FileEntry(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name, mime, q.getLong(3), q.getLong(4), source, folder)
                    }
                }
            }
        }
        return out
    }
}

/** Background reconciliation (PAL-11): battery and storage not low, about every six hours, rescheduled at launcher start. */
class FileIndexJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            try { FileIndexer.reconcile(applicationContext) { isActive } } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

object FileIndexScheduler {
    private const val JOB_ID = 0x7A11

    fun schedule(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, FileIndexJobService::class.java))
            .setPeriodic(6L * 3600_000L)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        js.schedule(info)
    }
}
