package com.bos.sphere.core.input

/**
 * INP-14 (Could, elevated tier only, ELV-01): shortcuts that work while another app has focus.
 *
 * The core build has no route to this: it ships no accessibility service and no hidden API, so a key press that another
 * app receives never reaches the launcher. This file is only the seam the elevated flavour plugs into and the rule the
 * core build follows when it is absent: the shortcut keeps working inside the launcher (INP-05) and is simply not
 * global. The core never calls a provider without asking [GlobalShortcuts.availability] first.
 */
interface GlobalShortcutProvider {
    /** True only when the person has switched the elevated route on and the platform allows it. */
    val active: Boolean
    /** Starts delivering [chords] while other apps are focused; each hit calls [onCommand]. Returns false when it cannot. */
    fun register(chords: Map<KeyChord, LauncherCommand>, onCommand: (LauncherCommand) -> Unit): Boolean
    fun unregister()
}

/** What the core flavour has: no provider. */
object NoGlobalShortcuts : GlobalShortcutProvider {
    override val active = false
    override fun register(chords: Map<KeyChord, LauncherCommand>, onCommand: (LauncherCommand) -> Unit) = false
    override fun unregister() = Unit
}

object GlobalShortcuts {
    enum class Availability { Available, NotInThisBuild, SwitchedOff }

    /** Commands that make sense with another app in front. Navigation and edit keys stay inside the launcher. */
    val eligible: Set<LauncherCommand> = setOf(
        LauncherCommand.OpenPalette, LauncherCommand.OpenDrawer, LauncherCommand.OpenLaunchpad, LauncherCommand.OpenPuck,
        LauncherCommand.OpenSettings, LauncherCommand.NextWorkspace, LauncherCommand.PreviousWorkspace,
        LauncherCommand.Dock1, LauncherCommand.Dock2, LauncherCommand.Dock3, LauncherCommand.Dock4, LauncherCommand.Dock5,
        LauncherCommand.Dock6, LauncherCommand.Dock7, LauncherCommand.Dock8, LauncherCommand.Dock9,
    )

    fun availability(provider: GlobalShortcutProvider, built: Boolean): Availability = when {
        !built -> Availability.NotInThisBuild
        !provider.active -> Availability.SwitchedOff
        else -> Availability.Available
    }

    /** The chords to hand a provider: the registry's current bindings for [commands], never a Meta chord (the system's). */
    fun chords(registry: ShortcutRegistry, commands: Set<LauncherCommand>): Map<KeyChord, LauncherCommand> =
        registry.all().filter { (c, k) -> c in commands && c in eligible && k != null && !k.meta }.associate { (c, k) -> k!! to c }

    /** The one-line explanation shown beside the setting in the capability report. */
    fun explain(a: Availability): String = when (a) {
        Availability.Available -> "Shortcuts also work while other apps are open."
        Availability.NotInThisBuild -> "Not in this build. Shortcuts work while Liftoff is open."
        Availability.SwitchedOff -> "Off. Shortcuts work while Liftoff is open."
    }
}
