package com.bos.sphere.core.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import com.bos.sphere.core.quality.MotionPrefs

/**
 * NFR-14: gate for one-shot `animate*AsState` micro-transitions. With animations off the value jumps
 * to its target in one frame instead of easing there; otherwise [spec] runs unchanged.
 */
fun <T> MotionPrefs.gate(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (reduced) snap() else spec
