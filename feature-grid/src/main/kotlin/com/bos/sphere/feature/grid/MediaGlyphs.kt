package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Transport-control glyphs (24-unit, filled with softly rounded joins) for the media strip. */
object MediaGlyphs {
    private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Play = glyph("play") { moveTo(8f, 5.5f); lineTo(19f, 12f); lineTo(8f, 18.5f); close() }
    val Pause = glyph("pause") {
        moveTo(7f, 5.5f); lineTo(10f, 5.5f); lineTo(10f, 18.5f); lineTo(7f, 18.5f); close()
        moveTo(14f, 5.5f); lineTo(17f, 5.5f); lineTo(17f, 18.5f); lineTo(14f, 18.5f); close()
    }
    val Next = glyph("next") {
        moveTo(6f, 6f); lineTo(14f, 12f); lineTo(6f, 18f); close()
        moveTo(17f, 6f); lineTo(17f, 18f)
    }
    val Previous = glyph("previous") {
        moveTo(18f, 6f); lineTo(10f, 12f); lineTo(18f, 18f); close()
        moveTo(7f, 6f); lineTo(7f, 18f)
    }
}
