package com.bos.sphere.feature.grid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.PagingAxis
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pager state whose page count GridHome drives (it adds a trailing drop page while dragging).
 * Hoisted so the host can jump pages (e.g. Home press → first page) and read the current page.
 */
class GridHomeState internal constructor(val pager: PagerState, internal val count: androidx.compose.runtime.MutableIntState) {
    val currentPage: Int get() = pager.currentPage
    suspend fun goToPage(page: Int, animate: Boolean = true) {
        val p = page.coerceIn(0, (count.intValue - 1).coerceAtLeast(0))
        if (animate) pager.animateScrollToPage(p) else pager.scrollToPage(p)
    }
}

@Composable
fun rememberGridHomeState(): GridHomeState {
    val count = remember { mutableIntStateOf(1) }
    val pager = androidx.compose.foundation.pager.rememberPagerState { count.intValue }
    return remember(pager) { GridHomeState(pager, count) }
}

/** Slot geometry of one page, grid or circle, in px within the page area. */
internal sealed interface PageGeometry {
    val capacity: Int
    val iconPx: Float
    fun center(index: Int): Pt
    fun slotAt(x: Float, y: Float): Int
    fun nearest(x: Float, y: Float): Int

    class Grid(val g: GridGeometry, iconScale: Float, labels: Boolean) : PageGeometry {
        override val capacity = g.capacity
        override val iconPx = g.pitch * iconScale * (if (labels) 0.78f else 1f)
        val labelOffset = if (labels) g.pitch * 0.06f else 0f
        override fun center(index: Int) = Pt(g.cellLeft(index) + g.pitch / 2f, g.cellTop(index) + g.pitch / 2f - labelOffset * 2)
        override fun slotAt(x: Float, y: Float) = g.cellAt(x, y)
        override fun nearest(x: Float, y: Float) = g.nearestCell(x, y)
    }

    class Coverflow(val c: CoverflowGeometry) : PageGeometry {
        override val capacity = PageLayout.COVERFLOW_CAPACITY
        override val iconPx = c.focusSize
        override fun center(index: Int) = c.center(index)
        override fun slotAt(x: Float, y: Float) = c.slotAt(x, y)
        override fun nearest(x: Float, y: Float) = c.nearest(x)
    }

    class Circle(val c: CircleGeometry) : PageGeometry {
        override val capacity = c.capacity
        override val iconPx = c.iconSize
        override fun center(index: Int) = c.slotCenter(index)
        override fun slotAt(x: Float, y: Float) = c.slotAt(x, y)
        override fun nearest(x: Float, y: Float) = c.nearestSlot(x, y)
    }

    companion object {
        fun of(
            layout: PageLayout,
            size: IntSize,
            prefs: LauncherPrefs,
            landscape: Boolean,
            count: Int = 0,
            coverPosition: Float = 0f,
        ): PageGeometry {
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            return when (layout.shape) {
                PageShape.Grid -> {
                    val spec = layout.gridFor(landscape, prefs)
                    Grid(
                        GridGeometry.compute(w, h, spec.columns, spec.rows, verticalBias = if (landscape) 0.5f else 0.12f),
                        prefs.iconScale,
                        prefs.showLabels,
                    )
                }
                PageShape.Circle -> Circle(CircleGeometry.compute(w, h, layout.rings))
                PageShape.Coverflow -> Coverflow(CoverflowGeometry(w, h, count, coverPosition))
            }
        }
    }
}

private sealed interface Hit {
    data class App(val key: String) : Hit
    data class Suggested(val app: AppEntry) : Hit
    /** A non-focused coverflow item: tapping brings it to the centre. */
    data class Focus(val page: Int, val index: Int) : Hit
    data class Tile(val key: String) : Hit
    data object Empty : Hit
}

private enum class Early { Up, Cancel, Swipe, Pinch }

