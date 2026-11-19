package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.drawscope.clipPath
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.input.InputMode
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

/**
 * Colours of the disc (wave 32), after the Concepts quick switcher: a solid ring with hairline dividers, glyphs in the divider colour, a hub
 * in the opposite tone. Dark themes: white ring, black glyphs and dividers, black hub with a white arrow. Light theme: a near-black ring
 * (relative luminance about 0.12) with white glyphs and dividers, a white hub with a black arrow, so it still contrasts with the light drawer.
 */
internal class PuckLook(
    val ring: Color, val pressed: Color, val divider: Color, val glyph: Color, val hub: Color, val hubPressed: Color, val hubGlyph: Color,
) {
    companion object {
        /** Share of the glyph's opacity for an empty slot's plus and for an unavailable action. */
        const val EMPTY_ALPHA = 0.40f
        const val UNAVAILABLE_ALPHA = 0.38f
        val Dark = PuckLook(
            ring = Color.White, pressed = Color(0xFFD9D9D9), divider = Color.Black, glyph = Color.Black,
            hub = Color.Black, hubPressed = Color(0xFF1C1C1C), hubGlyph = Color.White,
        )
        val Light = PuckLook(
            ring = Color(0xFF616161), pressed = Color(0xFF3A3A3A), divider = Color.White, glyph = Color.White,
            hub = Color.White, hubPressed = Color(0xFFDDDDDD), hubGlyph = Color.Black,
        )
        fun of(light: Boolean): PuckLook = if (light) Light else Dark
    }
}

private class WedgeShape(val path: Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(path)
}

/** Everything one wedge needs, built once per geometry (never per frame). */
private class WedgeDraw(
    val slot: Int,
    val left: Float, val top: Float, val width: Float, val height: Float,
    val shape: WedgeShape,
    val glyphX: Float, val glyphY: Float,
    val glyphDp: Float, val appDp: Float,
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
    onBack: () -> Unit,
) {
    val haptics = rememberLauncherHaptics()
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
            WedgeDraw(
                w.slot, left, top, b.width * d, b.height * d, WedgeShape(p),
                local.x + c.first * d, local.y + c.second * d,
                maxOf(PuckSectionsGeometry.MIN_GLYPH_DP, PuckSectionsGeometry.glyphSize(w, ring, PuckSectionsGeometry.APP_ICON_SHARE)),
                PuckSectionsGeometry.glyphSize(w, ring, PuckSectionsGeometry.APP_ICON_SHARE),
            )
        }
    }
    val look = PuckLook.of(HyleTheme.isLight)
    val accent = HyleColors.Violet
    val reduced = rememberMotionPrefs().reduced
    val keyboard = LocalInputMode.current.value == InputMode.Keyboard
    val hairline = remember(d) { Stroke(width = PuckSectionsGeometry.DIVIDER_DP * d) }
    val focusStroke = remember(d) { Stroke(width = 4f * d) } // clipped to the wedge, so the visible outline is 2 dp inside it
    var tip by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tip) { if (tip >= 0) { delay(1800); tip = -1 } }

    // Wave 33: the ring carries on round the hub past the quadrant's two edges (inert, same colour), so hub and ring read as one disc that only the
    // screen edges cut, like the Concepts fan; the window clips it. The four wedges and their taps stay inside the quadrant.
    Box(Modifier.fillMaxSize().drawBehind {
        drawCircle(look.ring, radius = ring.mid * d, center = corner, style = Stroke(width = ring.thickness * d))
    })

    wedges.forEach { w ->
        val a = actions.getOrNull(w.slot) ?: PuckAction(L10n.tr("Drawer shortcut %d, empty", w.slot + 1)) {}
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        var focused by remember { mutableStateOf(false) }
        val press by animateFloatAsState(if (pressed) 1f else 0f, if (reduced) snap() else tween(90), label = "puckPress")
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
                    drawPath(w.shape.path, lerp(look.ring, look.pressed, press))
                    if (focused && keyboard) clipPath(w.shape.path) { drawPath(w.shape.path, accent, style = focusStroke) }
                }
                .launcherItem(
                    "puck.section.${w.slot}", FocusKind.PuckRing, order = w.slot + 1, layer = 3, focusable = false, ringRadiusDp = 14f,
                    drawRing = false, onFocusState = { focused = it },
                )
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
            val alpha = when { empty -> PuckLook.EMPTY_ALPHA; !a.available -> PuckLook.UNAVAILABLE_ALPHA; else -> 1f }
            val boxDp = if (a.app != null) w.appDp else w.glyphDp
            val glyphPx = boxDp * d
            Box(
                Modifier
                    .offset { IntOffset((w.glyphX - glyphPx / 2f).roundToInt(), (w.glyphY - glyphPx / 2f).roundToInt()) }
                    .size(boxDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    empty -> Icon(Glyphs.Plus, null, tint = look.glyph.copy(alpha = alpha), modifier = Modifier.size((w.glyphDp * 0.7f).dp))
                    // Apps stay app icons: no tile, no tint, no extra clip (the icon theme's own shape, e.g. a circle, still applies).
                    a.app != null -> AppIcon(a.app, boxDp.dp, Modifier.graphicsLayer { this.alpha = alpha })
                    a.glyph != null -> Icon(a.glyph, null, tint = look.glyph.copy(alpha = alpha), modifier = Modifier.size(boxDp.dp))
                }
            }
        }
    }

    // Hairline dividers over the shared edges, from the hub to the outer arc (drawn once, above the wedges, below the glyphs' hit areas).
    Box(Modifier.fillMaxSize().drawBehind {
        for (deg in PuckSectionsGeometry.dividerAngles(anchor)) {
            val r = Math.toRadians(deg.toDouble())
            val c = kotlin.math.cos(r).toFloat(); val sn = kotlin.math.sin(r).toFloat()
            drawLine(
                look.divider, Offset(corner.x + c * ring.inner * d, corner.y + sn * ring.inner * d),
                Offset(corner.x + c * ring.outer * d, corner.y + sn * ring.outer * d), strokeWidth = hairline.width,
            )
        }
    })

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

    // The Back button: the round hub at the corner, hard against the ring (no gap), opposite tone to the ring.
    val backDp = ring.backRadius * 2f
    val backSource = remember { MutableInteractionSource() }
    val backPressed by backSource.collectIsPressedAsState()
    var backFocused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .offset { IntOffset((corner.x - ring.backRadius * d).roundToInt(), (corner.y - ring.backRadius * d).roundToInt()) }
            .size(backDp.dp)
            .semantics { contentDescription = L10n.tr("Back"); traversalIndex = 0f }
            .clip(CircleShape)
            .drawBehind {
                drawCircle(if (backPressed) look.hubPressed else look.hub)
                if (backFocused && keyboard) drawCircle(accent, radius = size.minDimension / 2f - 1f * d, style = Stroke(width = 2f * d))
            }
            .launcherItem(
                "puck.back", FocusKind.PuckHub, order = 0, layer = 3, focusable = false, ringRadiusDp = ring.backRadius,
                drawRing = false, onFocusState = { backFocused = it },
            )
            .clickable(interactionSource = backSource, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(BackGlyph, null, tint = look.hubGlyph, modifier = Modifier.size(24.dp))
    }
}
