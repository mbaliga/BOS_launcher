package com.bos.sphere.feature.grid

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.predictiveBackLayer
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Test tags of the One UI settings. */
object OneUiTags {
    const val ROOT = "oneui.root"
    const val SUB_PAGE = "oneui.sub"
    const val BACK = "oneui.back"
    const val TITLE_LARGE = "oneui.titleLarge"
    const val TITLE_SMALL = "oneui.titleSmall"
    const val SUBTITLE = "oneui.subtitle"
    const val SEARCH = "oneui.search"
    const val LEFT_PANE = "oneui.leftPane"
    const val RIGHT_PANE = "oneui.rightPane"
    /** Wave 46: the row of preview cards of a choice that shows its options inline. */
    const val RAIL = "oneui.rail"
    /** Wave 54: a group of rows (flat, no box), the hairline between two rows, and the ring of the row that has the keyboard focus. */
    const val GROUP = "oneui.group"
    const val DIVIDER = "oneui.divider"
    const val FOCUS_RING = "oneui.focusRing"
    /** Wave 56: a row that is on the page but off right now, the reason line under its title, and the parts of a slider row (default tick, "Default" action, readout). */
    const val DISABLED_ROW = "oneui.disabledRow"
    const val DISABLED_REASON = "oneui.disabledReason"
    const val SLIDER_TICK = "oneui.sliderTick"
    const val SLIDER_DEFAULT = "oneui.sliderDefault"
    const val SLIDER_READOUT = "oneui.sliderReadout"
    /** Wave 56: "Reset this page to defaults", its undo line and the Finish setup row of the root. */
    const val RESET_ROW = "oneui.resetRow"
    const val UNDO_ROW = "oneui.undoRow"
    /** Wave 46: the band of the large title (the title is centred in it). */
    const val TITLE_BAND = "oneui.titleBand"
}

/** Where the floating preview window's default anchor sits (dp from the top of the settings): under the app bar. */
internal val LocalPreviewTop = androidx.compose.runtime.compositionLocalOf { PreviewWindowGeometry.TOP_DEFAULT }

/** What the rows of a page need from the host. */
private class ItemHost(
    val tick: () -> Unit,
    val play: (UiSound) -> Unit,
    val openSub: (Int) -> Unit,
    val confirm: (SettingItem.Action) -> Unit,
)

/** Wave 56 (CFG-30): the line of the last reset, shown at the bottom for a few seconds with its Undo. */
private const val UNDO_SHOWN_MS = 9_000L

