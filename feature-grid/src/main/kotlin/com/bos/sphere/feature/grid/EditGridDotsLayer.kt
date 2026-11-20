package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bos.sphere.core.design.rememberMotionPrefs

/**
 * W45 (editChrome): the real grid while the home is edited: a 2 dp dot at every cell corner, 18% white, 40% within one cell of the active drag or selection
 * ([LocalEditGrid]). One Canvas, no composable per dot; the dots fade in over 150 ms (at once with reduced motion). Draw only: it takes no touch and has no
 * semantics.
 */
@Composable
internal fun EditGridDotsLayer(g: GridGeometry, modifier: Modifier = Modifier) {
    val state = LocalEditGrid.current
    val reduced = rememberMotionPrefs().reduced
    val fade = remember { Animatable(0f) }
    LaunchedEffect(reduced) { fade.animateTo(1f, tween(if (reduced) 0 else EditChromeGeometry.HINT_FADE_MS)) }
    val corners = remember(g) { EditGridDots.corners(g) }
    Box(modifier.fillMaxSize().testTag("edit.gridDots").clearAndSetSemantics { }.onGloballyPositioned { state?.layer = it }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            val active = state?.active // read here: a drag redraws the dots, it never recomposes the page
            val r = EditChromeGeometry.DOT_DP.dp.toPx() / 2f
            for ((x, y) in corners) drawCircle(Color.White.copy(alpha = EditGridDots.alpha(x, y, active, g.pitchX, g.pitchY)), r, Offset(x, y))
        }
    }
}
