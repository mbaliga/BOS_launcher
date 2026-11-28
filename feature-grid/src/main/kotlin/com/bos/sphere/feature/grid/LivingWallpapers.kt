package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.signals.SignalState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * HOM-16, PAL-24 (wave 15): the five living wallpapers (Beam, Spire, Halo, Laser, Shaft) and the Bottom glow effect, drawn
 * procedurally in one Canvas from layered radial, linear and sweep gradients, after the owner's reference images
 * (docs/decisions/backgroundsReset.md says which image each follows and how each is rotated in portrait). Everything that
 * does not depend on the signal level (paths, shaders, paints) is built once per window size in `prepare`; a frame only moves
 * a few numbers (canvas transform, paint alpha) taken from [BackgroundCurves] at the eased level, so nothing is allocated
 * while drawing and no blur or RenderEffect is used (NFR-05). Cores are drawn with an additive blend. A slow breath of a few
 * percent keeps the picture alive; reduced motion draws one static composition at the current level.
 */

/** Film grain: a small precomputed tile of faint noise, laid over the lit wallpapers so the long gradients do not band. */
internal object GrainTile {
    const val SIZE = 96
    val bitmap: Bitmap by lazy {
        val px = IntArray(SIZE * SIZE)
        var s = 0x2F6E2B1u
        for (i in px.indices) {
            s = s * 1664525u + 1013904223u
            val n = ((s shr 16) and 0xFFu).toInt()          // 0..255
            val a = (n * 9) / 255                            // alpha 0..9 of 255: a few percent at most
            px[i] = (a shl 24) or 0x00FFFFFF
        }
        Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }
}

/** What every wallpaper shares: orientation, additive paint, the breath, and the intensity / light-theme gain. */
internal abstract class WallpaperPainter(protected val geo: RadarGeometry) : SignalPainter {
    protected val portrait = geo.portrait
    protected val dp = geo.density
    protected val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    protected val rect = RectF()

    /** Alpha gain of everything drawn: intensity, and a quiet wash on light surfaces (additive light does not exist there). */
    protected fun gain(p: SignalDrawParams) = p.intensity * (if (p.dark) 1f else 0.30f)

    /** A few percent of slow breathing, 1 = still. */
    protected fun breath(p: SignalDrawParams, periodMs: Float, phase: Float = 0f): Float =
        if (p.still) 1f else 1f + 0.03f * sin(2f * PI.toFloat() * (p.timeMs % periodMs.toLong()) / periodMs + phase)

    protected fun alpha(a: Float): Int = (a * 255f + 0.5f).toInt().coerceIn(0, 255)

