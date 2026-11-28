package com.bos.sphere.core.data

/**
 * Wave 34 (wallpaper): the settings of a picture wallpaper and the pure rules around it. Pure JVM; the Android side (the Photo Picker,
 * the copy into private storage, decoding, drawing) lives in feature-grid and reads these.
 */

/** How a picture is laid on the screen: Fill centre-crops it over the whole screen (default), Fit shows all of it inside the screen. */
enum class PictureFit(val id: String, val label: String) {
    Fill("fill", "Fill"),
    Fit("fit", "Fit");

    companion object { fun forId(id: String?): PictureFit = entries.firstOrNull { it.id == id } ?: Fill }
}

/**
 * What an animated image (GIF, animated WebP) does as a wallpaper. [Still] shows the first frame; [Once] plays to the last frame
 * and stops there; [LoopContinuous] plays forward again and again; [LoopBounce] plays forward, then rewinds, again and again.
 */
enum class GifMode(val id: String, val label: String) {
    Still("still", "Still (first frame)"),
    Once("once", "Play once"),
    LoopContinuous("continuous", "Continuous"),
    LoopBounce("bounce", "Bounce");

    /** True when the mode keeps the screen redrawing (an infinite animation: it follows the motion policy). */
    val loops: Boolean get() = this == LoopContinuous || this == LoopBounce

    companion object { fun forId(id: String?): GifMode = entries.firstOrNull { it.id == id } ?: Still }
}

/** Whether an animated file can be played forward and back, decided when it was chosen; the other values are why not. */
enum class BounceSupport(val id: String, val note: GifNote?) {
    Yes("yes", null), TooLarge("large", GifNote.TooLargeToBounce), NeedsGif("gif", GifNote.BounceNeedsGif);
    companion object { fun forId(id: String?): BounceSupport = entries.firstOrNull { it.id == id } ?: Yes }
}

/** The picture of one surface. [file] is a name inside the app's private wallpaper folder ("" = none chosen yet). */
data class PictureSpec(
    val file: String = "",
    val fit: PictureFit = PictureFit.Fill,
    /** Black laid over the picture, in percent: one of [DIMS]. */
    val dim: Int = 0,
    val gif: GifMode = GifMode.Still,
    /** The file holds an animation or a video (decided when it was chosen). */
    val animated: Boolean = false,
    /** The file is a video (always muted); [animated] is then true as well. */
    val video: Boolean = false,
    /** Length of a video in milliseconds (0 for images). */
    val durationMs: Long = 0L,
    /** The user confirmed the "uses more power" warning for this file. Chosen anew with every new file. */
    val powerOk: Boolean = false,
    val bounce: BounceSupport = BounceSupport.Yes,
) {
    /** True when this picture, as set, plays: an animated file in any mode but Still. */
    val plays: Boolean get() = hasFile && animated && gif != GifMode.Still
    val hasFile: Boolean get() = file.isNotEmpty()
    /** A video has no Bounce (owner, wave 34): a stored Bounce on a video plays as Continuous. */
    fun normalised() = copy(dim = PictureLimits.nearestDim(dim), file = PictureLimits.safeName(file), gif = if (video && gif == GifMode.LoopBounce) GifMode.LoopContinuous else gif)
}

object PictureLimits {
    /** The dim choices (percent) the pickers offer, for a picture and for the system wallpaper alike. */
    val DIMS: List<Int> = listOf(0, 25, 50)
    fun nearestDim(percent: Int): Int = DIMS.minByOrNull { kotlin.math.abs(it - percent) } ?: 0

