package com.bos.sphere.core.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What one of the four sections around the all-apps hub does (CFG-15, wave 30). */
sealed interface PuckSlot {
    /** Nothing assigned: the section is drawn as a faint empty wedge and opens its editor on a long press. */
    data object Empty : PuckSlot

    /** A launcher action: a toggle, an intent or Radar ([CornerActionId.Search]). */
    data class Action(val id: CornerActionId) : PuckSlot

    /** An installed app, by its [AppEntry.key]. */
    data class App(val key: String) : PuckSlot
}

/**
 * CFG-15 (wave 30, puckSections): the four sections of the all-apps disc's inner quarter ring. Each of the [SLOTS] slots holds one
 * shortcut: any action but [CornerActionId.AllApps] (the disc is that surface itself; Radar is allowed and is how search stays reachable
 * from the disc now that the hub's centre button is Back), an app, or nothing. The list is always exactly [SLOTS] long.
 */
object PuckActions {
    const val SLOTS = 4

    /** Flashlight, alarm clock, notifications (bell) and Do not disturb (moon): what a fresh install and a user with nothing saved get. */
    val DEFAULT_IDS: List<CornerActionId> = listOf(
        CornerActionId.Flashlight, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb,
    )
    val DEFAULT: List<PuckSlot> = DEFAULT_IDS.map { PuckSlot.Action(it) }

    /** The disc is the all-apps surface, so it never offers itself. */
    val EXCLUDED: Set<CornerActionId> = setOf(CornerActionId.AllApps)

    /** Radar first (search), then the rest in the action list's order. */
    val AVAILABLE: List<CornerActionId> = listOf(CornerActionId.Search) + CornerActionId.entries.filter { it !in EXCLUDED && it != CornerActionId.Search }

    /** Exactly [SLOTS] entries: an unknown or excluded action becomes [PuckSlot.Empty], a short list is padded with [PuckSlot.Empty]. */
    fun normalize(list: List<PuckSlot>): List<PuckSlot> {
        val clean = list.take(SLOTS).map { s ->
            when {
                s is PuckSlot.Action && s.id !in AVAILABLE -> PuckSlot.Empty
                s is PuckSlot.App && s.key.isBlank() -> PuckSlot.Empty
                else -> s
            }
        }
        return clean + List(SLOTS - clean.size) { PuckSlot.Empty }
    }

    /** Replaces slot [index] (ignored when out of range). */
    fun with(list: List<PuckSlot>, index: Int, slot: PuckSlot): List<PuckSlot> =
        if (index !in 0 until SLOTS) normalize(list) else normalize(normalize(list).toMutableList().also { it[index] = slot })

    private fun encodeOne(s: PuckSlot): String = when (s) {
        PuckSlot.Empty -> "-"
        is PuckSlot.Action -> "a:" + s.id.name
        is PuckSlot.App -> "p:" + s.key
    }

    private fun decodeOne(t: String): PuckSlot = when {
        t.startsWith("a:") -> CornerActionId.entries.firstOrNull { it.name == t.substring(2) }?.let { PuckSlot.Action(it) } ?: PuckSlot.Empty
        t.startsWith("p:") -> PuckSlot.App(t.substring(2))
        else -> PuckSlot.Empty
    }

    /** One slot per line (an app key never holds a newline). */
    fun encode(list: List<PuckSlot>): String = normalize(list).joinToString("\n") { encodeOne(it) }

    /** Null (nothing saved) is [DEFAULT]. */
    fun decode(raw: String?): List<PuckSlot> = if (raw == null) DEFAULT else normalize(raw.split('\n').map { decodeOne(it) })

    /** The wave 29 format: a comma-separated list of action names (null = the old default, which is not what the sections start with). */
    fun decodeLegacy(raw: String?): List<CornerActionId>? =
        raw?.split(',')?.mapNotNull { n -> CornerActionId.entries.firstOrNull { it.name == n.trim() } }
            ?.filter { it !in EXCLUDED && it != CornerActionId.Search }?.distinct()

    /**
     * Migration of the old multi-select ring: the first four actions the user had, in order; fewer than four are completed with the
     * defaults that are not already there, and nothing saved (or an emptied ring) gives the defaults.
     */
    fun migrate(legacy: List<CornerActionId>?): List<PuckSlot> {
        val had = legacy.orEmpty().take(SLOTS)
        if (had.isEmpty()) return DEFAULT
        val fill = DEFAULT_IDS.filter { it !in had }
        return normalize((had + fill).take(SLOTS).map { PuckSlot.Action(it) })
    }
}

/**
 * Persists [PuckActions] in the `puck_actions` preference file (carried by the state export, see [PrefFilesSource]). The `slots` key holds
 * the four sections; the wave 29 `actions` key is read once to migrate, then removed (an old export imported later is migrated the same way).
 */
class PuckActionsStore(private val sp: SharedPreferences) {
    private val _slots = MutableStateFlow(load())
    val slots: StateFlow<List<PuckSlot>> = _slots.asStateFlow()

    private fun load(): List<PuckSlot> {
        val raw = sp.getString("slots", null)
        if (raw != null) return PuckActions.decode(raw)
        val legacy = sp.getString("actions", null) ?: return PuckActions.DEFAULT
        val migrated = PuckActions.migrate(PuckActions.decodeLegacy(legacy))
        sp.edit().putString("slots", PuckActions.encode(migrated)).remove("actions").apply()
        return migrated
    }

    fun set(index: Int, slot: PuckSlot) = setAll(PuckActions.with(_slots.value, index, slot))

    fun setAll(list: List<PuckSlot>) {
        val next = PuckActions.normalize(list)
        _slots.value = next
        sp.edit().putString("slots", PuckActions.encode(next)).remove("actions").apply()
    }

    fun reset() {
        _slots.value = PuckActions.DEFAULT
        sp.edit().remove("slots").remove("actions").apply()
    }

    /** Re-reads the preference file (after a state import or restore wrote it behind the store's back). */
    fun reload() { _slots.value = load() }

    companion object {
        @Volatile private var shared: PuckActionsStore? = null

        /** One instance per process, so the Ground Control pane and the home screen's disc see the same sections at once. */
        fun get(context: android.content.Context): PuckActionsStore = shared ?: synchronized(this) {
            shared ?: PuckActionsStore(TolerantPrefs.of(context.applicationContext, "puck_actions")).also { shared = it }
        }
    }
}

/**
 * CFG-15 (wave 30): whether a section's action can work on this device right now. An unavailable one is drawn dimmed; tapping it still runs the
 * action, which says why (no flashlight; Do not disturb access to grant) exactly as the old ring buttons did.
 */
object PuckAvailability {
    fun of(id: CornerActionId, hasFlash: Boolean, hasPolicyAccess: Boolean): Boolean = when (id) {
        CornerActionId.Flashlight -> hasFlash
        CornerActionId.DoNotDisturb, CornerActionId.RingerSilent -> hasPolicyAccess
        else -> true
    }

    fun policyAccess(context: android.content.Context): Boolean =
        runCatching { context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted }.getOrDefault(true)
}
