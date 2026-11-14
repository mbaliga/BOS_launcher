package com.bos.sphere.core.catalog

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.IBinder

/**
 * HOM-16: bridges home to the system wallpaper. Public APIs only: [WallpaperManager.setWallpaperOffsets]
 * forwards the scroll position, and [WallpaperManager.getWallpaperColors] feeds the accent.
 */
class WallpaperBridge(context: Context) {
    private val wm = WallpaperManager.getInstance(context.applicationContext)

    fun forwardOffset(windowToken: IBinder?, position: Float, pageCount: Int) {
        if (windowToken == null) return
        runCatching {
            wm.setWallpaperOffsetSteps(WallpaperMath.xStep(pageCount), 0f)
            wm.setWallpaperOffsets(windowToken, WallpaperMath.xOffset(position, pageCount), 0.5f)
        }
    }

    /** Accent derived from the current wallpaper, or [fallback] when it is not readable or not vivid. */
    fun accent(fallback: Int): Int {
        val c: WallpaperColors = runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return fallback
        val list = listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor).map { it.toArgb() }
        return WallpaperMath.accent(list, fallback)
    }

    fun addColorsListener(onChanged: (Int) -> Unit, fallback: Int): () -> Unit {
        val l = WallpaperManager.OnColorsChangedListener { _, _ -> onChanged(accent(fallback)) }
        wm.addOnColorsChangedListener(l, android.os.Handler(android.os.Looper.getMainLooper()))
        return { wm.removeOnColorsChangedListener(l) }
    }
}
