package com.bos.sphere.feature.widgets

/**
 * What to do on start so persisted placements and the host's allocated ids agree.
 *
 * - [keep]: placements whose id the host still knows, plus restore-pending placements while their
 *   restore window is open.
 * - [dropped]: placements whose id the host no longer knows (deleted by the system, e.g. the
 *   provider app was uninstalled or disabled), and restore-pending placements whose window closed
 *   without the system ever mapping them.
 * - [idsToDelete]: host ids no placement uses, no bind flow is waiting on and no unreadable stored
 *   line names (orphans, e.g. from a flow interrupted by process death); deleting them stops leaks.
 *   Always empty while any placement is waiting for a restore: until the restore broadcast says
 *   which new id is whose, every host id may belong to one of them.
 * - [unavailable]: kept placements whose provider info is gone: shown as an honest "widget
 *   unavailable" tile with a remove action, not silently dropped.
 * - [restoring]: kept placements still waiting for the restore broadcast (shown as "restoring").
 */
data class ReconcilePlan(
    val keep: List<WidgetPlacement>,
    val dropped: List<WidgetPlacement>,
    val idsToDelete: List<Int>,
    val unavailable: Set<Int>,
    val restoring: Set<Int>,
)

/** Result of remapping restore-pending placements after a backup restore. */
data class RemapResult(
    val placements: List<WidgetPlacement>,
    /** How many placements got their new id from this broadcast. */
    val remapped: Int,
)

object PlacementReconciler {

    /**
     * [pendingIds]: ids a bind flow is waiting on. [reservedIds]: ids named by stored lines this
     * version could not read. [restoreWindowOpen]: whether restore-pending placements may still be
     * mapped by a late `ACTION_APPWIDGET_HOST_RESTORED` ([RestoreWindow]).
     */
    fun plan(
        placements: List<WidgetPlacement>,
        hostIds: Collection<Int>,
        pendingIds: Collection<Int>,
        reservedIds: Collection<Int> = emptySet(),
        restoreWindowOpen: Boolean = false,
        hasProviderInfo: (Int) -> Boolean,
    ): ReconcilePlan {
        val known = hostIds.toSet()
        val (restorePending, local) = placements.partition { it.isRestorePending }
        val (keepLocal, droppedLocal) = local.partition { it.appWidgetId in known }
        val waiting = if (restoreWindowOpen) restorePending else emptyList()
        val expired = if (restoreWindowOpen) emptyList() else restorePending
        val keep = placements.filter { it in keepLocal || it in waiting }
        val used = keepLocal.mapTo(HashSet()) { it.appWidgetId }
        val held = pendingIds.toSet() + reservedIds
        val idsToDelete = if (waiting.isNotEmpty()) {
            emptyList()
        } else {
            known.filter { it !in used && it !in held }.sorted()
        }
        val unavailable = keepLocal.filterNot { hasProviderInfo(it.appWidgetId) }
            .mapTo(LinkedHashSet()) { it.appWidgetId }
        val restoring = waiting.mapTo(LinkedHashSet()) { it.appWidgetId }
        return ReconcilePlan(keep, droppedLocal + expired, idsToDelete, unavailable, restoring)
    }

    /**
     * Marks every local placement as restore-pending (`id → -id`). Used once, when placements are
     * found without this device's marker: they were restored from another device's backup, and
     * their ids mean nothing here until the restore broadcast maps them.
     */
    fun markRestorePending(placements: List<WidgetPlacement>): List<WidgetPlacement> =
        placements
            .map { if (it.appWidgetId > 0) it.copy(appWidgetId = -it.appWidgetId) else it }
            .distinctBy { it.appWidgetId }

    /**
     * `ACTION_APPWIDGET_HOST_RESTORED` hands the host parallel arrays of old (backed-up) and new
     * ids. Only restore-pending placements are remapped, and only those this broadcast names; every
     * other placement is left exactly as it is. The system may send more than one broadcast (each
     * carries only the records it hasn't sent yet, e.g. after a provider app is restored later), so
     * a placement a broadcast doesn't name is never dropped here: it waits for a later broadcast or
     * for its [RestoreWindow] to close. Mismatched arrays change nothing.
     */
    fun remapRestored(
        placements: List<WidgetPlacement>,
        oldIds: IntArray,
        newIds: IntArray,
    ): RemapResult {
        if (oldIds.size != newIds.size) return RemapResult(placements, 0)
        val map = HashMap<Int, Int>(oldIds.size)
        oldIds.forEachIndexed { i, old -> if (old > 0 && newIds[i] > 0) map[old] = newIds[i] }
        val taken = placements.filterNot { it.isRestorePending }.mapTo(HashSet()) { it.appWidgetId }
        var remapped = 0
        val out = placements.map { p ->
            val newId = if (p.isRestorePending) map[p.restoredFromId] else null
            if (newId != null && taken.add(newId)) {
                remapped++
                p.copy(appWidgetId = newId)
            } else {
                p
            }
        }
        return RemapResult(out, remapped)
    }
}

