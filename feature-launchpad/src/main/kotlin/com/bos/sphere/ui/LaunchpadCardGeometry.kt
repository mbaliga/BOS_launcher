package com.bos.sphere.ui

/** Which physical edge of the screen the shrunken home card is pushed against. */
enum class CardEdge {
    Right, Left;

    val sign: Float get() = if (this == Right) 1f else -1f

    companion object {
        /** Launchpad: the card goes to the right in left-to-right layouts and to the left in right-to-left ones. */
        fun forLaunchpad(rtl: Boolean) = if (rtl) Left else Right
        /** Home edit: the mirror image of Launchpad, so the page carousel takes the opposite side. */
        fun forEdit(rtl: Boolean) = if (rtl) Right else Left
    }
}

/** One frame of the card: a single scale and translate (plus a clip radius), applied as a graphicsLayer on the live home. */
data class CardPose(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    /** Corner radius to clip the unscaled layer with, in dp, so the visible radius after scaling is [visibleCornerDp]. */
    val layerCornerDp: Float,
    val visibleCornerDp: Float,
    /** How visible the background (notifications, or the page carousel) is. 1 except in the reduced-motion cross-fade. */
    val backdropAlpha: Float,
    val shadowDp: Float,
)

data class CardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun clippedTo(w: Float, h: Float) = CardRect(left.coerceIn(0f, w), top.coerceIn(0f, h), right.coerceIn(0f, w), bottom.coerceIn(0f, h))
}

/**
 * STR-01: where the home card sits for a reveal fraction 0..1. Pure maths (no Android types), tested on the JVM.
 * At 1 the home is a card about two thirds of its size, pushed against [edge] so its far side runs off the screen,
 * with the freed background beside it and a neighbouring card peeking in below it (the owner's mock-up).
 */
class LaunchpadCardGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val edge: CardEdge,
    val density: Float,
    val reducedMotion: Boolean = false,
) {
    val landscape: Boolean get() = widthPx > heightPx

    /** Scale of the card at rest in its pushed-aside pose. */
    val fullScale: Float get() = if (landscape) 0.72f else 0.64f
    /** Where the card's near edge sits, as a fraction of the window width from the side the card is NOT on. */
    val nearEdgeFraction: Float get() = if (landscape) 0.46f else 0.52f

    private val sideMargin get() = 16f * density
    private val peekGap get() = 14f * density

    /** The fraction that drives the pose: with reduced motion the card snaps and only the background cross-fades. */
    private fun eff(f: Float): Float {
        val c = f.coerceIn(0f, 1f)
        return if (reducedMotion) (if (c > 0f) 1f else 0f) else c
    }

    fun pose(fraction: Float): CardPose {
        val e = eff(fraction)
        val scale = 1f - (1f - fullScale) * e
        val fullTx = (nearEdgeFraction + fullScale / 2f - 0.5f) * widthPx
        val corner = CORNER_DP * e
        return CardPose(
            scale = scale,
            translationX = edge.sign * fullTx * e,
            translationY = -0.04f * heightPx * e,
            layerCornerDp = if (scale > 0f) corner / scale else corner,
            visibleCornerDp = corner,
            backdropAlpha = if (reducedMotion) fraction.coerceIn(0f, 1f) else 1f,
            shadowDp = 14f * e,
        )
    }

    /** The card as drawn on screen (before clipping to the window). */
    fun cardBounds(fraction: Float): CardRect {
        val p = pose(fraction)
        val w = widthPx * p.scale
        val h = heightPx * p.scale
        val cx = widthPx / 2f + p.translationX
        val cy = heightPx / 2f + p.translationY
        return CardRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** The slice of the card still on screen: this is what taps and drags on the card hit. */
    fun visibleCardBounds(fraction: Float): CardRect = cardBounds(fraction).clippedTo(widthPx, heightPx)

    /** The neighbouring page peeking in under the card, aligned with it. */
    fun neighbourBounds(fraction: Float): CardRect {
        val c = cardBounds(fraction)
        return CardRect(c.left, c.bottom + peekGap, c.right, c.bottom + peekGap + c.height)
    }

    /** Width of the background column the notifications (or carousel) live in, at rest, gutter included. */
    val backgroundColumnPx: Float get() = (nearEdgeFraction * widthPx - sideMargin).coerceAtLeast(0f)

    /** How much of the window the card still covers at rest, as a fraction of the width. */
    val cardCoverFraction: Float get() = 1f - nearEdgeFraction

    /** Drag distance, as a fraction of the window width, that takes the card from home to fully pushed aside. */
    val dragTravelFraction: Float get() = 0.5f

    companion object {
        const val CORNER_DP = 28f
    }
}
