package com.bos.sphere.feature.grid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.bos.sphere.core.data.AnimationInfo
import com.bos.sphere.core.data.AnimationSniffer
import com.bos.sphere.core.data.BounceSupport
import com.bos.sphere.core.data.PictureLimits
import com.bos.sphere.core.data.PictureSpec
import com.bos.sphere.core.data.WallpaperSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Why a chosen file was not taken; [text] is English (translated where shown). */
enum class ImportFailure(val text: String) {
    Unreadable("That file could not be read."),
    TooLarge("That file is too large (pictures up to 80 MB, animated pictures up to 30 MB, videos up to 200 MB)."),
    Corrupt("That picture or video could not be opened."),
}

sealed interface ImportResult {
    data class Ok(val spec: PictureSpec) : ImportResult
    data class Failed(val reason: ImportFailure) : ImportResult
}

/** The seam the picker calls with what the gallery returned. A test passes a fake; the app uses [AndroidPictureImporter]. */
fun interface PictureImporter {
    suspend fun import(uri: Uri, surface: WallpaperSurface, screenW: Int, screenH: Int): ImportResult
}

/** The app-private wallpaper folder: `files/wallpapers/<surface>-<id>.<ext>`. Never exported, never backed up (see StateSchema). */
object PictureFiles {
    const val DIR = "wallpapers"
    fun dir(ctx: Context): File = File(ctx.filesDir, DIR)
    fun resolve(ctx: Context, name: String): File? = PictureLimits.safeName(name).takeIf { it.isNotEmpty() }?.let { File(dir(ctx), it) }?.takeIf { it.isFile }
    /** Deletes the copy [name]; false when there was none. Off the main thread. */
    fun delete(ctx: Context, name: String): Boolean = PictureLimits.safeName(name).takeIf { it.isNotEmpty() }?.let { File(dir(ctx), it).delete() } ?: false
    /** Names of every file in the folder (for a clean-up of strays). */
    fun all(ctx: Context): List<String> = dir(ctx).list()?.toList().orEmpty()
}

/** Copies from [input] to [out], stopping with false once more than [limit] bytes were read. */
internal fun copyLimited(input: InputStream, out: OutputStream, limit: Long): Boolean {
    val buf = ByteArray(64 * 1024); var total = 0L
    while (true) {
        val n = input.read(buf); if (n < 0) return true
        total += n; if (total > limit) return false
        out.write(buf, 0, n)
    }
}

/**
 * Copies the chosen file into the private folder and reads what the playback needs (animated or not, video, length). Runs on the IO dispatcher.
 * Stills are decoded down to the screen x 1.5 and stored as JPEG (PNG when they have transparency); GIF and animated WebP keep their bytes (up to
 * [PictureLimits.MAX_ANIMATED_BYTES]); a video keeps its bytes (up to [PictureLimits.MAX_VIDEO_BYTES]). Never throws: a failure is a result.
 */
class AndroidPictureImporter internal constructor(private val context: Context, private val decoder: StillDecoder) : PictureImporter {
    constructor(context: Context) : this(context, PlatformStillDecoder)
    override suspend fun import(uri: Uri, surface: WallpaperSurface, screenW: Int, screenH: Int): ImportResult = withContext(Dispatchers.IO) {
        val dir = PictureFiles.dir(context).also { it.mkdirs() }
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Integer.toString((Math.random() * 1296).toInt(), 36)
        val raw = File(dir, "$surface-$id.tmp".lowercase())
        val head = ByteArray(32)
        var headLen = 0
        try {
            val src = context.contentResolver.openInputStream(uri) ?: return@withContext ImportResult.Failed(ImportFailure.Unreadable)
            src.use { i ->
                while (headLen < head.size) { val n = i.read(head, headLen, head.size - headLen); if (n < 0) break; headLen += n }
                val fmt = AnimationSniffer.format(head.copyOf(headLen))
                val limit = when (fmt) { AnimationSniffer.Format.Video -> PictureLimits.MAX_VIDEO_BYTES; AnimationSniffer.Format.Other -> STILL_LIMIT; else -> PictureLimits.MAX_ANIMATED_BYTES }
                val ok = raw.outputStream().use { o -> o.write(head, 0, headLen); copyLimited(i, o, limit - headLen) }
                if (!ok) { raw.delete(); return@withContext ImportResult.Failed(ImportFailure.TooLarge) }
                val name = "${surface.name.lowercase()}-$id"
                when (fmt) {
                    AnimationSniffer.Format.Video -> video(raw, File(dir, "$name.mp4"), "$name.mp4")
                    AnimationSniffer.Format.Gif -> gif(raw, File(dir, "$name.gif"), "$name.gif", screenW, screenH, dir, name)
                    AnimationSniffer.Format.WebP ->
                        if (AnimationSniffer.webpAnimated(head.copyOf(headLen))) webp(raw, File(dir, "$name.webp"), "$name.webp")
                        else still(raw, dir, name, screenW, screenH)
                    else -> still(raw, dir, name, screenW, screenH)
                }
            }
        } catch (_: IOException) { raw.delete(); ImportResult.Failed(ImportFailure.Unreadable) }
        catch (_: SecurityException) { raw.delete(); ImportResult.Failed(ImportFailure.Unreadable) }
        catch (_: OutOfMemoryError) { raw.delete(); ImportResult.Failed(ImportFailure.TooLarge) }
        catch (_: RuntimeException) { raw.delete(); ImportResult.Failed(ImportFailure.Corrupt) }
    }

