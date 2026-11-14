package com.bos.sphere.core.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CFG-15: which actions sit on the all-apps puck's ring, and in what order. The list replaces the fixed set that
 * used to be written into the home screen. The ring's capacity comes from the window ([MAX] is the ceiling; the live
 * limit is `PuckLayout.compute(...).capacity`, INP-19), so a list longer than the window holds is kept but only the first
 * ones that fit are drawn.
 */
object PuckActions {
    /** Equal to `PuckLayout.MAX_ACTION_COUNT` in core-input; a test in feature-grid keeps the two in step. */
    const val MAX = 9

    /** The set the puck shipped with. */
    val DEFAULT: List<CornerActionId> = listOf(
        CornerActionId.LauncherSettings, CornerActionId.Notifications, CornerActionId.Flashlight,
        CornerActionId.RingerCycle, CornerActionId.DoNotDisturb,
    )

    /** The puck has its own Radar button and is itself the all-apps surface, so those two never sit on the ring. */
    val EXCLUDED: Set<CornerActionId> = setOf(CornerActionId.Search, CornerActionId.AllApps)

    val AVAILABLE: List<CornerActionId> = CornerActionId.entries.filter { it !in EXCLUDED }

    /** Distinct, known, allowed, at most [max]. */
    fun sanitize(list: List<CornerActionId>, max: Int = MAX): List<CornerActionId> =
        list.filter { it in AVAILABLE }.distinct().take(max)

    fun add(list: List<CornerActionId>, id: CornerActionId, max: Int = MAX): List<CornerActionId> =
        if (id !in AVAILABLE || id in list || list.size >= max) list else list + id

    fun remove(list: List<CornerActionId>, index: Int): List<CornerActionId> =
        if (index in list.indices) list.filterIndexed { i, _ -> i != index } else list

    /** Moves the entry at [index] by [delta] places (clamped to the ends). */
    fun move(list: List<CornerActionId>, index: Int, delta: Int): List<CornerActionId> {
        if (index !in list.indices) return list
        val to = (index + delta).coerceIn(0, list.lastIndex)
        if (to == index) return list
        return list.toMutableList().also { val x = it.removeAt(index); it.add(to, x) }
    }

    /** Actions not yet on the ring, in the order of the action list. */
    fun candidates(list: List<CornerActionId>): List<CornerActionId> = AVAILABLE.filter { it !in list }

    fun encode(list: List<CornerActionId>): String = list.joinToString(",") { it.name }

    /** Null (nothing saved) is the default set; an empty string is a deliberately empty ring. Unknown names are dropped. */
    fun decode(raw: String?): List<CornerActionId> =
        if (raw == null) DEFAULT
        else sanitize(raw.split(',').mapNotNull { n -> CornerActionId.entries.firstOrNull { it.name == n.trim() } })
}

/** Persists [PuckActions] in the `puck_actions` preference file (carried by the state export, see [PrefFilesSource]). */
class PuckActionsStore(private val sp: SharedPreferences) {
    private val _actions = MutableStateFlow(PuckActions.decode(sp.getString("actions", null)))
    val actions: StateFlow<List<CornerActionId>> = _actions.asStateFlow()

    fun set(list: List<CornerActionId>) {
        val next = PuckActions.sanitize(list)
        _actions.value = next
        sp.edit().putString("actions", PuckActions.encode(next)).apply()
    }

    fun reset() {
        _actions.value = PuckActions.DEFAULT
        sp.edit().remove("actions").apply()
    }
}
