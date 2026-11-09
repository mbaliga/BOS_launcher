package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.Channel
import com.bos.sphere.core.data.ContactActions
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.WebRow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a tile tap / long-press does; supplied by the overlay. */
internal class TileActions(
    val onTap: (Tile) -> Unit,
    val onLongPress: (Tile) -> Unit,
    /** The user touched the results: hide the suggestion card. */
    val onTouch: () -> Unit,
)

// ---------------------------------------------------------------- contact channel bar

/** The call / SMS / email / WhatsApp / Telegram / Signal buttons of an expanded contact tile. */
@Composable
internal fun ContactChannelBar(c: ContactRow, onOpen: (android.content.Intent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(c.hit.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp).padding(end = 8.dp))
        c.channels.forEach { ch ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            ChannelButton(ch) { ContactHabits.record(ctx, c.hit.id, ch); ContactActions.intentFor(ch, c.hit)?.let(onOpen) }
        }
        if (c.channels.isEmpty()) {
            ChannelButton(Channel.Call) { onOpen(ContactActions.openContact(c.hit)) }
        }
    }
}

// ---------------------------------------------------------------- filmstrip

private val StripTile = 76.dp
private val StripGap = 14.dp
private const val LOOP_COUNT = Int.MAX_VALUE

/**
 * A horizontal strip of large tiles. With [motion] and 5+ tiles it drifts slowly (about 12 dp/s)
 * and wraps seamlessly; it stops the instant the user touches it and resumes a moment after.
 */