    protected fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray) =
        LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)

    /** Unit radial gradient (radius 1 at the origin): drawn under a canvas scale it is an ellipse of any size. */
    protected fun unitRadial(colors: IntArray, stops: FloatArray) = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)

    protected fun shaderPaint(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.shader = shader }

    protected fun strokePaint(shader: Shader?, widthPx: Float, round: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = widthPx; if (round) strokeCap = Paint.Cap.ROUND; this.shader = shader
    }

    /** A tapered band along +x in design space (a = distance along the axis, b = across): half width [hw0] at 0 to [hw1] at [len]. */
    protected fun bandPath(len: Float, hw0: Float, hw1: Float, power: Float): Path {
        val n = 28
        return Path().apply {
            moveTo(0f, -hw0)
            for (i in 1..n) { val u = i / n.toFloat(); lineTo(len * u, -(hw0 + (hw1 - hw0) * u.pow(power))) }
            for (i in n downTo 0) { val u = i / n.toFloat(); lineTo(len * u, hw0 + (hw1 - hw0) * u.pow(power)) }
            close()
        }
    }

    protected fun argb(rgb: Int, a: Int) = (a shl 24) or (rgb and 0x00FFFFFF)

    /** Linear mix of two ARGB colours (alpha included). */
    protected fun mix(c0: Int, c1: Int, t: Float): Int {
        fun ch(shift: Int) = (((c0 ushr shift) and 255) + ((((c1 ushr shift) and 255) - ((c0 ushr shift) and 255)) * t)).toInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Colour at [u] (0..1) along a list of evenly spread colour stops. */
    protected fun along(colors: IntArray, u: Float): Int {
        val x = u.coerceIn(0f, 1f) * (colors.size - 1); val i = x.toInt().coerceAtMost(colors.size - 2)
        return mix(colors[i], colors[i + 1], x - i)
    }

    /**
     * A soft band along +x in design space, built from [slices] thin slices, each filled with a cross-section gradient (a gaussian,
     * so the edges fade instead of ending in a step): half width [hw0] at 0 growing to [hw1] at [len] by [power]; the colour of
     * a slice comes from [colors] along the band. Slices abut exactly (anti-aliasing is off, so no seam and no double alpha). [draw] only sets the paints' alpha.
     */
    protected inner class SoftBand(len: Float, hw0: Float, hw1: Float, power: Float, slices: Int, colors: IntArray) {
        private val xs = FloatArray(slices + 1) { len * it / slices }
        private val paints = Array(slices) { i ->
            val u = (i + 0.5f) / slices
            val hw = hw0 + (hw1 - hw0) * u.pow(power)
            val base = along(colors, u)
            val n = 9
            val cs = IntArray(n); val ps = FloatArray(n)
            for (k in 0 until n) {
                val x = -1f + 2f * k / (n - 1); ps[k] = k / (n - 1f)
                val a = kotlin.math.exp(-(x * x) / 0.22f)
                cs[k] = (((base ushr 24) * a).toInt().coerceIn(0, 255) shl 24) or (base and 0x00FFFFFF)
            }
            shaderPaint(LinearGradient(0f, -hw, 0f, hw, cs, ps, Shader.TileMode.CLAMP)).also { it.isAntiAlias = false }
        }
        private val hws = FloatArray(slices) { i -> hw0 + (hw1 - hw0) * ((i + 0.5f) / slices).pow(power) }
        private val overlap = 0f
        fun draw(c: android.graphics.Canvas, alphaMul: Float) {
            val a = alpha(alphaMul)
            for (i in paints.indices) { paints[i].alpha = a; c.drawRect(xs[i] - overlap, -hws[i], xs[i + 1] + overlap, hws[i], paints[i]) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Beam (reference 30)

/**
 * Beam: a tapered violet beam from an edge, thin and white-lilac at the entry, spreading into soft violet. Landscape: it
 * enters from the right edge and runs left, as in the picture. Portrait: it enters from the bottom edge and runs up. With the
 * level its reach, its width and the brightness of the tip grow, and a beaded band of sparkles along the core appears.
 */
internal object BeamView : SignalView {
    override val id = BgItem.Beam.id
    override val label = BgItem.Beam.label
    override fun prepare(geo: RadarGeometry): SignalPainter = BeamPainter(geo)

    private class BeamPainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val len = if (portrait) geo.h else geo.w
        private val across = if (portrait) geo.w else geo.h
        private val design = len * 1.25f
        private val bodyColors = intArrayOf(0xFFF1E6FF.toInt(), 0xF0C4A6F2.toInt(), 0xD88E6BD2.toInt(), 0x90593A9F.toInt(), 0x00331F73)
        private val body = SoftBand(design, across * 0.014f, across * 0.58f, 0.85f, 112, bodyColors)
        private val tipPaint = shaderPaint(unitRadial(intArrayOf(0xFFFFFFFF.toInt(), 0x99D9C4FF.toInt(), 0x00B79BFF), floatArrayOf(0f, 0.3f, 1f))).also { it.xfermode = add }
        private val corePath = bandPath(len * 0.9f, across * 0.005f, across * 0.0012f, 1f)
        private val corePaint = shaderPaint(linear(0f, 0f, len * 0.9f, 0f, intArrayOf(0xFFFFFFFF.toInt(), 0xCCEBDDFF.toInt(), 0x00EBDDFF), floatArrayOf(0f, 0.5f, 1f))).also { it.xfermode = add }
        private val beadsA = FloatArray(BEADS * 2); private val beadsB = FloatArray(BEADS * 2)
        private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2.4f * dp; color = 0xFFFFF6FF.toInt(); xfermode = add }
        private val wash = shaderPaint(unitRadial(intArrayOf(0x40421A8C, 0x1E2A0F5C, 0x00000000), floatArrayOf(0f, 0.55f, 1f)))

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p)
            val reach = BackgroundCurves.at(BackgroundCurves.BEAM_REACH, p.level)
            val wid = BackgroundCurves.at(BackgroundCurves.BEAM_WIDTH, p.level) * breath(p, 9000f)
            val bodyB = BackgroundCurves.at(BackgroundCurves.BEAM_BODY, p.level)
            val tip = BackgroundCurves.at(BackgroundCurves.BEAM_TIP, p.level)
            val sparkle = BackgroundCurves.at(BackgroundCurves.BEAM_SPARKLE, p.level)
            c.save()
            if (portrait) { c.translate(geo.w / 2f, geo.h); c.rotate(-90f) } else { c.translate(geo.w, geo.h / 2f); c.rotate(180f) }
            // the faint violet wash the beam lights up
            c.save(); c.scale(len * 0.9f * reach, across * 0.55f * wid); wash.alpha = alpha(g * bodyB); c.drawCircle(0f, 0f, 1f, wash); c.restore()
            c.save(); c.scale(reach, wid); body.draw(c, g * bodyB * 0.95f); c.restore()
            if (p.dark) {
                val r = across * (0.03f + 0.07f * tip)
                c.save(); c.scale(r * 2.6f, r); tipPaint.alpha = alpha(g * tip * 0.95f); c.drawCircle(0f, 0f, 1f, tipPaint); c.restore()
                c.save(); c.scale(reach * 0.9f + 0.1f, 0.4f + 0.6f * wid)
                corePaint.alpha = alpha(g * (0.35f + 0.65f * tip)); c.drawPath(corePath, corePaint)
                c.restore()
                if (sparkle > 0.01f) {
                    val twA = if (p.still) 0.85f else 0.6f + 0.4f * sin(p.timeMs / 700f)
                    val twB = if (p.still) 0.85f else 0.6f + 0.4f * sin(p.timeMs / 700f + 2.4f)
                    for (i in 0 until BEADS) {
                        val u = i / (BEADS - 1f)
                        val a = len * reach * (0.18f + 0.66f * u)
                        val b = (if (i % 2 == 0) -1f else 1f) * across * 0.004f * (1f - u * 0.5f) * wid
                        val arr = if (i % 2 == 0) beadsA else beadsB
                        val k = (i / 2) * 2
                        arr[k] = a; arr[k + 1] = b
                    }
                    beadPaint.alpha = alpha(g * sparkle * 0.8f * twA); c.drawPoints(beadsA, 0, BEADS, beadPaint)
                    beadPaint.alpha = alpha(g * sparkle * 0.8f * twB); c.drawPoints(beadsB, 0, BEADS, beadPaint)
                }
            }
            c.restore()
        }
    }
    private const val BEADS = 28
}

