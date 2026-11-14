package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.WidgetSizing
import com.bos.sphere.core.catalog.WidgetStack
import com.bos.sphere.core.design.HyleColors
import kotlin.math.abs

/** HOM-14: the key-to-step mapping of a focused stack (arrows, Page Up/Down); null for keys it ignores. */
fun WidgetStack.forComposeKey(key: Key): WidgetStack? = when (key) {
    Key.DirectionRight -> forKey(WidgetSizing.Arrow.Right)
    Key.DirectionDown, Key.PageDown -> forKey(WidgetSizing.Arrow.Down)
    Key.DirectionLeft -> forKey(WidgetSizing.Arrow.Left)
    Key.DirectionUp, Key.PageUp -> forKey(WidgetSizing.Arrow.Up)
    else -> null
}

/**
 * HOM-14: several widgets sharing one slot. Only the active widget is composed ([content] gets its
 * key); a swipe, the scroll wheel or arrow and page keys switch it, and dots show the position.
 * The stack is state owned by the caller ([onChange] returns the new one).
 */
@Composable
fun WidgetStackView(
    stack: WidgetStack,
    onChange: (WidgetStack) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val current by rememberUpdatedState(stack)
    val change by rememberUpdatedState(onChange)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier
            .semantics { contentDescription = "Widget stack, ${stack.active + 1} of ${stack.keys.size}" }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else current.forComposeKey(e.key)?.let { change(it); true } ?: false
            }
            .pointerInput("wheel") {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type != PointerEventType.Scroll) continue
                        val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) { change(current.scroll(dy)); ev.changes.forEach { it.consume() } }
                    }
                }
            }
            .pointerInput(Unit) {
                var dx = 0f; var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val major = if (abs(dx) >= abs(dy)) dx else dy
                        if (abs(major) >= threshold) change(if (major < 0) current.next() else current.previous())
                    },
                ) { c, d -> c.consume(); dx += d.x; dy += d.y }
            }
            .focusTarget().focusable(),
    ) {
        stack.current?.let { content(it) }
        if (stack.keys.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stack.keys.forEachIndexed { i, _ ->
                    Box(Modifier.size(5.dp).background(if (i == stack.active) HyleColors.InkBright else HyleColors.Ink.copy(alpha = 0.45f), CircleShape))
                }
            }
        }
    }
}
