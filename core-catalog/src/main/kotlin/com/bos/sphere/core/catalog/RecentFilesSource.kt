package com.bos.sphere.core.catalog

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * HOM-15: lists files from folder trees the user granted through the system picker (persisted
 * URI permissions). No broad storage permission is used; a tree whose grant is gone is skipped.
 */
class RecentFilesSource(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun grantedTrees(): List<Uri> = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
        .filter { DocumentsContract.isTreeUri(it) }

    /** Direct children (files only) of one granted tree, newest first. */
    fun recent(tree: Uri, limit: Int = 20): List<RecentFile> = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<RecentFile>()
        resolver.query(children, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                out += RecentFile(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString(), c.getString(1).orEmpty(), mime, c.getLong(3), tree.toString())
            }
        }
        out.sortedByDescending { it.modifiedMs }.take(limit)
    }.getOrDefault(emptyList())

    fun all(limit: Int = 20): List<RecentFile> = RecentFilesModel.merge(grantedTrees().map { recent(it, limit) }, limit)
}
