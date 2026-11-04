package com.bos.sphere.feature.widgets

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Persists hosted-widget placements (id, provider, profile serial, surface, slot, hub size) in
 * SharedPreferences, the same zero-dependency way `HiddenAppsStore` persists hidden apps, and
 * exposes them as a [StateFlow]. The widget ids themselves persist in the system's
 * AppWidgetService, so placements and ids both survive process death and reboot.
 *
 * **Backup restore.** The preferences are backed up with the app (`allowBackup`); the widget ids in
 * them belong to the old device. A marker file in `noBackupFilesDir` (never backed up) says "these
 * placements were written on this device". Placements found without it came from a restore: they
 * are marked restore-pending ([PlacementReconciler.markRestorePending]) until the system's
 * `ACTION_APPWIDGET_HOST_RESTORED` maps their ids, and the restore window starts. If the marked
 * list can't be saved, nothing is marked and the old behaviour applies.
 *
 * Lines this version can't read are kept verbatim on every rewrite ([PlacementCodec.decodeAll]).
 */
internal class WidgetPlacementStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val unparsed: List<String>

    /** Ids named by stored lines this version could not read: never deleted as orphans. */
    val reservedIds: Set<Int>

    private val _placements: MutableStateFlow<List<WidgetPlacement>>
    val placements: StateFlow<List<WidgetPlacement>>

    init {
        val decoded = PlacementCodec.decodeAll(prefs.getString(KEY, null))
        unparsed = decoded.unparsed
        reservedIds = decoded.reservedIds
        var list = decoded.placements
        val marker = File(context.applicationContext.noBackupFilesDir, MARKER)
        if (!runCatching { marker.exists() }.getOrDefault(true)) {
            if (list.any { !it.isRestorePending }) {
                val marked = PlacementReconciler.markRestorePending(list)
                // Written synchronously before the marker: if the process dies in between, the next
                // start finds the placements already pending and only writes the marker.
                val saved = runCatching {
                    prefs.edit()
                        .putString(KEY, PlacementCodec.encode(marked, unparsed))
                        .putLong(KEY_RESTORE_SINCE, clock())
                        .commit()
                }.getOrDefault(false)
                if (saved) list = marked
            }
            runCatching { marker.createNewFile() }
        }
        _placements = MutableStateFlow(list)
        placements = _placements.asStateFlow()
    }

    /** Applies [transform] and persists the result if it changed. Main thread. */
    fun update(transform: (List<WidgetPlacement>) -> List<WidgetPlacement>) {
        val current = _placements.value
        val next = transform(current)
        if (next == current) return
        _placements.value = next
        prefs.edit().putString(KEY, PlacementCodec.encode(next, unparsed)).apply()
    }

    /**
     * Whether restore-pending placements may still wait for the restore broadcast. Starts the
     * window if none is recorded (or the recorded start lies in the future; see [RestoreWindow]).
     */
    fun restoreWindowOpen(): Boolean {
        val now = clock()
        val recorded = prefs.getLong(KEY_RESTORE_SINCE, 0L)
        val start = RestoreWindow.start(recorded, now)
        if (start != recorded) prefs.edit().putLong(KEY_RESTORE_SINCE, start).apply()
        return RestoreWindow.isOpen(start, now)
    }

    /** Forgets the restore window once no placement is waiting any more. */
    fun clearRestoreWindow() {
        if (prefs.contains(KEY_RESTORE_SINCE)) prefs.edit().remove(KEY_RESTORE_SINCE).apply()
    }

    private companion object {
        const val PREFS = "hosted_widgets"
        const val KEY = "placements"
        const val KEY_RESTORE_SINCE = "restore_pending_since"

        /** In `noBackupFilesDir`: present = the placements were written on this device. */
        const val MARKER = "hosted_widgets.device"
    }
}
