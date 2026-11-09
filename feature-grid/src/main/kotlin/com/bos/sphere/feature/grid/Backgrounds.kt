package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.provider.Settings
import com.bos.sphere.core.data.BackgroundKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Public preset catalogue
// ---------------------------------------------------------------------------------------------

data class BackgroundPreset(val id: String, val label: String)

/**
 * Gradient stops (ARGB, 2-4 entries) and the CSS-style angle: 0 = bottom to top, 90 = left to right, 180 = top to bottom.
 * With [radial] the first colour sits at ([cx], [cy]) (fractions of the width / height, may lie outside 0..1) and the
 * last at the farthest corner; [angleDeg] is then ignored.
 */
data class GradientSpec(
    val colors: List<Long>,
    val angleDeg: Float,
    val radial: Boolean = false,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
)

object BackgroundPresets {
    private val solidMap: Map<String, Pair<String, Long>> = linkedMapOf(
        "ink" to ("Ink" to 0xFF0B0C10L),
        "graphite" to ("Graphite" to 0xFF1E2024L),
        "midnight" to ("Midnight" to 0xFF0B1226L),
        "navy" to ("Navy" to 0xFF14244BL),
        "ocean" to ("Deep sea" to 0xFF0C3340L),
        "forest" to ("Forest" to 0xFF0F2A1EL),
        "moss" to ("Moss" to 0xFF3B4A2AL),
        "wine" to ("Wine" to 0xFF3A0F1EL),
        "plum" to ("Plum" to 0xFF2E1740L),
        "rust" to ("Rust" to 0xFF5A2B18L),
        "slate" to ("Slate" to 0xFF2B3340L),
        "steel" to ("Steel" to 0xFF5B6674L),
        "sand" to ("Sand" to 0xFFD8C9A8L),
        "cloud" to ("Cloud" to 0xFFE6E9F0L),
        "obsidian" to ("Obsidian" to 0xFF050506L),
        "charcoal" to ("Charcoal" to 0xFF26282DL),
        "indigo" to ("Indigo" to 0xFF1C1B4AL),
        "cobalt" to ("Cobalt" to 0xFF1B3A8CL),
        "teal" to ("Teal" to 0xFF0B4A4CL),
        "olive" to ("Olive" to 0xFF3A3F1EL),
        "burgundy" to ("Burgundy" to 0xFF4A0E1CL),
        "copper" to ("Copper" to 0xFF7A4326L),
        "mist" to ("Mist" to 0xFFB9C2CCL),
        "ivory" to ("Ivory" to 0xFFF1EBDDL),
    )

    private val gradientMap: Map<String, Pair<String, GradientSpec>> = linkedMapOf(
        "dusk" to ("Dusk" to GradientSpec(listOf(0xFF1B1B3AL, 0xFF693668L, 0xFFC76B5BL), 160f)),
        "ocean" to ("Ocean" to GradientSpec(listOf(0xFF03162EL, 0xFF0A4A6EL, 0xFF1D8A9AL), 165f)),
        "ember" to ("Ember" to GradientSpec(listOf(0xFF1A0A0AL, 0xFF6B1D12L, 0xFFD2561EL), 150f)),
        "aurora" to ("Aurora" to GradientSpec(listOf(0xFF0A1428L, 0xFF0E5A58L, 0xFF5B3A9AL), 200f)),
        "mint" to ("Mint" to GradientSpec(listOf(0xFF0E2A2AL, 0xFF2E7D6EL, 0xFFB7E4C7L), 155f)),
        "rose" to ("Rose" to GradientSpec(listOf(0xFF2A0F20L, 0xFFA23B64L, 0xFFF0A6B4L), 160f)),
        "steel" to ("Steel" to GradientSpec(listOf(0xFF14181FL, 0xFF3A4452L, 0xFF8793A3L), 170f)),
        "nebula" to ("Nebula" to GradientSpec(listOf(0xFF0D0630L, 0xFF4A1B7AL, 0xFF1E5AA8L), 135f)),
        "sunrise" to ("Sunrise" to GradientSpec(listOf(0xFFFFB45AL, 0xFFE8566AL, 0xFF5B2A6EL, 0xFF140E2EL), 0f, radial = true, cx = 0.5f, cy = 1.05f)),
        "lagoon" to ("Lagoon" to GradientSpec(listOf(0xFF042A3AL, 0xFF0E7C86L, 0xFF7FDCC8L), 170f)),
        "peach" to ("Peach" to GradientSpec(listOf(0xFF3B1F2BL, 0xFFE0707AL, 0xFFFFC8A2L), 160f)),
        "ultraviolet" to ("Ultraviolet" to GradientSpec(listOf(0xFF8E3BFFL, 0xFF3A1A8CL, 0xFF0A0620L), 0f, radial = true, cx = 0.5f, cy = 0f)),
        "graphite-fade" to ("Graphite fade" to GradientSpec(listOf(0xFF2E3138L, 0xFF17181CL, 0xFF08090BL), 180f)),
        "cherry" to ("Cherry" to GradientSpec(listOf(0xFF1C0509L, 0xFF7A0F2AL, 0xFFD6335BL), 155f)),
        "forest-mist" to ("Forest mist" to GradientSpec(listOf(0xFF0B1C16L, 0xFF1F4A3BL, 0xFF8DB8A3L), 195f)),
        "glacier" to ("Glacier" to GradientSpec(listOf(0xFFE3F4FFL, 0xFF7FB6DEL, 0xFF1C4A78L, 0xFF081A33L), 0f, radial = true, cx = 0.3f, cy = 0.15f)),
        "dune" to ("Dune" to GradientSpec(listOf(0xFF2A1D14L, 0xFFA8764AL, 0xFFEBCB96L), 160f)),
        "midnight-teal" to ("Midnight teal" to GradientSpec(listOf(0xFF050B1AL, 0xFF0B3C4CL, 0xFF14877FL), 175f)),
        "candy" to ("Candy" to GradientSpec(listOf(0xFFFF8FB8L, 0xFFB78CFFL, 0xFF6FD3FFL), 140f)),
        "ember-night" to ("Ember night" to GradientSpec(listOf(0xFFFF7A2EL, 0xFF9C2A14L, 0xFF2A0C10L, 0xFF0A0608L), 0f, radial = true, cx = 0.5f, cy = 1f)),
    )

    private val textureLabels = linkedMapOf(
        "grain" to "Grain", "grid" to "Grid", "dots" to "Dots", "linen" to "Linen",
        "carbon" to "Carbon", "paper" to "Paper", "hex" to "Hex",
        "noise" to "Cloudy noise", "stripes" to "Stripes", "diagonal" to "Diagonal",
        "wavelines" to "Wave lines", "topo" to "Topo", "circuit" to "Circuit",
        "bricks" to "Bricks", "scales" to "Scales",
    )
    private val liveLabels = linkedMapOf(
        "aurora" to "Aurora", "starfield" to "Starfield", "waves" to "Waves",
        "fireflies" to "Fireflies", "nebula" to "Nebula", "rain" to "Rain",
        "ribbon" to "Ribbon", "bokeh" to "Bokeh",
        "lava" to "Lava", "snow" to "Snow", "matrix" to "Matrix", "orbits" to "Orbits", "plasma" to "Plasma",
    )

    val solids: List<BackgroundPreset> = solidMap.map { BackgroundPreset(it.key, it.value.first) }
    val gradients: List<BackgroundPreset> = gradientMap.map { BackgroundPreset(it.key, it.value.first) }
    val textures: List<BackgroundPreset> = textureLabels.map { BackgroundPreset(it.key, it.value) }
    val lives: List<BackgroundPreset> = liveLabels.map { BackgroundPreset(it.key, it.value) }

    /** Picture presets; the user's own photo is handled by the host through [PictureSource.File]. */
    val pictures: List<BackgroundPreset> = listOf(BackgroundPreset("horizon", "Horizon"))

    /** Preset ids of [kind] in display order (empty for Black / System). */
    fun allIds(kind: BackgroundKind): List<String> = of(kind).map { it.id }

