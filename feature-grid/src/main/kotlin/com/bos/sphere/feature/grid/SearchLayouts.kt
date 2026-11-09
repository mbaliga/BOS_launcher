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
            ChannelButton(ch) { ContactActions.intentFor(ch, c.hit)?.let(onOpen) }
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
        val s = min(wPx * 1.1f, hPx * 1.3f)
        val cx = wPx / 2f
        val cy = hPx * 0.5f
        val radii = floatArrayOf(0f, 0.24f * s, 0.46f * s, 0.68f * s)
        val caps = intArrayOf(1, 6, 12, 18)

        // Slot assignment: index -> (ring, k, count on that ring).
        val placed = tiles.take(37)
        val slots = remember(placed.size) {
            val out = ArrayList<Triple<Int, Int, Int>>()
            var left = placed.size
            for (r in 0..3) {
                val m = min(caps[r], left)
                for (k in 0 until m) out += Triple(r, k, m)
                left -= m
            }
            out
        }

        Canvas(Modifier.fillMaxSize()) {
            for (r in 1..3) drawCircle(Color.White.copy(alpha = 0.16f), radius = radii[r], center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        }

        val centreSize = 66.dp
        val tileSize = 58.dp
        val colW = 84.dp
        val colWPx = with(density) { colW.toPx() }
        placed.forEachIndexed { i, tile ->
            val (ring, k, m) = slots[i]
            val face = if (ring == 0) centreSize else tileSize
            val facePx = with(density) { face.toPx() }
            val base = -PI / 2 + k * 2 * PI / m + (if (ring == 2) 0.26 else if (ring == 3) 0.12 else 0.0)
            val dir = if (ring == 2) -1.0 else 1.0
            val tilt = if (ring == 0) 0f else (((i * 37) % 21) - 10).toFloat()
            Column(
                Modifier
                    .width(colW)
                    .offset {
                        val a = base + dir * phase.value * 2 * PI
                        val x = cx + radii[ring] * cos(a).toFloat()
                        val y = cy + radii[ring] * sin(a).toFloat()
                        IntOffset((x - colWPx / 2f).roundToInt(), (y - facePx / 2f).roundToInt())
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.graphicsLayer { rotationZ = tilt }.orbitClick(tile, actions),
                ) { TileFace(tile, face) }
                if (tile !is Tile.App) {
                    Text(
                        tile.caption(), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        if (footnote != null) {
            Text(
                footnote, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
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
) {
    if (data.blank) return
    val rows = remember(data, scope, suggestions) {
        buildList<FRow> {
            var si = 0
            suggestions.forEach { g -> add(FRow.Header(g.header)); g.items.forEach { add(FRow.Sugg(it, si++)) } }
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
                add(FRow.Header(title + "​"))
                tiles.forEach { add(FRow.Item(it)) }
                if (note != null) add(FRow.Note(note))
            }
        }
    }
    if (rows.isEmpty()) return
    LazyColumn(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE61C1D21))
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
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
            tile.c.channels.forEach { ch -> ChannelButton(ch) { ContactActions.intentFor(ch, tile.c.hit)?.let(onOpenIntent) } }
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
