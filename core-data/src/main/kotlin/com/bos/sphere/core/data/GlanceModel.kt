package com.bos.sphere.core.data

/**
 * HOM-23 (wave 16): the pure model of the Glance home: which hotspots are on and the pop-in controls timer. No Android types, so every rule here is a plain unit test.
 */

/**
 * Wave 38 (glanceFixed): the Glance page shows no time, date or weather any more, so its content set (`glanceContent`: Time, Date, Weather, Now playing,
 * Battery) and the weather ambience strength (`glanceWeather`) are retired. The one thing that survived is the music line, now the plain `showMusic` toggle:
 * a stored `showMusic` wins; otherwise it follows the old content set (Now playing in it, or no old set at all, means on).
 */
object GlanceMusic {
    /** The retired keys: read as migration input on every load (so an import accounts for them), deleted on the next save. */
    val RETIRED_KEYS = listOf("glanceContent", "glanceWeather")

    fun resolve(storedShowMusic: Boolean?, legacyGlanceContent: String?): Boolean =
        storedShowMusic ?: (legacyGlanceContent == null || legacyGlanceContent.split(',').any { it.trim() == "NowPlaying" })
}

/** Presets of the hotspot count. */
enum class HotspotPreset(val label: String) { Four("Four"), Two("Two (bottom)"), One("One"), None("None"), Custom("Custom") }

/**
 * HOM-23, INP-16: which of the four hotspots are on. Enabled corners are stored by PHYSICAL corner of the natural portrait
 * orientation (like the corner assignments), so the whole composition rotates with the device.
 */
object GlanceHotspots {
    val ALL: Set<Corner> = Corner.entries.toSet()

    fun preset(p: HotspotPreset): Set<Corner>? = when (p) {
        HotspotPreset.Four -> ALL
        HotspotPreset.Two -> setOf(Corner.BottomLeft, Corner.BottomRight)
        HotspotPreset.One -> setOf(Corner.BottomRight)
        HotspotPreset.None -> emptySet()
        HotspotPreset.Custom -> null
    }

    fun presetOf(on: Set<Corner>): HotspotPreset =
        HotspotPreset.entries.firstOrNull { preset(it) == on } ?: HotspotPreset.Custom

    fun toggle(on: Set<Corner>, c: Corner): Set<Corner> = if (c in on) on - c else on + c

    fun encode(on: Set<Corner>): String = Corner.entries.filter { it in on }.joinToString(",") { it.name }

    /** Missing is all four; an empty string is none. */
    fun decode(stored: String?): Set<Corner> =
        if (stored == null) ALL else stored.split(',').mapNotNullTo(LinkedHashSet()) { n -> Corner.entries.firstOrNull { it.name == n } }

    /** The screen corners that draw a hotspot at [rotation] (0..3): the physical corner under each screen corner decides. */
    fun enabledOnScreen(on: Set<Corner>, rotation: Int, followDevice: Boolean): Set<Corner> =
        Corner.entries.filterTo(LinkedHashSet()) { screen ->
            val physical = if (followDevice) CornerMapping.physicalCornerAt(screen, rotation) else screen
            physical in on
        }

    /** What a hotspot runs: a single assignment, or the first item of a palette; a widget, complication or nothing is no hotspot. */
    fun itemOf(slot: CornerSlot): CornerItem? = MinimalHotspots.itemOf(slot)
}

/** How the pop-in controls timeout is clamped (setting 2 to 10 seconds, default 4). */
object GlanceControls {
    const val MIN_SECONDS = 2
    const val MAX_SECONDS = 10
    const val DEFAULT_SECONDS = 4
    fun clampSeconds(s: Int) = s.coerceIn(MIN_SECONDS, MAX_SECONDS)
}

/**
 * HOM-23: the state machine behind "tap pops the controls in, inactivity pops them out". Pure and clock-free: every call takes
 * the time. The host keeps one value, runs a timer to [hideAt] and calls [expired] when it fires.
 *
 * - A tap on the surface (not a hotspot, not the clock) with something playing shows the controls and arms the timer.
 * - Any interaction with the controls restarts the timer.
 * - While a finger is down the timer does not run; lifting restarts it.
 * - A tap outside the controls while they are shown hides them. A tap with nothing playing does nothing.
 */
