package com.bos.sphere.ui.viz

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The texture painters (`ASOC.viz.fills` and `canvasFills`). Each pattern is a small tile drawn once with the Canvas API into an
 * [ImageBitmap] and handed to Compose as a repeating [ShaderBrush]; tiles are cached by (texture, ink, scale, density), so a chart that
 * redraws allocates nothing. Solid is just the ink; fade is a gradient, not a tile ([fadeBrush]).
 */
object VizTextures {
    private data class Key(val kind: VizTexture, val ink: Int, val scaleQ: Int, val densityQ: Int)

    private val cache = object : LinkedHashMap<Key, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?): Boolean = size > MAX_TILES
    }
    private const val MAX_TILES = 40

    /** Tile edge in toolkit px at scale 1 (the `pat(name, size, ...)` calls of `fills`). */
    fun baseSize(kind: VizTexture): Float = when (kind) {
        VizTexture.Hatch -> 7f; VizTexture.Dots -> 9f; VizTexture.Cross -> 7f; VizTexture.Lines -> 6f
        VizTexture.Checks -> 8f; VizTexture.Stipple -> 4.5f; VizTexture.Solid, VizTexture.Fade -> 0f
    }

    /** Number of cached tiles (tests). */
    internal fun cached(): Int = synchronized(cache) { cache.size }
    internal fun clear() = synchronized(cache) { cache.clear() }

    /**
     * The brush of [kind] in [ink]. [scale] is the toolkit's texture scale (1 on a chart, 0.62 on a small swatch). [fadeEdge] only matters for
     * [VizTexture.Fade]: where the gradient runs (see [fadeBrush]).
     */
    fun brush(kind: VizTexture, ink: Color, scale: Float = 1f, density: Float = 1f, fadeTop: Float = 0f, fadeBottom: Float = 1f): Brush = when (kind) {
        VizTexture.Solid -> SolidColor(ink)
        VizTexture.Fade -> fadeBrush(ink, fadeTop, fadeBottom)
        else -> ShaderBrush(ImageShader(tile(kind, ink, scale, density), TileMode.Repeated, TileMode.Repeated))
    }

    /** `fade`, direction up: the mark is faintest (7 percent ink) at [bottom], 50 percent at 55 percent of the way up, solid at [top]. */
    fun fadeBrush(ink: Color, top: Float, bottom: Float): Brush = Brush.verticalGradient(
        0f to ink.copy(alpha = 1f), 0.45f to ink.copy(alpha = 0.5f), 1f to ink.copy(alpha = 0.07f),
        startY = top, endY = if (bottom > top) bottom else top + 1f,
    )

    /** `fade`, direction right (the bars use it): 7 percent at the left, solid at the right. */
    fun fadeBrushRight(ink: Color, left: Float, right: Float): Brush = Brush.horizontalGradient(
        0f to ink.copy(alpha = 0.07f), 0.55f to ink.copy(alpha = 0.5f), 1f to ink.copy(alpha = 1f),
        startX = left, endX = if (right > left) right else left + 1f,
    )

    /** `fade`, direction left (right-to-left bars): solid at the left, 7 percent at the right. */
    fun fadeBrushLeft(ink: Color, left: Float, right: Float): Brush = Brush.horizontalGradient(
        0f to ink.copy(alpha = 1f), 0.45f to ink.copy(alpha = 0.5f), 1f to ink.copy(alpha = 0.07f),
        startX = left, endX = if (right > left) right else left + 1f,
    )

    /** The repeating tile, from the cache. */
    fun tile(kind: VizTexture, ink: Color, scale: Float, density: Float): ImageBitmap {
        require(kind != VizTexture.Solid && kind != VizTexture.Fade) { "$kind has no tile" }
        val key = Key(kind, ink.value.toLong().hashCode(), Math.round(scale * 100f), Math.round(density * 100f))
        synchronized(cache) { cache[key]?.let { return it } }
        val made = draw(kind, ink, scale, density)
        synchronized(cache) { cache[key] = made }
        return made
    }

    private fun draw(kind: VizTexture, ink: Color, scale: Float, density: Float): ImageBitmap {
        val px = Math.max(2, Math.round(baseSize(kind) * scale * density))
        val s = px.toFloat()
        val k = scale * density
        val bmp = ImageBitmap(px, px)
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(s, s)) {
            fun seg(a: Float, b: Float, c: Float, d: Float, w: Float) = drawLine(ink, Offset(a, b), Offset(c, d), strokeWidth = w, cap = StrokeCap.Square)
            // up and down are the canvasFills `up(g,s)` / `down(g,s)`: three segments so the diagonal tiles without a seam.
            fun up(w: Float) { seg(0f, s, s, 0f, w); seg(-s / 2, s / 2, s / 2, -s / 2, w); seg(s / 2, s * 1.5f, s * 1.5f, s / 2, w) }
            fun down(w: Float) { seg(0f, 0f, s, s, w); seg(-s / 2, s / 2, s / 2, s * 1.5f, w); seg(s / 2, -s / 2, s * 1.5f, s / 2, w) }
            when (kind) {
                VizTexture.Hatch -> up(s * 0.32f)
                VizTexture.Cross -> { up(s * 0.13f); down(s * 0.13f) }
                VizTexture.Dots -> { drawCircle(ink, 1.5f * k, Offset(s * 0.25f, s * 0.25f)); drawCircle(ink, 1.5f * k, Offset(s * 0.75f, s * 0.75f)) }
                VizTexture.Lines -> drawRect(ink, Offset.Zero, Size(s, s * 0.3f))
                VizTexture.Checks -> { drawRect(ink, Offset.Zero, Size(s / 2, s / 2)); drawRect(ink, Offset(s / 2, s / 2), Size(s / 2, s / 2)) }
                VizTexture.Stipple -> drawCircle(ink, 0.85f * k, Offset(s / 2, s / 2))
                VizTexture.Solid, VizTexture.Fade -> Unit
            }
        }
        return bmp
    }
}