    /** A video larger than this is refused with a message. */
    const val MAX_VIDEO_BYTES: Long = 200L * 1024 * 1024
    /** A GIF or animated WebP larger than this is refused with a message. */
    const val MAX_ANIMATED_BYTES: Long = 30L * 1024 * 1024
    /** Stills are decoded down to the screen times this. */
    const val STILL_SCREEN_FACTOR = 1.5f
    /** Decoded frames kept in memory for bounce (and play once / continuous when they fit). */
    const val FRAME_CACHE_BYTES: Long = 48L * 1024 * 1024
    /** Longest side the app will even try to decode from the source (a bigger file is read with a sample size). */
    const val MAX_SOURCE_PIXELS: Long = 120_000_000L

    /** A file name inside the wallpaper folder: letters, digits, '-', '_' and one extension; anything else (a path, "..") is dropped. */
    fun safeName(raw: String): String = if (raw.isNotEmpty() && raw.length <= 80 && raw.matches(Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9]{1,5})?"))) raw else ""

    /** The size a still is decoded to: the source scaled down, keeping its shape, to fit inside the screen x [STILL_SCREEN_FACTOR]; never scaled up. */
    fun stillSize(srcW: Int, srcH: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0) return 0 to 0
        val maxW = (screenW * STILL_SCREEN_FACTOR).toInt().coerceAtLeast(1); val maxH = (screenH * STILL_SCREEN_FACTOR).toInt().coerceAtLeast(1)
        val s = minOf(1f, maxW / srcW.toFloat(), maxH / srcH.toFloat())
        return (srcW * s).toInt().coerceAtLeast(1) to (srcH * s).toInt().coerceAtLeast(1)
    }

    /**
     * The scale (1, 3/4 or 1/2 of the screen-bounded size) at which all [frames] of an [srcW] x [srcH] animation fit in [FRAME_CACHE_BYTES]
     * (4 bytes a pixel), or null when even half size does not fit (too large to cache: bounce is refused).
     */
    fun cacheScale(frames: Int, srcW: Int, srcH: Int, screenW: Int, screenH: Int): Float? {
        if (frames <= 0 || srcW <= 0 || srcH <= 0) return null
        val base = minOf(1f, screenW / srcW.toFloat(), screenH / srcH.toFloat())
        for (k in floatArrayOf(1f, 0.75f, 0.5f)) {
            val w = (srcW * base * k).toLong().coerceAtLeast(1); val h = (srcH * base * k).toLong().coerceAtLeast(1)
            if (w * h * 4L * frames <= FRAME_CACHE_BYTES) return base * k
        }
        return null
    }
}

/** The facts about an animated file the playback needs, read once when the picture is chosen. */
data class AnimationInfo(val frames: Int, val delaysMs: IntArray, val width: Int, val height: Int) {
    val animated: Boolean get() = frames > 1
    override fun equals(other: Any?) = other is AnimationInfo && frames == other.frames && width == other.width && height == other.height && delaysMs.contentEquals(other.delaysMs)
    override fun hashCode() = 31 * (31 * frames + width + height) + delaysMs.contentHashCode()
}

/** Why the animation is not played as chosen (shown in the picker). */
enum class GifNote(val text: String) {
    /** Reduced motion, battery saver or the infinite-animation policy: the first frame is shown. */
    MotionOff("Animation is off (reduced motion or battery saver): showing the first frame"),
    TooLargeToBounce("Too large to bounce, looping instead"),
    /** Animated WebP cannot be rewound frame by frame: it loops instead. */
    BounceNeedsGif("Bounce needs a GIF, looping instead"),
}

/** The warning that goes with every animated wallpaper (GIF, animated WebP, video): asked once when it is switched on, then shown standing. */
object PowerWarning {
    /** Whether applying [new] must first ask the user: it plays, and the user has not confirmed this file yet. */
    fun mustAsk(new: PictureSpec): Boolean = new.plays && !new.powerOk
    /** The wallpaper plays, so the standing warning shows. */
    fun standing(w: Wallpaper): Boolean = w.kind == WallpaperKind.Picture && w.picture.plays
}

/** What is actually played: [mode] (never more than the user asked for) and the [note] to show when it differs. */
data class GifPlan(val mode: GifMode, val note: GifNote?)