// ------------------------------------------------------------------------------------------------ Spire (reference 31)

/**
 * Spire: a blue cusp that rises from the bottom edge, centred, with a white core and a blue bloom below it (both
 * orientations: it always rises from the bottom, as in the picture). With the level the cusp rises higher and the core
 * brightens.
 */
internal object SpireView : SignalView {
    override val id = BgItem.Spire.id
    override val label = BgItem.Spire.label
    override fun prepare(geo: RadarGeometry): SignalPainter = SpirePainter(geo)

    private class SpirePainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val baseY = geo.h * 0.64f
        private val halfBase = minOf(geo.w * 0.40f, geo.h * 0.38f)
        private val cusp = Path().apply {
            moveTo(0f, -1f); cubicTo(0.03f, -0.55f, 0.12f, -0.12f, 1f, 0f); lineTo(-1f, 0f); cubicTo(-0.12f, -0.12f, -0.03f, -0.55f, 0f, -1f); close()
        }
        private val cuspPaint = shaderPaint(linear(0f, -1f, 0f, 0f, intArrayOf(0xFF86AEFF.toInt(), 0xFF6D9BFF.toInt(), 0xFF2F58F2.toInt(), 0xCC1230CC.toInt(), 0x00081A90), floatArrayOf(0f, 0.3f, 0.6f, 0.86f, 1f)))
        private val bloom = shaderPaint(unitRadial(intArrayOf(0xCC2244FF.toInt(), 0x771A30C8.toInt(), 0x330A1470, 0x00000000), floatArrayOf(0f, 0.4f, 0.75f, 1f)))
        private val core = shaderPaint(unitRadial(intArrayOf(0xFFFFFFFF.toInt(), 0xCC9DBBFF.toInt(), 0x00305CFF), floatArrayOf(0f, 0.4f, 1f))).also { it.xfermode = add }
        private val floorWash = shaderPaint(linear(0f, geo.h * 0.5f, 0f, geo.h, intArrayOf(0x00000000, 0x55081A8A), floatArrayOf(0f, 1f)))

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p)
            val h = maxOf(0f, minOf(BackgroundCurves.at(BackgroundCurves.SPIRE_HEIGHT, p.level) * geo.h * breath(p, 8000f), baseY))
            val rise = baseY / geo.h
            val coreB = BackgroundCurves.at(BackgroundCurves.SPIRE_CORE, p.level)
            val bl = BackgroundCurves.at(BackgroundCurves.SPIRE_BLOOM, p.level)
            val cx = geo.w / 2f
            floorWash.alpha = alpha(g * bl); c.drawRect(0f, geo.h * 0.5f, geo.w, geo.h, floorWash)
            c.save(); c.translate(cx, baseY + geo.h * 0.12f); c.scale(geo.w * 0.55f, geo.h * 0.20f + geo.h * 0.20f * rise)
            bloom.alpha = alpha(g * bl); c.drawCircle(0f, 0f, 1f, bloom); c.restore()
            c.save(); c.translate(cx, baseY); c.scale(halfBase * (0.8f + 0.2f * (p.level / 4f)), h)
            cuspPaint.alpha = alpha(g * (0.55f + 0.45f * coreB)); c.drawPath(cusp, cuspPaint); c.restore()
            if (p.dark) {
                c.save(); c.translate(cx, baseY - h * 0.45f); c.scale(halfBase * 0.30f, h * 0.38f)
                core.alpha = alpha(g * coreB); c.drawCircle(0f, 0f, 1f, core); c.restore()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Halo (reference 32)

/**
 * Halo: a rose ring, tilted, whose visible arc is a bright thin head with a haze trailing behind it. With the level the
 * arc sweeps from a short bright arc to a nearly closed ring and the haze brightens. The tilt and size follow the window
 * in both orientations. The arcs for the five levels are five precomputed sweep gradients; a frame fades between the two
 * nearest, so no gradient is built while drawing.
 */
internal object HaloView : SignalView {
    override val id = BgItem.Halo.id
    override val label = BgItem.Halo.label
    override fun prepare(geo: RadarGeometry): SignalPainter = HaloPainter(geo)

    private class HaloPainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val radius = if (portrait) geo.w * 0.36f else geo.h * 0.40f
        private val headDeg = 62f
        private val reachR = 2.6f
        /** Layers, wide haze to thin bright head: gaussian width (in ring radii) and strength. */
        private val sigma = floatArrayOf(0.55f, 0.32f, 0.15f, 0.035f)
        private val strength = floatArrayOf(0.70f, 0.75f, 0.85f, 1.00f)
        /** Colours along the arc, tail to head, per layer (alpha is set by the arc ramp). */
        private val tailRgb = intArrayOf(0x7A2E5E, 0x8A2E56, 0xB0405A, 0xFFB8C4)
        private val midRgb = intArrayOf(0xA82E50, 0xB83552, 0xD05468, 0xFFD0D8)
        private val headRgb = intArrayOf(0xE05A78, 0xE86A82, 0xF59AA8, 0xFFF0F2)
        /** One shader per level and layer: the ring's gaussian profile (radial) times the arc's ramp (sweep). */
        private val shaders: Array<Array<android.graphics.ComposeShader>> = Array(5) { lvl -> Array(sigma.size) { layer -> compose(BackgroundCurves.HALO_ARC[lvl], layer) } }
        private val paints = Array(sigma.size) { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
        private val ambient = shaderPaint(unitRadial(intArrayOf(0x552B1418, 0x222B1418, 0x00000000), floatArrayOf(0f, 0.6f, 1f)))
        private val headGlow = shaderPaint(unitRadial(intArrayOf(0xFFFFF0F2.toInt(), 0x88FFB0BC.toInt(), 0x00FF6078), floatArrayOf(0f, 0.3f, 1f))).also { it.xfermode = add }

        /** Ring profile: alpha exp(-((r-1)/sigma)^2) as radial stops out to [reachR] ring radii. */
        private fun ring(sig: Float): RadialGradient {
            val n = 24
            val cs = IntArray(n); val ps = FloatArray(n)
            for (k in 0 until n) {
                val r = reachR * k / (n - 1f); ps[k] = k / (n - 1f)
                val a = kotlin.math.exp(-((r - 1f) * (r - 1f)) / (sig * sig))
                cs[k] = alpha(a).shl(24) or 0x00FFFFFF
            }
            return RadialGradient(0f, 0f, reachR, cs, ps, Shader.TileMode.CLAMP)
        }

        /** Position 1.0 is the head: alpha 0 at the tail (1 - arc/360) rising to 1 at the head, rotated so the head sits at [headDeg]. */
        private fun compose(arcDeg: Float, layer: Int): android.graphics.ComposeShader {
            val tail = (1f - arcDeg / 360f).coerceIn(0.02f, 0.98f)
            val steps = 10
            val colors = IntArray(steps + 3); val stops = FloatArray(steps + 3)
            // the head is a little before the end so the arc tapers off softly instead of ending in a straight cut
            val headPos = 0.90f
            colors[0] = argb(tailRgb[layer], 0); stops[0] = 0f
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val rgb = if (u < 0.5f) mix(0xFF000000.toInt() or tailRgb[layer], 0xFF000000.toInt() or midRgb[layer], u * 2f) else mix(0xFF000000.toInt() or midRgb[layer], 0xFF000000.toInt() or headRgb[layer], (u - 0.5f) * 2f)
                colors[i + 1] = argb(rgb, alpha(u.pow(1.5f))); stops[i + 1] = tail * headPos + (headPos - tail * headPos) * u
            }
            colors[steps + 2] = argb(headRgb[layer], 0); stops[steps + 2] = 1f
            stops[steps + 1] = headPos + 0.006f
            val sweep = SweepGradient(0f, 0f, colors, stops).also { it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(headDeg) }) }
            return android.graphics.ComposeShader(ring(sigma[layer]), sweep, PorterDuff.Mode.SRC_IN)
        }

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p)
            val haze = BackgroundCurves.at(BackgroundCurves.HALO_HAZE, p.level)
            val head = BackgroundCurves.at(BackgroundCurves.HALO_HEAD, p.level)
            val lo = p.level.toInt().coerceIn(0, 3); val f = (p.level - lo).coerceIn(0f, 1f)
            val r = radius * breath(p, 10000f, 1f)
            c.save(); c.translate(geo.w / 2f, geo.h * 0.5f); c.rotate(-22f); c.scale(1f, 0.80f)
            c.save(); c.scale(r * 2.4f, r * 2.4f); ambient.alpha = alpha(g * haze); c.drawCircle(0f, 0f, 1f, ambient); c.restore()
            c.scale(r, r)
            for (layer in sigma.indices) {
                val a = strength[layer] * g * (if (layer == sigma.lastIndex) head else haze)
                val pt = paints[layer]
                pt.shader = shaders[lo][layer]; pt.alpha = alpha(a * (1f - f)); if (f < 0.999f) c.drawCircle(0f, 0f, reachR, pt)
                pt.shader = shaders[lo + 1][layer]; pt.alpha = alpha(a * f); if (f > 0.001f) c.drawCircle(0f, 0f, reachR, pt)
            }
            if (p.dark) {
                val ang = headDeg.toDouble() * PI / 180.0
                c.save(); c.translate(cos(ang).toFloat(), sin(ang).toFloat()); c.scale(0.22f, 0.22f)
                headGlow.alpha = alpha(g * head * 0.6f); c.drawCircle(0f, 0f, 1f, headGlow); c.restore()
            }
            c.restore()
        }
    }
}

