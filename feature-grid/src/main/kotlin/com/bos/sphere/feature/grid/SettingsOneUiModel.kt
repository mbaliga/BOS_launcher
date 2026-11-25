package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/**
 * Wave 40 (settingsOneUI), CFG-21: the pure model of the One UI style settings: how the rows of a section are cut into groups and
 * headed by small section titles, how a row offers its options, and the maths of the collapsing title. Wave 57 (settingsTabs) removed the root
 * list of categories (its groups, tiles and summary lines): the six sections are tabs now ([SettingsTabs]). No Compose in here, so every
 * rule is unit-tested on the JVM.
 */
object OneUiModel {
    /** The wide layout (list at the start, the page at the end) from this window width on, and only when the window is landscape. */
    const val TWO_PANE_MIN_DP = 600f

    fun twoPane(widthDp: Float, heightDp: Float): Boolean = widthDp >= TWO_PANE_MIN_DP && widthDp > heightDp

    /** Wave 52: the settings of the right-hand panel ("Side panel", not "Comms"); wave 57: one of the six sections ([SettingsTabs.SIDE_PANEL]). */
    const val SIDE_PANEL = SettingsTabs.SIDE_PANEL

    /**
     * Wave 56 (CFG-32): the page of what is still unfinished (default home, notification and usage access, Do Not Disturb access). It is a category of the
     * list (so a search and the back stack treat it like any page) but it is not a tab (wave 57): Settings shows it as one flat row above the tabs, only while it exists.
     */
    const val FINISH_SETUP = "Finish setup"

    /** The English key of a category from its (maybe translated) [title]: one of the six sections, or the Finish setup page; null otherwise. */
    fun key(title: String): String? {
        if (title == FINISH_SETUP || title == L10n.tr(FINISH_SETUP)) return FINISH_SETUP
        return SettingsTabs.key(title)
    }

    /** The index of the Finish setup page in [categories], or -1 while nothing is unfinished. */
    fun finishSetupIndex(categories: List<SettingCategory>): Int = categories.indexOfFirst { key(it.title) == FINISH_SETUP }

    /** Muted flat accent (0xAARRGGBB) of the icon tile of the Finish setup row (wave 57: the only row of Settings home with a tile). */
    fun accent(title: String): Long = if (key(title) == FINISH_SETUP) 0xFF8E7BFF else 0xFF6D7C8C

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
     * Wave 46, changed in wave 54 (settingsVisual): the one way each kind of row presents its options, never two. [Rail]: a choice of two to
     * [VISUAL_MAX] options shows them inline as a row of drawn cards ([OneUiVisualChoice]): no chevron, no page. [Page]: a choice with more options
     * than that (the ten languages) shows its value in grey with a chevron and opens a full page. [Switch], [Slider], [Stepper] are inline controls.
     * [Link]: an action (a confirmation dialog first when it is destructive). [Pane]: a pane opens as a page of its own.
     */
    enum class Presentation(val inline: Boolean, val chevron: Boolean, val opensPage: Boolean) {
        Switch(true, false, false), Slider(true, false, false), Stepper(true, false, false),
        Rail(true, false, false),
        Page(false, true, true), Pane(false, true, true), Link(false, false, false), LinkChevron(false, true, false),
    }

    /** Wave 54: a pick from this many options or fewer is offered as cards; more is a list page. */
    const val VISUAL_MAX = 9

    /** Whether [c] must be offered as cards (two to [VISUAL_MAX] options). Every such choice of the settings carries its cards ([SettingItem.Choice.preview]); a test holds the builder to it. */
    fun offersCards(c: SettingItem.Choice<*>): Boolean = c.options.size in 2..VISUAL_MAX

    fun presentation(i: SettingItem): Presentation = when (i) {
        is SettingItem.Toggle -> Presentation.Switch
        is SettingItem.Slider -> Presentation.Slider
        is SettingItem.Stepper -> Presentation.Stepper
        is SettingItem.Choice<*> -> if (i.preview != null) Presentation.Rail else Presentation.Page
        is SettingItem.Action -> if (i.navigates) Presentation.LinkChevron else Presentation.Link
        is SettingItem.Pane -> Presentation.Pane
    }
}
