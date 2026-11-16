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
 * STR-01: puts the live home composition into its card pose with ONE graphicsLayer (scale, translate, rounded clip, soft shadow).
 * [fraction] is read inside the layer block, so a drag never recomposes the home and the home keeps its state (NFR-05).
 */
fun Modifier.homeCardLayer(geometry: LaunchpadCardGeometry, fraction: () -> Float): Modifier = this.graphicsLayer {
    val f = fraction()
    val p = geometry.pose(f)
    scaleX = p.scale; scaleY = p.scale
    translationX = p.translationX; translationY = p.translationY
    if (f > 0.001f) {
        shape = RoundedCornerShape(p.layerCornerDp.dp)
        clip = true
        shadowElevation = p.shadowDp.dp.toPx() * p.scale
        ambientShadowColor = Color.Black.copy(alpha = 0.5f)
        spotShadowColor = Color.Black.copy(alpha = 0.5f)
    }
}

/**
 * The background the home card floats in front of: a quiet vertical gradient, and the neighbouring page peeking in under the card
 * (the second card of the mock-up) as a soft tinted gradient with no hard border.
 */
fun Modifier.launchpadNeighbourCard(rtl: Boolean, reduced: Boolean, bg: Color, surface: Color, fraction: () -> Float): Modifier = this.drawBehind {
    drawRect(Brush.verticalGradient(listOf(bg, androidx.compose.ui.graphics.lerp(bg, surface, 0.55f))))
    val f = fraction()
    if (f <= 0.001f) return@drawBehind
    val g = LaunchpadCardGeometry(size.width, size.height, CardEdge.forLaunchpad(rtl), density, reduced)
    val r = g.neighbourBounds(f)
    val radius = g.pose(f).visibleCornerDp * density
    val tint = BlueLight
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f)), startY = r.top, endY = r.top + r.height * 0.4f),
        topLeft = Offset(r.left, r.top), size = Size(r.width, r.height), cornerRadius = CornerRadius(radius, radius),
    )
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
    val widthDp = (geometry.cardCoverFraction * geometry.widthPx) / geometry.density
    Box(
        // Absolute sides: the card is on the physical right (left in right-to-left), whatever the layout direction does to Start and End.
        modifier.align(androidx.compose.ui.BiasAbsoluteAlignment(if (rtl) -1f else 1f, 0f)).fillMaxHeight().width(widthDp.dp)
            .testTag("lp.card")
            .semantics { contentDescription = L10n.tr("Home card. Return to home") }
            .launchpadCloseDrag(reveal, slideDp, mirrored = rtl)
            .clickable(onClickLabel = L10n.tr("Return to home"), role = Role.Button, onClick = onReturn),
    )
}
