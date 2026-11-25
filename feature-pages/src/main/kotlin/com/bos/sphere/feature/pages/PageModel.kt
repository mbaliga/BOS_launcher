package com.bos.sphere.feature.pages

/** What a page shows. [Home] is the coverflow; it is always page 0 and can't be removed. */
enum class PageKind(val wire: String) {
    Home("home"),

    /** One Android App Widget filling the page. */
    AppWidget("appwidget"),

    /** An HTML widget package (D-AC) or a single-file page (such as Orrery's), run by the runtime. */
    Html("html"),

    /** A website, by URL. */
    Web("web"),
    ;

    companion object {
        fun fromWire(s: String?): PageKind? = entries.firstOrNull { it.wire == s }
    }
}

/**
 * One page in the launcher's ordered set. Pure data, persisted by [PageListCodec].
 *
 * - [title]: what the user called it; blank means "use the content's own name" (the widget's label,
 *   the package's `name`, the page's `<title>`, the website's host).
 * - [widgetSlot] ([PageKind.AppWidget]): the page's link to its hosted widget. The widget's placement
 *   (in `:feature-widgets`) sits on the `Page` surface with this number as its slot, so the link
 *   survives the id remapping a backup restore does.
 * - [url] ([PageKind.Web]): a validated http(s) address ([UrlRules]).
 * - [isPackage] ([PageKind.Html]): true for a D-AC widget package, false for a single-file page.
 * - [network] ([PageKind.Html] single-file pages only): the per-page "Allow this page to reach the
 *   network" switch. Off by default; always off for packages, which never reach the network.
 * - [rev] ([PageKind.Html]): bumped on each re-import so a live view reloads the new files.
 */
data class Page(
    val id: String,
    val kind: PageKind,
    val title: String = "",
    val widgetSlot: Int = 0,
    val url: String = "",
    val isPackage: Boolean = false,
    val network: Boolean = false,
    val rev: Int = 0,
) {
    companion object {
        const val HOME_ID = "home"
        val Home = Page(HOME_ID, PageKind.Home)
    }
}

/**
 * The page list: [pages] always starts with [Page.Home] exactly once. [unknown] holds stored
 * entries this version can't read (a newer kind, a damaged entry), kept verbatim so a rewrite never
 * erases them; they are not shown.
 */
data class PageList(
    val pages: List<Page>,
    val unknown: List<Map<String, Any?>> = emptyList(),
) {
    val size: Int get() = pages.size

    operator fun get(index: Int): Page = pages[index]

    fun indexOf(id: String): Int = pages.indexOfFirst { it.id == id }

    fun byId(id: String): Page? = pages.firstOrNull { it.id == id }

    companion object {
        val HomeOnly = PageList(listOf(Page.Home))
    }
}

/** Page ids: `p` + 16 lowercase hex digits, random, never reused. Html pages derive their origin from it. */
object PageIds {
    private val VALID = Regex("^p[0-9a-f]{16}$")

    fun newId(random: () -> Long): String = "p" + java.lang.Long.toHexString(random()).padStart(16, '0')

    fun isValid(id: String): Boolean = VALID.matches(id)
}

/** Pure edits to the page list. Home stays first and can't be moved, renamed or removed. */
object PageEdits {
    const val MAX_TITLE = 80

    /** Home first exactly once, then the other pages in order, duplicate ids dropped (first kept). */
    fun normalize(list: PageList): PageList {
        val seen = HashSet<String>()
        val rest = list.pages.filter { it.kind != PageKind.Home && it.id != Page.HOME_ID && seen.add(it.id) }
        return list.copy(pages = listOf(Page.Home) + rest)
    }

    fun add(list: PageList, page: Page): PageList {
        if (page.kind == PageKind.Home || list.byId(page.id) != null) return list
        return list.copy(pages = list.pages + page.copy(title = cleanTitle(page.title)))
    }

    fun rename(list: PageList, id: String, title: String): PageList =
        edit(list, id) { it.copy(title = cleanTitle(title)) }

    /** Moves page [id] by [delta] places, staying after home. */
    fun move(list: PageList, id: String, delta: Int): PageList {
        val from = list.indexOf(id)
        if (from <= 0) return list
        return moveTo(list, id, from + delta)
    }

    /** Moves page [id] to [index] (clamped to 1..last). */
    fun moveTo(list: PageList, id: String, index: Int): PageList {
        val from = list.indexOf(id)
        if (from <= 0) return list
        val to = index.coerceIn(1, list.size - 1)
        if (to == from) return list
        val pages = list.pages.toMutableList()
        val page = pages.removeAt(from)
        pages.add(to, page)
        return list.copy(pages = pages)
    }