/**
 * Wave 57 (settingsTabs), CFG-40 / CFG-41: Settings as six sections behind Liftoff's own text tabs. The home of Settings is the large centred title,
 * the search field, the Finish setup row while something is unfinished, then the tab row (pinned under the bar once the title has scrolled away)
 * and the selected section's rows (flat, hairlines, grey section headers, drawn cards). A swipe sideways moves to the next section and the tab
 * line follows it; the last section shown is remembered ([memory]). A row with a page of its own (a choice's list, a pane) opens over everything
 * and Back returns to the tab. On a wide landscape window the tabs are a vertical list in the start pane and the section fills the end pane.
 * Ctrl+Tab, Ctrl+Shift+Tab and the controller's L1 / R1 switch sections. Wave 40's root list of categories and the category pages are gone.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun OneUiSettingsHost(
    categories: List<SettingCategory>,
    initialCategory: Int,
    sounds: UiSounds?,
    onClose: () -> Unit,
    /** Wave 56 (CFG-30): the snapshots a reset takes first and Undo restores; null = resets just run (tests, no state service). */
    snapshots: SettingsSnapshots? = null,
    /** Wave 57: the row of [initialCategory] to scroll to and flash (a deep link); -1 = none. */
    initialRow: Int = -1,
    memory: SettingsTabMemory = SettingsTabMemory.Process,
) {
    val haptics = rememberLauncherHaptics()
    val latest by rememberUpdatedState(categories)
    val tabs = remember(categories.map { it.title }) { SettingsTabs.tabIndices(categories) }
    val tabsRef by rememberUpdatedState(tabs)
    val titles = tabs.map { categories[it].title }
    val start = remember { SettingsTabs.initialTab(categories, tabs, initialCategory, memory.get()) }
    val pager = rememberPagerState(initialPage = start) { tabsRef.size }
    var nav by remember { mutableStateOf(SettingsTabs.Nav(start)) }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableStateOf(if (initialRow >= 0 && initialCategory in tabs) initialCategory to initialRow else null) }
    var confirming by remember { mutableStateOf<SettingItem.Action?>(null) }
    var resetResult by remember { mutableStateOf<ResetResult?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var paneFlash by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }
    val backProgress = remember { mutableFloatStateOf(0f) }
    val customBackdrop = LocalSettingsBackdrop.current
    val reduced = rememberMotionPrefs().reduced
    LaunchedEffect(Unit) { sounds?.play(UiSound.Open) }
    // CFG-05: arriving from a Radar settings result lands on (and lights) the exact row, in its tab.
    LaunchedEffect(Unit) {
        val want = SettingsFocus.take()
        if (initialCategory in tabs && want in 0 until (latest.getOrNull(initialCategory)?.items?.size ?: 0)) highlight = initialCategory to want
    }
    LaunchedEffect(highlight) { if (highlight != null) { delay(2200); highlight = null } }
    LaunchedEffect(paneFlash) { if (paneFlash != null) { delay(1600); paneFlash = null } }
    LaunchedEffect(resetResult) { if (resetResult != null) { delay(UNDO_SHOWN_MS); resetResult = null } }
    // The Finish setup page goes away once everything is done: a page that no longer exists closes back to the tabs.
    LaunchedEffect(latest.size) { if (nav.subCategory >= latest.size) nav = SettingsTabs.Nav(nav.tab) }
    // The section shown is remembered (by its English key, so a new language or a new row does not move it).
    LaunchedEffect(pager.settledPage) {
        nav = nav.copy(tab = pager.settledPage)
        latest.getOrNull(tabsRef.getOrNull(pager.settledPage) ?: -1)?.title?.let { t -> memory.set(SettingsTabs.key(t) ?: t) }
    }

    fun goTab(i: Int) {
        if (i !in tabsRef.indices) return
        scope.launch { if (reduced) pager.scrollToPage(i) else pager.animateScrollToPage(i) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = OneUiModel.twoPane(maxWidth.value, maxHeight.value)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val topInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }

        fun closing() = confirming == null && nav.back() == null && query.isEmpty()
        fun back() {
            when {
                confirming != null -> { confirming = null; sounds?.play(UiSound.Back) }
                nav.subOpen -> { nav = nav.back() ?: nav; sounds?.play(UiSound.Back) }
                query.isNotEmpty() -> { query = ""; sounds?.play(UiSound.Back) }
                else -> { sounds?.play(UiSound.Close); onClose() }
            }
        }
        PredictiveBackHandler(enabled = true) { events ->
            try {
                events.collect { backProgress.floatValue = it.progress.coerceIn(0f, 1f) }
                val closes = closing()
                back()
                if (!closes) backProgress.floatValue = 0f
            } catch (e: CancellationException) {
                backProgress.floatValue = 0f
                throw e
            }
        }

        val host = remember(sounds, haptics) {
            ItemHost(
                tick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                play = { sounds?.play(it) },
                openSub = { _ -> },
                confirm = { a -> confirming = a },
            )
        }
        fun hostFor(category: Int) = ItemHost(host.tick, host.play, { i -> sounds?.play(UiSound.Select); nav = nav.openSub(category, i) }, host.confirm)
        fun selectTab(i: Int) { sounds?.play(UiSound.Select); goTab(i) }
        fun openResult(e: SettingEntry) {
            sounds?.play(UiSound.Select)
            query = ""
            val pos = tabsRef.indexOf(e.categoryIndex)
            if (pos >= 0) scope.launch { pager.scrollToPage(pos) } // a hit in another section switches the tab
            highlight = e.categoryIndex to e.itemIndex
            // A hit on a row inside a pane opens the pane's page and flashes that row (CFG-28); a hit on the Finish setup page opens it.
            if (e.paneEntryId != null || pos < 0) nav = nav.openSub(e.categoryIndex, e.itemIndex)
            paneFlash = e.paneEntryId
        }
        val finish = OneUiModel.finishSetupIndex(latest)
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val keyNav = Modifier.onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (ev.key == Key.Escape) { back(); return@onPreviewKeyEvent true }
            val step = SettingsTabs.keyStep(ev.nativeKeyEvent.keyCode, ev.isCtrlPressed, ev.isShiftPressed)
            if (step == null || nav.subOpen || confirming != null || query.isNotEmpty()) return@onPreviewKeyEvent false
            sounds?.play(UiSound.Select)
            goTab(SettingsTabs.step(pager.currentPage, tabsRef.size, step == SettingsTabs.KeyStep.Next))
            true
        }

        val previewFocus = LocalPreviewFocus.current // wave 57 (CFG-50): the Preview shows what this page configures
        if (previewFocus != null) androidx.compose.runtime.SideEffect { val ci = if (nav.subOpen) nav.subCategory else (tabsRef.getOrNull(pager.currentPage) ?: -1); latest.getOrNull(ci).let { c -> previewFocus.onPage(c, if (nav.subOpen) c?.items?.getOrNull(nav.subItem) else null) } }
        CompositionLocalProvider(LocalPreviewTop provides (topInset.value + 56f + 8f), LocalPaneFlash provides paneFlash) {
          Box(Modifier.fillMaxSize().then(keyNav).focusRequester(rootFocus).focusable()) {
            LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } } // a hardware keyboard's Esc and Ctrl+Tab work from the first frame
            if (customBackdrop != null) customBackdrop()
            val pageAlpha = if (customBackdrop != null) 0.94f else 1f
            val pages: @Composable (Boolean) -> Unit = { titled ->
                HorizontalPager(
                    state = pager, key = { tabsRef.getOrElse(it) { it } }, modifier = Modifier.fillMaxSize().testTag(SettingsTabTags.PAGER),
                ) { page ->
                    val ci = tabsRef.getOrNull(page) ?: return@HorizontalPager
                    val c = latest.getOrNull(ci) ?: return@HorizontalPager
                    TabContent(c, ci, highlight, hostFor(ci), titled = titled, alpha = pageAlpha)
                }
            }
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.4f).fillMaxHeight().testTag(OneUiTags.LEFT_PANE)) {
                        OneUiPage(L10n.tr("Settings"), onBack = { sounds?.play(UiSound.Close); onClose() }, backLabel = L10n.tr("Close settings"), alpha = pageAlpha, compactHeader = true, tag = OneUiTags.ROOT,
                            modifier = Modifier.predictiveBackLayerIf(!nav.subOpen, backProgress)) {
                            item(key = "search") { OneUiSearchField(query, { query = it }, L10n.tr("Search settings"), Modifier.padding(bottom = 16.dp)) }
                            if (query.isNotBlank()) searchResults(latest, query, ::openResult)
                            else {
                                if (finish >= 0) item(key = "finish") { FinishSetupRow(latest[finish], selected = nav.subCategory == finish) { sounds?.play(UiSound.Select); nav = nav.openSub(finish, 0) } }
                                item(key = "rail") { LiftoffTabRail(titles, pager.currentPage, ::selectTab) }
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(HyleColors.Hairline))
                    Box(Modifier.weight(0.6f).fillMaxHeight().testTag(OneUiTags.RIGHT_PANE)) {
                        Box(Modifier.fillMaxSize().predictiveBackLayerIf(!nav.subOpen, backProgress).covered(nav.subOpen)) { pages(true) }
                        SubLayer(nav, latest, rtl, ::hostFor, ::back, backProgress)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    TabsHome(
                        titles = titles, selected = pager.currentPage, position = position, query = query, onQuery = { query = it },
                        finish = finish.takeIf { it >= 0 }?.let { latest[it] }, onFinish = { sounds?.play(UiSound.Select); nav = nav.openSub(finish, 0) },
                        onSelect = ::selectTab, onClose = { sounds?.play(UiSound.Close); onClose() }, alpha = pageAlpha,
                        modifier = Modifier.predictiveBackLayerIf(!nav.subOpen, backProgress).covered(nav.subOpen),
                        results = { SearchResultsList(latest, query, ::openResult) },
                        pages = { pages(false) },
                    )
                    SubLayer(nav, latest, rtl, ::hostFor, ::back, backProgress)
                }
            }
            confirming?.let { a ->
                OneUiConfirmDialog(
                    title = L10n.tr(a.title), body = L10n.tr(a.confirm ?: ""), confirmLabel = L10n.tr(a.confirmLabel),
                    onConfirm = {
                        confirming = null
                        // A reset (a row that changes settings in bulk) takes a snapshot first and offers Undo afterwards (CFG-30).
                        if (a.meta.snapshotFirst) scope.launch { resetResult = SettingsResetFlow.run(snapshots, "before " + a.title.lowercase()) { a.onClick() } } else a.onClick()
                    },
                    onDismiss = { confirming = null },
                    destructive = true, bodyTag = "gc.confirm.text",
                )
            }
            resetResult?.let { r ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    OneUiUndoRow(r, onUndo = { scope.launch { resetResult = SettingsResetFlow.undo(snapshots, r) } }, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)))
                }
            }
          }
        }
    }
}