/**
 * The paged home surface from the reference: a clock, pages of full-bleed icons (each page its
 * own grid density or a circular arrangement), and a page indicator along the paging axis.
 * Corners are layered on top by the caller ([CornerLayer]).
 *
 * One gesture arbiter on the page area handles: tap → launch; long-press → app menu (or page
 * menu on empty space); long-press-and-drag → rearrange (across pages, with edge-dwell page
 * flips and a trailing blank page to drop onto); pinch in/out; swipes perpendicular to the
 * paging axis; double-tap on empty space. Paging itself is the pager's own scroll.
 */
@Composable
fun GridHome(
    pages: List<HomePage>,
    appsByKey: Map<String, AppEntry>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    state: GridHomeState,
    suggestion: AppEntry?,
    gesturesBound: Set<HomeGesture>,
    capacityOf: (PageLayout) -> Int,
    onLaunch: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onPagesChange: (List<HomePage>) -> Unit,
    onEmptyLongPress: (page: Int) -> Unit,
    onGesture: (HomeGesture) -> Unit,
    onClockTap: () -> Unit,
    onTileLongPress: (key: String, page: Int) -> Unit,
    weather: com.bos.sphere.core.data.WeatherInfo? = null,
    modifier: Modifier = Modifier,
) {
    val paging = prefs.pagingFor(isLandscape)
    val pagerState = state.pager
    // The whole surface is laid out in physical (LTR) coordinates — geometry, hit-testing and
    // gestures are physical, and corners are pinned to physical corners — so RTL locales draw
    // exactly where touches land. Text inside still shapes right-to-left.
    val rtl = false
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // The pager fills the whole screen so pages slide edge to edge; each page insets its own
    // content. [areaSize] is that inner content area and [contentOffset] its top-left in screen px.
    var fullSize by remember { mutableStateOf(IntSize.Zero) }
    val safeInsets = WindowInsets.safeDrawing
    val dir = LayoutDirection.Ltr
    val indicatorAtStartEarly = prefs.pagingFor(isLandscape) == PagingAxis.Vertical
    val clockTop = prefs.clockPosition == ClockPosition.Top
    val clockBottom = prefs.clockPosition == ClockPosition.Bottom
    val clockH = clockBlockHeight(prefs)
    val cornerRow = prefs.cornerIconSize.dp + 22.dp
    val padTopDp = if (clockTop) maxOf(clockH + 14.dp, cornerRow) else cornerRow
    val padBottomDp = when {
        clockBottom && !indicatorAtStartEarly -> maxOf(clockH + 50.dp, cornerRow)
        clockBottom -> maxOf(clockH + 24.dp, cornerRow)
        indicatorAtStartEarly -> cornerRow
        else -> maxOf(cornerRow, 44.dp)
    }
    val padStartDp = if (indicatorAtStartEarly) 38.dp else 14.dp
    val padEndDp = 14.dp
    val insetLeft = with(density) { safeInsets.getLeft(this, dir).toDp() }
    val insetRight = with(density) { safeInsets.getRight(this, dir).toDp() }
    val insetTop = with(density) { safeInsets.getTop(this).toDp() }
    val insetBottom = with(density) { safeInsets.getBottom(this).toDp() }
    val contentLeftPx = with(density) { (insetLeft + padStartDp).toPx() }
    val contentTopPx = with(density) { (insetTop + padTopDp).toPx() }
    val contentRightPx = with(density) { (insetRight + padEndDp).toPx() }
    val contentBottomPx = with(density) { (insetBottom + padBottomDp).toPx() }
    val areaSize = IntSize(
        (fullSize.width - contentLeftPx - contentRightPx).toInt().coerceAtLeast(0),
        (fullSize.height - contentTopPx - contentBottomPx).toInt().coerceAtLeast(0),
    )
    val contentOffset = Offset(contentLeftPx, contentTopPx)

    // ---- drag state
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragBase by remember { mutableStateOf<List<HomePage>>(emptyList()) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var edgeDir by remember { mutableIntStateOf(0) }

    // Fractional focused index per coverflow page.
    val coverPos = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    val coverJobs = remember { mutableMapOf<Int, kotlinx.coroutines.Job>() }

    fun geomFor(page: Int, p: HomePage?) =
        PageGeometry.of(p?.layout ?: PageLayout(), areaSize, prefs, isLandscape, p?.keys?.size ?: 0, clampedCover(coverPos, page, p))

    fun focusCover(page: Int, index: Int) {
        coverJobs.remove(page)?.cancel()
        val last = (pages.getOrNull(page)?.keys?.size ?: 1) - 1
        val target = index.coerceIn(0, last.coerceAtLeast(0)).toFloat()
        coverJobs[page] = scope.launch {
            androidx.compose.animation.core.animate(
                clampedCover(coverPos, page, pages.getOrNull(page)),
                target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
            ) { v, _ -> coverPos[page] = v }
        }
    }

    fun dropTarget(): Pair<Int, Int> {
        val key = dragKey ?: return 0 to 0
        val tp = pagerState.currentPage
        val base = dragBase.getOrNull(tp)
        val remaining = base?.keys?.count { it != key } ?: 0
        val local = dragPos - contentOffset
        val idx = geomFor(tp, base?.copy(keys = base.keys.filter { it != key })).nearest(local.x, local.y).coerceAtMost(remaining)
        return tp to idx
    }

    val shown: List<HomePage> = dragKey?.let { key ->
        val (tp, idx) = dropTarget()
        HomeLayoutMath.move(dragBase, key, tp, idx, capacityOf, dropEmpty = false)
    } ?: pages
    val pageCount = if (dragKey != null) maxOf(shown.size, dragBase.size + 1) else shown.size
    androidx.compose.runtime.SideEffect { state.count.intValue = pageCount }

    // Edge dwell flips pages while dragging.
    LaunchedEffect(edgeDir, dragKey) {
        while (dragKey != null && edgeDir != 0) {
            delay(650)
            val next = (pagerState.currentPage + edgeDir).coerceIn(0, pageCount - 1)
            if (next != pagerState.currentPage) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                pagerState.animateScrollToPage(next)
            }
        }
    }

    // Keep the same apps in view across a rotation (pages re-flow with a different capacity).
    val latestPages by rememberUpdatedState(pages)
    var anchorKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { anchorKey = latestPages.getOrNull(it)?.keys?.firstOrNull() }
    }
    LaunchedEffect(isLandscape) {
        val k = anchorKey ?: return@LaunchedEffect
        val idx = HomeLayoutMath.pageOf(latestPages, k)
        if (idx >= 0 && idx != pagerState.currentPage) pagerState.scrollToPage(idx)
    }

    // Latest values for the long-lived gesture coroutine.
    val st by rememberUpdatedState(
        GestureInputs(pages, appsByKey, suggestion, gesturesBound, paging, areaSize, prefs, isLandscape, coverPos, rtl, contentOffset),
    )
    val cb by rememberUpdatedState(
        GestureCallbacks(onLaunch, onAppLongPress, onEmptyLongPress, onGesture, onPagesChange, onClockTap, onTileLongPress, capacityOf, ::focusCover),
    )

    fun hitTest(page: Int, screenPos: Offset): Hit {
        val s = st
        val pos = screenPos - s.contentOffset
        val p = s.pages.getOrNull(page) ?: return Hit.Empty
        val coverPosition = clampedCover(s.coverPos, page, p)
        val geom = PageGeometry.of(p.layout, s.areaSize, s.prefs, s.landscape, p.keys.size, coverPosition)
        val slot = geom.slotAt(pos.x, pos.y)
        if (slot < 0) return Hit.Empty
        if (geom is PageGeometry.Coverflow && slot != kotlin.math.round(coverPosition).toInt()) return Hit.Focus(page, slot)
        p.keys.getOrNull(slot)?.let { return if (HomeTiles.isTile(it)) Hit.Tile(it) else Hit.App(it) }
        val sug = s.suggestion
        if (page == 0 && sug != null && s.prefs.showSuggestion && slot == p.keys.size) return Hit.Suggested(sug)
        return Hit.Empty
    }

    val safe = WindowInsets.safeDrawing
    val indicatorAtStart = indicatorAtStartEarly

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(modifier = modifier.fillMaxSize()) {
        when (prefs.clockPosition) {
            ClockPosition.Top -> ClockBlock(
                prefs = prefs,
                weather = weather,
                onTap = onClockTap,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(safe).padding(top = 8.dp),
            )
            ClockPosition.Bottom -> ClockBlock(
                prefs = prefs,
                weather = weather,
                onTap = onClockTap,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(safe).padding(bottom = 14.dp),
            )
            ClockPosition.Hidden -> Unit
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { fullSize = it }
                .pointerInput(Unit) {
                    // Drop target from the *latest* inputs (this coroutine outlives compositions).
                    fun dropTargetNow(): Pair<Int, Int> {
                        val key = dragKey ?: return 0 to 0
                        val s = st
                        val tp = pagerState.currentPage
                        val base = dragBase.getOrNull(tp)
                        val rest = base?.keys?.filter { it != key }.orEmpty()
                        val geom = PageGeometry.of(base?.layout ?: PageLayout(), s.areaSize, s.prefs, s.landscape, rest.size, clampedCover(s.coverPos, tp, base))
                        val local = dragPos - s.contentOffset
                        return tp to geom.nearest(local.x, local.y).coerceAtMost(rest.size)
                    }
                    val slop = viewConfiguration.touchSlop
                    val swipeMin = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPage = pagerState.currentPage
                        val settled = !pagerState.isScrollInProgress
                        val hit = if (settled) hitTest(startPage, down.position) else Hit.Empty
                        var swipeDelta = Offset.Zero

                        val early: Early? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) return@withTimeoutOrNull Early.Pinch
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Early.Cancel
                                if (!c.pressed) return@withTimeoutOrNull Early.Up
                                if (c.isConsumed) return@withTimeoutOrNull Early.Cancel
                                val d = c.position - down.position
                                if (d.getDistance() > slop) {
                                    val perpendicular = if (st.paging == PagingAxis.Horizontal) abs(d.y) > abs(d.x) else abs(d.x) > abs(d.y)
                                    swipeDelta = d
                                    return@withTimeoutOrNull if (perpendicular) Early.Swipe else Early.Cancel
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") Early.Cancel
                        }

                        when (early) {
                            Early.Cancel -> Unit // the pager (or nobody) owns it

                            Early.Up -> {
                                if (!settled) return@awaitEachGesture
                                when (hit) {
                                    is Hit.App -> st.apps[hit.key]?.let(cb.onLaunch)
                                    is Hit.Suggested -> cb.onLaunch(hit.app)
                                    is Hit.Focus -> cb.focusCover(hit.page, hit.index)
                                    is Hit.Tile -> if (hit.key == HomeTiles.CLOCK) cb.onClockTap()
                                    Hit.Empty -> if (HomeGesture.DoubleTap in st.bound) {
                                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (second != null) {
                                            // Wait for the second release (a held second press is not a tap).
                                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                                var last = second
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val c = ev.changes.firstOrNull { it.id == second.id } ?: return@withTimeoutOrNull null
                                                    if (c.isConsumed) return@withTimeoutOrNull null
                                                    if (!c.pressed) return@withTimeoutOrNull last
                                                    last = c
                                                    if ((c.position - second.position).getDistance() > slop) return@withTimeoutOrNull null
                                                }
                                                @Suppress("UNREACHABLE_CODE") null
                                            }
                                            if (up != null) {
                                                if ((second.position - down.position).getDistance() < 48.dp.toPx()) {
                                                    cb.onGesture(HomeGesture.DoubleTap)
                                                } else if (!pagerState.isScrollInProgress) {
                                                    // Not a double tap: an ordinary tap somewhere else.
                                                    when (val h2 = hitTest(pagerState.currentPage, second.position)) {
                                                        is Hit.App -> st.apps[h2.key]?.let(cb.onLaunch)
                                                        is Hit.Suggested -> cb.onLaunch(h2.app)
                                                        is Hit.Focus -> cb.focusCover(h2.page, h2.index)
                                                        is Hit.Tile -> if (h2.key == HomeTiles.CLOCK) cb.onClockTap()
                                                        Hit.Empty -> Unit
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Early.Swipe -> {
                                var total = swipeDelta
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.changes.count { it.pressed } >= 2) return@awaitEachGesture
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    total = c.position - down.position
                                    if (!c.pressed) break
                                }
                                val g = when {
                                    st.paging == PagingAxis.Horizontal && abs(total.y) > swipeMin ->
                                        if (total.y < 0) HomeGesture.SwipeUp else HomeGesture.SwipeDown
                                    // Swipe directions are physical (screen) directions, also under RTL.
                                    st.paging == PagingAxis.Vertical && abs(total.x) > swipeMin ->
                                        if (total.x < 0) HomeGesture.SwipeLeft else HomeGesture.SwipeRight
                                    else -> null
                                }
                                if (g != null && g in st.bound) cb.onGesture(g)
                            }

                            Early.Pinch -> {
                                // Two fingers: take the gesture from the pager and measure the spread.
                                var startSpan = -1f
                                var span = -1f
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = ev.changes.filter { it.pressed }
                                    ev.changes.forEach { it.consume() }
                                    if (pressed.size >= 2) {
                                        span = (pressed[0].position - pressed[1].position).getDistance()
                                        if (startSpan < 0f) startSpan = span
                                    }
                                    if (pressed.isEmpty()) break
                                }
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage) }
                                if (startSpan > 0f && span > 0f) {
                                    val ratio = span / startSpan
                                    val g = when {
                                        ratio < 0.7f -> HomeGesture.PinchIn
                                        ratio > 1.4f -> HomeGesture.PinchOut
                                        else -> null
                                    }
                                    if (g != null && g in st.bound) cb.onGesture(g)
                                }
                            }

                            null -> {
                                // Long press.
                                if (!settled) return@awaitEachGesture
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val pressed: Hit = when (hit) {
                                    is Hit.Focus -> st.pages.getOrNull(hit.page)?.keys?.getOrNull(hit.index)
                                        ?.let { if (HomeTiles.isTile(it)) Hit.Tile(it) else Hit.App(it) } ?: Hit.Empty
                                    else -> hit
                                }
                                when (pressed) {
                                    is Hit.Focus -> Unit
                                    Hit.Empty -> {
                                        cb.onEmptyLongPress(startPage)
                                        do {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            ev.changes.forEach { it.consume() }
                                        } while (ev.changes.any { it.pressed })
                                    }
                                    is Hit.Suggested, is Hit.Tile -> {
                                        do {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            ev.changes.forEach { it.consume() }
                                        } while (ev.changes.any { it.pressed })
                                        when (pressed) {
                                            is Hit.Suggested -> cb.onAppLongPress(pressed.app)
                                            is Hit.Tile -> cb.onTileLongPress(pressed.key, startPage)
                                            else -> Unit
                                        }
                                    }
                                    is Hit.App -> {
                                        var dragging = false
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            ev.changes.forEach { it.consume() }
                                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!c.pressed) break
                                            if (!dragging && (c.position - down.position).getDistance() > slop) {
                                                dragging = true
                                                dragBase = st.pages
                                                dragKey = pressed.key
                                            }
                                            if (dragging) {
                                                dragPos = c.position
                                                val edge = 28.dp.toPx()
                                                val (along, extent) = if (st.paging == PagingAxis.Horizontal) {
                                                    c.position.x to size.width.toFloat()
                                                } else {
                                                    c.position.y to size.height.toFloat()
                                                }
                                                val rtlFlip = st.paging == PagingAxis.Horizontal && st.rtl
                                                val raw = when {
                                                    along < edge -> -1
                                                    along > extent - edge -> 1
                                                    else -> 0
                                                }
                                                edgeDir = if (rtlFlip) -raw else raw
                                            }
                                        }
                                        edgeDir = 0
                                        if (dragging) {
                                            val key = dragKey
                                            if (key != null) {
                                                val (tp, idx) = dropTargetNow()
                                                cb.onPagesChange(HomeLayoutMath.move(dragBase, key, tp, idx, cb.capacityOf, dropEmpty = true))
                                            }
                                            dragKey = null
                                            dragBase = emptyList()
                                        } else {
                                            st.apps[pressed.key]?.let(cb.onAppLongPress)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val pageContent: @Composable (Int) -> Unit = { index ->
                val page = shown.getOrNull(index) ?: HomePage()
                val geometry = geomFor(index, page)
                // Coverflow pages scrub sideways; what they can't consume (at either end) flows to
                // the pager through nested scroll.
                val coverModifier = if (geometry is PageGeometry.Coverflow) {
                    val cover = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
                        val step = geometry.c.stepPx
                        val cur = clampedCover(coverPos, index, page)
                        val next = (cur - delta / step).coerceIn(0f, (page.keys.size - 1).coerceAtLeast(0).toFloat())
                        coverPos[index] = next
                        -(next - cur) * step
                    }
                    LaunchedEffect(cover.isScrollInProgress) {
                        if (cover.isScrollInProgress) coverJobs.remove(index)?.cancel()
                        else focusCover(index, kotlin.math.round(clampedCover(coverPos, index, page)).toInt())
                    }
                    Modifier.scrollable(cover, androidx.compose.foundation.gestures.Orientation.Horizontal)
                } else Modifier
                PageView(
                    page = page,
                    geometry = geometry,
                    modifier = Modifier
                        .padding(
                            start = insetLeft + padStartDp, top = insetTop + padTopDp,
                            end = insetRight + padEndDp, bottom = insetBottom + padBottomDp,
                        )
                        .then(coverModifier),
                    appsByKey = appsByKey,
                    showLabels = prefs.showLabels && page.layout.shape == PageShape.Grid,
                    draggingKey = dragKey,
                    suggestion = if (index == 0 && prefs.showSuggestion && dragKey == null) suggestion else null,
                    onLaunch = onLaunch,
                    onLongPress = onAppLongPress,
                )
            }
            if (areaSize != IntSize.Zero) {
                if (paging == PagingAxis.Horizontal) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = dragKey == null,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageContent(it) }
                } else {
                    VerticalPager(
                        state = pagerState,
                        userScrollEnabled = dragKey == null,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageContent(it) }
                }
            }

            // The icon under the finger while rearranging.
            val key = dragKey
            if (key != null) {
                val tp = pagerState.currentPage
                val iconPx = geomFor(tp, dragBase.getOrNull(tp)).iconPx
                val iconDp = with(density) { iconPx.toDp() }
                val ghostModifier = Modifier
                    .offset { IntOffset((dragPos.x - iconPx / 2).roundToInt(), (dragPos.y - iconPx / 2).roundToInt()) }
                    .size(iconDp)
                    .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.95f }
                    .shadow(12.dp, RoundedCornerShape(iconDp * 0.22f))
                val ghost = appsByKey[key]
                when {
                    ghost != null -> AppIcon(ghost, iconDp, ghostModifier)
                    key == HomeTiles.CLOCK -> ClockTile(iconDp, ghostModifier)
                }
            }
        }

        PageIndicator(
            count = pageCount,
            current = pagerState.currentPage,
            vertical = indicatorAtStart,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = if (indicatorAtStart) {
                Modifier.align(Alignment.CenterStart).windowInsetsPadding(safe).padding(start = 14.dp)
            } else {
                // Lift the dots above a bottom clock.
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(safe)
                    .padding(bottom = if (prefs.clockPosition == ClockPosition.Bottom) 46.dp else 26.dp)
            },
        )
    }
    }
}