/**
 * How long restore-pending placements wait for the system's restore broadcast before they are
 * dropped as never restored. The window starts when the restored placements are first found.
 */
object RestoreWindow {
    const val DURATION_MS: Long = 3L * 24 * 60 * 60 * 1000

    /**
     * The window's start: [recorded] if valid, else [now]. A start in the future (the clock was
     * wrong when it was recorded, as it can be early in device setup) restarts the window now, so
     * a bad clock can't keep placements waiting forever.
     */
    fun start(recorded: Long, now: Long): Long = if (recorded <= 0L || recorded > now) now else recorded

    fun isOpen(start: Long, now: Long): Boolean = now - start < DURATION_MS
}

/**
 * Merges app tiles with equator widgets. A widget's [WidgetPlacement.slot] is the index it takes in
 * the merged list; widgets are inserted in slot order (ties by id), never before an earlier widget,
 * and clamped to the end when apps were removed. Hub widgets are ignored.
 */
object EquatorMerge {
    fun <T> merge(
        apps: List<T>,
        placements: List<WidgetPlacement>,
        toItem: (WidgetPlacement) -> T,
    ): List<T> {
        val widgets = placements
            .filter { it.surface == WidgetSurface.Equator }
            .sortedWith(compareBy({ it.slot }, { it.appWidgetId }))
        if (widgets.isEmpty()) return apps
        val out = ArrayList<T>(apps.size + widgets.size)
        out.addAll(apps)
        var previous = -1
        for (w in widgets) {
            val index = maxOf(w.slot, previous + 1).coerceAtMost(out.size)
            out.add(index, toItem(w))
            previous = index
        }
        return out
    }
}

/**
 * Pure edits to the placement list. Equator slots stay distinct: inserting at an index shifts the
 * widgets at or after it, and removing one closes the gap, so neighbouring widgets keep their
 * places relative to each other.
 */
object PlacementEdits {

    fun insertOnEquator(
        list: List<WidgetPlacement>,
        placement: WidgetPlacement,
        index: Int,
    ): List<WidgetPlacement> {
        val at = index.coerceAtLeast(0)
        val shifted = list
            .filter { it.appWidgetId != placement.appWidgetId }
            .map { if (it.surface == WidgetSurface.Equator && it.slot >= at) it.copy(slot = it.slot + 1) else it }
        return shifted + placement.copy(surface = WidgetSurface.Equator, slot = at)
    }

    fun appendToHub(list: List<WidgetPlacement>, placement: WidgetPlacement): List<WidgetPlacement> {
        val rest = list.filter { it.appWidgetId != placement.appWidgetId }
        val next = (rest.filter { it.surface == WidgetSurface.Hub }.maxOfOrNull { it.slot } ?: -1) + 1
        return rest + placement.copy(surface = WidgetSurface.Hub, slot = next)
    }

    fun remove(list: List<WidgetPlacement>, appWidgetId: Int): List<WidgetPlacement> {
        val target = list.firstOrNull { it.appWidgetId == appWidgetId } ?: return list
        val rest = list.filter { it.appWidgetId != appWidgetId }
        if (target.surface != WidgetSurface.Equator) return rest
        return rest.map {
            if (it.surface == WidgetSurface.Equator && it.slot > target.slot) it.copy(slot = it.slot - 1) else it
        }
    }

    fun moveToHub(list: List<WidgetPlacement>, appWidgetId: Int): List<WidgetPlacement> {
        val target = list.firstOrNull { it.appWidgetId == appWidgetId } ?: return list
        if (target.surface == WidgetSurface.Hub) return list
        return appendToHub(remove(list, appWidgetId), target)
    }

    fun moveToEquator(list: List<WidgetPlacement>, appWidgetId: Int, index: Int): List<WidgetPlacement> {
        val target = list.firstOrNull { it.appWidgetId == appWidgetId } ?: return list
        if (target.surface == WidgetSurface.Equator) return list
        return insertOnEquator(remove(list, appWidgetId), target, index)
    }

    fun setHubSize(list: List<WidgetPlacement>, appWidgetId: Int, size: HubSize): List<WidgetPlacement> =
        list.map { if (it.appWidgetId == appWidgetId) it.copy(hubSize = size) else it }

    /** Hub stack order: by slot, then id. */
    fun hubStack(list: List<WidgetPlacement>): List<WidgetPlacement> =
        list.filter { it.surface == WidgetSurface.Hub }
            .sortedWith(compareBy({ it.slot }, { it.appWidgetId }))
}