/** The page of a row (a choice's list, a pane, the Finish setup page), sliding in over the tabs. */
@Composable
private fun SubLayer(nav: SettingsTabs.Nav, categories: List<SettingCategory>, rtl: Boolean, hostFor: (Int) -> ItemHost, onBack: () -> Unit, progress: androidx.compose.runtime.State<Float>) {
    val shown = remember { intArrayOf(-1, -1) }
    if (nav.subOpen) { shown[0] = nav.subCategory; shown[1] = nav.subItem }
    SlideLayer(visible = nav.subOpen, rtl = rtl) {
        categories.getOrNull(shown[0])?.let { c -> OneUiSubPage(c, shown[1], hostFor(shown[0]), onBack = onBack, modifier = Modifier.predictiveBackLayerIf(nav.subOpen, progress)) }
    }
}

/** A page that another page lies over: gone for the screen reader and the keyboard focus, so nothing beneath the top page can be reached. */
private fun Modifier.covered(on: Boolean): Modifier =
    if (on) this.clearAndSetSemantics { }.focusProperties { canFocus = false } else this

@Composable
private fun Modifier.predictiveBackLayerIf(active: Boolean, progress: androidx.compose.runtime.State<Float>): Modifier =
    if (active) this.predictiveBackLayer(progress) else this

