package com.bos.sphere.core.data

/**
 * HOM-23 (wave 16): the pure model of the Glance home: what it shows, which hotspots are on, the pop-in controls timer and the
 * weather-to-effect mapping. No Android types, so every rule here is a plain unit test.
 */

/** One thing the Glance centre or top can show. */
enum class GlanceItem(val label: String) {
    Time("Time"), Date("Date"), Weather("Weather"), NowPlaying("Now playing"), Battery("Battery"),
}

/** The presets of the "Glance content" setting. Custom is any other combination of the free toggles. */
enum class GlancePreset(val label: String, val clockPart: Set<GlanceItem>?) {
    Time("Time", setOf(GlanceItem.Time)),
    TimeDate("Time + Date", setOf(GlanceItem.Time, GlanceItem.Date)),
    DateWeather("Date + Weather", setOf(GlanceItem.Date, GlanceItem.Weather)),
    TimeDateWeather("Time + Date + Weather", setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.Weather)),
    Custom("Custom", null),
}

object GlanceContent {
    /** Time + Date, and the music line (it only shows while something plays). */
    val DEFAULT: Set<GlanceItem> = setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.NowPlaying)

    private val CLOCK_PART = setOf(GlanceItem.Time, GlanceItem.Date, GlanceItem.Weather)

    /** A preset replaces the time, date and weather part and leaves Now playing and Battery as the user had them. */
    fun applyPreset(current: Set<GlanceItem>, preset: GlancePreset): Set<GlanceItem> {
        val part = preset.clockPart ?: return current
        return (current - CLOCK_PART) + part
    }

    fun presetOf(items: Set<GlanceItem>): GlancePreset {
        val part = items.filterTo(HashSet()) { it in CLOCK_PART }
        return GlancePreset.entries.firstOrNull { it.clockPart == part } ?: GlancePreset.Custom
    }

    fun toggle(items: Set<GlanceItem>, item: GlanceItem): Set<GlanceItem> = if (item in items) items - item else items + item

    fun encode(items: Set<GlanceItem>): String = GlanceItem.entries.filter { it in items }.joinToString(",") { it.name }

    /** A missing value is the default; an empty stored string is a valid "nothing" (a user may turn everything off). */
    fun decode(stored: String?): Set<GlanceItem> =
        if (stored == null) DEFAULT else stored.split(',').mapNotNullTo(LinkedHashSet()) { n -> GlanceItem.entries.firstOrNull { it.name == n } }

    /** Whether the temperature belongs next to the date, and whether the weather layer draws. */
    fun showsWeather(items: Set<GlanceItem>) = GlanceItem.Weather in items
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

/** The ambient effects of the weather layer. */
enum class GlanceWeatherEffect { ClearDay, ClearNight, Clouds, Rain, Snow, Fog, Thunder }

object GlanceWeather {
    /** WMO code and daylight to effect. Null when there is no weather: the layer is absent. */
    fun effectOf(info: WeatherInfo?, daylight: Boolean): GlanceWeatherEffect? {
        if (info == null) return null
        return when (info.code) {
            0, 1 -> if (daylight) GlanceWeatherEffect.ClearDay else GlanceWeatherEffect.ClearNight
            2, 3 -> GlanceWeatherEffect.Clouds
            45, 48 -> GlanceWeatherEffect.Fog
            in 51..67, in 80..82 -> GlanceWeatherEffect.Rain
            in 71..77, 85, 86 -> GlanceWeatherEffect.Snow
            in 95..99 -> GlanceWeatherEffect.Thunder
            else -> GlanceWeatherEffect.Clouds
        }
    }

    /** Daylight from the hour of day: a plain rule (06:00 to 19:59); no location or sun maths, nothing leaves the device. */
    fun isDaylight(hourOfDay: Int) = hourOfDay in 6..19

    /** Thunder flashes: at most one per 20 s and never above 3 Hz (a flash is one pulse, so 3 Hz is far above the cap). */
    const val FLASH_MIN_GAP_MS = 20_000L
    const val FLASH_MAX_HZ = 3

    /** True when a flash may start at [now] given the last one's start. */
    fun mayFlash(now: Long, lastFlashAt: Long) = lastFlashAt < 0 || now - lastFlashAt >= FLASH_MIN_GAP_MS

    /** One flash per period (23 s, above the 20 s floor) lasting 900 ms: a single smooth pulse, about 1 Hz, well under 3 Hz. */
    const val FLASH_PERIOD_MS = 23_000L
    const val FLASH_LENGTH_MS = 900L
    const val FLASH_PEAK = 0.10f

    /** The very soft thunder flash at [tMs] of the shared clock, 0..[FLASH_PEAK]. The first flash comes after 6 s, never at start. */
    fun flashAlpha(tMs: Long): Float {
        val phase = Math.floorMod(tMs - 6_000L, FLASH_PERIOD_MS)
        if (phase >= FLASH_LENGTH_MS) return 0f
        val x = phase.toFloat() / FLASH_LENGTH_MS
        val s = kotlin.math.sin(Math.PI * x).toFloat()
        return FLASH_PEAK * s * s
    }

    /** Whether the layer animates: never under reduced motion, while the screen is off or the surface is hidden. */
    fun animates(reducedMotion: Boolean, screenOn: Boolean, surfaceVisible: Boolean) = !reducedMotion && screenOn && surfaceVisible

    /** Strength of the layer, 0..1, from the setting. */
    fun strength(i: RadarIntensity) = if (i == RadarIntensity.Subtle) 0.55f else 1f
}

/**
 * Wave 26: the one-time reading of the removed home-landing setting. Every old landing value (Glance, "Minimal", Recents, AllApps, Selected,
 * missing or unknown) maps to Glance ON: the user's pages, folders and widgets are never touched (they live in the layout, not in the landing),
 * so a user who had Selected apps keeps those pages as the pages after Glance. The old keys are removed on the next save.
 * The old "Continuous" scroll setting becomes the Scroll mode (and counts as a choice); the old paged layout becomes Paginated.
 */
object HomeModelMigration {
    data class Result(val glanceEnabled: Boolean, val pagesModeChosen: Boolean, val pagesMode: HomeScroll)

    val RETIRED_KEYS = listOf("homeLanding", "landingChosen")

    fun resolve(storedLanding: String?, storedGlanceEnabled: Boolean?, storedPagesChosen: Boolean?, storedHomeScroll: String?): Result {
        val mode = HomeScroll.entries.firstOrNull { it.name == storedHomeScroll } ?: HomeScroll.Paged
        // Whatever the old landing was, Glance is on: the old value only decides nothing now. A stored new value always wins.
        val glance = storedGlanceEnabled ?: true
        val chosen = storedPagesChosen ?: (mode == HomeScroll.Continuous)
        return Result(glance, chosen, mode)
    }
}
