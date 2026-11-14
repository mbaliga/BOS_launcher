package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * CFG-18: the live preview dock. The real home draws itself into a [GraphicsLayer] (one modifier on the home surface),
 * and Ground Control draws that same layer, scaled, in a small frame. Nothing is rendered twice and nothing is a
 * mock-up: whatever the home looks like right now, with the setting just changed, is what the dock shows.
 */
class HomeCapture internal constructor(internal val layer: GraphicsLayer) {
    /** Size of the captured home in pixels; zero until it has been drawn once. */
    var size by mutableStateOf(IntSize.Zero)
        internal set
}

@Composable
fun rememberHomeCapture(): HomeCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { HomeCapture(layer) }
}

/** Put on the home surface: draws the home as usual and records it for the dock. */
fun Modifier.captureHome(capture: HomeCapture): Modifier = this.drawWithContent {
    capture.layer.record { this@drawWithContent.drawContent() }
    capture.size = IntSize(size.width.toInt(), size.height.toInt())
    drawLayer(capture.layer)
}

/** Pure sizing of the dock so it fits any window and keeps the home's own aspect. */
object DockGeometry {
    data class Box(val widthDp: Float, val heightDp: Float)

    /**
     * The dock's longest side is [maxFraction] of the window's short side, never above [maxDp];
     * the home's aspect ratio (landscape or portrait) is kept.
     */
    fun size(windowWidthDp: Float, windowHeightDp: Float, homeWidthPx: Int, homeHeightPx: Int, maxFraction: Float = 0.34f, maxDp: Float = 170f, minDp: Float = 72f): Box {
        if (homeWidthPx <= 0 || homeHeightPx <= 0) return Box(0f, 0f)
        val longest = (minOf(windowWidthDp, windowHeightDp) * maxFraction).coerceIn(minDp, maxDp)
        val aspect = homeWidthPx.toFloat() / homeHeightPx
        return if (aspect >= 1f) Box(longest, longest / aspect) else Box(longest * aspect, longest)
    }

    /** Scale from the home's pixel size to the dock's pixel size. */
    fun scale(homeWidthPx: Int, dockWidthPx: Float): Float = if (homeWidthPx <= 0) 0f else dockWidthPx / homeWidthPx
}

/**
 * The dock itself, anchored top-end under the Ground Control header. Always visible while settings are open, never
 * focusable and never in the way of the list (it sits above the empty right side of the category strip).
 */
@Composable
fun BoxScope.SettingsPreviewDock(capture: HomeCapture, modifier: Modifier = Modifier) {
    val conf = LocalConfiguration.current
    val box = DockGeometry.size(conf.screenWidthDp.toFloat(), conf.screenHeightDp.toFloat(), capture.size.width, capture.size.height)
    if (box.widthDp <= 0f) return
    val shape = RoundedCornerShape(12.dp)
    val label = L10n.tr("Live preview of your home")
    Box(
        modifier
            .align(Alignment.TopEnd)
            .padding(top = 64.dp, end = 12.dp)
            .size(box.widthDp.dp, box.heightDp.dp)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, HyleColors.InsetHighlight, shape)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(box.widthDp.dp, box.heightDp.dp)) {
            val k = DockGeometry.scale(capture.size.width, size.width)
            clipRect { scale(k, k, pivot = Offset.Zero) { drawLayer(capture.layer) } }
        }
    }
}
