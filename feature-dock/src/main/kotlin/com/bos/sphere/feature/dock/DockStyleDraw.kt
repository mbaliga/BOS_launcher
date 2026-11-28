package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import kotlin.math.min
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n

/** Wave 39 (dockThemes): drawing of the three dock styles, all Canvas and graphicsLayer (no bitmaps, no animation). */

private fun Int.c() = Color(this)

/** The rail's own surface: the old pill, nothing at all, or the 3D glass shelf. */
internal fun Modifier.dockSurface(style: DockStyle, dark: Boolean, edge: DockEdge, shape: Shape, cornerDp: Float = DockStyleTokens.CORNER_DP, shelfDepthDp: Float = DockStyleTokens.SHELF_DEPTH_DP, lipDp: Float = DockStyleTokens.LIP_DP): Modifier = when (style) {
    DockStyle.Standard -> clip(shape).background(HyleColors.SurfaceTranslucent).border(1.dp, HyleColors.Hairline, shape)
    DockStyle.Transparent -> this
    DockStyle.ThreeD -> shelf(dark, edge, shelfDepthDp, lipDp)
}

/**
 * Wave 56 (dockMagnify): the classic 3D dock. A glass shelf seen from slightly above: a translucent trapezoid floor (narrower at the back, the
 * perspective), a darker front lip, bevelled slanted ends, a bright line along the back edge, a soft inner shadow under it and a glow in the middle.
 * Drawn in one canonical frame (x along the dock, y from the back edge to the front lip) and turned for a side dock so the lip is on the screen edge.
 * Canvas only, no bitmap, nothing animates. The icons stand on the floor and are reflected in it (see [StyledIcon]).
 */
private fun Modifier.shelf(dark: Boolean, edge: DockEdge, depthDp: Float, lipDp: Float): Modifier {
    val t = DockStyleTokens.shelf(dark)
    return drawBehind {
        val d = density
        when (edge) {
            DockEdge.Bottom -> { val depth = min(size.height, depthDp * d); translate(0f, size.height - depth) { drawShelf(t, size.width, depth, lipDp * d, d) } }
            DockEdge.Left -> { val depth = min(size.width, depthDp * d); withTransform({ translate(depth, 0f); rotate(90f, Offset.Zero) }) { drawShelf(t, size.height, depth, lipDp * d, d) } }
            DockEdge.Right -> { val depth = min(size.width, depthDp * d); withTransform({ translate(size.width - depth, size.height); rotate(-90f, Offset.Zero) }) { drawShelf(t, size.height, depth, lipDp * d, d) } }
        }
    }
}

private fun DrawScope.drawShelf(t: DockStyleTokens.Shelf, length: Float, depth: Float, lipPx: Float, d: Float) {
    val lip = min(lipPx, depth * 0.4f)
    val y1 = depth - lip                       // where the floor meets the lip
    val insB = y1 * DockStyleTokens.BACK_INSET   // the back edge is narrower: the perspective
    val insF = 1.5f * d
    val floor = Path().apply { moveTo(insB, 0f); lineTo(length - insB, 0f); lineTo(length - insF, y1); lineTo(insF, y1); close() }
    val front = Path().apply { moveTo(insF, y1); lineTo(length - insF, y1); lineTo(length - insF - 1.2f * d, depth); lineTo(insF + 1.2f * d, depth); close() }
    // the shelf's shadow on the wall or wallpaper below it
    drawRect(Brush.verticalGradient(listOf(t.shadow.c(), Color.Transparent), startY = depth, endY = depth + 5f * d), Offset(insF + 2f * d, depth), Size(length - 2 * insF - 4f * d, 5f * d))
    drawPath(floor, Brush.verticalGradient(listOf(t.floorBack.c(), t.floorFront.c()), startY = 0f, endY = y1))
    clipPath(floor) {
        // the glow in the middle of the glass, a flat ellipse
        scale(1f, 0.32f, pivot = Offset(length / 2f, y1 * 0.5f)) {
            drawCircle(Brush.radialGradient(listOf(t.glow.c(), Color.Transparent), center = Offset(length / 2f, y1 * 0.5f), radius = length * 0.46f), radius = length * 0.46f, center = Offset(length / 2f, y1 * 0.5f))
        }
        // the inner shadow just under the back edge
        drawRect(Brush.verticalGradient(listOf(t.innerShadow.c(), Color.Transparent), startY = 0f, endY = 5f * d), size = Size(length, 5f * d))
    }
    drawPath(front, Brush.verticalGradient(listOf(t.lipTop.c(), t.lipBottom.c()), startY = y1, endY = depth))
    val hair = 1f * d
    // the bevel: the bright line along the back edge, the lit slanted ends, the edge where the floor turns down into the lip
    drawLine(t.topLine.c(), Offset(insB, 0.75f * d), Offset(length - insB, 0.75f * d), strokeWidth = 1.5f * d, cap = StrokeCap.Round)
    drawLine(t.sideLine.c(), Offset(insB, 0.75f * d), Offset(insF, y1), strokeWidth = hair, cap = StrokeCap.Round)
    drawLine(t.sideLine.c(), Offset(length - insB, 0.75f * d), Offset(length - insF, y1), strokeWidth = hair, cap = StrokeCap.Round)
    drawLine(t.junction.c(), Offset(insF, y1), Offset(length - insF, y1), strokeWidth = hair)
    drawLine(t.lipEdge.c(), Offset(insF + 1.2f * d, depth - 0.5f * d), Offset(length - insF - 1.2f * d, depth - 0.5f * d), strokeWidth = hair)
}

