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

/** Black, or the bundled picture (cropped, gently scrimmed, drifting slowly when [motion]). */
@Composable
internal fun SearchBackdrop(style: com.bos.sphere.core.data.SearchBackdropStyle, motion: Boolean, modifier: Modifier = Modifier) {
    val picture = style == com.bos.sphere.core.data.SearchBackdropStyle.Picture
    Box(modifier.background(Color.Black)) {
        if (style == com.bos.sphere.core.data.SearchBackdropStyle.Radar) RadarSweep(motion, Modifier.fillMaxSize())
        if (picture) {
            val drift = if (motion) {
                rememberInfiniteTransition(label = "backdrop").animateFloat(
                    -1f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
                )
            } else null
            Image(
                painter = painterResource(R.drawable.search_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val d = drift?.value ?: 0f
                    val s = if (drift != null) 1.12f else 1f
                    scaleX = s; scaleY = s
                    translationX = d * size.width * 0.035f
                    translationY = d * size.height * 0.015f
                },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x33000000), Color(0x80000000)))))
        }
    }
}

/**
 * An animated radar scope: faint rings and crosshair, a green sweep that turns once every few
 * seconds, and blips that flare when the sweep passes over them and fade after.
 */
@Composable
internal fun RadarSweep(motion: Boolean, modifier: Modifier = Modifier) {
    val angle = if (motion) {
        rememberInfiniteTransition(label = "radar").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "sweep").value
    } else 40f
    androidx.compose.foundation.Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.82f)
        val maxR = kotlin.math.hypot(size.width, size.height) * 0.62f
        drawRect(Brush.radialGradient(listOf(Color(0xFF06150F), Color(0xFF020706), Color.Black), center = c, radius = maxR))
        val green = Color(0xFF3CFF9A)
        for (i in 1..5) drawCircle(green.copy(alpha = 0.13f), radius = maxR * i / 5f * 0.78f, center = c, style = Stroke(1.dp.toPx()))
        drawLine(green.copy(alpha = 0.10f), Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 1.dp.toPx())
        drawLine(green.copy(alpha = 0.10f), Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 1.dp.toPx())
        // The sweep: a trailing wedge ending at the leading line.
        rotate(angle, c) {
            drawCircle(
                Brush.sweepGradient(0.78f to Color.Transparent, 1f to green.copy(alpha = 0.34f), center = c),
                radius = maxR, center = c,
            )
            drawLine(green.copy(alpha = 0.75f), c, Offset(c.x + maxR, c.y), 1.6.dp.toPx())
        }
        // Blips: fixed pseudo-random spots, bright just after the sweep passes.
        for (i in 0 until 9) {
            val a = ((i * 137.5f) % 360f)
            val r = maxR * (0.18f + ((i * 53) % 60) / 100f) * 0.8f
            val rad = Math.toRadians(a.toDouble())
            val p = Offset(c.x + r * kotlin.math.cos(rad).toFloat(), c.y + r * kotlin.math.sin(rad).toFloat())
            val since = ((angle - a) % 360f + 360f) % 360f
            val glow = (1f - since / 200f).coerceIn(0f, 1f)
            if (glow > 0f) {
                drawCircle(green.copy(alpha = 0.55f * glow), radius = 4.dp.toPx(), center = p)
                drawCircle(green.copy(alpha = 0.18f * glow), radius = 11.dp.toPx(), center = p)
            }
        }
    }
}

// ---------------------------------------------------------------- category wheel + query

private val WheelRow = 34.dp
internal val WheelHeight = WheelRow * 5

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
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    Box(
        modifier
            .height(WheelHeight)
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
                        alpha = (1f / (1f + 0.9f * d * d)) * (if (empty) 0.55f else 1f)
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

@Composable
internal fun BlinkingCaret(height: Dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "caret").animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "c")
    Box(modifier.padding(start = 2.dp).width(2.dp).height(height).graphicsLayer { alpha = if (t.value < 0.5f) 1f else 0f }.background(color))
}

/**
 * SEARCH, or the typed query once there is one (large, light) with a blinking caret and a clear
 * button. Sits left of the wheel, right-aligned against it.
 */
@Composable
internal fun QueryDisplay(query: String, onClear: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(WheelHeight).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onTap), contentAlignment = Alignment.CenterEnd) {
        if (query.isEmpty()) {
            Text(L10n.tr("SEARCH"), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, letterSpacing = 0.8.sp, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyphs.Close, L10n.tr("Clear"), tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(
                    query, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, lineHeight = 32.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                BlinkingCaret(30.dp)
            }
        }
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
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        if (plain) {
            // The bottom query line: "Everything 🌐 ⇅". Tapping fans the scopes out upward, right-aligned (macOS-style).
            val dim = Color(0xB3FFFFFF)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (open) 0f else 1f).clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Text(scopeLabel(shown, webLabel), color = dim, fontSize = 16.sp, maxLines = 1)
                Icon(scopeGlyph(shown), null, tint = dim, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                Column(Modifier.padding(start = 2.dp)) {
                    Icon(Glyphs.Up, null, tint = dim, modifier = Modifier.size(10.dp).offset(y = 2.dp))
                    Icon(Glyphs.Down, null, tint = dim, modifier = Modifier.size(10.dp).offset(y = (-2).dp))
                }
            }
            if (open) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.BottomEnd,
                    onDismissRequest = { open = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    // Furthest scope on top, "Everything" last — sitting exactly where the label was.
                    val order: List<SearchCategory?> = listOf(SearchCategory.Files, SearchCategory.Actions, SearchCategory.Web, SearchCategory.Contacts, SearchCategory.Apps, null)
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

// ---------------------------------------------------------------- suggestion card

/** The macOS suggestion menu: grey bold section headers over rows, hairline between groups. */
@Composable
internal fun SuggestionGroups(groups: List<SuggGroup>, onPick: (SuggItem) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier) {
        groups.forEachIndexed { gi, g ->
            if (gi > 0) Box(Modifier.padding(horizontal = 14.dp, vertical = 2.dp).fillMaxWidth().height(0.5.dp).background(Color(0x33FFFFFF)))
            Text(g.header, color = Color(0x99FFFFFF), fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = if (compact) 6.dp else 8.dp, bottom = 1.dp))
            g.items.forEach { item ->
                Text(
                    item.label, color = if (item.accent) HyleColors.VioletSoft else Color.White, fontSize = if (compact) 14.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(item) }.padding(horizontal = 14.dp, vertical = if (compact) 5.dp else 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun SuggestionCard(groups: List<SuggGroup>, onPick: (SuggItem) -> Unit, modifier: Modifier = Modifier) {
    SuggestionGroups(
        groups, onPick,
        compact = true,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFA1F2024)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)).padding(bottom = 6.dp),
    )
}

// ---------------------------------------------------------------- tile faces

internal fun hitGlyph(hit: SearchHit): ImageVector = hit.actionId?.let { Glyphs.forAction(it) } ?: when (hit.kind) {
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
        } else {
            Icon(hitGlyph(hit), null, tint = if (hit.actionId != null) HyleColors.VioletSoft else Color(0xFFE7E9F0), modifier = Modifier.size(size * 0.46f))
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
        is Tile.Bang -> Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(Color(0xFF1D2A3E)), contentAlignment = Alignment.Center) {
            Text("!${tile.code}", color = Color(0xFF9CC4FF), fontSize = (size.value * 0.28f).sp, fontWeight = FontWeight.SemiBold)
        }
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
