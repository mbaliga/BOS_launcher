package com.bos.sphere.core.input

/** Android key codes written out so routing is JVM-testable. */
object Keys {
    const val DPAD_UP = 19; const val DPAD_DOWN = 20; const val DPAD_LEFT = 21; const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23; const val ENTER = 66; const val NUMPAD_ENTER = 160
    const val TAB = 61; const val ESCAPE = 111; const val BACK = 4; const val MENU = 82
    const val F10 = 131 + 9; const val F1 = 131
    const val BUTTON_A = 96; const val BUTTON_B = 97; const val BUTTON_X = 99; const val BUTTON_Y = 100
    const val BUTTON_L1 = 102; const val BUTTON_R1 = 103; const val BUTTON_START = 108; const val BUTTON_SELECT = 109
    const val PAGE_UP = 92; const val PAGE_DOWN = 93; const val MOVE_HOME = 122; const val MOVE_END = 123
    const val META_LEFT = 117; const val META_RIGHT = 118
    const val SPACE = 62; const val DEL = 67; const val SLASH = 76
    fun isLetter(code: Int) = code in 29..54
    fun letterOf(code: Int): Char? = if (isLetter(code)) ('A' + (code - 29)) else null
    fun isModifier(code: Int) = code in 57..60 || code in 113..118 || code == 59
}

/**
 * Game-controller buttons become the keys the surfaces already understand (INP-20): D-pad stays arrows,
 * A is Enter, B is back/Esc, X opens the menu, Start opens the palette like its shortcut.
 */
object ControllerKeys {
    fun isController(keyCode: Int) = keyCode in 96..110
    /** The key a controller button stands for, or the same code when it is not a controller button. */
    fun translate(keyCode: Int): Int = when (keyCode) {
        Keys.BUTTON_A -> Keys.DPAD_CENTER
        Keys.BUTTON_B -> Keys.ESCAPE
        Keys.BUTTON_X -> Keys.MENU
        Keys.BUTTON_L1 -> Keys.TAB       // shoulder buttons step through the linear order
        Keys.BUTTON_R1 -> Keys.TAB
        else -> keyCode
    }
    /** R1 and L1 differ by direction: L1 is Shift+Tab. */
    fun shiftFor(keyCode: Int) = keyCode == Keys.BUTTON_L1
}

/** What a key press means to the launcher. */
sealed interface KeyRoute {
    data class Command(val command: LauncherCommand) : KeyRoute
    data class Move(val dir: FocusDir) : KeyRoute
    data class Tab(val backwards: Boolean) : KeyRoute
    data object Activate : KeyRoute
    data object Menu : KeyRoute
    data object None : KeyRoute
}

/**
 * Turns a raw key press into a [KeyRoute]. Shortcuts from the registry win; the system's Meta
 * shortcuts are never claimed (they belong to the OS and the launcher must not swallow them).
 */
class KeyRouter(private val registry: ShortcutRegistry) {
    fun route(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, meta: Boolean = false): KeyRoute {
        if (Keys.isModifier(keyCode)) return KeyRoute.None
        if (meta) return KeyRoute.None
        val code = ControllerKeys.translate(keyCode)
        val shiftNow = shift || ControllerKeys.shiftFor(keyCode)
        registry.commandFor(KeyChord(code, ctrl, alt, shiftNow, meta))?.let { return KeyRoute.Command(it) }
        if (!ctrl && !alt) when (code) {
            Keys.DPAD_UP -> return KeyRoute.Move(FocusDir.Up)
            Keys.DPAD_DOWN -> return KeyRoute.Move(FocusDir.Down)
            Keys.DPAD_LEFT -> return KeyRoute.Move(FocusDir.Left)
            Keys.DPAD_RIGHT -> return KeyRoute.Move(FocusDir.Right)
            Keys.TAB -> return KeyRoute.Tab(shiftNow)
            Keys.DPAD_CENTER, Keys.ENTER, Keys.NUMPAD_ENTER -> return KeyRoute.Activate
            Keys.MENU -> return KeyRoute.Menu
        }
        if (code == Keys.F10 && shiftNow) return KeyRoute.Menu
        return KeyRoute.None
    }
}

/** Human labels for chords, used by the shortcut sheet and the system shortcut helper. */
object KeyLabels {
    private val named = mapOf(
        Keys.F1 to "F1", Keys.ESCAPE to "Esc", Keys.TAB to "Tab", Keys.ENTER to "Enter", Keys.SPACE to "Space",
        55 to ",", 56 to ".", 76 to "/", 71 to "[", 72 to "]",
        Keys.DPAD_LEFT to "Left", Keys.DPAD_RIGHT to "Right", Keys.DPAD_UP to "Up", Keys.DPAD_DOWN to "Down",
    )

    fun keyName(code: Int): String = named[code]
        ?: Keys.letterOf(code)?.toString()
        ?: (if (code in 7..16) (code - 7).toString() else "Key $code")

    fun describe(c: KeyChord?): String = if (c == null) "Not set" else buildList {
        if (c.ctrl) add("Ctrl"); if (c.alt) add("Alt"); if (c.shift) add("Shift"); if (c.meta) add("Meta")
        add(keyName(c.keyCode))
    }.joinToString(" + ")

    /** The sentence the shortcut sheet shows about the system's own Meta shortcuts. */
    const val META_NOTE = "Shortcuts that use the Meta (Windows or Command) key belong to the system. Liftoff never claims them."
}