/** A coverflow page's focus position, clamped to its current item count. */
private fun clampedCover(map: Map<Int, Float>, page: Int, p: HomePage?): Float {
    val last = ((p?.keys?.size ?: 1) - 1).coerceAtLeast(0)
    // Until the user scrubs, a coverflow page rests on its middle app (neighbours on both sides).
    return (map[page] ?: (last / 2).toFloat()).coerceIn(0f, last.toFloat())
}

private data class GestureInputs(
    val pages: List<HomePage>,
    val apps: Map<String, AppEntry>,
    val suggestion: AppEntry?,
    val bound: Set<HomeGesture>,
    val paging: PagingAxis,
    val areaSize: IntSize,
    val prefs: LauncherPrefs,
    val landscape: Boolean,
    val coverPos: Map<Int, Float>,
    val rtl: Boolean,
    val contentOffset: Offset,
)

private class GestureCallbacks(
    val onLaunch: (AppEntry) -> Unit,
    val onAppLongPress: (AppEntry) -> Unit,
    val onEmptyLongPress: (Int) -> Unit,
    val onGesture: (HomeGesture) -> Unit,
    val onPagesChange: (List<HomePage>) -> Unit,
    val onClockTap: () -> Unit,
    val onTileLongPress: (String, Int) -> Unit,
    val capacityOf: (PageLayout) -> Int,
    val focusCover: (Int, Int) -> Unit,
)

