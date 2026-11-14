package com.bos.sphere.core.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns as Cols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Pure helpers for [FileSearch]. */
object FileSearchLogic {
    /** Escapes `%`, `_` and `\` for a LIKE pattern that declares `ESCAPE '\'`. */
    fun escapeLike(s: String): String = buildString {
        for (c in s) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }

    fun likePattern(query: String): String = "%" + escapeLike(query.trim()) + "%"

    fun kindFromMime(mime: String?, name: String? = null): HitKind {
        val m = mime?.lowercase().orEmpty()
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when {
            m.startsWith("image/") -> HitKind.Image
            m.startsWith("video/") -> HitKind.Video
            m.startsWith("audio/") -> HitKind.Audio
            m == "vnd.android.document/directory" -> HitKind.Folder
            m in archiveMimes || ext in archiveExts -> HitKind.Archive
            m.startsWith("text/") || m == "application/pdf" || m == "application/epub+zip" ||
                "word" in m || "excel" in m || "spreadsheet" in m ||
                "powerpoint" in m || "presentation" in m || "opendocument" in m || m == "application/rtf" ||
                ext in docExts -> HitKind.Document
            else -> HitKind.Other
        }
    }

    private val archiveMimes = setOf(
        "application/zip", "application/x-7z-compressed", "application/x-rar-compressed",
        "application/vnd.rar", "application/gzip", "application/x-tar", "application/x-gzip",
    )
    private val archiveExts = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")
    private val docExts = setOf("pdf", "doc", "docx", "txt", "md", "rtf", "odt", "ods", "odp", "xls", "xlsx", "csv", "ppt", "pptx", "epub")

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return ""
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v < 10) String.format(Locale.US, "%.1f %s", v, units[i]) else "${Math.round(v)} ${units[i]}"
    }

    /** Last non-empty segment of a MediaStore RELATIVE_PATH like `Download/Reports/`. */
    fun parentFolder(relativePath: String?): String? =
        relativePath?.split('/')?.lastOrNull { it.isNotBlank() }

    fun subtitle(relativePath: String?, size: Long): String? =
        listOfNotNull(parentFolder(relativePath), formatSize(size).ifEmpty { null }).joinToString(" · ").ifEmpty { null }
}

/** File-name search over MediaStore.Files. Quietly returns nothing without storage/media access. */
class FileSearch(private val context: Context) : SearchProvider {

    fun hasAccess(): Boolean {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(Manifest.permission.READ_MEDIA_AUDIO) ||
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) ||
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        if (limit <= 0 || !hasAccess()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                query(q, limit)
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: RuntimeException) {
                emptyList()
            }
        }
    }

    private fun query(q: String, limit: Int): List<SearchHit> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(Cols._ID, Cols.DISPLAY_NAME, Cols.MIME_TYPE, Cols.SIZE, Cols.RELATIVE_PATH)
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${Cols.DISPLAY_NAME} LIKE ? ESCAPE '\\' AND ${Cols.MIME_TYPE} IS NOT NULL")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(FileSearchLogic.likePattern(q)))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(Cols.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<SearchHit>()
        context.contentResolver.query(collection, projection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(Cols._ID)
            val iName = c.getColumnIndexOrThrow(Cols.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(Cols.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(Cols.SIZE)
            val iPath = c.getColumnIndexOrThrow(Cols.RELATIVE_PATH)
            while (c.moveToNext() && out.size < limit) {
                val id = c.getLong(iId)
                val name = c.getString(iName) ?: continue
                val mime = c.getString(iMime)
                val kind = FileSearchLogic.kindFromMime(mime, name)
                val base = when (kind) {
                    HitKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    HitKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    HitKind.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> collection
                }
                val uri = ContentUris.withAppendedId(base, id).toString()
                out += SearchHit(
                    id = "file:$id",
                    category = SearchCategory.Files,
                    title = name,
                    subtitle = FileSearchLogic.subtitle(c.getString(iPath), c.getLong(iSize)),
                    kind = kind,
                    uri = uri,
                    mime = mime,
                    thumbUri = if (kind == HitKind.Image || kind == HitKind.Video) uri else null,
                )
            }
        }
        return out
    }
}
