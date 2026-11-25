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
    OpenPalette("Open Radar"), OpenDrawer("Open all apps"), OpenSettings("Open Settings"), EditMode("Edit home"),
    OpenLaunchpad("Open Comms"), OpenPuck("Open the puck"), ShortcutSheet("Show shortcuts"),
    NextWorkspace("Next profile"), PreviousWorkspace("Previous profile"), FocusDock("Focus the dock"),
    Dock1("Dock item 1"), Dock2("Dock item 2"), Dock3("Dock item 3"), Dock4("Dock item 4"), Dock5("Dock item 5"),
    Dock6("Dock item 6"), Dock7("Dock item 7"), Dock8("Dock item 8"), Dock9("Dock item 9"),
    CornerTopLeft("Top-left corner"), CornerTopRight("Top-right corner"), CornerBottomLeft("Bottom-left corner"), CornerBottomRight("Bottom-right corner"),
    /** STR-14: open the Recents panel (the right-hand card). */
    OpenRecents("Open recents"),
    Dismiss("Dismiss"),
}

data class ShortcutDef(val command: LauncherCommand, val default: KeyChord?)

/**
 * The one registry every shortcut is declared in. Bindings can be remapped; a binding that clashes with another
 * command is refused and the clash is named. Android key codes are used as plain ints so this stays JVM-testable.
 */
class ShortcutRegistry(private val defs: List<ShortcutDef> = DefaultShortcuts.all) {
    private val overrides = mutableMapOf<LauncherCommand, KeyChord?>()

    @Synchronized fun binding(c: LauncherCommand): KeyChord? =
        if (c in overrides) overrides[c] else defs.firstOrNull { it.command == c }?.default

    @Synchronized fun all(): List<Pair<LauncherCommand, KeyChord?>> = defs.map { it.command to binding(it.command) }

    @Synchronized fun commandFor(chord: KeyChord): LauncherCommand? = all().firstOrNull { it.second == chord }?.first

    /** The command that already uses [chord], other than [except]. */
    @Synchronized fun conflictFor(chord: KeyChord, except: LauncherCommand): LauncherCommand? = all().firstOrNull { it.second == chord && it.first != except }?.first

    sealed interface Rebind { data object Ok : Rebind; data class Conflict(val with: LauncherCommand) : Rebind }

    @Synchronized fun rebind(c: LauncherCommand, chord: KeyChord?): Rebind {
        if (chord != null) conflictFor(chord, c)?.let { return Rebind.Conflict(it) }
        overrides[c] = chord
        return Rebind.Ok
    }

    @Synchronized fun reset(c: LauncherCommand) { overrides.remove(c) }

    /** True when the user changed (or unbound) [c]. */
    @Synchronized fun isOverridden(c: LauncherCommand): Boolean = c in overrides

    /** The factory chord of [c]. */
    fun defaultFor(c: LauncherCommand): KeyChord? = defs.firstOrNull { it.command == c }?.default

    /** Only the user's changes, for the exported configuration. */
    @Synchronized fun export(): String = overrides.entries.joinToString(";") { "${it.key.name}=${it.value?.encode() ?: "-"}" }

    @Synchronized fun import(raw: String) {
        val parsed = LinkedHashMap<LauncherCommand, KeyChord?>()
        raw.split(";").filter { it.isNotBlank() }.forEach { part ->
            val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val cmd = runCatching { LauncherCommand.valueOf(k) }.getOrNull() ?: return@forEach
            // A part that does not parse is ignored; it must never unbind a command by accident.
            parsed[cmd] = if (v == "-") null else (KeyChord.decode(v) ?: return@forEach)
        }
        // A hand-edited or merged file must not bind two commands to one chord (INP-10). A command nobody wrote a part for
        // keeps its binding; between two parts the earlier one wins. A dropped part falls back to its default chord, and if
        // that is taken as well (by a kept override or another command's default) the command is left unbound, so after the
        // import no two commands ever share a chord. Each command changes at most twice, so the loop ends.
        val kept = LinkedHashMap(parsed)
        fun effectiveOf(c: LauncherCommand): KeyChord? = if (c in kept) kept[c] else defs.firstOrNull { it.command == c }?.default
        while (true) {
            val clash = defs.map { it.command }.filter { effectiveOf(it) != null }.groupBy { effectiveOf(it) }.values.firstOrNull { it.size > 1 } ?: break
            val order = kept.keys.toList()
            val winner = clash.firstOrNull { it !in kept } ?: clash.minByOrNull { order.indexOf(it) }!!
            val losers = clash.filter { it != winner && it in kept }
            if (losers.isEmpty()) break // only possible with duplicate default chords in the definitions themselves
            for (c in losers) {
                kept.remove(c)
                val back = effectiveOf(c)
                if (back != null && defs.any { it.command != c && effectiveOf(it.command) == back }) kept[c] = null
            }
        }
        overrides.clear()
        overrides.putAll(kept)
    }
}

