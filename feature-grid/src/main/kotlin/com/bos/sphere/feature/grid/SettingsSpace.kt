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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.draw.drawBehind
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

/** One row in a settings category. Everything simple is edited right on the row; only [Pane] opens a page. */
sealed interface SettingItem {
    val title: String
    val glyph: ImageVector
    val value: String

    /** A switch on the row; tapping anywhere on the row flips it. */
    data class Toggle(override val title: String, override val glyph: ImageVector, val checked: Boolean, val hint: String? = null, val onChange: (Boolean) -> Unit) : SettingItem {
        override val value: String get() = if (checked) "On" else "Off"
    }

    /** Title and current value on one line, the slider beneath. [step] 0 = automatic (1% for fractions, whole numbers otherwise). */
    class Slider(
        override val title: String,
        override val glyph: ImageVector,
        val amount: Float,
        val range: ClosedFloatingPointRange<Float>,
        val valueText: String,
        val hint: String? = null,
        val step: Float = 0f,
        val onChange: (Float) -> Unit,
    ) : SettingItem {
        override val value: String get() = valueText
    }

    /** The current value on the row with up / down arrows that step through [options] (wrapping); more than 6 options open a menu. */
    class Choice<T>(
        override val title: String,
        override val glyph: ImageVector,
        val options: List<T>,
        val selected: T,
        val label: (T) -> String,
        val hint: String? = null,
        /** When set, a "Preview…" link on the row opens this as a full page (the visual picker). */
        val preview: (@Composable ColumnScope.() -> Unit)? = null,
        val onSelect: (T) -> Unit,
    ) : SettingItem {
        override val value: String get() = label(selected)
    }

    /** − value + for small whole numbers. */
    data class Stepper(override val title: String, override val glyph: ImageVector, val count: Int, val range: IntRange, val hint: String? = null, val onChange: (Int) -> Unit) : SettingItem {
        override val value: String get() = count.toString()
    }

    /** Runs on tap (opens a dialog, adds a page …). */
    data class Action(override val title: String, override val glyph: ImageVector, override val value: String = "", val navigates: Boolean = false, val onClick: () -> Unit) : SettingItem

    /** Opens a full-screen page with its own controls; the row shows the current value and a chevron. */
    class Pane(
        override val title: String,
        override val glyph: ImageVector,
        override val value: String,
        val content: @Composable ColumnScope.() -> Unit,
    ) : SettingItem
}

data class SettingCategory(val title: String, val glyph: ImageVector, val items: List<SettingItem>)

