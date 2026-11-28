package com.bos.sphere.feature.grid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerIconMode
import com.bos.sphere.core.data.CornerMapping
import com.bos.sphere.core.data.CornerSets
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs

/**
 * Wave 59 (glanceTop, CFG-77..79): the pure rules of the Hotspots editor. The editor replaced the Hotspots presets (Four, Two (bottom), One, None) and the
 * four per-corner switches: a live miniature of the home where each corner is tapped on or off, long pressed (or "Edit") to change what it runs.
 *
 * The miniature shows SCREEN corners of the orientation in view; a toggle or an edit acts on the PHYSICAL corner under it (hotspots stay physical, as the
 * corner assignments do: [CornerMapping]). The names read the screen position the user sees ("Top left").
 */
object HotspotsEditorModel {
    enum class Arrow { Left, Right, Up, Down }

    /** The corner an arrow key moves to from [c] (screen space; the miniature is laid out left to right in every language, so arrows are physical too). */
    fun move(c: Corner, a: Arrow): Corner {
        val top = c == Corner.TopLeft || c == Corner.TopRight
        val left = c == Corner.TopLeft || c == Corner.BottomLeft
        val nTop = when (a) { Arrow.Up -> true; Arrow.Down -> false; else -> top }
        val nLeft = when (a) { Arrow.Left -> true; Arrow.Right -> false; else -> left }
        return when { nTop && nLeft -> Corner.TopLeft; nTop -> Corner.TopRight; nLeft -> Corner.BottomLeft; else -> Corner.BottomRight }
    }

    /** The display rotation the miniature draws: the device's own when it shows the orientation the device is in, else the natural one of that orientation. */
    fun viewRotation(viewLandscape: Boolean, deviceLandscape: Boolean, deviceRotation: Int): Int =
        if (viewLandscape == deviceLandscape) deviceRotation else if (viewLandscape) 1 else 0

    /** The physical corner under [screen] at [rotation]. */
    fun physicalAt(screen: Corner, rotation: Int, followDevice: Boolean): Corner = if (followDevice) CornerMapping.physicalCornerAt(screen, rotation) else screen

    /** The assignments the miniature shows (and an edit writes): landscape's own set only with "Separate corners for landscape" on. */
    fun slotsFor(viewLandscape: Boolean, separate: Boolean, portrait: Map<Corner, CornerSlot>, landscape: Map<Corner, CornerSlot>): Map<Corner, CornerSlot> =
        CornerSets.select(portrait, landscape, separate, viewLandscape)

    /** Whether an edit made while [viewLandscape] goes to the landscape set. */
    fun editsLandscape(viewLandscape: Boolean, separate: Boolean): Boolean = CornerSets.writesLandscape(separate, viewLandscape)

    /** The row's short value: how many hotspots are on. */
    fun summary(on: Set<Corner>): String = L10n.tr("%d of 4 on", on.size)

    /** What a screen reader says for one corner (the on / off state is its state description). */
    fun description(screen: Corner, item: String): String = L10n.tr("%s hotspot", L10n.tr(screen.label)) + ", " + item
}

/** The name of what a corner holds, for the editor (translated; an empty corner is "Nothing"). */
fun hotspotItemLabel(slot: CornerSlot, appsByKey: Map<String, AppEntry>): String = when (slot) {
    CornerSlot.Empty -> L10n.tr("Nothing")
    is CornerSlot.Single -> itemLabel(slot.item, appsByKey)
    is CornerSlot.Palette -> slot.name
    is CornerSlot.Complication -> L10n.tr(slot.kind.label)
    is CornerSlot.ComplicationCluster -> L10n.tr("Cluster")
    is CornerSlot.Widget -> slot.label
}

object HotspotsEditorTags {
    const val EDITOR = "hotspots.editor"
    const val MINIATURE = "hotspots.miniature"
    fun corner(c: Corner) = "hotspots.corner.${c.name}"
    const val PULSE = "hotspots.pulse"
    const val ROW = "hotspots.row"
    const val ROW_NAME = "hotspots.row.name"
    const val ROW_ITEM = "hotspots.row.item"
    const val ROW_SWITCH = "hotspots.row.switch"
    const val EDIT = "hotspots.edit"
    const val ORIENTATION = "hotspots.orientation"
}

