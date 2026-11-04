package com.bos.sphere.feature.grid

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerMapping
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A corner slot resolved to where it shows on screen right now. */
data class CornerPlacement(val screen: Corner, val physical: Corner, val slot: CornerSlot)

/**
 * Resolves configured slots (keyed by physical corner) to screen corners. With [followDevice]
 * the slots stay under the same physical corner as the device rotates.
 */
fun cornerPlacements(slots: Map<Corner, CornerSlot>, rotation: Int, followDevice: Boolean): List<CornerPlacement> =
    Corner.entries.map { screen ->
        val physical = if (followDevice) CornerMapping.physicalCornerAt(screen, rotation) else screen
        CornerPlacement(screen, physical, slots[physical] ?: CornerSlot.Empty)
    }

/**
 * Current display rotation (Surface.ROTATION_*). Uses a DisplayListener as well as the
 * configuration: a 180° turn (landscape ↔ reverse landscape) changes no configuration.
 */
@Composable
fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: 0) }
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { view.display?.rotation?.let { rotation = it } }
    DisposableEffect(view) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val d = view.display ?: return
                if (d.displayId == displayId) rotation = d.rotation
            }
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }
    return rotation
}

/** Label for a corner item (action name or app label). */
fun itemLabel(item: CornerItem, appsByKey: Map<String, AppEntry>): String = when (item) {
    is CornerItem.Action -> item.id.label
    is CornerItem.App -> appsByKey[item.key]?.label ?: "Missing app"
}

