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
        /**
         * STR-14: the Recents panel is the mirror image of Launchpad: the card goes to the left in left-to-right layouts and to the right in
         * right-to-left ones. The same geometry, card layer, scrim and settle are used (parameterized by this edge, not forked).
         */
        fun forRecents(rtl: Boolean) = if (rtl) Right else Left
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
    /** Black scrim over the card, 0 at home, about half at rest: the card dims as it recedes (video reference). */
    val scrimAlpha: Float,
    /** How visible the panel's content is: it fades in over the last part of the motion. 1 with reduced motion (the backdrop cross-fades instead). */
    val contentAlpha: Float,
)

data class CardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun clippedTo(w: Float, h: Float) = CardRect(left.coerceIn(0f, w), top.coerceIn(0f, h), right.coerceIn(0f, w), bottom.coerceIn(0f, h))
}

/**
 * STR-01 (wave 9 card tuning): where the home card sits for a reveal fraction 0..1. Pure maths (no Android types), tested on the JVM.
 * At 1 the home is a card at about 0.93 scale pushed against [edge] so that only a thin sliver (7 percent of the width) of its inner
 * edge, with the rounded corners, stays on screen; the notifications panel is full screen and static beneath it.
 * The card scales, translates, rounds and dims with the one fraction, so it follows a finger 1:1.
 */
class LaunchpadCardGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val edge: CardEdge,
    val density: Float,
    val reducedMotion: Boolean = false,
) {
    val landscape: Boolean get() = widthPx > heightPx

    /** Scale of the card at rest (top and bottom inset a few percent, like the video reference). */
    val fullScale: Float get() = 0.93f
    /** Where the card's near edge sits at rest, as a fraction of the window width from the side the card is NOT on. */
    val nearEdgeFraction: Float get() = 0.93f

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
            translationY = 0f,
            layerCornerDp = if (scale > 0f) corner / scale else corner,
            visibleCornerDp = corner,
            backdropAlpha = if (reducedMotion) fraction.coerceIn(0f, 1f) else 1f,
            shadowDp = 14f * e,
            scrimAlpha = SCRIM_AT_REST * e,
            contentAlpha = if (reducedMotion) 1f else ((e - CONTENT_FADE_FROM) / (1f - CONTENT_FADE_FROM)).coerceIn(0f, 1f),
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

    /** Width of the panel area the content lives in at rest: the window minus the sliver's tap zone. */
    val contentWidthPx: Float get() = (widthPx - returnZonePx).coerceAtLeast(0f)

    /** The visible sliver at rest, in px. */
    val sliverPx: Float get() = (1f - nearEdgeFraction) * widthPx

    /** The tap and drag zone for returning home: the visible sliver, widened to a 48 dp touch target when the sliver is thinner. */
    val returnZonePx: Float get() = maxOf(sliverPx, MIN_TARGET_DP * density)

    /** How much of the window the card still covers at rest, as a fraction of the width (the sliver). */
    val cardCoverFraction: Float get() = 1f - nearEdgeFraction

    /** Drag distance, as a fraction of the window width, that takes the card from home to fully pushed aside: the card's edge then tracks the finger 1:1. */
    val dragTravelFraction: Float get() = nearEdgeFraction

    /** Duration of the ease-out settle for taps, the handle, the shortcut and a released drag. */
    val settleMs: Int get() = SETTLE_MS

    companion object {
        const val CORNER_DP = 28f
        const val SCRIM_AT_REST = 0.5f
        /** The panel's content starts to appear after this much of the motion. */
        const val CONTENT_FADE_FROM = 0.45f
        const val MIN_TARGET_DP = 48f
        const val SETTLE_MS = 450
    }
}