/** Test seam: the pulse's current scale of the selected corner (1 when still). */
val HotspotPulse = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("HotspotPulse")

/**
 * The Hotspots editor: a live miniature of the home at the phone's aspect (the real fixed wallpaper when a [capture] is given, the real corner layer
 * with the chosen corner style, icon size, icon type and glyphs), each corner showing what it holds. Tap a corner: on / off (off is dimmed with a
 * slashed outline). Long press, or Edit in the row below: the corner editor for that corner ([onEdit]: physical corner, landscape set). The selected
 * corner pulses (a still, thicker ring under reduced motion). Keys: arrows move between the corners, Enter toggles, E edits.
 *
 * [enabled] is the stored set (physical corners). With [separateLandscape] a Portrait / Landscape control picks the orientation being edited; the
 * miniature turns with it. [onSelect] reports the selected screen corner (the floating Preview's Corners topic follows it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotspotsEditor(
    enabled: Set<Corner>,
    portraitSlots: Map<Corner, CornerSlot>,
    landscapeSlots: Map<Corner, CornerSlot>,
    separateLandscape: Boolean,
    followDevice: Boolean,
    deviceLandscape: Boolean,
    deviceRotation: Int,
    style: CornerStyle,
    iconSize: Dp,
    iconMode: CornerIconMode,
    appsByKey: Map<String, AppEntry>,
    onToggle: (physical: Corner, on: Boolean) -> Unit,
    onEdit: (physical: Corner, landscapeSet: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    capture: HomeCapture? = null,
    large: Boolean = false,
    onSelect: (Corner) -> Unit = {},
) {
    var viewLandscape by rememberSaveable(separateLandscape, deviceLandscape) { mutableStateOf(deviceLandscape) }
    var selected by rememberSaveable { mutableStateOf(Corner.TopLeft) }
    val rotation = HotspotsEditorModel.viewRotation(viewLandscape, deviceLandscape, deviceRotation)
    val slots = HotspotsEditorModel.slotsFor(viewLandscape, separateLandscape, portraitSlots, landscapeSlots)
    val placements = cornerPlacements(slots, rotation, followDevice)
    fun physical(c: Corner) = HotspotsEditorModel.physicalAt(c, rotation, followDevice)
    fun isOn(c: Corner) = physical(c) in enabled
    val previewFocus = LocalPreviewFocus.current
    // The floating Preview's Corners topic rings the hotspots that are on and pulses the corner selected here.
    fun select(c: Corner) { selected = c; previewFocus?.onSubject(PreviewSubject(PreviewTopic.Corners, c)); onSelect(c) }
    fun toggle(c: Corner) { select(c); onToggle(physical(c), !isOn(c)) }
    fun edit(c: Corner) { select(c); onEdit(physical(c), HotspotsEditorModel.editsLandscape(viewLandscape, separateLandscape)) }

    val conf = LocalConfiguration.current
    // The window's own size for the orientation in view (the other orientation swaps the two sides).
    val shortDp = minOf(conf.screenWidthDp, conf.screenHeightDp).coerceAtLeast(240)
    val longDp = maxOf(conf.screenWidthDp, conf.screenHeightDp).coerceAtLeast(400)
    val homeWDp = if (viewLandscape) longDp else shortDp
    val homeHDp = if (viewLandscape) shortDp else longDp
    val requesters = remember { Corner.entries.associateWith { FocusRequester() } }
    val reduced = rememberMotionPrefs().reduced

    Column(modifier.fillMaxWidth().testTag(HotspotsEditorTags.EDITOR), horizontalAlignment = Alignment.CenterHorizontally) {
        if (separateLandscape) {
            OneUiSegmented(
                listOf(L10n.tr("Portrait"), L10n.tr("Landscape")), if (viewLandscape) 1 else 0, { viewLandscape = it == 1 },
                modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 10.dp).testTag(HotspotsEditorTags.ORIENTATION), description = L10n.tr("Orientation being edited"),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val avail = maxWidth.value
            val wDp = if (viewLandscape) minOf(avail, if (large) 420f else 340f) else minOf(avail * 0.7f, if (large) 270f else 210f)
            val hDp = wDp * homeHDp / homeWDp
            // Where a corner's glyph sits in the miniature (dp from its two edges): the corner layer's padding for the style, half the glyph box, the safe
            // insets of that side, all scaled like the miniature. The ring and the off mark are centred on it.
            val k = wDp / homeWDp
            val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
            val insetTop = with(LocalDensity.current) { safe.getTop(this).toDp().value }
            val insetBottom = with(LocalDensity.current) { safe.getBottom(this).toDp().value }
            val pad = when (style) { CornerStyle.Quarter -> 10f; CornerStyle.Bubble -> 8f; else -> 6f }
            fun glyphAt(c: Corner): Offset {
                val half = pad + (iconSize.value + 26f) / 2f
                val dy = half + if (c == Corner.TopLeft || c == Corner.TopRight) insetTop else insetBottom
                return Offset(half * k, dy * k)
            }
            val density = LocalDensity.current
            val homeWpx = with(density) { homeWDp.dp.roundToPx() }
            val homeHpx = with(density) { homeHDp.dp.roundToPx() }
            Box(
                Modifier.size(wDp.dp, hDp.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, HyleColors.InkDim.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .testTag(HotspotsEditorTags.MINIATURE)
                    .onPreviewKeyEvent { e ->
                        val a = when (e.key) {
                            Key.DirectionLeft -> HotspotsEditorModel.Arrow.Left
                            Key.DirectionRight -> HotspotsEditorModel.Arrow.Right
                            Key.DirectionUp -> HotspotsEditorModel.Arrow.Up
                            Key.DirectionDown -> HotspotsEditorModel.Arrow.Down
                            else -> null
                        }
                        val enter = e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                        when {
                            a != null -> { if (e.type == KeyEventType.KeyDown) { val n = HotspotsEditorModel.move(selected, a); select(n); runCatching { requesters.getValue(n).requestFocus() } }; true }
                            enter -> { if (e.type == KeyEventType.KeyDown) toggle(selected); true }
                            e.key == Key.E -> { if (e.type == KeyEventType.KeyDown) edit(selected); true }
                            else -> false
                        }
                    },
            ) {
                // 1. The backdrop: the real fixed wallpaper as the home draws it (when it is the orientation in view), else a quiet gradient.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B3050), Color(0xFF101322)))))
                if (capture != null && viewLandscape == deviceLandscape && capture.size.width > 0) {
                    Canvas(Modifier.fillMaxSize()) {
                        val k = DockGeometry.scale(capture.size.width, size.width)
                        clipRect { scale(k, k, pivot = Offset.Zero) { drawLayer(capture.wallpaperLayer) } }
                    }
                }
                // 2. The real corner layer, one per corner (an off corner is dimmed), composed at the home's own size and scaled down: nothing in it can be touched.
                PreviewSandbox(homeWpx, homeHpx) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        placements.forEach { p ->
                            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (physical(p.screen) in enabled) 1f else 0.28f }) {
                                CornerLayer(
                                    placements = listOf(p), style = style, appsByKey = appsByKey, onRun = { _, _ -> }, onEdit = {},
                                    complication = { _, slot, mod -> ComplicationView(slot.kind, 48.dp, onClick = {}, modifier = mod) },
                                    cluster = { pl, slot, mod -> ComplicationCluster(kinds = slot.kinds, anchor = pl.screen, itemSize = 42.dp, radius = 92.dp, onClick = {}, modifier = mod, focusIdPrefix = "hotspots.mini.${pl.screen.name}") },
                                    widget = { pl, slot -> WidgetPlaceholder(pl.screen, slot.label) },
                                    iconSize = iconSize, iconMode = iconMode,
                                )
                            }
                        }
                    }
                }
                // 3. The touch targets (48 dp, at the four corners) with the selection ring and the off mark; laid out left to right (corners are physical).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Box(Modifier.fillMaxSize()) {
                    // Everything between the corners is inert.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                    Corner.entries.forEach { c ->
                        val on = isOn(c)
                        val item = hotspotItemLabel(slots[physical(c)] ?: CornerSlot.Empty, appsByKey)
                        val isSel = c == selected
                        val align = when (c) { Corner.TopLeft -> Alignment.TopStart; Corner.TopRight -> Alignment.TopEnd; Corner.BottomRight -> Alignment.BottomEnd; Corner.BottomLeft -> Alignment.BottomStart }
                        CornerTarget(
                            on = on, selected = isSel, reduced = reduced, corner = c, glyph = glyphAt(c), glyphRadius = (iconSize.value / 2f * k + 7f).coerceIn(12f, 22f),
                            modifier = Modifier.align(align).size(56.dp).testTag(HotspotsEditorTags.corner(c)).focusRequester(requesters.getValue(c))
                                .semantics {
                                    contentDescription = HotspotsEditorModel.description(c, item)
                                    stateDescription = L10n.tr(if (on) "On" else "Off")
                                    toggleableState = ToggleableState(on)
                                    this.selected = isSel
                                    role = Role.Switch
                                    customActions = listOf(CustomAccessibilityAction(L10n.tr("Edit")) { edit(c); true })
                                    onClick(L10n.tr(if (on) "Turn off" else "Turn on")) { toggle(c); true }
                                    onLongClick(L10n.tr("Edit")) { edit(c); true }
                                }
                                .combinedClickable(onClick = { toggle(c) }, onLongClick = { edit(c) }),
                        )
                    }
                } }
            }
        }
        // The compact row for the selected corner: its name, what it holds, its switch and Edit.
        val selOn = isOn(selected)
        val selItem = hotspotItemLabel(slots[physical(selected)] ?: CornerSlot.Empty, appsByKey)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp).testTag(HotspotsEditorTags.ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(L10n.tr(selected.label), color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag(HotspotsEditorTags.ROW_NAME))
                Text(selItem, color = HyleColors.InkDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(HotspotsEditorTags.ROW_ITEM))
            }
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).testTag(HotspotsEditorTags.ROW_SWITCH)
                    .clickable(role = Role.Switch) { toggle(selected) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = L10n.tr("%s hotspot", L10n.tr(selected.label)); stateDescription = L10n.tr(if (selOn) "On" else "Off"); toggleableState = ToggleableState(selOn)
                    },
                contentAlignment = Alignment.Center,
            ) { OneUiSwitch(selOn) }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(HyleColors.Violet.copy(alpha = 0.22f)).testTag(HotspotsEditorTags.EDIT)
                    .clickable(role = Role.Button, onClickLabel = L10n.tr("Edit")) { edit(selected) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Glyphs.Sliders, null, tint = HyleColors.InkBright, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(L10n.tr("Edit"), color = HyleColors.InkBright, fontSize = 15.sp)
            }
        }
        Text(
            L10n.tr("Tap a corner to turn it on or off. Long press to change what it runs."), color = HyleColors.InkDim, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** One corner of the miniature: the pulsing selection ring and, while off, a dimmed outline with a slash. */
