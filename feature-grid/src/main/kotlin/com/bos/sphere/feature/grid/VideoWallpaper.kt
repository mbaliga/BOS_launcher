package com.bos.sphere.feature.grid

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.bos.sphere.core.data.GifMode
import com.bos.sphere.core.data.PictureFit
import com.bos.sphere.core.data.Wallpaper
import java.io.File

/** The seam of the video player (a test passes a fake that records start and release; the app uses [MediaPlayerEngine]). */
internal interface VideoEngine {
    /** Starts muted playback of [file] onto [surface]; [loop] true repeats, false stops on the last frame. Never blocks the caller. */
    fun start(file: File, surface: Surface, loop: Boolean, onSize: (Int, Int) -> Unit)
    /** Stops and frees the player. Safe to call twice. */
    fun release()
}

internal fun interface VideoEngineFactory { fun create(): VideoEngine }
internal val LocalVideoEngineFactory = androidx.compose.runtime.staticCompositionLocalOf { VideoEngineFactory { MediaPlayerEngine() } }

/**
 * MediaPlayer on its own handler thread, so preparing and decoding calls never run on the main thread. Always muted, never asks for audio focus
 * (MediaPlayer does not unless the app does), no wake lock. Errors end quietly: the poster underneath stays.
 */
internal class MediaPlayerEngine : VideoEngine {
    private val thread = HandlerThread("wallpaper-video").also { it.start() }
    private val h = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    override fun start(file: File, surface: Surface, loop: Boolean, onSize: (Int, Int) -> Unit) {
        h.post {
            stopNow()
            val mp = MediaPlayer()
            try {
                mp.setVolume(0f, 0f)
                mp.isLooping = loop
                mp.setDataSource(file.path)
                mp.setSurface(surface)
                mp.setOnVideoSizeChangedListener { _, w, hh -> main.post { onSize(w, hh) } }
                mp.setOnErrorListener { p, _, _ -> runCatching { p.release() }; if (player === p) player = null; true }
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
                player = mp
            } catch (_: Exception) { runCatching { mp.release() } }
        }
    }

    private fun stopNow() { player?.let { runCatching { it.stop() }; runCatching { it.release() } }; player = null }

    override fun release() { h.post { stopNow(); thread.quitSafely() } }
}

/** The matrix that lays a video the TextureView stretched to the view on the screen as Fill or Fit. */
internal fun videoMatrix(videoW: Int, videoH: Int, viewW: Float, viewH: Float, fit: PictureFit): Matrix {
    val p = PictureLayout.place(videoW, videoH, viewW, viewH, fit)
    return Matrix().apply { setScale(videoW * p.scale / viewW, videoH * p.scale / viewH, viewW / 2f, viewH / 2f) }
}

/**
 * The video wallpaper: a muted TextureView over the poster frame. It exists only while [active] (resumed, focused: released in onStop and when the
 * window loses focus) and the mode plays ([GifMode.Once] stops on the last frame, [GifMode.LoopContinuous] repeats; a video has no Bounce).
 */
@Composable
internal fun VideoWallpaper(file: File, w: Wallpaper, mode: GifMode, active: Boolean) {
    StillPicture(file, w) // the poster (first frame) is always underneath
    if (!active) return
    val factory = LocalVideoEngineFactory.current
    val engine = remember(file, mode) { factory.create() }
    DisposableEffect(engine) { onDispose { engine.release() } }
    val fit = w.picture.fit
    Box(Modifier.fillMaxSize().background(Color.Transparent)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    var vw = 0; var vh = 0
                    val apply = { if (vw > 0 && width > 0) setTransform(videoMatrix(vw, vh, width.toFloat(), height.toFloat(), fit)) }
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        var surface: Surface? = null
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            val s = Surface(st).also { surface = it }
                            engine.start(file, s, loop = mode == GifMode.LoopContinuous) { a, b -> vw = a; vh = b; apply() }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { engine.release(); surface?.release(); surface = null; return true }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
