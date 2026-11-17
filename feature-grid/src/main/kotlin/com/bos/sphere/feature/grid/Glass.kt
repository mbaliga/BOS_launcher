package com.bos.sphere.feature.grid

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wave 21 (owner): "Make it glass." One reusable look for pills and buttons that sit over the signal background
 * (docs/decisions/radarGlass.md). Glass here is cheap: translucency, a gradient hairline and a short sheen, all built once per size;
 * the background shows through untouched (no RenderEffect, no per-frame allocation, no blurred copy of the background).
 *
 * Numbers live in [GlassSpec] so a test can compute the worst colour a glass fill can lay over a background and check text contrast on it.
 */
object GlassSpec {
    /** White fill, top-left (the brightest corner) and bottom-right, at rest, for a dark surface. */
    const val FILL_TOP_LEFT = 0.16f
    const val FILL_BOTTOM_RIGHT = 0.08f
    /** A pressed glass is a little brighter, with no ripple. */
    const val PRESS_BOOST = 0.07f
    /** The sheen: white at this alpha at the very top, fading to nothing by the end of the top third. */
    const val SHEEN_TOP = 0.14f
    /** The soft inner shadow along the bottom edge (black). */
    const val INNER_SHADOW = 0.10f
    /** Hairline edge: bright on the top-left, faint on the bottom-right. */
    const val EDGE_TOP_LEFT = 0.55f
    const val EDGE_BOTTOM_RIGHT = 0.10f
    val Hairline: Dp = 1.dp
    /** The 56 dp round button (the mic), and the 48 dp touch target everything else keeps (NFR-13). */
    val BigButton: Dp = 56.dp
    val Target: Dp = 48.dp

    /** Light surfaces: a whiter, milkier fill with a darker hairline, and dark content. */
    const val LIGHT_FILL_TOP_LEFT = 0.60f
    const val LIGHT_FILL_BOTTOM_RIGHT = 0.38f

    /** Text and icon colour on glass: white on a dark surface, near-black on a light one. */
    fun content(dark: Boolean): Color = if (dark) Color.White else Color(0xFF14151A)
    /** Secondary text (placeholder, hints) on glass; still AA (checked by the Radar glass contrast test). */
    fun hint(dark: Boolean): Color = if (dark) Color(0xCCFFFFFF) else Color(0xB314151A)

    private fun over(top: Float, base: Float) = 1f - (1f - top) * (1f - base)

    /** The sheen is only in the top third; the glyphs and text sit in the middle, where at most this much of it reaches them. */
    const val SHEEN_AT_CONTENT = 0.02f

    /**
     * The strongest white a glass lays over any pixel that carries text or an icon: the brightest fill corner, pressed, plus the little
     * sheen that reaches the content (and, when [nested], the quieter glass of the scope pill on top of the field). Text contrast is
     * measured against the background blended with this, so every other pixel of the glass is easier to read than what is checked.
     */
    fun worstWhiteAlpha(pressed: Boolean = true, strength: Float = 1f, nested: Boolean = true): Float {
        val one = over(SHEEN_AT_CONTENT * strength, (FILL_TOP_LEFT + if (pressed) PRESS_BOOST else 0f) * strength)
        // The scope pill inside the field is glass on glass (quieter, [NESTED_STRENGTH]): its label sits on both.
        return if (nested) over(FILL_TOP_LEFT * NESTED_STRENGTH, one) else one
    }
    /** Glass nested inside glass (the scope pill in the field) is drawn at this strength. */
    const val NESTED_STRENGTH = 0.45f
}

