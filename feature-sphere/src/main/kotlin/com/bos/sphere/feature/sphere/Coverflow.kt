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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlin.math.abs

/** Base on-screen size of a tile in dp (the prototype's 132×132 card). */
const val TILE_SIZE_DP = 132f

/** Base on-screen size of a tile (the prototype's 132×132 card). */
internal val TileSize = TILE_SIZE_DP.dp

/**
 * Render-only coverflow equator. Paints [items] (apps, and hosted widgets drawn by [widgetTile])
 * as magnified tiles via `graphicsLayer` from the transforms in [state]. All gesture handling
 * lives in [LauncherSurface] so the navigation model (scrub / pinch / hub) is arbitrated in one
 * place.
 *
 * A widget tile is laid out at the focus-lens size ([SphereState.focusScale]) and scaled down by
 * the same factor, so it occupies exactly the box an app tile would, and the focused widget at
 * rest is drawn untransformed (scale 1, no rotation): taps reach the hosted view where it is drawn.
 * [widgetTile] receives `interactive = true` only for that focused, at-rest tile while
 * [widgetsInteractive] (no hub or globe in front).
 *
 * Performance: For large catalogs (500+ apps), only renders tiles within ±15 columns of the
 * current position, skipping off-screen transforms entirely.
 */
@Composable
fun CoverflowLayer(
    items: List<EquatorItem>,
    state: SphereState,
    modifier: Modifier = Modifier,
    widgetsInteractive: Boolean = false,
    widgetTile: @Composable (item: EquatorItem.Widget, interactive: Boolean) -> Unit = { _, _ -> },
) {
    val visibleRange by remember(state.position, state.columnCount) {
        derivedStateOf {
            val pos = state.position.toInt()
            val margin = 15
            (pos - margin).coerceAtLeast(0)..(pos + margin).coerceAtMost(state.maxIndex)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        items.forEachIndexed { index, item ->
            val col = state.columnOf(index)
            if (col in visibleRange) {
                // Keyed so a tile's state (icon bitmap, hosted widget view) follows its item when
                // widgets are inserted or moved, instead of sliding to a neighbour's slot.
                key(item.key) {
                    val transform by remember(index, state.position, state.params, state.rows) {
                        derivedStateOf { state.transformFor(index) }
                    }
                    if (transform.visible) {
                        when (item) {
                            is EquatorItem.App -> AppTile(
                                app = item.entry,
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

                            is EquatorItem.Widget -> {
                                val atRest = abs(col - state.position) < WidgetRestEpsilon
                                WidgetTileFrame(
                                    item = item,
                                    transform = transform,
                                    lens = state.focusScale,
                                    perspectiveDp = state.params.perspective,
                                ) {
                                    widgetTile(item, transform.focused && atRest && widgetsInteractive)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How close (in columns) a focused widget tile must be to rest before it takes touches. */
private const val WidgetRestEpsilon = 0.02f

/**
 * The card around a hosted widget: the prototype's `.surf.widget` (22 dp radius, violet hairline,
 * no label) plus its focus glow. Laid out at the lens size and scaled by `scale / lens`, so the
 * radius, border and glow are multiplied by [lens] to read the same as an app tile's after
 * scaling. The hosted content is clipped to the card.
 */
@Composable
private fun WidgetTileFrame(
    item: EquatorItem.Widget,
    transform: TileTransform,
    lens: Float,
    perspectiveDp: Float,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape((22f * lens).dp)
    val borderColor = if (transform.focused) {
        HyleColors.Violet.copy(alpha = 0.55f)
    } else {
        HyleColors.Violet.copy(alpha = 0.2f)
    }
    val glow = if (transform.focused) {
        Modifier.shadow(
            elevation = (14f * lens).dp,
            shape = shape,
            clip = false,
            ambientColor = HyleColors.Violet.copy(alpha = 0.55f),
            spotColor = HyleColors.Violet.copy(alpha = 0.55f),
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(TileSize * lens)
            .zIndex(transform.zIndex)
            .graphicsLayer {
                translationX = transform.translationX.dp.toPx()
                translationY = transform.translationY.dp.toPx()
                val s = transform.scale / lens
                scaleX = s
                scaleY = s
                rotationX = transform.rotationX
                rotationY = transform.rotationY
                cameraDistance = perspectiveDp.dp.toPx()
            }
            .then(glow)
            .clip(shape)
            .background(HyleColors.Surface, shape)
            .border(lens.dp, borderColor, shape)
            .semantics {
                contentDescription = "${item.label} widget" +
                    if (transform.focused) " - currently focused" else ""
            },
    ) {
        content()
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
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "${app.label}${if (transform.focused) " - currently focused" else ""}" },
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
 * Optimized: only checks tiles within visible range.
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

    val pos = state.position.toInt()
    val margin = 15
    val minCol = (pos - margin).coerceAtLeast(0)
    val maxCol = (pos + margin).coerceAtMost(state.maxIndex)

    for (i in 0 until itemCount) {
        val col = state.columnOf(i)
        if (col < minCol || col > maxCol) continue
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