    fun of(kind: BackgroundKind): List<BackgroundPreset> = when (kind) {
        BackgroundKind.Solid -> solids
        BackgroundKind.Gradient -> gradients
        BackgroundKind.Texture -> textures
        BackgroundKind.Live -> lives
        BackgroundKind.Picture -> pictures
        BackgroundKind.Black, BackgroundKind.System -> emptyList()
    }

    fun solidColor(id: String): Long? = solidMap[id]?.second
    fun gradientStops(id: String): GradientSpec? = gradientMap[id]?.second
    fun default(kind: BackgroundKind): String = of(kind).firstOrNull()?.id ?: ""
}

// ---------------------------------------------------------------------------------------------
// Pure helpers (JVM-testable, no Android types)
// ---------------------------------------------------------------------------------------------

internal object BackgroundMath {
    const val CUSTOM = "custom"

    /**
     * Start and end points (x0, y0, x1, y1) of a CSS-style linear gradient of [angleDeg] across a
     * [w] x [h] box: 0 = bottom to top, 90 = left to right, 180 = top to bottom, 270 = right to left.
     * The line passes through the centre and its length makes the corners hit exactly 0 and 1.
     */
    fun gradientEndpoints(angleDeg: Float, w: Float, h: Float, out: FloatArray = FloatArray(4)): FloatArray {
        val a = angleDeg * (PI / 180.0)
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        out[0] = cx - dx * half; out[1] = cy - dy * half
        out[2] = cx + dx * half; out[3] = cy + dy * half
        return out
    }

    /** Radius of a radial gradient centred at ([cx], [cy]) fractions of [w] x [h] that just reaches the farthest corner. */
    fun radialRadius(cx: Float, cy: Float, w: Float, h: Float): Float {
        val dx = max(abs(cx * w), abs(w - cx * w))
        val dy = max(abs(cy * h), abs(h - cy * h))
        return hypot(dx, dy)
    }

    /** Number of falling columns of the matrix scene for a canvas [wPx] wide. */
    fun matrixColumns(wPx: Float, density: Float): Int {
        val d = if (density > 0f) density else 1f
        return (wPx / (14f * d)).toInt().coerceIn(10, 56)
    }

    /** Deterministic hash to 0..1 (stateless). */
    fun hash01(i: Int, seed: Int): Float {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777216f
    }

    /** Small seeded xorshift32 generator; the same seed always yields the same sequence. */
    class Prng(seed: Int) {
        private var s: Int = if (seed == 0) 0x2545F491 else seed
        fun nextInt(): Int {
            var x = s
            x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
            s = x
            return x
        }
        /** 0 (inclusive) .. 1 (exclusive). */
        fun nextFloat(): Float = (nextInt() ushr 8) / 16777216f
    }

    /** Fills [xs] / [ys] (0..1) and [zs] (depth, [zMin]..1) for [n] particles from a fixed [seed]. */
    fun seedParticles(n: Int, seed: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray, zMin: Float = 0.25f) {
        val r = Prng(seed)
        for (i in 0 until n) {
            xs[i] = r.nextFloat(); ys[i] = r.nextFloat(); zs[i] = zMin + (1f - zMin) * r.nextFloat()
        }
    }

    /** Particle count for a canvas of [wPx] x [hPx] at [density], one per [dp2PerParticle] dp^2, clamped. */
    fun particleCount(wPx: Float, hPx: Float, density: Float, dp2PerParticle: Float, min: Int, max: Int): Int {
        val d = if (density > 0f) density else 1f
        return ((wPx / d) * (hPx / d) / dp2PerParticle).toInt().coerceIn(min, max)
    }

    fun wrap01(x: Float): Float = x - floor(x)

    fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** max - min channel, 0..1: how colourful [argb] is. */
    fun chroma(argb: Int): Float {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (max(r, max(g, b)) - min(r, min(g, b))) / 255f
    }

    /** Hue 0..360 of [argb], or -1 when it is (nearly) grey. */
    fun hueOf(argb: Int, minChroma: Float = 0.12f): Float {
        if (chroma(argb) < minChroma) return -1f
        val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0f) h + 360f else h
    }

    fun hsvToArgb(hDeg: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val h = ((hDeg % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - abs(h % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun q(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (q(r) shl 16) or (q(g) shl 8) or q(b)
    }

    /** Base hues (degrees) of each live scene; the first one anchors the tint shift. */
    fun sceneHues(scene: String): FloatArray = when (scene) {
        "aurora" -> floatArrayOf(150f, 190f, 275f, 320f)
        "starfield" -> floatArrayOf(215f, 40f)
        "waves" -> floatArrayOf(205f, 225f, 185f, 245f, 200f)
        "fireflies" -> floatArrayOf(62f, 85f)
        "nebula" -> floatArrayOf(265f, 305f, 215f, 335f, 245f)
        "rain" -> floatArrayOf(210f, 195f)
        "lava" -> floatArrayOf(14f, 345f, 32f, 355f, 5f)
        "snow" -> floatArrayOf(210f, 195f)
        "matrix" -> floatArrayOf(140f, 160f)
        "orbits" -> floatArrayOf(200f, 280f, 160f, 330f, 45f)
        "plasma" -> floatArrayOf(320f, 200f, 265f, 165f, 20f)
        else -> floatArrayOf(150f, 190f, 275f, 320f)
    }

    /** Opaque scene palette. A colourful [tint] rotates every hue so the first one lands on the tint's hue. */
    fun livePalette(scene: String, tint: Int, s: Float, v: Float): IntArray {
        val hues = sceneHues(scene)
        val th = hueOf(tint)
        val shift = if (th < 0f) 0f else th - hues[0]
        return IntArray(hues.size) { hsvToArgb(hues[it] + shift, s, v) }
    }

    /** Whether [tint] is colourful enough to recolour a live scene. */
    fun hasTint(tint: Int): Boolean = hueOf(tint) >= 0f
}

// ---------------------------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------------------------

private const val STATIC_T = 17f
private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * Draws the chosen background filling its bounds. Black is opaque black; System draws nothing so
 * the wallpaper shows through. With [animate] false a Live scene draws one static frame and no
 * infinite transition is started (thumbnails). A "custom" [preset] uses [colorA] (and [colorB]).
 */
@Composable
fun BackgroundLayer(
    kind: BackgroundKind,
    preset: String,
    colorA: Long,
    colorB: Long,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    touch: State<TouchPoint?>? = null,
    /** For [BackgroundKind.Picture] with preset "photo": the app-private image file. */
    picturePath: String = "",
) {
    when (kind) {
        BackgroundKind.Picture -> PictureBackground(
            if (preset == "photo" && picturePath.isNotEmpty()) PictureSource.File(picturePath) else PictureSource.Horizon,
            dim = 0f, modifier = modifier,
        )
        BackgroundKind.System -> Box(modifier)
        BackgroundKind.Black -> Canvas(modifier.fillMaxSize()) { drawRect(Color.Black) }
        BackgroundKind.Solid -> {
            val argb = if (preset == BackgroundMath.CUSTOM) colorA else BackgroundPresets.solidColor(preset)
                ?: BackgroundPresets.solidColor(BackgroundPresets.default(kind)) ?: colorA
            val c = Color(argb.toInt())
            Canvas(modifier.fillMaxSize().clipToBounds()) { drawRect(c) }
        }
        BackgroundKind.Gradient -> {
            val spec = if (preset == BackgroundMath.CUSTOM) GradientSpec(listOf(colorA, colorB), 160f)
            else BackgroundPresets.gradientStops(preset)
                ?: BackgroundPresets.gradientStops(BackgroundPresets.default(kind))!!
            val colors = remember(spec) { spec.colors.map { Color(it.toInt()) } }
            Canvas(modifier.fillMaxSize().clipToBounds()) {
                if (spec.radial) {
                    drawRect(
                        Brush.radialGradient(
                            colors,
                            center = Offset(spec.cx * size.width, spec.cy * size.height),
                            radius = BackgroundMath.radialRadius(spec.cx, spec.cy, size.width, size.height).coerceAtLeast(1f),
                        ),
                    )
                } else {
                    val e = BackgroundMath.gradientEndpoints(spec.angleDeg, size.width, size.height)
                    drawRect(Brush.linearGradient(colors, Offset(e[0], e[1]), Offset(e[2], e[3])))
                }
            }
        }
        BackgroundKind.Texture -> TextureBackground(preset, colorA, modifier)
        BackgroundKind.Live -> LiveBackground(preset, colorA, modifier, animate, touch)
    }
}

// ---------------------------------------------------------------------------------------------
// Textures: one small tile rendered once, repeated by a shader
// ---------------------------------------------------------------------------------------------

@Composable
private fun TextureBackground(preset: String, colorA: Long, modifier: Modifier) {
    val density = LocalDensity.current.density
    val custom = preset == BackgroundMath.CUSTOM
    val id = if (BackgroundPresets.textures.any { it.id == preset }) preset
    else if (custom) "grain" else BackgroundPresets.default(BackgroundKind.Texture)
    val brush = remember(id, custom, colorA, density) {
        val tile = TextureTiles.render(id, colorA.toInt(), custom, density)
        ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        drawRect(brush)
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = hypot(size.width, size.height) / 2f,
            ),
        )
    }
}

/** A *mutable* bitmap from pixels (`createBitmap(int[], …)` returns an immutable one, which a Canvas then refuses). */
private fun mutableBitmap(px: IntArray, size: Int): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, size, 0, 0, size, size) }

