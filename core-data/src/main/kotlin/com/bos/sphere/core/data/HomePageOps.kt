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
     * Deletes page [index] with everything placed on it (wave 30, pagesExplicit; before, its items joined the previous page, so a delete
     * looked like it did nothing). A stored page that overflowed into several visual pages is one page: deleting any chunk deletes it whole.
     * [Result.taken] holds every key that left the home so the caller can take apps off the home and drop folders, widgets and shortcuts the usual
     * way. The last remaining page is emptied (Pages stays on with one empty page).
     */
    fun delete(pages: List<HomePage>, index: Int): Result {
        if (index !in pages.indices) return Result(pages, emptySet(), index.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
        val g = groupOf(pages, index)
        val taken = g.flatMapTo(LinkedHashSet()) { pages[it].keys }
        val rest = pages.filterIndexed { i, _ -> i !in g }
        if (rest.isEmpty()) {
            val p = pages[g.first]
            return Result(listOf(p.copy(keys = emptyList(), id = if (p.id == 0L) 1L else p.id)), taken, 0)
        }
        return Result(rest, taken, (g.first - 1).coerceIn(0, rest.lastIndex))
    }

    /** How many items deleting the page group holding [index] removes (what the confirmation says). */
    fun itemCount(pages: List<HomePage>, index: Int): Int = groupOf(pages, index).sumOf { pages[it].keys.size }

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
     * Wave 30 (pagesExplicit): a tap puts the item on the CURRENT page when it has a free cell; a full page is "page full" ([place] returns null),
     * never another page and never a new one. Returns the page index to use, or null when the current page is full.
     */
    fun targetPage(pages: List<HomePage>, current: Int, capacityOf: (PageLayout) -> Int): Int? {
        if (pages.isEmpty()) return 0
        val i = current.coerceIn(0, pages.lastIndex)
        return i.takeIf { pages[it].keys.size < capacityOf(pages[it].layout).coerceAtLeast(1) }
    }

    /** The visual pages after adding [key] on the current page (see [targetPage]); null when that page is full (the caller says "Page full"). */
    fun place(pages: List<HomePage>, key: String, current: Int, capacityOf: (PageLayout) -> Int): List<HomePage>? {
        if (pages.any { key in it.keys }) return pages
        val base = pages.ifEmpty { listOf(HomePage(id = 1L)) }
        val idx = targetPage(base, current, capacityOf) ?: return null
        return base.mapIndexed { i, p -> if (i == idx) p.copy(keys = p.keys + key) else p }
    }
}