/** What a [StyledIcon] needs to follow the magnification: the icon's current scale and lift (pixels), read in draw and layer blocks only. */
internal class IconMotion(val scale: () -> Float, val lift: () -> Float)

/**
 * The icon of one slot, drawn for [style]: unchanged for Standard, with a halo for Transparent, standing on the glass shelf with a contact shadow and a
 * reflection in the floor for 3D. [edge] says where the shelf is (the reflection falls towards it); [motion] keeps the reflection on the floor while the
 * icon grows and lifts.
 */
@Composable
internal fun StyledIcon(style: DockStyle, dark: Boolean, item: DockItem, iconSize: Dp, edge: DockEdge = DockEdge.Bottom, reflect: Dp = DockStyleTokens.REFLECT_DP.dp, motion: IconMotion? = null, icon: @Composable (DockItem, Dp) -> Unit) {
    when (style) {
        DockStyle.Standard -> icon(item, iconSize)
        DockStyle.Transparent -> Box(
            Modifier.size(iconSize).drawBehind {
                val a = DockStyleTokens.HALO_ALPHA
                drawCircle(
                    Brush.radialGradient(0f to Color.Black.copy(alpha = a), DockStyleTokens.HALO_MID_AT to Color.Black.copy(alpha = a * DockStyleTokens.HALO_MID_ALPHA), 1f to Color.Transparent, center = center, radius = size.width * DockStyleTokens.HALO_RADIUS),
                    radius = size.width * DockStyleTokens.HALO_RADIUS, center = center,
                )
            },
            contentAlignment = Alignment.Center,
        ) { icon(item, iconSize) }
        DockStyle.ThreeD -> {
            val t = DockStyleTokens.shelf(dark)
            val horizontal = edge == DockEdge.Bottom
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                // The reflection in the floor: the icon mirrored across its foot, only the part that fits on the floor, fading out. Not a node of its own.
                Box(
                    Modifier.size(iconSize).clearAndSetSemantics { }
                        .graphicsLayer {
                            val s = motion?.scale?.invoke() ?: 1f
                            val lift = motion?.lift?.invoke() ?: 0f
                            val gap = DockStyleTokens.REFLECTION_GAP_DP * density
                            val full = (if (horizontal) iconSize.toPx() else iconSize.toPx())
                            // anchored to the floor: the slot's scale and lift would otherwise carry it away from the contact line
                            val local = (gap + lift) / s - gap
                            val away = full + gap + local
                            when (edge) {
                                DockEdge.Bottom -> { translationY = away; scaleY = -1f }
                                DockEdge.Left -> { translationX = -away; scaleX = -1f }
                                DockEdge.Right -> { translationX = away; scaleX = -1f }
                            }
                        }
                        // Fade it out away from the foot and keep it faint: an explicit layer (not a layer property), so it also draws on a software canvas.
                        .drawWithContent {
                            val s = motion?.scale?.invoke() ?: 1f
                            val visible = (reflect.toPx() / s).coerceAtMost(size.maxDimension)
                            drawIntoCanvas { c ->
                                c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint().apply { alpha = DockStyleTokens.REFLECTION_ALPHA })
                                drawContent()
                                // the content is mirrored by the layer: its foot (the part kept) is the bottom of the unmirrored icon for the bottom dock
                                val fade = when (edge) {
                                    DockEdge.Bottom -> Brush.verticalGradient(0f to Color.Transparent, 1f - visible / size.height to Color.Transparent, 1f to Color.Black)
                                    DockEdge.Left -> Brush.horizontalGradient(0f to Color.Black, visible / size.width to Color.Transparent, 1f to Color.Transparent)
                                    DockEdge.Right -> Brush.horizontalGradient(0f to Color.Transparent, 1f - visible / size.width to Color.Transparent, 1f to Color.Black)
                                }
                                drawRect(fade, blendMode = BlendMode.DstIn)
                                c.restore()
                            }
                        },
                ) { icon(item, iconSize) }
                // Where the icon stands: a small dark oval on the floor under it.
                Box(
                    Modifier.size(iconSize).drawBehind {
                        val w = size.width * 0.78f
                        val c = when (edge) { DockEdge.Bottom -> Offset(center.x, size.height - 1f * density); DockEdge.Left -> Offset(1f * density, center.y); DockEdge.Right -> Offset(size.width - 1f * density, center.y) }
                        scale(if (horizontal) 1f else 0.22f, if (horizontal) 0.22f else 1f, pivot = c) {
                            drawCircle(Brush.radialGradient(0f to t.iconShadow.c(), 1f to Color.Transparent, center = c, radius = w / 2f), radius = w / 2f, center = c)
                        }
                    },
                )
                icon(item, iconSize)
            }
        }
    }
}

