package com.bos.sphere.core.catalog

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

/** HOM-15: one cell of the Files tile: the file's name, and its thumbnail when the platform gave one. */
data class FileThumb<T>(val name: String, val image: T?)

/** A small least-recently-used map; reads and writes are safe from any thread. */
class BoundedCache<K, V>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }
    @Synchronized operator fun get(k: K): V? = map[k]
    @Synchronized fun containsKey(k: K) = map.containsKey(k)
    @Synchronized operator fun set(k: K, v: V) { map[k] = v }
    val size: Int get() = synchronized(this) { map.size }
    @Synchronized fun clear() = map.clear()
}

/**
 * HOM-15: thumbnails of the first few recent files for the Files tile. Only the first [MAX_TILE] files are looked at, each through the
 * platform's own thumbnail call on a folder the user granted (no storage permission is involved), at most [CACHE_MAX] results are kept
 * (a failure is remembered too, so a file without a thumbnail is not asked again on every refresh), and a file with no thumbnail is
 * just its name. [load] blocks: call it off the main thread.
 */
class FileThumbs<T : Any>(private val loader: (RecentFile) -> T?, private val cache: BoundedCache<String, Result<T>> = BoundedCache(CACHE_MAX)) {
    /** [value] is null when the platform gave no thumbnail. */
    class Result<T>(val value: T?)

    fun load(files: List<RecentFile>, max: Int = MAX_TILE): List<FileThumb<T>> = files.take(max.coerceIn(0, MAX_TILE)).map { f ->
        val key = "${f.uri}@${f.modifiedMs}"
        val hit = cache[key]
        val r = hit ?: Result(runCatching { loader(f) }.getOrNull()).also { cache[key] = it }
        FileThumb(f.name, r.value)
    }

    companion object {
        const val MAX_TILE = 4
        const val CACHE_MAX = 12
        /** Thumbnail edge in pixels: a quarter of the tile at xxhdpi, small enough to keep twelve of them cheap. */
        const val EDGE_PX = 192

        @Volatile private var shared: FileThumbs<Bitmap>? = null

        /** The process-wide instance using [ContentResolver.loadThumbnail] on the document URI (it carries the folder's grant). */
        fun shared(context: Context): FileThumbs<Bitmap> = shared ?: synchronized(this) {
            shared ?: FileThumbs(platformLoader(context.applicationContext)).also { shared = it }
        }

        /** The platform call; null for a file whose provider gives no thumbnail, or any failure. */
        fun platformLoader(context: Context): (RecentFile) -> Bitmap? = { f ->
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(f.uri), Size(EDGE_PX, EDGE_PX), null) }.getOrNull()
        }
    }
}
