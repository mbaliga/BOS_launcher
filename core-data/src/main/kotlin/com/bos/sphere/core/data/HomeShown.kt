package com.bos.sphere.core.data

/**
 * Wave 26 (HOM-22, HOM-23): which surface of the home is on screen. There is no landing choice any more: the home is Glance, optionally
 * followed by the pages the user built ([Pages]: the scroll or the paginated pages). This is runtime state of the deck, not a setting;
 * the only settings are "Glance on / off", the pages mode and the order. See docs/decisions/homePages.md.
 */
enum class HomeShown { Glance, Pages }

/** The pure rules of each surface, so "edit mode is for the pages" and "Glance draws nothing else" are each stated once. */
object HomeShownRules {
    /** What a long press on empty home does. */
    enum class LongPress { EditHome, QuickSheet }

    /** A long press edits the pages; on Glance it opens the quick sheet (Pages chips, "Edit home", and "Add page" when Pages is Paginated). */
    fun longPress(s: HomeShown): LongPress = if (s == HomeShown.Pages) LongPress.EditHome else LongPress.QuickSheet

    fun allowsEditMode(s: HomeShown): Boolean = s == HomeShown.Pages

    /** The surface "Edit home" and "Add page" go to. */
    val editHomePagesSurface: HomeShown = HomeShown.Pages

    /** Pages (page dots, the page carousel, folders, widgets and tiles) exist on the pages surface only. */
    fun hasPages(s: HomeShown): Boolean = s == HomeShown.Pages

    /** The grid, list, XMB or DSi style applies to the pages, not to Glance. */
    fun usesHomeStyle(s: HomeShown): Boolean = s == HomeShown.Pages

    /** The four hotspots are the only controls on Glance. */
    fun isHotspotOnly(s: HomeShown): Boolean = s == HomeShown.Glance

    /** The fresh-install hint ("Long press to add apps") belongs to the empty pages. */
    fun showsEmptyHomeHint(s: HomeShown, homeIsEmpty: Boolean): Boolean = s == HomeShown.Pages && homeIsEmpty
}

/**
 * HOM-22: the four hotspots of Glance (INP-16, INP-17). They are the existing corner slots; this only decides
 * which assignment a corner has when the landing is Minimal.
 */
object MinimalHotspots {
    /** Radar, Launchpad, the drawer and Ground Control, one per corner (top-left, top-right, bottom-left, bottom-right). */
    val defaults: Map<Corner, CornerSlot> = mapOf(
        Corner.TopLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.Search)),
        Corner.TopRight to CornerSlot.Single(CornerItem.Action(CornerActionId.OpenLaunchpad)),
        Corner.BottomLeft to CornerSlot.Single(CornerItem.Action(CornerActionId.AllApps)),
        Corner.BottomRight to CornerSlot.Single(CornerItem.Action(CornerActionId.LauncherSettings)),
    )

    /**
     * The slots the Glance uses. A corner still holding the factory assignment ([DefaultCorners]) gets the Minimal
     * default; a corner the user assigned (including an explicit "Nothing") keeps its own. Nothing is written, so leaving
     * Glance restores exactly what every other landing shows.
     */
    fun effective(corners: Map<Corner, CornerSlot>): Map<Corner, CornerSlot> =
        Corner.entries.associateWith { c ->
            val own = corners[c] ?: CornerSlot.Empty
            if (own == DefaultCorners[c] || (corners[c] == null)) defaults.getValue(c) else own
        }

    /**
     * What a hotspot runs: a single assignment, or the first item of a palette (a fan-out has no room on Minimal). A corner that
     * holds a complication or a widget, or nothing, is not a hotspot.
     */
    fun itemOf(slot: CornerSlot): CornerItem? = when (slot) {
        is CornerSlot.Single -> slot.item
        is CornerSlot.Palette -> slot.items.firstOrNull()
        else -> null
    }

}
