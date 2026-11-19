package com.bos.sphere.feature.grid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bos.sphere.core.data.AnimationInfo
import com.bos.sphere.core.data.AnimationSniffer
import com.bos.sphere.core.data.GifMode
import com.bos.sphere.core.data.GifPlan
import com.bos.sphere.core.data.GifPolicy
import com.bos.sphere.core.data.GifTimeline
import com.bos.sphere.core.data.PictureFit
import com.bos.sphere.core.data.PictureLimits
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/** Where a picture lands on a screen: the pure geometry of Fill (centre-crop) and Fit. */
internal object PictureLayout {
    class Placement(val scale: Float, val dx: Float, val dy: Float)

    fun place(srcW: Int, srcH: Int, dstW: Float, dstH: Float, fit: PictureFit): Placement {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0f || dstH <= 0f) return Placement(1f, 0f, 0f)
        val sx = dstW / srcW; val sy = dstH / srcH
        val s = if (fit == PictureFit.Fill) maxOf(sx, sy) else minOf(sx, sy)
        return Placement(s, (dstW - srcW * s) / 2f, (dstH - srcH * s) / 2f)
    }
}

/** All frames of an animation, decoded once at a bounded size. */
internal class FrameSet(val delaysMs: IntArray, val frames: List<ImageBitmap>, val width: Int, val height: Int)
/** A streaming animation (platform [AnimatedImageDrawable]): cannot be rewound or sought. */
internal class StreamAnim(val drawable: Drawable, val width: Int, val height: Int)

/** The decoding seam of the picture wallpaper: the app uses [AndroidPictureLoader], tests pass fakes. All calls are off the main thread. */
internal interface PictureLoader {
    suspend fun still(file: File, video: Boolean, screenW: Int, screenH: Int): ImageBitmap?
    suspend fun info(file: File): AnimationInfo?
    suspend fun frames(file: File, info: AnimationInfo, screenW: Int, screenH: Int): FrameSet?
    suspend fun stream(file: File): StreamAnim?
}

internal val LocalPictureLoader = staticCompositionLocalOf<PictureLoader> { AndroidPictureLoader }
/** Tests set this to force battery saver on (true) or off (false); null reads the phone. */
internal val LocalBatterySaverOverride = staticCompositionLocalOf<Boolean?> { null }

@Suppress("DEPRECATION")
internal object AndroidPictureLoader : PictureLoader {
    override suspend fun still(file: File, video: Boolean, screenW: Int, screenH: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (video) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.path)
                    val b = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                    val (tw, th) = PictureLimits.stillSize(b.width, b.height, screenW, screenH)
                    (if (tw < b.width) Bitmap.createScaledBitmap(b, tw, th, true) else b).asImageBitmap()
                } finally { runCatching { r.release() } }
            } else ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, i, _ ->
                val (tw, th) = PictureLimits.stillSize(i.size.width, i.size.height, screenW, screenH)
                d.setTargetSize(tw, th); d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
    }

    override suspend fun info(file: File): AnimationInfo? = withContext(Dispatchers.IO) { runCatching { FileInputStream(file).buffered().use { AnimationSniffer.gif(it) } }.getOrNull() }

    /** GIF frames through [Movie], which can be set to any time (no rewinding problem); drawn on a software canvas, one bitmap per frame. */
    override suspend fun frames(file: File, info: AnimationInfo, screenW: Int, screenH: Int): FrameSet? = withContext(Dispatchers.IO) {
        runCatching {
            val scale = PictureLimits.cacheScale(info.frames, info.width, info.height, screenW, screenH) ?: return@runCatching null
            val movie = FileInputStream(file).buffered().use { Movie.decodeStream(it) } ?: return@runCatching null
            val w = (movie.width() * scale).toInt().coerceAtLeast(1); val h = (movie.height() * scale).toInt().coerceAtLeast(1)
            val rawSum = info.delaysMs.sum().coerceAtLeast(1)
            val k = if (movie.duration() > 0) movie.duration().toFloat() / rawSum else 1f
            var start = 0L
            val out = ArrayList<ImageBitmap>(info.frames)
            for (i in 0 until info.frames) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp); c.scale(scale, scale)
                movie.setTime(((start + 1) * k).toInt().coerceAtMost((movie.duration() - 1).coerceAtLeast(0)))
                movie.draw(c, 0f, 0f)
                out.add(bmp.asImageBitmap())
                start += info.delaysMs[i]
            }
            FrameSet(info.delaysMs, out, w, h)
        }.getOrNull()
    }

    override suspend fun stream(file: File): StreamAnim? = withContext(Dispatchers.IO) {
        runCatching {
            var w = 0; var h = 0
            val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { _, i, _ -> w = i.size.width; h = i.size.height }
            if (d is AnimatedImageDrawable) StreamAnim(d, w, h) else null
        }.getOrNull()
    }
}