/** Glyph for an action, or the app's icon. */
@Composable
fun ItemGlyph(item: CornerItem, appsByKey: Map<String, AppEntry>, size: Dp, tint: Color = HyleColors.InkBright) {
    when (item) {
        is CornerItem.Action -> Icon(Glyphs.forAction(item.id), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        is CornerItem.App -> appsByKey[item.key]?.let { AppIcon(it, size) }
            ?: Box(Modifier.size(size).background(HyleColors.Surface, CircleShape))
    }
}

/** Short summary of a slot for settings rows. */
fun slotSummary(slot: CornerSlot, appsByKey: Map<String, AppEntry>): String = when (slot) {
    CornerSlot.Empty -> "Nothing"
    is CornerSlot.Single -> itemLabel(slot.item, appsByKey)
    is CornerSlot.Palette -> "Palette · ${slot.name} (${slot.items.size})"
    is CornerSlot.Complication -> "Complication · ${slot.kind.label}"
    is CornerSlot.ComplicationCluster -> "Cluster · ${slot.kinds.joinToString { it.label }}"
    is CornerSlot.Widget -> "Widget · ${slot.label}"
}

private fun Corner.alignment(): Alignment = when (this) {
    Corner.TopLeft -> Alignment.TopStart
    Corner.TopRight -> Alignment.TopEnd
    Corner.BottomRight -> Alignment.BottomEnd
    Corner.BottomLeft -> Alignment.BottomStart
}

/** [Corner]s are absolute; flip Start/End alignment back under RTL. */
@Composable
internal fun Corner.absoluteAlignment(): Alignment {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirrored = when (this) {
        Corner.TopLeft -> Corner.TopRight
        Corner.TopRight -> Corner.TopLeft
        Corner.BottomRight -> Corner.BottomLeft
        Corner.BottomLeft -> Corner.BottomRight
    }
    return (if (rtl) mirrored else this).alignment()
}

private val BubbleRadius = 84.dp
private val GlyphBox = 56.dp

/** State of an open radial palette. */
private class OpenPalette(
    val placement: CornerPlacement,
    val palette: CornerSlot.Palette,
    val anchor: Offset, // px, in the layer's coordinates
)

/**
 * The four corners plus the radial palette overlay. Corners are drawn in [style]; tapping a
 * single slot runs it, a palette fans its items out from the corner (tap an item, or press and
 * slide onto one and release), long-press opens [onEdit] for that physical corner.
 * Complications and widgets render through [complication] / [cluster] / [widget] so the layer
 * stays independent of how they're hosted.
 */
@Composable
fun CornerLayer(
    placements: List<CornerPlacement>,
    style: CornerStyle,
    appsByKey: Map<String, AppEntry>,
    onRun: (item: CornerItem, screen: Corner) -> Unit,
    onEdit: (physical: Corner) -> Unit,
    complication: @Composable (CornerPlacement, CornerSlot.Complication, Modifier) -> Unit,
    cluster: @Composable (CornerPlacement, CornerSlot.ComplicationCluster, Modifier) -> Unit,
    widget: @Composable (CornerPlacement, CornerSlot.Widget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var open by remember { mutableStateOf<OpenPalette?>(null) }
    var hovered by remember { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reachPx = with(density) { 38.dp.toPx() }

    fun close() {
        scope.launch {
            progress.animateTo(0f, tween(140))
            open = null
            hovered = -1
        }
    }

    LaunchedEffect(open) {
        if (open != null) progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    // A rotation re-maps corners: drop any open palette rather than leave it anchored wrongly.
    LaunchedEffect(placements) { if (open != null) { open = null; hovered = -1; progress.snapTo(0f) } }

    // Corners are physical: lay out LTR so palette offsets match the touch coordinates in RTL too.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { layerCoords = it }) {
        placements.forEach { placement ->
          // Keyed so a rotation re-mapping never leaves gesture handlers bound to the old corner.
          androidx.compose.runtime.key(placement.screen, placement.physical) {
            when (val slot = placement.slot) {
                is CornerSlot.Widget -> widget(placement, slot)
                else -> CornerSlotView(
                    placement = placement,
                    style = style,
                    appsByKey = appsByKey,
                    layerCoords = { layerCoords },
                    onTap = { item -> onRun(item, placement.screen) },
                    onEdit = { onEdit(placement.physical) },
                    onPaletteOpen = { palette, anchor ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        hovered = -1
                        open = OpenPalette(placement, palette, anchor)
                    },
                    onPaletteDrag = { pos ->
                        val o = open ?: return@CornerSlotView
                        // Hit-test against the fully-open layout so a fast slide isn't lost mid-animation.
                        val h = paletteHit(o, pos, 1f, density.density, reachPx)
                        if (h != hovered && h >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        hovered = h
                    },
                    onPaletteRelease = { slid ->
                        val o = open ?: return@CornerSlotView
                        val h = hovered
                        when {
                            h >= 0 -> { onRun(o.palette.items[h], o.placement.screen); close() }
                            slid -> close()
                            // A plain tap leaves the palette open for tapping.
                        }
                    },
                    onPaletteCancel = { close() },
                    complication = complication,
                    cluster = cluster,
                )
            }
          }
        }

        open?.let { o ->
            PaletteOverlay(
                open = o,
                progress = progress.value,
                hovered = hovered,
                appsByKey = appsByKey,
                onItem = { i -> onRun(o.palette.items[i], o.placement.screen); close() },
                onDismiss = { close() },
            )
        }
    }
    }
}

private val PaletteItemSize = 52.dp

private fun paletteHit(o: OpenPalette, pos: Offset, scale: Float, density: Float, reachPx: Float): Int {
    val offsets = PaletteMath.offsets(o.palette.items.size, o.placement.screen)
        .map { Pt(it.x * density, it.y * density) }
    return PaletteMath.hit(Pt(pos.x, pos.y), Pt(o.anchor.x, o.anchor.y), offsets, scale.coerceAtLeast(0.01f), reachPx)
}

@Composable
private fun PaletteOverlay(
    open: OpenPalette,
    progress: Float,
    hovered: Int,
    appsByKey: Map<String, AppEntry>,
    onItem: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    BackHandler(onBack = onDismiss)
    val items = open.palette.items
    val offsets = remember(items.size, open.placement.screen) { PaletteMath.offsets(items.size, open.placement.screen) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        // Arc guides behind the items (the "palette rim").
        Canvas(Modifier.fillMaxSize()) {
            var rings = 0
            var left = items.size
            while (left > 0) { left -= PaletteMath.RING_CAPACITY.getOrElse(rings) { 9 }; rings++ }
            for (r in 0 until minOf(rings, PaletteMath.RING_RADIUS_DP.size)) {
                val radius = PaletteMath.RING_RADIUS_DP[r].dp.toPx() * progress
                drawArc(
                    color = HyleColors.Hairline,
                    startAngle = PaletteMath.quadrantStart(open.placement.screen),
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(open.anchor.x - radius, open.anchor.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        val half = with(density) { (PaletteItemSize / 2).toPx() }
        items.forEachIndexed { i, item ->
            val stagger = (progress * (1f + items.size * 0.06f) - i * 0.06f).coerceIn(0f, 1.2f)
            val o = offsets[i]
            val x = open.anchor.x + o.x * density.density * stagger
            val y = open.anchor.y + o.y * density.density * stagger
            val isHover = i == hovered
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) }
                    .width(PaletteItemSize)
                    .graphicsLayer {
                        val s = (if (isHover) 1.18f else 1f) * stagger.coerceIn(0f, 1f)
                        scaleX = s; scaleY = s
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = itemLabel(item, appsByKey)
                        onClick { onItem(i); true }
                    }
                    .pointerInput(i) { detectTapGestures { onItem(i) } },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(PaletteItemSize)
                        .background(HyleColors.SurfaceTranslucent, CircleShape)
                        .border(if (isHover) 2.dp else 1.dp, if (isHover) HyleColors.Violet else HyleColors.InsetHighlight, CircleShape),
                ) {
                    ItemGlyph(item, appsByKey, size = if (item is CornerItem.App) 36.dp else 24.dp)
                }
                Text(
                    itemLabel(item, appsByKey),
                    color = if (isHover) HyleColors.InkBright else HyleColors.Ink,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp).width(72.dp),
                )
            }
        }
        // Palette name, tucked near the anchor.
        val nameOffset = offsets.firstOrNull()
        if (nameOffset != null) {
            Text(
                open.palette.name.uppercase(),
                color = HyleColors.InkDim,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.offset {
                    val dx = if (open.placement.screen == Corner.TopRight || open.placement.screen == Corner.BottomRight) -90.dp.toPx() else 24.dp.toPx()
                    val dy = if (open.placement.screen == Corner.BottomLeft || open.placement.screen == Corner.BottomRight) -36.dp.toPx() else 24.dp.toPx()
                    IntOffset((open.anchor.x + dx).roundToInt(), (open.anchor.y + dy).roundToInt())
                },
            )
        }
    }
}

@Composable
private fun CornerSlotView(
    placement: CornerPlacement,
    style: CornerStyle,
    appsByKey: Map<String, AppEntry>,
    layerCoords: () -> LayoutCoordinates?,
    onTap: (CornerItem) -> Unit,
    onEdit: () -> Unit,
    onPaletteOpen: (CornerSlot.Palette, Offset) -> Unit,
    onPaletteDrag: (Offset) -> Unit,
    onPaletteRelease: (slid: Boolean) -> Unit,
    onPaletteCancel: () -> Unit,
    complication: @Composable (CornerPlacement, CornerSlot.Complication, Modifier) -> Unit,
    cluster: @Composable (CornerPlacement, CornerSlot.ComplicationCluster, Modifier) -> Unit,
) {
    val corner = placement.screen
    val slot = placement.slot
    val align = corner.absoluteAlignment()
    val bubble = style == CornerStyle.Bubble
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        if (bubble && slot !is CornerSlot.ComplicationCluster) {
            CornerBubble(corner, Modifier.align(align))
        }
        val safe = WindowInsets.safeDrawing
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val boxModifier = Modifier
            .align(align)
            .padding(safeInsetsFor(corner, safe))
            .padding(if (bubble) 10.dp else 6.dp)
            .onGloballyPositioned { coords = it }

        when (slot) {
            is CornerSlot.ComplicationCluster -> cluster(
                placement,
                slot,
                Modifier.align(align).padding(safeInsetsFor(corner, safe)).longPressFirst(onEdit),
            )
            is CornerSlot.Complication -> complication(placement, slot, boxModifier.longPressFirst(onEdit))
            is CornerSlot.Palette -> Box(
                contentAlignment = Alignment.Center,
                modifier = boxModifier
                    .size(GlyphBox)
                    .semantics {
                        contentDescription = "${slot.name} palette"
                        onLongClick { onEdit(); true }
                    }
                    .pointerInput(slot) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val toLayer: (Offset) -> Offset = { p ->
                                val c = coords; val l = layerCoords()
                                if (c != null && l != null && c.isAttached && l.isAttached) l.localPositionOf(c, p) else p
                            }
                            val center = Offset(size.width / 2f, size.height / 2f)
                            onPaletteOpen(slot, toLayer(center))
                            val slop = viewConfiguration.touchSlop
                            var slid = false
                            // Phase 1: long-press (edit) unless the finger moves or lifts first.
                            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                    c.consume()
                                    if (!c.pressed) return@withTimeoutOrNull true
                                    if ((c.position - down.position).getDistance() > slop) {
                                        slid = true
                                        onPaletteDrag(toLayer(c.position))
                                        return@withTimeoutOrNull null
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE") null
                            }
                            when (early) {
                                true -> { onPaletteRelease(false); return@awaitEachGesture }
                                false -> { onPaletteCancel(); return@awaitEachGesture }
                                null -> if (!slid) {
                                    // Held still: edit this corner instead.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPaletteCancel()
                                    onEdit()
                                    do { val ev = awaitPointerEvent(); ev.changes.forEach { it.consume() } } while (ev.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                            }
                            // Phase 2: slide-to-select until release.
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                c.consume()
                                if (!c.pressed) break
                                onPaletteDrag(toLayer(c.position))
                            }
                            onPaletteRelease(true)
                        }
                    },
            ) {
                PaletteBadge(slot, appsByKey)
            }
            is CornerSlot.Single -> Box(
                contentAlignment = Alignment.Center,
                modifier = boxModifier
                    .size(GlyphBox)
                    .semantics {
                        contentDescription = itemLabel(slot.item, appsByKey)
                        onClick { onTap(slot.item); true }
                        onLongClick { onEdit(); true }
                    }
                    .pointerInput(slot) {
                        detectTapGestures(
                            onTap = { onTap(slot.item) },
                            onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
                        )
                    },
            ) {
                ItemGlyph(slot.item, appsByKey, size = if (slot.item is CornerItem.App) 34.dp else 28.dp)
            }
            CornerSlot.Empty -> Box(
                // Invisible but long-pressable so an empty corner can still be assigned.
                modifier = boxModifier
                    .size(GlyphBox)
                    .semantics { contentDescription = "Empty corner"; onLongClick { onEdit(); true } }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() })
                    },
            )
            is CornerSlot.Widget -> Unit // drawn by the widget host
        }
    }
}