@Composable
private fun CornerTarget(on: Boolean, selected: Boolean, reduced: Boolean, corner: Corner, glyph: Offset, glyphRadius: Float, modifier: Modifier) {
    val motion = rememberMotionPrefs()
    val still = reduced || motion.reduced || !selected
    val pulse = if (still) remember { mutableStateOf(1f) } else
        rememberInfiniteTransition(label = "hotspotPulse").animateFloat(0.7f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().then(if (selected) Modifier.testTag(HotspotsEditorTags.PULSE).semantics { this[HotspotPulse] = pulse.value } else Modifier)) {
            // The glyph's centre, measured from this corner's two edges, inside the 56 dp target.
            val m = glyphRadius.dp.toPx() + 3.dp.toPx() // clear of the miniature's rounded edge
            val gx = (glyph.x.dp.toPx()).coerceIn(m, size.width - m)
            val gy = (glyph.y.dp.toPx()).coerceIn(m, size.height - m)
            val left = corner == Corner.TopLeft || corner == Corner.BottomLeft
            val top = corner == Corner.TopLeft || corner == Corner.TopRight
            val c = Offset(if (left) gx else size.width - gx, if (top) gy else size.height - gy)
            val r = glyphRadius.dp.toPx()
            if (!on) {
                drawCircle(Color.White.copy(alpha = 0.45f), r, c, style = Stroke(1.5.dp.toPx()))
                drawLine(Color.White.copy(alpha = 0.55f), Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), 1.5.dp.toPx())
            }
            if (selected) {
                val rr = if (still) r * 1.08f else r * (0.92f + 0.2f * pulse.value)
                drawCircle(HyleColors.Violet.copy(alpha = 0.22f * pulse.value), rr, c)
                drawCircle(HyleColors.Violet, rr, c, style = Stroke(if (still && selected && (reduced || motion.reduced)) 3.5.dp.toPx() else 2.5.dp.toPx()))
            }
        }
    }
}

