package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** HOM-23 (wave 22): the numbers of the controls notch (pure, in dp). */
object GlanceNotch {
    /** The notch is this share of the clock-area width, within [MIN_WIDTH_DP] and [MAX_WIDTH_DP], never wider than the window. */
    const val WIDTH_SHARE = 0.58f
    const val MIN_WIDTH_DP = 220f
    const val MAX_WIDTH_DP = 320f
    /** The drawn height below the display cutout (the cutout itself is also covered: the shape starts at y = 0). */
    const val BODY_HEIGHT_DP = 32f
    /** Concave flare of each top shoulder, and the convex radius of each bottom corner. */
    const val FLARE_DP = 16f
    const val CORNER_DP = 14f
    const val TARGET_DP = 48f
    const val ICON_DP = 22f
    /** The icon centre sits this far below the top of the controls row (cutout bottom). */
    const val ICON_TOP_DP = 5f
    /** Gap between the notch bottom and the title line's slot (the line has its own 8 dp padding: about 11 dp to the text). */
    const val LINE_GAP_DP = 3f

    fun widthDp(areaWidthDp: Float): Float = (areaWidthDp * WIDTH_SHARE).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP).coerceAtMost(areaWidthDp)
    /** The straight-sided body is this wide: the top is wider by a flare on each side. */
    fun bottomWidth(topWidth: Float, flare: Float = FLARE_DP): Float = topWidth - 2f * flare
    fun heightDp(cutoutTopDp: Float): Float = cutoutTopDp.coerceAtLeast(0f) + BODY_HEIGHT_DP
}

/** The notch outline: flat top at y = 0 flaring out with concave curves, straight sides, rounded convex bottom corners. */
/** [flare] and [corner] are in dp. */
class NotchShape(private val flare: Float = GlanceNotch.FLARE_DP, private val corner: Float = GlanceNotch.CORNER_DP) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(path(size.width, size.height, flare * density.density, corner * density.density))
    }

    companion object {
        fun path(w: Float, h: Float, f: Float, c: Float): Path = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f)
            quadraticBezierTo(w - f, 0f, w - f, f)
            lineTo(w - f, h - c)
            quadraticBezierTo(w - f, h, w - f - c, h)
            lineTo(f + c, h)
            quadraticBezierTo(f, h, f, h - c)
            lineTo(f, f)
            quadraticBezierTo(f, 0f, 0f, 0f)
            close()
        }
    }
}