// ------------------------------------------------------------------------------------------------ Laser (reference 33)

/**
 * Laser: a thin red vertical beam standing on a floor line, a hair-thin arc over it, a floor glow and a spark above. Both
 * orientations keep the beam vertical; the arc's circle is sized by the narrower side. With the level the beam grows in
 * length, the arc draws further around, and the floor glow brightens.
 */
internal object LaserView : SignalView {
    override val id = BgItem.Laser.id
    override val label = BgItem.Laser.label
    override fun prepare(geo: RadarGeometry): SignalPainter = LaserPainter(geo)

    private class LaserPainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val r = if (portrait) geo.w * 0.40f else geo.h * 0.34f
        private val cx = geo.w / 2f
        private val apexY = geo.h * (if (portrait) 0.26f else 0.20f)
        private val cy = apexY + r
        private val floorY = cy + 0.47f * r
        private val fullRun = 118f
        private val ambient = shaderPaint(unitRadial(intArrayOf(0x66B02000, 0x33801400, 0x00000000), floatArrayOf(0f, 0.5f, 1f)))
        private val floorGlow = shaderPaint(unitRadial(intArrayOf(0xCCFF5A28.toInt(), 0x55D03010, 0x00000000), floatArrayOf(0f, 0.4f, 1f)))
        private val floorLine = shaderPaint(linear(cx - r * 0.7f, 0f, cx + r * 0.7f, 0f, intArrayOf(0x00FF8050, 0xFFFFC8A8.toInt(), 0x00FF8050), floatArrayOf(0f, 0.5f, 1f))).also { it.xfermode = add }
        private val beamPaints = arrayOf(strokePaint(null, 14f * dp, false), strokePaint(null, 6f * dp, false), strokePaint(null, 2.4f * dp, false))
        private val beamRgb = intArrayOf(0xE02A10, 0xFF4A24, 0xFFC09A)
        private val beamA = floatArrayOf(0.13f, 0.30f, 0.95f)
        private val arcPaints = arrayOf(strokePaint(null, 7f * dp), strokePaint(null, 3f * dp), strokePaint(null, 1.3f * dp))
        private val arcRgb = intArrayOf(0xD02A10, 0xFF4A24, 0xFF7C5C)
        private val arcA = floatArrayOf(0.09f, 0.18f, 0.55f)
        private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF7A5A.toInt(); xfermode = add }
        private val oval = RectF(cx - r, cy - r, cx + r, cy + r)

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p) * breath(p, 7000f)
            val beam = BackgroundCurves.at(BackgroundCurves.LASER_BEAM, p.level)
            val arc = BackgroundCurves.at(BackgroundCurves.LASER_ARC, p.level)
            val floor = BackgroundCurves.at(BackgroundCurves.LASER_FLOOR, p.level)
            c.save(); c.translate(cx, floorY - r * 0.6f); c.scale(r * 1.7f, r * 1.4f); ambient.alpha = alpha(g * (0.35f + 0.65f * floor)); c.drawCircle(0f, 0f, 1f, ambient); c.restore()
            c.save(); c.translate(cx, floorY); c.scale(r * 1.15f, r * 0.14f); floorGlow.alpha = alpha(g * floor); c.drawCircle(0f, 0f, 1f, floorGlow); c.restore()
            if (p.dark) {
                c.save(); c.translate(0f, floorY); floorLine.alpha = alpha(g * floor); c.drawRect(cx - r * 0.7f, -1.6f * dp, cx + r * 0.7f, 1.6f * dp, floorLine); c.restore()
            }
            val top = floorY - (floorY - apexY) * beam
            for (i in 0 until 3) {
                beamPaints[i].color = argb(beamRgb[i], alpha(beamA[i] * g)); c.drawLine(cx, floorY, cx, top, beamPaints[i])
            }
            val run = fullRun * arc
            for (k in 0 until 4) {
                val rr = run * (1f - 0.22f * k)
                for (i in 0 until 3) {
                    arcPaints[i].color = argb(arcRgb[i], alpha(arcA[i] * g * 0.55f))
                    c.drawArc(oval, -90f - rr, 2f * rr, false, arcPaints[i])
                }
            }
            if (p.dark && beam > 0.3f) { spark.alpha = alpha(g * beam); c.drawCircle(cx, top - r * 0.06f, 1.6f * dp, spark) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Shaft (reference 34)

/**
 * Shaft: a soft violet diagonal shaft of light falling across the window from the top edge to the lower right, brightest
 * near the top. Landscape follows the picture (about 60 degrees); portrait runs it steeper from the top left to the bottom
 * right. With the level the shaft widens and brightens.
 */
internal object ShaftView : SignalView {
    override val id = BgItem.Shaft.id
    override val label = BgItem.Shaft.label
    override fun prepare(geo: RadarGeometry): SignalPainter = ShaftPainter(geo)

    private class ShaftPainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val x0 = geo.w * (if (portrait) 0.28f else 0.34f); private val y0 = -geo.h * 0.04f
        private val x1 = geo.w * (if (portrait) 0.80f else 0.68f); private val y1 = geo.h * 1.04f
        private val len = hypot(x1 - x0, y1 - y0)
        private val ang = (atan2(y1 - y0, x1 - x0) * 180.0 / PI).toFloat()
        private val w0 = minOf(geo.w, geo.h) * (if (portrait) 0.30f else 0.26f)
        private val colors = intArrayOf(0x007C5AC8, 0xE07C5AC8.toInt(), 0xF06B3FC0.toInt(), 0xB04A2A9A.toInt(), 0x602E1B6B, 0x00201048)
        private val band = SoftBand(len, w0 * 1.0f, w0 * 1.7f, 1f, 96, colors)
        private val wash = shaderPaint(linear(0f, 0f, geo.w, geo.h, intArrayOf(0x330E0A24, 0x00000000, 0x00000000), floatArrayOf(0f, 0.5f, 1f)))
        private val hot = shaderPaint(unitRadial(intArrayOf(0x887C5AC8.toInt(), 0x336B3FC0, 0x00000000), floatArrayOf(0f, 0.5f, 1f))).also { it.xfermode = add }

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p)
            val w = BackgroundCurves.at(BackgroundCurves.SHAFT_WIDTH, p.level) * breath(p, 11000f)
            val b = BackgroundCurves.at(BackgroundCurves.SHAFT_BRIGHT, p.level)
            wash.alpha = alpha(g * b); c.drawRect(0f, 0f, geo.w, geo.h, wash)
            c.save(); c.translate(x0, y0); c.rotate(ang); c.scale(1f, w)
            band.draw(c, g * b)
            if (p.dark) { c.save(); c.translate(len * 0.16f, 0f); c.scale(len * 0.22f, w0 * 0.9f); hot.alpha = alpha(g * b * 0.5f); c.drawCircle(0f, 0f, 1f, hot); c.restore() }
            c.restore()
        }
    }
}

