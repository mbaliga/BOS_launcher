package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Wave 57 (profiles), CFG-46: the groups of settings a profile can carry. Each key is a key of the Studio schema
 * (`web/liftoff-studio/schema.json`): a `settings` key is the key of the "Export settings only" file, `gestures.X` is the
 * gesture of the `shortcuts.gestures` section, `dock.X` a field of `dock.settings`, `hapticStrength` the `launcher_haptics`
 * file. A value is held as the JSON text of what the Backup file holds at that place (so `"Dark"`, `true`, `4287527935`,
 * `0.5`, `null`); see docs/decisions/profiles.md for the table. `ProfileSettingsW57Test` checks every key against the schema.
 */
enum class ProfileGroup(val id: String, val label: String, val detail: String, val keys: List<String>) {
    Look(
        "look", "Look", "Theme, accent, font, icon style, calm style, Glance wallpaper colours",
        listOf(
            "themeMode", "accent", "uiFont", "iconShape", "iconTint", "iconPlate", "calmStyle", "calmRegular",
            "wpGlanceKind", "wpGlanceSolid", "wpGlanceFrom", "wpGlanceTo", "wpGlanceAngle",
        ),
    ),
    Home(
        "home", "Home behaviour", "Pages, hotspots, gestures, clock, keyboard, dock",
        listOf(
            "homePages", "glanceHotspots", "hotCornersOn", "hotCornerDwell", "cornerStyle",
            "clockStyle", "clockFormat", "glanceClockDate", "glanceClockSize",
            "alwaysKeyboard", "kbOpenP", "kbOpenL",
        ) + HomeGesture.entries.map { ProfileSettings.GESTURE + it.name } + listOf("dock.enabled", "dock.edge", "dock.mode"),
    ),
    Radar(
        "radar", "Radar", "Layout, background, sources",
        listOf(
            "searchLayout", "searchLayoutPinned", "searchMotion", "radarBackground", "radarIntensity",
            "searchWeb", "webEngine", "webSuggestions", "searchContacts", "searchFiles", "searchActions", "searchDeep",
        ),
    ),
    SidePanel("side", "Side panel and Comms", "Left and right side, Comms theme, Recents", listOf("leftPage", "launchpadRail", "lpTheme", "rightSide", "recentsLimit", "recentsUsage")),
    Sound("sound", "Sound and vibration", "Interface sounds, volume, vibration strength", listOf("uiSounds", "uiSoundVolume", ProfileSettings.HAPTIC)),
    ;

    companion object {
        fun forId(id: String): ProfileGroup? = entries.firstOrNull { it.id == id }
        fun ofIds(ids: Collection<String>): Set<ProfileGroup> = ids.mapNotNullTo(LinkedHashSet()) { forId(it) }
    }
}

object ProfileSettings {
    const val GESTURE = "gestures."
    const val DOCK = "dock."
    const val HAPTIC = "hapticStrength"

    /** Every key a profile can carry, in group order. */
    val ALL_KEYS: List<String> = ProfileGroup.entries.flatMap { it.keys }

    /** The keys that live in `launcher_prefs` (LauncherPrefs). */
    val PREF_KEYS: Set<String> = ALL_KEYS.filterTo(LinkedHashSet()) { !it.startsWith(GESTURE) && !it.startsWith(DOCK) && it != HAPTIC }

    fun keysOf(groups: Collection<ProfileGroup>): Set<String> = groups.flatMapTo(LinkedHashSet()) { it.keys }
    fun keysOfIds(ids: Collection<String>): Set<String> = keysOf(ProfileGroup.ofIds(ids))

    /** The JSON text of a value, as it would sit in the Backup file. Floats are written the way the settings export writes them. */
    fun text(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is Float -> JSONObject.numberToString(v.toString().toDouble())
        is Number -> JSONObject.numberToString(v)
        is Set<*> -> JSONArray(v.map { it.toString() }.sorted()).toString()
        is Collection<*> -> JSONArray(v.map { it.toString() }).toString()
        is Boolean -> v.toString()
        is JSONArray -> v.toString()
        else -> JSONObject.quote(v.toString())
    }