/** A widget corner in the miniature: a small card with the widget's name (the live widget is hosted by the home only). */
@Composable
private fun WidgetPlaceholder(corner: Corner, label: String) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val align = when (corner) { Corner.TopLeft -> Alignment.TopStart; Corner.TopRight -> Alignment.TopEnd; Corner.BottomRight -> Alignment.BottomEnd; Corner.BottomLeft -> Alignment.BottomStart }
        Box(Modifier.align(align).padding(10.dp).size(96.dp, 64.dp).clip(RoundedCornerShape(14.dp)).background(HyleColors.Surface), contentAlignment = Alignment.Center) {
            Text(label, color = HyleColors.InkBright, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

/** The editor as a full page (the Home sheet's Hotspots tile opens it): the page frame, the larger miniature and the same rows. */
@Composable
fun HotspotsEditorPage(onDismiss: () -> Unit, content: @Composable () -> Unit) = OneUiFullPage(onDismiss) {
    OneUiPageFrame(L10n.tr("Hotspots"), onDismiss, tag = "hotspots.page") {
        item(key = "hotspots") { Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) { content() } }
    }
}

/**
 * The editor wired to the launcher preferences (Settings > Home > Corners and the Home sheet's full page share it): a toggle writes the stored set
 * (`glanceHotspots`, physical corners), an edit opens the corner editor through [onEdit].
 */
@Composable
fun HotspotsEditorFor(
    prefs: com.bos.sphere.core.data.LauncherPrefs,
    update: ((com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs) -> Unit,
    portraitSlots: Map<Corner, CornerSlot>,
    landscapeSlots: Map<Corner, CornerSlot>,
    deviceLandscape: Boolean,
    deviceRotation: Int,
    appsByKey: Map<String, AppEntry>,
    onEdit: (physical: Corner, landscapeSet: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    capture: HomeCapture? = null,
    large: Boolean = false,
) = HotspotsEditor(
    enabled = prefs.glanceHotspots, portraitSlots = portraitSlots, landscapeSlots = landscapeSlots,
    separateLandscape = prefs.cornersPerOrientation, followDevice = prefs.cornersFollowDevice,
    deviceLandscape = deviceLandscape, deviceRotation = deviceRotation,
    style = prefs.cornerStyle, iconSize = prefs.cornerIconSize.dp, iconMode = prefs.cornerIconMode, appsByKey = appsByKey,
    onToggle = { c, on -> update { it.copy(glanceHotspots = if (on) it.glanceHotspots + c else it.glanceHotspots - c) } },
    onEdit = onEdit, modifier = modifier, capture = capture, large = large,
)
