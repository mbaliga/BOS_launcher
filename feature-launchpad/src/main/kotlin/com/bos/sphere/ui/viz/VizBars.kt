package com.bos.sphere.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.RoundRect

/**
 * `bars` (wave 29): the bar of one ranked row, [thickness] high (the toolkit's `th`), as long as [share] of the row's width (the toolkit's
 * `v / max * width`, at least 3 for anything above zero, ends rounded to half the thickness) and filled with the row's [texture]. A faint
 * track (8 percent ink) shows the full length. It starts from the reading start: the left edge, the right one in a right-to-left layout.
 * The toolkit writes the name and the figure on the line above each bar; the launcher's row carries them as text beside the icon instead, so this is
 * only the mark. It is decoration for sight: the row carries the spoken name.
 */
@Composable
fun VizBar(share: Float, texture: VizTexture, tone: VizTone, modifier: Modifier = Modifier, thickness: Dp = BAR_THICKNESS) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxWidth().height(thickness).clearAndSetSemantics { }) {
        val h = size.height; val w = size.width
        val r = CornerRadius(h / 2)
        drawRoundRect(tone.ink.copy(alpha = 0.08f), Offset.Zero, Size(w, h), r)
        val bw = VizBarGeometry.length(share, w, 3f * density)
        if (bw > 0f) {
            val left = if (rtl) w - bw else 0f
            val p = Path().apply { addRoundRect(RoundRect(left, 0f, left + bw, h, r)) }
            // Small swatch scale (0.62), as the toolkit uses under 20 px: a 4 dp bar holds a whole tile or two.
            val brush = when {
                texture != VizTexture.Fade -> VizTextures.brush(texture, tone.ink, VizBarGeometry.scale(texture), density)
                rtl -> VizTextures.fadeBrushLeft(tone.ink, left, left + bw)
                else -> VizTextures.fadeBrushRight(tone.ink, left, left + bw)
            }
            clipPath(p) { drawRect(brush, Offset(left, 0f), Size(bw, h)) }
        }
    }
}

val BAR_THICKNESS: Dp = 4.dp

/** The length of a bar in a row [width] wide: 0 for nothing (or a bad share), [minLen] at least for anything above zero, never more than the row. */
object VizBarGeometry {
    /** The tile scale of a texture on a 4 dp bar: the swatch scale (0.62), except dots, whose two staggered rows must both fit the bar (a 9 px tile at 0.44 is 4 dp) so they read as dots and not as stipple. */
    fun scale(texture: VizTexture): Float = if (texture == VizTexture.Dots) 0.44f else 0.62f


    fun length(share: Float, width: Float, minLen: Float = 3f): Float {
        if (!share.isFinite() || share <= 0f || !width.isFinite() || width <= 0f) return 0f
        return maxOf(minLen, share.coerceAtMost(1f) * width).coerceAtMost(width)
    }
}

/** The picked row when it still exists among [count] rows, else -1 (data changed under a saved pick: nothing is dimmed). */
fun clampPick(picked: Int, count: Int): Int = if (picked in 0 until count) picked else -1
