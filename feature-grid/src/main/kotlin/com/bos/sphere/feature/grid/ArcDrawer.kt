package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.Keys
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// W28 (arcShortcuts). The corner alphabet wheel: a looped ring of letters round a black hub (back arrow, search wedge), the apps of the
// selected letter fanned out as plain launcher icons, and up to four shortcuts as the bottom row. Replaces the wave 26 switcher and colour-picker fan.

private val RingWhite = Color(0xFFF4F4F4)
private val InkDark = Color(0xFF111111)
private val HubBlack = Color(0xFF050505)
private val LetterDim = Color(0xFF9A9A9A)
private val DividerGrey = Color(0xFF3A3A3A)

/** The letter bucket of an app for the wheel: A-Z of its label, "#" for digits, symbols and letters outside A-Z (the drawer's own letter index otherwise). */
internal fun arcLetterOf(label: String): String = wheelLetterOf(label).let { if (it.length == 1 && it[0] in 'A'..'Z') it else "#" }

/** The apps of each ring letter, each list in label order. */
internal fun arcAppsByLetter(apps: List<AppEntry>): Map<String, List<AppEntry>> =
    apps.groupBy { arcLetterOf(it.label) }.mapValues { (_, l) -> l.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }

/** Pixel metrics from the window and the density. */
internal class ArcMetrics(val anchor: Corner, val w: Float, val h: Float, val density: androidx.compose.ui.unit.Density, topInset: Float, shortcutCount: Int) {
    val inset: Float
    val hubR: Float
    val bandInner: Float
    val ringOuter: Float
    val letterR: Float
    val icon: Float
    val pitch: Float
    val gap: Float
    val cx: Float
    val cy: Float
    val slack: Float
    val ringStroke: Stroke
    val lineStroke: Stroke
    val dividers: FloatArray
    val slots: List<FanSlot>
    val shortcutCentres: List<Pair<Float, Float>>
    val searchPos: Offset

    init {
        with(density) {
            inset = 30.dp.toPx(); hubR = 35.dp.toPx(); bandInner = 62.dp.toPx(); ringOuter = 90.dp.toPx(); letterR = 76.dp.toPx()
            icon = ICON_DP.dp.toPx(); pitch = 56.dp.toPx(); gap = 8.dp.toPx(); slack = 10.dp.toPx()
            cx = if (ArcMath.onRight(anchor)) w - inset else inset
            cy = if (ArcMath.onBottom(anchor)) h - inset else inset
            ringStroke = Stroke(ringOuter - hubR)
            lineStroke = Stroke(1.dp.toPx())
            val divU = floatArrayOf(15f, 45f)
            dividers = FloatArray(divU.size * 4)
            for ((i, u) in divU.withIndex()) {
                dividers[i * 4] = ArcMath.pointX(anchor, cx, hubR, u); dividers[i * 4 + 1] = ArcMath.pointY(anchor, cy, hubR, u)
                dividers[i * 4 + 2] = ArcMath.pointX(anchor, cx, bandInner, u); dividers[i * 4 + 3] = ArcMath.pointY(anchor, cy, bandInner, u)
            }
            val sr = (hubR + bandInner) / 2f
            searchPos = Offset(ArcMath.pointX(anchor, cx, sr, 67.5f), ArcMath.pointY(anchor, cy, sr, 67.5f))
            shortcutCentres = ShortcutPlacement.wheelRow(anchor, cx, cy, ringOuter, icon, pitch, gap, shortcutCount)
            val reserved = ArrayList<FRect>()
            // The Radar hotspot of the opposite bottom corner and the top row of the screen stay clear.
            val hot = 64.dp.toPx()
            reserved += if (ArcMath.onRight(anchor)) FRect(0f, h - hot, hot, h) else FRect(w - hot, h - hot, w, h)
            val half = icon / 2f
            for ((x, y) in shortcutCentres) reserved += FRect(x - half, y - half, x + half, y + half)
            slots = ArcFan.slots(anchor, cx, cy, w, h, ringOuter, icon, pitch, gap, topInset + 4.dp.toPx(), reserved, useBottomChain = shortcutCount == 0)
        }
    }