/** A page that slides in from the end (from the start in a right-to-left language), or just fades under reduced motion. */
@Composable
private fun SlideLayer(visible: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    val reduced = rememberMotionPrefs().reduced
    val dir = if (rtl) -1 else 1
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = if (reduced) fadeIn(tween(90)) else slideInHorizontally(tween(280)) { it * dir } + fadeIn(tween(140)),
        exit = if (reduced) fadeOut(tween(90)) else slideOutHorizontally(tween(240)) { it * dir } + fadeOut(tween(200)),
    ) { content() }
}

// ---- the page chrome ---------------------------------------------------------------------------------------------------------

private val BarHeight = 56.dp

/** The pinned bar: a back arrow (or none) at the start and the small centred title that fades in as the large one collapses. */
@Composable
private fun OneUiTopBar(title: String, collapse: androidx.compose.runtime.State<Float>, onBack: (() -> Unit)?, backLabel: String) {
    // The small title is only there once it starts to show, so a screen reader does not read the title twice while the large one is whole.
    val smallShown by remember { derivedStateOf { OneUiModel.smallTitleAlpha(collapse.value) > 0f } }
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.padding(start = 4.dp).size(48.dp).clip(CircleShape).testTag(OneUiTags.BACK)
                    .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center,
            ) { OneUiBackArrow() }
        } else Spacer(Modifier.width(52.dp))
        if (smallShown) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            OneUiTitleText(title, Modifier.testTag(OneUiTags.TITLE_SMALL).graphicsLayer { alpha = OneUiModel.smallTitleAlpha(collapse.value) }, size = 18.sp, lineHeight = 22.sp, weight = FontWeight.SemiBold, maxLines = 1, heading = false)
        } else Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(52.dp))
    }
}

