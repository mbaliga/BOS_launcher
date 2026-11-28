package com.bos.sphere

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import androidx.compose.runtime.staticCompositionLocalOf
import com.bos.sphere.core.catalog.WeightedLruCache
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.RecentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** W31 (filesViews): what a thumbnail load gave back. [bitmap] null means "no preview, draw the type tile"; [durationMs] is a video's length when known. */
class ThumbData(val bitmap: Bitmap?, val durationMs: Long = -1) {
    /** An APK icon or a PDF page need different framing from a photo. */
    val weight: Long get() = (bitmap?.allocationByteCount?.toLong() ?: 0L) + 64
}

/** The seam the Files tab loads previews through; tests install a fake with [LocalFileThumbs]. Called off the main thread. */
interface FileThumbs {
    suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int): ThumbData
}

val LocalFileThumbs = staticCompositionLocalOf<FileThumbs> { error("no FileThumbs provided") }

/** The process-wide, bounded (24 MB) thumbnail memory cache. A failed load is cached too (weight 64) so a broken file is not retried on every scroll. */
object FileThumbCache {
    private const val MAX_BYTES = 24L * 1024 * 1024
    val cache = WeightedLruCache<String, ThumbData>(MAX_BYTES) { it.weight }
    fun key(f: RecentFile, w: Int, h: Int) = "${f.uri}|${f.modifiedMs}|${w}x$h"
}

/**
 * The real loader. Everything goes through the folder grant the file came from (a document URI under a persisted tree): the provider's own
 * thumbnail where it has one, else the platform decoders on the same URI. No new permission and no path access. Every call is wrapped so a
 * corrupt file, a revoked grant or a provider crash gives "no preview" instead of an exception.
 */
class RealFileThumbs(context: Context) : FileThumbs {
    private val app = context.applicationContext
    private val gate = Semaphore(3) // at most three decodes at once, so a fast fling does not pile up work

    override suspend fun load(file: RecentFile, kind: FileKind, widthPx: Int, heightPx: Int): ThumbData {
        if (!kind.previewable) return ThumbData(null)
        val w = widthPx.coerceIn(16, 1024)
        val h = heightPx.coerceIn(16, 1024)
        return gate.withPermit {
            val signal = CancellationSignal()
            coroutineScope {
                // The decode runs on the IO pool; when this coroutine is cancelled (the cell scrolled away) the platform call is told to stop too.
                val work = async(Dispatchers.IO) { try { loadNow(file, kind, w, h, signal) } catch (e: CancellationException) { throw e } catch (e: Throwable) { ThumbData(null) } }
                try { work.await() } catch (e: CancellationException) { signal.cancel(); work.cancel(); throw e }
            }
        }
    }

    private fun loadNow(file: RecentFile, kind: FileKind, w: Int, h: Int, signal: CancellationSignal): ThumbData {
        val uri = Uri.parse(file.uri)
        return when (kind) {
            FileKind.Pdf -> ThumbData(pdfFirstPage(uri, w, h) ?: providerThumb(uri, w, h, signal))
            FileKind.Apk -> ThumbData(apkIcon(uri, w))
            FileKind.Video -> ThumbData(providerThumb(uri, w, h, signal) ?: videoFrame(uri, w, h), duration(uri))
            FileKind.Audio -> ThumbData(providerThumb(uri, w, h, signal) ?: audioArt(uri, w, h))
            else -> ThumbData(providerThumb(uri, w, h, signal) ?: decodeImage(uri, w, h))
        }
    }

    private fun providerThumb(uri: Uri, w: Int, h: Int, signal: CancellationSignal): Bitmap? =
        runCatching { app.contentResolver.loadThumbnail(uri, android.util.Size(w, h), signal) }.getOrNull()

    private fun decodeImage(uri: Uri, w: Int, h: Int): Bitmap? = runCatching {
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(app.contentResolver, uri)) { dec, info, _ ->
            val s = minOf(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
            dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    private inline fun <T> retriever(uri: Uri, block: (MediaMetadataRetriever) -> T?): T? {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(app, uri); block(r) } catch (e: Exception) { null } finally { runCatching { r.release() } }
    }

    private fun videoFrame(uri: Uri, w: Int, h: Int): Bitmap? = retriever(uri) { it.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h) }
    private fun duration(uri: Uri): Long = retriever(uri) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } ?: -1L
    private fun audioArt(uri: Uri, w: Int, h: Int): Bitmap? = retriever(uri) { r ->
        r.embeddedPicture?.let { b ->
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= w && opts.outHeight / (sample * 2) >= h) sample *= 2
            android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    /** The first page on a white sheet, at most w by h (a page is scaled to fit the width). */
    private fun pdfFirstPage(uri: Uri, w: Int, h: Int): Bitmap? = runCatching {
        app.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return@use null
                r.openPage(0).use { p ->
                    val scale = (w.toFloat() / p.width).coerceAtMost(h.toFloat() * 1.4f / p.height)
                    val bw = (p.width * scale).toInt().coerceAtLeast(1)
                    val bh = (p.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(AColor.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    /** The app's own icon, read from the file through its descriptor path (the package manager wants a path, and gets the open descriptor's). */
    private fun apkIcon(uri: Uri, size: Int): Bitmap? = runCatching {
        app.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            val path = "/proc/self/fd/${fd.fd}"
            val info = app.packageManager.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return@use null
            info.sourceDir = path
            info.publicSourceDir = path
            val d = info.loadIcon(app.packageManager)
            val px = size.coerceIn(16, 256)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, px, px)
            d.draw(c)
            bmp
        }
    }.getOrNull()
}
