package com.bos.sphere.core.quality

import android.content.ContentResolver
import android.provider.Settings

/** How a surface change (morph) is presented. */
enum class MorphMode { Morph, CrossFade }

/**
 * NFR-14: reduced motion. The system's animation scales are the signal (Settings.Global, public API): when the
 * user sets animations off (scale 0), surface morphs become cross-fades and durations collapse.
 */
data class MotionPrefs(val animatorScale: Float, val transitionScale: Float, val windowScale: Float = 1f) {
    val reduced: Boolean get() = animatorScale <= 0f || transitionScale <= 0f || windowScale <= 0f
    val morphMode: MorphMode get() = if (reduced) MorphMode.CrossFade else MorphMode.Morph

    /** Duration to use for an animation that normally takes [baseMs]; the scale is honoured, 0 means no motion. */
    fun durationMs(baseMs: Int): Int = if (reduced) 0 else (baseMs * animatorScale).toInt().coerceAtLeast(0)

    /** Cross-fades stay short but visible so a change is still perceptible. */
    fun crossFadeMs(baseMs: Int = 120): Int = if (reduced) 90 else durationMs(baseMs)

    companion object {
        val Default = MotionPrefs(1f, 1f, 1f)

        fun read(resolver: ContentResolver): MotionPrefs = MotionPrefs(
            animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
            transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
            windowScale = Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f),
        )
    }
}
