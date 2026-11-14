package com.bos.sphere.feature.grid

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.InputModeTracker

/** Glue between Compose key events and the pure [RadarKeyboard] / [GroundControlNav] models. */
internal object RadarKeyMap {
    /** Soft-keyboard key events carry the virtual device id (-1); only a real device (id > 0) makes the input mode Keyboard. */
    fun isHardwareDevice(deviceId: Int): Boolean = deviceId > 0

    /** Plain navigation keys only: anything with Ctrl, Alt or Meta is a shortcut and is left alone. */
    fun radarKey(ev: KeyEvent): RKey? {
        if (ev.type != KeyEventType.KeyDown) return null
        if (ev.isCtrlPressed || ev.isAltPressed || ev.isMetaPressed) return null
        return when (ev.key) {
            Key.DirectionUp -> RKey.Up
            Key.DirectionDown -> RKey.Down
            Key.DirectionLeft -> RKey.Left
            Key.DirectionRight -> RKey.Right
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA -> RKey.Enter
            Key.Tab -> if (ev.isShiftPressed) RKey.ShiftTab else RKey.Tab
            Key.Escape, Key.ButtonB -> RKey.Escape
            Key.Menu -> RKey.Menu
            Key.F10 -> if (ev.isShiftPressed) RKey.Menu else null
            else -> null
        }
    }

    fun groundKey(ev: KeyEvent): GKey? {
        if (ev.type != KeyEventType.KeyDown) return null
        if (ev.isCtrlPressed || ev.isAltPressed || ev.isMetaPressed) return null
        return when (ev.key) {
            Key.DirectionUp -> GKey.Up
            Key.DirectionDown -> GKey.Down
            Key.DirectionLeft -> GKey.Left
            Key.DirectionRight -> GKey.Right
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA -> GKey.Enter
            Key.Escape, Key.ButtonB -> GKey.Escape
            else -> null
        }
    }

    fun hardware(ev: KeyEvent): Boolean = isHardwareDevice(ev.nativeKeyEvent.deviceId)
}

internal fun tileKind(t: Tile): ResultKind = when (t) {
    is Tile.App -> ResultKind.App
    is Tile.Person -> ResultKind.Person
    is Tile.Hit -> if (t.hit.actionId != null) ResultKind.Action else ResultKind.File
    is Tile.Web -> ResultKind.Web
    is Tile.Setting -> ResultKind.Setting
    else -> ResultKind.Other
}

internal fun appInfoIntent(pkg: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Feeds an [InputModeTracker] from the pointer events a surface sees (a mouse is Pointer, a finger is Touch). */
internal fun Modifier.trackPointerMode(tracker: InputModeTracker): Modifier = pointerInput(tracker) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val type = e.changes.firstOrNull()?.type ?: continue
            when (type.toInputMode()) {
                InputMode.Pointer -> tracker.onPointer()
                else -> tracker.onTouch()
            }
        }
    }
}

/** The action list of the keyboard-selected result (PAL-07, PAL-08): Tab or Right opens it, Up/Down and Enter drive it, a tap works too. */
@Composable
internal fun RadarActionList(entries: List<ResultMenuEntry>, index: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .background(Color(0xF2101018), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp),
    ) {
        entries.forEachIndexed { i, e ->
            Text(
                L10n.tr(e.label), color = Color.White, fontSize = 16.sp, fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (i == index) Color(0x44B9A8FF) else Color.Transparent)
                    .clickable { onPick(i) }
                    .semantics { contentDescription = e.label; selected = i == index }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