@Composable
internal fun Filmstrip(
    tiles: List<Tile>,
    motion: Boolean,
    query: String,
    expanded: ContactRow?,
    onOpenIntent: (android.content.Intent) -> Unit,
    footnote: String?,
    actions: TileActions,
    modifier: Modifier = Modifier,
) {
    val n = tiles.size
    val loop = motion && n >= 5
    val state = remember(loop, n, tiles.firstOrNull()?.key) {
        LazyListState(firstVisibleItemIndex = if (loop) (LOOP_COUNT / 2) - (LOOP_COUNT / 2) % n else 0)
    }
    var touching by remember { mutableStateOf(false) }
    var lastTouchAt by remember { mutableLongStateOf(0L) }
    val speedPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(loop, n) {
        if (!loop) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        var acc = 0f
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            if (touching) { lastTouchAt = now; acc = 0f; continue }
            if (now - lastTouchAt < 1_500_000_000L || state.isScrollInProgress) continue
            acc += speedPx * dt.coerceAtMost(0.1f)
            if (acc >= 1f) { state.scrollBy(acc); acc = 0f }
        }
    }
    val centreIndex by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val c = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - c) }?.index
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (n == 0) {
            Box(Modifier.height(StripTile)) {}
        } else {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(StripGap),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val down = e.changes.any { it.pressed }
                            if (down && !touching) actions.onTouch()
                            touching = down
                        }
                    }
                },
            ) {
                if (loop) {
                    items(LOOP_COUNT, key = { it }) { i -> StripTileView(tiles[i % n], actions) }
                } else {
                    itemsIndexed(tiles, key = { _, t -> t.key }) { _, t -> StripTileView(t, actions) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            val focus = centreIndex?.let { if (n > 0) tiles.getOrNull(it % n) else null } ?: tiles.firstOrNull()
            when {
                expanded != null -> ContactChannelBar(expanded, onOpenIntent)
                focus != null -> Text(
                    tileCaption(focus, query), color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                footnote != null -> Text(footnote, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (footnote != null && n > 0 && expanded == null) {
            Text(footnote, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

private fun tileCaption(t: Tile, query: String): AnnotatedString {
    if (t is Tile.Hit && t.hit.snippet != null) {
        return highlight(t.hit.title + "  ·  " + t.hit.snippet, query, Color(0xFFB9A8FF))
    }
    return AnnotatedString(t.caption())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripTileView(tile: Tile, actions: TileActions) {
    Box(
        Modifier
            .size(StripTile)
            .combinedClickable(onClick = { actions.onTap(tile) }, onLongClick = { actions.onLongPress(tile) }),
    ) { TileFace(tile, StripTile) }
}

// ---------------------------------------------------------------- orbit

private const val ORBIT_PERIOD_MS = 150_000

/**
 * Results floating on concentric rings (like the old AirDrop screen): the best match at the
 * centre, then up to 6 / 12 / 18 on rings that rotate slowly in alternating directions. Tiles
 * keep a small fixed tilt; positions come from layout offsets so only layout (not composition)
 * runs per frame.
 */
@Composable
internal fun Orbit(
    tiles: List<Tile>,
    motion: Boolean,
    footnote: String?,
    actions: TileActions,
    /** Distance from the bottom edge to the common centre of the rings (the middle of the category wheel). */
    centreFromBottom: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val phase: State<Float> = if (motion) {
        rememberInfiniteTransition(label = "orbit").animateFloat(0f, 1f, infiniteRepeatable(tween(ORBIT_PERIOD_MS, easing = LinearEasing)), label = "ph")
    } else remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.clipToBounds().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.any { it.pressed }) actions.onTouch()
            }
        }
    }) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val cx = wPx / 2f
        val cy = hPx - with(density) { centreFromBottom.toPx() }
        val tileSize = 54.dp
        val tilePx = with(density) { tileSize.toPx() }
        val colW = 80.dp
        val colWPx = with(density) { colW.toPx() }
        val margin = colWPx / 2f
        // Ring gaps stay wider than a tile (face + caption) so rings revolving past each other never touch.
        val gap = max(0.22f * wPx, with(density) { 92.dp.toPx() })
        val radii = FloatArray(4) { 0.36f * wPx + it * gap }
        val spacing = colWPx * 0.95f

        // The rings are centred on the wheel and only their upper arcs are used. Slots run from the
        // top of each arc outward, so the best matches sit straight above the wheel.
        val slots = remember(wPx, hPx, tiles.size, motion) {
            val out = ArrayList<Pair<Int, Double>>() // ring, angle
            if (motion) {
                // Moving: whole rings, tiles spread round the full circle, so they revolve like planets
                // (the lower half passes behind the wheel and keyboard). Best matches start at the top.
                for (r in radii.indices) {
                    val rad = radii[r]
                    val n = max(1, (2 * PI * rad / spacing).toInt())
                    val order = ArrayList<Double>()
                    for (k in 0 until n) order += -PI / 2 + k * 2 * PI / n + (if (r % 2 == 1) PI / n else 0.0)
                    order.sortBy { Math.abs(Math.atan2(sin(it + PI / 2), cos(it + PI / 2))) }
                    order.forEach { out += r to it }
                }
                return@remember out
            }
            for (r in radii.indices) {
                val rad = radii[r]
                val maxCos = ((cx - margin) / rad).coerceAtMost(1f)
                // Half-angle (from straight up) that still keeps the tile inside the screen width.
                val clearPx = with(density) { 105.dp.toPx() } // keep tiles off the wheel text
                val lim = min(Math.asin(maxCos.toDouble().coerceIn(0.0, 1.0)), Math.acos((clearPx / rad).toDouble().coerceIn(0.0, 1.0)))
                val fit = (2 * lim * rad / spacing).toInt() + 1
                val n = fit.coerceAtLeast(1)
                val step = if (n > 1) 2 * lim / (n - 1) else 0.0
                val order = ArrayList<Double>()
                for (k in 0 until n) order += -PI / 2 + (if (n == 1) 0.0 else -lim + k * step)
                order.sortBy { Math.abs(it + PI / 2) }
                order.filter { cy + rad * sin(it) >= tilePx }.forEach { out += r to it }
            }
            // Adjacent rings come close near the screen edges: drop any slot whose tile (face +
            // caption) would touch one already placed, keeping a gap for the gentle sway.
            val boxW = colWPx + with(density) { 8.dp.toPx() }
            val boxH = tilePx + with(density) { 26.dp.toPx() }
            val kept = ArrayList<Pair<Int, Double>>()
            val pts = ArrayList<Offset>()
            for ((r, ang) in out) {
                val pt = Offset(cx + radii[r] * cos(ang).toFloat(), cy + radii[r] * sin(ang).toFloat())
                if (pts.none { abs(it.x - pt.x) < boxW && abs(it.y - pt.y) < boxH }) { kept += r to ang; pts += pt }
            }
            kept
        }

        // Whole revolutions per animation period at about 18 dp/s, so the loop has no jump.
        val revs = IntArray(radii.size) { r ->
            max(1, ((ORBIT_PERIOD_MS / 1000.0) * with(density) { 18.dp.toPx() } / (2 * PI * radii[r])).roundToInt())
        }
        Canvas(Modifier.fillMaxSize()) {
            for (r in radii.indices) drawCircle(Color.White.copy(alpha = 0.10f), radius = radii[r], center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        }

        tiles.take(slots.size).forEachIndexed { i, tile ->
            val (ring, baseAngle) = slots[i]
            val dir = if (ring % 2 == 1) -1.0 else 1.0
            Column(
                Modifier
                    .width(colW)
                    .offset {
                        val sway = if (motion) dir * phase.value * 2 * PI * revs[ring] else 0.0
                        val a = baseAngle + sway
                        val x = cx + radii[ring] * cos(a).toFloat()
                        val y = cy + radii[ring] * sin(a).toFloat()
                        IntOffset((x - colWPx / 2f).roundToInt(), (y - tilePx / 2f).roundToInt())
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.clip(androidx.compose.foundation.shape.CircleShape).orbitClick(tile, actions),
                ) { TileFace(tile, tileSize) }
                Text(
                    tile.caption(), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (footnote != null) {
            Text(
                footnote, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.orbitClick(tile: Tile, actions: TileActions): Modifier =
    this.combinedClickable(onClick = { actions.onTap(tile) }, onLongClick = { actions.onLongPress(tile) })

// ---------------------------------------------------------------- flat (macOS) list

private sealed interface FRow {
    val key: String
    data class Header(val title: String) : FRow { override val key get() = "h:$title" }
    data class Sugg(val item: SuggItem, val index: Int) : FRow { override val key get() = "s:$index" }
    data class Item(val tile: Tile) : FRow { override val key get() = "i:${tile.key}" }
    data class Note(val text: String) : FRow { override val key get() = "n:$text" }
}

/**
 * The plain list: refinement suggestions on top, then Applications / Actions / Contacts / Files /
 * Web sections in a dark translucent rounded card. Nothing at all for an empty query.
 */
@Composable
internal fun FlatResults(
    data: SearchData,
    scope: SearchCategory?,
    suggestions: List<SuggGroup>,
    onPickSuggestion: (SuggItem) -> Unit,
    onTileTap: (Tile) -> Unit,
    onLongPressApp: (com.bos.sphere.core.data.AppEntry) -> Unit,
    onOpenIntent: (android.content.Intent) -> Unit,
    onQueryChange: (String) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    /** Typing mode: no card, apps first without a header, suggestions last, "Web Suggestions". */
    plain: Boolean = false,
) {
    if (data.blank) return
    val rows = remember(data, scope, suggestions) {
        buildList<FRow> {
            var si = 0
            fun addSuggestions() { suggestions.forEach { g -> add(FRow.Header(g.header)); g.items.forEach { add(FRow.Sugg(it, si++)) } } }
            if (!plain) addSuggestions()
            val order = listOf(
                SearchCategory.Apps to L10n.tr("Applications"), SearchCategory.Actions to L10n.tr("Actions"),
                SearchCategory.Contacts to L10n.tr("Contacts"), SearchCategory.Files to L10n.tr("Files"),
                SearchCategory.Web to L10n.tr("Web"),
            ).let { if (data.webFirst) it.sortedBy { p -> if (p.first == SearchCategory.Web) 0 else 1 } else it }
            for ((cat, title) in order) {
                if (scope != null && scope != cat) continue
                val tiles = data.tiles(cat)
                val note = data.footnote(cat)
                if (tiles.isEmpty() && note == null) continue
                // Suggestion headers already say "Contacts"/"Web": the results get their own header below.
                if (!(plain && cat == SearchCategory.Apps)) add(FRow.Header((if (plain && cat == SearchCategory.Web) L10n.tr("Web Suggestions") else title) + "​"))
                tiles.forEach { add(FRow.Item(it)) }
                if (note != null) add(FRow.Note(note))
            }
            if (plain) addSuggestions()
        }
    }
    if (rows.isEmpty()) return
    LazyColumn(
        modifier
            .then(if (plain) Modifier else Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE61C1D21)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp)))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.any { it.pressed }) onTouch()
                    }
                }
            },
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> r.key }) { idx, row ->
            when (row) {
                is FRow.Header -> Text(
                    row.title.removeSuffix("​"), color = Color(0x99FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 3.dp),
                )
                is FRow.Note -> Text(row.text, color = Color(0x80FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                is FRow.Sugg -> {
                    Text(
                        row.item.label, color = if (row.item.accent) HyleColors.VioletSoft else Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onPickSuggestion(row.item) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                    if (rows.getOrNull(idx + 1) is FRow.Sugg) Divider()
                }
                is FRow.Item -> {
                    FlatTileRow(row.tile, data.query, onTileTap, onLongPressApp, onOpenIntent, onQueryChange)
                    if (rows.getOrNull(idx + 1) is FRow.Item) Divider()
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 16.dp, end = 16.dp).fillMaxWidth().height(0.5.dp).background(Color(0x26FFFFFF)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatTileRow(
    tile: Tile,
    query: String,
    onTap: (Tile) -> Unit,
    onLongPressApp: (com.bos.sphere.core.data.AppEntry) -> Unit,
    onOpenIntent: (android.content.Intent) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    when (tile) {
        is Tile.Person -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable { onOpenIntent(ContactActions.openContact(tile.c.hit)) },
            ) {
                ContactFace(tile.c, 34.dp)
                Text(tile.c.hit.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 6.dp))
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            tile.c.channels.forEach { ch -> ChannelButton(ch) { ContactHabits.record(ctx, tile.c.hit.id, ch); ContactActions.intentFor(ch, tile.c.hit)?.let(onOpenIntent) } }
        }
        is Tile.Bang -> Text(
            "!${tile.code}  ${tile.name}", color = HyleColors.VioletSoft, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { onQueryChange(tile.rewritten) }.padding(horizontal = 16.dp, vertical = 11.dp),
        )
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onTap(tile) },
                    onLongClick = { if (tile is Tile.App) onLongPressApp(tile.app) },
                )
                .padding(vertical = 7.dp, horizontal = 16.dp),
        ) {
            TileFace(tile, if (tile is Tile.Hit && tile.hit.snippet != null) 40.dp else 32.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (tile is Tile.Hit) highlight(tile.hit.title, query, Color(0xFFB9A8FF)) else AnnotatedString(tile.caption()),
                    color = if (tile is Tile.Allow) Color(0x99FFFFFF) else Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (tile is Tile.Hit) {
                    tile.hit.subtitle?.let { Text(it, color = Color(0x99FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    tile.hit.snippet?.let {
                        Text(highlight(it, query, Color(0xFFB9A8FF)), color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (tile is Tile.Hit) sourceBadge(tile.hit.sourcePackage)?.let {
                Text(it, color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).background(Color(0x33FFFFFF), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}
