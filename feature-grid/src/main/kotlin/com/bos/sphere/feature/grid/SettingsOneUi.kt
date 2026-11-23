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
    const val CATEGORY_PAGE = "oneui.category"
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
    fun row(category: String) = "oneui.row.$category"
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
 * Wave 40 (settingsOneUI), CFG-21: the settings as One UI lays them out. The root is one plain list of every category (grouped
 * into rounded cards, an icon tile, a title and a live grey summary each) under a large collapsing title and a search field; a
 * category opens as a full page that slides in from the end, with its rows grouped into cards under small section titles; a row that
 * has a page of its own (a choice's radio list, a pane) opens one level deeper. On a wide landscape window the list stays at the
 * start and the category page fills the end.
 */
@Composable
internal fun OneUiSettingsHost(
    categories: List<SettingCategory>,
    summaries: Map<String, String>,
    initialCategory: Int,
    sounds: UiSounds?,
    onClose: () -> Unit,
    /** Wave 56 (CFG-30): the snapshots a reset takes first and Undo restores; null = resets just run (tests, no state service). */
    snapshots: SettingsSnapshots? = null,
) {
    val haptics = rememberLauncherHaptics()
    val latest by rememberUpdatedState(categories)
    var nav by remember { mutableStateOf(if (initialCategory in categories.indices) OneUiModel.Nav(initialCategory) else OneUiModel.Nav()) }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirming by remember { mutableStateOf<SettingItem.Action?>(null) }
    var resetResult by remember { mutableStateOf<ResetResult?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var paneFlash by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }
    val backProgress = remember { mutableFloatStateOf(0f) }
    val customBackdrop = LocalSettingsBackdrop.current
    LaunchedEffect(Unit) { sounds?.play(UiSound.Open) }
    // CFG-05: arriving from a Radar settings result lands on (and lights) the exact row.
    LaunchedEffect(Unit) {
        val want = SettingsFocus.take()
        val c = nav.category
        if (c >= 0 && want in 0 until (latest.getOrNull(c)?.items?.size ?: 0)) highlight = c to want
    }
    LaunchedEffect(highlight) { if (highlight != null) { delay(2200); highlight = null } }
    LaunchedEffect(paneFlash) { if (paneFlash != null) { delay(1600); paneFlash = null } }
    LaunchedEffect(resetResult) { if (resetResult != null) { delay(UNDO_SHOWN_MS); resetResult = null } }
    // The Finish setup page goes away once everything is done: a page that no longer exists closes to the root.
    LaunchedEffect(latest.size) { if (nav.category >= latest.size) nav = OneUiModel.Nav() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = OneUiModel.twoPane(maxWidth.value, maxHeight.value)
        val cat = if (twoPane) nav.category.coerceAtLeast(0) else nav.category
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val topInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }

        fun back() {
            when {
                confirming != null -> { confirming = null; sounds?.play(UiSound.Back) }
                nav.level == 0 && query.isNotEmpty() -> { query = ""; sounds?.play(UiSound.Back) }
                else -> {
                    val n = OneUiModel.Nav(cat, nav.sub).back(twoPane)
                    if (n == null) { sounds?.play(UiSound.Close); onClose() } else { nav = n; sounds?.play(UiSound.Back) }
                }
            }
        }
        PredictiveBackHandler(enabled = true) { events ->
            try {
                events.collect { backProgress.floatValue = it.progress.coerceIn(0f, 1f) }
                val closing = confirming == null && OneUiModel.Nav(cat, nav.sub).back(twoPane) == null && !(nav.level == 0 && query.isNotEmpty())
                back()
                if (!closing) backProgress.floatValue = 0f
            } catch (e: CancellationException) {
                backProgress.floatValue = 0f
                throw e
            }
        }

        val host = remember(sounds, haptics) {
            ItemHost(
                tick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                play = { sounds?.play(it) },
                openSub = { i -> sounds?.play(UiSound.Select); nav = OneUiModel.Nav(nav.category.coerceAtLeast(0), i) },
                confirm = { a -> confirming = a },
            )
        }
        fun openCategory(i: Int) { sounds?.play(UiSound.Select); nav = OneUiModel.Nav(i); highlight = null }
        fun openResult(e: SettingEntry) {
            sounds?.play(UiSound.Select)
            // A hit on a row inside a pane opens the pane's page and flashes that row (CFG-28); any other hit opens its category and lights the row.
            nav = if (e.paneEntryId != null) OneUiModel.Nav(e.categoryIndex, e.itemIndex) else OneUiModel.Nav(e.categoryIndex)
            highlight = e.categoryIndex to e.itemIndex
            paneFlash = e.paneEntryId
        }

        CompositionLocalProvider(LocalPreviewTop provides (topInset.value + 56f + 8f), LocalPaneFlash provides paneFlash) {
          Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) { back(); true } else false }
                .focusRequester(rootFocus)
                .focusable(),
          ) {
            LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } } // a hardware keyboard's Esc works from the first frame
            if (customBackdrop != null) customBackdrop()
            val pageAlpha = if (customBackdrop != null) 0.94f else 1f
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.4f).fillMaxHeight().testTag(OneUiTags.LEFT_PANE)) {
                        OneUiRootPage(latest, summaries, cat, query, { query = it }, ::openCategory, ::openResult, onClose = { sounds?.play(UiSound.Close); onClose() }, alpha = pageAlpha, compact = true, modifier = Modifier.predictiveBackLayerIf(nav.level == 0, backProgress))
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(HyleColors.Hairline))
                    Box(Modifier.weight(0.6f).fillMaxHeight().testTag(OneUiTags.RIGHT_PANE)) {
                        val c = latest.getOrNull(cat)
                        if (c != null) androidx.compose.runtime.key(cat) {
                            OneUiCategoryPage(c, cat, highlight, host, onBack = null, modifier = Modifier.predictiveBackLayerIf(nav.sub < 0, backProgress).covered(nav.sub >= 0))
                            SlideLayer(visible = nav.sub >= 0, rtl = rtl) { OneUiSubPage(c, nav.sub, host, onBack = ::back, modifier = Modifier.predictiveBackLayerIf(true, backProgress)) }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    OneUiRootPage(latest, summaries, -1, query, { query = it }, ::openCategory, ::openResult, onClose = { sounds?.play(UiSound.Close); onClose() }, alpha = pageAlpha, modifier = Modifier.predictiveBackLayerIf(nav.level == 0, backProgress).covered(cat >= 0))
                    val shownCat = remember { intArrayOf(0) }
                    if (cat >= 0) shownCat[0] = cat
                    val shownSub = remember { intArrayOf(0) }
                    if (nav.sub >= 0) shownSub[0] = nav.sub
                    SlideLayer(visible = cat >= 0, rtl = rtl) {
                        latest.getOrNull(shownCat[0])?.let { c ->
                            OneUiCategoryPage(c, shownCat[0], highlight, host, onBack = ::back, modifier = Modifier.predictiveBackLayerIf(nav.level == 1, backProgress).covered(nav.sub >= 0))
                        }
                    }
                    SlideLayer(visible = cat >= 0 && nav.sub >= 0, rtl = rtl) {
                        latest.getOrNull(shownCat[0])?.let { c -> OneUiSubPage(c, shownSub[0], host, onBack = ::back, modifier = Modifier.predictiveBackLayerIf(nav.level == 2, backProgress)) }
                    }
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
            modifier = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
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

// ---- root --------------------------------------------------------------------------------------------------------------------

@Composable
private fun OneUiRootPage(
    categories: List<SettingCategory>,
    summaries: Map<String, String>,
    selected: Int,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onResult: (SettingEntry) -> Unit,
    onClose: () -> Unit,
    alpha: Float,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val groups = remember(categories.map { it.title }) { OneUiModel.groups(categories) }
    val finish = OneUiModel.finishSetupIndex(categories)
    val searching = query.isNotBlank()
    val results = if (searching) SettingsIndex.fromCategories(categories).search(query) else emptyList()
    OneUiPage(
        title = L10n.tr("Settings"), onBack = onClose, backLabel = L10n.tr("Close settings"), modifier = modifier, alpha = alpha, compactHeader = compact, tag = OneUiTags.ROOT,
    ) {
        item(key = "search") { OneUiSearchField(query, onQuery, L10n.tr("Search settings"), Modifier.padding(bottom = 16.dp)) }
        if (searching) {
            if (results.isEmpty()) {
                item(key = "none") { Text(L10n.tr("No settings match “%s”", query), color = HyleColors.InkDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) }
            } else item(key = "results") {
                OneUiRows(results.size) {
                    results.forEachIndexed { i, e ->
                        if (i > 0) OneUiDivider(20.dp)
                        OneUiRowFrame(
                            e.title, summary = listOf(e.category, e.parent, e.disabledReason ?: e.value).filter { it.isNotBlank() }.joinToString(" › "),
                            onClick = { onResult(e) },
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${e.title}, ${e.category}" + (e.disabledReason?.let { ", $it" } ?: "") },
                            trailing = { OneUiChevron() },
                        )
                    }
                }
            }
        } else {
            if (finish >= 0) item(key = "finish") {
                // Wave 56 (CFG-32): one flat row above the groups while something is unfinished; it is gone once everything is done.
                val c = categories[finish]
                Column(Modifier.padding(bottom = OneUi.GroupGap)) {
                    OneUiRows(1) {
                        OneUiRowFrame(
                            L10n.tr(OneUiModel.FINISH_SETUP), summary = (c.items.firstOrNull() as? SettingItem.Pane)?.value.orEmpty(), min = OneUi.RootRowMin, selected = finish == selected,
                            leading = { OneUiIconTile(c.glyph, Color(OneUiModel.accent(OneUiModel.FINISH_SETUP))) },
                            onClick = { onOpen(finish) },
                            modifier = Modifier.testTag(SetupTags.ROW).semantics(mergeDescendants = true) {},
                            trailing = { OneUiChevron() },
                        )
                    }
                }
            }
            groups.forEachIndexed { gi, idx ->
                item(key = "group$gi") {
                    Column {
                        if (gi > 0) Spacer(Modifier.height(OneUi.GroupGap))
                        OneUiRows(idx.size) {
                            idx.forEachIndexed { r, ci ->
                                val c = categories[ci]
                                if (r > 0) OneUiDivider(72.dp)
                                OneUiRowFrame(
                                    OneUiModel.displayTitle(c.title), summary = OneUiModel.summary(c, summaries), min = OneUi.RootRowMin, selected = ci == selected,
                                    leading = { OneUiIconTile(c.glyph, Color(OneUiModel.accent(c.title))) },
                                    onClick = { onOpen(ci) },
                                    modifier = Modifier.testTag(OneUiTags.row(OneUiModel.displayTitle(c.title))).semantics(mergeDescendants = true) {},
                                    trailing = { OneUiChevron() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- a category --------------------------------------------------------------------------------------------------------------

@Composable
private fun OneUiCategoryPage(
    category: SettingCategory,
    index: Int,
    highlight: Pair<Int, Int>?,
    host: ItemHost,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val items = category.items
    val blocks = OneUiModel.blocks(items, OneUiSections.headers(category.title, items))
    val state = rememberLazyListState()
    val lit = highlight?.takeIf { it.first == index }?.second
    LaunchedEffect(lit) {
        if (lit != null) {
            val b = OneUiModel.blockOf(blocks, lit)
            if (b >= 0) state.scrollToItem(b + 1)
        }
    }
    val only = items.singleOrNull() as? SettingItem.Pane
    // Wave 52: a category that is one pane carries the pane's live line under its title (the active workspace), like the page of a row does.
    val onlySubtitle = only?.pageSubtitle?.invoke()
    OneUiPage(title = OneUiModel.displayTitle(category.title), subtitle = onlySubtitle, onBack = onBack, backLabel = L10n.tr("Back"), modifier = modifier, state = state, tag = OneUiTags.CATEGORY_PAGE) {
        if (only != null) { // a category that is one pane (Hidden apps, Workspaces) is that pane: no second level to tap through
            item(key = "pane") {
                if (only.bare) Column(Modifier.fillMaxWidth()) { only.content(this) }
                else Box(Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPad, vertical = 8.dp)) { Column(Modifier.fillMaxWidth()) { only.content(this) } }
            }
            return@OneUiPage
        }
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
        // Wave 56 (CFG-30): the last line of a page whose settings can be reset.
        SettingsResetFlow.pageAction(category)?.let { a -> item(key = "reset") { OneUiResetRow { host.play(UiSound.Select); host.confirm(a) } } }
    }
}

@Composable
private fun OneUiItem(item: SettingItem, index: Int, lit: Boolean, host: ItemHost) {
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
