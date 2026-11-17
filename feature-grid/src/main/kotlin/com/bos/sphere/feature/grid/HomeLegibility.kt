package com.bos.sphere.feature.grid

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
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.quality.HomeLegibility
import com.bos.sphere.core.signals.SignalState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFR-15 (wave 15): the colours text can land on over the background, so the scrim is sized from what is really drawn. The
 * two slots are painted once, off the main thread, into a small bitmap with the same painters the screen uses, at the
 * brightest level (4: the most light, so the strictest case) and at a mid level, at a few clock values, and sampled on a
 * grid. Nothing is assumed about an item: a new item is covered the moment it is in the registry.
 */
object BackgroundSamples {
    private const val W = 48
    private const val H = 96
    private val TIMES = longArrayOf(ScopeMath.STILL_TIME_MS, 4_000L, 9_000L, 31_000L)
    private val LEVELS = intArrayOf(4, 2)

    internal fun colors(choice: BackgroundChoice, intensity: RadarIntensity, dark: Boolean, levels: IntArray = LEVELS, times: LongArray = TIMES, registry: List<SignalView> = SignalViews.views): List<Int> {
        if (choice.items.isEmpty()) return listOf(if (dark) 0xFF000000.toInt() else 0xFFF3F3F6.toInt())
        val cfg = BackgroundConfig(choice, intensity, dark, baseFill = true)
        val out = ArrayList<Int>(25 * levels.size * times.size)
        for (level in levels) for (t in times) {
            val image = androidx.compose.ui.graphics.ImageBitmap(W, H)
            val canvas = androidx.compose.ui.graphics.Canvas(image)
            // A fresh painter per frame: its level easing starts at the target, so the sample is the settled picture.
            val painter = BackgroundPainter(W.toFloat(), H.toFloat(), 1f, cfg, registry)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas,
                androidx.compose.ui.geometry.Size(W.toFloat(), H.toFloat()),
            ) { painter.draw(this, SignalState.forced(level), t, true) }
            val px = IntArray(W * H)
            image.readPixels(px, 0, 0, W, H)
            out += gridOf(px, W, H)
        }
        return out
    }

    /** 5x5 grid of opaque pixel colours across a [w]x[h] pixel block. */
    internal fun gridOf(px: IntArray, w: Int, h: Int): List<Int> {
        val out = ArrayList<Int>(25)
        for (iy in 0..4) for (ix in 0..4) out += px[((h - 1) * iy / 4) * w + (w - 1) * ix / 4] or (0xFF shl 24)
        return out
    }
}

/** NFR-15: the last legibility check, shown as a Lab row. Null until the home has computed one. */
object LegibilityReport {
    var line by mutableStateOf<String?>(null)
        internal set
}

/** The scrim over the background for the current choice; recomputed off the main thread when the choice changes. */
@Composable
fun rememberHomeScrim(choice: BackgroundChoice, intensity: RadarIntensity, dark: Boolean = true): HomeLegibility.Plan {
    var plan by remember { mutableStateOf(HomeLegibility.Plan(0xFF000000.toInt(), 0.0, true)) }
    val texts = listOf(HyleColors.InkBright.toArgb(), HyleColors.Ink.toArgb())
    LaunchedEffect(choice, intensity, dark, texts) {
        val samples = withContext(Dispatchers.Default) { runCatching { BackgroundSamples.colors(choice, intensity, dark) }.getOrDefault(emptyList()) }
        plan = withContext(Dispatchers.Default) { HomeLegibility.plan(texts, samples) }
        LegibilityReport.line = HomeLegibility.describe(choice.items.joinToString(" + ") { it.label }.ifEmpty { "None" } + (if (samples.isEmpty()) " (not sampled, mid-grey assumed)" else ""), plan)
    }
    return plan
}

/** Draws the scrim; nothing when the background already gives AA. */
@Composable
fun HomeScrim(plan: HomeLegibility.Plan, modifier: Modifier = Modifier) {
    if (plan.alpha <= 0.0) return
    Box(modifier.fillMaxSize().background(Color(plan.scrimColor).copy(alpha = plan.alpha.toFloat())))
}
