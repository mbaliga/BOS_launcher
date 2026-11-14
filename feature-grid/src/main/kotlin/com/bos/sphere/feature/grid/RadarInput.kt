package com.bos.sphere.feature.grid

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.bos.sphere.core.input.InputMode

/** The active input as the host knows it (INP-06). The default is Touch; the host provides the real value, and Radar also tracks it itself from the events it sees. */
val LocalInputMode = compositionLocalOf { InputMode.Touch }

/** Mouse and trackpad events count as pointer; a finger never does. */
internal fun PointerType.toInputMode(): InputMode = if (this == PointerType.Mouse) InputMode.Pointer else InputMode.Touch

/** PAL-20: right-click (secondary button) runs [onSecondary], same as long-press. Other presses pass through. */
internal fun Modifier.secondaryClick(onSecondary: () -> Unit): Modifier = pointerInput(onSecondary) {
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
