package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/**
 * Wave 40 (settingsOneUI), CFG-21: the pure model of the One UI style settings: how the categories are grouped into the groups of
 * the root list, the accent of each category's icon tile, the summary line when the app gives none, how the rows of a category
 * page are cut into groups and headed by small section titles, and the maths of the collapsing title. No Compose in here, so every
 * rule is unit-tested on the JVM.
 */
object OneUiModel {
    /** The wide layout (list at the start, the page at the end) from this window width on, and only when the window is landscape. */
    const val TWO_PANE_MIN_DP = 600f

    fun twoPane(widthDp: Float, heightDp: Float): Boolean = widthDp >= TWO_PANE_MIN_DP && widthDp > heightDp

    /**
     * The English names of the categories, in the order and the groups One UI would give them: look and home first, then the
     * inputs, then notifications, then the system. A category that is not listed (a new one) lands in a group of its own before the last group.
     */
    val GROUPS: List<List<String>> = listOf(
        listOf(PAGE, "Home screen", "Workspaces"),
        listOf("Look", "App drawer", "Desktop & dock"),
        listOf("Corners", "Gestures", "Radar"),
        listOf(SIDE_PANEL),
        listOf("System & sound", "Hidden apps", "Backup", "Capabilities"),
        listOf("Lab", "About"),
    )

    /** Stands for the page category of the home's pages (wave 52: the row says "Pages", not "Page 1"; it holds the settings of the page under the finger). */
    const val PAGE = "Pages"

    /**
     * Wave 52: the settings of the right-hand panel. It was the "Comms" category; the panel can hold widgets too, so the row is named by what it is.
     * The sections inside the panel keep their own names (Comms and so on).
     */
    const val SIDE_PANEL = "Side panel"

    private val COUNT_SUFFIX = Regex("""^(.*) · \d+$""")

    /** The title to show: the tab-era "Hidden apps · 2" is plain "Hidden apps" (the count is in the summary line now). */
    fun displayTitle(title: String): String = COUNT_SUFFIX.matchEntire(title)?.groupValues?.get(1)?.takeIf { keyOf(it) != null } ?: title

    /** The English name of a category from its (maybe translated) [title]; null when it is not one of the known ones. */
    fun key(title: String): String? = keyOf(displayTitle(title))

    private fun keyOf(title: String): String? {
        for (g in GROUPS) for (k in g) {
            if (title == k || title == L10n.tr(k)) return k
        }
        return null
    }

    /** The category indices of [categories] cut into the groups of the root list, in order; every index appears exactly once. */
    fun groups(categories: List<SettingCategory>): List<List<Int>> {
        val byKey = categories.indices.groupBy { key(categories[it].title) }
        val out = ArrayList<List<Int>>()
        val unknown = byKey[null].orEmpty()
        GROUPS.forEachIndexed { gi, g ->
            if (gi == GROUPS.lastIndex && unknown.isNotEmpty()) out += unknown
            val idx = g.flatMap { byKey[it].orEmpty() }
            if (idx.isNotEmpty()) out += idx
        }
        return out
    }

    /** Muted flat accent (0xAARRGGBB) of the icon tile of a category. */
    fun accent(title: String): Long = when (key(title)) {
        PAGE, "Home screen" -> 0xFF4A86D9
        "Look" -> 0xFF9B6BD6
        "App drawer" -> 0xFF2FA3A0
        "Desktop & dock" -> 0xFF5C74C9
        "Corners" -> 0xFFD9923A
        "Gestures" -> 0xFF58A55C
        "Radar" -> 0xFF2F8FBF
        SIDE_PANEL -> 0xFFDE6A5E
        "Workspaces" -> 0xFF4FA38A
        "System & sound" -> 0xFF76859A
        "Hidden apps" -> 0xFF8C6CB4
        "Backup" -> 0xFF3F9E7A
        "Capabilities" -> 0xFFC48A2E
        "Lab" -> 0xFFB45F8C
        "About" -> 0xFF6D7C8C
        else -> 0xFF6D7C8C
    }

    /** The grey line under a category's title when the host gave none: the first two values worth showing, else how many settings it holds. */
    fun fallbackSummary(c: SettingCategory): String {
        val vals = c.items.filter { it is SettingItem.Choice<*> || it is SettingItem.Toggle || it is SettingItem.Slider }.map { it.value }.filter { it.isNotBlank() }.take(2)
        return if (vals.isNotEmpty()) vals.joinToString(" · ") else L10n.tr("%d settings", c.items.size)
    }

    /** What the root list shows under a category: the host's summary when it has one, else [fallbackSummary]. */
    fun summary(c: SettingCategory, given: Map<String, String>): String = given[c.title]?.takeIf { it.isNotBlank() } ?: fallbackSummary(c)

    // ---- category pages ---------------------------------------------------------------------------------------------------------

    sealed interface Block {
        data class Header(val text: String) : Block
        /** Indices (into the category's items) of the rows of one group. */
        data class Group(val rows: List<Int>) : Block
    }

    private fun navigates(i: SettingItem) = i is SettingItem.Action || i is SettingItem.Pane

    /** Rows that carry a row of live preview cards: they get their own group. */
    fun isRail(i: SettingItem) = i is SettingItem.Choice<*> && i.preview != null

