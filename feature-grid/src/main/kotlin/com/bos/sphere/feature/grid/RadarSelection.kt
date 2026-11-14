package com.bos.sphere.feature.grid

import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The key of the result the keyboard is on, or null when the keyboard is not driving (INP-06: focus rings only in keyboard mode). */
internal val LocalRadarSelection = compositionLocalOf<String?> { null }

/** A ring round the keyboard-selected result, drawn the same way in all three layouts. */
internal fun Modifier.radarSelectedRing(selected: Boolean, shape: Shape): Modifier =
    if (selected) this.border(2.dp, Color(0xFFB9A8FF), shape) else this