private object TextureTiles {
    private const val DARK = 0xFF0C0D12.toInt()

    fun render(id: String, tint: Int, custom: Boolean, density: Float): Bitmap {
        val base0 = when (id) {
            "paper" -> 0xFF17160F.toInt()
            "linen" -> 0xFF14161B.toInt()
            "carbon" -> 0xFF0A0A0C.toInt()
            else -> DARK
        }
        val base = BackgroundMath.lerpArgb(base0, tint or (0xFF shl 24), if (custom) 0.55f else 0.08f)
        return when (id) {
            "grain" -> noise(base, 256, 0.12f, 11)
            "paper" -> noise(base, 256, 0.05f, 29).also { fibres(it, density) }
            "grid" -> grid(base, density)
            "dots" -> dots(base, density)
            "linen" -> linen(base, density)
            "carbon" -> carbon(base, density)
            "hex" -> hex(base, density)
            "noise" -> cloudy(base)
            "stripes" -> stripes(base, density)
            "diagonal" -> diagonal(base, density)
            "wavelines" -> waveLines(base, density)
            "topo" -> topo(base, density)
            "circuit" -> circuit(base, density)
            "bricks" -> bricks(base, density)
            "scales" -> scales(base, density)
            else -> noise(base, 256, 0.12f, 11)
        }
    }

    private fun noise(base: Int, size: Int, amp: Float, seed: Int): Bitmap {
        val px = IntArray(size * size)
        val br = (base shr 16) and 0xFF; val bg = (base shr 8) and 0xFF; val bb = base and 0xFF
        for (i in px.indices) {
            val d = ((BackgroundMath.hash01(i, seed) - 0.5f) * amp * 255f).toInt()
            px[i] = (0xFF shl 24) or ((br + d).coerceIn(0, 255) shl 16) or
                ((bg + d).coerceIn(0, 255) shl 8) or (bb + d).coerceIn(0, 255)
        }
        return mutableBitmap(px, size)
    }

