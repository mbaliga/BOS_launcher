package com.bos.sphere.core.design

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.bos.sphere.core.quality.PredictiveBackMath
import kotlin.coroutines.cancellation.CancellationException

/**
 * NFR-19: back handling for an overlay surface that animates. While [enabled] (the surface is open and not yet leaving)
 * the system back gesture drives the returned progress (0 to 1); a committed back calls [onBack], a cancelled one
 * returns the progress to 0. Apply the progress with [predictiveBackLayer].
 */
@Composable
fun rememberPredictiveBackProgress(enabled: Boolean, onBack: () -> Unit): State<Float> {
    val progress = remember { mutableFloatStateOf(0f) }
    val current = rememberUpdatedState(onBack)
    LaunchedEffect(enabled) { if (enabled) progress.floatValue = 0f }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress.floatValue = PredictiveBackMath.clamp(it.progress) }
            current.value()
        } catch (e: CancellationException) {
            progress.floatValue = 0f
            throw e
        }
    }
    return progress
}

/** Shrinks and dims the surface by the held back gesture; no scale under reduced motion. */
@Composable
fun Modifier.predictiveBackLayer(progress: State<Float>): Modifier {
    val reduced = rememberMotionPrefs().reduced
    return graphicsLayer {
        val p = progress.value
        val s = PredictiveBackMath.scale(p, reduced)
        scaleX = s; scaleY = s
        alpha = PredictiveBackMath.alpha(p)
    }
}
