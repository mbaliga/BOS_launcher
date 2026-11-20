package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Wave 38 (popupsHidden): where the Comms bottom sheets (Sound and Vibration, Connectivity) are drawn. Comms is a panel under the shrunken home
 * card and is narrower than the window, so a sheet drawn inside it was only as wide as the panel and the card covered its right end. The panel
 * hands the sheets to this host instead; the app draws [Layer] in a full-window box above the home card, so the sheet spans the whole window
 * width and the scrim covers the whole screen. Without a host (tests, a bare panel) the sheets are drawn inside the panel as before.
 */
class PopupSheetHost {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Draws whatever the panel put here (nothing while no sheet is open). */
    @Composable
    fun Layer() { content?.invoke() }
}

@Composable
fun rememberPopupSheetHost(): PopupSheetHost = remember { PopupSheetHost() }