@Composable
private fun PageView(
    page: HomePage,
    geometry: PageGeometry,
    appsByKey: Map<String, AppEntry>,
    showLabels: Boolean,
    draggingKey: String?,
    suggestion: AppEntry?,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        if (geometry is PageGeometry.Circle) {
            // Faint ring guides for circular pages.
            Canvas(Modifier.fillMaxSize()) {
                val c = geometry.c
                for (r in 1..c.rings) {
                    drawCircle(HyleColors.Hairline, radius = c.ringStep * r, center = Offset(c.center.x, c.center.y), style = Stroke(1.dp.toPx()))
                }
            }
        }
        page.keys.forEachIndexed { index, itemKey ->
            if (index >= geometry.capacity) return@forEachIndexed
            val app = appsByKey[itemKey]
            if (app == null && !HomeTiles.isTile(itemKey)) return@forEachIndexed
            if (geometry is PageGeometry.Coverflow && !geometry.c.visible(index)) return@forEachIndexed
            key(itemKey) {
                val itemPx = if (geometry is PageGeometry.Coverflow) geometry.c.sizeAt(index) else geometry.iconPx
                val itemDp = with(density) { itemPx.toDp() }
                val center = geometry.center(index)
                val target = IntOffset((center.x - itemPx / 2).roundToInt(), (center.y - itemPx / 2).roundToInt())
                // Coverflow follows the finger directly; grid/circle items glide to new slots.
                val animated = geometry !is PageGeometry.Coverflow
                val x = if (animated) animateFloatAsState(target.x.toFloat(), spring(dampingRatio = 0.8f, stiffness = 500f), label = "x").value else target.x.toFloat()
                val y = if (animated) animateFloatAsState(target.y.toFloat(), spring(dampingRatio = 0.8f, stiffness = 500f), label = "y").value else target.y.toFloat()
                val isDragged = itemKey == draggingKey
                val depth = if (geometry is PageGeometry.Coverflow) -kotlin.math.abs(index - geometry.c.position) else 0f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .zIndex(depth)
                        .graphicsLayer { alpha = if (isDragged) 0.18f else 1f }
                        .semantics(mergeDescendants = true) {
                            if (app != null) {
                                contentDescription = app.label
                                onClick { onLaunch(app); true }
                                customActions = listOf(CustomAccessibilityAction("Options") { onLongPress(app); true })
                            } else {
                                contentDescription = "Clock"
                            }
                        },
                ) {
                    if (app != null) {
                        AppIcon(app, itemDp, Modifier.size(itemDp))
                        if (showLabels) AppLabel(app.label, itemDp)
                    } else {
                        ClockTile(itemDp, Modifier.size(itemDp))
                    }
                }
            }
        }
        if (suggestion != null && geometry !is PageGeometry.Coverflow &&
            page.keys.size < geometry.capacity && suggestion.key !in page.keys
        ) {
            SuggestionSlot(suggestion, geometry, page.keys.size, geometry.iconPx)
        }
    }
}

