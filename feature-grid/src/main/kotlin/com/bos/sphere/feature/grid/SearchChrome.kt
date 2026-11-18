package com.bos.sphere.feature.grid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HitKind
import com.bos.sphere.core.data.SearchCategory
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.data.ContactLogic
import com.bos.sphere.core.data.Channel
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.WebRow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- backdrop

/** Wave 15: Radar's own base is plain black; the background items ([SignalBackground]) are drawn over it by the search overlay. */
@Composable
internal fun SearchBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black))
}

// ---------------------------------------------------------------- category wheel + query

private val WheelRow = 34.dp
/** The wheel at its roomiest: five rows. */
internal val WheelHeight = WheelRow * 5

/** How far above and below the middle of the wheel its labels are still bright enough to be in the way of a tile. */
internal val WheelRowHalfVisible = WheelRow * 1.6f

/**
 * Wave 22: the wheel sized to the room it really has. [rows] is odd (the selected label sits in the middle row) and [row] grows with the
 * font scale so two labels never overlap; [height] is the wheel's own box, which is the only place a label is drawn.
 */
internal data class WheelSpec(val rows: Int, val row: Dp) {
    val height: Dp get() = row * rows
    /** How far from the middle a tile must stay clear: the labels that are still bright. */
    val halfVisible: Dp get() = row * minOf(1.6f, rows / 2f + 0.1f)

    companion object {
        val Default = WheelSpec(5, WheelRow)
        /** Row height in dp for [fontScale]: 34 dp, or 1.5 lines of the 16 sp label when the text is larger. */
        fun rowFor(fontScale: Float): Dp = maxOf(WheelRow.value, 16f * fontScale * 1.5f).dp
        /** The most rows (1, 3 or 5) that fit in [freeDp] (the free height, keeping 8 dp of air), never fewer than one. */
        fun fit(freeDp: Float, fontScale: Float): WheelSpec {
            val row = rowFor(fontScale)
            val n = ((freeDp - 8f) / row.value).toInt().coerceIn(1, 5)
            return WheelSpec(if (n % 2 == 0) n - 1 else n, row)
        }
    }
}

/**
 * Opacity of the wheel label [d] rows from the selected one (0 is the selected label) in a wheel of [rows] rows: the old falloff, ramped
 * to nothing over the last half row, so no glyph is drawn outside the wheel's box and the edge of the results area never cuts one in half.
 */
internal fun wheelLabelAlpha(d: Float, rows: Int = 5): Float {
    val a = d.coerceAtLeast(0f)
    return (1f / (1f + 0.9f * a * a)) * ((rows / 2f - a) * 2f).coerceIn(0f, 1f)
}

/**
 * The slot-machine category wheel: [selected] sits bright in the middle, its neighbours dim away
 * above and below. Drag vertically or tap a label to change; each step calls [onTick].
 */
