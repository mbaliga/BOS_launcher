package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A plain rectangle in px, kept free of Compose types so the geometry is plain JVM maths. */
data class EdgeRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Which side of the screen the shrunk home card is pushed to. */
enum class CardSide { Left, Right }

/**
 * Edit home (HOM-12) and Launchpad (STR-01) share one picture: the live home shrinks into a rounded card with
 * about 28 dp corners, pushed against one screen edge so that edge runs off screen, and the freed background houses
 * something else. Edit mode puts the card on the *start* side (left in LTR, right in RTL) and a page carousel on the
 * other; Launchpad mirrors it. Everything here is pure: the screen size goes in, the card, the carousel column and the
 * add-carousel band come out, as one scale and one translation of the home's own layer (the home is never
 * recomposed or captured).
 */
object EditCardGeometry {
    const val CORNER_DP = 28f
    const val PORTRAIT_SCALE = 0.68f
    const val LANDSCAPE_SCALE = 0.60f
    /** How much of the card's pushed side is off screen, as a fraction of the card width. */
    const val OFF_SCREEN = 0.04f
    const val MIN_COLUMN_FRACTION = 0.26f

    /** The end state of the card for a [widthPx] by [heightPx] window. [topReservePx] / [bottomReservePx] keep the banner and bars clear. */
    data class Target(
        val scale: Float,
        /** Translation of the layer from where it sits full screen (the layer's pivot is its own centre). */
        val translationX: Float,
        val translationY: Float,
        val card: EdgeRect,
        /** The scrolling page carousel (vertical column) in the freed background. */
        val carousel: EdgeRect,
        /** The add carousel (horizontal strip) under the card in portrait, at the foot of the column in landscape. */
        val addBand: EdgeRect,
        val side: CardSide,
        val landscape: Boolean,
    )

    fun target(
        widthPx: Float, heightPx: Float, rtl: Boolean,
        topReservePx: Float = 0f, bottomReservePx: Float = 0f, addBandPx: Float = 0f,
        side: CardSide? = null,
    ): Target {
        val w = widthPx.coerceAtLeast(1f); val h = heightPx.coerceAtLeast(1f)
        val landscape = w > h
        val s = side ?: if (rtl) CardSide.Right else CardSide.Left
        val base = if (landscape) LANDSCAPE_SCALE else PORTRAIT_SCALE
        // In portrait the add carousel sits under the card, so the card must leave room for it.
        val belowPx = bottomReservePx + if (landscape) 0f else addBandPx
        val fit = ((h - topReservePx - belowPx) / h).coerceAtLeast(0.3f)
        val maxByColumn = (1f - MIN_COLUMN_FRACTION) / (1f - OFF_SCREEN)
        val scale = minOf(base, fit, maxByColumn)
        val cw = w * scale; val ch = h * scale
        val off = cw * OFF_SCREEN
        val left = if (s == CardSide.Left) -off else w - cw + off
        val top = topReservePx.coerceAtLeast(0f)
        val card = EdgeRect(left, top, left + cw, top + ch)
        // The layer is laid out full screen and scaled about its centre: translation puts the scaled centre where the card's centre is.
        val tx = card.left + cw / 2f - w / 2f
        val ty = card.top + ch / 2f - h / 2f
        val colLeft = if (s == CardSide.Left) card.right else 0f
        val colRight = if (s == CardSide.Left) w else card.left
        val colBottom = if (landscape) (h - bottomReservePx - addBandPx).coerceAtLeast(top) else card.bottom
        val carousel = EdgeRect(colLeft, top, colRight, colBottom)
        val addBand = if (landscape) EdgeRect(colLeft, colBottom, colRight, (h - bottomReservePx).coerceAtLeast(colBottom))
        else EdgeRect(0f, card.bottom, w, (h - bottomReservePx).coerceAtLeast(card.bottom))
        return Target(scale, tx, ty, card, carousel, addBand, s, landscape)
    }

    /** The layer transform at [progress] 0 (full screen) .. 1 (card). */
    data class Frame(val scale: Float, val translationX: Float, val translationY: Float, val cornerDp: Float, val carouselAlpha: Float, val shadowAlpha: Float)

    /**
     * NFR-14: with reduced motion there is no shrink: the card is at rest from the first frame of the change and the
     * carousel cross-fades in with [progress] (and out again on the way back).
     */
    fun frame(t: Target, progress: Float, reduced: Boolean): Frame {
        val p = progress.coerceIn(0f, 1f)
        if (reduced) {
            val on = p > 0f
            return Frame(if (on) t.scale else 1f, if (on) t.translationX else 0f, if (on) t.translationY else 0f, if (on) CORNER_DP else 0f, p, if (on) 1f else 0f)
        }
        fun lerp(a: Float, b: Float) = a + (b - a) * p
        return Frame(lerp(1f, t.scale), lerp(0f, t.translationX), lerp(0f, t.translationY), CORNER_DP * p, p, p)
    }

    /** True when the point (x, y) in screen px is on the card (a tap there while it is a Launchpad card brings the home back). */
    fun hitsCard(t: Target, x: Float, y: Float): Boolean = x >= t.card.left && x <= t.card.right && y >= t.card.top && y <= t.card.bottom
}

/**
 * The one graphicsLayer scale and translate on the existing home composition. The content under it keeps its
 * state; touches follow the layer, so drag, drop-onto and resize handles work on the card.
 */
fun Modifier.editCardLayer(frame: () -> EditCardGeometry.Frame): Modifier = graphicsLayer {
    val f = frame()
    transformOrigin = TransformOrigin.Center
    scaleX = f.scale; scaleY = f.scale
    translationX = f.translationX; translationY = f.translationY
    if (f.cornerDp > 0.01f) {
        shape = RoundedCornerShape((f.cornerDp / f.scale.coerceAtLeast(0.1f)).dp)
        clip = true
    }
}

/** The progress of the card transition, animated (a jump with reduced motion; the carousel then cross-fades). */
@Composable
fun rememberEditCardProgress(active: Boolean): State<Float> {
    val reduced = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    // A State, read only inside graphicsLayer / draw lambdas, so the frames of the transition never recompose the home.
    return animateFloatAsState(if (active) 1f else 0f, tween(if (reduced) 160 else 380), label = "editCard")
}
