package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * One section of the all-apps disc (wave 30): what the wedge shows and does. A section with neither [glyph] nor [app] is an empty slot.
 * [available] false draws it dimmed (the tap still runs [onClick], which explains why it cannot work); [onLongClick] is the empty slot's
 * "open the editor" (a filled one shows its name instead).
 */
class PuckAction(
    val label: String,
    val glyph: ImageVector? = null,
    val app: AppEntry? = null,
    val available: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
) {
    val empty: Boolean get() = glyph == null && app == null
}

/** The Back arrow on the hub's centre button. */
internal val BackGlyph: ImageVector = ImageVector.Builder("back", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.White), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
}.build()

private class WedgeShape(val path: Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(path)
}

/** Everything one wedge needs, built once per geometry (never per frame). */
private class WedgeDraw(
    val slot: Int,
    val left: Float, val top: Float, val width: Float, val height: Float,
    val shape: WedgeShape,
    val glyphX: Float, val glyphY: Float,
    val arcTopLeft: Offset, val arcSize: Size, val arcStart: Float, val arcSweep: Float,
)

/**
 * The four sections around the hub plus the Back button at the centre, drawn like the wedge sections of the Concepts quick switcher.
 * [corner] is the wheel's centre in px inside this box. [onBack] closes the drawer; [actions] are the sections in slot order (padded with
 * empty ones to four).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.PuckSectionsLayer(
    anchor: Corner,
    corner: Offset,
    ring: SectionRing,
    actions: List<PuckAction>,
    haptics: HapticFeedback,
    onBack: () -> Unit,
) {
    val density = LocalDensity.current
    val d = density.density
    val wedges = remember(anchor, ring, corner, d) {
        PuckSectionsGeometry.wedges(anchor, ring).map { w ->
            val b = PuckSectionsGeometry.bounds(w, ring)
            val c = PuckSectionsGeometry.centroid(w, ring)
            val left = corner.x + b.left * d; val top = corner.y + b.top * d
            val local = Offset(-b.left * d, -b.top * d)
            val outer = ring.outer * d; val inner = ring.inner * d
            val p = Path().apply {
                arcTo(Rect(local.x - outer, local.y - outer, local.x + outer, local.y + outer), w.startDeg, w.sweep, true)
                arcTo(Rect(local.x - inner, local.y - inner, local.x + inner, local.y + inner), w.endDeg, -w.sweep, false)
                close()
            }
            val er = outer - 1.5f * d
            WedgeDraw(
                w.slot, left, top, b.width * d, b.height * d, WedgeShape(p),
                local.x + c.first * d, local.y + c.second * d,
                Offset(local.x - er, local.y - er), Size(er * 2, er * 2), w.startDeg + 0.8f, w.sweep - 1.6f,
            )
        }
    }
    val surface = HyleColors.Surface
    val pressedFill = remember(surface) { lerp(surface, Color.Black, 0.78f) }
    val idleFill = remember(surface) { lerp(surface, HyleColors.Ink, 0.17f) }
    val emptyFill = remember(surface) { lerp(surface, HyleColors.Ink, 0.05f).copy(alpha = 0.6f) }
    val accent = HyleColors.Violet
    val arcStroke = remember(d) { Stroke(width = 2.5f * d, cap = StrokeCap.Round) }
    val hairline = remember(d) { Stroke(width = 1f * d) }
    val glyphDp = 22.dp
    val glyphPx = glyphDp.value * d
    var tip by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tip) { if (tip >= 0) { delay(1800); tip = -1 } }

    wedges.forEach { w ->
        val a = actions.getOrNull(w.slot) ?: PuckAction(L10n.tr("Drawer shortcut %d, empty", w.slot + 1)) {}
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val empty = a.empty
        val label = if (empty) L10n.tr("Drawer shortcut %d, empty", w.slot + 1) else a.label
        Box(
            Modifier
                .offset { IntOffset(w.left.roundToInt(), w.top.roundToInt()) }
                .size(with(density) { w.width.toDp() }, with(density) { w.height.toDp() })
                .semantics {
                    contentDescription = label
                    traversalIndex = (w.slot + 1).toFloat()
                    if (!a.available && !empty) stateDescription = L10n.tr("Not available on this device")
                }
                .clip(w.shape)
                .drawBehind {
                    val fill = when { pressed -> pressedFill; empty -> emptyFill; else -> idleFill }
                    drawPath(w.shape.path, fill)
                                        if (pressed) drawArc(accent, w.arcStart, w.arcSweep, false, w.arcTopLeft, w.arcSize, style = arcStroke)
                }
                .launcherItem("puck.section.${w.slot}", FocusKind.PuckRing, order = w.slot + 1, layer = 3, focusable = false, ringRadiusDp = 14f)
                .combinedClickable(
                    interactionSource = source, indication = null, role = Role.Button,
                    onLongClickLabel = if (empty) L10n.tr("Edit drawer shortcuts") else L10n.tr("Show name"),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (empty) a.onLongClick?.invoke() else tip = w.slot
                    },
                    onClick = {
                        if (empty) return@combinedClickable
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        a.onClick()
                    },
                ),
        ) {
            if (!empty) {
                val alpha = if (a.available) 1f else 0.38f
                Box(
                    Modifier
                        .offset { IntOffset((w.glyphX - glyphPx / 2f).roundToInt(), (w.glyphY - glyphPx / 2f).roundToInt()) }
                        .size(glyphDp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        a.app != null -> AppIcon(a.app, glyphDp, Modifier.graphicsLayer { this.alpha = alpha })
                        a.glyph != null -> Icon(a.glyph, null, tint = HyleColors.InkBright.copy(alpha = alpha), modifier = Modifier.size(glyphDp))
                    }
                }
            }
        }
    }

    // The long-press name, over the wedge it belongs to.
    val shown = wedges.getOrNull(tip)
    if (shown != null) {
        val name = actions.getOrNull(tip)?.label.orEmpty()
        Text(
            name, color = HyleColors.InkBright, fontSize = 12.sp,
            modifier = Modifier
                .offset { IntOffset((shown.left + shown.glyphX).roundToInt(), (shown.top + shown.glyphY).roundToInt()) }
                .offset(x = (-40).dp, y = (-34).dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }

    // The Back button: a plain round hub at the corner (Concepts' hub carries no glyph of its own; ours is the way out).
    val backDp = ring.backRadius * 2f
    val backSource = remember { MutableInteractionSource() }
    val backPressed by backSource.collectIsPressedAsState()
    Box(
        Modifier
            .offset { IntOffset((corner.x - ring.backRadius * d).roundToInt(), (corner.y - ring.backRadius * d).roundToInt()) }
            .size(backDp.dp)
            .semantics { contentDescription = L10n.tr("Back"); traversalIndex = 0f }
            .clip(CircleShape)
            .drawBehind {
                drawCircle(if (backPressed) Color(0xFF050507) else Color.Black)
                drawCircle(if (backPressed) accent else HyleColors.Hairline, radius = size.minDimension / 2f - 0.5f * d, style = hairline)
            }
            .launcherItem("puck.back", FocusKind.PuckHub, order = 0, layer = 3, focusable = false, ringRadiusDp = ring.backRadius)
            .clickable(interactionSource = backSource, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(BackGlyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(24.dp))
    }
}