    /** The value of a JSON text (a set for an array, null for null). Unreadable text reads as null. */
    fun value(text: String): Any? = when (val v = runCatching { JSONTokener(text).nextValue() }.getOrNull()) {
        null, JSONObject.NULL -> null
        is JSONArray -> List(v.length()) { v.getString(it) }.toSet()
        else -> v
    }

    /**
     * A value worth keeping in a profile. The Glance wallpaper kind is carried only when it is Solid or Gradient (the owner's
     * "Glance wallpaper solid/gradient"): a picture is a file on this phone and the live kinds are not colours.
     */
    fun capturable(key: String, text: String): Boolean = key != "wpGlanceKind" || text == "\"solid\"" || text == "\"gradient\""

    /** The [PREF_KEYS] of [p] as JSON texts; a key the store leaves out (an unset calm style) reads as `null`. */
    fun readPrefs(p: LauncherPrefs): Map<String, String> {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).writeAll(p)
        val all = sp.all
        return PREF_KEYS.associateWith { k -> text(all[k]) }
    }

    /** [p] with the pref keys of [values] written over it (other keys are ignored); what the store reads back, so ranges are clamped. */
    fun writePrefs(p: LauncherPrefs, values: Map<String, String>): LauncherPrefs {
        val mine = values.filterKeys { it in PREF_KEYS }
        if (mine.isEmpty()) return p
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).writeAll(p)
        val raw = LinkedHashMap<String, Any?>(sp.all)
        mine.forEach { (k, t) -> val v = value(t); if (v == null) raw.remove(k) else raw[k] = v }
        return LauncherPrefsStore(MapSharedPreferences(raw)).prefs.value.copy(onboardingDone = p.onboardingDone, leftWidgetId = p.leftWidgetId)
    }

    fun toJson(values: Map<String, String>): JSONObject = JSONObject().apply { values.toSortedMap().forEach { (k, t) -> put(k, runCatching { JSONTokener(t).nextValue() }.getOrDefault(JSONObject.NULL)) } }

    fun fromJson(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k ->
            if (k !in ALL_KEYS) return@forEach
            val v = o.get(k)
            out[k] = if (v is JSONArray) text(List(v.length()) { v.getString(it) }.toSet()) else text(v)
        }
        return out
    }
}

/**
 * CFG-48: what a switch does to the settings, as one pure step. The rule (docs/decisions/profiles.md, "Precedence"):
 * a key the active profile carries holds the profile's value while it is active, and its global value waits in [stash];
 * every other key is global. A change made while a profile is active goes to the profile for the keys it carries
 * (they are captured on the way out, and written through while it is active) and stays global for the rest.
 */
object ProfileSwitch {
    data class Plan(
        /** What the profile being left keeps: its carried keys as they are now. */
        val leaving: Map<String, String>,
        /** The global values of the keys the entering profile carries. */
        val stash: Map<String, String>,
        /** The values to write so the live settings become the entering profile's (only the ones that change). */
        val writes: Map<String, String>,
        /** Every value after the switch, for an undo. */
        val after: Map<String, String>,
    )

    /** The global values: the live ones, except the keys the active profile carries, whose global value is in [stash]. */
    fun global(live: Map<String, String>, activeKeys: Set<String>, stash: Map<String, String>): Map<String, String> {
        val g = LinkedHashMap(live)
        for (k in activeKeys) stash[k]?.let { g[k] = it }
        return g
    }

    fun capture(live: Map<String, String>, keys: Set<String>): Map<String, String> =
        live.filter { (k, v) -> k in keys && ProfileSettings.capturable(k, v) }

    fun plan(live: Map<String, String>, leavingKeys: Set<String>, stash: Map<String, String>, enteringKeys: Set<String>, entering: Map<String, String>): Plan {
        val g = global(live, leavingKeys, stash)
        val target = LinkedHashMap(g)
        for (k in enteringKeys) entering[k]?.let { target[k] = it }
        return Plan(
            leaving = capture(live, leavingKeys),
            stash = g.filterKeys { it in enteringKeys },
            writes = target.filter { (k, v) -> live[k] != v },
            after = target,
        )
    }
}
