package com.bos.sphere.core.data

/**
 * HOM-12 (page carousel): page-level edits over the visual page list, as pure functions. A stored page that
 * overflowed on a density change is drawn as several visual pages with the same id; those chunks are one *group*
 * and are moved together so they can never be pulled apart.
 */
object HomePageOps {

    /** The visual indices that belong to the same stored page as [index] (the index itself when the page is not split). */
    fun groupOf(pages: List<HomePage>, index: Int): IntRange {
        if (index !in pages.indices) return IntRange.EMPTY
        val id = pages[index].id
        if (id == 0L) return index..index
        var s = index; var e = index
        while (s > 0 && pages[s - 1].id == id) s--
        while (e < pages.lastIndex && pages[e + 1].id == id) e++
        return s..e
    }

    /** Result of a page edit: the new visual pages, the apps it took off the home, and the page to show afterwards. */
    data class Result(val pages: List<HomePage>, val taken: Set<String>, val focus: Int)

    /**
     * Deletes page [index]. Its items join the end of the page before (the next one for the first page), as the quick
     * sheet's delete always did. The last remaining page cannot vanish: it is emptied instead and its items are
     * reported in [Result.taken] so the caller can mark them removed from home.
     */
    fun delete(pages: List<HomePage>, index: Int, capacityOf: (PageLayout) -> Int): Result {
        if (index !in pages.indices) return Result(pages, emptySet(), index.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        // A stored page that overflowed into several visual pages is one page: deleting any chunk deletes it whole (otherwise its items
        // would join a sibling chunk and overflow straight back, so the page could never be removed).
        val g = groupOf(pages, index)
        val whole = if (g.last > g.first) pages.take(g.first) + pages[g.first].copy(keys = g.flatMap { pages[it].keys }) + pages.drop(g.last + 1) else pages
        val at = g.first
        if (whole.size == 1) {
            val p = whole[0]
            return Result(listOf(p.copy(keys = emptyList(), id = if (p.id == 0L) 1L else p.id)), p.keys.toSet(), 0)
        }
        val out = HomeLayoutMath.deletePage(whole, at, capacityOf)
        return Result(out, emptySet(), (at - 1).coerceIn(0, out.lastIndex))
    }

    /** Moves the page group holding [index] by [delta] groups (-1 = up/earlier, +1 = down/later). Null when it cannot move. */
    fun move(pages: List<HomePage>, index: Int, delta: Int): Result? {
        if (delta == 0 || index !in pages.indices) return null
        val g = groupOf(pages, index)
        val groups = ArrayList<List<HomePage>>()
        var i = 0
        var gi = -1
        while (i < pages.size) {
            val r = groupOf(pages, i)
            if (r == g) gi = groups.size
            groups += pages.subList(r.first, r.last + 1)
            i = r.last + 1
        }
        val to = (gi + delta).coerceIn(0, groups.lastIndex)
        if (to == gi) return null
        val moved = groups.removeAt(gi)
        groups.add(to, moved)
        val out = groups.flatten()
        return Result(out, emptySet(), groups.take(to).sumOf { it.size })
    }

    /** Drag-reorder: moves the group holding [index] to the position of the group holding [target]. */
    fun moveTo(pages: List<HomePage>, index: Int, target: Int): Result? {
        if (index !in pages.indices || target !in pages.indices) return null
        var cur = pages
        var at = index
        val steps = groupIndexOf(pages, target) - groupIndexOf(pages, index)
        if (steps == 0) return null
        repeat(kotlin.math.abs(steps)) {
            val r = move(cur, at, if (steps > 0) 1 else -1) ?: return null
            cur = r.pages; at = r.focus
        }
        return Result(cur, emptySet(), at)
    }

    private fun groupIndexOf(pages: List<HomePage>, index: Int): Int {
        var n = 0; var i = 0
        while (i < pages.size) {
            val r = groupOf(pages, i)
            if (index in r) return n
            n++; i = r.last + 1
        }
        return n
    }

    /** Adds a blank page after [after] and says which visual page it is. */
    fun add(pages: List<HomePage>, after: Int): Result {
        val at = (after.coerceIn(-1, pages.lastIndex) + 1)
        return Result(HomeLayoutMath.insertPage(pages, after.coerceIn(-1, pages.lastIndex)), emptySet(), at)
    }
}

/**
 * Empty default home: a fresh install starts with one empty page and no apps placed. The decision is the absence of
 * a saved arrangement (never seeded, nothing removed) on a launcher that has not finished onboarding (a layout with pages is a saved layout whatever its seeded flag says); an existing
 * user (saved layout, or settings from before this change) is never touched and an imported or restored arrangement
 * always counts as saved.
 */
object FreshHome {
    fun isFresh(layout: HomeLayout, onboardingDone: Boolean, legacySeeded: Boolean = false): Boolean =
        // [HomeLayout.seeded] is not consulted: the size-class binding marks a layout seeded on the very first frame even when nothing was ever arranged.
        layout.pages.isEmpty() && layout.removed.isEmpty() && !onboardingDone && !legacySeeded

