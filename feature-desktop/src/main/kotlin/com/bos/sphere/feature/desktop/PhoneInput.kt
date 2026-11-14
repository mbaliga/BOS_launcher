package com.bos.sphere.feature.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.min

/** What the phone's trackpad and keyboard panel sends to the launcher's own desktop surface (EXT-10). */
sealed interface InputCommand {
    data class Move(val dx: Float, val dy: Float) : InputCommand
    data object Click : InputCommand
    data object SecondaryClick : InputCommand
    data class Scroll(val dy: Float) : InputCommand
    data class Text(val text: String) : InputCommand
    data class Key(val key: PanelKey) : InputCommand
}

enum class PanelKey { Enter, Escape, Backspace, Up, Down, Left, Right, Tab }

/**
 * In-process channel between the phone panel and the desktop surface. It only exists inside the launcher:
 * no accessibility service, no injection into other apps. Commands are dropped unless a desktop surface is
 * focused, which is what the panel's disabled state reflects.
 */
object DesktopInputBus {
    private val _commands = MutableSharedFlow<InputCommand>(extraBufferCapacity = 128)
    val commands: SharedFlow<InputCommand> = _commands
    private val _focused = MutableStateFlow(false)
    val focused: StateFlow<Boolean> = _focused

    fun setFocused(value: Boolean) { _focused.value = value }

    /** Returns false (and drops the command) when no desktop surface is focused. */
    fun send(cmd: InputCommand): Boolean = _focused.value && _commands.tryEmit(cmd)
}

/** Virtual pointer of the desktop surface. Pure math, clamped to the surface. */
class CursorModel(var width: Float, var height: Float, private val baseGain: Float = 1.4f) {
    var x = width / 2f
        private set
    var y = height / 2f
        private set

    fun resize(w: Float, h: Float) { width = w; height = h; x = x.coerceIn(0f, w); y = y.coerceIn(0f, h) }

    /** Moves by the finger delta with gentle acceleration for fast strokes. */
    fun move(dx: Float, dy: Float) {
        val g = gain(hypot(dx, dy))
        x = (x + dx * g).coerceIn(0f, width)
        y = (y + dy * g).coerceIn(0f, height)
    }

    fun gain(speed: Float): Float = baseGain * (1f + min(speed / 24f, 1.5f))
}

/** Which item sits under the cursor, topmost (last) first. */
object HitTest {
    fun itemAt(x: Float, y: Float, items: List<Pair<String, FRect>>): String? =
        items.lastOrNull { (_, r) -> x >= r.left && x <= r.right && y >= r.top && y <= r.bottom }?.first
}
