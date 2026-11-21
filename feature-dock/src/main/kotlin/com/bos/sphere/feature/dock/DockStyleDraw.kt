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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n

/** Wave 39 (dockThemes): drawing of the three dock styles, all Canvas and graphicsLayer (no bitmaps, no animation). */

private fun Int.c() = Color(this)

/** The rail's own surface: the old pill, nothing at all, or the glossy 3D slab. */
internal fun Modifier.dockSurface(style: DockStyle, dark: Boolean, edge: DockEdge, shape: Shape, cornerDp: Float = DockStyleTokens.CORNER_DP): Modifier = when (style) {
    DockStyle.Standard -> clip(shape).background(HyleColors.SurfaceTranslucent).border(1.dp, HyleColors.Hairline, shape)
    DockStyle.Transparent -> this
    DockStyle.ThreeD -> slab(dark, edge, shape, cornerDp)
}

private fun Modifier.slab(dark: Boolean, edge: DockEdge, shape: Shape, cornerDp: Float): Modifier {
    val t = DockStyleTokens.slab(dark)
    return drawBehind {
        // The contact shadow: stacked translucent rounded rectangles, each a little larger and further along the light's direction.
        val (dx, dy) = DockStyleTokens.shadowDirection(edge)
        val r = cornerDp.dp.toPx()
        val reach = DockStyleTokens.CONTACT_DP.dp.toPx()
        val total = (t.contactShadow ushr 24) / 255f
        for (i in 0 until DockStyleTokens.CONTACT_LAYERS) {
            val f = (i + 1f) / DockStyleTokens.CONTACT_LAYERS
            val grow = reach * f * 0.5f
            drawRoundRect(
                Color.Black.copy(alpha = DockStyleTokens.contactLayerAlpha(i, total).coerceIn(0f, 1f)),
                topLeft = Offset(dx * reach * f * 0.6f - grow, dy * reach * f * 0.6f - grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(r + grow),
            )
        }
    }.clip(shape).background(Brush.verticalGradient(listOf(t.faceTop.c(), t.faceBottom.c()))).drawBehind {
        val r = cornerDp.dp.toPx()
        val px = 1.dp.toPx()
        // Inner shadow: a band under the top edge and a darker lip along the bottom.
        drawRect(Brush.verticalGradient(listOf(t.innerShadow.c(), Color.Transparent), startY = 0f, endY = 9.dp.toPx()), size = Size(size.width, 9.dp.toPx()))
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, t.innerShadow.c()), startY = size.height - 12.dp.toPx(), endY = size.height),
            topLeft = Offset(0f, size.height - 12.dp.toPx()), size = Size(size.width, 12.dp.toPx()),
        )
        // Top highlight: a bright stroke along the top edge that fades out down the sides.
        drawRoundRect(
            Brush.verticalGradient(0f to t.highlight.c(), DockStyleTokens.HIGHLIGHT_REACH to Color.Transparent, 1f to Color.Transparent),
            topLeft = Offset(px * 1.5f, px * 1.5f), size = Size(size.width - px * 3, size.height - px * 3), cornerRadius = CornerRadius(r - px * 1.5f), style = Stroke(1.5f * px),
        )
        // Bevelled edge: light on top, dark underneath.
        drawRoundRect(
            Brush.verticalGradient(listOf(t.bevelLight.c(), Color.Transparent, t.bevelDark.c())),
            topLeft = Offset(px / 2, px / 2), size = Size(size.width - px, size.height - px), cornerRadius = CornerRadius(r - px / 2), style = Stroke(px),
        )
    }
}