    private fun video(raw: File, to: File, name: String): ImportResult {
        val ms = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(raw.path); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { runCatching { r.release() } }
        }.getOrNull()
        if (ms == null || ms <= 0L) { raw.delete(); return ImportResult.Failed(ImportFailure.Corrupt) }
        if (!raw.renameTo(to)) { raw.delete(); return ImportResult.Failed(ImportFailure.Unreadable) }
        return ImportResult.Ok(PictureSpec(file = name, animated = true, video = true, durationMs = ms, bounce = BounceSupport.Yes))
    }

    private fun gif(raw: File, to: File, name: String, sw: Int, sh: Int, dir: File, base: String): ImportResult {
        val info: AnimationInfo = FileInputStream(raw).use { AnimationSniffer.gif(it) } ?: run { raw.delete(); return ImportResult.Failed(ImportFailure.Corrupt) }
        if (!info.animated) return still(raw, dir, base, sw, sh)
        if (!raw.renameTo(to)) { raw.delete(); return ImportResult.Failed(ImportFailure.Unreadable) }
        val bounce = if (PictureLimits.cacheScale(info.frames, info.width, info.height, sw, sh) != null) BounceSupport.Yes else BounceSupport.TooLarge
        return ImportResult.Ok(PictureSpec(file = name, animated = true, bounce = bounce))
    }

    private fun webp(raw: File, to: File, name: String): ImportResult {
        // Decode a small version once so a broken file is refused here and not when drawn.
        val ok = runCatching { decoder.decode(raw, 64, 64).recycle() }.isSuccess
        if (!ok) { raw.delete(); return ImportResult.Failed(ImportFailure.Corrupt) }
        if (!raw.renameTo(to)) { raw.delete(); return ImportResult.Failed(ImportFailure.Unreadable) }
        return ImportResult.Ok(PictureSpec(file = name, animated = true, bounce = BounceSupport.NeedsGif))
    }

    private fun still(raw: File, dir: File, base: String, sw: Int, sh: Int): ImportResult {
        val bmp: Bitmap = try { decoder.decode(raw, sw, sh) }
        catch (_: HugeImage) { raw.delete(); return ImportResult.Failed(ImportFailure.TooLarge) }
        catch (_: IOException) { raw.delete(); return ImportResult.Failed(ImportFailure.Corrupt) }
        catch (_: RuntimeException) { raw.delete(); return ImportResult.Failed(ImportFailure.Corrupt) }
        raw.delete()
        val png = bmp.hasAlpha()
        val name = base + if (png) ".png" else ".jpg"
        val ok = File(dir, name).outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        if (!ok) { File(dir, name).delete(); return ImportResult.Failed(ImportFailure.Unreadable) }
        return ImportResult.Ok(PictureSpec(file = name))
    }

    private companion object { const val STILL_LIMIT = 80L * 1024 * 1024 }
}

/** The source has more pixels than [PictureLimits.MAX_SOURCE_PIXELS]. */
internal class HugeImage : IOException("huge")

/** Decodes the first frame of an image file to at most the screen x 1.5 (the seam of the still import; tests replace it, Robolectric has no ImageDecoder). */
internal fun interface StillDecoder { fun decode(file: File, screenW: Int, screenH: Int): Bitmap }

internal object PlatformStillDecoder : StillDecoder {
    override fun decode(file: File, screenW: Int, screenH: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, info, _ ->
        val w = info.size.width; val h = info.size.height
        if (w.toLong() * h > PictureLimits.MAX_SOURCE_PIXELS) throw HugeImage()
        val (tw, th) = PictureLimits.stillSize(w, h, screenW, screenH)
        d.setTargetSize(tw, th)
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}
