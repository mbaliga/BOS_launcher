package com.bos.sphere.core.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What one segment of the all-apps disc's shortcut ring does (CFG-15, waves 30 and 38). */
sealed interface PuckSlot {
    /** Nothing assigned: the section is drawn as a faint empty wedge and opens its editor on a long press. */
    data object Empty : PuckSlot

    /** A launcher action: a toggle, an intent or Radar ([CornerActionId.Search]). */
    data class Action(val id: CornerActionId) : PuckSlot

    /** An installed app, by its [AppEntry.key]. */
    data class App(val key: String) : PuckSlot
}

/**
 * CFG-15 (wave 30 puckSections, wave 38 drawerRing): the shortcut ring of the all-apps disc. The ring is cut into [segments] equal segments
 * (6, 7 or 8, default 8) and each segment holds one shortcut: any action but [CornerActionId.AllApps] (the disc is that surface itself; Radar
 * is allowed and is how search stays reachable from the disc now that the hub's centre button is Back), an app, or nothing.
 *
 * The store always keeps [SLOTS] (8) slots whatever the count, so going from 8 to 6 and back does not lose the last two. The first
 * `segments` of them are the ring. Before wave 38 there were four; a store that holds fewer than [SLOTS] is completed once with
 * [completeFromDefaults] (the user's four stay in place, the rest get actions from [DEFAULT_IDS] they do not already have).
 */
object PuckActions {
    /** What the store keeps: the largest ring. */
    const val SLOTS = 8
    const val MIN_SEGMENTS = 6
    const val MAX_SEGMENTS = 8
    const val DEFAULT_SEGMENTS = 8
    /** The ring had four sections until wave 37. */
    const val LEGACY_SLOTS = 4

    /** The allowed ring sizes, in the order the settings choice lists them. */
    val SEGMENT_CHOICES: List<Int> = (MIN_SEGMENTS..MAX_SEGMENTS).toList()

    fun clampSegments(n: Int): Int = n.coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)

    /**
     * What a fresh install and a user with nothing saved get: the four of wave 30 (flashlight, alarm clock, notifications bell, Do not
     * disturb) and then Radar, camera, calculator and system settings (each runs through the existing action runner).
     */
    val DEFAULT_IDS: List<CornerActionId> = listOf(
        CornerActionId.Flashlight, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb,
        CornerActionId.Search, CornerActionId.Camera, CornerActionId.Calculator, CornerActionId.SystemSettings,
    )
    val DEFAULT: List<PuckSlot> = DEFAULT_IDS.map { PuckSlot.Action(it) }

    /** Offered when a user's own actions already cover [DEFAULT_IDS], so completion still finds something to put in the new segments. */
    private val FILL_EXTRA: List<CornerActionId> = listOf(CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Maps, CornerActionId.Calendar)

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

    /**
     * Wave 38 migration: a list shorter than [SLOTS] (the wave 30 four) keeps what it has, in place, and the missing tail gets the actions of
     * [DEFAULT_IDS] (then a few common ones) that the list does not already hold. Idempotent: a full list comes back unchanged.
     */
    fun completeFromDefaults(list: List<PuckSlot>): List<PuckSlot> {
        val have = normalize(list.take(SLOTS)).take(list.size.coerceAtMost(SLOTS))
        if (have.size >= SLOTS) return have
        val used = have.mapNotNull { (it as? PuckSlot.Action)?.id }.toMutableSet()
        val fill = (DEFAULT_IDS + FILL_EXTRA).filter { it !in used }.take(SLOTS - have.size).map { PuckSlot.Action(it) }
        return normalize(have + fill)
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

    /** One slot per line (an app key never holds a newline); always [SLOTS] lines. */
    fun encode(list: List<PuckSlot>): String = normalize(list).joinToString("\n") { encodeOne(it) }

    /** Null (nothing saved) is [DEFAULT]; a blank string is all empty; fewer than [SLOTS] lines is a wave 30 save and is completed. */
    fun decode(raw: String?): List<PuckSlot> = when {
        raw == null -> DEFAULT
        raw.isEmpty() -> normalize(emptyList())
        else -> completeFromDefaults(raw.split('\n').map { decodeOne(it) })
    }

    /** The wave 29 format: a comma-separated list of action names (null = the old default, which is not what the sections start with). */
    fun decodeLegacy(raw: String?): List<CornerActionId>? =
        raw?.split(',')?.mapNotNull { n -> CornerActionId.entries.firstOrNull { it.name == n.trim() } }
            ?.filter { it !in EXCLUDED && it != CornerActionId.Search }?.distinct()

    /**
     * Migration of the old multi-select ring: the first four actions the user had, in order; fewer than four are completed with the
     * defaults that are not already there, and nothing saved (or an emptied ring) gives the defaults. The tail up to [SLOTS] is filled
     * with [completeFromDefaults].
     */
    fun migrate(legacy: List<CornerActionId>?): List<PuckSlot> {
        val had = legacy.orEmpty().take(LEGACY_SLOTS)
        if (had.isEmpty()) return DEFAULT
        val fill = DEFAULT_IDS.take(LEGACY_SLOTS).filter { it !in had }
        return completeFromDefaults((had + fill).take(LEGACY_SLOTS).map { PuckSlot.Action(it) })
    }
}

/**
 * Persists [PuckActions] in the `puck_actions` preference file (carried by the state export, see [PrefFilesSource]). The `slots` key holds
 * the slots, the `segments` key the ring size (6 to 8) and `labels` whether a small label sits under each glyph; the wave 29 `actions` key
 * is read once to migrate, then removed (an old export imported later is migrated the same way).
 */
class PuckActionsStore(private val sp: SharedPreferences) {
    private val _slots = MutableStateFlow(load())
    val slots: StateFlow<List<PuckSlot>> = _slots.asStateFlow()
    private val _segments = MutableStateFlow(PuckActions.clampSegments(sp.getInt("segments", PuckActions.DEFAULT_SEGMENTS)))
    /** The ring size, 6 to 8 (default 8). */
    val segments: StateFlow<Int> = _segments.asStateFlow()
    private val _labels = MutableStateFlow(sp.getBoolean("labels", false))
    /** Whether the visible segments show a small label under their glyph (default off). */
    val showLabels: StateFlow<Boolean> = _labels.asStateFlow()

    private fun load(): List<PuckSlot> {
        val raw = sp.getString("slots", null)
        if (raw != null) {
            val decoded = PuckActions.decode(raw)
            // Idempotent wave 38 migration: a four-slot save is rewritten once as eight.
            if (raw.isNotEmpty() && raw.split('\n').size < PuckActions.SLOTS) sp.edit().putString("slots", PuckActions.encode(decoded)).apply()
            return decoded
        }
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

    fun setSegments(n: Int) {
        val c = PuckActions.clampSegments(n)
        _segments.value = c
        sp.edit().putInt("segments", c).apply()
    }

    fun setShowLabels(on: Boolean) {
        _labels.value = on
        sp.edit().putBoolean("labels", on).apply()
    }

    fun reset() {
        _slots.value = PuckActions.DEFAULT
        sp.edit().remove("slots").remove("actions").apply()
    }

    /** Re-reads the preference file (after a state import or restore wrote it behind the store's back). */
    fun reload() {
        _slots.value = load()
        _segments.value = PuckActions.clampSegments(sp.getInt("segments", PuckActions.DEFAULT_SEGMENTS))
        _labels.value = sp.getBoolean("labels", false)
    }

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
