package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * HOM-23 (wave 22): the thin outline transport glyphs of the Glance notch: an outlined double-triangle rewind, an outlined single
 * triangle for play, two thin rounded bars for pause and an outlined double-triangle fast-forward. Stroke only (no fill), 24-unit grid.
 */
object OutlineMediaGlyphs {
    /** Stroke width in grid units: at the 22 dp the notch draws them it is about 1.65 dp. */
    const val STROKE = 1.8f

    private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.White), strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    val Rewind = glyph("OutlineRewind") {
        moveTo(11.5f, 6f); lineTo(3.5f, 12f); lineTo(11.5f, 18f); close()
        moveTo(20.5f, 6f); lineTo(12.5f, 12f); lineTo(20.5f, 18f); close()
    }
    val Play = glyph("OutlinePlay") { moveTo(8f, 5.5f); lineTo(19f, 12f); lineTo(8f, 18.5f); close() }
    val Pause = glyph("OutlinePause") {
        moveTo(9f, 6f); lineTo(9f, 18f)
        moveTo(15f, 6f); lineTo(15f, 18f)
    }
    val Forward = glyph("OutlineForward") {
        moveTo(3.5f, 6f); lineTo(11.5f, 12f); lineTo(3.5f, 18f); close()
        moveTo(12.5f, 6f); lineTo(20.5f, 12f); lineTo(12.5f, 18f); close()
    }
}