    private fun fibres(bmp: Bitmap, density: Float) {
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
        val s = bmp.width.toFloat()
        for (i in 0 until 90) {
            val x = BackgroundMath.hash01(i, 3) * s; val y = BackgroundMath.hash01(i, 5) * s
            val a = BackgroundMath.hash01(i, 7) * TWO_PI
            val l = (3f + 9f * BackgroundMath.hash01(i, 9)) * max(1f, density / 2f)
            p.color = if (i % 2 == 0) 0x0CFFFFFF else 0x14000000
            c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, p)
        }
    }

    private fun tile(w: Int, h: Int, base: Int, draw: (android.graphics.Canvas, Paint) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(base)
        draw(c, Paint(Paint.ANTI_ALIAS_FLAG))
        return bmp
    }

    private fun grid(base: Int, d: Float): Bitmap {
        val cell = 24f * d
        val n = 4
        val sz = (cell * n).toInt()
        val line = max(1f, d * 0.75f)
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.FILL
            for (i in 0 until n) {
                p.color = if (i == 0) 0x1CFFFFFF else 0x0DFFFFFF
                val o = i * cell
                c.drawRect(o, 0f, o + line, sz.toFloat(), p)
                c.drawRect(0f, o, sz.toFloat(), o + line, p)
            }
        }
    }

    private fun dots(base: Int, d: Float): Bitmap {
        val cell = 22f * d
        val sz = (cell * 2).toInt()
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.FILL
            p.color = 0x2CFFFFFF
            val r = 1.25f * d
            c.drawCircle(sz * 0.25f, sz * 0.25f, r, p)
            c.drawCircle(sz * 0.75f, sz * 0.75f, r, p)
        }
    }

    private fun linen(base: Int, d: Float): Bitmap {
        val sz = max(48, (48 * d).toInt())
        val step = max(1f, d)
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.FILL
            val n = (sz / step).toInt()
            for (i in 0 until n) {
                p.color = ((BackgroundMath.hash01(i, 41) * 16f).toInt() shl 24) or 0xFFFFFF
                c.drawRect(0f, i * step, sz.toFloat(), (i + 1) * step, p)
                p.color = ((BackgroundMath.hash01(i, 43) * 14f).toInt() shl 24) or 0xFFFFFF
                c.drawRect(i * step, 0f, (i + 1) * step, sz.toFloat(), p)
            }
        }
    }

    private fun carbon(base: Int, d: Float): Bitmap {
        val cell = max(3f, 4f * d)
        val sz = (cell * 2).toInt()
        val half = sz / 2f
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.FILL
            p.color = 0x40000000
            c.drawRect(0f, 0f, half, half, p)
            c.drawRect(half, half, sz.toFloat(), sz.toFloat(), p)
            p.color = 0x0EFFFFFF
            c.drawRect(half, 0f, sz.toFloat(), half, p)
            c.drawRect(0f, half, half, sz.toFloat(), p)
            p.color = 0x14FFFFFF
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, d * 0.6f)
            c.drawLine(0f, half, half, 0f, p)
            c.drawLine(half, sz.toFloat(), sz.toFloat(), half, p)
        }
    }

    private fun white(base: Int, k: Float) = BackgroundMath.lerpArgb(base, 0xFFFFFFFF.toInt(), k)

    /** Tileable smooth value noise: a wrapped random lattice, smoothstep-interpolated. */
    private fun valueField(size: Int, cells: Int, seed: Int): FloatArray {
        val out = FloatArray(size * size)
        val step = size.toFloat() / cells
        for (y in 0 until size) {
            val fy = y / step; val y0 = fy.toInt(); val ty = fy - y0
            val sy = ty * ty * (3f - 2f * ty)
            for (x in 0 until size) {
                val fx = x / step; val x0 = fx.toInt(); val tx = fx - x0
                val sx = tx * tx * (3f - 2f * tx)
                val a = lattice(x0, y0, cells, seed); val b = lattice(x0 + 1, y0, cells, seed)
                val c = lattice(x0, y0 + 1, cells, seed); val e = lattice(x0 + 1, y0 + 1, cells, seed)
                out[y * size + x] = (a + (b - a) * sx) * (1f - sy) + (c + (e - c) * sx) * sy
            }
        }
        return out
    }

    private fun lattice(ix: Int, iy: Int, cells: Int, seed: Int) =
        BackgroundMath.hash01(((iy % cells) * cells + (ix % cells)) * 7919 + 13, seed)

    private fun cloudy(base: Int): Bitmap {
        val size = 192
        val f1 = valueField(size, 6, 17); val f2 = valueField(size, 16, 23)
        val px = IntArray(size * size)
        val br = (base shr 16) and 0xFF; val bg = (base shr 8) and 0xFF; val bb = base and 0xFF
        for (i in px.indices) {
            val v = 0.65f * f1[i] + 0.35f * f2[i] - 0.5f
            val d = (v * 0.30f * 255f + (BackgroundMath.hash01(i, 5) - 0.5f) * 0.05f * 255f).toInt()
            px[i] = (0xFF shl 24) or ((br + d).coerceIn(0, 255) shl 16) or
                ((bg + d).coerceIn(0, 255) shl 8) or (bb + d).coerceIn(0, 255)
        }
        return mutableBitmap(px, size)
    }

    private fun stripes(base: Int, d: Float): Bitmap {
        val sz = max(4, (12f * d).toInt())
        val half = sz / 2f
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.FILL
            p.color = 0x0EFFFFFF
            c.drawRect(0f, 0f, half, sz.toFloat(), p)
            p.color = 0x18FFFFFF
            c.drawRect(0f, 0f, max(1f, d * 0.75f), sz.toFloat(), p)
            p.color = 0x22000000
            c.drawRect(half, 0f, sz.toFloat(), sz.toFloat(), p)
        }
    }

    private fun diagonal(base: Int, d: Float): Bitmap {
        val sz = max(6, (14f * d).toInt())
        val s = sz.toFloat()
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.STROKE
            for ((w, col) in listOf(3.2f * d to 0x0AFFFFFF, max(1f, 0.9f * d) to 0x1CFFFFFF)) {
                p.strokeWidth = w; p.color = col
                for (o in -1..1) c.drawLine(o * s, s, o * s + s, 0f, p)
            }
        }
    }

    private fun waveLines(base: Int, d: Float): Bitmap {
        val w = max(16, (40f * d).toInt()); val h = max(8, (14f * d).toInt())
        return tile(w, h, base) { c, p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, d * 0.9f)
            p.color = 0x20FFFFFF
            val path = android.graphics.Path()
            val amp = h * 0.22f
            for (x in 0..w) {
                val y = h / 2f + amp * sin(TWO_PI * x / w)
                if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
            }
            c.drawPath(path, p)
        }
    }

    private fun topo(base: Int, d: Float): Bitmap {
        val size = (170f * d).toInt().coerceIn(170, 360)
        val f1 = valueField(size, 4, 31); val f2 = valueField(size, 8, 37)
        val px = IntArray(size * size)
        val levels = 7f
        val w = 0.06f
        for (i in px.indices) {
            val v = (0.7f * f1[i] + 0.3f * f2[i]) * levels
            val fr = v - floor(v)
            val dist = min(fr, 1f - fr)
            val line = (1f - dist / w).coerceIn(0f, 1f)
            val major = if (floor(v + 0.5f).toInt() % 3 == 0) 1f else 0.6f
            px[i] = white(base, 0.02f + 0.20f * line * line * major)
        }
        return mutableBitmap(px, size)
    }

    private fun circuit(base: Int, d: Float): Bitmap {
        val n = 4
        val cell = max(8f, 12f * d)
        val sz = (cell * n).toInt()
        fun h(i: Int, j: Int) = BackgroundMath.hash01(((i % n) + n) % n + n * (((j % n) + n) % n), 61) < 0.42f
        fun v(i: Int, j: Int) = BackgroundMath.hash01(((i % n) + n) % n + n * (((j % n) + n) % n), 67) < 0.42f
        return tile(sz, sz, base) { c, p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, d * 0.9f)
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0x2AFFFFFF
            val s = sz.toFloat()
            for (j in 0 until n) for (i in 0 until n) {
                val cx = (i + 0.5f) * cell; val cy = (j + 0.5f) * cell
                if (h(i, j)) {
                    c.drawLine(cx, cy, cx + cell, cy, p)
                    if (i == n - 1) c.drawLine(cx - s, cy, cx - s + cell, cy, p)
                }
                if (v(i, j)) {
                    c.drawLine(cx, cy, cx, cy + cell, p)
                    if (j == n - 1) c.drawLine(cx, cy - s, cx, cy - s + cell, p)
                }
            }
            for (j in 0 until n) for (i in 0 until n) {
                val deg = (if (h(i, j)) 1 else 0) + (if (h(i - 1, j)) 1 else 0) + (if (v(i, j)) 1 else 0) + (if (v(i, j - 1)) 1 else 0)
                val cx = (i + 0.5f) * cell; val cy = (j + 0.5f) * cell
                if (deg == 1) {
                    p.style = Paint.Style.STROKE; p.color = 0x3CFFFFFF
                    c.drawCircle(cx, cy, 1.8f * d, p)
                } else if (deg >= 3) {
                    p.style = Paint.Style.FILL; p.color = 0x3CFFFFFF
                    c.drawCircle(cx, cy, 1.4f * d, p)
                }
            }
        }
    }

    private fun bricks(base: Int, d: Float): Bitmap {
        val bw = max(12f, 24f * d); val bh = max(6f, 12f * d)
        val cols = 3; val rows = 4
        val w = (bw * cols).toInt(); val h = (bh * rows).toInt()
        return tile(w, h, base) { c, p ->
            p.style = Paint.Style.FILL
            for (r in 0 until rows) for (k in -1 until cols) {
                val x0 = k * bw + (if (r % 2 == 1) bw / 2f else 0f)
                val kk = ((k % cols) + cols) % cols
                p.color = ((BackgroundMath.hash01(r * 8 + kk, 73) * 14f).toInt() shl 24) or 0xFFFFFF
                c.drawRect(x0, r * bh, x0 + bw, (r + 1) * bh, p)
            }
            val lw = max(1f, d * 0.9f)
            p.color = 0x4A000000
            for (r in 0 until rows) {
                c.drawRect(0f, r * bh, w.toFloat(), r * bh + lw, p)
                for (k in -1..cols) {
                    val x = k * bw + (if (r % 2 == 1) bw / 2f else 0f)
                    c.drawRect(x, r * bh, x + lw, (r + 1) * bh, p)
                }
            }
            p.color = 0x10FFFFFF
            for (r in 0 until rows) c.drawRect(0f, r * bh + lw, w.toFloat(), r * bh + lw * 2f, p)
        }
    }

    private fun scales(base: Int, d: Float): Bitmap {
        val r = max(6f, 10f * d)
        val sz = (2f * r).toInt()
        return tile(sz, sz, base) { c, p ->
            // Bottom row first so the rows above overlap it: each scale shows its lower arc.
            for (j in 2 downTo -1) for (i in -1..2) {
                val cx = i * 2f * r + (if (((j % 2) + 2) % 2 == 1) r else 0f)
                val cy = j * r
                val shade = 0.025f + 0.03f * BackgroundMath.hash01(((i % 2) + 2) % 2 + 2 * (((j % 2) + 2) % 2), 79)
                p.style = Paint.Style.FILL
                p.color = white(base, shade)
                c.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1f, d * 0.8f)
                p.color = white(base, 0.16f)
                c.drawCircle(cx, cy, r - p.strokeWidth / 2f, p)
            }
        }
    }

    private fun hex(base: Int, d: Float): Bitmap {
        val s = 14f * d
        val w = sqrt(3f) * s
        val h = 3f * s
        return tile(Math.round(w), Math.round(h), base) { c, p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, d * 0.8f)
            p.color = 0x1AFFFFFF
            val path = android.graphics.Path()
            fun hexAt(cx: Float, cy: Float) {
                path.reset()
                for (k in 0 until 6) {
                    val a = (30f + 60f * k) * (PI.toFloat() / 180f)
                    val x = cx + s * cos(a); val y = cy + s * sin(a)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, p)
            }
            for (cx in floatArrayOf(0f, w)) for (cy in floatArrayOf(0f, h)) hexAt(cx, cy)
            hexAt(w / 2f, h / 2f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Live scenes
// ---------------------------------------------------------------------------------------------

@Composable
private fun LiveBackground(
    preset: String, colorA: Long, modifier: Modifier, animateRequested: Boolean, touch: State<TouchPoint?>?,
) {
    val scene = if (BackgroundPresets.lives.any { it.id == preset }) preset
    else if (preset == BackgroundMath.CUSTOM) "aurora" else BackgroundPresets.default(BackgroundKind.Live)
    val context = LocalContext.current
    // Reduced motion: animator duration scale 0 renders one static frame.
    val animate = animateRequested && remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (_: Throwable) { true }
    }
    val time: State<Float>? = if (animate) {
        val transition = rememberInfiniteTransition(label = "background")
        // Seconds of animation clock; wraps (invisibly) once an hour.
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3600f,
            animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        )
    } else null
    val density = LocalDensity.current.density
    val cache = remember { LiveCache() }
    val tint = colorA.toInt()
    val ribbon = remember { RibbonCache() }
    val bokeh = remember { BokehState() }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        if (scene == "ribbon") {
            drawRibbon(ribbon, (time?.value ?: STATIC_T) * 0.4f, density)
            return@Canvas
        }
        if (scene == "bokeh") {
            drawBokeh(bokeh, time?.value ?: STATIC_T, animate, touch?.value, density)
            return@Canvas
        }
        cache.ensure(size.width, size.height, density, scene, tint)
        val t = time?.value ?: STATIC_T
        drawRect(Color(cache.base))
        when (scene) {
            "starfield" -> drawStarfield(cache, t, density)
            "waves" -> drawWaves(cache, t)
            "fireflies" -> drawFireflies(cache, t, density)
            "nebula" -> drawNebula(cache, t)
            "rain" -> drawRain(cache, t, density)
            "lava" -> drawLava(cache, t)
            "snow" -> drawSnow(cache, t, density)
            "matrix" -> drawMatrix(cache, t, density)
            "orbits" -> drawOrbits(cache, t, density)
            "plasma" -> drawPlasma(cache, t)
            else -> drawAurora(cache, t)
        }
    }
}

