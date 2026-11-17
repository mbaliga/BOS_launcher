package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bos.sphere.core.data.GlanceWeather
import com.bos.sphere.core.data.GlanceWeatherEffect
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlin.math.floor
import kotlin.math.sin

/**
 * HOM-23: the immersive weather ambience behind the Glance clock. One Canvas, gentle and low contrast, drawn from shapes that
 * are precomputed once (flat float arrays and brushes cached per size), so a frame allocates nothing. It reads the shared
 * clock inside the draw lambda (no recomposition per frame), draws one still frame under reduced motion, and the clock does
 * not run while the screen is off or the surface is not visible. Absent when there is no weather ([effect] null).
 */
@Composable
fun GlanceWeatherLayer(
    effect: GlanceWeatherEffect?,
    intensity: RadarIntensity,
    dark: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    if (effect == null) return
    val reduced = rememberMotionPrefs().reduced
    val active = rememberRadarActive()
    val still = reduced || !animate
    val clock = rememberRadarClock(GlanceWeather.animates(reducedMotion = reduced, screenOn = active, surfaceVisible = animate), false)
    val shapes = remember { WeatherShapes() }
    val strength = GlanceWeather.strength(intensity)
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize().clearAndSetSemantics { }.drawWithCache {
            val brushes = WeatherBrushes(size.width, size.height, dark, effect)
            val px = density
            onDrawBehind {
                val t = if (still) WeatherShapes.STILL_MS else clock.now()
                drawWeather(effect, t, strength, dark, shapes, brushes, px, still)
            }
        },
    )
}

/** Flat arrays of random-but-fixed shape parameters (one seed, so a still frame is the same everywhere). */
internal class WeatherShapes(seed: Long = 16L) {
    val n = 64
    val xs = FloatArray(n); val ys = FloatArray(n); val sz = FloatArray(n); val ph = FloatArray(n); val sp = FloatArray(n)
    init {
        val r = java.util.Random(seed)
        for (i in 0 until n) { xs[i] = r.nextFloat(); ys[i] = r.nextFloat(); sz[i] = 0.4f + r.nextFloat() * 0.6f; ph[i] = r.nextFloat() * 6.2831855f; sp[i] = 0.6f + r.nextFloat() * 0.8f }
    }
    companion object { const val STILL_MS = 9_000L }
}

/** Brushes that depend on the size; built once per size in the draw cache. */
internal class WeatherBrushes(val w: Float, val h: Float, dark: Boolean, effect: GlanceWeatherEffect) {
    private val warm = Color(0xFFFFB45A)
    private val cloudTone = if (dark) Color(0xFF9AA7BD) else Color(0xFF5C6678)
    private val fogTone = if (dark) Color(0xFFB8C0CC) else Color(0xFF7A8496)
    val dayGlow: Brush = Brush.radialGradient(listOf(warm.copy(alpha = 0.55f), warm.copy(alpha = 0f)), Offset(w * 0.5f, h * 0.3f), maxOf(w, h) * 0.55f)
    val cloud: Brush = Brush.radialGradient(listOf(cloudTone.copy(alpha = 0.55f), cloudTone.copy(alpha = 0f)), Offset.Zero, 100f)
    val stormCloud: Brush = Brush.radialGradient(listOf(Color(0xFF55607A).copy(alpha = 0.6f), Color(0xFF55607A).copy(alpha = 0f)), Offset.Zero, 100f)
    val fogH = h * 0.35f
    val fog: Brush = Brush.verticalGradient(listOf(Color.Transparent, fogTone.copy(alpha = 0.5f), Color.Transparent), 0f, fogH)
}

private fun frac(x: Float) = x - floor(x)

private fun DrawScope.drawWeather(effect: GlanceWeatherEffect, t: Long, k: Float, dark: Boolean, s: WeatherShapes, b: WeatherBrushes, px: Float, still: Boolean) {
    val w = b.w; val h = b.h
    val tf = t.toFloat()
    val ink = if (dark) Color.White else Color(0xFF101016)
    when (effect) {
        GlanceWeatherEffect.ClearDay -> {
            drawRect(b.dayGlow, alpha = (0.62f + 0.08f * sin(tf / 6000f * 6.2831855f)) * k)
        }
        GlanceWeatherEffect.ClearNight -> {
            for (i in 0 until 40) {
                val tw = 0.55f + 0.45f * sin(tf * 0.0011f * s.sp[i] + s.ph[i])
                drawCircle(ink, (0.7f + 1.1f * s.sz[i]) * px, Offset(s.xs[i] * w, s.ys[i] * h * 0.85f), alpha = 0.38f * k * tw)
            }
        }
        GlanceWeatherEffect.Clouds, GlanceWeatherEffect.Thunder -> {
            val brush = if (effect == GlanceWeatherEffect.Thunder) b.stormCloud else b.cloud
            for (i in 0 until 5) {
                val r = w * (0.34f + 0.1f * s.sz[i])
                val x = frac(s.xs[i] + tf / (110_000f * s.sp[i])) * (w + 2 * r) - r
                val y = (0.12f + 0.17f * i) * h
                translate(x, y) { scale(r / 100f * 1.9f, r / 100f * 0.8f, Offset.Zero) { drawCircle(brush, 100f, Offset.Zero, alpha = 0.55f * k) } }
            }
            if (effect == GlanceWeatherEffect.Thunder && !still) {
                val f = GlanceWeather.flashAlpha(t)
                if (f > 0f) drawRect(Color(0xFFDDE6FF), alpha = f * k)
            }
        }
        GlanceWeatherEffect.Rain -> {
            val len = 16f * px
            for (i in 0 until 48) {
                val y = frac(s.ys[i] + tf * s.sp[i] * 0.00045f) * (h + len) - len
                val x = s.xs[i] * w * 1.25f - y * 0.25f
                drawLine(ink, Offset(x, y), Offset(x - len * 0.25f, y + len), strokeWidth = px, alpha = 0.22f * k * s.sz[i])
            }
        }
        GlanceWeatherEffect.Snow -> {
            for (i in 0 until 40) {
                val y = frac(s.ys[i] + tf * s.sp[i] * 0.00006f) * h
                val x = s.xs[i] * w + sin(tf * 0.0007f * s.sp[i] + s.ph[i]) * 18f * px
                drawCircle(ink, (1f + 2f * s.sz[i]) * px, Offset(x, y), alpha = 0.4f * k)
            }
        }
        GlanceWeatherEffect.Fog -> {
            for (i in 0 until 3) {
                val dx = sin(tf * 0.00005f * (1f + i * 0.3f) + s.ph[i]) * w * 0.15f
                translate(dx, h * (0.08f + 0.3f * i)) { drawRect(b.fog, Offset.Zero, Size(w, b.fogH), alpha = (0.6f + 0.2f * sin(tf * 0.0002f + s.ph[i])) * k) }
            }
        }
    }
}