/**
 * Paints the glass behind this element: a translucent tinted fill (brighter toward the top-left), a short sheen in the top third,
 * a soft inner shadow, and a 1 dp gradient hairline. Brushes are built once per size ([drawWithCache]); drawing allocates nothing.
 * Apply it before any `clip` or `clickable` that should sit on it; it does not clip, so give the same [shape] to `clip` if the content must stay inside.
 *
 * @param tint the colour of the glass; white by default (a violet tint marks "listening").
 * @param pressed slightly brighter; there is no ripple.
 * @param dark the surface is dark (Radar always is); false gives the milky light-theme glass.
 * @param strength 0..1 scales every layer, so glass nested inside glass can be quieter.
 * @param shade black laid under the glass at this alpha (0 = none): a measured darkening where the background behind it is so bright that
 *   white text would fail AA (see [GlassShades]). Most backgrounds need none.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(50),
    tint: Color = Color.White,
    pressed: Boolean = false,
    dark: Boolean = true,
    strength: Float = 1f,
    shade: Float = 0f,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val w = size.width
    val h = size.height
    val boost = if (pressed) GlassSpec.PRESS_BOOST else 0f
    val topLeft = (if (dark) GlassSpec.FILL_TOP_LEFT else GlassSpec.LIGHT_FILL_TOP_LEFT) + boost
    val bottomRight = (if (dark) GlassSpec.FILL_BOTTOM_RIGHT else GlassSpec.LIGHT_FILL_BOTTOM_RIGHT) + boost
    val base = if (dark) tint else if (tint == Color.White) Color.White else tint
    val fill = Brush.linearGradient(
        listOf(base.copy(alpha = (topLeft * strength).coerceIn(0f, 1f)), base.copy(alpha = (bottomRight * strength).coerceIn(0f, 1f))),
        start = Offset.Zero, end = Offset(w, h),
    )
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = GlassSpec.SHEEN_TOP * strength), Color.Transparent), startY = 0f, endY = h / 3f)
    val shadow = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = GlassSpec.INNER_SHADOW * strength)), startY = h * 0.55f, endY = h)
    val edgeDark = if (dark) Color.White else Color(0xFF14151A)
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = (GlassSpec.EDGE_TOP_LEFT * strength).coerceIn(0f, 1f)), edgeDark.copy(alpha = (GlassSpec.EDGE_BOTTOM_RIGHT * strength).coerceIn(0f, 1f))),
        start = Offset.Zero, end = Offset(w, h),
    )
    val hairline = Stroke(width = GlassSpec.Hairline.toPx())
    val under = Color.Black.copy(alpha = shade.coerceIn(0f, 1f))
    onDrawBehind {
        if (shade > 0f) drawGlassOutline(outline, SolidColor(under))
        drawGlassOutline(outline, fill)
        drawGlassOutline(outline, shadow)
        drawGlassOutline(outline, sheen)
        drawGlassOutline(outline, edge, style = hairline)
    }
}

/**
 * A round (or any [shape]) glass button with a 48 dp touch target by default. [filled] false draws no glass at rest and only the
 * brighter fill while pressed (for a button that sits inside a glass field, like the Clear X). Labelled for TalkBack, focusable and
 * operable from a keyboard (`clickable`).
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = GlassSpec.Target,
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    dark: Boolean = true,
    filled: Boolean = true,
    shade: Float = 0f,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Wave 22 (second audit): a keyboard or d-pad user needs to see where focus is (there is no indication on these buttons otherwise).
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .then(if (filled) Modifier.glass(shape, tint, pressed, dark, shade = shade) else if (pressed) Modifier.glass(shape, tint, true, dark, strength = 0.7f) else Modifier)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A fully rounded glass field: [content] lays out in a Row (leading item, the text, trailing item). Mirrors in right-to-left.
 * The field itself is not clickable; the text input inside it is.
 */
