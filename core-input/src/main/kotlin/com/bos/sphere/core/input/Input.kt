package com.bos.sphere.core.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The active input, decided by the last event (INP-06). It changes affordances only, never focus or state. */
enum class InputMode { Touch, Keyboard, Pointer }

class InputModeTracker(initial: InputMode = InputMode.Touch) {
    private val _mode = MutableStateFlow(initial)
    val mode: StateFlow<InputMode> = _mode

    fun onTouch() { _mode.value = InputMode.Touch }
    fun onKey() { _mode.value = InputMode.Keyboard }
    /** Hover and mouse buttons count as pointer; a touchscreen finger never does. */
    fun onPointer() { _mode.value = InputMode.Pointer }

    /** Focus rings are drawn only in keyboard mode; hover affordances only in pointer mode. */
    val showFocusRing get() = _mode.value == InputMode.Keyboard
    val showHover get() = _mode.value == InputMode.Pointer
}

// ---------------------------------------------------------------- shortcuts (INP-04, INP-05, INP-10)

data class KeyChord(val keyCode: Int, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false, val meta: Boolean = false) {
    fun encode(): String = listOf(if (ctrl) "C" else "", if (alt) "A" else "", if (shift) "S" else "", if (meta) "M" else "").joinToString("") + ":" + keyCode
    companion object {
        fun decode(s: String): KeyChord? {
            val (mods, code) = s.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
            return KeyChord(code.toIntOrNull() ?: return null, 'C' in mods, 'A' in mods, 'S' in mods, 'M' in mods)
        }
    }
}

/** Everything a shortcut can do, matching the default set in INP-05. */
enum class LauncherCommand(val label: String) {
    OpenPalette("Open Radar"), OpenDrawer("Open all apps"), OpenSettings("Open Ground Control"), EditMode("Edit home"),
    OpenLaunchpad("Open Launchpad"), OpenPuck("Open the puck"), ShortcutSheet("Show shortcuts"),
    NextWorkspace("Next workspace"), PreviousWorkspace("Previous workspace"), FocusDock("Focus the dock"),
    Dock1("Dock item 1"), Dock2("Dock item 2"), Dock3("Dock item 3"), Dock4("Dock item 4"), Dock5("Dock item 5"),
    Dock6("Dock item 6"), Dock7("Dock item 7"), Dock8("Dock item 8"), Dock9("Dock item 9"),
    CornerTopLeft("Top-left corner"), CornerTopRight("Top-right corner"), CornerBottomLeft("Bottom-left corner"), CornerBottomRight("Bottom-right corner"),
    Dismiss("Dismiss"),
}

data class ShortcutDef(val command: LauncherCommand, val default: KeyChord?)

/**
 * The one registry every shortcut is declared in. Bindings can be remapped; a binding that clashes with another
 * command is refused and the clash is named. Android key codes are used as plain ints so this stays JVM-testable.
 */
class ShortcutRegistry(private val defs: List<ShortcutDef> = DefaultShortcuts.all) {
    private val overrides = mutableMapOf<LauncherCommand, KeyChord?>()

    fun binding(c: LauncherCommand): KeyChord? =
        if (c in overrides) overrides[c] else defs.firstOrNull { it.command == c }?.default

    fun all(): List<Pair<LauncherCommand, KeyChord?>> = defs.map { it.command to binding(it.command) }

    fun commandFor(chord: KeyChord): LauncherCommand? = all().firstOrNull { it.second == chord }?.first

    /** The command that already uses [chord], other than [except]. */
    fun conflictFor(chord: KeyChord, except: LauncherCommand): LauncherCommand? = all().firstOrNull { it.second == chord && it.first != except }?.first

    sealed interface Rebind { data object Ok : Rebind; data class Conflict(val with: LauncherCommand) : Rebind }

    fun rebind(c: LauncherCommand, chord: KeyChord?): Rebind {
        if (chord != null) conflictFor(chord, c)?.let { return Rebind.Conflict(it) }
        overrides[c] = chord
        return Rebind.Ok
    }

    fun reset(c: LauncherCommand) { overrides.remove(c) }

    /** Only the user's changes, for the exported configuration. */
    fun export(): String = overrides.entries.joinToString(";") { "${it.key.name}=${it.value?.encode() ?: "-"}" }

    fun import(raw: String) {
        overrides.clear()
        raw.split(";").filter { it.isNotBlank() }.forEach { part ->
            val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val cmd = runCatching { LauncherCommand.valueOf(k) }.getOrNull() ?: return@forEach
            overrides[cmd] = if (v == "-") null else KeyChord.decode(v)
        }
    }
}

