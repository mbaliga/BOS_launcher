package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.FocusKind
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.DrawerSort
import com.bos.sphere.core.data.DrawerView
import com.bos.sphere.core.data.RailSide
import com.bos.sphere.core.data.UsageStats
import com.bos.sphere.core.design.HyleColors
import java.util.Calendar
import kotlin.math.roundToInt

/** Pure ordering / grouping / scrub maths for the app drawer. No Android types. */
internal object DrawerOrdering {
    data class Section<T>(val title: String, val items: List<T>)

    /** [item] is -1 when the rail lands on the section header itself. */
    data class RailTarget(val section: Int, val item: Int)

    const val DAY_MS = 24L * 60 * 60 * 1000

    fun letterOf(label: String): String {
        val c = label.trim().firstOrNull() ?: return "#"
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }

    fun dayBucket(last: Long, startOfToday: Long): String = when {
        last <= 0L -> "Earlier"
        last >= startOfToday -> "Today"
        last >= startOfToday - DAY_MS -> "Yesterday"
        last >= startOfToday - 6 * DAY_MS -> "This week"
        else -> "Earlier"
    }

    fun relativeTime(now: Long, last: Long): String {
        if (last <= 0L) return "Never"
        val d = (now - last).coerceAtLeast(0L)
        val min = d / 60_000
        return when {
            min < 1 -> "Just now"
            min < 60 -> "$min min ago"
            min < 60 * 24 -> "${min / 60} h ago"
            min < 60 * 24 * 30 -> "${min / (60 * 24)} d ago"
            else -> "${min / (60 * 24 * 30)} mo ago"
        }
    }

    /** Sections for [sort]. [categoryOf] may return null for "unknown" (grouped under "Other"). */
    fun <T> sections(
        items: List<T>,
        sort: DrawerSort,
        label: (T) -> String,
        key: (T) -> String,
        usage: Map<String, UsageStats.Usage>,
        categoryOf: (T) -> String?,
        startOfToday: Long,
    ): List<Section<T>> {
        if (items.isEmpty()) return emptyList()
        val alpha = Comparator<T> { a, b ->
            val c = label(a).lowercase().compareTo(label(b).lowercase())
            if (c != 0) c else key(a).compareTo(key(b))
        }
        fun last(t: T) = usage[key(t)]?.lastMillis ?: 0L
        fun count(t: T) = usage[key(t)]?.count ?: 0
        return when (sort) {
            DrawerSort.Alphabetical -> items.sortedWith(alpha).groupBy { letterOf(label(it)) }
                .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
                .map { (k, v) -> Section(k, v) }

            DrawerSort.Category -> items.sortedWith(alpha).groupBy { categoryOf(it) ?: "Other" }
                .toSortedMap(compareBy<String> { it == "Other" }.thenBy { it })
                .map { (k, v) -> Section(k, v) }

            DrawerSort.Recent -> {
                val sorted = items.sortedWith(compareByDescending<T> { last(it) }.then(alpha))
                val order = listOf("Today", "Yesterday", "This week", "Earlier")
                val g = sorted.groupBy { dayBucket(last(it), startOfToday) }
                order.mapNotNull { t -> g[t]?.let { Section(t, it) } }
            }

            DrawerSort.Frequent -> listOf(
                Section("Most used", items.sortedWith(compareByDescending<T> { count(it) }.thenByDescending { last(it) }.then(alpha))),
            )
        }
    }

    /** Index of the section header in a flat (header + items) list. */
    fun headerIndex(sizes: List<Int>, section: Int): Int {
        var i = 0
        for (s in 0 until section.coerceIn(0, sizes.size)) i += 1 + sizes[s]
        return i
    }

    /** Flat list index for a [RailTarget] (header counted as one entry). */
    fun flatIndex(sizes: List<Int>, t: RailTarget): Int = headerIndex(sizes, t.section) + 1 + t.item

