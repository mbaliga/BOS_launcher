package com.bos.sphere.feature.grid

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a tile tap / long-press does; supplied by the overlay. */
internal class TileActions(
    val onTap: (Tile) -> Unit,
    val onLongPress: (Tile) -> Unit,
    /** The user touched the results: hide the suggestion card. */
    val onTouch: () -> Unit,
    /** Right-click: defaults to what long-press does (PAL-20). */
    val onSecondary: (Tile) -> Unit = onLongPress,
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

/**
 * Wave 25: what the Filmstrip draws in the height it is given: the tile (76 dp, down to 32 dp), the caption line under it (26 dp), the footnote line
 * (16 dp) and the nested shortcut pills. [of] drops the extras and shrinks the tile before any of it would run past [heightDp].
 */
internal class StripFit(val tile: Dp, val caption: Boolean, val footnote: Boolean, val pills: Boolean) {
    companion object {
        val Full = StripFit(StripTile, caption = true, footnote = true, pills = true)
        fun of(heightDp: Float): StripFit {
            // 4 dp of air under the caption (the caller's bottom padding).
            val room = heightDp - 4f
            if (room >= 76f + 26f + 16f) return Full
            if (room >= 76f + 26f) return StripFit(StripTile, caption = true, footnote = false, pills = false)
            val tile = (room - 26f).coerceIn(32f, 76f)
            if (room - 26f >= 32f) return StripFit(tile.dp, caption = true, footnote = false, pills = false)
            return StripFit((room).coerceIn(32f, 76f).dp, caption = false, footnote = false, pills = false)
        }
    }
}
private val StripGap = 14.dp
private const val LOOP_COUNT = Int.MAX_VALUE

/** The next frame time for an endless animation: the platform's infinite-animation policy decides (none on a device, so this is plain withFrameNanos there; a test that auto-advances its clock ends the loop). */
private suspend fun infiniteFrameNanos(): Long {
    val policy = kotlin.coroutines.coroutineContext[androidx.compose.ui.platform.InfiniteAnimationPolicy]
    return if (policy != null) policy.onInfiniteOperation { androidx.compose.runtime.withFrameNanos { it } } else androidx.compose.runtime.withFrameNanos { it }
}

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
    /** Wave 18: a quiet line while there is nothing to show yet ("Rings fill up as you use Radar"). */
    emptyHint: String? = null,
    /** Wave 25: the tile size and what else fits in the room the caller has (landscape with the keyboard up), so nothing is cut at the bottom. */
    fit: StripFit = StripFit.Full,
) {
    val z = LocalResultScale.current
    val selectedKey = LocalRadarSelection.current
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
            // Wave 22 (tour): the drift is an endless animation, so it asks the platform's infinite-animation policy for its frames. On a device that is plain withFrameNanos; a UI test
            // that auto-advances its clock ends the loop instead of never becoming idle (the seeded monkey walk hung on this in a busy state).
            val now = infiniteFrameNanos()
            val dt = (now - last) / 1_000_000_000f
            last = now
            if (touching) { lastTouchAt = now; acc = 0f; continue }
            if (now - lastTouchAt < 1_500_000_000L || state.isScrollInProgress) continue
            acc += speedPx * dt.coerceAtMost(0.1f)
            if (acc >= 1f) { state.scrollBy(acc); acc = 0f }
        }
    }
    // The keyboard selection scrolls into the middle.
    LaunchedEffect(selectedKey, n) {
        val target = tiles.indexOfFirst { it.key == selectedKey }
        if (target < 0) return@LaunchedEffect
        if (loop) {
            val base = state.firstVisibleItemIndex - state.firstVisibleItemIndex % n
            runCatching { state.animateScrollToItem(base + target) }
        } else runCatching { state.animateScrollToItem(target) }
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
            Box(Modifier.height(fit.tile), contentAlignment = Alignment.Center) {
                if (emptyHint != null) Text(emptyHint, color = Color.White.copy(alpha = 0.6f), fontSize = (13 * z).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
        } else {
            // The first tile starts exactly in the middle, and a flick settles on a tile (not between two).
            val sidePad = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.let { ((it - fit.tile) / 2).coerceAtLeast(0.dp) }
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(StripGap),
                contentPadding = PaddingValues(horizontal = sidePad),
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
                    state, androidx.compose.foundation.gestures.snapping.SnapPosition.Center,
                ),
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
                    items(LOOP_COUNT, key = { it }) { i -> StripTileView(tiles[i % n], actions, fit) }
                } else {
                    itemsIndexed(tiles, key = { _, t -> t.key }) { _, t -> StripTileView(t, actions, fit) }
                }
            }
        }
        if (fit.caption) Box(Modifier.fillMaxWidth().heightIn(min = 26.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            val focus = centreIndex?.let { if (n > 0) tiles.getOrNull(it % n) else null } ?: tiles.firstOrNull()
            when {
                expanded != null -> ContactChannelBar(expanded, onOpenIntent)
                focus != null -> Text(
                    tileCaption(focus, query), color = Color.White.copy(alpha = 0.92f), fontSize = (14 * z).sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                footnote != null -> Text(footnote, color = Color.White.copy(alpha = 0.6f), fontSize = (12 * z).sp, maxLines = 1)
            }
        }
        if (fit.footnote && footnote != null && n > 0 && expanded == null) {
            Text(footnote, color = Color.White.copy(alpha = 0.55f), fontSize = (11 * z).sp, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
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
private fun StripTileView(tile: Tile, actions: TileActions, fit: StripFit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        Modifier
            .size(fit.tile)
            .radarSelectedRing(LocalRadarSelection.current == tile.key, RoundedCornerShape(14.dp))
            .secondaryClick { actions.onSecondary(tile) }
            .radarDragOut(tile)
            // NFR-13: the strip shows the caption of the centred tile only, so every tile carries its own name.
            .semantics(mergeDescendants = true) { contentDescription = tile.caption() }
            .combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { actions.onTap(tile) }, onLongClick = { actions.onLongPress(tile) }),
    ) { TileFace(tile, fit.tile) }
    // PAL-03: the app's own shortcuts sit inside its tile (not when the room is short: they would hang below it).
    if (tile is Tile.App && fit.pills) NestedShortcutPills(tile, NestedShortcuts.SMALL_SHOWN, fit.tile, onRun = { actions.onTap(Tile.Hit(it)) }, onMore = { actions.onLongPress(tile) })
  }
}

// ---------------------------------------------------------------- orbit


/**
 * Results floating on concentric elliptical rings (like the old AirDrop screen): the best match
 * nearest the top of the innermost ring, then 5 / 9 / 13 / 17 outward. Wave 25: the rings are fitted to the
 * region and nothing leaves it ([OrbitFit]); positions come from layout offsets so only layout (not composition)
 * runs per frame.
 */
@Composable
internal fun Orbit(
    tiles: List<Tile>,
    motionRequested: Boolean,
    footnote: String?,
    actions: TileActions,
    /** Distance from the bottom edge to the common centre of the rings (the middle of the category wheel). */
    centreFromBottom: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** Wave 22: the wheel's size, so tiles stay clear of what is really drawn. */
    wheel: WheelSpec = WheelSpec.Default,
    /** Reports how many of [tiles] have a slot on screen, so the keyboard never selects one that is not drawn. */
    onVisibleCount: (Int) -> Unit = {},
    /** Wave 18: a quiet line under the wheel while there is nothing to show yet ("Rings fill up as you use Radar"). */
    emptyHint: String? = null,
    /** Wave 20: the TalkBack names of the tiers, innermost first (match tiers while a query is typed). */
    tierNames: List<String> = RadarRings.tierNames,
) {
    // NFR-14: with system animations off the tiles do not drift by themselves.
    val motion = motionRequested && !com.bos.sphere.core.design.rememberMotionPrefs().reduced
    val z = LocalResultScale.current
    val selectedKey = LocalRadarSelection.current
    // Arc length travelled (px). It creeps along very slowly by itself; a sideways drag turns the
    // rings directly and a fling leaves extra speed that eases back to the creep.
    val travel = remember { mutableFloatStateOf(0f) }
    val extra = remember { floatArrayOf(0f) } // px/s added by the user
    val dragging = remember { booleanArrayOf(false) }
    val density0 = LocalDensity.current
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // PAL-23: tuned values persist; load them once so the Orbit uses them without the Lab page being opened first.
    androidx.compose.runtime.LaunchedEffect(Unit) { OrbitFeel.load(appCtx) }
    androidx.compose.runtime.LaunchedEffect(motion) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                if (!dragging[0]) {
                    // PAL-23: the feel constants are read live, so the Lab page retunes a running Orbit.
                    val feel = OrbitFeel.current
                    val base = if (motion) with(density0) { feel.creepDp.dp.toPx() } else 0f
                    travel.floatValue += (base + extra[0]) * dt
                    extra[0] = OrbitMotion.decay(extra[0], dt, feel)
                }
            }
        }
    }

    BoxWithConstraints(modifier.linearTraversal().clipToBounds().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.any { it.pressed }) actions.onTouch()
            }
        }
    }) {
        val density = LocalDensity.current
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val cyDp = centreFromBottom.value // (named for history: this is the centre's distance from the top)
        val fs = maxOf(1f, density.fontScale)
        // Wave 25 (owner: "Don't let icons exit the display area. I should be able to see all recents at once"): the rings are ellipses fitted to this
        // region ([OrbitFit]); icons shrink before anything is dropped and no tile box lies even partly outside. The rings no longer revolve
        // through the whole screen (that is what carried icons off it); with motion on each tile drifts a few dp around its place.
        val fit = remember(wDp, hDp, cyDp, tiles.size, wheel, fs, z) {
            OrbitFit.fit(wDp, hDp, cyDp, 90f * fs, 100f * fs, wheel.halfVisible.value, tiles.size, fs, z)
        }
        val shown = tiles.take(fit.slots.size)
        androidx.compose.runtime.SideEffect { onVisibleCount(shown.size) }
        Canvas(Modifier.fillMaxSize()) {
            // Each ring is a faint tier guide; the inner tiers are a little brighter.
            if (fit.guides) fit.rings.forEachIndexed { r, e ->
                drawOval(Color.White.copy(alpha = 0.16f - 0.035f * r), topLeft = Offset((e[0] - e[2]) * density.density, (e[1] - e[3]) * density.density), size = androidx.compose.ui.geometry.Size(2 * e[2] * density.density, 2 * e[3] * density.density), style = Stroke(width = 1.dp.toPx()))
            }
        }
        // TalkBack: one quiet heading per ring that has results, named for its tier.
        fit.slots.take(shown.size).map { it.ring }.distinct().forEach { r ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .offset { IntOffset((wDp / 2f * density.density).roundToInt(), ((fit.rings[r][1] - fit.rings[r][3]) * density.density).roundToInt()) }
                    .size(1.dp)
                    .semantics { heading(); contentDescription = L10n.tr(tierNames[r.coerceIn(0, tierNames.lastIndex)]) },
            )
        }
        val wPx = wDp * density.density
        val hPx = hDp * density.density
        val boxHPx = fit.boxH * density.density

        shown.forEachIndexed { i, tile ->
            val slot = fit.slots[i]
            val ring = slot.ring
            val tierDescription = L10n.tr(tierNames[ring.coerceIn(0, tierNames.lastIndex)])
            Column(
                Modifier
                    // The tile is placed first (outermost), so its semantics and its touch area are where it is drawn, not at the origin.
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        layout(p.width, p.height) {
                            // The tile's own measured box is kept inside the region whatever it holds (a caption, the nested shortcut pills).
                            val bob = if (motion) with(density) { OrbitFit.BOB.dp.toPx() } else 0f
                            val ph = travel.floatValue / 120f + i * 1.7f
                            val x = (slot.x * density.density + bob * cos(ph) - p.width / 2f).coerceIn(0f, (wPx - p.width).coerceAtLeast(0f))
                            val y = (slot.y * density.density - boxHPx / 2f + bob * sin(ph)).coerceIn(0f, (hPx - p.height).coerceAtLeast(0f))
                            p.place(x.roundToInt(), y.roundToInt())
                        }
                    }
                    .width(fit.boxW.dp)
                    // NFR-13: geometry means little to TalkBack; the tiles read in rank order, best match first.
                    .semantics(mergeDescendants = true) { traversalIndex = i.toFloat(); contentDescription = tile.caption(); stateDescription = tierDescription }
                    .testTag("orbitTile"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.radarSelectedRing(selectedKey == tile.key, androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape).secondaryClick { actions.onSecondary(tile) }.radarDragOut(tile, pointer = false).orbitClick(tile, actions),
                ) { TileFace(tile, fit.icon.dp) }
                Text(
                    tile.caption(), color = Color.White.copy(alpha = 0.85f), fontSize = (10 * z).sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                )
                if (tile is Tile.App) NestedShortcutPills(tile, NestedShortcuts.SMALL_SHOWN, fit.boxW.dp, onRun = { actions.onTap(Tile.Hit(it)) }, onMore = { actions.onLongPress(tile) })
            }
        }
        if (footnote != null) {
            Text(
                footnote, color = Color.White.copy(alpha = 0.55f), fontSize = (11 * z).sp, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
        if (emptyHint != null && tiles.isEmpty()) {
            Text(
                emptyHint, color = Color.White.copy(alpha = 0.6f), fontSize = (13 * z).sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (cyDp * density.density + with(density) { (wheel.height / 2 + 14.dp).toPx() }).roundToInt()) }.padding(horizontal = 32.dp),
            )
        }
    }
}