object DefaultShortcuts {
    // Android KeyEvent codes, written out so the registry has no Android dependency.
    private const val K_SLASH = 76; private const val K_D = 32; private const val K_COMMA = 55; private const val K_E = 33
    private const val K_N = 42; private const val K_P = 44; private const val K_H = 36; private const val K_PERIOD = 56
    private const val K_ESC = 111; private const val K_1 = 8; private const val K_LB = 71; private const val K_RB = 72
    private const val K_TAB = 61; private const val K_L = 40; private const val K_F1 = 131; private const val K_Q = 45
    private const val K_W = 51; private const val K_A = 29; private const val K_S = 47

    val all: List<ShortcutDef> = buildList {
        add(ShortcutDef(LauncherCommand.OpenPalette, KeyChord(K_SLASH, ctrl = true)))
        add(ShortcutDef(LauncherCommand.OpenDrawer, KeyChord(K_D, ctrl = true)))
        add(ShortcutDef(LauncherCommand.OpenSettings, KeyChord(K_COMMA, ctrl = true)))
        add(ShortcutDef(LauncherCommand.EditMode, KeyChord(K_E, ctrl = true)))
        add(ShortcutDef(LauncherCommand.OpenLaunchpad, KeyChord(K_N, ctrl = true)))
        add(ShortcutDef(LauncherCommand.OpenPuck, KeyChord(K_P, ctrl = true)))
        add(ShortcutDef(LauncherCommand.ShortcutSheet, KeyChord(K_F1)))
        add(ShortcutDef(LauncherCommand.NextWorkspace, KeyChord(K_RB, ctrl = true)))
        add(ShortcutDef(LauncherCommand.PreviousWorkspace, KeyChord(K_LB, ctrl = true)))
        add(ShortcutDef(LauncherCommand.FocusDock, KeyChord(K_H, ctrl = true)))
        for (n in 1..9) add(ShortcutDef(LauncherCommand.valueOf("Dock$n"), KeyChord(K_1 + n - 1, alt = true)))
        add(ShortcutDef(LauncherCommand.CornerTopLeft, KeyChord(K_Q, ctrl = true, shift = true)))
        add(ShortcutDef(LauncherCommand.CornerTopRight, KeyChord(K_W, ctrl = true, shift = true)))
        add(ShortcutDef(LauncherCommand.CornerBottomLeft, KeyChord(K_A, ctrl = true, shift = true)))
        add(ShortcutDef(LauncherCommand.CornerBottomRight, KeyChord(K_S, ctrl = true, shift = true)))
        add(ShortcutDef(LauncherCommand.Dismiss, KeyChord(K_ESC)))
    }
}

// ---------------------------------------------------------------- one context-menu model (INP-03)

enum class MenuAction(val label: String) {
    Open("Open"), OpenOnDisplay("Open on another display"), OpenBeside("Open beside"), AppInfo("App info"),
    AddToDock("Add to dock"), AddToWorkspace("Add to workspace"), Uninstall("Uninstall"),
    Rename("Rename"), Remove("Remove from home"), MoveToFolder("Move to folder"),
}

data class MenuEntry(val action: MenuAction, val enabled: Boolean = true)

/** What the item can do right now; the menu is derived from this alone. */
data class MenuContext(
    val isApp: Boolean = true,
    val canUninstall: Boolean = true,
    val otherDisplays: Int = 0,
    val adjacentSupported: Boolean = false,
    val onHome: Boolean = false,
    val inDock: Boolean = false,
)

/** Long-press, right-click and the Menu key all call this, so the three can never differ. */
object ContextMenus {
    fun forItem(c: MenuContext): List<MenuEntry> = buildList {
        add(MenuEntry(MenuAction.Open))
        if (c.otherDisplays > 0) add(MenuEntry(MenuAction.OpenOnDisplay))
        if (c.adjacentSupported) add(MenuEntry(MenuAction.OpenBeside))
        if (c.isApp) add(MenuEntry(MenuAction.AppInfo))
        if (!c.inDock) add(MenuEntry(MenuAction.AddToDock))
        add(MenuEntry(MenuAction.AddToWorkspace))
        if (c.onHome) { add(MenuEntry(MenuAction.MoveToFolder)); add(MenuEntry(MenuAction.Remove)) }
        if (c.isApp && c.canUninstall) add(MenuEntry(MenuAction.Uninstall))
    }

    fun onLongPress(c: MenuContext) = forItem(c)
    fun onSecondaryClick(c: MenuContext) = forItem(c)
    fun onMenuKey(c: MenuContext) = forItem(c)
}
