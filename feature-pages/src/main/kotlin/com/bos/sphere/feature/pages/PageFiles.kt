package com.bos.sphere.feature.pages

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/** What the importer stored about an HTML page or package (in `record.json` beside its files). */
data class ImportRecord(
    val name: String,
    val isPackage: Boolean,
    val manifest: WidgetManifest,
    val isOrrery: Boolean,
    val mentionedHosts: List<String>,
    val bytes: Long,
    val sourceName: String?,
    val importedAt: Long,
    val warnings: List<ManifestIssue>,
)

sealed interface ImportFailure {
    data class Manifest(val issues: List<ManifestIssue>) : ImportFailure
    data class Layout(val problem: PackageProblem, val detail: String) : ImportFailure
    data object NotHtml : ImportFailure
    data object TooLarge : ImportFailure
    data object Unreadable : ImportFailure
}

sealed interface ImportResult {
    data class Ok(val record: ImportRecord) : ImportResult
    data class Failed(val failure: ImportFailure) : ImportResult
}

/**
 * Imported HTML pages and widget packages, each in `noBackupFilesDir/pages/<page id>/` (`record.json`
 * + `content/`; the disk layout and its rules are [PageDirs]). Imports come only from documents the
 * user picked in the system file picker. A re-import builds the new files beside the old ones and
 * swaps them in, so a failed import leaves the page as it was, and no import error can reach the
 * launcher (it becomes [ImportFailure.Unreadable]). All methods except [record] (cached after its first look) do
 * file work: call them off the main thread.
 *
 * Why `noBackupFilesDir`: a big package could push the app's Auto Backup past its 25 MB quota, which
 * would make the whole backup (widget placements included) fail. After a restore, the page list comes
 * back but html pages say their files need importing again.
 */
class PageFiles(context: Context) {
    private val app = context.applicationContext
    private val resolver: ContentResolver = app.contentResolver
    private val dirs = PageDirs(File(app.noBackupFilesDir, "pages"))
    private val records = ConcurrentHashMap<String, ImportRecord>()