/** Height of the typed Orbit's result strip; the rings are centred above it. */
internal val RadarStripHeight = 52.dp

/**
 * Wave 20: the compact strip under the typed Orbit rings: the web hand-off rows (and a Filenames refinement when files can be
 * searched) as one scrolling line of pills, so they stay one tap away and the rings keep the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultStrip(
    tiles: List<Tile>,
    filenames: String?,
    actions: TileActions,
    onFilenames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val z = LocalResultScale.current
    val selectedKey = LocalRadarSelection.current
    // Wave 22 (audit): every pill is a 48 dp high labelled button (the pill keeps its look inside it), and the strip grows with a big text
    // size instead of clipping the pill (it used to be a fixed 52 dp high with 6 dp padding, so a 34 dp pill was the target).
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().heightIn(min = RadarStripHeight).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { t ->
            Box(
                Modifier.heightIn(min = 48.dp)
                    .secondaryClick { actions.onSecondary(t) }
                    .combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { actions.onTap(t) }, onLongClick = { actions.onLongPress(t) })
                    .semantics { role = Role.Button},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.caption(), color = Color.White, fontSize = (13 * z).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp).radarSelectedRing(selectedKey == t.key, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Color(0x33FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
        if (filenames != null) {
            Box(Modifier.heightIn(min = 48.dp).clickable(onClick = onFilenames).semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                Text(
                    filenames, color = Color(0xCCFFFFFF), fontSize = (13 * z).sp, maxLines = 1,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Color(0x1FFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.orbitClick(tile: Tile, actions: TileActions): Modifier =
    this.combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { actions.onTap(tile) }, onLongClick = { actions.onLongPress(tile) })

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
    /** Long-press / right-click on a result (the host shows the preview, the same one in all layouts). */
    onPreview: (Tile) -> Unit = {},
) {
    val rows = remember(data, scope, suggestions) {
        buildList<FRow> {
            var si = 0
            fun addSuggestions() { suggestions.forEach { g -> add(FRow.Header(g.header)); g.items.forEach { add(FRow.Sugg(it, si++)) } } }
            if (!plain) addSuggestions()
            for ((cat, title) in flatSections(data)) {
                if (scope != null && scope != cat) continue
                if (!restingShows(data, scope, cat)) continue
                val tiles = data.tiles(cat)
                val note = data.footnote(cat)
                if (tiles.isEmpty() && note == null) continue
                // Suggestion headers already say "Contacts"/"Web": the results get their own header below.
                // Wave 18: at rest the list is what you open most, most used first.
                if (data.blank && cat == SearchCategory.Apps) add(FRow.Header(L10n.tr("Most used") + "​"))
                else if (!(plain && cat == SearchCategory.Apps)) add(FRow.Header((if (plain && cat == SearchCategory.Web) L10n.tr("Web Suggestions") else title) + "​"))
                tiles.forEach { add(FRow.Item(it)) }
                if (note != null) add(FRow.Note(note))
            }
            if (plain) addSuggestions()
        }
    }
    if (rows.isEmpty()) {
        // Wave 18: nothing learned yet; a quiet line instead of an empty screen.
        if (data.blank && scope == null) Text(L10n.tr("Rings fill up as you use Radar"), color = Color(0x99FFFFFF), fontSize = (14 * LocalResultScale.current).sp, modifier = modifier.padding(horizontal = 4.dp, vertical = 12.dp))
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedKey = LocalRadarSelection.current
    // The keyboard selection stays in view.
    LaunchedEffect(selectedKey) {
        val at = rows.indexOfFirst { it is FRow.Item && it.tile.key == selectedKey }
        if (at >= 0) {
            val vis = listState.layoutInfo.visibleItemsInfo
            if (vis.isEmpty() || at <= vis.first().index || at >= vis.last().index) runCatching { listState.animateScrollToItem((at - 1).coerceAtLeast(0)) }
        }
    }
    // Pinch and Ctrl+scroll live on the whole Radar screen (PAL-20), so the scale comes from LocalResultScale.
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
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> r.key }) { idx, row ->
            val z = LocalResultScale.current
            when (row) {
                is FRow.Header -> Text(
                    row.title.removeSuffix("​"), color = Color(0x99FFFFFF), fontSize = (15 * z).sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 3.dp).semantics { heading() },
                )
                is FRow.Note -> Text(row.text, color = Color(0x80FFFFFF), fontSize = (15 * z).sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                is FRow.Sugg -> {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onPickSuggestion(row.item) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KindIcon(row.item.icon, 20.dp, if (row.item.accent) HyleColors.VioletSoft else Color.White)
                        Text(
                            row.item.label, color = if (row.item.accent) HyleColors.VioletSoft else Color.White, fontSize = (18 * z).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    if (rows.getOrNull(idx + 1) is FRow.Sugg) Divider()
                }
                is FRow.Item -> {
                    FlatTileRow(row.tile, data.query, onTileTap, onPreview, onOpenIntent, onQueryChange)
                    if (rows.getOrNull(idx + 1) is FRow.Item) Divider()
                }
            }
        }
    }
}

