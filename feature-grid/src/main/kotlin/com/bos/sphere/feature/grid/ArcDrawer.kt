package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.SwitcherStyle
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.PuckEffect
import com.bos.sphere.core.input.PuckKey
import com.bos.sphere.core.input.PuckKeys
import com.bos.sphere.core.input.PuckLayout
import com.bos.sphere.core.input.PuckMode
import com.bos.sphere.core.input.PuckState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// W26 (arcDrawer). The corner arc drawer in two forms: the quarter-ring section SWITCHER (hub + wedges, looped, compact or expanded)
// and, when it expands, the colour-picker style FAN: one ring of rotated cells per section, stacked outward from the hub.

private val WedgeLight = Color(0xFFF1F1F1)
private val WedgeDark = Color(0xFF121212)
private val HubLight = Color(0xFFDCDCDC)
private val HubInk = Color(0xFF1A1A1A)
private val CellLight = Color(0xFFF2F2F2)
private val CellInk = Color(0xFF1A1A1A)
private val PillGrey = Color(0xFFC4C4C4)
private val SectionColours = listOf(
    Color(0xFF1E88E5), Color(0xFF2EBD6B), Color(0xFF8E6BFF), Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF14B8A6),
)

/** Pixel metrics, from the window and the density; everything the pure [ArcMath] and the drawing need. */
internal class ArcMetrics(val anchor: Corner, val w: Float, val h: Float, density: androidx.compose.ui.unit.Density, actionCount: Int) {
    val minSideDp: Float
    val puck: com.bos.sphere.core.input.PuckLayoutResult
    val inset: Float
    val cell: Float
    val gap: Float
    val hubCollapsed: Float
    val hubExpanded: Float
    val band: Float
    val cx: Float
    val cy: Float
    val rings: List<ArcMath.Ring>
    val trackStroke: Stroke
    val cellStroke: Stroke
    val edgeStroke: Stroke
    val selEdgeStroke: Stroke
    val bandStroke: Stroke
    val bandSelStroke: Stroke
    val slack: Float

    init {
        with(density) {
            minSideDp = min(w, h).toDp().value
            puck = PuckLayout.compute(minSideDp, actionCount)
            inset = 36.dp.toPx()
            cell = CELL_DP.dp.toPx()
            gap = 6.dp.toPx()
            hubCollapsed = 66.dp.toPx()
            hubExpanded = max(puck.hubRadiusDp.dp.toPx(), hubCollapsed + 24.dp.toPx())
            band = 48.dp.toPx()
            slack = 10.dp.toPx()
            cx = if (ArcMath.onRight(anchor)) w - inset else inset
            cy = if (ArcMath.onBottom(anchor)) h - inset else inset
            rings = ArcMath.rings(anchor, cx, cy, w, h, hubExpanded + 3.dp.toPx() + band, cell, gap, MAX_RINGS)
            trackStroke = Stroke(cell + gap * 0.5f)
            cellStroke = Stroke(2.dp.toPx())
            edgeStroke = Stroke(2.5.dp.toPx())
            selEdgeStroke = Stroke(4.dp.toPx())
            bandStroke = Stroke(band)
            bandSelStroke = Stroke(band + 6f)
        }
    }

    fun hubR(g: Float) = hubCollapsed + (hubExpanded - hubCollapsed) * g
    fun bandInner(g: Float) = hubR(g) + 3f
    fun bandOuter(g: Float) = bandInner(g) + band
    val ringsOuter: Float get() = rings.lastOrNull()?.let { it.radius + cell / 2f } ?: bandOuter(1f)

    companion object {
        const val CELL_DP = 52
        const val MAX_RINGS = 8
    }
}

