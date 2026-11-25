package com.bos.sphere.feature.pages.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleMotion
import com.bos.sphere.feature.pages.DragFeed
import com.bos.sphere.feature.pages.EdgeSwipe
import com.bos.sphere.feature.pages.HomeSwipeUp
import com.bos.sphere.feature.pages.MotionPolicy
import com.bos.sphere.feature.pages.NavMode
import com.bos.sphere.feature.pages.OpenTarget
import com.bos.sphere.feature.pages.Page
import com.bos.sphere.feature.pages.PageAxis
import com.bos.sphere.feature.pages.PageEdits
import com.bos.sphere.feature.pages.PageKind
import com.bos.sphere.feature.pages.PageList
import com.bos.sphere.feature.pages.PageStore
import com.bos.sphere.feature.pages.PageTurn
import com.bos.sphere.feature.pages.PastEnd
import com.bos.sphere.feature.pages.Pt
import com.bos.sphere.feature.pages.R
import com.bos.sphere.feature.pages.TwoFingerSwipe
import com.bos.sphere.feature.pages.Verdict
import com.bos.sphere.feature.pages.runtime.PagesRuntime
import com.bos.sphere.feature.pages.runtime.wipeOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

/**
 * Glue the home screen gives the pages for App Widget pages, which are hosted by `:feature-widgets`
 * (this module doesn't depend on it). Slots tie a page to its widget placement.
 */
interface AppWidgetPageHooks {
    /** Slots of widgets placed on the Page surface (state-backed, so composition follows it). */
    val placedSlots: Set<Int>

    fun label(slot: Int): String?

    /** Opens the widget picker for a new page whose widget goes into [slot]. */
    fun add(slot: Int)

    /** Removes the widget in [slot] and gives its id back to the system. */
    fun remove(slot: Int)

    fun canReconfigure(slot: Int): Boolean

    fun reconfigure(slot: Int)
}

/** Dialogs the pages module can show. */
internal sealed interface PagesDialog {
    data object Manager : PagesDialog
    data object Add : PagesDialog
    data object Url : PagesDialog
    data class Rename(val id: String) : PagesDialog
    data class Remove(val id: String) : PagesDialog
    data class Info(val id: String) : PagesDialog
    data class ImportFailed(val failure: com.bos.sphere.feature.pages.ImportFailure) : PagesDialog
    data class ImportNotes(val warnings: List<com.bos.sphere.feature.pages.ManifestIssue>) : PagesDialog
}

/**
 * The launcher's pages: page 0 is the coverflow home; every other page is an App Widget, an HTML
 * widget or page run by the widget runtime, or a website. Holds the page list and navigation mode
 * ([store]), the pager [position] (in pages), the dialogs, and the live WebViews' bookkeeping
 * (state saved for destroyed views, back actions, pause/resume). Main thread.
 */
