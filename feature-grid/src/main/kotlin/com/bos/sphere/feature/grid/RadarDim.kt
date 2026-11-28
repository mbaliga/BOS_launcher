package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarBackground
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlin.math.pow

/**
 * Wave 30 (radarDim, owner: "The wallpapers must dim or use a true black for the radar background, otherwise radar becomes a poor experience"): the maths of Radar's own
 * background. Dimmed draws the wallpaper under a black scrim of [SCRIM] (so no pixel is brighter than a quarter of what the wallpaper made it), and the area behind the
 * category wheel gets a second, softer black cap ([WHEEL_CAP]) because that is where the Spire tip and bloom sit. All plain numbers, tested on the JVM.
 */
internal object RadarDim {
    /** Black laid over the whole wallpaper: the wallpaper keeps at most 1 - SCRIM = 25 percent of its brightness. */
    const val SCRIM = 0.75f
    /** A second black layer over the wheel's zone: the worst pixel there (a pure white bloom) ends at 2 percent grey. */
    const val WHEEL_CAP = 0.92f
    /** The fade-in when Radar opens (not run under reduced motion). */
    const val FADE_MS = 240

    /** The brightest a channel can be after the layers [alphas] of black (each laid over the last) are drawn on a pixel of brightness [c] (0..1). */
    fun after(c: Float, vararg alphas: Float): Float { var v = c; for (a in alphas) v *= (1f - a); return v }

    /** The brightest possible pixel (pure white) after the scrim, and after the scrim and the wheel cap. */
    val brightestAnywhere: Float get() = after(1f, SCRIM)
    val brightestBehindWheel: Float get() = after(1f, SCRIM, WHEEL_CAP)

    fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    /** WCAG relative luminance of a grey. */
    fun greyLuminance(g: Float): Float = linear(g.coerceIn(0f, 1f))
    fun contrast(l1: Float, l2: Float): Float { val hi = maxOf(l1, l2); val lo = minOf(l1, l2); return (hi + 0.05f) / (lo + 0.05f) }

    /** Contrast of white text drawn at [textAlpha] over a background of grey [bg] (the text is the lighter colour). */
    fun whiteOnGrey(textAlpha: Float, bg: Float): Float {
        val text = textAlpha + (1f - textAlpha) * bg
        return contrast(greyLuminance(text), greyLuminance(bg))
    }
    /** Contrast of white text at [textAlpha] over the actual luminance (0..1, sRGB-encoded grey) of the worst background pixel. */
    fun whiteOnLuminance(textAlpha: Float, bgLuminance: Float): Float = whiteOnGrey(textAlpha, bgLuminance)

    /** How far, in dp, the labels reach from the wheel row's centre line (the longest label, EVERYTHING, with its count chip). */
    const val ZONE_HALF_WIDTH_DP = 190f
    /** The ellipse is this much bigger than the zone and flat out to [PLATEAU] of its radius, so every corner of the zone is under the full cap. */
    const val ELLIPSE_GROWTH = 1.9f
    const val PLATEAU = 0.8f

    /** The area that gets the wheel cap: the wheel row ([row], in the root's pixels), at most [ZONE_HALF_WIDTH_DP] either side of its centre line. */
    fun wheelZone(row: Rect, density: Float): Rect {
        val half = minOf(row.width / 2f, ZONE_HALF_WIDTH_DP * density)
        return Rect(row.center.x - half, row.top, row.center.x + half, row.bottom)
    }
    /** True when every corner of [box] lies in the flat, fully capped part of the ellipse over [zone]. */
    fun fullyCapped(zone: Rect, box: Rect): Boolean {
        val rx = zone.width / 2f * ELLIPSE_GROWTH; val ry = zone.height / 2f * ELLIPSE_GROWTH
        if (rx <= 0f || ry <= 0f) return false
        return listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom).all { (x, y) ->
            val nx = (x - zone.center.x) / rx; val ny = (y - zone.center.y) / ry
            nx * nx + ny * ny <= PLATEAU * PLATEAU
        }
    }

    const val SELECTED_LABEL_ALPHA = 0.9f
    const val NEIGHBOUR_LABEL_ALPHA = 0.35f
    const val SELECTED_MIN_CONTRAST = 4.5f
    const val NEIGHBOUR_MIN_CONTRAST = 3f
}

/**
 * Radar's background layer (wave 30). [RadarBackground.TrueBlack] draws nothing at all (the screen's own black base shows: no [SignalBackground] is composed, so the signal
 * glow, its clock and its signal collection do not run). [RadarBackground.Dimmed] draws the wallpaper of the Glance, then the [RadarDim.SCRIM] black over it, then the wheel cap
 * over [wheelZone]; it fades in when Radar opens (no fade under reduced motion). Cheap by design: two solid black layers with alpha and one cached radial brush, no blur.
 */
@Composable
internal fun RadarBackgroundLayer(prefs: LauncherPrefs, wheelZone: () -> Rect?, modifier: Modifier = Modifier) {
    if (prefs.radarBackground == RadarBackground.TrueBlack) {
        return
    }
    val reduced = rememberMotionPrefs().reduced
    val fade = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { if (reduced) fade.snapTo(1f) else fade.animateTo(1f, tween(RadarDim.FADE_MS)) }
    Box(modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.testTag(RADAR_DIM_TAG)) {
        val glance = prefs.wallpaper(WallpaperSurface.Glance)
        if (glance.kind == WallpaperKind.Live) SignalBackground(prefs, Modifier.fillMaxSize(), dark = true, choice = glance.live) // Radar is dark in every theme; HOM-16, PAL-24: the two background items, the same on every surface
        // Wave 34: any other wallpaper kind of the Glance (solid, gradient, picture, the system's), drawn still (an animated picture or video shows its first frame: nothing plays on Radar) and dimmed like the rest.
        else HomeSurfaceBackground(WallpaperSurface.Glance, prefs, Modifier.fillMaxSize(), scrim = false, play = false, alert = false)
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val zone = wheelZone()
                val scrim = Color.Black.copy(alpha = RadarDim.SCRIM)
                val cap = Color.Black.copy(alpha = RadarDim.WHEEL_CAP)
                // An ellipse 1.9 times the wheel row (half width capped by the caller), flat at full strength out to 80 percent of its radius so the label boxes' corners are inside it.
                val centre = zone?.center ?: Offset.Zero
                val ry = (zone?.height ?: 0f) / 2f * RadarDim.ELLIPSE_GROWTH
                val rx = (zone?.width ?: 0f) / 2f * RadarDim.ELLIPSE_GROWTH
                val brush = if (zone != null && ry > 0f) Brush.radialGradient(0f to cap, RadarDim.PLATEAU to cap, 1f to Color.Transparent, center = centre, radius = ry) else null
                val sx = if (ry > 0f) rx / ry else 1f
                onDrawBehind {
                    drawRect(scrim)
                    if (brush != null) scale(sx, 1f, pivot = centre) { drawCircle(brush, radius = ry, center = centre) }
                }
            },
        )
    }
}

/** The layer's own marker for tests (the dimmed background is present or not). */
internal const val RADAR_DIM_TAG = "radar-dim-background"
/** The wheel row (the "SEARCH" word and the wheel), whose zone gets the extra cap; tests read its bounds. */
internal const val RADAR_WHEEL_ROW_TAG = "radar-wheel-row"
