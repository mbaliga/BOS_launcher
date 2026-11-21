package com.bos.sphere.core.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * A boxless backdrop for popups drawn over the Radar and the home: a radial gradient that is dark in the middle and fades to
 * fully transparent at the edge of the composable's bounds, like smoke, instead of a filled and bordered card. The gradient is
 * elliptical (it follows the bounds), so pad the content inside it so the text sits on the dark part.
 */
object SmokyBackdrop {
    /** Alpha of the dark core. */
    const val CORE_ALPHA = 0.94f
    /** Where along the radius the dark core ends and the fade starts. */
    const val CORE_STOP = 0.5f
}

fun Modifier.smokyBackdrop(): Modifier = drawBehind {
    val side = maxOf(size.width, size.height)
    if (side <= 0f) return@drawBehind
    val r = side / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val brush = Brush.radialGradient(
        0f to Color.Black.copy(alpha = SmokyBackdrop.CORE_ALPHA),
        SmokyBackdrop.CORE_STOP to Color.Black.copy(alpha = SmokyBackdrop.CORE_ALPHA * 0.82f),
        0.8f to Color.Black.copy(alpha = SmokyBackdrop.CORE_ALPHA * 0.28f),
        1f to Color.Transparent,
        center = Offset(r, r), radius = r,
    )
    withTransform({ scale(size.width / side, size.height / side, pivot = centre) }) {
        drawRect(brush, topLeft = Offset(centre.x - r, centre.y - r), size = Size(side, side))
    }
}
