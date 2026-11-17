package com.bos.sphere.feature.grid

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.signals.AndroidSignalSource
import com.bos.sphere.core.signals.SignalKind
import com.bos.sphere.core.signals.SignalLogic
import com.bos.sphere.core.signals.SignalSource
import com.bos.sphere.core.signals.SignalState
import kotlin.math.hypot

/**
 * PAL-24: the Radar background. One Canvas, one animation clock, no recomposition per frame: every layer reads the
 * clock inside the draw lambda. Layers, bottom to top: breathing dot grid, scope rings and crosshair, the signal view,
 * comic lines (no signal only), sweep wedge, ping ring, centre dot. Decoration only: its semantics are cleared.
 */

/**
 * The shared animation clock. In the app it follows one infinite transition ([live], seconds); tests and previews set
 * [ms] directly. Read only inside draw lambdas, so a tick redraws and never recomposes.
 */
internal class RadarClock {
    val ms = mutableLongStateOf(0L)
    var live: State<Float>? = null
    fun now(): Long = live?.let { (it.value * 1000f).toLong() } ?: ms.longValue
}

/** Tests provide a source that injects states; null = the phone's real connectivity. */
internal val LocalSignalSource = staticCompositionLocalOf<SignalSource?> { null }

/**
 * One clock for every layer. It runs only while [active] (activity resumed and window focused, so never with the screen
 * off or Radar closed) and not [reduced] (NFR-14); otherwise nothing animates.
 */
@Composable
internal fun rememberRadarClock(active: Boolean, reduced: Boolean): RadarClock {
    val clock = remember { RadarClock() }
    val running = active && !reduced
    val t = if (running) {
        rememberInfiniteTransition(label = "radarClock").animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "t")
    } else null
    SideEffect { clock.live = t }
    return clock
}

/** Resumed activity and a focused window: the screen is on and the user can see it (battery). */
@Composable
internal fun rememberRadarActive(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return resumed && LocalWindowInfo.current.isWindowFocused
}

/** What the background draws, from Ground Control's settings. */
internal data class RadarBackdropConfig(
    val sweep: Boolean = true,
    val viewId: String = SignalViews.DEFAULT,
    val comic: Boolean = true,
    val intensity: RadarIntensity = RadarIntensity.Normal,
    val dark: Boolean = true,
    /** Paint the base colour (black / white) too; the Radar already has its own base. */
    val baseFill: Boolean = false,
) {
    val strength: Float get() = if (intensity == RadarIntensity.Subtle) 0.6f else 1f
    companion object { fun from(p: LauncherPrefs, dark: Boolean = true) = RadarBackdropConfig(p.radarSweep, p.radarSignalView, p.radarComicNoSignal, p.radarIntensity, dark) }
}

private fun ink(dark: Boolean) = if (dark) 0xFFFFFFFF.toInt() else 0xFF101016.toInt()
private fun argb(rgb: Int, alpha: Float) = (rgb and 0x00FFFFFF) or ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)

/** The dots, painted through a handful of alpha buckets with round caps; everything preallocated per size. */
internal class DotPainter(val w: Float, val h: Float, private val density: Float) {
    private val layout = DotGridMath.layout(w, h, density)
    private val buckets = Array(DotGridMath.BUCKETS) { FloatArray(layout.count * 2) }
    private val counts = IntArray(DotGridMath.BUCKETS)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun draw(canvas: android.graphics.Canvas, tMs: Long, still: Boolean, dark: Boolean, strength: Float) {
        DotGridMath.fillBuckets(layout, tMs, still, buckets, counts)
        val rgb = ink(dark)
        val tone = if (dark) 1f else 0.6f
        for (k in 1 until DotGridMath.BUCKETS) {
            val n = counts[k]; if (n == 0) continue
            val f = DotGridMath.bucketFraction(k)
            val r = DotGridMath.bucketRadiusDp(k) * density
            if (k >= 3) { // soft halo: a wider, much fainter dot underneath
                paint.strokeWidth = 2f * (r + 3f * density)
                paint.color = argb(rgb, 0.05f * f * strength * tone)
                canvas.drawPoints(buckets[k], 0, n * 2, paint)
            }
            paint.strokeWidth = 2f * r
            paint.color = argb(rgb, DotGridMath.PEAK_ALPHA * f * strength * tone)
            canvas.drawPoints(buckets[k], 0, n * 2, paint)
        }
    }
}