/** Page frame: the bar on top and a list under it whose first item is the large title; reports the collapse fraction to the bar. */
@Composable
internal fun OneUiPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)?,
    backLabel: String,
    modifier: Modifier = Modifier,
    /** A shorter large-title band (the list of the two-pane layout, whose neighbour page keeps the tall band for the preview window). */
    compactHeader: Boolean = false,
    alpha: Float = 1f,
    state: LazyListState = rememberLazyListState(),
    tag: String? = null,
    /** Wave 57: a tag on the scrolling list itself (a section's list in the landscape end pane), so a test or a screen reader scrolls that list. */
    listTag: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val headerDp = if (compactHeader) OneUiModel.COMPACT_HEADER_DP else OneUiModel.headerDp(LocalConfiguration.current.screenHeightDp.toFloat())
    val headerPx = with(density) { headerDp.dp.toPx() }
    val barPx = with(density) { BarHeight.toPx() }
    val minPad = with(density) { 16.dp.toPx() }
    val collapse = remember(state, headerPx) { derivedStateOf { OneUiModel.collapse(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, headerPx, barPx * 0.5f) } }
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    // The empty tap handler only stops taps reaching a page beneath; the page is a container with no semantics of its own (the title is the heading).
    Column(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.background(OneUi.Page.copy(alpha = alpha)).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        OneUiTopBar(title, collapse, onBack, backLabel)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).then(if (listTag != null) Modifier.testTag(listTag) else Modifier),
            contentPadding = PaddingValues(bottom = bottom + 28.dp),
        ) {
            item(key = "title") {
                // Wave 46: the large title sits in the vertical centre of the empty band between the bar and the first row, not at its bottom.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = headerDp.dp).padding(horizontal = 28.dp).testTag(OneUiTags.TITLE_BAND)
                        .layout { m, c ->
                            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                            val band = maxOf(headerPx, p.height + 2 * minPad).roundToInt()
                            layout(c.maxWidth, band) { p.place(0, OneUiModel.centredTop(band.toFloat(), p.height.toFloat()).roundToInt()) }
                        },
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        OneUiTitleText(title, Modifier.testTag(OneUiTags.TITLE_LARGE).graphicsLayer { this.alpha = OneUiModel.largeTitleAlpha(collapse.value) })
                        if (subtitle != null) Text(
                            subtitle, color = HyleColors.InkDim, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).testTag(OneUiTags.SUBTITLE)
                                .graphicsLayer { this.alpha = OneUiModel.largeTitleAlpha(collapse.value) },
                        )
                    }
                }
            }
            content()
        }
    }
}

// ---- the home of Settings: title, search, Finish setup, the tabs and the sections ------------------------------------------------