/** The clock as a page tile, sized like an icon. */
@Composable
fun ClockTile(size: Dp, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val context = LocalContext.current
    val text = remember(now) {
        val is24 = DateFormat.is24HourFormat(context)
        java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(now)
    }
    val day = remember(now) { java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).format(now) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(HyleColors.Surface, RoundedCornerShape(size * 0.22f))
            .padding(4.dp),
    ) {
        Text(text, color = HyleColors.InkBright, fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(day.uppercase(), color = HyleColors.InkDim, fontSize = (size.value * 0.1f).sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun AppLabel(label: String, iconDp: Dp) {
    Text(
        label,
        color = HyleColors.Ink,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp).width(iconDp * 1.25f),
    )
}

/** The predicted app: a hairline above it and a bolt, like the reference's last slot. */
@Composable
private fun BoxScope.SuggestionSlot(app: AppEntry, geometry: PageGeometry, index: Int, iconPx: Float) {
    val density = LocalDensity.current
    val center = geometry.center(index)
    val inner = iconPx * 0.86f
    val iconDp = with(density) { iconPx.toDp() }
    val innerDp = with(density) { inner.toDp() }
    Box(
        Modifier
            .offset { IntOffset((center.x - iconPx / 2).roundToInt(), (center.y - iconPx / 2).roundToInt()) }
            .size(iconDp)
            .semantics(mergeDescendants = true) { contentDescription = "Suggested: ${app.label}" },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -(iconDp * 0.06f))
                .width(iconDp * 1.1f)
                .height(1.dp)
                .background(HyleColors.InkDim.copy(alpha = 0.6f)),
        )
        AppIcon(app, innerDp, Modifier.align(Alignment.BottomEnd).size(innerDp))
        Icon(
            Glyphs.Bolt,
            contentDescription = null,
            tint = HyleColors.InkBright,
            modifier = Modifier.align(Alignment.TopStart).padding(top = iconDp * 0.06f).size(iconDp * 0.2f),
        )
    }
}

