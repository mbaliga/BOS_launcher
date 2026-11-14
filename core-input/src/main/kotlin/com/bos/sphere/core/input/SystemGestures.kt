package com.bos.sphere.core.input

/**
 * Where a touch must not start a launcher swipe because the system owns it (INP-08): the bottom edge is
 * the home and recents gesture, the side edges are back, the top edge opens the shade. A home-surface swipe
 * that begins inside one of these bands is ignored by the launcher, so its defaults never clash with them.
 */
object SystemGestureZones {
    const val SIDE_DP = 24f
    const val BOTTOM_DP = 32f
    const val TOP_DP = 24f

    enum class Swipe { Up, Down, Left, Right }

    /** True when a touch starting at ([x],[y]) sits in a band the system gesture for [swipe] uses. Sizes in one unit. */
    fun conflicts(x: Float, y: Float, width: Float, height: Float, swipe: Swipe, side: Float, bottom: Float, top: Float): Boolean = when (swipe) {
        Swipe.Up -> y >= height - bottom
        Swipe.Down -> y <= top
        Swipe.Left, Swipe.Right -> x <= side || x >= width - side
    }

    /** Any band at all: used when only the position is known. */
    fun inAnyBand(x: Float, y: Float, width: Float, height: Float, side: Float, bottom: Float, top: Float): Boolean =
        x <= side || x >= width - side || y >= height - bottom || y <= top
}