/** The glyph of a section: its icon when it has one, else its short text (the letter of an A-Z section). */
@Composable
private fun SectionGlyph(group: WheelGroup, tint: Color, size: Dp) {
    val glyph = group.glyph
    if (glyph != null) {
        androidx.compose.material3.Icon(glyph, null, tint = tint, modifier = Modifier.size(size))
    } else {
        Text(group.label.take(2), color = tint, fontSize = (size.value * 0.8f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArcDrawerContent(
    anchor: Corner,
    apps: List<AppEntry>,
    grouping: WheelGrouping,
    onGroupingChange: (WheelGrouping) -> Unit,
    recentKeys: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    actions: List<PuckAction>,
    onDismiss: () -> Unit,
    interactive: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    switcherStyle: SwitcherStyle,
    progress: State<Float>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val m = remember(anchor, w, h, density, actions.size) { ArcMetrics(anchor, w, h, density, actions.size) }
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val haptics = rememberLauncherHaptics()
        val motion = com.bos.sphere.core.design.rememberMotionPrefs()

        val categories by produceState(initialValue = WheelCategoryCache.cachedFor(apps.map { it.packageName }), apps, grouping) {
            if (grouping == WheelGrouping.Category && value == null) value = WheelCategoryCache.resolve(context, apps.map { it.packageName })
        }
        val groups = remember(apps, grouping, recentKeys, categories) { buildWheelGroups(apps, grouping, recentKeys, categories) }
        val n = groups.size
        var sel by remember(grouping, anchor) { mutableIntStateOf(0) }
        val selected = ArcMath.wrap(sel, n)
        var kbItem by remember(grouping, anchor) { mutableIntStateOf(0) }
        val offsets = remember(groups) { Array(n) { Animatable(0f) } }

        val expandedNow by androidx.compose.runtime.rememberUpdatedState(expanded)
        val e = remember { Animatable(0f) }
        LaunchedEffect(expanded, motion.reduced) {
            val target = if (expanded) 1f else 0f
            if (motion.reduced) e.animateTo(target, tween(com.bos.sphere.core.quality.MorphPolicy.progressMs(motion, 120)))
            else e.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
        // Geometry follows the animation, except under reduced motion where it is final at once and only the alpha fades.
        val geo = { if (motion.reduced) (if (expandedNow) 1f else 0f) else e.value }
        val showRings by remember { derivedStateOf { e.value > 0.002f } }

        val currentGroups by rememberUpdatedState(groups)
        val currentMetrics by rememberUpdatedState(m)
        val currentSel by rememberUpdatedState(selected)
        val currentExpanded by rememberUpdatedState(expanded)
        val currentSetExpanded by rememberUpdatedState(onExpandedChange)
        val currentDismiss by rememberUpdatedState(onDismiss)
        val currentKbItem by rememberUpdatedState(kbItem)
        val currentGrouping by rememberUpdatedState(grouping)
        val currentActions by rememberUpdatedState(actions)

        fun select(i: Int, buzz: Boolean = true) {
            val next = ArcMath.wrap(i, currentGroups.size)
            if (next == currentSel) return
            sel = next
            kbItem = 0
            if (buzz) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        fun stepSection(d: Int) = select(ArcMath.step(currentSel, d, currentGroups.size))
        val currentStep by rememberUpdatedState(::stepSection)
        val currentSelect by rememberUpdatedState(::select)

        // The keyboard's app stays in view: turn its ring only as far as needed.
        LaunchedEffect(kbItem, selected, expanded) {
            val ring = m.rings.firstOrNull() ?: return@LaunchedEffect
            val g = groups.getOrNull(selected) ?: return@LaunchedEffect
            val off = offsets.getOrNull(selected) ?: return@LaunchedEffect
            val to = ArcMath.offsetToShow(ring, g.apps.size + 1, off.value, kbItem + 1)
            if (to != off.value) if (motion.reduced) off.snapTo(to) else off.animateTo(to, spring(stiffness = Spring.StiffnessLow))
        }

        // INP-18: keyboard and scroll wheel. The drawer owns its keys while open.
        DisposableEffect(interactive) {
            if (!interactive) return@DisposableEffect onDispose { }
            val handler: (android.view.KeyEvent) -> Boolean = h@{ ev ->
                if (ev.isCtrlPressed || ev.isAltPressed || ev.isMetaPressed) return@h false
                val ch = ev.unicodeChar.takeIf { it > 0 }?.toChar()
                if (!PuckKeys.owns(ev.keyCode, ch)) return@h false
                val g = currentGroups.getOrNull(currentSel)
                if (!currentExpanded && ev.keyCode in listOf(
                        com.bos.sphere.core.input.Keys.ENTER, com.bos.sphere.core.input.Keys.DPAD_CENTER, com.bos.sphere.core.input.Keys.NUMPAD_ENTER,
                        com.bos.sphere.core.input.Keys.DPAD_DOWN, com.bos.sphere.core.input.Keys.DPAD_UP,
                    ) && g != null
                ) { currentSetExpanded(true); return@h true }
                val state = PuckState(PuckMode.entries[currentGrouping.ordinal], currentGroups.map { it.label }, currentSel, currentKbItem, g?.apps?.size ?: 0)
                when (val eff = PuckKeys.onKey(state, PuckKey(ev.keyCode, ev.isShiftPressed, ch))) {
                    is PuckEffect.FocusGroup -> currentSelect(eff.index, true)
                    is PuckEffect.SetMode -> onGroupingChange(WheelGrouping.entries[eff.mode.ordinal])
                    is PuckEffect.SelectItem -> kbItem = eff.index
                    PuckEffect.Launch -> g?.apps?.getOrNull(currentKbItem)?.let { onLaunch(it) }
                    PuckEffect.Search -> onSearch()
                    is PuckEffect.Action -> currentActions.getOrNull(eff.index)?.onClick?.invoke()
                    PuckEffect.Close -> if (currentExpanded) currentSetExpanded(false) else currentDismiss()
                    null -> Unit
                }
                true
            }
            LauncherInput.pushKeyHandler(handler)
            onDispose { LauncherInput.removeKeyHandler(handler) }
        }
        var wheelCarry by remember { mutableFloatStateOf(0f) }
        val inputMode by LocalInputMode.current
        val keyboardApp = if (inputMode == com.bos.sphere.core.input.InputMode.Keyboard && interactive && expanded) groups.getOrNull(selected)?.apps?.getOrNull(kbItem)?.key else null

        val measurer = rememberTextMeasurer()
        val corner = Offset(m.cx, m.cy)
        val scrim = remember(w, h, corner) {
            val reach = hypot(w, h) * 0.95f
            Brush.radialGradient(
                0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.88f), 0.65f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent,
                center = corner, radius = reach,
            )
        }
        val wedgeList = ArcMath.wedgeSections(selected, n)
        val ringCount = min(m.rings.size, n)
    
        FocusLayer(layer = 3, active = interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .linearTraversal()
                    .pointerInput(interactive) {
                        // The scroll wheel (or a trackpad) steps the sections round the ring, one per notch.
                        if (!interactive) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.type != PointerEventType.Scroll) continue
                                val d = ev.changes.first().scrollDelta
                                val (steps, carry) = PuckKeys.wheelSteps(d.y + d.x, wheelCarry)
                                wheelCarry = carry
                                if (steps != 0 && currentGroups.isNotEmpty()) currentStep(steps)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .pointerInput(anchor, m, interactive, offsets) {
                        if (!interactive) {
                            awaitEachGesture { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val mm = currentMetrics
                            val g0 = if (motion.reduced) (if (currentExpanded) 1f else 0f) else e.value
                            val startR = hypot(down.position.x - mm.cx, down.position.y - mm.cy)
                            if (startR <= mm.hubR(g0)) return@awaitEachGesture // the hub's own buttons handle this touch
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val startU = ArcMath.uOf(mm.anchor, mm.cx, mm.cy, down.position.x, down.position.y)
                            val inBand = startR in (mm.bandInner(g0) - mm.slack)..(mm.bandOuter(g0) + mm.slack)
                            val sgn = ArcMath.sign(mm.anchor)
                            var lastAngle = Math.toDegrees(kotlin.math.atan2((down.position.y - mm.cy).toDouble(), (down.position.x - mm.cx).toDouble())).toFloat()
                            var dragging = false
                            var mode = 0 // 0 undecided, 1 sections, 2 ring turn, 3 expand, -1 ignore
                            var accum = 0f
                            var ringIdx = -1
                            var up: PointerInputChange? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) { up = change; break }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dx = change.position.x - mm.cx; val dy = change.position.y - mm.cy
                                val ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val dAng = WheelGeometry.normalize180(ang - lastAngle)
                                lastAngle = ang
                                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                                if (!dragging) continue
                                if (mode == 0) {
                                    val r = hypot(dx, dy)
                                    val radial = r - startR
                                    val tangential = abs(Math.toRadians(WheelGeometry.normalize180(ang - Math.toDegrees(kotlin.math.atan2((down.position.y - mm.cy).toDouble(), (down.position.x - mm.cx).toDouble())).toFloat()).toDouble()).toFloat()) * startR
                                    mode = when {
                                        !currentExpanded && radial > tangential && radial > 0f -> 3
                                        inBand && tangential >= abs(radial) -> 1
                                        inBand -> -1
                                        currentExpanded -> {
                                            ringIdx = ArcMath.ringAt(mm.rings, startR, mm.cell, mm.gap).let { i -> if (i >= 0) i else mm.rings.indexOfMinBy { abs(startR - it.radius) } }
                                            if (ringIdx in 0 until min(mm.rings.size, currentGroups.size)) 2 else -1
                                        }
                                        else -> -1
                                    }
                                    if (mode == 2) scope.launch { offsets.getOrNull(ArcMath.wrap(currentSel + ringIdx, currentGroups.size))?.stop() }
                                }
                                val du = sgn * dAng
                                when (mode) {
                                    3 -> { currentSetExpanded(true); mode = -1; change.consume() }
                                    1 -> {
                                        accum += du
                                        while (accum <= -SWEEP) { currentStep(1); accum += SWEEP }
                                        while (accum >= SWEEP) { currentStep(-1); accum -= SWEEP }
                                        change.consume()
                                    }
                                    2 -> {
                                        val sec = ArcMath.wrap(currentSel + ringIdx, currentGroups.size)
                                        val ring = mm.rings[ringIdx]
                                        val count = currentGroups[sec].apps.size + 1
                                        val a = offsets[sec]
                                        val next = ArcMath.clampOffset(ring, count, a.value + du)
                                        scope.launch { a.snapTo(next) }
                                        change.consume()
                                    }
                                }
                            }
                            val release = up
                            if (dragging && mode == 2 && release != null && !motion.reduced) {
                                val sec = ArcMath.wrap(currentSel + ringIdx, currentGroups.size)
                                val ring = mm.rings[ringIdx]
                                val count = currentGroups[sec].apps.size + 1
                                val v = tracker.calculateVelocity()
                                val omega = WheelGeometry.angularVelocityDeg(release.position.x - mm.cx, release.position.y - mm.cy, v.x, v.y)
                                val duDot = (sgn * omega).coerceIn(-1500f, 1500f)
                                val a = offsets[sec]
                                val b = ArcMath.offsetBounds(ring, count)
                                scope.launch {
                                    a.updateBounds(b.start, b.endInclusive)
                                    if (abs(duDot) > 20f) a.animateDecay(duDot, exponentialDecay(frictionMultiplier = 1.4f))
                                }
                            } else if (!dragging && release != null && !release.isConsumed) {
                                release.consume()
                                val r = hypot(release.position.x - mm.cx, release.position.y - mm.cy)
                                val u = ArcMath.uOf(mm.anchor, mm.cx, mm.cy, release.position.x, release.position.y)
                                val gNow = if (motion.reduced) (if (currentExpanded) 1f else 0f) else e.value
                                val count = ArcMath.wedgeSections(currentSel, currentGroups.size).size
                                when {
                                    r in (mm.bandInner(gNow) - mm.slack)..(mm.bandOuter(gNow) + mm.slack) -> {
                                        val pos = ArcMath.wedgeAt(u, count)
                                        if (pos == 0) currentSetExpanded(!currentExpanded)
                                        else if (pos > 0) currentSelect(currentSel + pos, true)
                                    }
                                    currentExpanded -> currentSetExpanded(false)
                                    else -> currentDismiss()
                                }
                            }
                        }
                    },
            ) {
                // Scrim, ring tracks, hub, wedges. Nothing here allocates per frame beyond the draw calls themselves.
                Canvas(Modifier.fillMaxSize()) {
                    val a = progress.value.coerceIn(0f, 1f)
                    drawRect(scrim, alpha = a)
                    val g = geo()
                    val ea = e.value.coerceIn(0f, 1f)
                    val s = 1f
                    if (ea > 0.002f) {
                        val scale = 0.55f + 0.45f * g
                        for (k in 0 until ringCount) {
                            val ring = m.rings[k]
                            val rr = ring.radius * scale
                            val start = if (ArcMath.sign(anchor) > 0) ArcMath.screenAngle(anchor, ring.lo) else ArcMath.screenAngle(anchor, ring.hi)
                            drawArc(
                                color = Color.White.copy(alpha = 0.07f * ea * a), startAngle = start, sweepAngle = ring.window, useCenter = false,
                                topLeft = Offset(m.cx - rr, m.cy - rr), size = Size(rr * 2f, rr * 2f), style = m.trackStroke,
                            )
                        }
                    }
                    val hubR = m.hubR(g) * s
                    drawCircle(HubLight, radius = hubR, center = corner, alpha = a)
                    val inner = m.bandInner(g) * s
                    val thick = m.band
                    val mid = inner + thick / 2f
                    for (p in wedgeList.indices) {
                        val sec = wedgeList[p]
                        val u0 = p * SWEEP; val u1 = u0 + SWEEP
                        val start = if (ArcMath.sign(anchor) > 0) ArcMath.screenAngle(anchor, u0) else ArcMath.screenAngle(anchor, u1)
                        val isSel = p == 0
                        val rr = if (isSel) mid + 3f else mid
                        drawArc(
                            color = if (isSel) WedgeDark else WedgeLight, startAngle = start + 0.4f, sweepAngle = SWEEP - 0.8f, useCenter = false,
                            topLeft = Offset(m.cx - rr, m.cy - rr), size = Size(rr * 2f, rr * 2f), style = if (isSel) m.bandSelStroke else m.bandStroke, alpha = a,
                        )
                        val er = inner - 1f
                        drawArc(
                            color = SectionColours[sec % SectionColours.size], startAngle = start + 1f, sweepAngle = SWEEP - 2f, useCenter = false,
                            topLeft = Offset(m.cx - er, m.cy - er), size = Size(er * 2f, er * 2f), style = if (isSel) m.selEdgeStroke else m.edgeStroke,
                            alpha = a,
                        )
                    }
                }

                // Wedge icons (and, when Expanded, the small count) with their semantics; taps are read by the pointer logic above.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                ) {
                    wedgeList.forEachIndexed { p, sec ->
                        val grp = groups[sec]
                        val isSel = p == 0
                        key(p) {
                            Box(
                                Modifier
                                    .centeredAt {
                                        val g = geo()
                                        val r = (m.bandInner(g) + m.band / 2f)
                                        val u = p * SWEEP + SWEEP / 2f
                                        Offset(ArcMath.pointX(anchor, m.cx, r, u), ArcMath.pointY(anchor, m.cy, r, u))
                                    }
                                    .size(48.dp)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = L10n.tr("%s, %s apps", grp.label, grp.apps.size.toString())
                                        role = Role.Tab
                                        this.selected = isSel
                                        onClick(label = if (isSel) L10n.tr(if (expanded) "Collapse" else "Expand") else L10n.tr("Show section")) {
                                            if (isSel) currentSetExpanded(!currentExpanded) else currentSelect(currentSel + p, true); true
                                        }
                                        if (isSel) customActions = listOf(
                                            CustomAccessibilityAction(L10n.tr("Next section")) { currentStep(1); true },
                                            CustomAccessibilityAction(L10n.tr("Previous section")) { currentStep(-1); true },
                                            CustomAccessibilityAction(L10n.tr(if (expanded) "Collapse" else "Expand")) { currentSetExpanded(!currentExpanded); true },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                SectionGlyph(grp, if (isSel) Color.White else HubInk, 22.dp)
                                if (switcherStyle == SwitcherStyle.Expanded) {
                                    Text(
                                        if (grp.apps.size > 99) "99+" else grp.apps.size.toString(),
                                        color = if (isSel) Color(0xFFE0E0E0) else Color(0xFF555555), fontSize = 9.sp, maxLines = 1,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // The hub: the Radar glyph always; the mode row and the ring actions once expanded.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                ) {
                    Crater(m.puck.searchSizeDp.dp, accent = true, modifier = Modifier.centeredAt { corner }.semantics { traversalIndex = 0f }) {
                        SearchButton(onClick = onSearch, modifier = Modifier.size(m.puck.searchSizeDp.dp))
                    }
                    if (showRings) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = e.value.coerceIn(0f, 1f) }) {
                            val chipR = with(density) { m.puck.chipRadiusDp.dp.toPx() } * 0.85f
                            WheelGrouping.entries.forEachIndexed { i, gMode ->
                                val isMode = gMode == grouping
                                val u = MODE_U[i.coerceAtMost(MODE_U.lastIndex)]
                                Box(
                                    Modifier
                                        .centeredAt { Offset(ArcMath.pointX(anchor, m.cx, chipR, u), ArcMath.pointY(anchor, m.cy, chipR, u)) }
                                        .size(width = 84.dp, height = 48.dp)
                                        .semantics { contentDescription = L10n.tr(gMode.label); role = Role.Tab; this.selected = isMode }
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null, role = Role.Tab,
                                        ) { if (!isMode) onGroupingChange(gMode) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        L10n.tr(gMode.label), color = HubInk, fontSize = 14.sp,
                                        fontWeight = if (isMode) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                                        modifier = Modifier
                                            .then(if (isMode) Modifier.drawBehind { drawRoundRect(PillGrey, cornerRadius = CornerRadius(8.dp.toPx())) } else Modifier)
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            actions.take(m.puck.slots.size).forEachIndexed { i, a ->
                                val slot = m.puck.slots[i]
                                val rPx = with(density) { slot.radiusDp.dp.toPx() }
                                Crater(
                                    m.puck.actionSizeDp.dp,
                                    modifier = Modifier
                                        .centeredAt { Offset(ArcMath.pointX(anchor, m.cx, rPx, slot.angleDeg), ArcMath.pointY(anchor, m.cy, rPx, slot.angleDeg)) }
                                        .semantics { traversalIndex = (i + 1).toFloat() },
                                ) {
                                    Box(
                                        Modifier
                                            .size(m.puck.actionSizeDp.dp)
                                            .semantics { contentDescription = a.label }
                                            .launcherItem("puck.action.$i", FocusKind.PuckRing, order = i + 1, layer = 3, focusable = false, ringRadiusDp = 21f)
                                            .clickable(role = Role.Button) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); a.onClick() },
                                        contentAlignment = Alignment.Center,
                                    ) { androidx.compose.material3.Icon(a.glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size((m.puck.actionSizeDp * 0.52f).dp)) }
                                }
                            }
                        }
                    }
                }

                // The rings: one per section starting at the selected one, each a list of rotated cells; only the visible cells are composed.
                if (showRings) {
                    for (k in 0 until ringCount) {
                        val sec = ArcMath.wrap(selected + k, n)
                        val ring = m.rings[k]
                        val grp = groups[sec]
                        val off = offsets[sec]
                        val count = grp.apps.size + 1
                        key(sec) {
                            val range by remember(ring, count, off) { derivedStateOf { ArcMath.visibleCells(ring, count, off.value) } }
                            Box(
                                Modifier.fillMaxSize().semantics {
                                    contentDescription = L10n.tr("%s, %s apps", grp.label, grp.apps.size.toString())
                                    collectionInfo = CollectionInfo(rowCount = grp.apps.size, columnCount = 1)
                                    traversalIndex = 10f + k
                                },
                            ) {
                                for (j in range) key(j) {
                                    val appForCell = if (j == 0) null else grp.apps[j - 1]
                                    ArcCell(
                                        anchor = anchor, m = m, ring = ring, j = j, off = off, geo = geo, ea = { e.value.coerceIn(0f, 1f) * progress.value.coerceIn(0f, 1f) },
                                        label = grp.label, app = appForCell, measurer = measurer, sectionColour = SectionColours[sec % SectionColours.size],
                                        focusedByKeyboard = appForCell != null && appForCell.key == keyboardApp,
                                        headerSelected = k == 0,
                                        itemIndex = j - 1,
                                        onClick = { appForCell?.let { onLaunch(it) } },
                                        onLongClick = { appForCell?.let { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPressApp(it) } },
                                    )
                                }
                            }
                        }
                    }
                }

                if (groups.isEmpty()) {
                    val text = when {
                        grouping == WheelGrouping.Recent -> "No recent apps yet"
                        grouping == WheelGrouping.Category && categories == null -> "Sorting…"
                        else -> "No apps"
                    }
                    val r = m.bandOuter(0f) + 40f
                    Text(
                        text = L10n.tr(text), color = HyleColors.InkDim, fontSize = 13.sp,
                        modifier = Modifier
                            .centeredAt { Offset(ArcMath.pointX(anchor, m.cx, r, 45f), ArcMath.pointY(anchor, m.cy, r, 45f)) }
                            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

private const val SWEEP = ArcMath.QUADRANT / ArcMath.WEDGES
private val MODE_U = floatArrayOf(20f, 46f, 72f)

private inline fun <T> List<T>.indexOfMinBy(sel: (T) -> Float): Int {
    var best = -1; var bv = Float.MAX_VALUE
    forEachIndexed { i, t -> val v = sel(t); if (v < bv) { bv = v; best = i } }
    return best
}

/** One cell of a ring: a rounded square at its place on the arc, turned so its own up points away from the corner; the label reads along the radial direction. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArcCell(
    anchor: Corner,
    m: ArcMetrics,
    ring: ArcMath.Ring,
    j: Int,
    off: Animatable<Float, *>,
    geo: () -> Float,
    ea: () -> Float,
    label: String,
    app: AppEntry?,
    measurer: TextMeasurer,
    sectionColour: Color,
    focusedByKeyboard: Boolean,
    headerSelected: Boolean,
    itemIndex: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val density = LocalDensity.current
    val cellDp = ArcMetrics.CELL_DP.dp
    val text = app?.label ?: label
    val isHeader = app == null
    val textLayout: TextLayoutResult = remember(text, isHeader, density) {
        measurer.measure(
            text = text,
            style = TextStyle(fontSize = if (isHeader) 11.sp else 8.5.sp, fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium, color = if (isHeader) Color.White else CellInk),
            overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
            constraints = Constraints(maxWidth = with(density) { (if (isHeader) 44.dp else 34.dp).roundToPx() }),
        )
    }
    val base = Modifier
        .centeredAt {
            val s = 0.55f + 0.45f * geo()
            val u = ArcMath.cellU(ring, j, off.value)
            Offset(ArcMath.pointX(anchor, m.cx, ring.radius * s, u), ArcMath.pointY(anchor, m.cy, ring.radius * s, u))
        }
        .size(cellDp)
        .graphicsLayer {
            alpha = ea()
            rotationZ = ArcMath.cellRotation(anchor, ArcMath.cellU(ring, j, off.value))
        }
    if (isHeader) {
        Box(
            base
                .semantics { heading() }
                .drawBehind {
                    drawRoundRect(WedgeDark, cornerRadius = CornerRadius(10.dp.toPx()))
                    drawRoundRect(sectionColour, cornerRadius = CornerRadius(10.dp.toPx()), style = m.cellStroke)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    rotate(if (ArcMath.onRight(anchor)) 90f else -90f, c) { drawText(textLayout, topLeft = Offset(c.x - textLayout.size.width / 2f, c.y - textLayout.size.height / 2f)) }
                },
        )
    } else {
        val a = app!!
        Box(
            base
                .semantics {
                    contentDescription = a.label
                    collectionItemInfo = CollectionItemInfo(itemIndex, 1, 0, 1)
                }
                .drawBehind {
                    drawRoundRect(CellLight, cornerRadius = CornerRadius(10.dp.toPx()))
                    val c = Offset(size.width - 12.dp.toPx(), size.height / 2f)
                    rotate(if (ArcMath.onRight(anchor)) 90f else -90f, c) { drawText(textLayout, topLeft = Offset(c.x - textLayout.size.width / 2f, c.y - textLayout.size.height / 2f)) }
                }
                .drawWithContent {
                    drawContent()
                    if (focusedByKeyboard) drawRoundRect(HubInk, cornerRadius = CornerRadius(10.dp.toPx()), style = m.cellStroke)
                }
                .launcherItem("puck.app.${a.key}", FocusKind.PuckSpoke, layer = 3, focusable = false, onMenu = onLongClick, ringRadiusDp = 24f)
                .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick),
        ) {
            AppIcon(app = a, size = 28.dp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        }
    }
}
