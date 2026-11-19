package com.bos.sphere.feature.grid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.testTag
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
private data class WallpaperKey(val owner: WallpaperSurface, val kind: WallpaperKind, val live: BackgroundChoice?, val pic: String = "")

/**
 * Wave 34: the system live wallpaper kind. The launcher draws NOTHING opaque here (the window is transparent and wallpaper-backed, see themes.xml), so
 * whatever the system shows behind the launcher, a live wallpaper included, shows through. Only the optional dim (a black layer, 0 / 25 / 50 percent) is drawn.
 */
@Composable
internal fun SystemWallpaperLayer(w: Wallpaper, modifier: Modifier = Modifier) {
    val dim = w.systemDim
    Box(modifier.fillMaxSize().clearAndSetSemantics { }.testTag(SYSTEM_WALLPAPER_TAG).drawBehind { if (dim > 0) drawRect(Color.Black.copy(alpha = dim / 100f)) })
}
internal const val SYSTEM_WALLPAPER_TAG = "system-wallpaper-layer"
internal const val SPIDER_SENSE_TAG = "spider-sense-alert"

/**
 * The spider-sense alert (owner: "the comic-book lines alert appears when there is an issue, like lack of connectivity"): the comic speed lines of
 * [ComicPainter], drawn alone, over any wallpaper that does not already draw them (everything but a signal wallpaper with items, which draws them itself).
 * It follows the same motion policy as the signal wallpaper: one shared clock only while the app is resumed and focused, a still frame under reduced motion.
 * It paints nothing while there is signal (or the signal is not known yet), and collects the signal only while composed and started.
 */
@Composable
fun SpiderSenseAlert(prefs: LauncherPrefs, modifier: Modifier = Modifier, dark: Boolean = isDarkSurface()) {
    val reduced = rememberMotionPrefs().reduced
    val active = rememberRadarActive()
    val clock = rememberRadarClock(active, reduced)
    val cfg = remember(prefs.radarIntensity, dark) { BackgroundConfig(BackgroundChoice(com.bos.sphere.core.data.BgItem.None, com.bos.sphere.core.data.BgItem.None), prefs.radarIntensity, dark, baseFill = false) }
    val signal = rememberRadarSignal(prefs.radarSignalForce, wanted = true)
    SignalBackgroundCanvas(cfg, signal, clock, reduced, modifier.testTag(SPIDER_SENSE_TAG))
}

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
    /** Wave 34: false draws an animated picture or video as its still first frame (Radar, thumbnails). */
    play: Boolean = true,
    /** Wave 34: the spider-sense alert over this wallpaper; off only where the caller draws it itself. */
    alert: Boolean = true,
    dark: Boolean = isDarkSurface(),
) {
    val w = prefs.wallpaper(surface)
    val owner = if (prefs.sharesWallpaper(surface)) WallpaperSurface.Glance else surface
    val key = WallpaperKey(owner, w.kind, if (w.kind == WallpaperKind.Live) w.live else null, if (w.kind == WallpaperKind.Picture) w.picture.file else "")
    val latest = remember { HashMap<WallpaperKey, Wallpaper>() }.also { it[key] = w }
    val motion = rememberMotionPrefs()
    Box(modifier) {
        Crossfade(key, Modifier.fillMaxSize(), animationSpec = tween(motion.crossFadeMs(260)), label = "wallpaper") { k ->
            val shown = latest[k] ?: w
            when (shown.kind) {
                WallpaperKind.Live -> SignalBackground(prefs, Modifier.fillMaxSize(), dark = dark, baseFill = baseFill, animate = animate, announce = announce, choice = shown.live)
                WallpaperKind.System -> SystemWallpaperLayer(shown)
                WallpaperKind.Picture -> PictureWallpaper(shown, play = play && animate)
                else -> FlatWallpaper(shown)
            }
        }
        if (scrim) {
            when (w.kind) {
                WallpaperKind.Live -> HomeScrim(rememberHomeScrim(w.live, prefs.radarIntensity, dark))
                WallpaperKind.System, WallpaperKind.Picture -> Unit // the user's own dim chips do the job; a picture or the system wallpaper has no colour the app knows
                else -> HomeScrim(rememberFlatScrim(w))
            }
        }
        // Wave 34: the alert over every kind; a signal wallpaper that has items draws it itself, inside its own layers.
        if (alert && (w.kind != WallpaperKind.Live || w.live.items.isEmpty())) SpiderSenseAlert(prefs, Modifier.fillMaxSize(), dark = dark)
    }
}

/** English summary of a wallpaper for a settings row: its kind, and the items for a live one. */
fun wallpaperSummary(w: Wallpaper, tr: (String) -> String): String = when (w.kind) {
    WallpaperKind.Live -> tr(w.kind.label) + ": " + (w.live.items.joinToString(" + ") { tr(it.label) }.ifEmpty { tr("None") })
    WallpaperKind.Picture -> tr(w.kind.label) + (if (w.picture.plays) ": " + tr(if (w.picture.video) "Video" else "Animated") + ", " + tr("uses more power") else "")
    else -> tr(w.kind.label)
}