/** The glyph colour for the rail's own controls: white over the wallpaper in Transparent (with a halo), the theme's ink otherwise. */
internal fun glyphColor(style: DockStyle, themed: Color): Color = if (style == DockStyle.Transparent) DockStyleTokens.GLYPH.c() else themed

/** A soft dark halo behind a small control (the home glyph, the plus ring, the divider) in the Transparent style; nothing in the others. */
internal fun Modifier.glyphHalo(style: DockStyle, shape: Shape = CircleShape): Modifier =
    if (style == DockStyle.Transparent) background(Color.Black.copy(alpha = DockStyleTokens.HALO_ALPHA), shape) else this

// ---------------------------------------------------------------------------------------------------------------------------------------
// The style's preview (the art of its card in the dock sheet and in Settings): the dock's own drawing code in miniature.
// ---------------------------------------------------------------------------------------------------------------------------------------

object DockStyleUi {
    fun rowTag(style: DockStyle) = "dock.style.${style.id}"
    const val TAG_PREVIEW = "dock.style.preview."
    fun previewTag(style: DockStyle) = TAG_PREVIEW + style.id
    fun stateText(style: DockStyle): String = L10n.tr("Dock style: %s", L10n.tr(style.label))
}

/** A miniature dock of [style] over a wallpaper-like gradient: drawn with the same code as the real dock, so what the preview shows is what the dock gets. */
@Composable
fun DockStylePreview(style: DockStyle, modifier: Modifier = Modifier, dark: Boolean = !HyleTheme.isLight, compact: Boolean = false) {
    val wall = Brush.linearGradient(listOf(Color(0xFFE9D8A6), Color(0xFF6FA8DC), Color(0xFF3B4C7A)))
    val icons = if (compact) 3 else 4
    val cols = listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFB36BFF)).take(icons)
    Box(modifier.size(if (compact) DpSize(72.dp, 48.dp) else DpSize(104.dp, 56.dp)).clip(RoundedCornerShape(10.dp)).background(wall).testTag(DockStyleUi.previewTag(style)).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (style == DockStyle.ThreeD) {
            // The shelf with the icons standing on it, their reflections in the floor and a running dot on the lip.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).height(34.dp).dockSurface(style, dark, DockEdge.Bottom, RoundedCornerShape(0.dp), shelfDepthDp = 17f, lipDp = 3f).padding(horizontal = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cols.forEachIndexed { i, col ->
                    Box(Modifier.size(22.dp, 34.dp)) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp)) {
                            StyledIcon(style, dark, DockItem(DockItemRef.app("preview$i"), ""), 18.dp, reflect = 8.dp) { _, sz -> GlossyTile(col, sz) }
                        }
                        if (i == 0) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 0.5.dp).size(3.dp).clip(CircleShape).background(if (dark) Color.White.copy(alpha = 0.9f) else Color(0xFF2A2F40)))
                    }
                }
            }
            return@Box
        }
        // W60: a percent shape, so the preview card's capsule matches the real dock's at any height (see DockRail.kt for the reasoning).
        val pill = RoundedCornerShape(50)
        Row(
            Modifier.dockSurface(style, dark, DockEdge.Bottom, pill, 12f).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            cols.forEachIndexed { i, col ->
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    StyledIcon(style, dark, DockItem(DockItemRef.app("preview$i"), ""), 18.dp) { _, s -> Box(Modifier.size(s).clip(RoundedCornerShape(5.dp)).background(col)) }
                }
            }
        }
    }
}

/** A glossy rounded-square tile (a lighter upper half, a bright rim at the top): the stand-in icon of the previews. */
@Composable
internal fun GlossyTile(color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(color).drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.5f to Color.White.copy(alpha = 0.12f), 0.52f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f)))
        },
    )
}
