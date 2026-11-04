package com.bos.sphere.feature.grid

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
) {
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            DrawerContent(apps, usage, view, sort, rail, onViewChange, onSortChange, onLaunch, onLongPress, onDismiss, sounds)
        }
    }
}

private data class ScrubState(val tag: Int, val text: String, val fraction: Float, val leftSide: Boolean)

@Composable
private fun DrawerContent(
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
    sounds: UiSounds?,
) {
    val context = LocalContext.current
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
            .background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF0E0C22), Color(0xFF071622))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        DrawerHeader(view, sort, onViewChange, onSortChange, onDismiss, sounds)
        Box(Modifier.fillMaxSize()) {
            val leftPad = if (rail == RailSide.Right) 0.dp else RAIL_WIDTH
            val rightPad = if (rail == RailSide.Left) 0.dp else RAIL_WIDTH
            val padding = PaddingValues(start = leftPad + 8.dp, end = rightPad + 8.dp, top = 4.dp, bottom = 24.dp)

            var scrollTo: (Int) -> Unit = {}
            key(effectiveSort, view) {
                when (view) {
                    DrawerView.Grid -> {
                        val st = rememberLazyGridState()
                        scrollTo = { st.requestScrollToItem(it) }
                        DrawerGrid(st, rows, padding, onLaunch, onLongPress)
                    }
                    DrawerView.Bubbles -> {
                        BubbleField(
                            apps = rows.filterIsInstance<DrawerRow.App>().map { it.app },
                            onLaunch = onLaunch, onLongPress = onLongPress,
                            modifier = Modifier.fillMaxSize(), contentPadding = padding, sounds = sounds,
                        )
                    }
                    else -> {
                        val st = rememberLazyListState()
                        scrollTo = { st.requestScrollToItem(it) }
                        DrawerList(st, rows, padding, view == DrawerView.Details, categories, usage, now, onLaunch, onLongPress)
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

            if (rail != RailSide.Right) ScrubRail(Alignment.CenterStart, true, sections, effectiveSort, onScrub, onScrubEnd)
            if (rail != RailSide.Left) ScrubRail(Alignment.CenterEnd, false, sections, effectiveSort, onScrub, onScrubEnd)

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
    onViewChange: (DrawerView) -> Unit,
    onSortChange: (DrawerSort) -> Unit,
    onClose: () -> Unit,
    sounds: UiSounds?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("SPHERE", color = HyleColors.InkBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            Text("  ·  APPS", color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
            Icon(
                Glyphs.Close, contentDescription = "Close apps", tint = HyleColors.Ink,
                modifier = Modifier.size(36.dp).clickable { sounds?.play(UiSound.Close); onClose() }.padding(8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (v in DrawerView.entries) Toggle(v.label, v == view) { sounds?.play(UiSound.Tick); onViewChange(v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (s in DrawerSort.entries) Toggle(s.label, s == sort) { sounds?.play(UiSound.Tick); onSortChange(s) }
        }
    }
}

@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) HyleColors.VioletSoft else HyleColors.InkDim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .semantics { this.selected = selected; contentDescription = text }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(), color = HyleColors.Violet.copy(alpha = 0.85f), fontSize = 11.sp,
        letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
    )
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
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(80.dp),
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
                        .combinedClickable(onClick = { onLaunch(r.app) }, onLongClick = { onLongPress(r.app) })
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                ) {
                    AppIcon(r.app, 52.dp)
                    Text(
                        r.app.label, color = HyleColors.Ink, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
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
) {
    LazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        items(rows, key = { rowKey(it) }, contentType = { if (it is DrawerRow.Header) 0 else 1 }) { r ->
            when (r) {
                is DrawerRow.Header -> SectionTitle(r.title)
                is DrawerRow.App -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
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