object DefaultShortcuts {
    // Android KeyEvent codes, written out so the registry has no Android dependency.
    private const val K_SLASH = 76; private const val K_D = 32; private const val K_COMMA = 55; private const val K_E = 33
    private const val K_N = 42; private const val K_P = 44; private const val K_H = 36; private const val K_PERIOD = 56
    private const val K_ESC = 111; private const val K_1 = 8; private const val K_LB = 71; private const val K_RB = 72
    private const val K_TAB = 61; private const val K_L = 40; private const val K_F1 = 131; private const val K_Q = 45
    private const val K_W = 51; private const val K_A = 29; private const val K_S = 47; private const val K_R = 46

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
        add(ShortcutDef(LauncherCommand.OpenRecents, KeyChord(K_R, ctrl = true)))
        add(ShortcutDef(LauncherCommand.Dismiss, KeyChord(K_ESC)))
    }
}

// ---------------------------------------------------------------- one context-menu model (INP-03)

enum class MenuAction(val label: String) {
    Open("Open"), OpenOnDisplay("Open on another display"), OpenBeside("Open beside"), AppInfo("App info"),
    AddToDock("Add to dock"), AddToWorkspace("Add to a profile"), Uninstall("Uninstall"),
    Rename("Rename"), Remove("Remove from home"), MoveToFolder("Move to folder"),
}

/**
 * Rows only the home's app menu has (the palette's action list does not). They live in their own enum so the palette,
 * which maps every [MenuAction] to an engine action by name, is not affected (INP-03).
 */
enum class HomeMenuAction(val label: String) {
    Tags("Tags"), AddToHome("Add to home"), IconColour("Icon colour"), Hide("Hide"), Unhide("Unhide"), HomeSettings("Home settings"),
}

/**
 * HOM-21: the on/off rows of the home's long-press sheet. Each is a row with a state, which is the new entry kind
 * [AppMenuEntry.Toggle]; the page rows (align, make home) are states of the page and use the same kind.
 */
enum class SheetToggle(val label: String) {
    Time("Time"), Weather("Weather"), Battery("Battery %"),
    AlignTop("Align with top"), AlignBottom("Align with bottom"), MakeHome("Make this home"),
}

/** HOM-21: the plain action rows of the home's long-press sheet. */
enum class HomeSheetAction(val label: String) {
    EditHome("Edit home"), Panels("Files, usage and timeline"), Wallpaper("Wallpaper"), GroundControl("Settings") /* wave 57: the name the UI uses; "ground control" stays a hidden search word */,
    HomeItems("Widgets, folders and icons"), AddPage("Add page"), DeletePage("Delete page"),
}

/** One row of the home's app menu: a shared [MenuAction] or a [HomeMenuAction]; the home sheet adds toggles, choices and its own actions. */
sealed interface AppMenuEntry {
    val label: String
    data class Shared(val entry: MenuEntry) : AppMenuEntry { override val label get() = entry.action.label }
    data class Home(val action: HomeMenuAction) : AppMenuEntry { override val label get() = action.label }
    /** HOM-21: a row that is switched on or off. */
    data class Toggle(val kind: SheetToggle, val on: Boolean) : AppMenuEntry { override val label get() = kind.label }
    /** HOM-21: one option of a group of which one is selected (the page transition). */
    data class Choice(val group: String, val option: String, val selected: Boolean) : AppMenuEntry { override val label get() = option }
    /** HOM-21: a plain action row of the home sheet. */
    data class Sheet(val action: HomeSheetAction, val danger: Boolean = false) : AppMenuEntry { override val label get() = action.label }
}

/** What the home sheet can offer right now; the sheet is derived from this alone ([ContextMenus.forHomeSheet]). */
data class HomeSheetContext(
    val timeOn: Boolean = true,
    val weatherOn: Boolean = false,
    val batteryOn: Boolean = false,
    /** null on homes without pages: the page rows are not offered. */
    val page: Int? = null,
    val pageCount: Int = 1,
    val alignTop: Boolean = false,
    val alignBottom: Boolean = false,
    val isHomePage: Boolean = false,
    /** The page transitions with the selected one marked; empty hides the group. */
    val transitions: List<Pair<String, Boolean>> = emptyList(),
    val canEdit: Boolean = true,
    val hasPanels: Boolean = true,
    val hasHomeItems: Boolean = true,
)

data class MenuEntry(val action: MenuAction, val enabled: Boolean = true)

/** What the item can do right now; the menu is derived from this alone. */
data class MenuContext(
    val isApp: Boolean = true,
    val canUninstall: Boolean = true,
    val otherDisplays: Int = 0,
    val adjacentSupported: Boolean = false,
    val onHome: Boolean = false,
    val inDock: Boolean = false,
    /** Whether "Move to folder" is offered; folders on the home are filed from the menu only where they exist. */
    val canMoveToFolder: Boolean = onHome,
    /** Tags (HOM-05) apply to apps. */
    val canTag: Boolean = false,
    /** null: the home has no pages to add to; true: the app is on the home; false: it is not. */
    val homeMembership: Boolean? = null,
    val canPickIconColour: Boolean = false,
    /** null: hiding is not offered; true: the app is hidden now; false: it is visible. */
    val hidden: Boolean? = null,
    val canOpenHomeSettings: Boolean = false,
    /** False where the launcher has no per-workspace app membership to add to (the app menu says so instead of showing a dead row). */
    val workspacesSupported: Boolean = true,
)

