package com.bos.sphere.feature.grid

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import com.bos.sphere.core.signals.SignalState

/**
 * PAL-24: pluggable ways of showing signal on the Radar background. A [SignalView] is stateless and cheap to hold; it
 * prepares a [SignalPainter] once per window size (paths, paints, buffers), and the painter draws one frame from a
 * [SignalState] and the shared clock without allocating. More views are added by listing them in [SignalViews.views].
 */
internal class RadarGeometry(val w: Float, val h: Float, val density: Float) {
    val portrait: Boolean get() = h > w
}

/** Per-frame inputs, one mutable instance reused every frame. */
internal class SignalDrawParams {
    var timeMs: Long = 0L
    /** Reduced motion: draw one static composition. */
    var still: Boolean = false
    var dark: Boolean = true
    /** 0..1 strength of everything drawn (Subtle < Normal). */
    var intensity: Float = 1f
    /** 0..1: how much the comic lines currently cover the screen (glow lines hide as it rises). */
    var comicShown: Float = 0f
}

internal interface SignalPainter {
    fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams)
}

internal interface SignalView {
    val id: String
    /** English label, translated by the UI. */
    val label: String
    fun prepare(geo: RadarGeometry): SignalPainter
}

internal object SignalViews {
    const val OFF = "off"
    const val GLOW = "glow"
    const val DEFAULT = GLOW

    /** The views, in the order Ground Control lists them. The owner will add more ways to view signal here. */
    val views: List<SignalView> = listOf(GlowLinesView)

    class Option(val id: String, val label: String)

    val options: List<Option> = listOf(Option(OFF, "Off")) + views.map { Option(it.id, it.label) }

    /** The view for a stored id; null = Off. An unknown id (a view removed later) falls back to the default view. */
    fun forId(id: String, registry: List<SignalView> = views): SignalView? = if (id == OFF) null else registry.firstOrNull { it.id == id } ?: registry.firstOrNull { it.id == DEFAULT }
}

/** "Glow lines": the owner's eight bracket lines with blue pulses whose count, speed and brightness encode the signal level. */
internal object GlowLinesView : SignalView {
    override val id = SignalViews.GLOW
    override val label = "Glow lines"
    private const val BLUE = 0xFF078EFB.toInt()

    override fun prepare(geo: RadarGeometry): SignalPainter = GlowPainter(geo)

    private class GlowPainter(val geo: RadarGeometry) : SignalPainter {
        /** The resting shapes as Compose paths, parsed once with the platform path parser. */
        private val shapes: List<Path> = GlowLinesSvg.PATHS.map { PathParser().parsePathString(it).toPath() }
        private val matrix = Matrix().also { m ->
            val sxL = geo.w / GlowLinesSvg.ARTBOARD_W; val syL = geo.h / GlowLinesSvg.ARTBOARD_H
            if (geo.portrait) {
                // Transpose: artboard x runs down the window, artboard y across it.
                m.values[Matrix.ScaleX] = 0f; m.values[Matrix.ScaleY] = 0f
                m.values[Matrix.SkewX] = geo.w / GlowLinesSvg.ARTBOARD_H; m.values[Matrix.SkewY] = geo.h / GlowLinesSvg.ARTBOARD_W
            } else { m.values[Matrix.ScaleX] = sxL; m.values[Matrix.ScaleY] = syL }
        }
        private fun restBrush(rgb: Int): Brush {
            val cx = GlowLinesSvg.ARTBOARD_W / 2f; val cy = GlowLinesSvg.ARTBOARD_H / 2f
            val r = 134.5f * 1.3f
            val clear = rgb and 0x00FFFFFF
            val shader = RadialGradient(cx, cy, r, intArrayOf(clear, clear, rgb or (0xFF shl 24)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(1f, 82f / 134.5f, cx, cy) })
            return ShaderBrush(shader)
        }
        private val restDark = restBrush(0x383838)
        private val restLight = restBrush(0xCFCFD6)
        private val layers = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND } }
        private val layerWidthDp = floatArrayOf(11f, 5f, 2f)
        private val layerLength = floatArrayOf(1f, 0.7f, 0.4f)
        private val layerAlpha = floatArrayOf(0.09f, 0.15f, 0.25f)
        private val buf = FloatArray(GlowLinesMath.CENTRE_LINES.maxOf { it.size } * 4)
        private val tmp = FloatArray(2)
        /** One reused path the pulse slices are traced into (rewound each use): overlapping segment caps would pile up alpha. */
        private val trace = android.graphics.Path()
        private val glow = Fade()
        private var lastLevel = 1

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val level = signal.level
            if (level > 0) lastLevel = level
            val g = glow.step(if (level > 0) 1f else 0f, p.timeMs, 400, p.still)
            val rest = (1f - p.comicShown).coerceIn(0f, 1f)
            if (rest > 0.01f) {
                scope.withTransform({ transform(matrix) }) {
                    shapes.forEach { drawPath(it, if (p.dark) restDark else restLight, alpha = rest * p.intensity) }
                }
            }
            val params = GlowLinesMath.params(lastLevel) ?: return
            if (g <= 0.01f) return
            val canvas = scope.drawContext.canvas.nativeCanvas
            val bluePart = if (p.dark) 1f else 0.7f
            for (rank in 0 until params.lines) {
                val line = GlowLinesMath.PRIORITY[rank]
                val poly = GlowLinesMath.CENTRE_LINES[line]
                for (k in 0 until params.pulses) {
                    GlowLinesMath.pulse(lastLevel, line, k, p.timeMs, p.still, tmp)
                    val centre = tmp[0]
                    val idx = (centre * (poly.size - 1)).toInt().coerceIn(0, poly.size - 1)
                    val a = params.brightness * tmp[1] * GlowLinesMath.calm(poly.nx[idx], poly.ny[idx]) * g * rest * p.intensity * bluePart
                    if (a <= 0.01f) continue
                    for (j in 0 until 3) {
                        val half = params.length * layerLength[j] / 2f
                        val n = GlowLinesMath.slice(poly, centre - half, centre + half, geo.w, geo.h, geo.portrait, buf)
                        if (n == 0) continue
                        val paint = layers[j]
                        paint.strokeWidth = layerWidthDp[j] * geo.density
                        paint.color = (BLUE and 0x00FFFFFF) or ((a * layerAlpha[j] * 255f).toInt().coerceIn(0, 255) shl 24)
                        trace.rewind(); trace.moveTo(buf[0], buf[1])
                        var q = 0
                        while (q < n) { trace.lineTo(buf[q + 2], buf[q + 3]); q += 4 }
                        canvas.drawPath(trace, paint)
                    }
                }
            }
        }
    }
}
