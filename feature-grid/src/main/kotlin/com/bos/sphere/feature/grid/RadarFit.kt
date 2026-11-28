package com.bos.sphere.feature.grid

/**
 * Wave 26 (owner: "Let the radar be circles as it was before and let them go out of the screen, it's fine"): the Orbit is back to circular rings
 * (see `Orbit` in SearchLayouts.kt) whose tiles may revolve past the screen edge. The wave 25 ellipse fitting that kept every tile inside the display was
 * removed; this object only keeps the one number the overlay still needs.
 */
internal object OrbitFit {
    /** The room the Orbit's wheel may take out of a region [availDp] high: half of it (plus its 8 dp of air), so the rings keep the other half. */
    fun wheelRoom(availDp: Float): Float = availDp * 0.5f + 8f
}
