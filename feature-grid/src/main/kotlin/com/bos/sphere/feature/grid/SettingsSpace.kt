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
    val itemIndex = remember { mutableMapOf<Int, Int>() }
    var item by remember { mutableIntStateOf(0) }
    var paneOpen by remember { mutableIntStateOf(-1) } // index of the item whose pane is open
    val latestCategories by rememberUpdatedState(categories)

    LaunchedEffect(Unit) { sounds?.play(UiSound.Open) }

    fun items() = latestCategories.getOrNull(cat)?.items.orEmpty()

    fun moveCategory(to: Int) {
        val next = to.coerceIn(0, latestCategories.lastIndex)
        if (next == cat) { sounds?.play(UiSound.Nope); return }
        itemIndex[cat] = item
        cat = next
        item = (itemIndex[next] ?: 0).coerceIn(0, (items().size - 1).coerceAtLeast(0))
        paneOpen = -1
        sounds?.play(UiSound.Move)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun moveItem(to: Int) {
        val next = to.coerceIn(0, (items().size - 1).coerceAtLeast(0))
        if (next == item) return
        item = next
        paneOpen = -1
        sounds?.play(UiSound.Hover)
    }

    fun activate(index: Int) {
        val it = items().getOrNull(index) ?: return
        if (index != item) { moveItem(index); return }
        when (it) {
            is SettingItem.Toggle -> { it.onChange(!it.checked); sounds?.play(UiSound.Tick) }
            is SettingItem.Action -> { sounds?.play(UiSound.Select); it.onClick() }
            is SettingItem.Pane -> { paneOpen = index; sounds?.play(UiSound.Select) }
        }
    }

    BackHandler {
        if (paneOpen >= 0) { paneOpen = -1; sounds?.play(UiSound.Back) } else { sounds?.play(UiSound.Close); onClose() }
    }

    val catAnim by animateFloatAsState(cat.toFloat(), spring(dampingRatio = 0.82f, stiffness = 320f), label = "cat")
    val itemAnim by animateFloatAsState(item.toFloat(), spring(dampingRatio = 0.85f, stiffness = 360f), label = "item")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF0E0C22), Color(0xFF071622)))),
    ) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val landscape = w > h
        val catSpacing = with(density) { 104.dp.toPx() }
        val rowH = with(density) { 64.dp.toPx() }
        val barY = h * (if (landscape) 0.24f else 0.22f)
        val anchorX = w * (if (landscape) 0.24f else 0.3f)

        WaveField(Modifier.fillMaxSize())

        // Gestures: horizontal → categories, vertical → items, tap → activate.
        var dragAcc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
                    detectDragGestures(
                        onDragStart = { axis = 0; dragAcc = 0f },
                        onDragEnd = { axis = 0; dragAcc = 0f },
                        onDragCancel = { axis = 0; dragAcc = 0f },
                    ) { change, d ->
                        change.consume()
                        if (axis == 0) axis = if (abs(d.x) > abs(d.y)) 1 else 2
                        if (axis == 1) {
                            dragAcc += d.x
                            val step = 72.dp.toPx()
                            while (dragAcc <= -step) { dragAcc += step; moveCategory(cat + 1) }
                            while (dragAcc >= step) { dragAcc -= step; moveCategory(cat - 1) }
                        } else {
                            dragAcc += d.y
                            val step = 52.dp.toPx()
                            while (dragAcc <= -step) { dragAcc += step; moveItem(item + 1) }
                            while (dragAcc >= step) { dragAcc -= step; moveItem(item - 1) }
                        }
                    }
                }
                .pointerInput(w, h, catSpacing, rowH) {
                    detectTapGestures { p ->
                        // Category bar hit?
                        if (abs(p.y - barY) < 44.dp.toPx()) {
                            val idx = ((p.x - anchorX) / catSpacing + cat).roundToInt()
                            if (idx in latestCategories.indices) moveCategory(idx)
                            return@detectTapGestures
                        }
                        val firstY = barY + ITEMS_GAP.toPx()
                        if (p.y > firstY - rowH / 2) {
                            val idx = ((p.y - firstY) / rowH + item).roundToInt()
                            if (idx in items().indices && p.x < (if (paneOpen >= 0) w * 0.52f else w)) activate(idx)
                        }
                    }
                },
        )

        // Header.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 22.dp, vertical = 14.dp).fillMaxWidth(),
        ) {
            Text("SPHERE", color = HyleColors.InkBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            Text("  ·  SETTINGS", color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
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

        // Category bar.
        categories.forEachIndexed { i, c ->
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

        // Item column under the selected category.
        val list = items()
        val firstY = barY + with(density) { ITEMS_GAP.toPx() }
        list.forEachIndexed { i, it ->
            val d = i - itemAnim
            val y = firstY + d * rowH
            if (d < -0.6f || y > h + rowH) return@forEachIndexed
            val sel = i == item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset((anchorX - 20.dp.toPx()).roundToInt(), (y - rowH / 2).roundToInt()) }
                    .width(with(density) { ((if (paneOpen >= 0) w * 0.5f else w * 0.72f) - 10f).toDp() })
                    .alpha(if (d < 0) (1f + d / 0.6f).coerceIn(0f, 1f) else (1f - d * 0.12f).coerceIn(0.3f, 1f))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${it.title}, ${it.value}"
                        selected = sel
                        onClick { item = i; activate(i); true }
                    },
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (sel) Halo(44.dp)
                    Icon(it.glyph, null, tint = if (sel) HyleColors.InkBright else HyleColors.InkDim, modifier = Modifier.size(if (sel) 26.dp else 22.dp))
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        it.title,
                        color = if (sel) HyleColors.InkBright else HyleColors.Ink,
                        fontSize = if (sel) 17.sp else 15.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (it.value.isNotEmpty()) {
                        Text(
                            it.value,
                            color = if (sel) HyleColors.VioletSoft else HyleColors.InkDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Footer hint.
        Text(
            "◀ ▶ categories   ▲ ▼ items   tap to open   back to leave",
            color = HyleColors.InkDim.copy(alpha = 0.7f),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 12.dp),
        )

        // Option pane.
        val pane = list.getOrNull(paneOpen) as? SettingItem.Pane
        AnimatedVisibility(
            visible = pane != null,
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = slideOutHorizontally { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val current = pane ?: return@AnimatedVisibility
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .width(with(density) { (w * 0.46f).toDp() }.coerceAtMost(420.dp))
                    .fillMaxHeight(0.86f)
                    .background(HyleColors.SurfaceTranslucent.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
                    .border(1.dp, Brush.verticalGradient(listOf(HyleColors.Violet.copy(alpha = 0.6f), HyleColors.Hairline)), RoundedCornerShape(18.dp))
                    .pointerInput(Unit) { detectTapGestures { } } // keep taps inside the pane
                    .padding(16.dp),
            ) {
                Text(current.title.uppercase(), color = HyleColors.InkBright, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    current.content(this)
                }
            }
        }
    }
}

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