    /** The one empty page a fresh install starts with. */
    fun emptyLayout(): HomeLayout = HomeLayout(pages = listOf(HomePage(id = 1L)), seeded = true)

    /** Only the apps the user adds are placed; everything else stays behind the add carousel, drawer, Radar and search. */
    fun prefs(p: LauncherPrefs): LauncherPrefs = p.copy(appsOnHome = AppsOnHome.PinnedOnly)

    /** The quiet hint shows when no page holds anything. */
    fun isEmpty(pages: List<HomePage>): Boolean = pages.all { it.keys.isEmpty() }
}

/** The add carousel's rules: apps (alphabetical, filtered by typing), and where a tap places the item. */
object AddCarouselModel {
    /** Visible apps not already on the home, alphabetical, filtered by [query] (case-insensitive substring of the label). */
    fun <T> apps(
        all: List<T>, onHome: Set<String>, query: String, work: Boolean? = null,
        isWork: (T) -> Boolean = { false }, key: (T) -> String, label: (T) -> String,
    ): List<T> {
        val q = query.trim().lowercase()
        return all.asSequence()
            .filter { key(it) !in onHome }
            .filter { q.isEmpty() || label(it).lowercase().contains(q) }
            .filter { work == null || work == isWork(it) }
            .sortedBy { label(it).lowercase() }
            .toList()
    }

    fun apps(all: List<AppEntry>, onHome: Set<String>, query: String, work: Boolean? = null, isWork: (AppEntry) -> Boolean = { false }): List<AppEntry> =
        apps(all, onHome, query, work, isWork, { it.key }, { it.label })

    /**
     * Where a tap puts a new item: the current page when it has a free cell, else the first page after it with one,
     * else a new page at the end. Returns the page index (== pages.size means "new page").
     */
    fun targetPage(pages: List<HomePage>, current: Int, capacityOf: (PageLayout) -> Int): Int {
        if (pages.isEmpty()) return 0
        val order = (current.coerceIn(0, pages.lastIndex)..pages.lastIndex) + (0 until current.coerceIn(0, pages.lastIndex))
        return order.firstOrNull { pages[it].keys.size < capacityOf(pages[it].layout) } ?: pages.size
    }

    /** The visual pages after adding [key] in the first free cell (see [targetPage]); a full home grows a page. */
    fun place(pages: List<HomePage>, key: String, current: Int, capacityOf: (PageLayout) -> Int): List<HomePage> {
        if (pages.any { key in it.keys }) return pages
        val base = pages.ifEmpty { listOf(HomePage(id = 1L)) }
        val t = targetPage(base, current, capacityOf)
        val withRoom = if (t >= base.size) HomeLayoutMath.insertPage(base, base.lastIndex) else base
        val idx = t.coerceAtMost(withRoom.lastIndex)
        return withRoom.mapIndexed { i, p -> if (i == idx) p.copy(keys = p.keys + key) else p }
    }
}