/**
 * Wave 57: the portrait home of Settings. The title band, the search field and the Finish setup row scroll away with the first upward drag of a
 * section's list (the bar's small title fades in); the tab row then stays pinned under the bar. The list's viewport is always the visible part,
 * so a screen reader's scroll and a test's scroll-to bring a row into sight. While a query is typed the results replace the tabs.
 */
@Composable
private fun TabsHome(
    titles: List<String>,
    selected: Int,
    position: Float,
    query: String,
    onQuery: (String) -> Unit,
    finish: SettingCategory?,
    onFinish: () -> Unit,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    alpha: Float,
    modifier: Modifier,
    results: @Composable () -> Unit,
    pages: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val headerDp = OneUiModel.headerDp(LocalConfiguration.current.screenHeightDp.toFloat())
    val bandPx = with(density) { headerDp.dp.toPx() }
    val barPx = with(density) { BarHeight.toPx() }
    val offset = remember { mutableFloatStateOf(0f) }
    val headerPx = remember { floatArrayOf(0f) }
    val searching = query.isNotBlank()
    LaunchedEffect(searching) { if (searching) offset.floatValue = 0f } // the field stays in sight while typing
    val collapse = remember(bandPx) { derivedStateOf { (-offset.floatValue / (bandPx - barPx * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f) } }
    val connection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            fun take(dy: Float): Float {
                val old = offset.floatValue
                val new = (old + dy).coerceIn(-headerPx[0], 0f)
                offset.floatValue = new
                return new - old
            }
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
                if (available.y < 0f) androidx.compose.ui.geometry.Offset(0f, take(available.y)) else androidx.compose.ui.geometry.Offset.Zero
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
                if (available.y > 0f) androidx.compose.ui.geometry.Offset(0f, take(available.y)) else androidx.compose.ui.geometry.Offset.Zero
        }
    }
    Column(modifier.fillMaxSize().background(OneUi.Page.copy(alpha = alpha)).testTag(OneUiTags.ROOT)) {
        OneUiTopBar(L10n.tr("Settings"), collapse, onClose, L10n.tr("Close settings"))
        androidx.compose.ui.layout.Layout(
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().nestedScroll(connection).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            content = {
                Column(Modifier.fillMaxWidth()) {
                    TitleBand(L10n.tr("Settings"), headerDp, { OneUiModel.largeTitleAlpha(collapse.value) })
                    OneUiSearchField(query, onQuery, L10n.tr("Search settings"), Modifier.padding(bottom = 12.dp))
                    if (finish != null && !searching) FinishSetupRow(finish, selected = false, onClick = onFinish)
                }
                if (!searching) LiftoffTabRow(titles, selected, position, onSelect) else Spacer(Modifier.height(0.dp))
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = if (searching) 0f else 1f }.covered(searching)) { pages() }
                    if (searching) Box(Modifier.fillMaxSize().background(OneUi.Page.copy(alpha = alpha))) { results() }
                }
            },
        ) { m, c ->
            val loose = c.copy(minWidth = c.maxWidth, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
            val h = m[0].measure(loose)
            headerPx[0] = h.height.toFloat()
            val off = offset.floatValue.coerceAtLeast(-h.height.toFloat()).roundToInt()
            val s = m[1].measure(loose)
            val top = (h.height + off).coerceAtLeast(0)
            val bodyH = (c.maxHeight - top - s.height).coerceAtLeast(0)
            val b = m[2].measure(c.copy(minWidth = c.maxWidth, minHeight = bodyH, maxHeight = bodyH))
            layout(c.maxWidth, c.maxHeight) { h.place(0, off); b.place(0, top + s.height); s.place(0, top) }
        }
    }
}