object GifPolicy {
    /**
     * [requested] is the chosen mode; [animated] false means the file is a still image. [motionAllowed] is false under reduced motion, battery
     * saver or when the app's infinite-animation policy says no animation. [bounce] says whether the file can be rewound.
     */
    fun plan(requested: GifMode, animated: Boolean, motionAllowed: Boolean, bounce: BounceSupport = BounceSupport.Yes): GifPlan {
        if (!animated || requested == GifMode.Still) return GifPlan(GifMode.Still, null)
        if (!motionAllowed) return GifPlan(GifMode.Still, GifNote.MotionOff)
        if (requested == GifMode.LoopBounce && bounce != BounceSupport.Yes) return GifPlan(GifMode.LoopContinuous, bounce.note)
        return GifPlan(requested, null)
    }
}

/**
 * The playback clock: which frame shows at a given elapsed time, for one mode. Delays are the file's own per-frame delays (a delay of 10 ms
 * or less is 100 ms, as browsers do). Bounce plays 0..n-1 then n-2..1 and wraps, so the two end frames are each shown once per cycle.
 */
class GifTimeline(delaysMs: IntArray, val mode: GifMode) {
    class Position(val frame: Int, val finished: Boolean, /** Milliseconds until the frame changes; [Long.MAX_VALUE] when it never will. */ val untilNextMs: Long)

    val frames: Int = delaysMs.size
    private val order: IntArray = when {
        frames <= 1 || mode == GifMode.Still -> IntArray(minOf(frames, 1))
        mode == GifMode.LoopBounce && frames > 2 -> IntArray(2 * frames - 2) { if (it < frames) it else 2 * frames - 2 - it }
        else -> IntArray(frames) { it }
    }
    private val ends = LongArray(order.size).also { e -> var acc = 0L; for (k in order.indices) { acc += normal(delaysMs[order[k]]); e[k] = acc } }
    /** The length of one full pass (a cycle for the looping modes). */
    val cycleMs: Long = if (ends.isEmpty()) 0L else ends.last()
    private val staysStill: Boolean = order.size <= 1 || mode == GifMode.Still

    fun at(elapsedMs: Long): Position {
        if (frames == 0) return Position(0, true, Long.MAX_VALUE)
        if (staysStill) return Position(0, true, Long.MAX_VALUE)
        val t = elapsedMs.coerceAtLeast(0L)
        if (mode == GifMode.Once && t >= cycleMs) return Position(frames - 1, true, Long.MAX_VALUE)
        val local = if (mode == GifMode.Once) t else t % cycleMs
        var lo = 0; var hi = ends.size - 1
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (ends[mid] > local) hi = mid else lo = mid + 1 }
        return Position(order[lo], false, ends[lo] - local)
    }

    companion object {
        fun normal(delayMs: Int): Int = if (delayMs <= 10) 100 else delayMs.coerceAtMost(60_000)
    }
}

/** Reads the frame delays of a GIF (and whether a WebP is animated) from the file's own bytes, without decoding any pixels. Pure. */
object AnimationSniffer {
    enum class Format { Gif, WebP, Video, Other }

    fun format(head: ByteArray): Format = when {
        head.size >= 6 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() -> Format.Gif
        head.size >= 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> Format.WebP
        head.size >= 12 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> Format.Video
        head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> Format.Video
        else -> Format.Other
    }

