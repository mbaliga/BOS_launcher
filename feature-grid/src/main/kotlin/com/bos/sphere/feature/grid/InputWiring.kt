package com.bos.sphere.feature.grid

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.EditModeState
import com.bos.sphere.core.input.HotCornerId
import com.bos.sphere.core.input.LauncherCommand
import kotlinx.coroutines.flow.drop

/**
 * The home's one hook into the input layer. Collects registered shortcuts and routes them to the home's own handlers,
 * hosts the shortcut sheet, sends the Menu key on empty home to the quick sheet (HOM-21) and tells the hot-corner layer
 * what each corner does. [onCommand] returns true when the home handled the command.
 */
@Composable
fun LauncherInputWiring(
    overlayOpen: Boolean,
    onCommand: (LauncherCommand) -> Boolean,
    onEmptyMenu: () -> Unit,
    hotCornerDwellMs: Long,
    cornerLabel: (HotCornerId) -> String?,
    onCornerActivate: (HotCornerId) -> Unit,
    /** INP-12: Radar or the drawer is open, and the prefs ask for the keyboard to rise with it in this orientation. */
    surfaceOpen: Boolean = false,
    raiseKeyboard: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(surfaceOpen, raiseKeyboard) {
        // Give the surface's text field a moment to take focus, then ask for the keyboard.
        if (surfaceOpen && raiseKeyboard) { kotlinx.coroutines.delay(KEYBOARD_DELAY_MS); keyboard?.show() }
    }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { LauncherInput.editMode.value }.drop(1).collect { on ->
            android.widget.Toast.makeText(
                context,
                if (on) L10n.tr("Edit mode: arrows move the focused item, Esc to finish") else L10n.tr("Edit mode off"),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val latestCommand by rememberUpdatedState(onCommand)
    val latestMenu by rememberUpdatedState(onEmptyMenu)
    val latestCorner by rememberUpdatedState(onCornerActivate)
    val latestLabel by rememberUpdatedState(cornerLabel)
    SideEffectOverlay(overlayOpen)
    androidx.compose.runtime.SideEffect { LauncherInput.keyboardWanted.value = surfaceOpen && raiseKeyboard }
    LaunchedEffect(Unit) {
        LauncherInput.commands.collect { c ->
            when (c) {
                LauncherCommand.ShortcutSheet -> LauncherInput.sheetOpen.value = !LauncherInput.sheetOpen.value
                LauncherCommand.Dismiss -> when {
                    LauncherInput.sheetOpen.value -> LauncherInput.sheetOpen.value = false
                    LauncherInput.editMode.value -> LauncherInput.editMode.value = false
                    else -> back?.onBackPressed()
                }
                LauncherCommand.EditMode -> LauncherInput.editMode.value = EditModeState.next(LauncherInput.editMode.value, c)
                LauncherCommand.CornerTopLeft -> latestCorner(HotCornerId.TopLeft)
                LauncherCommand.CornerTopRight -> latestCorner(HotCornerId.TopRight)
                LauncherCommand.CornerBottomLeft -> latestCorner(HotCornerId.BottomLeft)
                LauncherCommand.CornerBottomRight -> latestCorner(HotCornerId.BottomRight)
                else -> latestCommand(c)
            }
        }
    }
    LaunchedEffect(Unit) { LauncherInput.emptyMenu.collect { latestMenu() } }
    DisposableEffect(hotCornerDwellMs) {
        LauncherInput.hotCorner.value = HotCornerBinding(hotCornerDwellMs, { latestLabel(it) }, { latestCorner(it) })
        onDispose { LauncherInput.hotCorner.value = null }
    }
    if (LauncherInput.sheetOpen.value) ShortcutSheet(LauncherInput.registry) { LauncherInput.sheetOpen.value = false }
}

@Composable
private fun SideEffectOverlay(open: Boolean) {
    androidx.compose.runtime.SideEffect { LauncherInput.overlayOpen = open }
}

private const val KEYBOARD_DELAY_MS = 250L