/** The sections of the flat list, in order: Applications, Actions, Contacts, Files, Web (always last, PAL-06; an inline answer moves Actions to the top, PAL-05). */
internal fun flatSections(data: SearchData): List<Pair<SearchCategory, String>> = listOf(
    SearchCategory.Apps to L10n.tr("Applications"), SearchCategory.Actions to L10n.tr("Actions"),
    SearchCategory.Contacts to L10n.tr("Contacts"), SearchCategory.Files to L10n.tr("Files"),
    SearchCategory.Web to L10n.tr("Web"),
).let { l -> if (data.answer != null) l.sortedBy { p -> if (p.first == SearchCategory.Actions) 0 else 1 } else l } // PAL-05, PAL-06: an answer on top, the web hand-off always last

/** The results the keyboard walks in the flat list (PAL-07), top to bottom as they are drawn. */
internal fun flatTiles(data: SearchData, scope: SearchCategory?): List<Tile> =
    flatSections(data).filter { (scope == null || scope == it.first) && restingShows(data, scope, it.first) }.flatMap { data.tiles(it.first) }

/** Before anything is typed, "Everything" lists only what you really use: apps and people (the same rule as [SearchData.tiles]). */
internal fun restingShows(data: SearchData, scope: SearchCategory?, cat: SearchCategory): Boolean =
    !(data.blank && scope == null && cat != SearchCategory.Apps && cat != SearchCategory.Contacts)

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
    onPreview: (Tile) -> Unit,
    onOpenIntent: (android.content.Intent) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    val z = LocalResultScale.current
    val sel = LocalRadarSelection.current == tile.key
    val selBg = if (sel) Modifier.background(Color(0x33B9A8FF)).radarSelectedRing(true, RoundedCornerShape(10.dp)) else Modifier
    when (tile) {
        is Tile.Person -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(selBg).padding(vertical = 6.dp, horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).secondaryClick { onPreview(tile) }.combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { onOpenIntent(ContactActions.openContact(tile.c.hit)) }, onLongClick = { onPreview(tile) }),
            ) {
                ContactFace(tile.c, 38.dp * z)
                Text(tile.c.hit.name, color = Color.White, fontSize = (18 * z).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 6.dp))
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            tile.c.channels.forEach { ch -> ChannelButton(ch) { ContactHabits.record(ctx, tile.c.hit.id, ch); ContactActions.intentFor(ch, tile.c.hit)?.let(onOpenIntent) } }
        }
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(selBg)
                .secondaryClick { onPreview(tile) }
                .radarDragOut(tile)
                .combinedClickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = { onTap(tile) },
                    onLongClick = { onPreview(tile) },
                )
                .heightIn(min = 48.dp).padding(vertical = 7.dp, horizontal = 16.dp),
        ) {
            TileFace(tile, if (tile is Tile.Hit && tile.hit.snippet != null) 44.dp * z else 36.dp * z)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (tile is Tile.Hit) highlight(tile.hit.title, query, Color(0xFFB9A8FF)) else AnnotatedString(tile.caption()),
                    color = if (tile is Tile.Allow) Color(0x99FFFFFF) else Color.White, fontSize = (18 * z).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (tile is Tile.App) NestedShortcutRow(tile, onRun = { onTap(Tile.Hit(it)) })
                if (tile is Tile.Hit) {
                    tile.hit.subtitle?.let { Text(it, color = Color(0x99FFFFFF), fontSize = (12 * z).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    tile.hit.snippet?.let {
                        Text(highlight(it, query, Color(0xFFB9A8FF)), color = Color(0xCCFFFFFF), fontSize = (15 * z).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (tile is Tile.Hit) sourceBadge(tile.hit.sourcePackage)?.let {
                Text(it, color = Color(0xCCFFFFFF), fontSize = (10 * z).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).background(Color(0x33FFFFFF), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}


/** Radar result text scale (1 = default), kept across visits. */
internal val LocalResultScale = androidx.compose.runtime.compositionLocalOf { 1f }

internal object RadarPrefs {
    private fun sp(c: android.content.Context) = c.getSharedPreferences("radar_ui", android.content.Context.MODE_PRIVATE)
    fun scale(c: android.content.Context): Float = runCatching { sp(c).getFloat("resultScale", 1.15f) }.getOrDefault(1.15f)
    fun setScale(c: android.content.Context, v: Float) { runCatching { sp(c).edit().putFloat("resultScale", v).apply() } }
}

/** Long-press preview: the result at full size, with everything it knows, and the way to open it. */
@Composable
internal fun ResultPreview(
    tile: Tile,
    query: String,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onLongPressApp: (com.bos.sphere.core.data.AppEntry) -> Unit,
    onOpenIntent: ((android.content.Intent) -> Unit)? = null,
    /** PAL-03: runs one of an app result's nested shortcuts. */
    onShortcut: (com.bos.sphere.core.data.SearchHit) -> Unit = {},
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DialogControllerKeys()
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xF2101018)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(24.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileFace(tile, 84.dp)
            Text(tile.caption(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            if (tile is Tile.Hit) {
                tile.hit.subtitle?.let { Text(it, color = Color(0xB3FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
                tile.hit.snippet?.let { Text(highlight(it, query, Color(0xFFB9A8FF)), color = Color(0xE6FFFFFF), fontSize = 16.sp, maxLines = 12, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp)) }
            }
            if (tile is Tile.App) NestedShortcutRow(tile, onRun = onShortcut, modifier = Modifier.padding(top = 10.dp))
            if (tile is Tile.Setting) {
                Text(tile.entry.category, color = Color(0xB3FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                if (tile.entry.description.isNotEmpty()) Text(tile.entry.description, color = Color(0xE6FFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            if (tile is Tile.Person && onOpenIntent != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(Modifier.padding(top = 14.dp)) {
                    tile.c.channels.forEach { ch -> ChannelButton(ch) { ContactHabits.record(ctx, tile.c.hit.id, ch); ContactActions.intentFor(ch, tile.c.hit)?.let(onOpenIntent) } }
                }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                if (tile is Tile.App) Text(L10n.tr("Options").uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF241F37)).clickable(role = androidx.compose.ui.semantics.Role.Button) { onLongPressApp(tile.app) }.padding(horizontal = 20.dp, vertical = 12.dp))
                Text(L10n.tr("Open").uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(HyleColors.Violet.copy(alpha = 0.55f)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen).padding(horizontal = 28.dp, vertical = 12.dp))
            }
        }
    }
}

/** INP-11: a drag out of a Radar app result (a mouse drag, or a finger after a long press) carries the app to the home, a folder or the dock. */
internal fun Modifier.radarDragOut(tile: Tile, pointer: Boolean = true): Modifier =
    if (tile is Tile.App) appDragSource(tile.app.key, onStart = { LauncherDrops.onDragStart?.invoke() }, pointer = pointer) else this