@Composable
internal fun CategoryWheel(
    selected: SearchCategory?,
    counts: Map<SearchCategory, Int>?,
    onSelect: (SearchCategory?) -> Unit,
    onTick: () -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    spec: WheelSpec = WheelSpec.Default,
) {
    val cats: List<SearchCategory?> = listOf(null) + SearchCategory.entries
    fun idx(c: SearchCategory?) = cats.indexOf(c).toFloat()
    val pos = remember { Animatable(idx(selected)) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val raw = remember { floatArrayOf(idx(selected)) }
    val onSelectNow by rememberUpdatedState(onSelect)
    val onTickNow by rememberUpdatedState(onTick)
    val onTouchNow by rememberUpdatedState(onTouch)
    LaunchedEffect(selected) {
        if (!dragging) { raw[0] = idx(selected); pos.animateTo(idx(selected), spring(stiffness = 380f)) }
    }
    val rowPx = with(LocalDensity.current) { spec.row.toPx() }
    Box(
        modifier
            .height(spec.height)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; onTouchNow() },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val before = raw[0].roundToInt()
                        raw[0] = (raw[0] - dy / rowPx).coerceIn(0f, (cats.size - 1).toFloat())
                        val v = raw[0]
                        scope.launch { pos.snapTo(v) }
                        val now = v.roundToInt()
                        if (now != before) { onTickNow(); onSelectNow(cats[now]) }
                    },
                    onDragEnd = { dragging = false; scope.launch { pos.animateTo(raw[0].roundToInt().toFloat(), spring(stiffness = 380f)) } },
                    onDragCancel = { dragging = false; scope.launch { pos.animateTo(raw[0].roundToInt().toFloat(), spring(stiffness = 380f)) } },
                )
            },
    ) {
        cats.forEachIndexed { i, cat ->
            val n = if (cat == null) -1 else counts?.get(cat) ?: -1
            val empty = cat != null && counts != null && n == 0 && cat != selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(0, ((i - pos.value) * rowPx).roundToInt()) }
                    .graphicsLayer {
                        val d = abs(i - pos.value)
                        alpha = wheelLabelAlpha(d, spec.rows) * (if (empty) 0.55f else 1f)
                    }
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        onTouchNow(); if (cat != selected) { onTickNow(); onSelectNow(cat) }
                    },
            ) {
                Text(
                    (cat?.label ?: L10n.tr("Everything")).uppercase(), color = Color.White, fontSize = 16.sp, letterSpacing = 0.8.sp,
                    fontWeight = if (cat == selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                )
                if (n > 0 && cat != selected) {
                    Text(
                        n.coerceAtMost(99).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * The "SEARCH" label left of the wheel, right-aligned against it; a tap raises the keyboard. Wave 22 (audit): the typed-query
 * branch (a large query, a blinking caret and a 28 dp clear button) belonged to the old bottom field layout and was never drawn
 * once the one glass field moved to the top. Wave 23 (sweep): the branch and its two parameters were still in the code, called only with an
 * empty query, and are now gone for real; the query and its Clear X live in the glass field.
 */
@Composable
internal fun QueryDisplay(onTap: () -> Unit, modifier: Modifier = Modifier, height: Dp = WheelHeight) {
    Box(modifier.height(height).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onTap), contentAlignment = Alignment.CenterEnd) {
        Text(L10n.tr("SEARCH"), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- scope token + menu

/** The macOS "TAG ⌄" token: the current scope; tapping opens the Everything / The web / … menu. */
@Composable
internal fun ScopeToken(
    scope: SearchCategory?,
    shown: SearchCategory?,
    onPick: (SearchCategory?) -> Unit,
    modifier: Modifier = Modifier,
    /** Plain text with an up/down mark (the bottom query line) instead of the chip. */
    plain: Boolean = false,
    /** What "The web" is called in the list: the chosen search engine. */
    webLabel: String = L10n.tr("The web"),
    /** The query line is at the top of the screen, so the scopes fan out downward (wave 18). */
    fanDown: Boolean = false,
    /** False draws only the glyph and the mark, so a narrow query line keeps its room (the scope is still named for TalkBack). */
    showLabel: Boolean = true,
    /** Wave 21: the plain token wears a glass pill (it sits inside the glass field) and keeps a 48 dp touch target. */
    glass: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        if (plain) {
            // The query line: "Everything 🌐 ⇅". Tapping fans the scopes out (downward from the top line), right-aligned (macOS-style).
            val dim = Color(0xB3FFFFFF)
            val ink = if (glass) GlassSpec.content(true) else dim
            val tokenContent: @Composable () -> Unit = {
                if (showLabel) Text(scopeLabel(shown, webLabel), color = ink, fontSize = 16.sp, maxLines = 1)
                Icon(scopeGlyph(shown), if (showLabel) null else scopeLabel(shown, webLabel), tint = ink, modifier = Modifier.padding(start = if (showLabel) 8.dp else 0.dp).size(20.dp))
                Column(Modifier.padding(start = 2.dp)) {
                    Icon(Glyphs.Up, null, tint = ink, modifier = Modifier.size(10.dp).offset(y = 2.dp))
                    Icon(Glyphs.Down, null, tint = ink, modifier = Modifier.size(10.dp).offset(y = (-2).dp))
                }
            }
            if (glass) {
                // Wave 21: a quiet glass pill inside the glass field; the clickable area is the full 48 dp height (NFR-13).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (open) 0f else 1f).heightIn(min = GlassSpec.Target).clip(RoundedCornerShape(24.dp)).clickable { open = true }
                        .semantics { role = androidx.compose.ui.semantics.Role.Button },
                ) {
                    Row(Modifier.glass(RoundedCornerShape(50), strength = GlassSpec.NESTED_STRENGTH).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { tokenContent() }
                }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (open) 0f else 1f).clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 6.dp),
            ) { tokenContent() }
            if (open) {
                androidx.compose.ui.window.Popup(
                    alignment = if (fanDown) Alignment.TopEnd else Alignment.BottomEnd,
                    onDismissRequest = { open = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    // Furthest scope on top, "Everything" last — sitting exactly where the label was.
                    val order: List<SearchCategory?> = listOf(SearchCategory.Files, SearchCategory.Actions, SearchCategory.Web, SearchCategory.Contacts, SearchCategory.Apps, null).let { if (fanDown) it.asReversed() else it }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 6.dp)) {
                        order.forEach { opt ->
                            val on = opt == scope
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { open = false; onPick(opt) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            ) {
                                Text(scopeLabel(opt, webLabel), color = if (on) Color.White else dim, fontSize = 16.sp, maxLines = 1)
                                Icon(scopeGlyph(opt), null, tint = if (on) Color.White else dim, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                                Spacer(Modifier.width(12.dp)) // lines the glyphs up with the ⇅ mark
                            }
                        }
                    }
                }
            }
        } else Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x40FFFFFF))
                .clickable { open = true }
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                (shown?.label ?: L10n.tr("Everything")).uppercase(), color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, maxLines = 1,
            )
            Icon(Glyphs.Down, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        if (!plain) DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            containerColor = Color(0xF21F2024), shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        ) {
            val options: List<SearchCategory?> = listOf(null, SearchCategory.Web, SearchCategory.Actions, SearchCategory.Apps, SearchCategory.Contacts, SearchCategory.Files)
            for (opt in options) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(22.dp)) { if (opt == scope) Icon(CheckGlyph, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            Text(opt?.label ?: L10n.tr("Everything"), color = Color.White, fontSize = 16.sp)
                        }
                    },
                    onClick = { open = false; onPick(opt) },
                )
            }
        }
    }
}

