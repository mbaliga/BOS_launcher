package com.bos.sphere.feature.grid

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.exp

/** PAL-20: the pure arithmetic of Radar text size, shared by pinch and Ctrl+scroll in every layout. */
object RadarScaleMath {
    const val MIN = 0.8f
    const val MAX = 2.2f
    fun clamp(v: Float) = v.coerceIn(MIN, MAX)
    fun pinch(current: Float, zoom: Float) = clamp(current * zoom)

    /** One mouse-wheel notch is [scrollY] = ±1; scrolling up (negative y) grows the text, about 8% a notch. */
    fun ctrlScroll(current: Float, scrollY: Float) = clamp(current * exp(-scrollY * 0.077f))
}

/** The one text-size state of a Radar visit; every layout reads it through [LocalResultScale]. */
@Composable
internal fun rememberRadarScale(): MutableFloatState {
    val ctx = LocalContext.current
    return remember { mutableFloatStateOf(RadarPrefs.scale(ctx)) }
}

/** Pinch with two fingers or Ctrl+scroll anywhere on the results to resize the text; the size is remembered. Single touches pass through. */
@Composable
internal fun Modifier.radarScaleGestures(scale: MutableFloatState): Modifier {
    val ctx = LocalContext.current.applicationContext
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var changed = false
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.changes.count { it.pressed } >= 2) {
                        val z = ev.calculateZoom()
                        if (z != 1f) {
                            scale.floatValue = RadarScaleMath.pinch(scale.floatValue, z)
                            changed = true
                            ev.changes.forEach { it.consume() }
                        }
                    }
                } while (ev.changes.any { it.pressed })
                if (changed) RadarPrefs.setScale(ctx, scale.floatValue)
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.type == PointerEventType.Scroll && ev.keyboardModifiers.isCtrlPressed) {
                        val dy = ev.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (dy != 0f) {
                            scale.floatValue = RadarScaleMath.ctrlScroll(scale.floatValue, dy)
                            RadarPrefs.setScale(ctx, scale.floatValue)
                            ev.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        }
}