    /**
     * Maps a rail fraction (0 top .. 1 bottom) to a section; with one section, to an item
     * within it instead. Null when there is nothing to scrub.
     */
    fun railTarget(fraction: Float, sizes: List<Int>): RailTarget? {
        if (sizes.isEmpty()) return null
        val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        if (sizes.size == 1) {
            val n = sizes[0]
            if (n == 0) return RailTarget(0, -1)
            return RailTarget(0, (f * n).toInt().coerceIn(0, n - 1))
        }
        return RailTarget((f * sizes.size).toInt().coerceIn(0, sizes.size - 1), -1)
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private sealed interface DrawerRow {
    data class Header(val title: String) : DrawerRow
    data class App(val app: AppEntry) : DrawerRow
}

private val RAIL_WIDTH = 28.dp

/** Focus layer of the drawer: above the home (0), below the puck (3), Radar's palette (5) and menus (8). */
internal const val DRAWER_FOCUS_LAYER = 2

/**
 * The app drawer as its own full-screen space: view (grid / list / details) and sort toggles in
 * a Hyle-style header, plus a quick-scrub rail on the chosen edge(s).
 */
@Composable
fun AppDrawerSpace(
    visible: Boolean,
    apps: List<AppEntry>,
    usage: Map<String, UsageStats.Usage>,
    view: DrawerView,
    sort: DrawerSort,
    rail: RailSide,
    onViewChange: (DrawerView) -> Unit,
    onSortChange: (DrawerSort) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    sounds: UiSounds? = null,
    modifier: Modifier = Modifier,
    railVisible: Boolean = true,
    viewSwitch: com.bos.sphere.core.data.SwitchStyle = com.bos.sphere.core.data.SwitchStyle.IconText,
) {
    BackHandler(enabled = visible) { onDismiss() }
    val drawerMotion = com.bos.sphere.core.design.rememberMotionPrefs()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(drawerMotion.crossFadeMs(160))),
        exit = fadeOut(tween(drawerMotion.crossFadeMs(120))),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // INP-01: the drawer sits above the home, so only its rows take arrows and Tab while it is open.
            FocusLayer(layer = DRAWER_FOCUS_LAYER) {
                DrawerContent(apps, usage, view, sort, rail, onViewChange, onSortChange, onLaunch, onLongPress, onDismiss, sounds, railVisible, viewSwitch)
            }
        }
    }
}

private data class ScrubState(val tag: Int, val text: String, val fraction: Float, val leftSide: Boolean)

@Composable
private fun DrawerContent(
    allApps: List<AppEntry>,
    usage: Map<String, UsageStats.Usage>,
    view: DrawerView,
    sort: DrawerSort,
    rail: RailSide,
    onViewChange: (DrawerView) -> Unit,
    onSortChange: (DrawerSort) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    sounds: UiSounds?,
    railVisible: Boolean,
    viewSwitch: com.bos.sphere.core.data.SwitchStyle,
) {
    val context = LocalContext.current
    // HOM-05 / HOM-06: tab, type-to-filter and tag filter apply before sorting into sections.
    val drawerFilter = rememberDrawerFilter()
    val apps = rememberFilteredApps(allApps, drawerFilter)
    // Categories resolve off the main thread; A–Z stands in until they are ready.
    val categories by produceState(
        initialValue = WheelCategoryCache.cachedFor(apps.map { it.packageName }),
        apps,
    ) {
        if (value == null) value = WheelCategoryCache.resolve(context, apps.map { it.packageName })
    }
    val effectiveSort = if (sort == DrawerSort.Category && categories == null) DrawerSort.Alphabetical else sort
    val sections = remember(apps, effectiveSort, usage, categories) {
        DrawerOrdering.sections(
            items = apps, sort = effectiveSort, label = { it.label }, key = { it.key }, usage = usage,
            categoryOf = { a -> categories?.get(a.packageName)?.let { wheelCategoryName(it) } },
            startOfToday = startOfToday(),
        )
    }
    val sizes = remember(sections) { sections.map { it.items.size } }
    val rows = remember(sections) {
        buildList {
            for (s in sections) { add(DrawerRow.Header(s.title)); for (a in s.items) add(DrawerRow.App(a)) }
        }
    }
    val now = remember(rows) { System.currentTimeMillis() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF50A0B10))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        DrawerHeader(view, sort, apps.size, viewSwitch, onViewChange, onSortChange, onDismiss, sounds)
        DrawerFilterBar(drawerFilter)
        Box(Modifier.fillMaxSize()) {
            val leftPad = if (rail == RailSide.Right) 0.dp else RAIL_WIDTH
            val rightPad = if (rail == RailSide.Left) 0.dp else RAIL_WIDTH
            val padding = PaddingValues(start = leftPad + 14.dp, end = rightPad + 14.dp, top = 4.dp, bottom = 28.dp)

            var scrollTo: (Int) -> Unit = {}
            key(effectiveSort, view) {
                when (view) {
                    DrawerView.Grid -> {
                        val st = rememberLazyGridState()
                        scrollTo = { st.requestScrollToItem(it) }
                        DrawerGrid(st, rows, padding, onLaunch, onLongPress, onDragStart = onDismiss)
                    }
                    else -> {
                        val st = rememberLazyListState()
                        scrollTo = { st.requestScrollToItem(it) }
                        DrawerList(st, rows, padding, view == DrawerView.Details, categories, usage, now, onLaunch, onLongPress, onDragStart = onDismiss)
                    }
                }
            }
            val scrollNow by rememberUpdatedState(scrollTo)
            val sectionsNow by rememberUpdatedState(sections)
            val sizesNow by rememberUpdatedState(sizes)

            var scrub by remember { mutableStateOf<ScrubState?>(null) }
            val haptic = LocalHapticFeedback.current
            val onScrub: (Float, Boolean) -> Unit = { f, leftSide ->
                val t = DrawerOrdering.railTarget(f, sizesNow)
                if (t != null) {
                    val secs = sectionsNow
                    val single = secs.size == 1
                    val tag = if (single) t.item else t.section
                    val text = if (single) {
                        secs[0].items.getOrNull(t.item)?.let { DrawerOrdering.letterOf(it.label) } ?: secs[0].title
                    } else secs[t.section].title
                    if (scrub?.tag != tag) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scrollNow(DrawerOrdering.flatIndex(sizesNow, t))
                    }
                    scrub = ScrubState(tag, text, f.coerceIn(0f, 1f), leftSide)
                }
            }
            val onScrubEnd: () -> Unit = { scrub = null }

            if (rail != RailSide.Right) ScrubRail(Alignment.CenterStart, true, sections, effectiveSort, railVisible, onScrub, onScrubEnd)
            if (rail != RailSide.Left) ScrubRail(Alignment.CenterEnd, false, sections, effectiveSort, railVisible, onScrub, onScrubEnd)

            scrub?.let { ScrubBubble(it) }
        }
    }
}

