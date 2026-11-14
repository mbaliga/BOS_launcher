package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.bos.sphere.core.catalog.WallpaperBridge

/**
 * HOM-16: while home is drawn over the system wallpaper, forwards the scroll position (in pages)
 * to the wallpaper so a live or panoramic wallpaper follows the pages.
 */
@Composable
fun WallpaperScrollEffect(enabled: Boolean, position: Float, pageCount: Int) {
    if (!enabled) return
    val context = LocalContext.current
    val view = LocalView.current
    val bridge = remember(context) { WallpaperBridge(context) }
    LaunchedEffect(position, pageCount, view.windowToken) { bridge.forwardOffset(view.windowToken, position, pageCount) }
}

/** The accent to use: derived from the system wallpaper when [fromWallpaper], else [fallback]. Updates when the wallpaper changes. */
@Composable
fun rememberHomeAccent(fromWallpaper: Boolean, fallback: Long): Long {
    if (!fromWallpaper) return fallback
    val context = LocalContext.current
    val bridge = remember(context) { WallpaperBridge(context) }
    var accent by remember(fallback) { mutableLongStateOf(bridge.accent(fallback.toInt()).toLong() and 0xFFFFFFFFL) }
    DisposableEffect(bridge, fallback) {
        val off = bridge.addColorsListener({ accent = it.toLong() and 0xFFFFFFFFL }, fallback.toInt())
        onDispose(off)
    }
    return accent
}
