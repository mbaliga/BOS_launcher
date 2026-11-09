package com.bos.sphere.feature.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** One row in a settings category. */
sealed interface SettingItem {
    val title: String
    val glyph: ImageVector
    val value: String

    /** Flips on tap, right in the list. */
    data class Toggle(override val title: String, override val glyph: ImageVector, val checked: Boolean, val hint: String? = null, val onChange: (Boolean) -> Unit) : SettingItem {
        override val value: String get() = if (checked) "On" else "Off"
    }

    /** Runs on tap (opens a dialog, adds a page …). */
    data class Action(override val title: String, override val glyph: ImageVector, override val value: String = "", val onClick: () -> Unit) : SettingItem

    /** Opens the side pane with its own controls. */
    class Pane(
        override val title: String,
        override val glyph: ImageVector,
        override val value: String,
        val content: @Composable ColumnScope.() -> Unit,
    ) : SettingItem
}

data class SettingCategory(val title: String, val glyph: ImageVector, val items: List<SettingItem>)

/**
 * The dedicated settings space — a cross-media-bar: category glyphs run along a horizontal
 * bar; the selected category's items run vertically beneath it; swipe sideways for
 * categories, up/down for items, tap to open. A slow wave field and the substrate dots sit
 * behind; every move clicks with Orrery's console tones ([UiSounds]).
 */
@Composable
fun SettingsSpace(
    visible: Boolean,
    categories: List<SettingCategory>,
    initialCategory: Int,
    sounds: UiSounds?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bump to reset the selection to [initialCategory] for a fresh visit. */
    session: Int = 0,
    /** What sits behind Settings (the dimmed wallpaper, or black). Null = the built-in violet gradient and waves. */
    backdrop: (@Composable () -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 1.06f, animationSpec = tween(260)),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 1.04f, animationSpec = tween(180)),
        modifier = modifier,
    ) {
        // Physical layout: positions and hit-tests below are computed in screen coordinates.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            LocalSettingsBackdrop provides backdrop,
        ) {
            androidx.compose.runtime.key(session) { SettingsSpaceContent(categories, initialCategory, sounds, onClose) }
        }
    }
}