@Composable
private fun BoxScope.ScrubBubble(s: ScrubState) {
    BoxWithConstraints(
        Modifier
            .align(if (s.leftSide) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(horizontal = RAIL_WIDTH + 12.dp),
    ) {
        val h = constraints.maxHeight
        val bubbleH = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.roundToPx() }
        val y = (s.fraction * h - bubbleH / 2f).roundToInt().coerceIn(0, (h - bubbleH).coerceAtLeast(0))
        Box(
            Modifier
                .offset { IntOffset(0, y) }
                .size(width = 96.dp, height = 56.dp)
                .background(HyleColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                s.text, color = HyleColors.InkBright, fontSize = if (s.text.length > 2) 15.sp else 26.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.ScrubRail(
    align: Alignment,
    leftSide: Boolean,
    sections: List<DrawerOrdering.Section<AppEntry>>,
    sort: DrawerSort,
    visible: Boolean,
    onScrub: (Float, Boolean) -> Unit,
    onEnd: () -> Unit,
) {
    val onScrubNow by rememberUpdatedState(onScrub)
    val onEndNow by rememberUpdatedState(onEnd)
    val letters = sort == DrawerSort.Alphabetical && sections.size > 1
    Box(
        Modifier
            .align(align)
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .pointerInput(leftSide) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onScrubNow(down.position.y / h, leftSide)
                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (c.pressed) { onScrubNow(c.position.y / h, leftSide); c.consume() }
                    } while (ev.changes.any { it.pressed })
                    onEndNow()
                }
            },
    ) {
        if (!visible) return@Box
        if (letters) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                for (s in sections) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(s.title, color = HyleColors.InkDim.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val n = sections.size.coerceAtLeast(1)
                val step = size.height / n
                val cx = size.width / 2f
                for (i in 0 until n) {
                    val y = step * (i + 0.5f)
                    drawLine(HyleColors.InkDim.copy(alpha = 0.4f), Offset(cx - 3.dp.toPx(), y), Offset(cx + 3.dp.toPx(), y), 1.5.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    view: DrawerView,
    sort: DrawerSort,
    count: Int,
    viewSwitch: com.bos.sphere.core.data.SwitchStyle,
    onViewChange: (DrawerView) -> Unit,
    onSortChange: (DrawerSort) -> Unit,
    onClose: () -> Unit,
    sounds: UiSounds?,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Apps", color = HyleColors.InkBright, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
            Text("$count", color = HyleColors.InkDim, fontSize = 11.sp)
        }
        // View: one quiet segmented pill.
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (v in DrawerView.entries) {
                val on = v == view
                val tint = if (on) HyleColors.InkBright else HyleColors.InkDim
                val showIcon = viewSwitch != com.bos.sphere.core.data.SwitchStyle.TextOnly
                // Icons + text: only the active option spells itself out, which keeps the row narrow.
                val showText = viewSwitch == com.bos.sphere.core.data.SwitchStyle.TextOnly || (viewSwitch == com.bos.sphere.core.data.SwitchStyle.IconText && on)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .semantics { this.selected = on; contentDescription = v.label }
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color(0x26FFFFFF) else Color.Transparent)
                        .clickable { sounds?.play(UiSound.Tick); onViewChange(v) }
                        .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
                        .padding(horizontal = if (showText) 12.dp else 10.dp, vertical = 7.dp),
                ) {
                    if (showIcon) {
                        Icon(
                            when (v) { DrawerView.Grid -> Glyphs.Apps; DrawerView.List -> Glyphs.ViewList; DrawerView.Details -> Glyphs.ViewDetails },
                            contentDescription = null, tint = tint, modifier = Modifier.size(18.dp),
                        )
                    }
                    if (showIcon && showText) Spacer(Modifier.width(6.dp))
                    if (showText) Text(v.label, color = tint, fontSize = 12.sp)
                }
            }
        }
        // Sort: one chip with a menu instead of a second row of words.
        Box(Modifier.padding(start = 8.dp)) {
            Text(
                sort.label + "  ▾", color = HyleColors.InkBright, fontSize = 12.sp,
                modifier = Modifier
                    .semantics { contentDescription = "Sort by ${sort.label}" }
                    .clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF))
                    .clickable { sortOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            androidx.compose.material3.DropdownMenu(
                expanded = sortOpen, onDismissRequest = { sortOpen = false },
                containerColor = Color(0xF21F2024), shape = RoundedCornerShape(12.dp),
            ) {
                for (s in DrawerSort.entries) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(s.label, color = if (s == sort) HyleColors.VioletSoft else Color.White, fontSize = 15.sp) },
                        onClick = { sortOpen = false; sounds?.play(UiSound.Tick); onSortChange(s) },
                    )
                }
            }
        }
        Icon(
            Glyphs.Close, contentDescription = "Close apps", tint = HyleColors.Ink,
            modifier = Modifier.padding(start = 2.dp).size(48.dp).clickable { sounds?.play(UiSound.Close); onClose() }.padding(13.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.padding(start = 12.dp).weight(1f).height(1.dp).background(Color(0x14FFFFFF)))
    }
}

