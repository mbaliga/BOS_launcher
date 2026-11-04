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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
 * The dedicated settings space. Categories run along a bar (tap one, or swipe sideways); the
 * selected category's options are all visible in a list beneath it — tapping an option flips it
 * (toggles), runs it (actions) or opens its configuration pane. A slow wave field and dot lattice
 * sit behind, and every interaction clicks with Orrery's console tones ([UiSounds]).
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
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        // Physical layout: positions are computed in screen coordinates.
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
    var paneItem by remember { mutableIntStateOf(-1) } // index of the item whose pane is open
    val latest by rememberUpdatedState(categories)
    val barState = androidx.compose.foundation.rememberScrollState()

    LaunchedEffect(Unit) { sounds?.play(UiSound.Open) }

    fun select(to: Int) {
        val next = to.coerceIn(0, latest.lastIndex)
        if (next == cat) return
        cat = next
        paneItem = -1
        sounds?.play(UiSound.Move)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun leave() { sounds?.play(UiSound.Close); onClose() }

    BackHandler {
        if (paneItem >= 0) { paneItem = -1; sounds?.play(UiSound.Back) } else leave()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF0E0C22), Color(0xFF071622)))),
    ) {
        val landscape = maxWidth > maxHeight
        WaveField(Modifier.fillMaxSize())

        val items = latest.getOrNull(cat)?.items.orEmpty()
        val pane = items.getOrNull(paneItem) as? SettingItem.Pane
        // Portrait: the pane is a bottom sheet; landscape: a side panel.
        val listEndFraction = if (landscape && pane != null) 0.5f else 1f

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp).fillMaxWidth(),
            ) {
                Text("SPHERE", color = HyleColors.InkBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                Text("  ·  SETTINGS", color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
                Icon(
                    Glyphs.Close,
                    contentDescription = "Close settings",
                    tint = HyleColors.Ink,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) { detectTapGestures { leave() } }
                        .padding(10.dp),
                )
            }

            // Category bar: every category visible (scrolls if there are more than fit).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(barState)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                latest.forEachIndexed { i, c ->
                    val sel = i == cat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .pointerInput(i) { detectTapGestures { select(i) } }
                            .padding(vertical = 6.dp)
                            .semantics { contentDescription = c.title; selected = sel; onClick { select(i); true } },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (sel) Halo(52.dp)
                            Icon(
                                c.glyph, null,
                                tint = if (sel) HyleColors.InkBright else HyleColors.InkDim,
                                modifier = Modifier.size(if (sel) 30.dp else 26.dp),
                            )
                        }
                        Text(
                            c.title,
                            color = if (sel) HyleColors.InkBright else HyleColors.InkDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .height(2.dp)
                                .width(if (sel) 22.dp else 0.dp)
                                .background(HyleColors.Violet, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
            // Keep the selected category in view when the bar scrolls.
            val catWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 78.dp.toPx() }
            LaunchedEffect(cat) {
                barState.animateScrollTo(((cat * catWidthPx) - barState.viewportSize / 2 + catWidthPx / 2).toInt().coerceAtLeast(0))
            }

            // Options: all visible; tap to open. Horizontal swipes change category.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(listEndFraction.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .pointerInput(cat) {
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = { if (dx < -90f) select(cat + 1) else if (dx > 90f) select(cat - 1) },
                            ) { _, d -> dx += d }
                        },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = if (pane != null && !landscape) 8.dp else 24.dp,
                    ),
                ) {
                    itemsIndexed(items) { i, it ->
                        SettingRow(
                            item = it,
                            selected = i == paneItem,
                            onClick = {
                                when (it) {
                                    is SettingItem.Toggle -> { it.onChange(!it.checked); sounds?.play(UiSound.Tick) }
                                    is SettingItem.Action -> { sounds?.play(UiSound.Select); it.onClick() }
                                    is SettingItem.Pane -> {
                                        paneItem = if (paneItem == i) -1 else i
                                        sounds?.play(UiSound.Select)
                                    }
                                }
                            },
                        )
                    }
                }
                if (landscape && pane != null) {
                    PanePanel(pane, onClose = { paneItem = -1; sounds?.play(UiSound.Back) }, modifier = Modifier.weight(0.5f).fillMaxHeight())
                }
            }
            if (!landscape && pane != null) {
                PanePanel(
                    pane,
                    onClose = { paneItem = -1; sounds?.play(UiSound.Back) },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))
            .pointerInput(item) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.title}, ${item.value}"
                onClick { onClick(); true }
            },
    ) {
        Icon(item.glyph, null, tint = if (selected) HyleColors.InkBright else HyleColors.Ink, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(item.title, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when (item) {
                is SettingItem.Toggle -> item.hint
                else -> item.value.takeIf { it.isNotEmpty() }
            }
            if (sub != null) Text(sub, color = HyleColors.InkDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (item) {
            is SettingItem.Toggle -> Box(
                Modifier
                    .size(width = 40.dp, height = 22.dp)
                    .background(if (item.checked) HyleColors.Violet else HyleColors.Surface, RoundedCornerShape(11.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(11.dp)),
                contentAlignment = if (item.checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.padding(3.dp).size(16.dp).background(HyleColors.InkBright, androidx.compose.foundation.shape.CircleShape)) }
            is SettingItem.Pane -> Text("›", color = HyleColors.InkDim, fontSize = 20.sp)
            is SettingItem.Action -> Unit
        }
    }
}

@Composable
private fun PanePanel(pane: SettingItem.Pane, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(10.dp)
            .background(HyleColors.SurfaceTranslucent.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .border(1.dp, Brush.verticalGradient(listOf(HyleColors.Violet.copy(alpha = 0.55f), HyleColors.Hairline)), RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pane.title.uppercase(), color = HyleColors.InkBright, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Glyphs.Close, "Close", tint = HyleColors.InkDim, modifier = Modifier.size(32.dp).pointerInput(Unit) { detectTapGestures { onClose() } }.padding(7.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            pane.content(this)
        }
    }
}

/** Distance from the category bar to the first item row (clears the category label). */
private val ITEMS_GAP = 104.dp // unused now; kept for reference

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