    companion object { const val ICON_DP = 48 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArcDrawerContent(
    anchor: Corner,
    apps: List<AppEntry>,
    shortcuts: List<AppEntry>,
    startLetter: String?,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    interactive: Boolean,
    progress: State<Float>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
        val m = remember(anchor, w, h, density, topInset, shortcuts.size) { ArcMetrics(anchor, w, h, density, topInset, shortcuts.size) }
        val scope = rememberCoroutineScope()
        val haptics = rememberLauncherHaptics()
        val motion = com.bos.sphere.core.design.rememberMotionPrefs()
        val reduced = motion.reduced

        val byLetter = remember(apps) { arcAppsByLetter(apps) }
        val firstLetter = remember(byLetter, startLetter) {
            AlphabetRing.indexOf(startLetter ?: "").takeIf { it >= 0 } ?: AlphabetRing.LETTERS.indexOfFirst { byLetter[it].orEmpty().isNotEmpty() }.coerceAtLeast(0)
        }
        val rot = remember { Animatable(firstLetter.toFloat()) }
        val selected by remember { derivedStateOf { AlphabetRing.selected(rot.value) } }
        val list = byLetter[AlphabetRing.LETTERS[selected]].orEmpty()
        var scrollStart by remember(selected) { mutableIntStateOf(0) }
        var kbItem by remember(selected) { mutableIntStateOf(0) }
        val capacity = m.slots.size
        val range = ArcFan.shown(list.size, capacity, scrollStart)
        val first = ArcFan.clampStart(scrollStart, list.size, capacity)

        val currentMetrics by rememberUpdatedState(m)
        val currentList by rememberUpdatedState(list)
        val currentDismiss by rememberUpdatedState(onDismiss)
        val currentSearch by rememberUpdatedState(onSearch)
        val currentLaunch by rememberUpdatedState(onLaunch)
        val currentKb by rememberUpdatedState(kbItem)
        val currentCap by rememberUpdatedState(capacity)
        val currentSel by rememberUpdatedState(selected)
        val currentStart by rememberUpdatedState(scrollStart)

        fun goTo(index: Int, buzz: Boolean = true) {
            val target = AlphabetRing.rotationFor(rot.value, AlphabetRing.wrap(index))
            if (buzz && AlphabetRing.wrap(index) != currentSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                if (reduced) rot.snapTo(target) else rot.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
            }
        }
        fun stepLetter(d: Int) = goTo(AlphabetRing.step(currentSel, d))
        fun bumpScroll(st: Int) { scrollStart = ArcFan.clampStart(currentStart + st, currentList.size, currentCap) }
        val currentBump by rememberUpdatedState(::bumpScroll)
        val currentGoTo by rememberUpdatedState(::goTo)
        val currentStepLetter by rememberUpdatedState(::stepLetter)

        // INP-18: the drawer owns the keys while open: arrows step the letters round the ring, a typed letter jumps, Tab walks the icons, Enter launches.
        DisposableEffect(interactive) {
            if (!interactive) return@DisposableEffect onDispose { }
            val handler: (android.view.KeyEvent) -> Boolean = h@{ ev ->
                if (ev.isCtrlPressed || ev.isAltPressed || ev.isMetaPressed) return@h false
                val ch = ev.unicodeChar.takeIf { it > 0 }?.toChar()
                val code = ev.keyCode
                val letterIdx = (ch ?: Keys.letterOf(code))?.let { AlphabetRing.indexOfChar(it) } ?: -1
                when {
                    code == Keys.DPAD_LEFT || code == Keys.DPAD_UP -> currentStepLetter(-1)
                    code == Keys.DPAD_RIGHT || code == Keys.DPAD_DOWN -> currentStepLetter(+1)
                    code == Keys.TAB -> {
                        val n = minOf(currentList.size, currentCap)
                        if (n > 0) kbItem = Math.floorMod(currentKb + if (ev.isShiftPressed) -1 else 1, n)
                    }
                    code == Keys.ENTER || code == Keys.DPAD_CENTER || code == Keys.NUMPAD_ENTER ->
                        currentList.getOrNull(ArcFan.clampStart(currentStart, currentList.size, currentCap) + currentKb)?.let { currentLaunch(it) }
                    code == Keys.ESCAPE || code == Keys.BACK -> currentDismiss()
                    code == Keys.SLASH -> currentSearch()
                    letterIdx >= 0 -> currentGoTo(letterIdx, true)
                    else -> return@h code in setOf(Keys.DPAD_CENTER)
                }
                true
            }
            LauncherInput.pushKeyHandler(handler)
            onDispose { LauncherInput.removeKeyHandler(handler) }
        }
        val inputMode by LocalInputMode.current
        val keyboardApp = if (inputMode == com.bos.sphere.core.input.InputMode.Keyboard && interactive) list.getOrNull(first + kbItem)?.key else null

        val measurer = rememberTextMeasurer()
        val normal = remember(measurer, density) { Array(AlphabetRing.COUNT) { measurer.measure(AlphabetRing.LETTERS[it], TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) } }
        val bold = remember(measurer, density) { Array(AlphabetRing.COUNT) { measurer.measure(AlphabetRing.LETTERS[it], TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)) } }
        val hasApps = remember(byLetter) { BooleanArray(AlphabetRing.COUNT) { byLetter[AlphabetRing.LETTERS[it]].orEmpty().isNotEmpty() } }
        val corner = Offset(m.cx, m.cy)
        val geo = { progress.value.coerceIn(0f, 1f) }
        val selDiscR = with(density) { 12.dp.toPx() }
        var wheelCarry by remember { mutableFloatStateOf(0f) }

        FocusLayer(layer = 3, active = interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .linearTraversal()
                    .pointerInput(interactive) {
                        // The scroll wheel (or a trackpad) steps the letters round the ring, one per notch.
                        if (!interactive) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.type != PointerEventType.Scroll) continue
                                val d = ev.changes.first().scrollDelta
                                val (steps, carry) = com.bos.sphere.core.input.PuckKeys.wheelSteps(d.y + d.x, wheelCarry)
                                wheelCarry = carry
                                if (steps != 0) currentStepLetter(steps)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .pointerInput(anchor, m, interactive) {
                        if (!interactive) {
                            awaitEachGesture { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val mm = currentMetrics
                            val startR = hypot(down.position.x - mm.cx, down.position.y - mm.cy)
                            if (startR <= mm.hubR) return@awaitEachGesture // the hub's own buttons handle this touch
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val onBand = startR in (mm.bandInner - mm.slack)..(mm.ringOuter + mm.slack)
                            var lastU = ArcMath.uOf(mm.anchor, mm.cx, mm.cy, down.position.x, down.position.y)
                            var lastR = startR
                            var dragging = false
                            var fanTravel = 0f
                            var up: PointerInputChange? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) { up = change; break }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val u = ArcMath.uOf(mm.anchor, mm.cx, mm.cy, change.position.x, change.position.y)
                                val r = hypot(change.position.x - mm.cx, change.position.y - mm.cy)
                                val du = WheelGeometry.normalize180(u - lastU); val dr = r - lastR
                                lastU = u; lastR = r
                                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    dragging = true
                                    if (onBand) scope.launch { rot.stop() }
                                }
                                if (!dragging || change.isConsumed) continue
                                if (onBand) {
                                    val next = AlphabetRing.rotateBy(rot.value, du)
                                    scope.launch { rot.snapTo(next) }
                                } else {
                                    fanTravel += dr
                                    val st = ArcFan.stepsFor(fanTravel, mm.pitch)
                                    if (st != 0) {
                                        currentBump(st)
                                        fanTravel += st * mm.pitch
                                    }
                                }
                                change.consume()
                            }
                            val release = up
                            if (dragging && onBand) {
                                val v = tracker.calculateVelocity()
                                val omega = if (release != null) WheelGeometry.angularVelocityDeg(release.position.x - mm.cx, release.position.y - mm.cy, v.x, v.y) else 0f
                                val letterVel = (ArcMath.sign(mm.anchor) * omega).coerceIn(-1500f, 1500f) / AlphabetRing.STEP_DEG
                                scope.launch {
                                    if (!reduced && abs(letterVel) > 1f) rot.animateDecay(letterVel, exponentialDecay(frictionMultiplier = 2f))
                                    val target = rot.value.roundToInt().toFloat()
                                    if (reduced) rot.snapTo(target) else rot.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
                                }
                            } else if (!dragging && release != null && !release.isConsumed) {
                                release.consume()
                                val r = hypot(release.position.x - mm.cx, release.position.y - mm.cy)
                                val u = ArcMath.uOf(mm.anchor, mm.cx, mm.cy, release.position.x, release.position.y)
                                if (r in (mm.bandInner)..(mm.ringOuter + mm.slack)) {
                                    val idx = AlphabetRing.letterAt(rot.value, u)
                                    if (idx >= 0) currentGoTo(idx, true)
                                } else if (r > mm.ringOuter + mm.slack) currentDismiss()
                            }
                        }
                    },
            ) {
                // Scrim, the white ring with its letters, the hub. Nothing here allocates per frame beyond the draw calls themselves.
                Canvas(Modifier.fillMaxSize()) {
                    val a = geo()
                    drawRect(Color.Black, alpha = 0.62f * a)
                    scale(0.6f + 0.4f * a, pivot = corner) {
                        drawCircle(RingWhite, radius = (m.ringOuter + m.hubR) / 2f, center = corner, style = m.ringStroke, alpha = a)
                        drawCircle(DividerGrey, radius = m.bandInner, center = corner, style = m.lineStroke, alpha = a)
                        drawLine(DividerGrey, Offset(m.dividers[0], m.dividers[1]), Offset(m.dividers[2], m.dividers[3]), 1.dp.toPx(), alpha = a)
                        drawLine(DividerGrey, Offset(m.dividers[4], m.dividers[5]), Offset(m.dividers[6], m.dividers[7]), 1.dp.toPx(), alpha = a)
                        val rv = rot.value
                        val sel = AlphabetRing.selected(rv)
                        for (i in 0 until AlphabetRing.COUNT) {
                            val u = AlphabetRing.letterU(rv, i)
                            if (u < -30f || u > 120f) continue
                            val px = ArcMath.pointX(anchor, m.cx, m.letterR, u); val py = ArcMath.pointY(anchor, m.cy, m.letterR, u)
                            val isSel = i == sel
                            if (isSel) drawCircle(InkDark, radius = selDiscR, center = Offset(px, py), alpha = a)
                            val layout = if (isSel) bold[i] else normal[i]
                            rotate(ArcMath.radialRotation(anchor, u), Offset(px, py)) {
                                drawText(layout, color = if (isSel) Color.White else if (hasApps[i]) InkDark else LetterDim, topLeft = Offset(px - layout.size.width / 2f, py - layout.size.height / 2f), alpha = a)
                            }
                        }
                        drawCircle(HubBlack, radius = m.hubR, center = corner, alpha = a)
                    }
                }

                // The hub: the back arrow at the centre and the search glyph in its wedge of the ring.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = geo() }) {
                    Box(
                        Modifier
                            .centeredAt { corner }
                            .size(48.dp)
                            .semantics { contentDescription = L10n.tr("Back"); traversalIndex = 1f }
                            .clickable(role = Role.Button) { onDismiss() },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val sw = 2.dp.toPx(); val len = 11.dp.toPx(); val head = 6.dp.toPx()
                            // The arrow points to the screen edge the wheel sits on, away from the rest of the screen.
                            val d = if (ArcMath.onRight(anchor)) 1f else -1f
                            drawLine(Color.White, Offset(c.x - d * len, c.y), Offset(c.x + d * len, c.y), sw, StrokeCap.Round)
                            drawLine(Color.White, Offset(c.x + d * len, c.y), Offset(c.x + d * (len - head), c.y - head), sw, StrokeCap.Round)
                            drawLine(Color.White, Offset(c.x + d * len, c.y), Offset(c.x + d * (len - head), c.y + head), sw, StrokeCap.Round)
                        }
                    }
                    Box(
                        Modifier
                            .centeredAt { m.searchPos }
                            .size(44.dp)
                            .semantics { traversalIndex = 2f },
                    ) { SearchButton(onClick = onSearch, modifier = Modifier.size(44.dp), tint = InkDark) }
                }

                // The ring's accessibility: the wheel as a whole with Next and Previous letter, each letter an item.
                Box(
                    Modifier.fillMaxSize().semantics {
                        contentDescription = L10n.tr("Alphabet wheel")
                        traversalIndex = 3f
                        customActions = listOf(
                            CustomAccessibilityAction(L10n.tr("Next letter")) { stepLetter(1); true },
                            CustomAccessibilityAction(L10n.tr("Previous letter")) { stepLetter(-1); true },
                        )
                    },
                ) {
                    for (i in 0 until AlphabetRing.COUNT) key(i) {
                        val letter = AlphabetRing.LETTERS[i]
                        val count = byLetter[letter].orEmpty().size
                        Box(
                            Modifier
                                .centeredAt {
                                    val u = AlphabetRing.letterU(rot.value, i)
                                    Offset(ArcMath.pointX(anchor, m.cx, m.letterR, u), ArcMath.pointY(anchor, m.cy, m.letterR, u))
                                }
                                .size(24.dp)
                                .semantics {
                                    contentDescription = L10n.tr("Letter %s, %s apps", letter, count.toString())
                                    role = Role.Tab
                                    this.selected = i == selected
                                    traversalIndex = 4f + i * 0.01f
                                    onClick(label = L10n.tr("Show apps")) { goTo(i); true }
                                },
                        )
                    }
                }

                // The shortcuts: the bottom row along the edge, next to the wheel.
                shortcuts.forEachIndexed { i, a ->
                    val centre = m.shortcutCentres.getOrNull(i)
                    if (centre != null) key(a.key) {
                        FanIcon(
                            app = a, m = m, x = centre.first, y = centre.second, geo = geo, rank = i, focused = false, shortcut = true,
                            onClick = { onLaunch(a) }, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPressApp(a) },
                        )
                    }
                }

                // The app fan: the selected letter's apps along the chains, nearest slot first.
                for (idx in range) key(list[idx].key) {
                    val a = list[idx]
                    val slot = m.slots[idx - first]
                    FanIcon(
                        app = a, m = m, x = slot.x, y = slot.y, geo = geo, rank = 10 + idx, focused = a.key == keyboardApp, shortcut = false,
                        onClick = { onLaunch(a) }, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPressApp(a) },
                    )
                }
                if (list.isEmpty()) {
                    val r = m.ringOuter + 40f
                    androidx.compose.material3.Text(
                        text = L10n.tr("No apps"), color = Color(0xFFBBBBBB), fontSize = 13.sp,
                        modifier = Modifier
                            .centeredAt { Offset(ArcMath.pointX(anchor, m.cx, r, 45f), ArcMath.pointY(anchor, m.cy, r, 45f)) }
                            .graphicsLayer { alpha = geo() },
                    )
                }
            }
        }
    }
}

