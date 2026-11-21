package com.bos.sphere.feature.grid

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wave 25 (owner, item 5): "remove the glass effect buttons and keep only black gradients instead." The Radar's field, Clear X, Close and
 * mic are black surfaces with one subtle vertical gradient (black at the top, a little less opaque at the bottom): no blur, no translucent
 * white rim, no inner glow, no grey fill (docs/decisions/radarBlack.md, which supersedes radarGlass.md).
 *
 * White text and icons keep their contrast by construction: the least opaque pixel of the surface is [BlackSpec.BOTTOM_ALPHA] black, so even over a
 * pure white background the surface is at worst a 30 percent white, a dark grey, and white on it is far above 4.5:1
 * (`RadarBlackW25Test` computes it). There is no measuring of the background any more.
 */
object BlackSpec {
    /** Black at the top of a surface, and at its bottom (the gradient runs between the two, straight down). */
    const val TOP_ALPHA = 0.96f
    const val BOTTOM_ALPHA = 0.70f
    /** The 56 dp round button (the mic), and the 48 dp touch target everything else keeps (NFR-13). */
    val BigButton: Dp = 56.dp
    val Target: Dp = 48.dp

    /** Text and icons on the black surfaces. */
    val Content: Color = Color.White
    /** Placeholder text: 80 percent white, also above 4.5:1 on the worst pixel. */
    val Hint: Color = Color(0xCCFFFFFF)
}

/**
 * Paints the black gradient behind this element (one vertical brush built once per size; drawing allocates nothing). It does not clip: give the
 * same [shape] to `clip` if the content must stay inside.
 */
fun Modifier.blackSurface(shape: Shape = RoundedCornerShape(50), pressed: Boolean = false): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = if (pressed) Brush.verticalGradient(listOf(Color.Black, Color.Black))
    else Brush.verticalGradient(listOf(Color.Black.copy(alpha = BlackSpec.TOP_ALPHA), Color.Black.copy(alpha = BlackSpec.BOTTOM_ALPHA)), startY = 0f, endY = size.height)
    onDrawBehind { drawOutline(outline, brush) }
}

/**
 * A black (or, with [filled] false, plain) button with a 48 dp touch target by default. Labelled for TalkBack, focusable, operable from a
 * keyboard, with a white focus ring. No ripple.
 */
@Composable
fun BlackButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = BlackSpec.Target,
    shape: Shape = CircleShape,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .then(if (filled || pressed) Modifier.blackSurface(shape, pressed) else Modifier)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A fully rounded black field: [content] lays out in a Row (leading item, the text, trailing item). Mirrors in right-to-left. Not clickable itself. */
@Composable
fun BlackField(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.blackSurface(shape).clip(shape), verticalAlignment = Alignment.CenterVertically, content = content)
}
