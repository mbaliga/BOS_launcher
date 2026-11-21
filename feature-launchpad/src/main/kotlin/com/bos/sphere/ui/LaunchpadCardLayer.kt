package com.bos.sphere.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.L10n

/**
 * STR-01: puts the live home composition into its card pose with ONE graphicsLayer (scale, translate, rounded clip, soft shadow),
 * then dims it with a black scrim and draws a hairline lighter edge on its leading side (the side facing the panel), both inside the
 * clip. [fraction] is read inside the layer and draw blocks, so a drag never recomposes the home and the home keeps its state (NFR-05).
 */
fun Modifier.homeCardLayer(geometry: LaunchpadCardGeometry, fraction: () -> Float): Modifier = this.graphicsLayer {
    val f = fraction()
    val p = geometry.pose(f)
    scaleX = p.scale; scaleY = p.scale
    translationX = p.translationX; translationY = p.translationY
    if (f > 0.001f) {
        shape = RoundedCornerShape(p.layerCornerDp.dp)
        clip = true
        // Wave 26: no drop shadow; it fell over the notification rows beside the card (the scrim and hairline already separate the card).
    }
}.drawWithContent {
    drawContent()
    val f = fraction()
    if (f <= 0.001f) return@drawWithContent
    val p = geometry.pose(f)
    if (p.scrimAlpha > 0f) drawRect(Color.Black.copy(alpha = p.scrimAlpha))
    // Hairline on the leading side only: a rounded outline that fades out away from that side.
    val r = p.layerCornerDp * density
    val leftLeading = geometry.edge == CardEdge.Right
    val stroke = (1.5f * density / p.scale).coerceAtLeast(1f)
    val fadeTo = (r * 2f).coerceAtMost(size.width)
    val brush = if (leftLeading) Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.Transparent), 0f, fadeTo)
    else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.Transparent), size.width, size.width - fadeTo)
    drawRoundRect(
        brush = brush, topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r, r), style = Stroke(stroke),
    )
}

/** The panel's own backdrop: a quiet vertical gradient, static (the panel does not move). */
fun Modifier.launchpadBackdrop(bg: Color, surface: Color): Modifier = this.drawBehind {
    drawRect(Brush.verticalGradient(listOf(bg, androidx.compose.ui.graphics.lerp(bg, surface, 0.55f))))
}

/**
 * The visible slice of the home card while Launchpad is open: tap it (or press Enter on it) to return home, drag it back to close live.
 * Sits over the card so taps never reach the home beneath, and is a focusable button so keyboard and switch users can reach it.
 */
@Composable
fun BoxScope.LaunchpadCardOverlay(
    geometry: LaunchpadCardGeometry,
    reveal: LaunchpadRevealState,
    onReturn: () -> Unit,
    slideDp: Float,
    modifier: Modifier = Modifier,
) {
    val rtl = geometry.edge == CardEdge.Left
    // The visible sliver, widened to a 48 dp touch target when it is thinner than that.
    val widthDp = geometry.returnZonePx / geometry.density
    Box(
        // Absolute sides: the card is on the physical right (left in right-to-left), whatever the layout direction does to Start and End.
        modifier.align(androidx.compose.ui.BiasAbsoluteAlignment(if (rtl) -1f else 1f, 0f)).fillMaxHeight().width(widthDp.dp)
            .testTag("lp.card")
            .semantics { contentDescription = L10n.tr("Home card. Return to home") }
            .launchpadCloseDrag(reveal, slideDp, mirrored = rtl)
            .clickable(onClickLabel = L10n.tr("Return to home"), role = Role.Button, onClick = onReturn),
    )
}
