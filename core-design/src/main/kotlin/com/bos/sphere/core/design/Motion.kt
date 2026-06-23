package com.bos.sphere.core.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Hyle motion vocabulary (build brief §4 / §6).
 *
 * The system ships eight *named* motion primitives so that motion, tactile (`:haptics`) and
 * sound stay one language — a given gesture always resolves to the same curve everywhere.
 * Each primitive is exposed as a reusable Compose [AnimationSpec]. The matching
 * `VibrationEffect`s live in the `:haptics` module and are keyed by the same names.
 */
object HyleMotion {

    /** The house easing: `cubic-bezier(.4, 0, .2, 1)` — Material standard, the prototype default. */
    val StandardEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Emphasised decelerate, for things arriving into focus. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Default duration (ms). */
    const val DurationDefault = 300
    const val DurationFast = 180
    const val DurationSlow = 520

    // --- The eight named primitives -----------------------------------------

    /** Breath — slow ambient pulse (live dots, the Hub "needs you" indicator). Infinite. */
    fun <T> breath(): AnimationSpec<T> = infiniteRepeatable(
        animation = tween(durationMillis = 3400, easing = StandardEasing),
        repeatMode = RepeatMode.Reverse,
    )

    /** Stir — gentle continuous agitation; momentum/idle drift on the globe. Infinite. */
    fun <T> stir(): AnimationSpec<T> = infiniteRepeatable(
        animation = tween(durationMillis = 5200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    )

    /** Reform — layout re-flow (rows 1↔2, orientation change). */
    fun <T> reform(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationDefault, easing = StandardEasing)

    /** Reach — the lean-forward acquire: tile/element coming to the focal point. */
    fun <T> reach(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationDefault, easing = EnterEasing)

    /** Erupt — sharp, energetic emphasis (launch confirmation, alert surfacing). */
    fun <T> erupt(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.42f,
        stiffness = Spring.StiffnessHigh,
    )

    /** Coalesce — scattered elements gathering (all-apps globe forming, search results). */
    fun <T> coalesce(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

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

/** The eight motion primitives as an enum so `:haptics` / `:audio` can key off the same names. */
enum class MotionPrimitive {
    Breath, Stir, Reform, Reach, Erupt, Coalesce, Give, Settle,
}