/** Geometry, palette and brushes for the active scene; rebuilt only when size / scene / tint change. */
private class LiveCache {
    var w = -1f; var h = -1f; var dens = -1f; var scene = ""; var tint = 0
    var n = 0
    var xs = FloatArray(0); var ys = FloatArray(0); var zs = FloatArray(0)
    var ph = FloatArray(0); var sp = FloatArray(0)
    var pal = IntArray(0)
    var base = 0xFF000000.toInt()
    var brushes: Array<Brush> = emptyArray()
    var stroke = Stroke(1f)

    fun ensure(width: Float, height: Float, density: Float, scene: String, tint: Int) {
        if (width == w && height == h && density == dens && scene == this.scene && tint == this.tint) return
        w = width; h = height; dens = density; this.scene = scene; this.tint = tint
        val seed = scene.hashCode()
        val (s, v) = when (scene) {
            "starfield" -> 0.18f to 0.95f
            "rain" -> 0.30f to 0.80f
            "fireflies" -> 0.65f to 0.95f
            "lava" -> 0.85f to 0.95f
            "snow" -> 0.10f to 1.00f
            "matrix" -> 0.85f to 0.95f
            "orbits" -> 0.55f to 0.95f
            "plasma" -> 0.85f to 0.85f
            else -> 0.70f to 0.70f
        }
        pal = BackgroundMath.livePalette(scene, tint, s, v)
        base = BackgroundMath.lerpArgb(0xFF05060A.toInt(), pal[0], 0.07f)
        n = when (scene) {
            "starfield" -> BackgroundMath.particleCount(w, h, density, 2600f, 40, 260)
            "fireflies" -> BackgroundMath.particleCount(w, h, density, 14000f, 14, 60)
            "rain" -> BackgroundMath.particleCount(w, h, density, 3500f, 30, 160)
            "nebula" -> 5
            "waves" -> 5
            "lava" -> 6
            "plasma" -> 5
            "orbits" -> 7
            "snow" -> BackgroundMath.particleCount(w, h, density, 3000f, 40, 200)
            "matrix" -> BackgroundMath.matrixColumns(w, density)
            else -> 4
        }
        xs = FloatArray(n); ys = FloatArray(n); zs = FloatArray(n); ph = FloatArray(n); sp = FloatArray(n)
        BackgroundMath.seedParticles(n, seed, xs, ys, zs)
        for (i in 0 until n) {
            ph[i] = BackgroundMath.hash01(i, seed + 1) * TWO_PI
            sp[i] = 0.5f + 2f * BackgroundMath.hash01(i, seed + 2)
        }
        brushes = when (scene) {
            "aurora" -> Array(n) { i ->
                val yc = h * (0.16f + 0.15f * i)
                val top = yc - h * 0.07f
                val bottom = yc + h * 0.24f + h * 0.07f
                val c = Color(pal[i % pal.size])
                Brush.verticalGradient(
                    0f to Color.Transparent, 0.45f to c.copy(alpha = 0.34f), 1f to Color.Transparent,
                    startY = top, endY = bottom,
                )
            }
            "waves" -> Array(n) { i ->
                val y0 = h * (0.50f + 0.09f * i)
                val c = Color(pal[i % pal.size])
                Brush.verticalGradient(
                    0f to c.copy(alpha = 0.22f), 1f to c.copy(alpha = 0.03f),
                    startY = y0 - h * 0.03f, endY = h,
                )
            }
            "nebula" -> Array(n) { i ->
                val c = Color(pal[i % pal.size])
                Brush.radialGradient(
                    0f to c.copy(alpha = 0.34f), 0.5f to c.copy(alpha = 0.13f), 1f to Color.Transparent,
                    center = Offset.Zero, radius = 1f,
                )
            }
            "fireflies" -> Array(pal.size) { i ->
                val c = Color(pal[i])
                Brush.radialGradient(
                    0f to c.copy(alpha = 0.55f), 0.35f to c.copy(alpha = 0.18f), 1f to Color.Transparent,
                    center = Offset.Zero, radius = 1f,
                )
            }
            "lava" -> Array(n) { i ->
                val c = Color(pal[i % pal.size])
                Brush.radialGradient(
                    0f to c.copy(alpha = 0.70f), 0.55f to c.copy(alpha = 0.40f), 0.8f to c.copy(alpha = 0.12f), 1f to Color.Transparent,
                    center = Offset.Zero, radius = 1f,
                )
            }
            "plasma" -> Array(n) { i ->
                val c = Color(pal[i % pal.size])
                Brush.radialGradient(
                    0f to c.copy(alpha = 0.30f), 0.45f to c.copy(alpha = 0.13f), 1f to Color.Transparent,
                    center = Offset.Zero, radius = 1f,
                )
            }
            else -> emptyArray()
        }
        stroke = Stroke(width = max(1f, density * 0.8f))
    }
}

// Ribbons share one reusable Path.
private val ribbonPath = Path()