    /** The frame count, per-frame delays and size of a GIF; null when the bytes are not a readable GIF. Stops at the trailer. */
    fun gif(input: java.io.InputStream): AnimationInfo? {
        val delays = ArrayList<Int>()
        var w = 0; var h = 0
        // A file cut short (or with a bad block after some frames) keeps the frames read so far; nothing readable is null.
        fun finish(): AnimationInfo? = if (delays.isEmpty()) null else AnimationInfo(delays.size, delays.toIntArray(), w, h)
        try {
            val head = ByteArray(13)
            if (!readFully(input, head) || format(head) != Format.Gif) return null
            w = u16(head, 6); h = u16(head, 8)
            val flags = head[10].toInt() and 0xFF
            if (flags and 0x80 != 0) skip(input, 3L * (1 shl ((flags and 7) + 1)))
            var pending = 10
            while (true) {
                when (input.read()) {
                    -1, 0x3B -> return finish()
                    0x21 -> {
                        val label = input.read()
                        if (label == 0xF9) { // graphic control: size 4, flags, delay (1/100 s), transparent index, terminator
                            val blk = ByteArray(6); if (!readFully(input, blk)) return finish()
                            pending = u16(blk, 2) * 10
                        } else skipSubBlocks(input)
                    }
                    0x2C -> {
                        val d = ByteArray(9); if (!readFully(input, d)) return finish()
                        val lf = d[8].toInt() and 0xFF
                        if (lf and 0x80 != 0) skip(input, 3L * (1 shl ((lf and 7) + 1)))
                        input.read() // LZW minimum code size
                        skipSubBlocks(input)
                        delays.add(pending); pending = 10
                    }
                    else -> return finish()
                }
            }
        } catch (_: java.io.IOException) { return finish() }
    }

    /** True when a WebP file is an animation (the VP8X header's animation flag). */
    fun webpAnimated(head: ByteArray): Boolean =
        head.size >= 21 && String(head, 12, 4, Charsets.ISO_8859_1) == "VP8X" && (head[20].toInt() and 0x02) != 0

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun readFully(i: java.io.InputStream, b: ByteArray): Boolean { var n = 0; while (n < b.size) { val r = i.read(b, n, b.size - n); if (r < 0) return false; n += r }; return true }
    private fun skip(i: java.io.InputStream, n: Long) { var left = n; while (left > 0) { val s = i.skip(left); if (s <= 0) { if (i.read() < 0) throw java.io.IOException("eof"); left-- } else left -= s } }
    private fun skipSubBlocks(i: java.io.InputStream) { while (true) { val n = i.read(); if (n <= 0) return; skip(i, n.toLong()) } }
}

/** The stored keys of a surface's picture and system dim, all in launcher_prefs; the file and what describes the file are device-bound (see [StateSchema.DEVICE_BOUND_PREF_KEYS]). */
internal object WallpaperPictureKeys {
    fun write(e: android.content.SharedPreferences.Editor, p: String, pic: PictureSpec, sysDim: Int) {
        e.putString(p + "Pic", pic.file).putString(p + "PicFit", pic.fit.id).putInt(p + "PicDim", pic.dim).putString(p + "PicGif", pic.gif.id)
            .putBoolean(p + "PicAnim", pic.animated).putBoolean(p + "PicVid", pic.video).putLong(p + "PicMs", pic.durationMs).putBoolean(p + "PicOk", pic.powerOk).putString(p + "PicBounce", pic.bounce.id)
            .putInt(p + "SysDim", sysDim)
    }
    fun readPicture(sp: android.content.SharedPreferences, p: String): PictureSpec = PictureSpec(
        file = sp.getString(p + "Pic", "") ?: "", fit = PictureFit.forId(sp.getString(p + "PicFit", null)), dim = sp.getInt(p + "PicDim", 0),
        gif = GifMode.forId(sp.getString(p + "PicGif", null)), animated = sp.getBoolean(p + "PicAnim", false), video = sp.getBoolean(p + "PicVid", false),
        durationMs = sp.getLong(p + "PicMs", 0L).coerceAtLeast(0L), powerOk = sp.getBoolean(p + "PicOk", false), bounce = BounceSupport.forId(sp.getString(p + "PicBounce", null)),
    ).normalised()
    fun readDim(sp: android.content.SharedPreferences, p: String): Int = PictureLimits.nearestDim(sp.getInt(p + "SysDim", 0))
}
