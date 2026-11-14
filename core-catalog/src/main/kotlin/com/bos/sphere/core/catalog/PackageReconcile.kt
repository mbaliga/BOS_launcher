package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout

/**
 * HOM-10: package changes. [AppRecord] is the catalog's view of one launchable; the reconcile
 * functions turn two inventories into a diff and apply it to the arrangement, so install, update,
 * uninstall, disable, archive and suspend are all handled by one pure path that is unit-tested.
 */
data class AppRecord(
    val key: String,
    val packageName: String,
    val label: String,
    val profileId: Int = 0,
    val suspended: Boolean = false,
    /** Archived apps (Android 15) stay listed; launching restores them. */
    val archived: Boolean = false,
    /** Bumped on update so an icon change is noticed. */
    val versionStamp: Long = 0L,
)

data class CatalogDiff(
    val added: List<AppRecord>,
    val removed: List<AppRecord>,
    val changed: List<AppRecord>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
    /** True when cached icons for these keys must be dropped. */
    fun iconKeysToEvict(): Set<String> = (changed.map { it.key } + removed.map { it.key }).toSet()
}

object PackageReconcile {

    fun diff(old: List<AppRecord>, new: List<AppRecord>): CatalogDiff {
        val o = old.associateBy { it.key }
        val n = new.associateBy { it.key }
        return CatalogDiff(
            added = new.filter { it.key !in o },
            removed = old.filter { it.key !in n },
            changed = new.filter { o[it.key]?.let { prev -> prev != it } == true },
        )
    }

    /** Whether the user can launch it right now (suspended apps are shown dimmed, never crash on tap). */
    fun launchable(r: AppRecord): Boolean = !r.suspended

    /**
     * Applies an inventory change to the arrangement: removed apps vanish from pages, dock and
     * folders without leaving gaps; new apps are not forced onto pages here (the page resolver
     * appends them when the user shows all apps).
     */
    fun applyToState(state: HomeState, diff: CatalogDiff, cap: (PageLayout) -> Int): HomeState {
        if (diff.removed.isEmpty()) return state
        val live = state.allKeys().filter { ItemKeys.kind(it) == ItemKind.App }.toMutableSet()
        live.removeAll(diff.removed.map { it.key }.toSet())
        return HomeEdit.pruneMissing(state, live, cap)
    }

    /**
     * Pages with no gaps after removals: a key that is not available is dropped by the resolver,
     * and an emptied middle page is not left blank.
     */
    fun visible(state: HomeState, apps: List<AppRecord>, cap: (PageLayout) -> Int): List<HomePage> =
        HomeLayoutMath.visualPages(
            com.bos.sphere.core.data.HomeLayout(pages = state.pages, seeded = true),
            apps.map { it.key } + state.nonAppKeys(), cap, autoPlace = false,
        )
}

/**
 * Coalesces bursts of LauncherApps callbacks (a bulk install fires dozens): a refresh is requested
 * at most once per [windowMs], and never later than [windowMs] after the first event, so the
 * surface is current well inside the 1 s of HOM-10.
 */
class RefreshCoalescer(private val windowMs: Long = 250L) {
    private var firstPendingAt = -1L

    /** Records an event at [now]; returns the time at which a refresh must run (or already runs). */
    fun onEvent(now: Long): Long {
        if (firstPendingAt < 0) firstPendingAt = now
        return firstPendingAt + windowMs
    }

    /** True when a refresh should run at [now]; resets the window when it does. */
    fun due(now: Long): Boolean {
        if (firstPendingAt < 0 || now < firstPendingAt + windowMs) return false
        firstPendingAt = -1L
        return true
    }
}
