package com.bos.sphere.core.data

/**
 * Wave 24 (HOM-02, HOM-07, HOM-15, HOM-17, HOM-18): where the home goes after an item is placed on it.
 *
 * Widgets, folders, shortcuts, links, groups and panel tiles live on the pages, and pages exist only on the Selected apps landing
 * ([HomeLandingRules.hasPages]). The quick sheet's "Widgets, folders and icons" is offered on every landing, so an item placed from
 * Glance, Recents or All apps went onto pages that were not on screen: placed, with nothing to show for it. The rule mirrors
 * "Edit home pages" ([HomeLandingRules.editHomePagesLanding]): placing an item moves to the landing that draws it. See
 * docs/decisions/homeLandingPlacement.md.
 */
object HomePlacementLanding {
    /** The landing to switch to after an item was placed while [current] was shown, or null to stay. */
    fun after(current: HomeLanding): HomeLanding? =
        if (HomeLandingRules.hasPages(current)) null else HomeLandingRules.editHomePagesLanding
}