/**
 * The dedicated settings space ("Ground Control"): a header, a strip of category tabs (every one
 * shows its icon and its name), and a full-width list whose rows carry their own controls —
 * switches, sliders, value steppers — so most things change without leaving the list. Swipe
 * sideways on the list for the next category. Every change clicks with Orrery's console tones
 * ([UiSounds]) and a haptic tick.
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
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            LocalSettingsBackdrop provides backdrop,
        ) {
            androidx.compose.runtime.key(session) { SettingsSpaceContent(categories, initialCategory, sounds, onClose) }
        }
    }
}

/** The full-screen page a row opens, if it has one. */
private fun SettingItem.detail(): (@Composable ColumnScope.() -> Unit)? = when (this) {
    is SettingItem.Pane -> content
    is SettingItem.Choice<*> -> preview
    else -> null
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
    var paneOpen by remember { mutableIntStateOf(-1) } // index (in the displayed list) of the item whose page is open
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
        paneOpen = -1
        sounds?.play(UiSound.Move)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun openDetail(index: Int) { paneOpen = index; sounds?.play(UiSound.Select) }
    fun tick() { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    fun closeSearch() { searching = false; query = ""; paneOpen = -1; keyboard?.hide() }

    BackHandler {
        when {
            paneOpen >= 0 -> { paneOpen = -1; sounds?.play(UiSound.Back) }
            searching -> { closeSearch(); sounds?.play(UiSound.Back) }
            else -> { sounds?.play(UiSound.Close); onClose() }
        }
    }

    val customBackdrop = LocalSettingsBackdrop.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (customBackdrop == null) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF0E0C22), Color(0xFF071622)))) else Modifier),
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        if (customBackdrop != null) customBackdrop()
        if (customBackdrop == null) WaveField(Modifier.fillMaxSize())
        val list = shown()
        val detailItem = list.getOrNull(paneOpen)?.item
        val detail = detailItem?.detail()

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Header: title (or the search field), search, close.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth()) {
                if (searching) {
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
                    Box(Modifier.weight(1f).padding(end = 8.dp)) {
                        if (query.isEmpty()) Text(com.bos.sphere.core.design.L10n.tr("Search settings"), color = HyleColors.InkDim, fontSize = 15.sp)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it; paneOpen = -1 },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = HyleColors.InkBright, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(HyleColors.Violet),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                } else {
                    Text(com.bos.sphere.core.design.L10n.tr("GROUND CONTROL"), color = HyleColors.InkBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
                }
                Icon(
                    Glyphs.Search,
                    contentDescription = "Search settings",
                    tint = if (searching) HyleColors.Violet else HyleColors.Ink,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(searching) { detectTapGestures { if (searching) closeSearch() else { searching = true; paneOpen = -1 } } }
                        .padding(9.dp),
                )
                Icon(
                    Glyphs.Close,
                    contentDescription = "Close settings",
                    tint = HyleColors.Ink,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) { detectTapGestures { sounds?.play(UiSound.Close); onClose() } }
                        .padding(9.dp),
                )
            }

            if (!searching) CategoryTabs(categories, cat, ::moveCategory)

            // The list: full width, swipe sideways for the neighbouring category.
            val listState = rememberLazyListState()
            LaunchedEffect(cat, query, searching) { listState.scrollToItem(0) }
            var dragAcc by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(cat, searching) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAcc = 0f },
                            onDragEnd = { dragAcc = 0f },
                            onDragCancel = { dragAcc = 0f },
                        ) { change, d ->
                            change.consume()
                            if (searching) return@detectHorizontalDragGestures
                            dragAcc += d
                            val step = 96.dp.toPx()
                            if (dragAcc <= -step) { dragAcc = 0f; moveCategory(cat + 1) }
                            else if (dragAcc >= step) { dragAcc = 0f; moveCategory(cat - 1) }
                        }
                    },
            ) {
                // The original placement: the list hangs under the lit category (about 30% in on a phone held
                // upright, a little less in landscape), with the labels flowing to its right.
                val anchor = (boxW * (if (boxW > boxH) 0.2f else 0.28f)) - 15.dp
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = anchor.coerceAtLeast(14.dp), end = 20.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { i, s -> "${s.origin}|$i|${s.item.title}" }) { i, s ->
                        Column {
                            if (s.origin != null) Text(s.origin.uppercase(), color = HyleColors.InkDim, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
                            when (val it = s.item) {
                                is SettingItem.Toggle -> ToggleSettingRow(it) { v -> it.onChange(v); sounds?.play(UiSound.Tick); tick() }
                                is SettingItem.Slider -> SliderSettingRow(it, ::tick)
                                is SettingItem.Stepper -> StepperSettingRow(it) { sounds?.play(UiSound.Tick); tick() }
                                is SettingItem.Choice<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    ChoiceSettingRow(it as SettingItem.Choice<Any?>, { sounds?.play(UiSound.Tick); tick() }, { openDetail(i) })
                                }
                                is SettingItem.Action -> NavigationSettingRow(it, chevron = it.navigates, highlighted = false, onClick = { sounds?.play(UiSound.Select); it.onClick() })
                                is SettingItem.Pane -> NavigationSettingRow(it, chevron = true, highlighted = i == paneOpen, onClick = { openDetail(i) })
                            }
                        }
                    }
                }
                if (searching && query.isNotBlank() && list.isEmpty()) {
                    Text(
                        com.bos.sphere.core.design.L10n.tr("No settings match “%s”", query),
                        color = HyleColors.InkDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 22.dp, top = 24.dp),
                    )
                }
            }
        }

        // Option page: full screen, breadcrumbs on top.
        androidx.compose.animation.AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 6 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { it / 6 },
        ) {
            val current = detail ?: return@AnimatedVisibility
            DetailScreen(
                crumbs = listOf(com.bos.sphere.core.design.L10n.tr("Ground Control"), categories.getOrNull(cat)?.title ?: "", detailItem?.title ?: ""),
                onCrumb = { i -> sounds?.play(UiSound.Back); if (i == 0) onClose() else paneOpen = -1 },
            ) { current(this) }
        }
    }
}

/** Horizontally scrolling category tabs: every one shows its icon and its name; the selected one is lit and underlined. */
@Composable
private fun CategoryTabs(categories: List<SettingCategory>, selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(categories, key = { i, c -> "$i|${c.title}" }) { i, c ->
            val sel = i == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onSelect(i) }
                    .drawBehind {
                        // The soft glow behind the lit category (as in the original bar).
                        if (sel) drawCircle(Brush.radialGradient(listOf(HyleColors.Violet.copy(alpha = 0.38f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.34f), radius = size.width * 0.7f), radius = size.width * 0.7f, center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.34f))
                    }
                    .semantics { contentDescription = c.title; this.selected = sel }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(c.glyph, null, tint = if (sel) HyleColors.InkBright else HyleColors.Ink.copy(alpha = 0.72f), modifier = Modifier.size(24.dp))
                Text(
                    c.title,
                    color = if (sel) HyleColors.InkBright else HyleColors.Ink.copy(alpha = 0.72f),
                    fontSize = 11.5.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 4.dp),
                )

            }
        }
    }
}

/** The Settings backdrop slot, shared with the full-screen option pages. */
internal val LocalSettingsBackdrop = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }


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