data class ControlsPop(val visible: Boolean = false, val hideAt: Long = NEVER, val fingerDown: Boolean = false) {
    fun tapSurface(now: Long, playing: Boolean, hideMs: Long): ControlsPop = when {
        !playing -> ControlsPop()
        visible -> ControlsPop()
        else -> ControlsPop(visible = true, hideAt = now + hideMs)
    }

    /** A press, tap or key on a control button. */
    fun interact(now: Long, hideMs: Long): ControlsPop = if (visible) copy(hideAt = if (fingerDown) NEVER else now + hideMs) else this

    fun fingerDown(): ControlsPop = if (visible) copy(fingerDown = true, hideAt = NEVER) else this

    fun fingerUp(now: Long, hideMs: Long): ControlsPop = if (visible) copy(fingerDown = false, hideAt = now + hideMs) else this

    /** The timer fired at [now]: hide when due and no finger is down. */
    fun expired(now: Long): ControlsPop = if (visible && !fingerDown && hideAt != NEVER && now >= hideAt) ControlsPop() else this

    /** Nothing plays any more, or the surface left the screen. */
    fun dismiss(): ControlsPop = ControlsPop()

    companion object { const val NEVER = Long.MAX_VALUE }
}

/**
 * Wave 26: the one-time reading of the removed home-landing setting. Every old landing value (Glance, "Minimal", Recents, AllApps, Selected,
 * missing or unknown) maps to Glance ON: the user's pages, folders and widgets are never touched (they live in the layout, not in the landing),
 * so a user who had Selected apps keeps those pages as the pages after Glance. The old keys are removed on the next save.
 * The old "Continuous" scroll setting becomes the Scroll mode (and counts as a choice); the old paged layout becomes Paginated.
 */
object HomeModelMigration {
    data class Result(val glanceEnabled: Boolean)

    /** The keys of the removed model (home landing; wave 28: the Scroll / Paginated choice and its "chosen" flag). Deleted on the next save, read only as migration input. */
    val RETIRED_KEYS = listOf("homeLanding", "landingChosen", "homeScroll", "pagesModeChosen")

    fun resolve(storedLanding: String?, storedGlanceEnabled: Boolean?): Result =
        // Whatever the old landing was, Glance is on: the old value only decides nothing now. A stored new value always wins.
        Result(storedGlanceEnabled ?: true)

    /**
     * Wave 28 (homeUnify): the "Pages" setting from what an install stored. A stored `homePages` always wins. Otherwise: a user who chose (or had) the
     * Scroll gets Continuous, one who chose Paginated gets Paginated, one who never chose gets Paginated when the home already has pages with items
     * (an existing home never loses its pages) and Off when it has none.
     */
    fun pagesMode(storedPages: String?, storedHomeScroll: String?, storedChosen: Boolean?, hasPages: Boolean): HomePages {
        HomePages.entries.firstOrNull { it.name == storedPages }?.let { return it }
        if (storedHomeScroll == "Continuous") return HomePages.Continuous
        if (storedChosen == true) return HomePages.Paginated
        return if (hasPages) HomePages.Paginated else HomePages.Off
    }

    /** True when the home layout holds anything the user built: more than one page, or an item on the first. */
    fun hasBuiltPages(pages: List<HomePage>): Boolean = pages.size > 1 || pages.any { it.keys.isNotEmpty() }
}

/**
 * Wave 38: the Clock and status settings are gone (the clock lives on the Glance page, Analog or Digital, 12 or 24 hour). The stored keys of the removed
 * settings: clock position, layout, size, date, day, weather, the temperature unit, the status strip and the Health Connect source that only fed it.
 * `clockFormat` is KEPT (the user's 12 or 24 hour choice; System, the default, follows the device's own setting).
 */
object ClockRetired {
    val KEYS = listOf("clockPosition", "clockLayout", "clockSize", "clockDate", "clockDay", "clockWeather", "weatherF", "strip", "healthSource")
}