/** Long-press, right-click and the Menu key all call this, so the three can never differ. */
object ContextMenus {
    fun forItem(c: MenuContext): List<MenuEntry> = buildList {
        add(MenuEntry(MenuAction.Open))
        if (c.otherDisplays > 0) add(MenuEntry(MenuAction.OpenOnDisplay))
        if (c.adjacentSupported) add(MenuEntry(MenuAction.OpenBeside))
        if (c.isApp) add(MenuEntry(MenuAction.AppInfo))
        if (!c.inDock) add(MenuEntry(MenuAction.AddToDock))
        if (c.workspacesSupported) add(MenuEntry(MenuAction.AddToWorkspace))
        if (c.onHome) { if (c.canMoveToFolder) add(MenuEntry(MenuAction.MoveToFolder)); add(MenuEntry(MenuAction.Remove)) }
        if (c.isApp && c.canUninstall) add(MenuEntry(MenuAction.Uninstall))
    }

    /**
     * The home's app menu: the shared entries of [forItem] with the home-only rows (tags, add to home, icon colour,
     * hide, home settings) placed before the closing group (move, remove, uninstall). Every trigger uses this one list.
     */
    fun forApp(c: MenuContext): List<AppMenuEntry> {
        val shared = forItem(c)
        val closing = setOf(MenuAction.MoveToFolder, MenuAction.Remove, MenuAction.Uninstall)
        val head = shared.filter { it.action !in closing }
        val tail = shared.filter { it.action in closing }.toMutableList()
        // On the paged home "remove from home" is offered for an app that is on a page, without the folder entry.
        if (c.homeMembership == true && tail.none { it.action == MenuAction.Remove }) tail.add(0, MenuEntry(MenuAction.Remove))
        return buildList {
            head.forEach { add(AppMenuEntry.Shared(it)) }
            if (c.canTag) add(AppMenuEntry.Home(HomeMenuAction.Tags))
            if (c.homeMembership == false) add(AppMenuEntry.Home(HomeMenuAction.AddToHome))
            if (c.canPickIconColour) add(AppMenuEntry.Home(HomeMenuAction.IconColour))
            if (c.hidden == false) add(AppMenuEntry.Home(HomeMenuAction.Hide))
            if (c.hidden == true) add(AppMenuEntry.Home(HomeMenuAction.Unhide))
            if (c.canOpenHomeSettings) add(AppMenuEntry.Home(HomeMenuAction.HomeSettings))
            tail.forEach { add(AppMenuEntry.Shared(it)) }
        }
    }

    const val TRANSITION_GROUP = "Transition"

    /**
     * HOM-21: the rows of the home's long-press sheet: the three switches people use most, this page's rows (align, make
     * home, transition, add and delete page) when the home has pages, then the actions. The sheet draws exactly this list,
     * and a touch long-press, a secondary click and the Menu key on empty home space all ask for it.
     */
    fun forHomeSheet(c: HomeSheetContext): List<AppMenuEntry> = buildList {
        add(AppMenuEntry.Toggle(SheetToggle.Time, c.timeOn))
        add(AppMenuEntry.Toggle(SheetToggle.Weather, c.weatherOn))
        add(AppMenuEntry.Toggle(SheetToggle.Battery, c.batteryOn))
        if (c.page != null) {
            add(AppMenuEntry.Toggle(SheetToggle.AlignTop, c.alignTop))
            add(AppMenuEntry.Toggle(SheetToggle.AlignBottom, c.alignBottom))
            add(AppMenuEntry.Toggle(SheetToggle.MakeHome, c.isHomePage))
            c.transitions.forEach { (name, selected) -> add(AppMenuEntry.Choice(TRANSITION_GROUP, name, selected)) }
            add(AppMenuEntry.Sheet(HomeSheetAction.AddPage))
            if (c.pageCount > 1) add(AppMenuEntry.Sheet(HomeSheetAction.DeletePage, danger = true))
        }
        if (c.canEdit) add(AppMenuEntry.Sheet(HomeSheetAction.EditHome))
        if (c.hasPanels) add(AppMenuEntry.Sheet(HomeSheetAction.Panels))
        add(AppMenuEntry.Sheet(HomeSheetAction.Wallpaper))
        add(AppMenuEntry.Sheet(HomeSheetAction.GroundControl))
        if (c.hasHomeItems) add(AppMenuEntry.Sheet(HomeSheetAction.HomeItems))
    }

    fun onLongPressHome(c: HomeSheetContext) = forHomeSheet(c)
    fun onSecondaryClickHome(c: HomeSheetContext) = forHomeSheet(c)
    fun onMenuKeyHome(c: HomeSheetContext) = forHomeSheet(c)

    fun onLongPress(c: MenuContext) = forItem(c)
    fun onSecondaryClick(c: MenuContext) = forItem(c)
    fun onMenuKey(c: MenuContext) = forItem(c)
}