    fun remove(list: PageList, id: String): PageList {
        if (id == Page.HOME_ID) return list
        return list.copy(pages = list.pages.filterNot { it.id == id })
    }

    /** The network switch exists only for single-file html pages; packages never reach the network. */
    fun setNetwork(list: PageList, id: String, on: Boolean): PageList =
        edit(list, id) { if (it.kind == PageKind.Html && !it.isPackage) it.copy(network = on) else it }

    /** After a re-import: new content, same place, same origin (so the page's storage is kept). */
    fun reimported(list: PageList, id: String, isPackage: Boolean): PageList =
        edit(list, id) {
            if (it.kind != PageKind.Html) it
            else it.copy(isPackage = isPackage, network = if (isPackage) false else it.network, rev = it.rev + 1)
        }

    /**
     * Keeps App Widget pages in step with the widget placements on the `Page` surface: a placement
     * whose slot no page references (a widget just bound for a new page, or pages restored without
     * their list) gets a page at the end. Pages whose widget is gone are kept, so the user sees an
     * honest "widget gone" page in its place rather than a page silently vanishing.
     */
    fun syncWidgetPages(list: PageList, placedSlots: Collection<Int>, newId: () -> String): PageList {
        // A slot named by an entry this version can't read is that entry's: no second page for it.
        val referenced = list.pages.filter { it.kind == PageKind.AppWidget }.mapTo(HashSet()) { it.widgetSlot } +
            unknownSlots(list)
        val missing = placedSlots.filter { it > 0 && it !in referenced }.distinct().sorted()
        if (missing.isEmpty()) return list
        val taken = list.pages.mapTo(HashSet()) { it.id }.apply { addAll(unknownIds(list)) }
        val added = missing.map { slot ->
            var id = newId()
            while (id in taken) id = newId()
            taken += id
            Page(id = id, kind = PageKind.AppWidget, widgetSlot = slot)
        }
        return list.copy(pages = list.pages + added)
    }

    /** A widget slot no page, no placement and no unread entry uses yet (1, 2, …). */
    fun nextWidgetSlot(list: PageList, placedSlots: Collection<Int>): Int {
        val used = list.pages.filter { it.kind == PageKind.AppWidget }.map { it.widgetSlot } + placedSlots + unknownSlots(list)
        return (used.maxOrNull() ?: 0).coerceAtLeast(0) + 1
    }

    /** Ids named by entries this version can't read ([PageList.unknown]); they keep their files. */
    fun unknownIds(list: PageList): Set<String> = list.unknown.mapNotNullTo(HashSet()) { it["id"] as? String }

    /** Widget slots named by entries this version can't read; they stay theirs. */
    fun unknownSlots(list: PageList): Set<Int> =
        list.unknown.mapNotNullTo(HashSet()) { e -> e.long("slot")?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() }

    /**
     * Which pages' imported files stay when the launcher tidies them at start: the html pages in the
     * list plus every id an unread entry names. Null = remove nothing: [listReadFully] is false when
     * the stored list was unreadable or written by a newer version (or such a list was kept aside),
     * and then the decoded list is not the whole truth, so no page's files may be swept.
     */
    fun filesToKeep(list: PageList, listReadFully: Boolean): Set<String>? {
        if (!listReadFully) return null
        return list.pages.filter { it.kind == PageKind.Html }.mapTo(HashSet()) { it.id } + unknownIds(list)
    }

    fun cleanTitle(title: String): String =
        title.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE)

    private fun edit(list: PageList, id: String, f: (Page) -> Page): PageList {
        if (id == Page.HOME_ID) return list
        var changed = false
        val pages = list.pages.map { if (it.id == id) f(it).also { n -> changed = n != it } else it }
        return if (changed) list.copy(pages = pages) else list
    }
}

/** How the user moves between pages (Settings → "Move between pages"); [TwoFingerDots] is the default. */
enum class NavMode(val wire: String) {
    /** Two-finger sideways swipe on every page, plus the page dots. */
    TwoFingerDots("two-finger"),

    /** Scrub past the coverflow's ends on home; an edge strip on full pages; the dots. */
    PastEnds("past-ends"),

    /** Pages stacked below home: swipe up on home, two-finger vertical swipe on full pages; the dots. */
    Vertical("vertical"),
    ;

    companion object {
        val Default = TwoFingerDots

        fun fromWire(s: String?): NavMode = entries.firstOrNull { it.wire == s } ?: Default
    }
}
