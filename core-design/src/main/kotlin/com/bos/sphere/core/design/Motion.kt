package com.bos.sphere.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Hyle motion vocabulary: the named curves the app still uses (reach, give, settle) and the two house easings, so a given gesture
 * resolves to the same curve everywhere. Each primitive is a reusable Compose animation spec.
 */
object HyleMotion {

    /** The house easing: `cubic-bezier(.4, 0, .2, 1)` — Material standard, the prototype default. */
    val StandardEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Emphasised decelerate, for things arriving into focus. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Default duration (ms). */
    const val DurationDefault = 300

    // --- The named primitives still in use: reach, give, settle (wave 20 removed the unused five) ---

    /** Reach — the lean-forward acquire: tile/element coming to the focal point. */
    fun <T> reach(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationDefault, easing = EnterEasing)

    /** Give — soft yielding release (drag let-go, chip dismiss). */
    fun <T> give(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Settle — damped come-to-rest; the coverflow snapping to an index. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