@Stable
class PagesUi internal constructor(
    val store: PageStore,
    val runtime: PagesRuntime,
    private val scope: CoroutineScope,
    private val appContext: Context,
) {
    /** Continuous pager position in pages (0 = home). */
    val position = Animatable(0f)

    /** Android's "Remove animations": page turns snap instead of sliding. */
    var reduceMotion by mutableStateOf(false)
        internal set

    internal var dialog by mutableStateOf<PagesDialog?>(null)
    internal var importing by mutableStateOf(false)

    /** Set while a page gesture owns the current touch; the coverflow yields the rest of it. */
    private var claimed = false

    /** For `LauncherSurface(gestureClaimed = …)`. */
    fun gestureClaimed(): Boolean = claimed

    internal val webStates = HashMap<String, Bundle>()
    internal val backActions = HashMap<String, () -> Boolean>()
    internal val liveViews = LinkedHashMap<WebView, String>()
    private var pendingWidgetSlot: Int? = null
    private var currentId: String = Page.HOME_ID

    /** Turns started by [turnTo] and not yet finished; [followCurrent] leaves the position alone meanwhile. */
    private var turns = 0

    val pages: State<PageList> @Composable get() = store.pages.collectAsState()
    val mode: State<NavMode> @Composable get() = store.mode.collectAsState()

    fun current(): Int = PageTurn.current(position.value, store.pages.value.size)

    fun currentPage(): Page = store.pages.value.pages.getOrElse(current()) { Page.Home }

    /**
     * Turns to [index] (one page slides; a longer jump snaps next to it first, so pages between aren't
     * built). Also settles a page drag. The target page is remembered by id, and while the turn runs
     * [followCurrent] (which reacts to list changes, e.g. the page just added that this turn goes to)
     * leaves the position alone; when the turn ends the user is on that page even if the list moved it.
     */
    fun turnTo(index: Int) {
        val list = store.pages.value
        val target = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        val targetId = list.pages.getOrNull(target)?.id ?: Page.HOME_ID
        turns++
        scope.launch {
            try {
                val from = position.value
                if (reduceMotion) {
                    position.snapTo(target.toFloat())
                } else {
                    if (abs(target - from) > 1.5f) position.snapTo(target - sign(target - from))
                    position.animateTo(target.toFloat(), HyleMotion.settle())
                }
            } finally {
                turns--
            }
            // Not reached when a newer turn or drag cancelled this one: that one decides.
            currentId = targetId
            followCurrent(store.pages.value)
        }
    }

    fun turnToId(id: String) {
        val index = store.pages.value.indexOf(id)
        if (index >= 0) turnTo(index)
    }

    /** The home key: back to page 0. */
    fun goHome() {
        dialog = null
        if (current() != 0 || !PageTurn.atRest(position.value)) turnTo(0)
    }

    fun openManager() {
        dialog = PagesDialog.Manager
    }

    fun dismissAll() {
        dialog = null
    }

    /** A widget was just placed for a page: turn to that page once it exists. */
    fun showWidgetPage(slot: Int) {
        pendingWidgetSlot = slot
        resolvePendingWidget()
    }

    internal fun resolvePendingWidget() {
        val slot = pendingWidgetSlot ?: return
        val index = store.pages.value.pages.indexOfFirst { it.kind == PageKind.AppWidget && it.widgetSlot == slot }
        if (index >= 0) {
            pendingWidgetSlot = null
            turnTo(index)
        }
    }

    /** Mode 2 on home: the coverflow was released [pastEndColumns] past its ends. */
    fun onScrubReleased(pastEndColumns: Float, homeAtRest: Boolean) {
        if (store.mode.value != NavMode.PastEnds || !homeAtRest || current() != 0) return
        val dir = PastEnd.direction(pastEndColumns)
        PageTurn.neighbour(0, dir, store.pages.value.size)?.let { turnTo(it) }
    }

    /** `orrery.open` from a widget: an app by package, or a page by id. */
    internal fun open(target: OpenTarget) {
        when (target) {
            is OpenTarget.Page -> if (target.pageId == Page.HOME_ID) turnTo(0) else turnToId(target.pageId)
            is OpenTarget.App -> runCatching {
                val la = appContext.getSystemService(LauncherApps::class.java)
                val user = Process.myUserHandle()
                la.getActivityList(target.packageName, user).firstOrNull()?.let {
                    la.startMainActivity(it.componentName, user, null, null)
                }
            }
        }
    }

    /**
     * Removes a page with everything it owns: imported files, and what it stored at its origin
     * (`localStorage`, IndexedDB, Cache Storage, cookies; see [wipeOrigin]).
     */
    internal fun removePage(page: Page, hooks: AppWidgetPageHooks?) {
        when (page.kind) {
            PageKind.AppWidget -> hooks?.remove(page.widgetSlot)
            PageKind.Html -> {
                val id = page.id
                runtime.forgetNet(id)
                scope.launch {
                    // The page's own view is released when the list drops it (the next composition):
                    // wait for that, so it can't write to its storage again after the wipe.
                    repeat(2) { withFrameNanos { } }
                    wipeOrigin(appContext, id)
                    withContext(Dispatchers.IO) { runtime.files.delete(id) }
                }
            }
            PageKind.Web, PageKind.Home -> Unit
        }
        webStates.remove(page.id)
        store.update { PageEdits.remove(it, page.id) }
    }

    /** Keeps the user on the same page (by id) when pages are added, removed or reordered. */
    internal suspend fun followCurrent(list: PageList) {
        if (turns > 0 || position.isRunning || !PageTurn.atRest(position.value)) return
        val index = list.indexOf(currentId)
        val target = if (index >= 0) index else current().coerceAtMost(list.size - 1)
        if (target.toFloat() != position.value) position.snapTo(target.toFloat())
        currentId = list.pages.getOrNull(target)?.id ?: Page.HOME_ID
    }

    internal fun setClaimed(value: Boolean) {
        claimed = value
    }

    internal fun readReduceMotion() {
        reduceMotion = MotionPolicy.reduceMotion(
            runCatching { Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
                .getOrDefault(1f),
        )
    }

    internal fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }
}