/** Battery saver on (the phone's power-save mode); [LocalBatterySaverOverride] wins when set. */
@Composable
internal fun rememberBatterySaver(): Boolean {
    LocalBatterySaverOverride.current?.let { return it }
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(runCatching { ctx.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { on = runCatching { ctx.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false) }
        }
        runCatching { ctx.registerReceiver(r, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) }
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return on
}

/** The plan a picture plays with right now: the chosen mode, minus what motion settings or the file's limits forbid. */
@Composable
internal fun rememberPicturePlan(w: Wallpaper, play: Boolean = true): GifPlan {
    val reduced = rememberMotionPrefs().reduced
    val saver = rememberBatterySaver()
    return GifPolicy.plan(w.picture.gif, w.picture.animated, motionAllowed = play && !reduced && !saver, bounce = w.picture.bounce)
}

private sealed interface Located { data object Loading : Located; data object Missing : Located; class Found(val file: File) : Located }

/** Draws a bitmap with Fill or Fit. */
private fun DrawScope.drawPicture(img: ImageBitmap, fit: PictureFit) {
    val p = PictureLayout.place(img.width, img.height, size.width, size.height, fit)
    withTransform({ translate(p.dx, p.dy); scale(p.scale, p.scale, Offset.Zero) }) { drawImage(img, IntOffset.Zero) }
}

/**
 * The picture wallpaper of a surface: its still, or its animation. A missing file draws the solid colour of the wallpaper (never a crash, never a blank).
 * [play] false (Radar, previews) shows the still first frame whatever the mode. Animation also stops while the app is not resumed and focused, and
 * under reduced motion or battery saver. Decoding is off the main thread; the main thread only draws. Dim is a black layer over everything.
 */
@Composable
internal fun PictureWallpaper(w: Wallpaper, play: Boolean = true, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val spec = w.picture
    val located by produceState<Located>(Located.Loading, spec.file) {
        value = withContext(Dispatchers.IO) { PictureFiles.resolve(ctx, spec.file) }?.let { Located.Found(it) } ?: Located.Missing
    }
    val plan = rememberPicturePlan(w, play)
    val active = rememberRadarActive()
    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        when (val l = located) {
            is Located.Found -> {
                val animate = plan.mode != GifMode.Still
                when {
                    !animate -> StillPicture(l.file, w)
                    spec.video -> VideoWallpaper(l.file, w, plan.mode, active)
                    l.file.name.endsWith(".gif") -> GifPicture(l.file, w, plan.mode, active)
                    else -> StreamPicture(l.file, w, plan.mode, active)
                }
            }
            Located.Loading -> Unit
            Located.Missing -> FlatWallpaper(w.copy(kind = com.bos.sphere.core.data.WallpaperKind.Solid))
        }
        if (spec.dim > 0 && located is Located.Found) Box(Modifier.fillMaxSize().drawBehindDim(spec.dim))
    }
}

private fun Modifier.drawBehindDim(percent: Int): Modifier = this.then(Modifier.drawBehindColor(Color.Black.copy(alpha = percent / 100f)))
private fun Modifier.drawBehindColor(c: Color): Modifier = drawBehind { drawRect(c) }

@Composable
private fun screenPx(): IntSize { val m = LocalContext.current.resources.displayMetrics; return IntSize(m.widthPixels, m.heightPixels) }