/** Rings, crosshair, sweep wedge, ping and centre dot of the scope, built once per size and config. */
internal class ScopePainter(val w: Float, val h: Float, private val density: Float, dark: Boolean, private val strength: Float) {
    private val c = Offset(w / 2f, h / 2f)
    private val maxR = 0.5f * hypot(w, h)
    private val tone = if (dark) 1f else 0.6f
    private val inkC = Color(ink(dark))
    private val hair = Stroke(1f * density)
    private val pingStroke = Stroke(1.2f * density)
    private val wedge = Brush.sweepGradient(
        colorStops = arrayOf(
            0f to Color.Transparent, 0.80f to Color.Transparent,
            0.93f to inkC.copy(alpha = 0.035f * strength * tone), 1f to inkC.copy(alpha = 0.075f * strength * tone),
        ),
        center = c,
    )

    fun drawStatic(s: DrawScope) = with(s) {
        for (f in ScopeMath.RINGS) drawCircle(inkC.copy(alpha = 0.07f * strength * tone), radius = maxR * f, center = c, style = hair)
        drawLine(inkC.copy(alpha = 0.06f * strength * tone), Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 1f * density)
        drawLine(inkC.copy(alpha = 0.06f * strength * tone), Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 1f * density)
    }

    fun drawMoving(s: DrawScope, tMs: Long, still: Boolean) = with(s) {
        val angle = if (still) ScopeMath.STILL_ANGLE else ScopeMath.sweepAngle(tMs)
        rotate(angle, c) {
            drawCircle(wedge, radius = maxR, center = c)
            drawLine(inkC.copy(alpha = 0.09f * strength * tone), c, Offset(c.x + maxR, c.y), 1.4f * density)
        }
        if (!still) {
            val p = ScopeMath.pingProgress(tMs)
            if (p >= 0f) drawCircle(inkC.copy(alpha = 0.26f * ScopeMath.pingAlpha(p) * strength * tone), radius = maxR * 0.9f * ScopeMath.pingRadius(p), center = c, style = pingStroke)
        }
        drawCircle(inkC.copy(alpha = 0.16f * strength * tone), radius = 1.5f * density, center = c)
    }
}

/** Comic lines: the buffers filled each (stepped) frame, three width classes. */
internal class ComicPainter(val w: Float, val h: Float, private val density: Float, private val dark: Boolean) {
    private val lines = ComicLines()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    fun draw(canvas: android.graphics.Canvas, tMs: Long, still: Boolean, fade: Float, strength: Float) {
        lines.fill(w, h, tMs, still)
        val rgb = if (dark) 0xFFE8E8EC.toInt() else 0xFF1C1C22.toInt()
        for (k in 0 until ComicLines.CLASSES) {
            if (lines.counts[k] == 0) continue
            paint.strokeWidth = ComicLines.WIDTH_DP[k] * density
            paint.color = argb(rgb, ComicLines.ALPHA[k] * fade * strength * (if (dark) 1f else 0.8f))
            canvas.drawLines(lines.buffers[k], 0, lines.counts[k], paint)
        }
    }
}

/** One frame of everything, from per-size painters. Shared by the composable and the draw tests. */
internal class RadarBackdropPainter(w: Float, h: Float, density: Float, private val cfg: RadarBackdropConfig, registry: List<SignalView> = SignalViews.views) {
    private val geo = RadarGeometry(w, h, density)
    private val dots = DotPainter(w, h, density)
    private val scope = ScopePainter(w, h, density, cfg.dark, cfg.strength)
    private val view: SignalPainter? = SignalViews.forId(cfg.viewId, registry)?.prepare(geo)
    private val comic = ComicPainter(w, h, density, cfg.dark)
    private val params = SignalDrawParams().also { it.dark = cfg.dark; it.intensity = cfg.strength }
    private val comicFade = Fade()