@Composable
fun rememberPagesUi(): PagesUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = remember { PagesUi(PageStore(context), PagesRuntime.get(context), scope, context.applicationContext) }
    LaunchedEffect(ui) {
        ui.readReduceMotion()
        // Files of pages no longer listed (an interrupted removal or import) are removed, unless the
        // stored list couldn't be read fully: then nothing is (PageEdits.filesToKeep).
        val keep = PageEdits.filesToKeep(ui.store.pages.value, ui.store.listReadFully)
        withContext(Dispatchers.IO) { ui.runtime.files.sweep(keep) }
    }
    return ui
}

/**
 * Keeps App Widget pages in step with the widget placements on the Page surface (a widget just bound
 * gets its page; see [PageEdits.syncWidgetPages]).
 */
@Composable
fun PagesUi.SyncWidgetPages(placedSlots: Set<Int>) {
    LaunchedEffect(placedSlots) {
        store.update { PageEdits.syncWidgetPages(it, placedSlots, store::newId) }
        resolvePendingWidget()
    }
}

/**
 * The pager: page 0 is [homeContent] (the coverflow and its chrome), the others are full pages.
 * Pages slide along the mode's axis; the current page and its neighbours stay composed (so at most
 * three pages' WebViews are alive), plus home, which is always kept for a fast home-key return.
 *
 * Page gestures are read in the Initial pass, before any content (the coverflow, a WebView, a
 * hosted App Widget) sees them, and only *claimed* once they are clearly a page gesture; until then
 * the content receives the touch untouched, and a claimed gesture sends the content ACTION_CANCEL.
 * One-finger input on full pages always reaches the content (except mode 2's edge strips).
 */
