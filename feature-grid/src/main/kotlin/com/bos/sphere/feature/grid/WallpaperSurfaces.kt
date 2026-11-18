package com.bos.sphere.feature.grid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.sharesWallpaper
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.quality.HomeLegibility
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The geometry of a linear two-stop gradient at an angle (0 = left to right, 90 = top to bottom), pure so it can be tested. */
internal object GradientGeometry {
    /** Start and end points so the gradient's colour line spans the whole rectangle at [angle] degrees. */
    fun endpoints(w: Float, h: Float, angle: Int): Pair<Offset, Offset> {
        val r = Math.toRadians(angle.toDouble())
        val dx = cos(r).toFloat(); val dy = sin(r).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val c = Offset(w / 2f, h / 2f)
        return Offset(c.x - dx * half, c.y - dy * half) to Offset(c.x + dx * half, c.y + dy * half)
    }

    /** [n] evenly spaced colours along the gradient, for the legibility check. */
    fun samples(g: GradientSpec, n: Int = 9): List<Int> = List(n) { i ->
        val t = i / (n - 1f)
        androidx.compose.ui.graphics.lerp(Color(g.from.toInt()), Color(g.to.toInt()), t).toArgb() or (0xFF shl 24)
    }
}

/** A solid or gradient wallpaper: one rectangle fill, the brush built once per size, no animation, no allocation per frame. */
@Composable
internal fun FlatWallpaper(w: Wallpaper, modifier: Modifier = Modifier) {
    val solid = w.solid; val g = w.gradient; val kind = w.kind
    Box(
        modifier.fillMaxSize().clearAndSetSemantics { }.drawWithCache {
            if (kind == WallpaperKind.Gradient) {
                val (a, b) = GradientGeometry.endpoints(size.width, size.height, g.angle)
                val brush = Brush.linearGradient(listOf(Color(g.from.toInt()), Color(g.to.toInt())), start = a, end = b)
                onDrawBehind { drawRect(brush) }
            } else {
                val color = Color(solid.toInt())
                onDrawBehind { drawRect(color) }
            }
        },
    )
}

/** Which background instance is on screen: the pages' own, or the Glance's, plus the kind and (live only) the choice. */
private data class WallpaperKey(val owner: WallpaperSurface, val kind: WallpaperKind, val live: BackgroundChoice?)

/** The legibility scrim for a solid or gradient wallpaper (live wallpapers sample their painters, see [rememberHomeScrim]). */
@Composable
internal fun rememberFlatScrim(w: Wallpaper): HomeLegibility.Plan {
    val texts = listOf(com.bos.sphere.core.design.HyleColors.InkBright.toArgb(), com.bos.sphere.core.design.HyleColors.Ink.toArgb())
    return remember(w.kind, w.solid, w.gradient, texts) {
        val samples = if (w.kind == WallpaperKind.Gradient) GradientGeometry.samples(w.gradient) else listOf(w.solid.toInt() or (0xFF shl 24))
        HomeLegibility.plan(texts, samples)
    }
}

/**
 * HOM-16 (wave 26): THE seam every home surface draws its wallpaper through: `HomeSurfaceBackground(WallpaperSurface.Glance, prefs)`
 * behind the Glance, `HomeSurfaceBackground(WallpaperSurface.Pages, prefs)` behind the paginated pages and the scroll alike. It
 * picks the surface's wallpaper (the pages use the Glance one while the "same as Glance" switch is on), draws a solid colour, a
 * gradient or the live wallpaper through the two-slot [SignalBackground], and the NFR-15 scrim over it. When the wallpaper
 * is the same on both surfaces the key does not change across a surface change, so the one instance stays (no crossfade, no
 * second layer); when it differs the two cross-fade (a short fade under reduced motion). Fills its box; no touch handling.
 */
@Composable
fun HomeSurfaceBackground(
    surface: WallpaperSurface,
    prefs: LauncherPrefs,
    modifier: Modifier = Modifier,
    baseFill: Boolean = true,
    animate: Boolean = true,
    announce: Boolean = true,
    scrim: Boolean = true,
) {
    val w = prefs.wallpaper(surface)
    val owner = if (prefs.sharesWallpaper(surface)) WallpaperSurface.Glance else surface
    val key = WallpaperKey(owner, w.kind, if (w.kind == WallpaperKind.Live) w.live else null)
    val latest = remember { HashMap<WallpaperKey, Wallpaper>() }.also { it[key] = w }
    val motion = rememberMotionPrefs()
    val dark = isDarkSurface()
    Box(modifier) {
        Crossfade(key, Modifier.fillMaxSize(), animationSpec = tween(motion.crossFadeMs(260)), label = "wallpaper") { k ->
            val shown = latest[k] ?: w
            if (shown.kind == WallpaperKind.Live) SignalBackground(prefs, Modifier.fillMaxSize(), dark = dark, baseFill = baseFill, animate = animate, announce = announce, choice = shown.live)
            else FlatWallpaper(shown)
        }
        if (scrim) {
            if (w.kind == WallpaperKind.Live) HomeScrim(rememberHomeScrim(w.live, prefs.radarIntensity, dark))
            else HomeScrim(rememberFlatScrim(w))
        }
    }
}

/** English summary of a wallpaper for a settings row: its kind, and the items for a live one. */
fun wallpaperSummary(w: Wallpaper, tr: (String) -> String): String = when (w.kind) {
    WallpaperKind.Live -> tr(w.kind.label) + ": " + (w.live.items.joinToString(" + ") { tr(it.label) }.ifEmpty { tr("None") })
    else -> tr(w.kind.label)
}