private fun DrawScope.drawAurora(c: LiveCache, t: Float) {
    val w = size.width; val h = size.height
    val seg = 28
    val path = ribbonPath
    for (i in 0 until c.n) {
        val yc = h * (0.16f + 0.15f * i)
        val amp = h * 0.05f
        val thick = h * 0.24f
        val k1 = TWO_PI * (0.7f + 0.5f * c.zs[i]) / w
        val k2 = TWO_PI * (1.6f + 0.6f * c.zs[i]) / w
        val s1 = t * 0.10f * c.sp[i]
        val s2 = t * 0.07f * c.sp[i]
        path.reset()
        for (j in 0..seg) {
            val x = w * j / seg
            val y = yc + amp * (0.65f * sin(x * k1 + s1 + c.ph[i]) + 0.35f * sin(x * k2 - s2 + c.ph[i] * 2f))
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (j in seg downTo 0) {
            val x = w * j / seg
            val y = yc + amp * (0.65f * sin(x * k1 + s1 + c.ph[i]) + 0.35f * sin(x * k2 - s2 + c.ph[i] * 2f))
            path.lineTo(x, y + thick * (0.85f + 0.15f * sin(x * k1 * 0.7f + s2)))
        }
        path.close()
        drawPath(path, c.brushes[i], blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawStarfield(c: LiveCache, t: Float, dens: Float) {
    val w = size.width; val h = size.height
    val cool = Color(c.pal[0]); val warm = Color(c.pal[1 % c.pal.size])
    for (i in 0 until c.n) {
        val z = c.zs[i]
        val x = BackgroundMath.wrap01(c.xs[i] + t * 0.0045f * z) * w
        val y = c.ys[i] * h
        val tw = 0.5f + 0.5f * sin(t * c.sp[i] + c.ph[i])
        val a = (0.25f + 0.75f * tw) * (0.30f + 0.65f * z) * 0.8f
        val r = (0.5f + 1.0f * z) * dens
        drawCircle((if (i % 6 == 0) warm else cool).copy(alpha = a), r, Offset(x, y))
    }
    // A few slow shooting stars, each on its own long period.
    for (slot in 0 until 3) {
        val period = 12f + 5f * slot
        val clock = (t + slot * 7.3f) / period
        val u = clock - floor(clock)
        if (u > 0.12f) continue
        val s = u / 0.12f
        val epoch = floor(clock).toInt()
        val sx = (0.1f + 0.8f * BackgroundMath.hash01(epoch, 100 + slot)) * w
        val sy = (0.05f + 0.35f * BackgroundMath.hash01(epoch, 200 + slot)) * h
        val ang = (20f + 25f * BackgroundMath.hash01(epoch, 300 + slot)) * (PI.toFloat() / 180f)
        val dx = cos(ang); val dy = sin(ang)
        val travel = 0.35f * max(w, h)
        val len = 0.10f * max(w, h)
        val hx = sx + dx * travel * s; val hy = sy + dy * travel * s
        val env = sin(s * PI.toFloat())
        for (j in 0 until 5) {
            val f0 = j / 5f; val f1 = (j + 1) / 5f
            drawLine(
                cool.copy(alpha = (1f - f0) * env * 0.7f),
                Offset(hx - dx * len * f0, hy - dy * len * f0),
                Offset(hx - dx * len * f1, hy - dy * len * f1),
                strokeWidth = 1.3f * dens * (1f - f0 * 0.6f), cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawWaves(c: LiveCache, t: Float) {
    val w = size.width; val h = size.height
    val seg = 32
    val path = ribbonPath
    for (i in 0 until c.n) {
        val y0 = h * (0.50f + 0.09f * i)
        val amp = h * (0.020f + 0.004f * i)
        val k = TWO_PI / (w * (1.1f - 0.12f * i))
        val sp = t * (0.35f + 0.12f * i) * (if (i % 2 == 0) 1f else -1f)
        path.reset()
        for (j in 0..seg) {
            val x = w * j / seg
            val y = y0 + amp * sin(x * k + sp + c.ph[i]) + amp * 0.5f * sin(x * k * 1.9f - sp * 0.7f)
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        drawPath(path, c.brushes[i], blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawFireflies(c: LiveCache, t: Float, dens: Float) {
    val w = size.width; val h = size.height
    val unit = Offset.Zero
    for (i in 0 until c.n) {
        val z = c.zs[i]
        val x = c.xs[i] * w + sin(t * 0.11f * c.sp[i] + c.ph[i]) * w * 0.05f + sin(t * 0.05f + c.ph[i] * 3f) * w * 0.02f
        val y = c.ys[i] * h + cos(t * 0.09f * c.sp[i] + c.ph[i] * 2f) * h * 0.035f
        val f = 0.5f + 0.5f * sin(t * c.sp[i] * 0.9f + c.ph[i] * 3f)
        val glow = f * f
        val r = (7f + 11f * z) * dens
        val a = (0.15f + 0.85f * glow) * 0.75f
        withTransform({ translate(x, y); scale(r, r, unit) }) {
            drawCircle(c.brushes[i % c.brushes.size], 1f, unit, alpha = a, blendMode = BlendMode.Plus)
        }
        drawCircle(Color(c.pal[i % c.pal.size]).copy(alpha = a * 0.8f), 0.9f * dens + 0.5f * z * dens, Offset(x, y))
    }
}

private fun DrawScope.drawNebula(c: LiveCache, t: Float) {
    val w = size.width; val h = size.height
    val big = max(w, h)
    val unit = Offset.Zero
    for (i in 0 until c.n) {
        val cx = (0.15f + 0.7f * c.xs[i]) * w + sin(t * 0.045f * c.sp[i] + c.ph[i]) * w * 0.10f
        val cy = (0.12f + 0.76f * c.ys[i]) * h + cos(t * 0.04f * c.sp[i] + c.ph[i] * 2f) * h * 0.08f
        val r = big * (0.32f + 0.14f * c.zs[i]) * (1f + 0.12f * sin(t * 0.06f + c.ph[i]))
        val sx = 1f + 0.25f * sin(t * 0.035f * c.sp[i] + c.ph[i] * 2f)
        val rot = (t * 1.2f * (if (i % 2 == 0) 1f else -1f) + c.ph[i] * 57f) % 360f
        withTransform({ translate(cx, cy); rotate(rot, unit); scale(r * sx, r / sx, unit) }) {
            drawCircle(c.brushes[i], 1f, unit, blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawRain(c: LiveCache, t: Float, dens: Float) {
    val h = size.height; val w = size.width
    val col = Color(c.pal[0]); val col2 = Color(c.pal[1 % c.pal.size])
    val slope = 0.22f // horizontal travel per unit of fall
    val inv = 1f / sqrt(1f + slope * slope)
    for (i in 0 until c.n) {
        val z = c.zs[i]
        val u = BackgroundMath.wrap01(c.ph[i] / TWO_PI + t * (0.45f + 0.55f * z) * 0.8f)
        val landY = h * (0.45f + 0.55f * z)
        val landX = c.xs[i] * w
        val color = if (i % 4 == 0) col2 else col
        if (u < 0.86f) {
            val len = (12f + 26f * z) * dens
            val headY = -len + u / 0.86f * (landY + len)
            val headX = landX + (headY - landY) * slope
            val tailY = headY - len * inv
            val tailX = headX - len * inv * slope
            drawLine(
                color.copy(alpha = 0.10f + 0.30f * z), Offset(tailX, tailY), Offset(headX, headY),
                strokeWidth = (0.6f + 0.8f * z) * dens, cap = StrokeCap.Round,
            )
        } else {
            val s = (u - 0.86f) / 0.14f
            val rx = (3f + 10f * z) * dens * s
            val ry = rx * 0.35f
            if (rx > 0.5f) {
                drawOval(
                    color.copy(alpha = (1f - s) * 0.30f * z), Offset(landX - rx, landY - ry),
                    Size(rx * 2f, ry * 2f), style = c.stroke,
                )
            }
        }
    }
}

/** Slow metaball-like blobs: big warm radial blobs that drift and breathe, summed additively. */
private fun DrawScope.drawLava(c: LiveCache, t: Float) {
    val w = size.width; val h = size.height
    val big = max(w, h)
    val unit = Offset.Zero
    for (i in 0 until c.n) {
        val cx = (0.12f + 0.76f * c.xs[i]) * w + sin(t * 0.060f * c.sp[i] + c.ph[i]) * w * 0.14f
        val cy = h * (0.5f + 0.46f * sin(t * 0.045f * c.sp[i] + c.ph[i] * 2f))
        val r = big * (0.16f + 0.10f * c.zs[i]) * (1f + 0.18f * sin(t * 0.09f * c.sp[i] + c.ph[i]))
        val sy = 1f + 0.30f * sin(t * 0.07f + c.ph[i] * 3f)
        withTransform({ translate(cx, cy); scale(r, r * sy, unit) }) {
            drawCircle(c.brushes[i], 1f, unit, blendMode = BlendMode.Plus)
        }
    }
}

/** Large soft colour fields drifting on Lissajous paths. */
private fun DrawScope.drawPlasma(c: LiveCache, t: Float) {
    val w = size.width; val h = size.height
    val big = max(w, h)
    val unit = Offset.Zero
    for (i in 0 until c.n) {
        val cx = w * (0.5f + 0.42f * sin(t * 0.070f * c.sp[i] + c.ph[i]))
        val cy = h * (0.5f + 0.42f * sin(t * 0.055f * c.sp[i] + c.ph[i] * 1.7f + 1f))
        val r = big * (0.50f + 0.25f * c.zs[i]) * (1f + 0.10f * sin(t * 0.08f + c.ph[i] * 2f))
        withTransform({ translate(cx, cy); scale(r, r, unit) }) {
            drawCircle(c.brushes[i], 1f, unit, blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawSnow(c: LiveCache, t: Float, dens: Float) {
    val w = size.width; val h = size.height
    val col = Color(c.pal[0]); val tint = Color(c.pal[1 % c.pal.size])
    for (i in 0 until c.n) {
        val z = c.zs[i]
        val y = BackgroundMath.wrap01(c.ys[i] + t * (0.014f + 0.030f * z) * (0.6f + 0.2f * c.sp[i])) * h
        val x = BackgroundMath.wrap01(c.xs[i] + sin(t * 0.25f * c.sp[i] + c.ph[i]) * 0.02f + t * 0.002f * z) * w
        val r = (0.8f + 2.2f * z) * dens
        val a = 0.18f + 0.62f * z
        drawCircle((if (i % 5 == 0) tint else col).copy(alpha = a), r, Offset(x, y))
        if (z > 0.8f) drawCircle(col.copy(alpha = a * 0.18f), r * 2.4f, Offset(x, y))
    }
}

/** Falling glyph-like streaks (short bars of varying size, no text) with a bright head and a fading trail. */
private fun DrawScope.drawMatrix(c: LiveCache, t: Float, dens: Float) {
    val w = size.width; val h = size.height
    val cols = c.n
    val cell = w / cols
    val rows = (h / cell).toInt() + 1
    val body = Color(c.pal[0])
    val head = Color(BackgroundMath.lerpArgb(c.pal[1 % c.pal.size], 0xFFFFFFFF.toInt(), 0.7f))
    val flip = floor(t * 1.5f).toInt()
    for (i in 0 until cols) {
        val trail = (6 + 12 * c.zs[i]).toInt()
        val span = rows + trail
        val pos = BackgroundMath.wrap01(c.ph[i] / TWO_PI + t * (0.012f + 0.016f * c.sp[i])) * span
        val headRow = pos.toInt()
        val x0 = i * cell
        for (k in 0 until trail) {
            val row = headRow - k
            if (row < 0 || row >= rows) continue
            val f = 1f - k / trail.toFloat()
            val seed = row * 131 + i * 17 + (if (k < 3) flip else 0)
            val gw = cell * (0.25f + 0.45f * BackgroundMath.hash01(seed, 91))
            val gh = cell * (0.35f + 0.45f * BackgroundMath.hash01(seed, 93))
            val colr = if (k == 0) head.copy(alpha = 0.95f) else body.copy(alpha = f * f * 0.65f)
            drawRect(colr, Offset(x0 + (cell - gw) / 2f, row * cell + (cell - gh) / 2f), Size(gw, max(gh, dens)))
        }
    }
}

private fun DrawScope.drawOrbits(c: LiveCache, t: Float, dens: Float) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h * 0.46f
    val diag = hypot(w, h)
    for (i in 0 until c.n) {
        val r = diag * (0.07f + 0.075f * i)
        val col = Color(c.pal[i % c.pal.size])
        drawCircle(col.copy(alpha = 0.10f), r, Offset(cx, cy), style = c.stroke)
        val dots = 2 + i % 3
        val dir = if (i % 2 == 0) 1f else -1f
        val speed = (0.16f - 0.015f * i) * dir
        val dotR = (2.2f + 0.5f * (i % 3)) * dens
        for (k in 0 until dots) {
            val a = t * speed + c.ph[i] + TWO_PI * k / dots
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            drawCircle(col.copy(alpha = 0.10f), dotR * 3f, Offset(x, y))
            drawCircle(col.copy(alpha = 0.85f), dotR, Offset(x, y))
        }
    }
    drawCircle(Color(c.pal[0]).copy(alpha = 0.9f), 3.2f * dens, Offset(cx, cy))
    drawCircle(Color(c.pal[0]).copy(alpha = 0.12f), 12f * dens, Offset(cx, cy))
}

// ---------------------------------------------------------------------------------------------
// Prototype scenes: "ribbon" and "bokeh" (touch-reactive)
// ---------------------------------------------------------------------------------------------

/**
 * A finger on the home screen, in the background layer's pixel space. [vx]/[vy] are px per second;
 * [id] increments for every new touch-down (the Bokeh scene spawns a ring when it changes).
 */
data class TouchPoint(val x: Float, val y: Float, val vx: Float, val vy: Float, val id: Int)

/**
 * Observes touches on this node (Initial pass, never consumes) and writes the first pointer into
 * [sink]; the value is null while nothing is pressed. Attach it to an ancestor of the home content
 * and pass the same state to `BackgroundLayer(touch = sink)`. Coordinates must share the layer's
 * origin (attach to a node with the same top-left and size as the layer).
 */
fun Modifier.observeBackgroundTouch(sink: androidx.compose.runtime.MutableState<TouchPoint?>): Modifier =
    pointerInput(sink) {
        var gesture = 0
        awaitPointerEventScope {
            var tracking = false
            var lx = 0f; var ly = 0f; var lt = 0L; var vx = 0f; var vy = 0f
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val p = e.changes.firstOrNull() ?: continue
                if (p.pressed) {
                    val pos = p.position
                    val now = p.uptimeMillis
                    if (!tracking) {
                        gesture++; tracking = true; vx = 0f; vy = 0f
                    } else {
                        val dt = (now - lt) / 1000f
                        if (dt > 0f) {
                            vx = vx * 0.5f + 0.5f * (pos.x - lx) / dt
                            vy = vy * 0.5f + 0.5f * (pos.y - ly) / dt
                        }
                    }
                    lx = pos.x; ly = pos.y; lt = now
                    sink.value = TouchPoint(pos.x, pos.y, vx, vy, gesture)
                } else {
                    tracking = false
                    if (sink.value != null) sink.value = null
                }
            }
        }
    }

/** Pure formulas of the two prototype scenes (unit-tested). */
internal object PrototypeMath {
    const val RIBBON_LINES = 30
    const val BOKEH_COUNT = 46

    /** Position of line [k] of [n] in -1..1 across the ribbon. */
    fun ribbonQ(k: Int, n: Int): Float = (k - (n - 1) / 2f) / (n / 2f)
    fun ribbonAlpha(q: Float): Float = 0.07f + 0.3f * Math.pow((1f - abs(q)).toDouble(), 1.6).toFloat()
    fun ribbonCentre(x: Float, s: Float, h: Float): Float =
        h * 0.66f + sin(x * 0.009f + s) * h * 0.05f + sin(x * 0.0035f - s * 0.6f) * h * 0.09f
    fun ribbonSpread(x: Float, s: Float, h: Float): Float =
        h * 0.012f + h * 0.016f * sin(x * 0.006f + s * 0.8f + 1.2f)

    fun bokehRadius(z: Float): Float = 6f + (68f - 6f) * Math.pow(z.toDouble(), 1.4).toFloat()
    fun bokehAlpha(z: Float): Float = 0.07f + 0.11f * z
    /** Touch push falloff: (1 - d/R)^2 inside the radius, 0 outside. */
    fun pushFactor(d: Float, radius: Float): Float {
        if (d >= radius || radius <= 0f) return 0f
        val f = 1f - d / radius
        return f * f
    }
}

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

private class RibbonCache {
    var w = -1f; var h = -1f; var dens = -1f
    var bg: Brush? = null; var glow: Brush? = null; var line: Brush? = null
    var cs = FloatArray(0); var sps = FloatArray(0); var count = 0
    var stroke = Stroke(1f)
    val path = Path()

    fun ensure(width: Float, height: Float, density: Float) {
        if (width == w && height == h && density == dens) return
        w = width; h = height; dens = density
        bg = Brush.linearGradient(
            0f to Color(0xFF141850), 0.55f to Color(0xFF2F3790), 1f to Color(0xFF5A64BD),
            start = Offset.Zero, end = Offset(w, h),
        )
        glow = Brush.radialGradient(
            0f to rgba(90, 255, 150, 0.38f), 1f to rgba(90, 255, 150, 0f),
            center = Offset(0.9f * w, 0.92f * h), radius = 0.9f * w,
        )
        line = Brush.horizontalGradient(
            0f to rgba(200, 210, 255, 0.5f), 0.7f to rgba(210, 225, 255, 0.5f), 1f to rgba(160, 255, 200, 0.7f),
            startX = 0f, endX = w,
        )
        count = ((w + 20f * density) / (10f * density)).toInt() + 1
        cs = FloatArray(count); sps = FloatArray(count)
        stroke = Stroke(width = max(1f, density))
    }
}

private fun DrawScope.drawRibbon(c: RibbonCache, s: Float, dens: Float) {
    c.ensure(size.width, size.height, dens)
    val h = size.height
    drawRect(c.bg!!)
    drawRect(c.glow!!)
    val step = 10f * dens
    for (i in 0 until c.count) {
        val xd = (-10f * dens + i * step) / dens // prototype units (dp)
        c.cs[i] = PrototypeMath.ribbonCentre(xd, s, h)
        c.sps[i] = PrototypeMath.ribbonSpread(xd, s, h)
    }
    val n = PrototypeMath.RIBBON_LINES
    val path = c.path
    val brush = c.line!!
    for (k in 0 until n) {
        val q = PrototypeMath.ribbonQ(k, n)
        val off = q * n / 2f
        path.reset()
        for (i in 0 until c.count) {
            val x = -10f * dens + i * step
            val y = c.cs[i] + off * c.sps[i]
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, brush, alpha = PrototypeMath.ribbonAlpha(q), style = c.stroke)
    }
}

private class BokehState {
    val n = PrototypeMath.BOKEH_COUNT
    var w = -1f; var h = -1f; var dens = -1f
    val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n); val vy = FloatArray(n)
    val f = FloatArray(n); val ph = FloatArray(n); val sw = FloatArray(n)
    val ox = FloatArray(n); val oy = FloatArray(n); val ovx = FloatArray(n); val ovy = FloatArray(n)
    val warm = BooleanArray(n)
    var rng = BackgroundMath.Prng(0xB0CE)
    var lastT = -1f
    var lastGesture = 0
    val rx = FloatArray(4); val ry = FloatArray(4); val rr = FloatArray(4); val ra = FloatArray(4)
    var ringNext = 0
    var bg: Brush? = null; var glow: Brush? = null
    var soft: Array<Brush> = emptyArray()   // [cool, warm], unit radius, alpha scaled by draw alpha
    var hollow: Array<Brush> = emptyArray()
    var thin = Stroke(1f); var ringStroke = Stroke(1.2f)

    fun ensure(width: Float, height: Float, density: Float) {
        if (width == w && height == h && density == dens) return
        w = width; h = height; dens = density
        rng = BackgroundMath.Prng(0xB0CE)
        val zs = FloatArray(n) { rng.nextFloat() }
        zs.sort() // draw order: far to near
        for (i in 0 until n) {
            z[i] = zs[i]
            x[i] = rng.nextFloat() * w
            y[i] = rng.nextFloat() * h
            vy[i] = -(4f + 14f * z[i]) * density
            f[i] = 0.25f + rng.nextFloat() * 0.5f
            ph[i] = rng.nextFloat() * TWO_PI
            sw[i] = (5f + 18f * z[i]) * density
            warm[i] = rng.nextFloat() < 0.2f
            ox[i] = 0f; oy[i] = 0f; ovx[i] = 0f; ovy[i] = 0f
        }
        for (i in 0 until 4) ra[i] = 0f
        lastT = -1f
        bg = Brush.linearGradient(
            0f to Color(0xFF0C1040), 0.55f to Color(0xFF212A78), 1f to Color(0xFF3A4BA6),
            start = Offset.Zero, end = Offset(0.4f * w, h),
        )
        glow = Brush.radialGradient(
            0f to rgba(90, 255, 150, 0.32f), 1f to rgba(90, 255, 150, 0f),
            center = Offset(0.9f * w, 0.95f * h), radius = 0.85f * w,
        )
        val cols = arrayOf(rgba(205, 218, 255, 1f), rgba(140, 255, 185, 1f))
        soft = Array(2) {
            val c = cols[it]
            Brush.radialGradient(
                0f to c.copy(alpha = 0.6f), 0.1f to c.copy(alpha = 0.6f), 0.7f to c, 1f to c.copy(alpha = 0f),
                center = Offset.Zero, radius = 1f,
            )
        }
        hollow = Array(2) {
            val c = cols[it]
            Brush.radialGradient(
                0f to c.copy(alpha = 0.4f / 2.2f), 0.88f to c.copy(alpha = 1f / 2.2f), 1f to c,
                center = Offset.Zero, radius = 1f,
            )
        }
        thin = Stroke(width = max(1f, density))
        ringStroke = Stroke(width = 1.2f * density)
    }
}

private fun DrawScope.drawBokeh(b: BokehState, t: Float, animate: Boolean, tp: TouchPoint?, dens: Float) {
    b.ensure(size.width, size.height, dens)
    val w = size.width; val h = size.height
    val dt = if (!animate || b.lastT < 0f) 0f else (t - b.lastT).coerceIn(0f, 0.05f)
    b.lastT = t
    drawRect(b.bg!!)
    drawRect(b.glow!!)

    if (tp != null && tp.id != b.lastGesture) {
        b.lastGesture = tp.id
        val s = b.ringNext++ and 3
        b.rx[s] = tp.x; b.ry[s] = tp.y; b.rr[s] = 0f; b.ra[s] = 0.8f
    }
    val unit = Offset.Zero
    for (i in 0 until b.n) {
        val z = b.z[i]
        val r = PrototypeMath.bokehRadius(z) * dens
        if (dt > 0f) {
            b.y[i] += b.vy[i] * dt
            if (b.y[i] < -r - 20f * dens) { b.y[i] = h + r; b.x[i] = b.rng.nextFloat() * w }
        }
        var px = b.x[i] + sin(t * b.f[i] + b.ph[i]) * b.sw[i] + b.ox[i]
        var py = b.y[i] + b.oy[i]
        if (dt > 0f) {
            if (tp != null) {
                val dx = px - tp.x; val dy = py - tp.y
                val d = hypot(dx, dy)
                val ff = PrototypeMath.pushFactor(d, (110f + 70f * z) * dens)
                if (ff > 0f) {
                    val m = 0.45f + 0.9f * z
                    val ux = if (d > 1e-3f) dx / d else 0f
                    val uy = if (d > 1e-3f) dy / d else -1f
                    b.ovx[i] += (ux * ff * 900f * dens * m + tp.vx * ff * 0.35f * m) * dt
                    b.ovy[i] += (uy * ff * 900f * dens * m + tp.vy * ff * 0.35f * m) * dt
                }
            }
            b.ovx[i] += (-b.ox[i] * 5f - b.ovx[i] * 2.6f) * dt
            b.ovy[i] += (-b.oy[i] * 5f - b.ovy[i] * 2.6f) * dt
            b.ox[i] += b.ovx[i] * dt
            b.oy[i] += b.ovy[i] * dt
            px = b.x[i] + sin(t * b.f[i] + b.ph[i]) * b.sw[i] + b.ox[i]
            py = b.y[i] + b.oy[i]
        }
        val a = PrototypeMath.bokehAlpha(z)
        val wi = if (b.warm[i]) 1 else 0
        if (z > 0.6f) {
            withTransform({ translate(px, py); scale(r, r, unit) }) {
                drawCircle(b.soft[wi], 1f, unit, alpha = a)
            }
        } else {
            withTransform({ translate(px, py); scale(r, r, unit) }) {
                drawCircle(b.hollow[wi], 1f, unit, alpha = (a * 2.2f).coerceAtMost(1f))
            }
            val base = if (b.warm[i]) rgba(140, 255, 185, 1f) else rgba(205, 218, 255, 1f)
            drawCircle(base.copy(alpha = 0.3f * (1f - z)), r, Offset(px, py), style = b.thin)
        }
    }
    for (s in 0 until 4) {
        if (b.ra[s] <= 0f) continue
        if (dt > 0f) {
            b.rr[s] += 280f * dens * dt
            b.ra[s] = (b.ra[s] - 1.3f * dt).coerceAtLeast(0f)
        }
        drawCircle(rgba(190, 255, 215, b.ra[s] * 0.5f), b.rr[s], Offset(b.rx[s], b.ry[s]), style = b.ringStroke)
    }
}