    fun draw(s: DrawScope, signal: SignalState?, tMs: Long, still: Boolean) {
        val canvas = s.drawContext.canvas.nativeCanvas
        if (cfg.baseFill) s.drawRect(if (cfg.dark) Color.Black else Color(0xFFF3F3F6))
        dots.draw(canvas, tMs, still, cfg.dark, cfg.strength)
        if (cfg.sweep) scope.drawStatic(s)
        val wantComic = cfg.comic && signal != null && !signal.hasSignal
        val shown = comicFade.step(if (wantComic) 1f else 0f, tMs, 400, still)
        params.timeMs = tMs; params.still = still; params.comicShown = shown
        view?.draw(s, signal ?: PENDING, params)
        if (shown > 0.01f) comic.draw(canvas, tMs, still, shown, cfg.strength)
        if (cfg.sweep) scope.drawMoving(s, tMs, still)
    }

    private companion object { val PENDING = SignalState.NoSignal }
}

/** The background itself: pass the signal (null = not known yet) and the clock; [still] draws the reduced-motion frame. */
@Composable
internal fun RadarBackground(
    config: RadarBackdropConfig,
    signal: State<SignalState?>,
    clock: RadarClock,
    still: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().clearAndSetSemantics { }.drawWithCache {
            val painter = RadarBackdropPainter(size.width, size.height, density, config)
            onDrawBehind {
                val t = if (still) ScopeMath.STILL_TIME_MS else clock.now()
                painter.draw(this, signal.value, t, still)
            }
        },
    )
}

/** Reusable breathing dot grid (owner's design, docs/design/radar-dot-grid.md) on black, or its inverse when [dark] is false. */
@Composable
fun BreathingDotGrid(modifier: Modifier = Modifier, dark: Boolean = true, motion: Boolean = true) {
    val reduced = rememberMotionPrefs().reduced || !motion
    val clock = rememberRadarClock(active = rememberRadarActive(), reduced = reduced)
    Box(
        modifier.clearAndSetSemantics { }.drawWithCache {
            val painter = DotPainter(size.width, size.height, density)
            onDrawBehind {
                drawRect(if (dark) Color.Black else Color(0xFFF3F3F6))
                painter.draw(drawContext.canvas.nativeCanvas, if (reduced) 0L else clock.now(), reduced, dark, 1f)
            }
        },
    )
}

/** The words of the TalkBack description, translated ("Signal: good, Wi-Fi"). */
internal fun signalDescription(s: SignalState): String {
    if (!s.hasSignal) return L10n.tr("No signal")
    val level = when (SignalLogic.levelWord(s.level)) {
        "weak" -> L10n.tr("weak"); "fair" -> L10n.tr("fair"); "good" -> L10n.tr("good"); else -> L10n.tr("excellent")
    }
    val kind = when (s.kind) {
        SignalKind.Wifi -> L10n.tr("Wi-Fi"); SignalKind.Cellular -> L10n.tr("Mobile data"); SignalKind.Ethernet -> L10n.tr("Wired"); SignalKind.None -> L10n.tr("Offline")
    }
    return L10n.tr("Signal: %s, %s", level, kind)
}

/** Live (or forced) signal as state; the live source is collected only while the host is in composition and the activity is started. */
@Composable
internal fun rememberRadarSignal(force: SignalForce, wanted: Boolean): State<SignalState?> {
    val context = LocalContext.current
    val injected = LocalSignalSource.current
    val live = remember { mutableStateOf<SignalState?>(null) }
    val owner = LocalLifecycleOwner.current
    val needLive = wanted && force == SignalForce.Live
    LaunchedEffect(needLive, injected, owner) {
        if (!needLive) return@LaunchedEffect
        val source = injected ?: AndroidSignalSource(context)
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { source.states().collect { live.value = it } }
    }
    return remember(force) { derivedStateOf { if (force == SignalForce.Live) live.value else SignalState.forced(force.level) } }
}

/** What Radar puts behind its content: the background plus a polite description of the signal for TalkBack. */
@Composable
internal fun RadarBackgroundHost(prefs: LauncherPrefs, modifier: Modifier = Modifier, dark: Boolean = true) {
    val reduced = rememberMotionPrefs().reduced
    val active = rememberRadarActive()
    val clock = rememberRadarClock(active, reduced)
    val cfg = RadarBackdropConfig.from(prefs, dark)
    val showsSignal = cfg.viewId != SignalViews.OFF || cfg.comic
    val signal = rememberRadarSignal(prefs.radarSignalForce, showsSignal)
    RadarBackground(cfg, signal, clock, reduced, modifier)
    val s = signal.value
    if (showsSignal && s != null) {
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = signalDescription(s) })
    }
}
