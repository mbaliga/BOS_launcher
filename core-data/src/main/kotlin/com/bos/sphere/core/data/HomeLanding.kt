package com.bos.sphere.core.data

/**
 * HOM-22: what the home shows. One at a time, switchable at any time; switching never changes the saved pages, folders,
 * widgets, tags or hidden apps, because only [LauncherPrefs.homeLanding] changes.
 */
enum class HomeLanding(val label: String) {
    /** The user's own pages, folders and widgets in the chosen home style (the home of HOM-01). */
    Selected("Selected apps"),
    /** The apps opened most recently, newest first, in the chosen home style, without pages. */
    Recents("Recent apps"),
    /** Every app alphabetically in the chosen home style, with the drawer's index and type-to-filter. */
    AllApps("All apps"),
    /** Only the wallpaper and the four hotspots. */
    Minimal("Minimal");

    /** The next landing in the order above; the last wraps to the first (the "Cycle home landing" command). */
    fun next(): HomeLanding = entries[(ordinal + 1) % entries.size]

    companion object {
        /** A stored name, or [Selected] for a missing or unknown one (a newer build's value never breaks an older one). */
        fun parse(name: String?): HomeLanding = entries.firstOrNull { it.name == name } ?: Selected
    }
}

/**
 * HOM-22: the pure rules of each landing. The UI asks here instead of testing the enum in scattered places, so "edit mode is
 * only for Selected apps" and "Minimal draws nothing else" are each stated once.
 */
object HomeLandingRules {
    /** What a long press on empty home does. */
    enum class LongPress { EditHome, QuickSheet }

    /** Long press into edit mode (the left card with the page carousel) is only for Selected apps; elsewhere it opens the quick sheet. */
    fun longPress(l: HomeLanding): LongPress = if (l == HomeLanding.Selected) LongPress.EditHome else LongPress.QuickSheet

    fun allowsEditMode(l: HomeLanding): Boolean = l == HomeLanding.Selected

    /** The landing that "Edit home pages" in the quick sheet switches to before it enters edit mode. */
    val editHomePagesLanding: HomeLanding = HomeLanding.Selected

    /** Pages (and so page dots, the page carousel, folders, widgets and tiles) exist only on the Selected apps landing. */
    fun hasPages(l: HomeLanding): Boolean = l == HomeLanding.Selected

    /** The grid, list, XMB or DSi style applies to every landing except Minimal. */
    fun usesHomeStyle(l: HomeLanding): Boolean = l != HomeLanding.Minimal

    /** Clock, status strip, dock, page dots and tiles: none of them on Minimal. */
    fun showsChrome(l: HomeLanding): Boolean = l != HomeLanding.Minimal

    /** The four hotspots are the only controls on Minimal; the other landings keep their usual corners. */
    fun isHotspotOnly(l: HomeLanding): Boolean = l == HomeLanding.Minimal

    /** The fresh-install hint ("Long press to add apps", "Or show recent apps") belongs to the empty Selected home only. */
    fun showsEmptyHomeHint(l: HomeLanding, homeIsEmpty: Boolean): Boolean = l == HomeLanding.Selected && homeIsEmpty

    /** Alphabet index and type-to-filter exist where the list is long and ordered: All apps. */
    fun hasAlphabetIndex(l: HomeLanding): Boolean = l == HomeLanding.AllApps

    fun hasTypeToFilter(l: HomeLanding): Boolean = l == HomeLanding.AllApps

    /** Work and Personal tabs follow the drawer on All apps; Recent apps marks work apps with the profile badge instead. */
    fun hasProfileTabs(l: HomeLanding): Boolean = l == HomeLanding.AllApps

    /** Recents offers "Remove from recents" and "Clear recents" on its items. */
    fun hasRecentsMenu(l: HomeLanding): Boolean = l == HomeLanding.Recents
}

/**
 * HOM-22: the four hotspots of the Minimal landing (INP-16, INP-17). They are the existing corner slots; this only decides
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
     * The slots the Minimal landing uses. A corner still holding the factory assignment ([DefaultCorners]) gets the Minimal
     * default; a corner the user assigned (including an explicit "Nothing") keeps its own. Nothing is written, so leaving
     * Minimal restores exactly what every other landing shows.
     */
    fun effective(corners: Map<Corner, CornerSlot>): Map<Corner, CornerSlot> =
        Corner.entries.associateWith { c ->
            val own = corners[c] ?: CornerSlot.Empty
            if (own == DefaultCorners[c] || (corners[c] == null)) defaults.getValue(c) else own
        }

    /** The dot hint is drawn when the setting is on and the landing is Minimal. */
    fun hintsVisible(l: HomeLanding, hintsOn: Boolean): Boolean = l == HomeLanding.Minimal && hintsOn
}