@Composable
internal fun StillPicture(file: File, w: Wallpaper) {
    val loader = LocalPictureLoader.current
    val screen = screenPx()
    val img by produceState<ImageBitmap?>(null, file, w.picture.video) { value = loader.still(file, w.picture.video, screen.width, screen.height) }
    val fit = w.picture.fit
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black)
        img?.let { drawPicture(it, fit) }
    }
}

/** A GIF held as decoded frames when they fit the cache (any mode), else streamed (Once and Continuous only: Bounce already fell back). */
@Composable
private fun GifPicture(file: File, w: Wallpaper, mode: GifMode, active: Boolean) {
    val loader = LocalPictureLoader.current
    val screen = screenPx()
    val set by produceState<FrameSet?>(null, file) {
        val info = loader.info(file)
        value = info?.takeIf { it.animated }?.let { loader.frames(file, it, screen.width, screen.height) }
    }
    val s = set
    if (s == null) { StreamOrStill(file, w, mode, active, waiting = true); return }
    FramePlayer(s, mode, active, w.picture.fit)
}

@Composable
private fun StreamOrStill(file: File, w: Wallpaper, mode: GifMode, active: Boolean, waiting: Boolean) {
    // While the frames decode (or when they do not fit) the first frame is on screen; a file too large for the cache streams instead.
    val loader = LocalPictureLoader.current
    val streamed by produceState<StreamAnim?>(null, file) { value = loader.stream(file) }
    if (streamed == null) StillPicture(file, w) else StreamPlayer(streamed!!, mode, active, w.picture.fit)
}

@Composable
private fun StreamPicture(file: File, w: Wallpaper, mode: GifMode, active: Boolean) = StreamOrStill(file, w, mode, active, waiting = false)

/** Plays [set] by the real delays of the file: redraws only when the frame changes, sleeping between frames, paused while not [active]. */
@Composable
internal fun FramePlayer(set: FrameSet, mode: GifMode, active: Boolean, fit: PictureFit) {
    val timeline = remember(set, mode) { GifTimeline(set.delaysMs, mode) }
    var shown by remember(timeline) { mutableIntStateOf(0) }
    var elapsed by remember(timeline) { mutableLongStateOf(0L) }
    LaunchedEffect(timeline, active) {
        if (!active) return@LaunchedEffect
        var last = infiniteFrameNanos()
        while (true) {
            val pos = timeline.at(elapsed)
            if (shown != pos.frame) shown = pos.frame
            if (pos.finished) break
            delay(pos.untilNextMs.coerceIn(1L, 1000L))
            val now = infiniteFrameNanos()
            elapsed += ((now - last) / 1_000_000L).coerceIn(0L, 1000L)
            last = now
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black)
        set.frames.getOrNull(shown)?.let { drawPicture(it, fit) }
    }
}

/** Plays a platform animation (no seeking): started only while [active], stopped when not. */
@Composable
private fun StreamPlayer(a: StreamAnim, mode: GifMode, active: Boolean, fit: PictureFit) {
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(a, mode, active) {
        val d = a.drawable as AnimatedImageDrawable
        val h = Handler(Looper.getMainLooper())
        d.repeatCount = if (mode == GifMode.Once) 0 else AnimatedImageDrawable.REPEAT_INFINITE
        d.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { tick.intValue++ }
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) { h.postAtTime(what, who, `when`) }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) { h.removeCallbacks(what, who) }
        }
        if (active) d.start()
        onDispose { d.stop(); d.callback = null; h.removeCallbacksAndMessages(d) }
    }
    Canvas(Modifier.fillMaxSize()) {
        tick.intValue // redraw when the drawable asks
        drawRect(Color.Black)
        val p = PictureLayout.place(a.width, a.height, size.width, size.height, fit)
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save(); n.translate(p.dx, p.dy); n.scale(p.scale, p.scale)
            a.drawable.setBounds(0, 0, a.width, a.height); a.drawable.draw(n)
            n.restore()
        }
    }
}
