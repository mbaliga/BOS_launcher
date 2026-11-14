package com.bos.sphere.feature.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.L10n
import kotlin.math.abs

/**
 * Trackpad and keyboard panel for the phone (EXT-10). One finger moves, a short tap clicks, a long press is a
 * secondary click, two fingers scroll. It talks only to the launcher's own desktop surface through
 * [DesktopInputBus]; it cannot move the system pointer or type into other apps.
 */
private const val SENTINEL = "\u200B"

@Composable
fun TrackpadPanel(modifier: Modifier = Modifier) {
    val focused by DesktopInputBus.focused.collectAsState()
    var typed by remember { mutableStateOf(SENTINEL) }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (focused) L10n.tr("Controlling the desktop surface") else L10n.tr("Open the desktop surface on the external display to use the trackpad"),
            color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
        )
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (focused) 1f else 0.5f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val t0 = down.uptimeMillis
                        var moved = 0f
                        var maxPointers = 1
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.size > maxPointers) maxPointers = pressed.size
                            if (pressed.isEmpty()) {
                                val dt = ev.changes.first().uptimeMillis - t0
                                if (moved < 12f && maxPointers == 1) {
                                    if (dt > 450) DesktopInputBus.send(InputCommand.SecondaryClick)
                                    else DesktopInputBus.send(InputCommand.Click)
                                } else if (moved < 12f && maxPointers == 2) DesktopInputBus.send(InputCommand.SecondaryClick)
                                break
                            }
                            val dx = pressed.map { it.positionChange().x }.average().toFloat()
                            val dy = pressed.map { it.positionChange().y }.average().toFloat()
                            moved += abs(dx) + abs(dy)
                            if (pressed.size >= 2) DesktopInputBus.send(InputCommand.Scroll(-dy)) else DesktopInputBus.send(InputCommand.Move(dx, dy))
                            ev.changes.forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(L10n.tr("Trackpad"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Esc" to PanelKey.Escape, "Tab" to PanelKey.Tab, "⌫" to PanelKey.Backspace, "↵" to PanelKey.Enter).forEach { (label, key) ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { DesktopInputBus.send(InputCommand.Key(key)) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Text(L10n.tr("Type here"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        BasicTextField(
            value = typed,
            onValueChange = { new ->
                // Send only what was added; keep the field short so it never grows.
                if (new.length > typed.length) DesktopInputBus.send(InputCommand.Text(new.substring(typed.length)))
                else if (new.length < typed.length) DesktopInputBus.send(InputCommand.Key(PanelKey.Backspace))
                typed = SENTINEL // always keep one invisible char so Backspace still registers on an empty field
            },
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        )
    }
}
