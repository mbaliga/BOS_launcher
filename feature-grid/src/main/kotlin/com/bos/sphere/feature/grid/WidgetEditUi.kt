package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.WidgetSizing
import com.bos.sphere.core.design.HyleColors

/**
 * HOM-07: resize chrome for a widget in edit mode. Handles on the east, south and south-east edges
 * report a span change when released ([onResize] gets column and row deltas); Shift+arrow keys do
 * the same from the keyboard. Wrap a hosted widget in it and give it the cell pitch in px.
 */
@Composable
fun WidgetResizeFrame(
    cellWpx: Float,
    cellHpx: Float,
    onResize: (dCols: Int, dRows: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .border(2.dp, HyleColors.VioletSoft, RoundedCornerShape(12.dp))
            .onKeyEvent { e ->
                val arrow = when (e.key) {
                    Key.DirectionRight -> WidgetSizing.Arrow.Right
                    Key.DirectionLeft -> WidgetSizing.Arrow.Left
                    Key.DirectionDown -> WidgetSizing.Arrow.Down
                    Key.DirectionUp -> WidgetSizing.Arrow.Up
                    else -> null
                }
                if (arrow != null && e.isShiftPressed) {
                    if (e.type == KeyEventType.KeyUp) WidgetSizing.deltaForKey(arrow).let { onResize(it.first, it.second) }
                    true
                } else false
            }
            .focusable(),
    ) {
        content()
        Handle(WidgetSizing.Handle.East, Alignment.CenterEnd, cellWpx, cellHpx, onResize)
        Handle(WidgetSizing.Handle.South, Alignment.BottomCenter, cellWpx, cellHpx, onResize)
        Handle(WidgetSizing.Handle.SouthEast, Alignment.BottomEnd, cellWpx, cellHpx, onResize)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Handle(
    handle: WidgetSizing.Handle, at: Alignment, cellWpx: Float, cellHpx: Float, onResize: (Int, Int) -> Unit,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.align(at).size(28.dp).background(HyleColors.VioletSoft.copy(alpha = 0.9f), CircleShape)
            .pointerInput(handle, cellWpx, cellHpx) {
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        WidgetSizing.deltaForDrag(handle, dx, dy, cellWpx, cellHpx).let { if (it.first != 0 || it.second != 0) onResize(it.first, it.second) }
                        dx = 0f; dy = 0f
                    },
                    onDragCancel = { dx = 0f; dy = 0f },
                ) { _, drag -> dx += drag.x; dy += drag.y }
            },
    )
}