@Composable
fun PageIndicator(
    count: Int,
    current: Int,
    vertical: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val content: @Composable () -> Unit = {
        for (i in 0 until count) {
            val active = i == current
            val long by animateDpAsState(if (active) 20.dp else 6.dp, label = "pill")
            Box(
                Modifier
                    .padding(4.dp)
                    .size(width = if (vertical) 6.dp else long, height = if (vertical) long else 6.dp)
                    .background(if (active) HyleColors.InkBright else HyleColors.Ink.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                    .semantics { contentDescription = "Page ${i + 1} of $count" }
                    .pointerInput(i) { detectTapGesturesSimple { onSelect(i) } },
            )
        }
    }
    if (vertical) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { content() }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesSimple(onTap: () -> Unit) =
    detectTapGestures(onTap = { onTap() })

/** The current time, refreshed every minute and on clock / zone changes. */
@Composable
fun rememberNow(): Date {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now = Date() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        now = Date()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

/** "12:01 PM" (or 24-hour), following the system setting. */
@Composable
fun HomeClock(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val text = remember(now) { DateFormat.getTimeFormat(context).format(now) }
    Text(
        text,
        color = HyleColors.InkBright,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
        modifier = modifier
            .semantics { contentDescription = "Time $text" }
            .pointerInput(Unit) { detectTapGesturesSimple(onTap) },
    )
}