// ------------------------------------------------------------------------------------------------ Bottom glow (references 27 to 29)

/**
 * Bottom glow: a bloom rising from a thin bright line near the bottom edge, white-hot at the line and blue above it, over a
 * deep blue wash; the line spans the middle half of the window in both orientations, as in the pictures. With the level the
 * bloom rises higher and brightens (level 1 is the quiet line of the second picture, level 4 the full bloom of the first).
 */
internal object BottomGlowView : SignalView {
    override val id = BgItem.BottomGlow.id
    override val label = BgItem.BottomGlow.label
    override fun prepare(geo: RadarGeometry): SignalPainter = BottomGlowPainter(geo)

    private class BottomGlowPainter(geo: RadarGeometry) : WallpaperPainter(geo) {
        private val baseY = geo.h * 0.935f
        private val half = geo.w * 0.25f
        private val cx = geo.w / 2f
        private val washPaint = shaderPaint(linear(0f, 0f, 0f, baseY, intArrayOf(0xFF090A22.toInt(), 0xFF141654.toInt(), 0xFF2024A0.toInt()), floatArrayOf(0f, 0.6f, 1f)))
        private val bloomBlue = shaderPaint(unitRadial(intArrayOf(0xFF8C98FF.toInt(), 0xAA4A52F0.toInt(), 0x552A30C8, 0x00000000), floatArrayOf(0f, 0.3f, 0.65f, 1f)))
        private val bloomWhite = shaderPaint(unitRadial(intArrayOf(0xFFFFFFFF.toInt(), 0xBBDCE0FF.toInt(), 0x00A0A8FF), floatArrayOf(0f, 0.4f, 1f))).also { it.xfermode = add }
        private val line = shaderPaint(linear(cx - half, 0f, cx + half, 0f, intArrayOf(0x006070FF, 0xFFB4BCFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFB4BCFF.toInt(), 0x006070FF), floatArrayOf(0f, 0.15f, 0.5f, 0.85f, 1f))).also { it.xfermode = add }
        private val shelf = Paint().apply { color = 0xFF04051A.toInt() }

