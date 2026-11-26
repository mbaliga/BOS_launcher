package com.bos.sphere.core.data

/**
 * Wave 58 (defects58, CFG-62): "Pinch changes grid density" binds pinch in and out to Denser and Sparser. Turning it on remembers the two bindings it
 * replaces ([Change.saved]); turning it off restores them. With nothing remembered (an install from before wave 58, or the toggle was on from an import)
 * it falls back to the defaults: pinch in opens Radar, pinch out does nothing. A null item is "nothing" on purpose.
 */
object PinchDensityToggle {
    val PINCHES = listOf(HomeGesture.PinchIn, HomeGesture.PinchOut)

    class Change(val bindings: Map<HomeGesture, CornerItem?>, val saved: Map<HomeGesture, CornerItem?>?)

    private val density: Map<HomeGesture, CornerItem?> = mapOf(
        HomeGesture.PinchIn to CornerItem.Action(CornerActionId.GridDenser),
        HomeGesture.PinchOut to CornerItem.Action(CornerActionId.GridSparser),
    )

    fun isOn(gestures: Map<HomeGesture, CornerItem>): Boolean = PINCHES.all { gestures[it] == density[it] }

    fun on(current: Map<HomeGesture, CornerItem>): Change =
        Change(density, if (isOn(current)) null else PINCHES.associateWith { current[it] })

    fun off(saved: Map<HomeGesture, CornerItem?>?): Change =
        Change(saved?.takeIf { it.keys.containsAll(PINCHES) } ?: PINCHES.associateWith { DefaultGestures[it] }, null)
}