/** The large title, centred both ways in the empty band under the bar (wave 46 / wave 52). */
@Composable
private fun TitleBand(title: String, headerDp: Float, alpha: () -> Float) {
    val density = LocalDensity.current
    val headerPx = with(density) { headerDp.dp.toPx() }
    val minPad = with(density) { 16.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = headerDp.dp).padding(horizontal = 28.dp).testTag(OneUiTags.TITLE_BAND)
            .layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                val band = maxOf(headerPx, p.height + 2 * minPad).roundToInt()
                layout(c.maxWidth, band) { p.place(0, OneUiModel.centredTop(band.toFloat(), p.height.toFloat()).roundToInt()) }
            },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OneUiTitleText(title, Modifier.testTag(OneUiTags.TITLE_LARGE).graphicsLayer { this.alpha = alpha() })
        }
    }
}

/** Wave 56 (CFG-32): one flat row above the tabs while something is unfinished; it is gone once everything is done. */
@Composable
private fun FinishSetupRow(c: SettingCategory, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        OneUiRows(1) {
            OneUiRowFrame(
                L10n.tr(OneUiModel.FINISH_SETUP), summary = (c.items.firstOrNull() as? SettingItem.Pane)?.value.orEmpty(), min = OneUi.RootRowMin, selected = selected,
                leading = { OneUiIconTile(c.glyph, Color(OneUiModel.accent(OneUiModel.FINISH_SETUP))) },
                onClick = onClick,
                modifier = Modifier.testTag(SetupTags.ROW).semantics(mergeDescendants = true) {},
                trailing = { OneUiChevron() },
            )
        }
    }
}

