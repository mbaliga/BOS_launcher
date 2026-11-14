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
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.bos.sphere.core.data.ActionApps
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerIconMode
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
import com.bos.sphere.core.input.FocusKind
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
    is CornerItem.Action -> com.bos.sphere.core.design.L10n.tr(item.id.label)
    is CornerItem.App -> com.bos.sphere.core.data.CornerShortcuts.parse(item.key)?.label?.takeIf { it.isNotBlank() } ?: appsByKey[item.key]?.label ?: "Missing app"
}

/** Glyph for an action, or the app's icon. */
@Composable
fun ItemGlyph(
    item: CornerItem,
    appsByKey: Map<String, AppEntry>,
    size: Dp,
    tint: Color = HyleColors.InkBright,
    mode: CornerIconMode = CornerIconMode.Glyph,
) {
    when (item) {
        is CornerItem.Action -> {
            // In app-icon mode, actions that open an app show that app's real icon.
            val real = if (mode == CornerIconMode.AppIcon && item.id in ActionApps.appCapable) actionAppIcon(item.id) else null
            if (real != null) {
                Image(
                    bitmap = real,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size * 1.25f).clip(RoundedCornerShape(size * 0.28f)),
                )
            } else {
                Icon(Glyphs.forAction(item.id), contentDescription = null, tint = tint, modifier = Modifier.size(size))
            }
        }
        // A shortcut wears its app's icon.
        is CornerItem.App -> (appsByKey[item.key] ?: com.bos.sphere.core.data.CornerShortcuts.parse(item.key)?.let { p -> appsByKey.values.firstOrNull { it.packageName == p.packageName } })?.let { AppIcon(it, size) }
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
    iconSize: Dp = 32.dp,
    iconMode: CornerIconMode = CornerIconMode.Glyph,
) {
    var layerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var open by remember { mutableStateOf<OpenPalette?>(null) }
    var hovered by remember { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

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
                    iconSize = iconSize,
                    iconMode = iconMode,
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
                        val h = paletteHit(o, pos, density.density)
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

private fun paletteHit(o: OpenPalette, pos: Offset, density: Float): Int {
    val slots = PaletteMath.layout(o.palette.items.size, o.placement.screen)
    // A thumb sliding between tiles keeps its nearest tile (slack in dp).
    return PaletteMath.hit((pos.x - o.anchor.x) / density, (pos.y - o.anchor.y) / density, slots, slackDp = 10f)
}

/** Concepts-style wheel: concentric rings of wedge tiles fanning out from the corner. */
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
    val corner = open.placement.screen
    val slots = remember(items.size, corner) { PaletteMath.layout(items.size, corner) }
    val p = progress.coerceIn(0f, 1f)
    val glass = HyleColors.SurfaceTranslucent
    val accent = HyleColors.Violet
    FocusLayer(layer = 5) {
    Box(
        Modifier
            .fillMaxSize()
            .linearTraversal()
            .background(Color.Black.copy(alpha = 0.55f * p))
            .pointerInput(open, slots) {
                detectTapGestures { pos ->
                    val h = paletteHit(open, pos, density.density)
                    if (h >= 0) onItem(h) else onDismiss()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density.density
            for (slot in slots) {
                // Each ring unfurls a little after the one inside it.
                val local = (progress * (1.25f + slot.ring * 0.18f) - slot.ring * 0.18f).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val inner = slot.innerDp * d * local
                val outer = slot.outerDp * d * local
                val path = androidx.compose.ui.graphics.Path().apply {
                    val oRect = androidx.compose.ui.geometry.Rect(open.anchor, outer)
                    val iRect = androidx.compose.ui.geometry.Rect(open.anchor, inner)
                    arcTo(oRect, slot.startDeg, slot.sweepDeg, false)
                    arcTo(iRect, slot.startDeg + slot.sweepDeg, -slot.sweepDeg, false)
                    close()
                }
                val isHover = slot.index == hovered
                if (items[slot.index] is CornerItem.App) {
                    // App icons float on the scrim — no pie block behind them; only a hover ring shows.
                    if (isHover) drawPath(path, accent.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx()))
                } else {
                    drawPath(path, if (isHover) accent.copy(alpha = 0.85f) else glass.copy(alpha = 0.92f))
                    drawPath(path, if (isHover) HyleColors.InkBright.copy(alpha = 0.7f) else HyleColors.InsetHighlight, style = Stroke(width = if (isHover) 1.6.dp.toPx() else 0.8.dp.toPx()))
                }
            }
        }
        slots.forEach { slot ->
            val local = (progress * (1.25f + slot.ring * 0.18f) - slot.ring * 0.18f).coerceIn(0f, 1f)
            if (local <= 0.35f) return@forEach
            val c = PaletteMath.centre(slot)
            val item = items[slot.index]
            val iconDp = if (item is CornerItem.App) 34.dp else 26.dp
            val half = with(density) { (iconDp / 2).toPx() }
            val x = open.anchor.x + c.x * density.density * local
            val y = open.anchor.y + c.y * density.density * local
            val isHover = slot.index == hovered
            Box(
                Modifier
                    .offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) }
                    .graphicsLayer {
                        val sc = (if (isHover) 1.15f else 1f) * local
                        scaleX = sc; scaleY = sc; alpha = local
                    }
                    .launcherItem("palette.${slot.index}", FocusKind.Item, order = slot.index, layer = 5, onActivate = { onItem(slot.index) }, ringRadiusDp = 16f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = itemLabel(item, appsByKey)
                        onClick { onItem(slot.index); true }
                    },
            ) {
                ItemGlyph(item, appsByKey, size = iconDp, tint = if (isHover) HyleColors.Background else HyleColors.InkBright)
            }
        }
        // The hovered item's name (or the palette's) sits in the hub near the corner.
        val label = items.getOrNull(hovered)?.let { itemLabel(it, appsByKey) } ?: open.palette.name
        val hubDp = PaletteMath.HUB_DP
        val onRight = corner == Corner.TopRight || corner == Corner.BottomRight
        val onBottom = corner == Corner.BottomLeft || corner == Corner.BottomRight
        Text(
            label.uppercase(),
            color = HyleColors.InkBright,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width((hubDp - 12).dp)
                .offset {
                    val w = (hubDp - 12).dp.toPx()
                    val dx = if (onRight) -(w + 8.dp.toPx()) else 8.dp.toPx()
                    val dy = if (onBottom) -(hubDp * 0.5f).dp.toPx() else (hubDp * 0.42f).dp.toPx()
                    IntOffset((open.anchor.x + dx).roundToInt(), (open.anchor.y + dy).roundToInt())
                },
            textAlign = if (onRight) TextAlign.End else TextAlign.Start,
        )
    }
    }
}

