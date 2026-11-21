package com.bos.sphere.feature.dock

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester

/**
 * INP-01, INP-20: the seam between the dock and the launcher's shared input graph. This module cannot see the grid
 * module (which owns the focus registry and the dialog window hook), so the app installs these once and the rail and the
 * panel dialogs call them; with nothing installed (tests, previews) they do nothing.
 */
object DockInput {
    /** Where the rail's items are registered on the shared focus graph. */
    interface FocusBridge {
        /** An item's bounds in root pixels changed (or it appeared). */
        fun register(id: String, left: Float, top: Float, right: Float, bottom: Float, requester: FocusRequester)
        fun unregister(id: String)
        fun onFocus(id: String, focused: Boolean)
    }

    @Volatile var bridge: FocusBridge? = null

    /**
     * Called first inside every dock dialog (the panel, its editors and pickers, the schedule dialog, the rail's help):
     * installs the controller window hook (B and Esc close, A confirms) and marks the dialog as a surface above the home
     * for the shared graph, for as long as the dialog is composed.
     */
    @Volatile var dialogHook: @Composable () -> Unit = {}

    /** The focus layer the dock's dialogs sit on: above the drawer (2), the puck (3) and corner editors (5), below menus (8). */
    const val DIALOG_LAYER = 6
}

/** Marks the call site as inside a dock dialog's content (INP-20). */
@Composable
internal fun DockDialogInput() { DockInput.dialogHook() }