@Composable
private fun SettingsSpaceContent(
    categories: List<SettingCategory>,
    initialCategory: Int,
    sounds: UiSounds?,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var cat by remember { mutableIntStateOf(initialCategory.coerceIn(0, (categories.size - 1).coerceAtLeast(0))) }
    // Options are all visible; the list scrolls (with fling) and a tap opens one.
    var scrollPx by remember { mutableFloatStateOf(0f) }
    var maxScroll by remember { mutableFloatStateOf(0f) }
    var paneOpen by remember { mutableIntStateOf(-1) } // index (in the displayed list) of the item whose pane is open
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val latestCategories by rememberUpdatedState(categories)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { sounds?.play(UiSound.Open) }

    /** What is listed: the category's options, or — while searching — matches across every category. */
    class Shown(val item: SettingItem, val origin: String?)
    fun shown(): List<Shown> {
        val q = query.trim()
        if (!searching || q.isEmpty()) return latestCategories.getOrNull(cat)?.items.orEmpty().map { Shown(it, null) }
        return latestCategories.flatMap { c ->
            c.items.filter { i -> i.title.contains(q, true) || i.value.contains(q, true) || c.title.contains(q, true) }.map { Shown(it, c.title) }
        }
    }

    fun moveCategory(to: Int) {
        val next = to.coerceIn(0, latestCategories.lastIndex)
        if (next == cat) { sounds?.play(UiSound.Nope); return }
        cat = next
        scrollPx = 0f
        paneOpen = -1
        sounds?.play(UiSound.Move)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun activate(index: Int) {
        val it = shown().getOrNull(index)?.item ?: return
        when (it) {
            is SettingItem.Toggle -> { it.onChange(!it.checked); sounds?.play(UiSound.Tick) }
            is SettingItem.Action -> { sounds?.play(UiSound.Select); it.onClick() }
            is SettingItem.Pane -> { paneOpen = index; sounds?.play(UiSound.Select) }
        }
    }

    fun closeSearch() { searching = false; query = ""; paneOpen = -1; scrollPx = 0f; keyboard?.hide() }

    BackHandler {
        when {
            paneOpen >= 0 -> { paneOpen = -1; sounds?.play(UiSound.Back) }
            searching -> { closeSearch(); sounds?.play(UiSound.Back) }
            else -> { sounds?.play(UiSound.Close); onClose() }
        }
    }

    val catAnim by animateFloatAsState(cat.toFloat(), spring(dampingRatio = 0.82f, stiffness = 320f), label = "cat")
    val scrollState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
        val n = (scrollPx - delta).coerceIn(0f, maxScroll)
        val used = scrollPx - n
        scrollPx = n
        used
    }

    val customBackdrop = LocalSettingsBackdrop.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (customBackdrop == null) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF0E0C22), Color(0xFF071622)))) else Modifier),
    ) {
        if (customBackdrop != null) customBackdrop()
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val landscape = w > h
        val catSpacing = with(density) { 104.dp.toPx() }
        val rowH = with(density) { 64.dp.toPx() }
        val barY = h * (if (landscape) 0.24f else 0.2f)
        val anchorX = w * (if (landscape) 0.24f else 0.3f)
        // Portrait needs more air between the category row and the options beneath it.
        val firstY = barY + with(density) { (if (landscape) 104.dp else 150.dp).toPx() }
        val list = shown()
        val paneItem = list.getOrNull(paneOpen)?.item as? SettingItem.Pane
        // In portrait the pane is a bottom sheet; the list scrolls within the space above it.
        val sheetFraction = 0.5f
        val visibleBottom = if (paneItem != null && !landscape) h * (1f - sheetFraction) else h
        maxScroll = ((list.size - 1) * rowH - (visibleBottom - firstY - rowH * 1.2f)).coerceAtLeast(0f)
        if (scrollPx > maxScroll) scrollPx = maxScroll

        if (customBackdrop == null) WaveField(Modifier.fillMaxSize())

        // Gestures: vertical scrolls the options (with fling), horizontal changes category, tap activates.
        var dragAcc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .scrollable(scrollState, androidx.compose.foundation.gestures.Orientation.Vertical)
                .pointerInput(cat, searching) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAcc = 0f },
                        onDragEnd = { dragAcc = 0f },
                        onDragCancel = { dragAcc = 0f },
                    ) { change, d ->
                        change.consume()
                        if (searching) return@detectHorizontalDragGestures
                        dragAcc += d
                        val step = 72.dp.toPx()
                        while (dragAcc <= -step) { dragAcc += step; moveCategory(cat + 1) }
                        while (dragAcc >= step) { dragAcc -= step; moveCategory(cat - 1) }
                    }
                }
                .pointerInput(w, h, catSpacing, rowH, firstY, searching) {
                    detectTapGestures { p ->
                        if (!searching && abs(p.y - barY) < 44.dp.toPx()) {
                            val idx = ((p.x - anchorX) / catSpacing + cat).roundToInt()
                            if (idx in latestCategories.indices) moveCategory(idx)
                            return@detectTapGestures
                        }
                        if (p.y > firstY - rowH / 2) {
                            val idx = ((p.y - firstY + scrollPx) / rowH).roundToInt()
                            if (idx in shown().indices) activate(idx)
                        }
                    }
                },
        )

        // Header: title (or the search field), search, close.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 22.dp, vertical = 14.dp).fillMaxWidth(),
        ) {
            if (searching) {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
                Box(Modifier.weight(1f).padding(end = 8.dp)) {
                    if (query.isEmpty()) Text(com.bos.sphere.core.design.L10n.tr("Search settings"), color = HyleColors.InkDim, fontSize = 15.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it; scrollPx = 0f; paneOpen = -1 },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = HyleColors.InkBright, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(HyleColors.Violet),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            } else {
                Text("SPHERE", color = HyleColors.InkBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                Text("  ·  SETTINGS", color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
            }
            Icon(
                Glyphs.Search,
                contentDescription = "Search settings",
                tint = if (searching) HyleColors.Violet else HyleColors.Ink,
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(searching) { detectTapGestures { if (searching) closeSearch() else { searching = true; paneOpen = -1 } } }
                    .padding(8.dp),
            )
            Icon(
                Glyphs.Close,
                contentDescription = "Close settings",
                tint = HyleColors.Ink,
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(Unit) { detectTapGestures { sounds?.play(UiSound.Close); onClose() } }
                    .padding(8.dp),
            )
        }

        // Category bar (hidden while searching).
        if (!searching) categories.forEachIndexed { i, c ->
            val d = i - catAnim
            val x = anchorX + d * catSpacing
            val sel = i == cat
            val scale = 1f + 0.38f * (1f - abs(d).coerceAtMost(1f))
            val glyphPx = with(density) { 34.dp.toPx() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset((x - glyphPx).roundToInt(), (barY - glyphPx / 2).roundToInt()) }
                    .width(with(density) { (glyphPx * 2).toDp() })
                    .alpha((1f - abs(d) * 0.22f).coerceIn(0.25f, 1f))
                    .semantics {
                        contentDescription = c.title
                        selected = sel
                        onClick { moveCategory(i); true }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (sel) Halo(56.dp)
                    Icon(
                        c.glyph,
                        null,
                        tint = if (sel) HyleColors.InkBright else HyleColors.Ink,
                        modifier = Modifier.size(34.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
                Text(
                    c.title,
                    color = HyleColors.InkBright,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 10.dp).alpha((1f - abs(d) * 2f).coerceIn(0f, 1f)),
                )
            }
        }

        // Options.
        val top = firstY - rowH * 0.9f
        val listTop = if (searching) with(density) { 80.dp.toPx() } else firstY
        list.forEachIndexed { i, row ->
            val it = row.item
            val y = listTop + i * rowH - scrollPx
            val rowTop = listTop - rowH * 0.9f
            if (y < rowTop || y > h + rowH) return@forEachIndexed
            val sel = i == paneOpen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset((anchorX - 20.dp.toPx()).roundToInt(), (y - rowH / 2).roundToInt()) }
                    .width(with(density) { ((w * 0.72f) - 10f).toDp() })
                    .alpha(((y - rowTop) / (rowH * 0.6f)).coerceIn(0f, 1f))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${it.title}, ${it.value}"
                        selected = sel
                        onClick { activate(i); true }
                    },
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (sel) Halo(44.dp)
                    Icon(it.glyph, null, tint = if (sel) HyleColors.InkBright else HyleColors.Ink, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        it.title,
                        color = HyleColors.InkBright,
                        fontSize = 16.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(row.origin, it.value.takeIf { v -> v.isNotEmpty() }).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            color = if (sel) HyleColors.VioletSoft else HyleColors.InkDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (searching && query.isNotBlank() && list.isEmpty()) {
            Text(
                com.bos.sphere.core.design.L10n.tr("No settings match “%s”", query),
                color = HyleColors.InkDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 24.dp, top = 96.dp).windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        // Option page: full screen, breadcrumbs on top.
        androidx.compose.animation.AnimatedVisibility(
            visible = paneItem != null,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 6 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { it / 6 },
        ) {
            val current = paneItem ?: return@AnimatedVisibility
            DetailScreen(
                crumbs = listOf(com.bos.sphere.core.design.L10n.tr("Settings"), categories.getOrNull(cat)?.title ?: "", current.title),
                onCrumb = { i -> sounds?.play(UiSound.Back); if (i == 0) onClose() else paneOpen = -1 },
            ) { current.content(this) }
        }
    }
}

/** The Settings backdrop slot, shared with the full-screen option pages. */
internal val LocalSettingsBackdrop = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

/** Distance from the category bar to the first item row (clears the category label). */
private val ITEMS_GAP = 104.dp

/** Soft violet glow behind the selected glyph. */
@Composable
private fun Halo(size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "halo")
    val pulse by t.animateFloat(0.18f, 0.34f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .size(size)
            .background(Brush.radialGradient(listOf(HyleColors.Violet.copy(alpha = pulse), Color.Transparent)), RoundedCornerShape(50)),
    )
}

/** Slowly drifting translucent ribbons — the "waves" behind a console menu, in Hyle colours. */
@Composable
internal fun WaveField(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "waves")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val ribbons = listOf(
            Triple(HyleColors.Violet, 0.58f, 0.0f),
            Triple(HyleColors.ProvenanceCloud, 0.64f, 0.33f),
            Triple(HyleColors.VioletSoft, 0.70f, 0.66f),
        )
        ribbons.forEach { (color, yFrac, offset) ->
            val path = Path()
            val baseY = size.height * yFrac
            val amp = size.height * 0.07f
            val steps = 48
            for (i in 0..steps) {
                val x = size.width * i / steps
                val a = 2 * PI * (i.toDouble() / steps * 1.3 + phase + offset)
                val y = baseY + amp * sin(a).toFloat() + amp * 0.4f * sin(2.1 * a + offset * 5).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.16f), style = Stroke(width = 1.4.dp.toPx()))
            val band = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(band, Brush.verticalGradient(listOf(color.copy(alpha = 0.05f), Color.Transparent), startY = baseY - amp, endY = size.height))
        }
        // Faint dot lattice.
        val spacing = 26.dp.toPx()
        var y = spacing / 2
        while (y < size.height) {
            var x = spacing / 2
            while (x < size.width) {
                drawCircle(HyleColors.InkDim.copy(alpha = 0.08f), radius = 1.dp.toPx(), center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}
