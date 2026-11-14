package com.bos.sphere.feature.grid

import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.bos.sphere.core.input.DialogKeys

/** While a dialog is capturing a key chord (the shortcut sheet), Esc and the controller buttons must reach it untranslated. */
object DialogKeyGate { @Volatile var passThrough: Boolean = false }

/** Wraps a dialog window's callback so controller buttons and Esc act as arrows, Enter, Menu and Back (INP-20). */
private class ControllerWindowCallback(private val base: Window.Callback) : Window.Callback by base {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (DialogKeyGate.passThrough) return base.dispatchKeyEvent(event)
        val mapped = DialogKeys.translate(event.keyCode)
        if (mapped == event.keyCode) return base.dispatchKeyEvent(event)
        val meta = if (DialogKeys.shiftFor(event.keyCode)) event.metaState or KeyEvent.META_SHIFT_ON else event.metaState
        return base.dispatchKeyEvent(
            KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, meta, event.deviceId, event.scanCode, event.flags, event.source),
        )
    }
}

/**
 * Call once inside the content of a Compose Dialog. Dialogs are their own window and never see the activity key
 * hook, so without this a controller's B button would not close them (INP-20, INP-01).
 */
@Composable
fun DialogControllerKeys() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previous = window?.callback
        if (window != null && previous != null && previous !is ControllerWindowCallback) window.callback = ControllerWindowCallback(previous)
        onDispose { if (window != null && previous != null && window.callback is ControllerWindowCallback) window.callback = previous }
    }
}
