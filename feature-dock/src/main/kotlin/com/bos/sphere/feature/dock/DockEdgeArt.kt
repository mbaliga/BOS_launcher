package com.bos.sphere.feature.dock

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bos.sphere.feature.grid.ArtSet
import com.bos.sphere.feature.grid.VisualArt

/**
 * Wave 54 (settingsVisual): the three places the dock can sit (Bottom, Left, Right) as drawn cards, in physical coordinates (the phone lying down,
 * because the side positions only exist in landscape).
 */
object DockEdgeArt {
    val edge = ArtSet(DockEdge.entries) { edgeArt(it) }

    private fun edgeArt(e: DockEdge): VisualArt = { ink ->
        val h = size.height * 0.62f; val w = h * 1.85f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        val d = density
        drawRoundRect(ink.soft, o, Size(w, h), CornerRadius(5f * d), style = Stroke(1.5f * d))
        val bar = ink.strong
        when (e) {
            DockEdge.Bottom -> drawRoundRect(bar, Offset(o.x + w * 0.22f, o.y + h * 0.78f), Size(w * 0.56f, h * 0.14f), CornerRadius(3f * d))
            DockEdge.Left -> drawRoundRect(bar, Offset(o.x + w * 0.05f, o.y + h * 0.2f), Size(w * 0.09f, h * 0.6f), CornerRadius(3f * d))
            DockEdge.Right -> drawRoundRect(bar, Offset(o.x + w * 0.86f, o.y + h * 0.2f), Size(w * 0.09f, h * 0.6f), CornerRadius(3f * d))
        }
        for (i in 0 until 3) drawRoundRect(ink.faint, Offset(o.x + w * (0.3f + i * 0.14f), o.y + h * 0.28f), Size(w * 0.09f, w * 0.09f), CornerRadius(2f * d))
    }
}