/** The icon of one slot, drawn for [style]: unchanged for Standard, with a halo for Transparent, raised with a shadow and a reflection for 3D. */
@Composable
internal fun StyledIcon(style: DockStyle, dark: Boolean, item: DockItem, iconSize: Dp, icon: @Composable (DockItem, Dp) -> Unit) {
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
            val raise = DockStyleTokens.ICON_RAISE_DP.dp
            val t = DockStyleTokens.slab(dark)
            Box(Modifier.size(iconSize).offset(y = -raise), contentAlignment = Alignment.Center) {
                // The faint reflection on the shelf: the icon mirrored under itself, faded out downwards. Not a node of its own.
                Box(
                    Modifier.size(iconSize).clearAndSetSemantics { }
                        .graphicsLayer { translationY = iconSize.toPx() + DockStyleTokens.REFLECTION_GAP_DP.dp.toPx(); scaleY = -1f }
                        // Fade it out downwards and keep it faint: an explicit layer (not a layer property), so it also draws on a software canvas.
                        .drawWithContent {
                            drawIntoCanvas { c ->
                                c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint().apply { alpha = DockStyleTokens.REFLECTION_ALPHA })
                                drawContent()
                                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f - DockStyleTokens.REFLECTION_VISIBLE_DP.dp.toPx() / size.height to Color.Transparent, 1f to Color.Black), blendMode = BlendMode.DstIn)
                                c.restore()
                            }
                        },
                ) { icon(item, iconSize) }
                // The icon's own small shadow: a squashed dark oval under it.
                Box(
                    Modifier.size(iconSize).drawBehind {
                        val w = size.width * 0.74f
                        val cy = size.height - 1.5.dp.toPx()
                        scale(1f, 0.22f, pivot = Offset(center.x, cy)) {
                            drawCircle(Brush.radialGradient(0f to t.iconShadow.c(), 1f to Color.Transparent, center = Offset(center.x, cy), radius = w / 2f), radius = w / 2f, center = Offset(center.x, cy))
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
// The chooser: three radio rows with a small preview each (Ground Control, and the dock's own long-press menu).
// ---------------------------------------------------------------------------------------------------------------------------------------

object DockStyleUi {
    fun rowTag(style: DockStyle) = "dock.style.${style.id}"
    const val TAG_PREVIEW = "dock.style.preview."
    fun previewTag(style: DockStyle) = TAG_PREVIEW + style.id
    fun stateText(style: DockStyle): String = L10n.tr("Dock style: %s", L10n.tr(style.label))
}

/** A miniature dock of [style] over a wallpaper-like gradient: drawn with the same code as the real dock, so what the preview shows is what the dock gets. */
@Composable
fun DockStylePreview(style: DockStyle, modifier: Modifier = Modifier, dark: Boolean = !HyleTheme.isLight) {
    val shape = RoundedCornerShape(12.dp)
    val wall = Brush.linearGradient(listOf(Color(0xFFE9D8A6), Color(0xFF6FA8DC), Color(0xFF3B4C7A)))
    Box(modifier.size(104.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(wall).testTag(DockStyleUi.previewTag(style)).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val pill = RoundedCornerShape(12.dp)
        Row(
            Modifier.dockSurface(style, dark, DockEdge.Bottom, pill, 12f).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFF4F8CFF), Color(0xFFFF7A59), Color(0xFF3DDC84), Color(0xFFB36BFF)).forEachIndexed { i, col ->
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    StyledIcon(style, dark, DockItem(DockItemRef.app("preview$i"), ""), 18.dp) { _, s -> Box(Modifier.size(s).clip(RoundedCornerShape(5.dp)).background(col)) }
                }
            }
        }
    }
}

/** Three radio rows (Standard, Transparent, 3D), each with its preview; [onSelect] gets the choice. 48 dp rows, one node per choice. */
@Composable
fun DockStyleChooser(current: DockStyle, onSelect: (DockStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = L10n.tr("Dock style") }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DockStyle.entries.forEach { s ->
            val selected = s == current
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
                    .background(if (selected) HyleColors.Violet.copy(alpha = 0.18f) else HyleColors.Hairline.copy(alpha = 0.06f))
                    .then(if (selected) Modifier.border(1.5.dp, HyleColors.Violet, shape) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(s) })
                    .testTag(DockStyleUi.rowTag(s))
                    .semantics(mergeDescendants = true) { contentDescription = L10n.tr(s.label); stateDescription = if (selected) L10n.tr("Selected") else L10n.tr("Not selected") }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockStylePreview(s)
                Spacer(Modifier.width(14.dp))
                Text(L10n.tr(s.label), color = HyleColors.InkBright, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
        Text(L10n.tr("Transparent draws no panel: only the icons, with a soft halo. 3D is a glossy shelf with raised icons."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The dock's own long-press route to the same choice: a full page (wave 46), not a popup, because it holds a set of options. */
@Composable
internal fun DockStyleDialog(current: DockStyle, onSelect: (DockStyle) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook) {
        com.bos.sphere.feature.grid.OneUiFullPage(onDismiss) {
            com.bos.sphere.feature.grid.OneUiPageFrame(L10n.tr("Dock style"), onDismiss, tag = "dock.styleDialog") {
                item(key = "style") {
                    Column(Modifier.padding(horizontal = 18.dp)) { DockStyleChooser(current, onSelect = { onSelect(it); onDismiss() }) }
                }
            }
        }
    }
}