    /** Pages whose record was looked for and isn't there (so titles don't hit the disk every frame). */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** Prepared documents of the most recently served pages; bounded, so a big page's bytes don't stay on the heap. */
    private val prepared = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > MAX_PREPARED
    }

    fun contentDir(pageId: String): File = dirs.contentDir(pageId)

    fun hasFiles(pageId: String): Boolean = dirs.recordFile(pageId).isFile

    /**
     * The page's import record, or null. Successes and misses are both cached, so after the first
     * look this never touches the disk (it is called while composing titles).
     */
    fun record(pageId: String): ImportRecord? {
        records[pageId]?.let { return it }
        if (pageId in missing) return null
        val r = runCatching { PageDirs.decodeRecord(dirs.recordFile(pageId).readText()) }.getOrNull()
        if (r != null) records[pageId] = r else missing += pageId
        return r
    }

    /** Imports a picked file: a zip is read as a D-AC package, anything else as a single-file page. */
    fun importFile(pageId: String, uri: Uri): ImportResult = guarded {
        val name = displayName(uri)
        val head = runCatching { resolver.openInputStream(uri)?.use { PageDirs.readUpTo(it, 4) } }.getOrNull()
            ?: return@guarded ImportResult.Failed(ImportFailure.Unreadable)
        if (PackageLayout.isZip(head)) importZip(pageId, uri, name) else importHtml(pageId, uri, name)
    }

    /** Imports a picked folder (a tree from the system picker) as a D-AC package. */
    fun importFolder(pageId: String, tree: Uri): ImportResult = guarded {
        val listing = runCatching { listTree(tree) }.getOrNull()
            ?: return@guarded ImportResult.Failed(ImportFailure.Unreadable)
        if (listing.size > PackageLayout.MAX_FILES * 2) return@guarded ImportResult.Failed(ImportFailure.Layout(PackageProblem.TooManyFiles, ""))
        val layout = PackageLayout.select(listing.map { PackageEntry(it.key, it.value.second) })
        val ok = layout as? LayoutCheck.Ok
            ?: return@guarded (layout as LayoutCheck.Invalid).let { ImportResult.Failed(ImportFailure.Layout(it.problem, it.detail)) }
        stored(pageId, dirs.writePackage(pageId, ok, folderName(tree), System.currentTimeMillis()) { source ->
            resolver.openInputStream(listing.getValue(source).first)
        })
    }

    /** Whatever an import throws (a provider failing mid-read, a full disk) is a failed import, never a crash. */
    private inline fun guarded(block: () -> ImportResult): ImportResult = try {
        block()
    } catch (_: java.io.IOException) {
        ImportResult.Failed(ImportFailure.Unreadable)
    } catch (_: RuntimeException) {
        ImportResult.Failed(ImportFailure.Unreadable)
    }

    private fun importZip(pageId: String, uri: Uri, name: String?): ImportResult {
        val tmp = File(app.cacheDir, "import-$pageId.zip")
        try {
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { PageDirs.copyCapped(input, it, MAX_ZIP_BYTES) } }
            }.getOrNull() ?: return ImportResult.Failed(ImportFailure.Unreadable)
            if (copied < 0) return ImportResult.Failed(ImportFailure.TooLarge)
            val zip = runCatching { ZipFile(tmp) }.getOrNull() ?: return ImportResult.Failed(ImportFailure.Unreadable)
            zip.use { z ->
                // Counted from the central directory before any entry object is made: a zip of a
                // huge number of tiny entries is refused without filling the launcher's heap.
                if (z.size() > PackageLayout.MAX_FILES * 4) return ImportResult.Failed(ImportFailure.Layout(PackageProblem.TooManyFiles, ""))
                val entries = z.entries().toList()
                val layout = PackageLayout.select(entries.map { PackageEntry(it.name, it.size) })
                val ok = layout as? LayoutCheck.Ok
                    ?: return (layout as LayoutCheck.Invalid).let { ImportResult.Failed(ImportFailure.Layout(it.problem, it.detail)) }
                val byName = entries.associateBy { PackageLayout.safePath(it.name) }
                return stored(pageId, dirs.writePackage(pageId, ok, name, System.currentTimeMillis()) { source -> byName[source]?.let { z.getInputStream(it) } })
            }
        } finally {
            tmp.delete()
        }
    }

    private fun importHtml(pageId: String, uri: Uri, name: String?): ImportResult {
        val bytes = runCatching { resolver.openInputStream(uri)?.use { PageDirs.readUpTo(it, MAX_HTML_BYTES + 1) } }.getOrNull()
            ?: return ImportResult.Failed(ImportFailure.Unreadable)
        if (bytes.isEmpty()) return ImportResult.Failed(ImportFailure.NotHtml)
        if (bytes.size > MAX_HTML_BYTES) return ImportResult.Failed(ImportFailure.TooLarge)
        val text = bytes.toString(Charsets.UTF_8)
        if ('\u0000' in text.take(4096) || !text.contains('<')) return ImportResult.Failed(ImportFailure.NotHtml)
        val info = HtmlScan.scan(text)
        val display = HtmlScan.displayName(info, name)
        val record = ImportRecord(
            name = display,
            isPackage = false,
            manifest = ManifestRules.forPage(display),
            isOrrery = info.isOrrery,
            mentionedHosts = info.mentionedHosts,
            bytes = bytes.size.toLong(),
            sourceName = name,
            importedAt = System.currentTimeMillis(),
            warnings = emptyList(),
        )
        return stored(pageId, dirs.writePage(pageId, bytes, record))
    }

    /** After an import: the caches follow the new files. */
    private fun stored(pageId: String, result: ImportResult): ImportResult {
        if (result is ImportResult.Ok) {
            records[pageId] = result.record
            missing -= pageId
            forgetPrepared(pageId)
        }
        return result
    }

    private fun forgetPrepared(pageId: String) {
        synchronized(prepared) { prepared.keys.removeAll { it.startsWith("$pageId|") } }
    }

    /** Deletes a page's files (its WebView storage is cleared by the runtime). */
    fun delete(pageId: String) {
        records.remove(pageId)
        missing -= pageId
        forgetPrepared(pageId)
        dirs.delete(pageId)
    }

    /**
     * Tidies the files at start ([PageDirs.sweep]): [keep] = the ids whose files stay, or null when
     * the page list couldn't be fully read, so nothing is removed.
     */
    fun sweep(keep: Set<String>?) {
        dirs.sweep(keep)
        if (keep != null) records.keys.retainAll(keep)
        missing.clear()
    }

    /** The served document: the stored HTML with the skeleton (fragments) and the runtime's head block. */
    fun document(pageId: String, isPackage: Boolean, networkOn: Boolean): ByteArray? {
        val key = "$pageId|$isPackage|$networkOn"
        synchronized(prepared) { prepared[key] }?.let { return it }
        val file = File(contentDir(pageId), if (isPackage) PackageLayout.HTML else ServePolicy.PAGE_FILE)
        val html = runCatching { file.readText() }.getOrNull() ?: return null
        return HtmlPrep.prepare(html, isPackage, networkOn).toByteArray(Charsets.UTF_8)
            .also { synchronized(prepared) { prepared[key] = it } }
    }

    /** A stored file under the page's content directory, or null (never anything outside it). */
    fun file(pageId: String, relative: String): File? {
        val dir = contentDir(pageId)
        val f = File(dir, relative)
        return f.takeIf { it.isFile && it.canonicalPath.startsWith(dir.canonicalPath + File.separator) }
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun folderName(tree: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/') }.getOrNull()

    /** path inside the tree → (document uri, size); depth and count bounded (checked for every row). */
    private fun listTree(tree: Uri): Map<String, Pair<Uri, Long>> {
        val out = LinkedHashMap<String, Pair<Uri, Long>>()
        val cap = PackageLayout.MAX_FILES * 2
        fun walk(docId: String, prefix: String, depth: Int) {
            if (depth > 8 || out.size > cap) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (out.size <= cap && c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val path = prefix + name
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id, "$path/", depth + 1)
                    } else {
                        out[path] = DocumentsContract.buildDocumentUriUsingTree(tree, id) to (if (c.isNull(3)) 0L else c.getLong(3))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return out
    }

    companion object {
        const val MAX_HTML_BYTES = 32 * 1024 * 1024
        const val MAX_ZIP_BYTES = 32L * 1024 * 1024

        /** Prepared documents kept: the current page and its two neighbours. */
        const val MAX_PREPARED = 3
    }
}