        override fun draw(scope: DrawScope, signal: SignalState, p: SignalDrawParams) {
            val c = scope.drawContext.canvas.nativeCanvas
            val g = gain(p)
            val h = BackgroundCurves.at(BackgroundCurves.BOTTOM_HEIGHT, p.level) * geo.h * breath(p, 8000f)
            val b = BackgroundCurves.at(BackgroundCurves.BOTTOM_BRIGHT, p.level)
            washPaint.alpha = alpha(g * (0.10f + 0.50f * b)); c.drawRect(0f, 0f, geo.w, baseY, washPaint)
            c.save(); c.translate(cx, baseY); c.scale(half * 1.25f, h)
            bloomBlue.alpha = alpha(g * b); c.drawCircle(0f, 0f, 1f, bloomBlue); c.restore()
            if (p.dark) {
                c.save(); c.translate(cx, baseY); c.scale(half * 0.95f, h * 0.55f)
                bloomWhite.alpha = alpha(g * b * b); c.drawCircle(0f, 0f, 1f, bloomWhite); c.restore()
                c.save(); c.translate(0f, baseY); line.alpha = alpha(g * (0.35f + 0.65f * b)); c.drawRect(cx - half, -1.2f * dp, cx + half, 1.2f * dp, line); c.restore()
            }
            // below the line the window drops back to near black, as in the pictures
            if (p.dark) { shelf.alpha = alpha(0.92f * minOf(1f, g)); c.drawRect(cx - half, baseY + 1.2f * dp, cx + half, geo.h, shelf) }
        }
    }
}

/** The grain over the lit wallpapers, one tile shader, drawn once per frame. */
internal class GrainPainter(private val w: Float, private val h: Float) {
    private val paint = Paint().apply { shader = BitmapShader(GrainTile.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    fun draw(canvas: android.graphics.Canvas, amount: Float) {
        if (amount <= 0.01f) return
        paint.alpha = (amount * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