@Composable
fun GlassField(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    dark: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    shade: Float = 0f,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.glass(shape, tint, false, dark, shade = shade).clip(shape),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun DrawScope.drawGlassOutline(outline: Outline, brush: Brush, style: DrawStyle = Fill) {
    when (outline) {
        is Outline.Rectangle -> drawRect(brush, outline.rect.topLeft, outline.rect.size, style = style)
        is Outline.Rounded -> {
            val r = outline.roundRect
            drawRoundRect(brush, Offset(r.left, r.top), androidx.compose.ui.geometry.Size(r.width, r.height), cornerRadius = r.topLeftCornerRadius, style = style)
        }
        is Outline.Generic -> drawPath(outline.path, brush, style = style)
    }
}

/**
 * How much black the glass needs under it, at the top and at the bottom of the screen, so white text and icons on it keep WCAG AA over
 * the background in use (NFR-15). Measured, not guessed: the background is painted with the real painters into a small bitmap at the
 * brightest and a quiet signal level and at a few clock values, the pixels where the glass sits are read, and [shadeFor] finds the least
 * black that holds AA over the brightest of them. Most backgrounds give 0.
 */
internal data class GlassShades(val top: Float = 0f, val bottom: Float = 0f)

internal object GlassShade {
    private const val SCALE = 0.5f
    private val LEVELS = intArrayOf(4, 2, 0)
    private val TIMES = longArrayOf(ScopeMath.STILL_TIME_MS, 2_500L, 4_000L, 6_500L, 9_000L, 14_000L, 31_000L)
    /** A little over AA, so a bright pixel the sampler missed between two grid points still lands on AA. */
    private const val TARGET = com.bos.sphere.core.quality.Contrast.AA_NORMAL + 0.2
    /** More than this hides the background; the text then gets its AA from the cap alone. */
    const val MAX = 0.92f

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val HINT = 0xCCFFFFFF.toInt()

    /** The glass colour over [bg] with [shade] black under it and the strongest white the glass lays over any pixel. */
    fun glassOver(bg: Int, shade: Float): Int =
        com.bos.sphere.core.quality.Contrast.blend(WHITE, com.bos.sphere.core.quality.Contrast.blend(0xFF000000.toInt(), bg, shade.toDouble()), GlassSpec.worstWhiteAlpha().toDouble())

    private fun holds(bgs: IntArray, shade: Float): Boolean {
        for (bg in bgs) {
            val g = glassOver(bg, shade)
            if (com.bos.sphere.core.quality.Contrast.ratio(WHITE, g) < TARGET) return false
            if (com.bos.sphere.core.quality.Contrast.ratio(com.bos.sphere.core.quality.Contrast.blend(HINT, g, 0.8), g) < TARGET) return false
        }
        return true
    }

    /** The least shade in 0..[MAX] under which white text and the placeholder keep AA over every colour in [backgrounds] (the cap when none does). */
    fun shadeFor(backgrounds: IntArray): Float {
        if (holds(backgrounds, 0f)) return 0f
        var lo = 0f; var hi = MAX
        if (!holds(backgrounds, hi)) return MAX
        repeat(14) { val mid = (lo + hi) / 2; if (holds(backgrounds, mid)) hi = mid else lo = mid }
        return hi
    }

    /**
     * The zones, in dp, where Radar's glass sits on a [wDp] x [hDp] screen: the top bar (field, Close) and the bottom centre (the mic).
     * Wave 22 (audit): these are the positions with no status-bar inset and the keyboard closed. The field really sits below the status bar
     * and cutout and the mic rides above the keyboard; measured on the default pair, a top zone of 8 to 84 dp needs a shade of 0.77 (not
     * 0.37) and a mic zone from 140 dp above the bottom needs 0.46, from 300 dp 0.64 (not 0.40). They are NOT applied: that would turn the
     * default glass much darker, which is the owner's call (docs/status/w22-auditGlass.md).
     */
    internal fun topZone(wDp: Float) = floatArrayOf(16f, wDp - 12f, 8f, 60f)
    internal fun bottomZone(wDp: Float, hDp: Float) = floatArrayOf(wDp / 2 - 28f, wDp / 2 + 28f, hDp - 64f, hDp - 8f)

    internal fun render(choice: com.bos.sphere.core.data.BackgroundChoice, intensity: com.bos.sphere.core.data.RadarIntensity, level: Int, timeMs: Long, wDp: Int, hDp: Int, scale: Float = SCALE): IntArray {
        val w = (wDp * scale).toInt().coerceAtLeast(2); val h = (hDp * scale).toInt().coerceAtLeast(2)
        val image = androidx.compose.ui.graphics.ImageBitmap(w, h)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        val painter = BackgroundPainter(w.toFloat(), h.toFloat(), scale, BackgroundConfig(choice, intensity, true, baseFill = true))
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(scale), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
        ) { painter.draw(this, com.bos.sphere.core.signals.SignalState.forced(level), timeMs, true) }
        return IntArray(w * h).also { image.readPixels(it, 0, 0, w, h) }
    }

    /** The pixels of [zone] (x0, x1, y0, y1 in dp) of a render at [scale], opaque. */
    internal fun pixels(px: IntArray, wDp: Int, hDp: Int, zone: FloatArray, scale: Float = SCALE): IntArray {
        val w = (wDp * scale).toInt().coerceAtLeast(2); val h = (hDp * scale).toInt().coerceAtLeast(2)
        val out = ArrayList<Int>()
        for (y in (zone[2] * scale).toInt().coerceAtLeast(0) until (zone[3] * scale).toInt().coerceAtMost(h))
            for (x in (zone[0] * scale).toInt().coerceAtLeast(0) until (zone[1] * scale).toInt().coerceAtMost(w)) out += px[y * w + x] or (0xFF shl 24)
        return out.toIntArray()
    }

    fun compute(choice: com.bos.sphere.core.data.BackgroundChoice, intensity: com.bos.sphere.core.data.RadarIntensity, wDp: Int, hDp: Int): GlassShades {
        val top = ArrayList<Int>(); val bottom = ArrayList<Int>()
        for (level in LEVELS) for (t in TIMES) {
            val px = render(choice, intensity, level, t, wDp, hDp)
            top += pixels(px, wDp, hDp, topZone(wDp.toFloat()), SCALE).toList()
            bottom += pixels(px, wDp, hDp, bottomZone(wDp.toFloat(), hDp.toFloat()), SCALE).toList()
        }
        return GlassShades(shadeFor(top.toIntArray()), shadeFor(bottom.toIntArray()))
    }
}

/** The glass shades for the background in use at this window size; computed once off the main thread and again only when the background or the size changes. */
@Composable
internal fun rememberGlassShades(choice: com.bos.sphere.core.data.BackgroundChoice, intensity: com.bos.sphere.core.data.RadarIntensity, wDp: Int, hDp: Int): GlassShades {
    var shades by remember { androidx.compose.runtime.mutableStateOf(GlassShades()) }
    androidx.compose.runtime.LaunchedEffect(choice, intensity, wDp, hDp) {
        shades = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { GlassShade.compute(choice, intensity, wDp, hDp) }.getOrDefault(GlassShades(0.5f, 0.5f)) }
    }
    return shades
}
