package com.bos.sphere.feature.grid

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.BackgroundKind
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.quality.HomeLegibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFR-15: wallpaper colours the home text can land on, so the scrim can be sized from real data. Solid and gradient
 * backgrounds are exact (every stop counts); a picture is sampled on a 3x3 grid from a downscaled decode; the system
 * wallpaper uses the colours the platform extracts. Textures and live scenes are animated or procedural and are not
 * sampled: they use the mid-grey fallback of [com.bos.sphere.core.quality.Contrast.scrimAlphaForAny] (known limit).
 */
object WallpaperSamples {
    fun staticColors(kind: BackgroundKind, preset: String, colorA: Long, colorB: Long): List<Int> = when (kind) {
        BackgroundKind.Black -> listOf(0xFF000000.toInt())
        BackgroundKind.Solid -> listOf(
            (if (preset == BackgroundMath.CUSTOM) colorA else BackgroundPresets.solidColor(preset) ?: colorA).toInt(),
        )
        BackgroundKind.Gradient -> (
            if (preset == BackgroundMath.CUSTOM) listOf(colorA, colorB) else BackgroundPresets.gradientStops(preset)?.colors ?: listOf(colorA, colorB)
            ).map { it.toInt() }
        else -> emptyList()
    }

    /** 3x3 grid of pixel colours of the image at [path], decoded at most about 64 px wide. Empty when unreadable. */
    fun photoColors(path: String): List<Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return emptyList()
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return emptyList()
        val out = ArrayList<Int>(9)
        for (iy in 0..2) for (ix in 0..2) out += bmp.getPixel((bmp.width - 1) * ix / 2, (bmp.height - 1) * iy / 2) or (0xFF shl 24)
        bmp.recycle()
        return out
    }
}

/** The scrim over the wallpaper for the current background; recomputed off the main thread when the background changes. */
@Composable
fun rememberHomeScrim(kind: BackgroundKind, preset: String, colorA: Long, colorB: Long, picturePath: String): HomeLegibility.Plan {
    val context = LocalContext.current
    var plan by remember { mutableStateOf(HomeLegibility.Plan(0xFF000000.toInt(), 0.0, true)) }
    val texts = listOf(HyleColors.InkBright.toArgb(), HyleColors.Ink.toArgb())
    LaunchedEffect(kind, preset, colorA, colorB, picturePath, texts) {
        val samples = withContext(Dispatchers.Default) {
            val fixed = WallpaperSamples.staticColors(kind, preset, colorA, colorB)
            when {
                fixed.isNotEmpty() -> fixed
                kind == BackgroundKind.Picture && preset == "photo" && picturePath.isNotEmpty() -> runCatching { WallpaperSamples.photoColors(picturePath) }.getOrDefault(emptyList())
                kind == BackgroundKind.System -> runCatching {
                    val c = context.getSystemService(WallpaperManager::class.java)?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    listOfNotNull(c?.primaryColor, c?.secondaryColor, c?.tertiaryColor).map { it.toArgb() }
                }.getOrDefault(emptyList())
                else -> emptyList()
            }
        }
        plan = withContext(Dispatchers.Default) { HomeLegibility.plan(texts, samples) }
    }
    return plan
}

/** Draws the scrim; nothing when the wallpaper already gives AA. */
@Composable
fun HomeScrim(plan: HomeLegibility.Plan, modifier: Modifier = Modifier) {
    if (plan.alpha <= 0.0) return
    Box(modifier.fillMaxSize().background(Color(plan.scrimColor).copy(alpha = plan.alpha.toFloat())))
}