    /**
     * Cuts [items] into groups: a new group starts at every [headers] entry (item index to header text, the header is emitted first), where
     * the rows change from controls (switches, choices, sliders) to links (actions, panes), and around a row of preview cards.
     */
    fun blocks(items: List<SettingItem>, headers: Map<Int, String> = emptyMap()): List<Block> {
        val out = ArrayList<Block>()
        var cur = ArrayList<Int>()
        fun flush() { if (cur.isNotEmpty()) { out += Block.Group(cur); cur = ArrayList() } }
        items.forEachIndexed { i, it ->
            val h = headers[i]
            if (h != null) { flush(); out += Block.Header(h) }
            else if (cur.isNotEmpty()) {
                val prev = items[cur.last()]
                if (isRail(it) || isRail(prev) || navigates(it) != navigates(prev)) flush()
            }
            cur += i
        }
        flush()
        return out
    }

    /** The index in [blocks] of the group holding row [item], or -1. */
    fun blockOf(blocks: List<Block>, item: Int): Int = blocks.indexOfFirst { it is Block.Group && item in it.rows }

    // ---- collapsing title ---------------------------------------------------------------------------------------------------------

    /**
     * 0 = the large title is fully shown, 1 = it has collapsed into the small centred one. [firstIndex] and [offsetPx] are the first visible
     * list item and its scroll offset; the large title is the first item, [headerPx] tall, and the bar is [barPx] tall.
     */
    fun collapse(firstIndex: Int, offsetPx: Int, headerPx: Float, barPx: Float): Float {
        if (firstIndex > 0) return 1f
        val span = (headerPx - barPx).coerceAtLeast(1f)
        return (offsetPx / span).coerceIn(0f, 1f)
    }

    /** The small title takes over once the large one is mostly gone. */
    fun smallTitleAlpha(collapse: Float): Float = ((collapse - 0.55f) / 0.45f).coerceIn(0f, 1f)

    /** The large title fades as it is pushed up. */
    fun largeTitleAlpha(collapse: Float): Float = (1f - collapse * 1.15f).coerceIn(0f, 1f)

    /** The large-title band of the list in the two-pane layout: its neighbour carries the tall one. */
    const val COMPACT_HEADER_DP = 112f

    /** The height of the large-title area: the upper third of the window, never less than 172 dp, so the floating preview window at its default anchor sits in the empty band beside the title or more than 260 dp. */
    fun headerDp(windowHeightDp: Float): Float = (windowHeightDp / 3f).coerceIn(172f, 260f)

    /**
     * Wave 46: the top of a block [contentPx] tall that sits in the vertical centre of a band [bandPx] tall (the large title between the
     * app bar and the first row). Never negative: a block taller than the band starts at the top.
     */
    fun centredTop(bandPx: Float, contentPx: Float): Float = ((bandPx - contentPx) / 2f).coerceAtLeast(0f)

    // ---- how a row offers its options (wave 46) ---------------------------------------------------------------------------------

    /**
     * Wave 46: the one way each kind of row presents its options, never two. [Rail]: a choice with preview cards shows them inline and has no
     * chevron and no page. [Page]: a choice without cards shows its value in grey with a chevron and opens a full page of radio rows (no dialog).
     * [Segmented]: two or three short options sit inline in a segmented control, no chevron. [Switch], [Slider], [Stepper] are inline controls.
     * [Link]: an action (a confirmation dialog first when it is destructive). [Pane]: a pane opens as a page of its own.
     */
    enum class Presentation(val inline: Boolean, val chevron: Boolean, val opensPage: Boolean) {
        Switch(true, false, false), Slider(true, false, false), Stepper(true, false, false),
        Rail(true, false, false), Segmented(true, false, false),
        Page(false, true, true), Pane(false, true, true), Link(false, false, false), LinkChevron(false, true, false),
    }

    fun presentation(i: SettingItem): Presentation = when (i) {
        is SettingItem.Toggle -> Presentation.Switch
        is SettingItem.Slider -> Presentation.Slider
        is SettingItem.Stepper -> Presentation.Stepper
        is SettingItem.Choice<*> -> if (i.preview != null) Presentation.Rail else Presentation.Page
        is SettingItem.Action -> if (i.navigates) Presentation.LinkChevron else Presentation.Link
        is SettingItem.Pane -> Presentation.Pane
    }

    // ---- navigation ---------------------------------------------------------------------------------------------------------------

    /** Where the user is: the root list ([category] -1), a category page, or a page of one of its rows ([sub] >= 0). */
    data class Nav(val category: Int = -1, val sub: Int = -1) {
        val level: Int get() = if (category < 0) 0 else if (sub < 0) 1 else 2
        fun open(category: Int) = Nav(category, -1)
        fun openSub(item: Int) = if (category >= 0) Nav(category, item) else this
        /** One step back; null when there is nowhere to go and the settings close. [twoPane] keeps a category selected (the list stays beside it). */
        fun back(twoPane: Boolean): Nav? = when {
            sub >= 0 -> Nav(category, -1)
            category >= 0 && !twoPane -> Nav(-1, -1)
            else -> null
        }
    }
}
