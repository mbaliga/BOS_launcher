package com.bos.sphere.feature.sphere

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlin.math.abs

/** Base on-screen size of a tile (the prototype's 132×132 card). */
internal val TileSize = 132.dp

/**
 * Render-only coverflow equator. Paints [apps] as magnified tiles via `graphicsLayer` from the
 * transforms in [state]. All gesture handling lives in [LauncherSurface] so the navigation model
 * (scrub / pinch / hub) is arbitrated in one place.
 */
@Composable
fun CoverflowLayer(
    apps: List<AppEntry>,
    state: SphereState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        apps.forEachIndexed { index, app ->
            val transform by remember(index, state) {
                derivedStateOf { state.transformFor(index) }
            }
            if (transform.visible) {
                AppTile(
                    app = app,
                    transform = transform,
                    modifier = Modifier
                        .size(TileSize)
                        .zIndex(transform.zIndex)
                        .graphicsLayer {
                            translationX = transform.translationX.dp.toPx()
                            translationY = transform.translationY.dp.toPx()
                            scaleX = transform.scale
                            scaleY = transform.scale
                            rotationX = transform.rotationX
                            rotationY = transform.rotationY
                            cameraDistance = state.params.perspective.dp.toPx()
                        },
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    transform: TileTransform,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (transform.focused) {
        HyleColors.Violet.copy(alpha = 0.55f)
    } else {
        HyleColors.Hairline
    }
    Box(
        modifier = modifier
            .background(HyleColors.Surface, RoundedCornerShape(22.dp))
            .border(1.dp, borderColor, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = remember(app.key) {
            app.loadIcon()?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
        }
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(60.dp))
        }
        if (transform.focused) {
            Text(
                text = app.label,
                color = HyleColors.InkBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = 36.dp.toPx() },
            )
        }
    }
}

/**
 * Returns the index of the top-most tile whose transformed rect contains [tap], or -1.
 * Tile centers are reconstructed from the same transforms used to paint them.
 */
internal fun coverflowHitTest(
    tap: Offset,
    itemCount: Int,
    state: SphereState,
    container: IntSize,
    density: Float,
): Int {
    val cx = container.width / 2f
    val cy = container.height / 2f
    val halfBase = TileSize.value * density / 2f
    var best = -1
    var bestZ = Float.NEGATIVE_INFINITY
    for (i in 0 until itemCount) {
        val t = state.transformFor(i)
        if (!t.visible) continue
        val tileCx = cx + t.translationX * density
        val tileCy = cy + t.translationY * density
        val half = halfBase * t.scale
        if (abs(tap.x - tileCx) <= half && abs(tap.y - tileCy) <= half && t.zIndex > bestZ) {
            best = i
            bestZ = t.zIndex
        }
    }
    return best
}