@Composable
fun PagesUi.PagesHost(
    homeAtRest: () -> Boolean,
    homeRegion: () -> LayoutCoordinates?,
    appWidgets: AppWidgetPageHooks?,
    modifier: Modifier = Modifier,
    homeLongPressOpened: () -> Boolean = { false },
    appWidgetPage: @Composable (page: Page, interactive: Boolean) -> Unit,
    homeContent: @Composable () -> Unit,
) {
    val list by pages
    val navMode by mode
    val axis = if (navMode == NavMode.Vertical) PageAxis.Vertical else PageAxis.Horizontal
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val gestureInsets = WindowInsets.systemGestures
    val insetLeft = gestureInsets.getLeft(density, layoutDirection).toFloat()
    val insetRight = gestureInsets.getRight(density, layoutDirection).toFloat()
    val insetBottom = gestureInsets.getBottom(density).toFloat()
    val insets by rememberUpdatedState(Triple(insetLeft, insetRight, insetBottom))
    val atRest by rememberUpdatedState(homeAtRest)
    val region by rememberUpdatedState(homeRegion)
    val longPressOpened by rememberUpdatedState(homeLongPressOpened)
    val hostCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    LaunchedEffect(list) {
        followCurrent(list)
        resolvePendingWidget()
    }

    // TalkBack: name the page once a turn has settled (two-finger drags reach TalkBack users as
    // one-finger ones, so they turn pages with the dots, back or the home key, and hear where they are).
    val view = LocalView.current
    val settledIndex by remember {
        derivedStateOf { if (PageTurn.atRest(position.value)) PageTurn.current(position.value, list.size) else -1 }
    }
    val announcement = list.pages.getOrNull(settledIndex)?.let {
        stringResource(R.string.pages_dot_current_cd, settledIndex + 1, list.size, titleOf(it, appWidgets))
    }
    val lastAnnounced = remember { intArrayOf(-1) }
    LaunchedEffect(settledIndex) {
        if (settledIndex < 0) return@LaunchedEffect
        if (lastAnnounced[0] >= 0 && lastAnnounced[0] != settledIndex && announcement != null) {
            view.announceForAccessibility(announcement)
        }
        lastAnnounced[0] = settledIndex
    }

    // Back: a page's own history first (websites), then home.
    val currentIndex by remember { derivedStateOf { PageTurn.current(position.value, store.pages.value.size) } }
    BackHandler(enabled = currentIndex != 0) {
        val id = store.pages.value.pages.getOrNull(currentIndex)?.id
        if (id != null && backActions[id]?.invoke() == true) return@BackHandler
        turnTo(0)
    }

    // WebViews stop their timers while the launcher is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    readReduceMotion()
                    liveViews.keys.firstOrNull()?.resumeTimers()
                    val currentId = currentPage().id
                    liveViews.forEach { (view, id) -> if (id == currentId) view.onResume() }
                }
                Lifecycle.Event.ON_STOP -> {
                    liveViews.keys.forEach { it.onPause() }
                    liveViews.keys.firstOrNull()?.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostCoords[0] = it }
            .pointerInput(navMode, list.size) {
                val slop = viewConfiguration.touchSlop
                val stripPx = EdgeSwipe.STRIP_DP.dp.toPx()
                val flingPx = FLING_DP_PER_S.dp.toPx()
                awaitEachGesture {
                    setClaimed(false)
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val count = store.pages.value.size
                    if (count < 2 || !PageTurn.atRest(position.value)) return@awaitEachGesture
                    val start = current()
                    val onHome = start == 0
                    val p0 = Pt(down.position.x, down.position.y)
                    val inHome = onHome && atRest() && insideRegion(region(), hostCoords[0], down.position)
                    val (iL, iR, iB) = insets
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val extent = if (axis == PageAxis.Horizontal) width else height

                    val two = when (navMode) {
                        NavMode.TwoFingerDots, NavMode.Vertical ->
                            if (!onHome || inHome) TwoFingerSwipe(axis, slop) else null
                        NavMode.PastEnds -> null
                    }
                    val edge = if (navMode == NavMode.PastEnds && !onHome) {
                        EdgeSwipe(EdgeSwipe.sideAt(p0.x, width, iL, iR, stripPx), p0, slop)
                            .takeIf { it.verdict == Verdict.Pending }
                    } else {
                        null
                    }
                    val up = if (navMode == NavMode.Vertical && inHome &&
                        HomeSwipeUp.eligible(p0, height, iB, hubFraction = 0f, zoom = 0f)
                    ) HomeSwipeUp(p0, slop) else null
                    if (two == null && edge == null && up == null) return@awaitEachGesture

                    var direction = 0
                    // The claimed classifier's drag while all its fingers are down (by pointer id).
                    var liveDrag: (List<Pair<Long, Pt>>) -> Float? = { null }
                    val feed = DragFeed()
                    val velocity = VelocityTracker()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                            .map { it.id.value to Pt(it.position.x, it.position.y) }
                        if (direction == 0) {
                            // On home, a long-press that opened something (an app's menu, the widget
                            // picker) owns the rest of the gesture: never turn the page behind it.
                            if (onHome && longPressOpened()) return@awaitEachGesture
                            val verdicts = listOfNotNull(two?.update(pressed), edge?.update(pressed), up?.update(pressed))
                            val claim = verdicts.filterIsInstance<Verdict.Claimed>().firstOrNull()
                            if (claim != null) {
                                direction = claim.direction
                                liveDrag = when {
                                    two?.verdict is Verdict.Claimed -> { ps -> two.liveDrag(ps) }
                                    edge?.verdict is Verdict.Claimed -> { ps -> edge.liveDrag(ps) }
                                    else -> { ps -> up?.liveDrag(ps) }
                                }
                                setClaimed(true)
                            } else if (verdicts.all { it == Verdict.Declined }) {
                                return@awaitEachGesture
                            }
                        }
                        if (direction != 0) {
                            event.changes.forEach { it.consume() }
                            // Only events with every claimed finger down move the page; the lift of
                            // the first finger (POINTER_UP, before the last UP) records nothing, so the
                            // page doesn't snap back and the release keeps the swipe's velocity.
                            val time = event.changes.first().uptimeMillis
                            feed.feed(time, liveDrag(pressed))?.let { d ->
                                velocity.addPosition(time, if (axis == PageAxis.Horizontal) Offset(d, 0f) else Offset(0f, d))
                                val pos = PageTurn.dragPosition(start, d, extent, count)
                                launch { position.snapTo(pos) }
                            }
                        }
                        if (pressed.isEmpty()) break
                    }
                    if (direction != 0) {
                        val v = velocity.calculateVelocity().let { if (axis == PageAxis.Horizontal) it.x else it.y }
                        turnTo(PageTurn.settleTarget(start, position.value, v, flingPx, count))
                    }
                }
            },
    ) {
        val extentPx = with(density) { (if (axis == PageAxis.Horizontal) maxWidth else maxHeight).toPx() }
        val window by remember(list.size) { derivedStateOf { PageTurn.liveWindow(position.value, list.size) } }
        val current by remember(list.size) { derivedStateOf { PageTurn.current(position.value, list.size) } }
        val resting by remember { derivedStateOf { PageTurn.atRest(position.value) } }
        list.pages.forEachIndexed { index, page ->
            if (index == 0 || index in window) {
                key(page.id) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = index - position.value
                                if (axis == PageAxis.Horizontal) translationX = offset * extentPx else translationY = offset * extentPx
                                alpha = if (abs(offset) >= 1f) 0f else 1f
                            },
                    ) {
                        if (index == 0) {
                            homeContent()
                        } else {
                            FullPage(
                                page = page,
                                isCurrent = index == current,
                                interactive = index == current && resting,
                                appWidgets = appWidgets,
                                appWidgetPage = appWidgetPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fling speed (dp/s) that turns the page whatever the distance. */
private const val FLING_DP_PER_S = 650f

private fun insideRegion(region: LayoutCoordinates?, host: LayoutCoordinates?, position: Offset): Boolean {
    if (region == null || host == null || !region.isAttached || !host.isAttached) return false
    val p = runCatching { region.localPositionOf(host, position) }.getOrNull() ?: return false
    return p.x >= 0f && p.y >= 0f && p.x <= region.size.width && p.y <= region.size.height
}

/** Room kept clear for the page dots (48 dp targets plus a margin) on full pages. */
private val DotsReserve = 56.dp

@Composable
private fun PagesUi.FullPage(
    page: Page,
    isCurrent: Boolean,
    interactive: Boolean,
    appWidgets: AppWidgetPageHooks?,
    appWidgetPage: @Composable (page: Page, interactive: Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HyleColors.Background)
            .padding(bottom = DotsReserve),
    ) {
        when (page.kind) {
            PageKind.AppWidget -> if (appWidgets != null && page.widgetSlot in appWidgets.placedSlots) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) { appWidgetPage(page, interactive) }
            } else {
                WidgetGone(page, appWidgets)
            }
            PageKind.Html -> HtmlPage(page, isCurrent)
            PageKind.Web -> WebPage(page, isCurrent)
            PageKind.Home -> Unit
        }
    }
}
