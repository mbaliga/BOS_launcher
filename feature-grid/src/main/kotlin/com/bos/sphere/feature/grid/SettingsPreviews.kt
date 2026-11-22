package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Wave 54 (settingsVisual): the option cards of every setting are drawn by [OneUiVisualChoice] and the providers in [SettingsArtHome] and
 * [SettingsArtLook]; the old sideways rail (VisualChoice, PreviewCard) and its one-off previews are gone. What stays here is the live density
 * preview under the Default density cards, drawn with the real grid geometry.
 */

/** Sample colours for the live density preview (no real apps are shown). */
object SampleApps {
    private val palette = longArrayOf(
        0xFF6D5BFF, 0xFFFF6B6B, 0xFF2EC4B6, 0xFFFFB703, 0xFF3A86FF, 0xFFE76F51, 0xFF8338EC, 0xFF06D6A0,
        0xFFEF476F, 0xFF118AB2, 0xFFF4A261, 0xFF9B5DE5,
    )

    fun color(i: Int): Color = Color(palette[i % palette.size].toInt())
}

/** A grid of [cols]×[rows] with the real geometry, icon scale and spacing. */
@Composable
fun DensityPreview(
    cols: Int, rows: Int, iconScale: Float, spacing: Float, modifier: Modifier = Modifier,
    spacingV: Float = spacing, insetX: Float = 0f, insetTop: Float = 0f, insetBottom: Float = 0f,
) {
    ComposeCanvas(modifier.fillMaxSize().padding(4.dp)) {
        val g = GridGeometry.compute(size.width, size.height, cols, rows, verticalBias = 0.5f, spread = spacing, spreadY = spacingV, insetX = insetX, insetTop = insetTop, insetBottom = insetBottom)
        val icon = g.base * iconScale
        for (i in 0 until g.capacity) {
            drawRoundRect(SampleApps.color(i), Offset(g.centreX(i) - icon / 2, g.centreY(i) - icon / 2), Size(icon, icon), CornerRadius(icon * 0.24f))
        }
    }
}