private fun scopeLabel(c: SearchCategory?, webLabel: String): String = when (c) {
    null -> L10n.tr("Everything")
    SearchCategory.Web -> webLabel
    else -> c.label
}

private fun scopeGlyph(c: SearchCategory?): ImageVector = when (c) {
    null -> Glyphs.Globe
    SearchCategory.Web -> Glyphs.Search
    SearchCategory.Actions -> Glyphs.Bolt
    SearchCategory.Apps -> Glyphs.Apps
    SearchCategory.Contacts -> Glyphs.Person
    SearchCategory.Files -> FolderGlyph
}

// ---------------------------------------------------------------- tile faces

internal fun hitGlyph(hit: SearchHit): ImageVector = hit.actionId?.let { Glyphs.forAction(it) } ?: ActionIcons.kind(hit)?.glyph ?: when (hit.kind) {
    HitKind.Action -> Glyphs.Bolt
    HitKind.Document -> DocGlyph
    HitKind.Image -> Glyphs.Image
    HitKind.Video -> VideoGlyph
    HitKind.Audio -> Glyphs.Music
    HitKind.Archive -> Glyphs.Storage
    HitKind.Folder -> FolderGlyph
    HitKind.Web -> Glyphs.Globe
    HitKind.Other -> DocGlyph
}

internal fun channelGlyph(ch: Channel): ImageVector = when (ch) {
    Channel.Call -> Glyphs.Phone
    Channel.Sms -> Glyphs.Message
    Channel.Email -> Glyphs.Mail
    Channel.WhatsApp -> WhatsAppGlyph
    Channel.Telegram -> TelegramGlyph
    Channel.Signal -> SignalGlyph
}

