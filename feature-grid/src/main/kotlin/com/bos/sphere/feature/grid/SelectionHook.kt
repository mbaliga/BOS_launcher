package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Wave 45 (editHandles): the two things every selection needs next to the page. The live region that says what an edit did
 * (TalkBack), and, until the item action bar (Resize, Remove, Settings) exists, a minimal 48 dp Remove button under the selected
 * item. The bar replaces the button by setting [EditSelectionState.fallbackBar] to false.
 */
@Composable
fun EditSelectionOverlay(selection: EditSelectionState, modifier: Modifier = Modifier, showBar: Boolean = true) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.onSizeChanged { area = it }) {
        val say = selection.announcement
        if (say.isNotEmpty()) Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = say })
        val b = selection.bounds
        if (showBar && selection.fallbackBar && selection.selectedKey != null && b != null) {
            val h = with(density) { 48.dp.toPx() }
            val gap = with(density) { 14.dp.toPx() }
            // Above the item when there is room (a page starts with a free band; the rows under an item are full of icons).
            val below = b.top - origin.y - gap - h < 0f
            val x = ((b.center.x - origin.x) - with(density) { 48.dp.toPx() }).coerceIn(0f, (area.width - with(density) { 96.dp.toPx() }).coerceAtLeast(0f))
            val y = if (below) b.bottom - origin.y + gap else b.top - origin.y - gap - h
            Box(
                Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
                    .defaultMinSize(minWidth = 96.dp).height(48.dp)
                    .testTag("selection-remove")
                    .background(Color(0xE61C1D26), RoundedCornerShape(24.dp))
                    .clickable { selection.remove() }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text(L10n.tr("Remove"), color = HyleColors.InkBright, fontSize = 14.sp) }
        }
    }
}

/** The dashed target ghost of a drag-move: the snapped cells (primary), blocked (error), or the folder target (ring and tint). */
fun Modifier.moveGhost(color: Color, radiusPx: Float, strokePx: Float, dashPx: Float, filled: Boolean = true): Modifier = drawBehind {
    if (filled) drawRoundRect(color.copy(alpha = 0.18f), cornerRadius = CornerRadius(radiusPx))
    drawRoundRect(color, Offset.Zero, Size(size.width, size.height), CornerRadius(radiusPx), style = Stroke(strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx * 0.6f))))
}

/** The solid ring that marks the item a dragged icon will make a folder with. */
fun Modifier.folderTarget(color: Color, radiusPx: Float, strokePx: Float): Modifier = drawBehind {
    drawRoundRect(color.copy(alpha = 0.28f), cornerRadius = CornerRadius(radiusPx))
    drawRoundRect(color, Offset.Zero, Size(size.width, size.height), CornerRadius(radiusPx), style = Stroke(strokePx))
}