@Composable
private fun CornerSlotView(
    placement: CornerPlacement,
    style: CornerStyle,
    iconSize: Dp,
    iconMode: CornerIconMode,
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
    val bubble = style == CornerStyle.Quarter
    val round = style == CornerStyle.Bubble
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        if (bubble && slot !is CornerSlot.ComplicationCluster) {
            CornerBubble(corner, Modifier.align(align))
        }
        val safe = WindowInsets.safeDrawing
        // Round bubbles: a glass disc behind the icon, floating a little in from the edges.
        if (round && (slot is CornerSlot.Single || slot is CornerSlot.Palette)) {
            Box(
                Modifier.align(align).padding(safeInsetsFor(corner, safe)).padding(8.dp).size(iconSize + 26.dp)
                    .clip(CircleShape).background(HyleColors.SurfaceTranslucent).border(1.dp, HyleColors.InsetHighlight, CircleShape),
            )
        }
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val boxModifier = Modifier
            .align(align)
            .padding(safeInsetsFor(corner, safe))
            .padding(if (bubble) 10.dp else if (round) 8.dp else 6.dp)
            .onGloballyPositioned { coords = it }

        when (slot) {
            is CornerSlot.ComplicationCluster -> cluster(
                placement,
                slot,
                Modifier.align(align).padding(safeInsetsFor(corner, safe)).longPressFirst(onEdit),
            )
            // INP-17: a complication corner is a node in the focus graph like every other corner; Enter runs its action, the Menu key edits it.
            is CornerSlot.Complication -> complication(
                placement, slot,
                boxModifier.longPressFirst(onEdit)
                    .launcherItem("corner.${corner.name}", FocusKind.Corner, focusable = false, onActivate = { slot.kind.action?.let { onTap(CornerItem.Action(it)) } }, onMenu = onEdit, ringRadiusDp = 24f),
            )
            is CornerSlot.Palette -> Box(
                contentAlignment = Alignment.Center,
                modifier = boxModifier
                    .size(iconSize + 26.dp)
                    .semantics {
                        contentDescription = "${slot.name} palette"
                        onLongClick { onEdit(); true }
                    }
                    .launcherItem(
                        "corner.${corner.name}", FocusKind.Corner,
                        onActivate = {
                            val c = coords; val l = layerCoords()
                            if (c != null && l != null && c.isAttached && l.isAttached) {
                                onPaletteOpen(slot, l.localPositionOf(c, Offset(c.size.width / 2f, c.size.height / 2f)))
                            }
                        },
                        onMenu = onEdit, ringRadiusDp = 22f,
                    )
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
                PaletteBadge(slot, appsByKey, iconSize, iconMode)
            }
            is CornerSlot.Single -> Box(
                contentAlignment = Alignment.Center,
                modifier = boxModifier
                    .size(iconSize + 26.dp)
                    .semantics {
                        contentDescription = itemLabel(slot.item, appsByKey)
                        onClick { onTap(slot.item); true }
                        onLongClick { onEdit(); true }
                    }
                    .launcherItem("corner.${corner.name}", FocusKind.Corner, onActivate = { onTap(slot.item) }, onMenu = onEdit, ringRadiusDp = 22f)
                    .pointerInput(slot) {
                        detectTapGestures(
                            onTap = { onTap(slot.item) },
                            onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
                        )
                    },
            ) {
                ItemGlyph(slot.item, appsByKey, size = if (slot.item is CornerItem.App) iconSize * 1.2f else iconSize, mode = iconMode)
            }
            CornerSlot.Empty -> Box(
                // Invisible but long-pressable so an empty corner can still be assigned.
                modifier = boxModifier
                    .size(iconSize + 26.dp)
                    .semantics { contentDescription = "Empty corner"; onLongClick { onEdit(); true } }
                    .launcherItem("corner.${corner.name}", FocusKind.Corner, onActivate = onEdit, onMenu = onEdit, ringRadiusDp = 22f)
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
private fun PaletteBadge(slot: CornerSlot.Palette, appsByKey: Map<String, AppEntry>, iconSize: Dp, mode: CornerIconMode) {
    val shown = slot.items.take(4)
    if (shown.size <= 1) {
        shown.firstOrNull()?.let { ItemGlyph(it, appsByKey, iconSize, mode = mode) } ?: Icon(Glyphs.Palette, null, tint = HyleColors.InkBright, modifier = Modifier.size(iconSize))
        return
    }
    val cell = iconSize * 0.56f
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                row.forEach { ItemGlyph(it, appsByKey, size = if (it is CornerItem.App) cell * 1.1f else cell, mode = mode) }
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

/** The real icon of the app that handles [id] (Music → your music player), or null if none resolves. */
@Composable
fun actionAppIcon(id: CornerActionId): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val icon by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(ActionIconCache.get(id), id) {
        if (value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    val cn = ActionApps.resolve(context, id) ?: return@runCatching null
                    context.packageManager.getActivityIcon(cn).toBitmap(160, 160).asImageBitmap()
                }.getOrNull()?.also { ActionIconCache.put(id, it) }
            }
        }
    }
    return icon
}

private object ActionIconCache {
    private val map = HashMap<CornerActionId, androidx.compose.ui.graphics.ImageBitmap>()
    @Synchronized fun get(id: CornerActionId) = map[id]
    @Synchronized fun put(id: CornerActionId, b: androidx.compose.ui.graphics.ImageBitmap) { map[id] = b }
}
