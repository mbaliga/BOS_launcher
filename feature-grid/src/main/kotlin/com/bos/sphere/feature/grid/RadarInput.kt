package com.bos.sphere.feature.grid

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.bos.sphere.core.input.InputMode


/** Mouse and trackpad events count as pointer; a finger never does. */
internal fun PointerType.toInputMode(): InputMode = if (this == PointerType.Mouse) InputMode.Pointer else InputMode.Touch

/**
 * PAL-20: right-click (secondary button) runs [onSecondary], same as long-press. Other presses pass through.
 * INP-02: the same tile also shows the pointer affordances of every other launcher item: a hand pointer icon and a
 * hover wash (hover events only ever come from a mouse, trackpad or stylus, so a finger never sees the wash).
 */
internal fun Modifier.secondaryClick(onSecondary: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // INP-02: the pointer icon shown while hovering is reported through a seam, so a test can observe hover -> intended icon.
    LaunchedEffect(hovered) { RadarHoverProbe.observer?.invoke(if (hovered) RADAR_POINTER_ICON else null) }
    Modifier
        .hoverable(source)
        .pointerHoverIcon(RADAR_POINTER_ICON)
        .drawBehind { if (hovered) drawRoundRect(RADAR_HOVER_WASH, cornerRadius = CornerRadius(10.dp.toPx())) }
        .pointerInput(onSecondary) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                        e.changes.forEach { it.consume() }
                        onSecondary()
                    }
                }
            }
        }
}

/** INP-02 test seam: told the icon a hovered Radar tile shows (null when the pointer leaves). Null in the app. */
internal object RadarHoverProbe { @Volatile var observer: ((PointerIcon?) -> Unit)? = null }

/** The pointer shape over a Radar tile (INP-02); a constant so a test can state it. */
internal val RADAR_POINTER_ICON = PointerIcon.Hand
private val RADAR_HOVER_WASH = Color(0x22FFFFFF)