private fun rowKey(r: DrawerRow): String = when (r) {
    is DrawerRow.Header -> "h:${r.title}"
    is DrawerRow.App -> "a:${r.app.key}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGrid(
    state: LazyGridState,
    rows: List<DrawerRow>,
    padding: PaddingValues,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDragStart: () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        state = state,
        contentPadding = padding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            rows.size,
            key = { rowKey(rows[it]) },
            span = { if (rows[it] is DrawerRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { if (rows[it] is DrawerRow.Header) 0 else 1 },
        ) { i ->
            when (val r = rows[i]) {
                is DrawerRow.Header -> SectionTitle(r.title)
                is DrawerRow.App -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .launcherItem("drawer.${r.app.key}", FocusKind.Item, layer = DRAWER_FOCUS_LAYER, focusable = false, onActivate = { onLaunch(r.app) }, onMenu = { onLongPress(r.app) }, ringRadiusDp = 12f)
                        .appDragSource(r.app.key, onDragStart) // INP-11: a mouse drag carries the app to the home
                        .combinedClickable(onClick = { onLaunch(r.app) }, onLongClick = { onLongPress(r.app) })
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                ) {
                    AppIcon(r.app, 56.dp)
                    Text(
                        r.app.label, color = HyleColors.Ink, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerList(
    state: LazyListState,
    rows: List<DrawerRow>,
    padding: PaddingValues,
    details: Boolean,
    categories: Map<String, Int>?,
    usage: Map<String, UsageStats.Usage>,
    now: Long,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDragStart: () -> Unit = {},
) {
    LazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        items(rows, key = { rowKey(it) }, contentType = { if (it is DrawerRow.Header) 0 else 1 }) { r ->
            when (r) {
                is DrawerRow.Header -> SectionTitle(r.title)
                is DrawerRow.App -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .launcherItem("drawer.${r.app.key}", FocusKind.Item, layer = DRAWER_FOCUS_LAYER, focusable = false, onActivate = { onLaunch(r.app) }, onMenu = { onLongPress(r.app) }, ringRadiusDp = 12f)
                        .appDragSource(r.app.key, onDragStart) // INP-11: a mouse drag carries the app to the home
                        .combinedClickable(onClick = { onLaunch(r.app) }, onLongClick = { onLongPress(r.app) })
                        .padding(horizontal = 8.dp, vertical = if (details) 8.dp else 6.dp),
                ) {
                    AppIcon(r.app, if (details) 44.dp else 40.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(r.app.label, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (details) {
                            val u = usage[r.app.key]
                            val cat = categories?.get(r.app.packageName)?.let { wheelCategoryName(it) } ?: "…"
                            Text(r.app.packageName, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$cat  ·  ${u?.count ?: 0} launches  ·  ${DrawerOrdering.relativeTime(now, u?.lastMillis ?: 0L)}",
                                color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