/**
 * One app of the fan or the shortcut row: the plain launcher icon exactly as the icon cache gives it ([AppIcon]; no disc, no clip, no
 * ring, no caption), 48 dp. The label is its content description; long press opens the app menu or, moving on, drags it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FanIcon(
    app: AppEntry,
    m: ArcMetrics,
    x: Float,
    y: Float,
    geo: () -> Float,
    rank: Int,
    focused: Boolean,
    shortcut: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val half = m.icon / 2f
    Box(
        Modifier
            .offset {
                val s = 0.5f + 0.5f * geo()
                IntOffset((m.cx + (x - m.cx) * s - half).roundToInt(), (m.cy + (y - m.cy) * s - half).roundToInt())
            }
            .size(ArcMetrics.ICON_DP.dp)
            .graphicsLayer { alpha = geo() }
            .semantics {
                contentDescription = app.label
                traversalIndex = rank.toFloat()
                if (!shortcut) collectionItemInfo = CollectionItemInfo(rank - 10, 1, 0, 1)
            }
            .drawWithContent {
                drawContent()
                if (focused) drawRoundRect(Color.White, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            .launcherItem(if (shortcut) "wheel.shortcut.${app.key}" else "wheel.app.${app.key}", FocusKind.PuckSpoke, layer = 3, focusable = false, onMenu = onLongClick, ringRadiusDp = 26f)
            .appDragSource(app.key)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick),
    ) { AppIcon(app = app, size = ArcMetrics.ICON_DP.dp) }
}