/** The search hits, in every section: title, then where it is (section, section header, pane) and its value. */
private fun LazyListScope.searchResults(categories: List<SettingCategory>, query: String, onResult: (SettingEntry) -> Unit) {
    val results = SettingsIndex.fromCategories(categories).search(query)
    if (results.isEmpty()) {
        item(key = "none") { Text(L10n.tr("No settings match “%s”", query), color = HyleColors.InkDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) }
    } else item(key = "results") {
        OneUiRows(results.size) {
            results.forEachIndexed { i, e ->
                if (i > 0) OneUiDivider(20.dp)
                OneUiRowFrame(
                    e.title, summary = listOf(e.category, e.section, e.parent, e.disabledReason ?: e.value).filter { it.isNotBlank() }.joinToString(" › "),
                    onClick = { onResult(e) },
                    modifier = Modifier.testTag("oneui.result").semantics(mergeDescendants = true) { contentDescription = "${e.title}, ${e.category}" + (e.disabledReason?.let { ", $it" } ?: "") },
                    trailing = { OneUiChevron() },
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(categories: List<SettingCategory>, query: String, onResult: (SettingEntry) -> Unit) {
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 28.dp)) { searchResults(categories, query, onResult) }
}

// ---- a section ---------------------------------------------------------------------------------------------------------------------

/**
 * One section's rows: the groups under their grey headers ([SettingCategory.headers]) and the page reset at the end. [titled] (the end pane of the
 * landscape layout) gives the section its own bar and large title, so the floating preview keeps its band; in portrait the shared header is above.
 */
@Composable
private fun TabContent(category: SettingCategory, index: Int, highlight: Pair<Int, Int>?, host: ItemHost, titled: Boolean, alpha: Float) {
    val items = category.items
    val blocks = OneUiModel.blocks(items, OneUiSections.headers(category))
    val state = rememberLazyListState()
    val lit = highlight?.takeIf { it.first == index }?.second
    val base = if (titled) 1 else 0
    LaunchedEffect(lit) {
        if (lit != null) {
            val b = OneUiModel.blockOf(blocks, lit)
            if (b >= 0) state.scrollToItem(base + if (b > 0 && blocks[b - 1] is OneUiModel.Block.Header) b - 1 else b)
        }
    }
    val body: LazyListScope.() -> Unit = { sectionRows(category, blocks, lit, host) }
    if (titled) {
        OneUiPage(title = category.title, onBack = null, backLabel = L10n.tr("Back"), state = state, alpha = alpha, listTag = SettingsTabTags.list(category.title), content = body)
    } else {
        val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag(SettingsTabTags.list(category.title)), contentPadding = PaddingValues(top = 8.dp, bottom = bottom + 28.dp), content = body)
    }
}

private fun LazyListScope.sectionRows(category: SettingCategory, blocks: List<OneUiModel.Block>, lit: Int?, host: ItemHost) {
    val items = category.items
    blocks.forEachIndexed { bi, b ->
        when (b) {
            is OneUiModel.Block.Header -> item(key = "h$bi") { OneUiSectionHeader(b.text) }
            is OneUiModel.Block.Group -> item(key = "c$bi") {
                Column {
                    if (bi > 0 && blocks[bi - 1] is OneUiModel.Block.Group) Spacer(Modifier.height(OneUi.GroupGap))
                    OneUiRows(b.rows.size) {
                        b.rows.forEachIndexed { r, i ->
                            if (r > 0) OneUiDivider(20.dp)
                            OneUiItem(items[i], i, lit == i, host)
                        }
                    }
                }
            }
        }
    }
    // Wave 56 (CFG-30): the last line of a section whose settings can be reset.
    SettingsResetFlow.pageAction(category)?.let { a -> item(key = "reset") { OneUiResetRow { host.play(UiSound.Select); host.confirm(a) } } }
}

@Composable
private fun OneUiItem(item: SettingItem, index: Int, lit: Boolean, host: ItemHost) = Box(Modifier.previewRow(item)) { // wave 57: a touched row is what the Preview shows
    when (item) {
        is SettingItem.Toggle -> OneUiToggleRow(item, lit) { v -> item.onChange(v); host.play(UiSound.Tick); host.tick() }
        is SettingItem.Slider -> OneUiSliderRow(item, lit) { host.tick() }
        is SettingItem.Stepper -> OneUiStepperRow(item, lit) { host.play(UiSound.Tick); host.tick() }
        is SettingItem.Choice<*> -> if (OneUiModel.presentation(item) == OneUiModel.Presentation.Rail) OneUiRailRow(item, lit) else OneUiChoiceRow(item, lit) { host.openSub(index) }
        is SettingItem.Action -> OneUiActionRow(item, lit) { host.play(UiSound.Select); if (item.confirm != null) host.confirm(item) else item.onClick() }
        is SettingItem.Pane -> OneUiPaneRow(item, lit) { host.openSub(index) }
    }
}

// ---- a row's own page --------------------------------------------------------------------------------------------------------

@Composable
private fun OneUiSubPage(category: SettingCategory, itemIndex: Int, host: ItemHost, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val row = category.items.getOrNull(itemIndex) ?: return
    // Wave 46: a choice's one-line explanation sits under the large title, like a pane's live line.
    val subtitle = (row as? SettingItem.Pane)?.pageSubtitle?.invoke() ?: (row as? SettingItem.Choice<*>)?.hint?.takeIf { it.isNotEmpty() }
    OneUiPage(title = row.title, subtitle = subtitle, onBack = onBack, backLabel = L10n.tr("Back"), modifier = modifier, tag = OneUiTags.SUB_PAGE) {
        when (row) {
            is SettingItem.Pane -> item(key = "pane") {
                if (row.bare) Column(Modifier.fillMaxWidth()) { row.content(this) }
                else Box(Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPad, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth()) { row.content(this) }
                }
            }
            is SettingItem.Choice<*> -> {
                @Suppress("UNCHECKED_CAST") val c = row as SettingItem.Choice<Any?>
                item(key = "options") {
                    OneUiRows(c.options.size) {
                        c.options.forEachIndexed { i, opt ->
                            if (i > 0) OneUiDivider(20.dp)
                            OneUiRadioRow(c.label(opt), opt == c.selected) {
                                if (opt != c.selected) { host.play(UiSound.Tick); host.tick(); c.onSelect(opt) }
                                onBack()
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
