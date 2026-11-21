package com.bos.sphere.core.catalog

/** Where a new intent to the home activity leads. */
sealed interface HomeIntentRoute {
    /** Radar asked for a home folder to open (HOM-04). */
    data class OpenFolder(val id: String) : HomeIntentRoute
    /** A launcher action forwarded by the motion service or its notification. */
    data class CornerAction(val name: String) : HomeIntentRoute
    /** The home button, or any other launch of the activity. */
    data object Home : HomeIntentRoute
}

/**
 * HOM-04: what the home activity does with a new intent. Opening a folder from Radar, like the home button, brings the home
 * forward, so the drawer, the search overlay, the settings and the other overlays close first (the same "home signal" the home
 * button raises); a forwarded corner action does not, because it may be asking for one of those overlays itself.
 */
object HomeIntentRouting {
    fun route(openFolder: String?, action: String?, knownAction: (String) -> Boolean): HomeIntentRoute = when {
        !openFolder.isNullOrEmpty() -> HomeIntentRoute.OpenFolder(openFolder)
        action != null && knownAction(action) -> HomeIntentRoute.CornerAction(action)
        else -> HomeIntentRoute.Home
    }

    fun closesOverlays(r: HomeIntentRoute): Boolean = r !is HomeIntentRoute.CornerAction
}