/** A palette shows as a 2×2 cluster of its first items (the corner-folder look). */
@Composable
private fun PaletteBadge(slot: CornerSlot.Palette, appsByKey: Map<String, AppEntry>) {
    val shown = slot.items.take(4)
    if (shown.size <= 1) {
        shown.firstOrNull()?.let { ItemGlyph(it, appsByKey, 28.dp) } ?: Icon(Glyphs.Palette, null, tint = HyleColors.InkBright, modifier = Modifier.size(28.dp))
        return
    }
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                row.forEach { ItemGlyph(it, appsByKey, size = if (it is CornerItem.App) 18.dp else 16.dp) }
            }
        }
    }
}

/** Quarter-circle glass bubble hugging the physical screen corner. */
@Composable
private fun CornerBubble(corner: Corner, modifier: Modifier) {
    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    val dir = LocalLayoutDirection.current
    val extra = with(density) {
        val v = if (corner == Corner.TopLeft || corner == Corner.TopRight) safe.getTop(this) else safe.getBottom(this)
        val h = if (corner == Corner.TopLeft || corner == Corner.BottomLeft) safe.getLeft(this, dir) else safe.getRight(this, dir)
        maxOf(v, h).toDp()
    }
    val r = BubbleRadius + extra
    Canvas(modifier.size(r)) {
        val c = when (corner) {
            Corner.TopLeft -> Offset(0f, 0f)
            Corner.TopRight -> Offset(size.width, 0f)
            Corner.BottomRight -> Offset(size.width, size.height)
            Corner.BottomLeft -> Offset(0f, size.height)
        }
        drawCircle(HyleColors.SurfaceTranslucent, radius = size.width, center = c)
        drawCircle(HyleColors.InsetHighlight, radius = size.width - 0.5.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun safeInsetsFor(corner: Corner, safe: WindowInsets): androidx.compose.foundation.layout.PaddingValues {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    return with(density) {
        val top = safe.getTop(this).toDp()
        val bottom = safe.getBottom(this).toDp()
        val left = safe.getLeft(this, dir).toDp()
        val right = safe.getRight(this, dir).toDp()
        // Only the two edges that meet at this corner matter.
        androidx.compose.foundation.layout.PaddingValues.Absolute(
            left = if (corner == Corner.TopLeft || corner == Corner.BottomLeft) left else 0.dp,
            top = if (corner == Corner.TopLeft || corner == Corner.TopRight) top else 0.dp,
            right = if (corner == Corner.TopRight || corner == Corner.BottomRight) right else 0.dp,
            bottom = if (corner == Corner.BottomLeft || corner == Corner.BottomRight) bottom else 0.dp,
        )
    }
}

/**
 * Long-press detector that observes in the Initial pass (before children such as a clickable
 * complication) and, once it fires, swallows the rest of the gesture so the child's tap is
 * cancelled.
 */
internal fun Modifier.longPressFirst(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed || c.isConsumed) return@withTimeoutOrNull true
                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (ended == null) {
            onLongPress()
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
            } while (ev.changes.any { it.pressed })
        }
    }
}