/** A file / action thumbnail (loaded lazily, cached) or the kind glyph on a dark plate. */
@Composable
internal fun HitFace(hit: SearchHit, size: Dp, modifier: Modifier = Modifier, radius: Dp = size * 0.26f) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(48) }
    val thumbSource = hit.thumbUri ?: if (hit.kind == HitKind.Image || hit.kind == HitKind.Video) hit.uri else null
    var bmp by remember(hit.id) { mutableStateOf(thumbSource?.let { ThumbCache.get(it) }) }
    LaunchedEffect(hit.id, thumbSource) {
        if (bmp == null && thumbSource != null) bmp = ThumbCache.load(context, thumbSource, px)
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.size(size).clip(shape).background(if (hit.actionId != null || hit.kind == HitKind.Action) Color(0xFF2A2350) else Color(0xFF20232D)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (NestedShortcuts.isAppShortcut(hit)) {
            // Wave 25 (owner item 6): an app shortcut shows its own icon (else its app's), at a larger size than a glyph.
            HitLeadingIcon(hit, size * 0.62f, HyleColors.VioletSoft)
        } else {
            Icon(hitGlyph(hit), null, tint = if (hit.actionId != null || hit.kind == HitKind.Action) HyleColors.VioletSoft else Color(0xFFE7E9F0), modifier = Modifier.size(size * 0.46f))
        }
        sourceBadge(hit.sourcePackage)?.let { s ->
            Text(
                s, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

/** Squircle avatar with initials and, per available channel, a small badge. */
@Composable
internal fun ContactFace(c: ContactRow, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val photo by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(ContactPhotos.peek(c.hit.id), c.hit.id) {
            if (value == null) value = ContactPhotos.load(ctx, c.hit)
        }
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .background(Brush.linearGradient(listOf(HyleColors.VioletSoft, HyleColors.Violet))),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = photo
            if (bmp != null) androidx.compose.foundation.Image(bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
            else Text(ContactLogic.initials(c.hit.name), color = HyleColors.OnAccent, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold)
        }
        c.primary?.let { ch ->
            val telegram = ch == Channel.Telegram
            Box(
                Modifier.align(Alignment.TopEnd).offset(size * 0.1f, -size * 0.1f).size(size * 0.38f).clip(CircleShape)
                    .background(if (telegram) Color(0xFF2AABEE) else Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(channelGlyph(ch), null, tint = if (telegram) Color.White else Color.Black, modifier = Modifier.size(size * 0.22f)) }
        }
    }
}

@Composable
internal fun WebFace(glyph: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(Color(0xFF1D2A3E)), contentAlignment = Alignment.Center) {
        Icon(glyph, null, tint = Color(0xFF9CC4FF), modifier = Modifier.size(size * 0.46f))
    }
}

/** The face of any [Tile] at [size]. */
@Composable
internal fun TileFace(tile: Tile, size: Dp, modifier: Modifier = Modifier) {
    when (tile) {
        is Tile.App -> AppIcon(tile.app, size, modifier)
        is Tile.Person -> ContactFace(tile.c, size, modifier)
        is Tile.Hit -> HitFace(tile.hit, size, modifier)
        is Tile.Web -> WebFace(
            when (tile.row) {
                is WebRow.Open -> Glyphs.Globe
                is WebRow.PlayStore -> Glyphs.Apps
                else -> Glyphs.Search
            },
            size, modifier,
        )
        is Tile.Allow -> WebFace(if (tile.files) FolderGlyph else Glyphs.Person, size, modifier)
        is Tile.Setting -> WebFace(Glyphs.Sliders, size, modifier)
    }
}

// ---------------------------------------------------------------- glyphs

private fun sglyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        )
    }.build()

internal val CheckGlyph = sglyph("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
internal val DocGlyph = sglyph("doc") {
    moveTo(6f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(6f, 21f); close()
    moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
    moveTo(9f, 13f); lineTo(16f, 13f); moveTo(9f, 17f); lineTo(16f, 17f)
}
internal val FolderGlyph = sglyph("folder") {
    moveTo(3f, 7f); lineTo(3f, 19f); lineTo(21f, 19f); lineTo(21f, 9f); lineTo(11f, 9f); lineTo(9f, 6f); lineTo(4f, 6f); close()
}
internal val VideoGlyph = sglyph("video") {
    moveTo(3f, 6f); lineTo(16f, 6f); lineTo(16f, 18f); lineTo(3f, 18f); close()
    moveTo(16f, 10f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(16f, 14f)
}

/** Contact photos (profile pictures), loaded off the main thread and cached; null when a contact has none. */
internal object ContactPhotos {
    private val cache = object : android.util.LruCache<Long, androidx.compose.ui.graphics.ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: androidx.compose.ui.graphics.ImageBitmap) = value.width * value.height * 4
    }
    private val none = java.util.Collections.synchronizedSet(HashSet<Long>())
    fun peek(id: Long): androidx.compose.ui.graphics.ImageBitmap? = cache.get(id)
    suspend fun load(context: android.content.Context, hit: com.bos.sphere.core.data.ContactHit): androidx.compose.ui.graphics.ImageBitmap? {
        cache.get(hit.id)?.let { return it }
        if (hit.id in none) return null
        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = android.provider.ContactsContract.Contacts.getLookupUri(hit.id, hit.lookupKey)
                android.provider.ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.let { b ->
                        android.graphics.Bitmap.createScaledBitmap(b, 160, 160, true).let { bb -> bb.asImageBitmap() }
                    }
                }
            }.getOrNull()
        }
        if (bmp != null) cache.put(hit.id, bmp) else none += hit.id
        return bmp
    }
}
