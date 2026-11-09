package com.bos.sphere.feature.grid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlin.math.ceil

enum class ContinuousStyle { Plain, Dsi }

/** Pure sizing / scroll maths behind [ContinuousHome] (unit tested). Everything in one unit (px or dp). */
internal object ContinuousMath {
    /** Cross-axis cell: `crossCount` cells always exactly span the short axis. */
    fun cell(crossLength: Float, crossCount: Int): Float = crossLength / crossCount.coerceAtLeast(1)

    /** Icon (Plain) or tile plate (Dsi) edge. Capped so icon + [labelRoom] still fit the [cell] when [capToCell]. */
    fun iconSize(cell: Float, scale: Float, labelRoom: Float, capToCell: Boolean): Float {
        val s = cell * scale.coerceIn(0.2f, 1f)
        return if (capToCell) s.coerceAtMost((cell - labelRoom - 2f).coerceAtLeast(cell * 0.3f)) else s
    }

    /** Length of one item along the scroll axis; [spacing] 0..1 widens the gap between rows/columns. */
    fun mainExtent(icon: Float, cell: Float, spacing: Float, labelRoom: Float): Float =
        icon + labelRoom + cell * 0.08f + cell * 0.3f * spacing.coerceIn(0f, 1f)

    fun rows(count: Int, crossCount: Int): Int = if (count <= 0) 0 else ceil(count / crossCount.coerceAtLeast(1).toFloat()).toInt()

    /** 0 at the start, 1 at the end of the scroll range. */
    fun scrollFraction(firstIndex: Int, firstOffset: Float, crossCount: Int, extent: Float, count: Int, viewport: Float, padBefore: Float, padAfter: Float): Float {
        val content = rows(count, crossCount) * extent + padBefore + padAfter
        val range = content - viewport
        if (range <= 0.5f) return 0f
        val scrolled = (firstIndex / crossCount.coerceAtLeast(1)) * extent + firstOffset
        return (scrolled / range).coerceIn(0f, 1f)
    }

    /** Thumb length as a fraction of the track (viewport / content), never thinner than [min]. */
    fun thumbFraction(count: Int, crossCount: Int, extent: Float, viewport: Float, padBefore: Float, padAfter: Float, min: Float = 0.08f): Float {
        val content = rows(count, crossCount) * extent + padBefore + padAfter
        if (content <= viewport || content <= 0f) return 1f
        return (viewport / content).coerceIn(min, 1f)
    }
}

private val LabelRoomPlain = 18.dp
private val LabelRoomDsi = 26.dp

/**
 * A continuous (non-paged) scrolling home. Portrait: a vertical grid of [crossCount] columns;
 * landscape: a horizontal grid of [crossCount] rows — so [crossCount] icons always span the
 * short axis (cell = short axis / crossCount, icon = cell * [iconScale]). [ContinuousStyle.Dsi]
 * puts each icon on a glassy rounded tile with a caption pill and draws a thin scroll bar.
 */
@Composable
fun ContinuousHome(
    apps: List<AppEntry>,
    crossCount: Int,
    landscape: Boolean,
    style: ContinuousStyle,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconScale: Float = 0.8f,
    spacing: Float = 0.5f,
    showLabels: Boolean = true,
    sounds: UiSounds? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ContinuousImpl(apps, crossCount.coerceAtLeast(1), landscape, style, onLaunch, onLongPress, modifier, contentPadding, iconScale, spacing, showLabels, sounds)
    }
}

@Composable
private fun ContinuousImpl(
    apps: List<AppEntry>,
    crossCount: Int,
    landscape: Boolean,
    style: ContinuousStyle,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    iconScale: Float,
    spacing: Float,
    showLabels: Boolean,
    sounds: UiSounds?,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestLong by rememberUpdatedState(onLongPress)
    val latestSounds by rememberUpdatedState(sounds)
    val gridState = rememberLazyGridState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val padL = contentPadding.calculateLeftPadding(LayoutDirection.Ltr)
        val padR = contentPadding.calculateRightPadding(LayoutDirection.Ltr)
        val padT = contentPadding.calculateTopPadding()
        val padB = contentPadding.calculateBottomPadding()
        val cross: Dp = if (landscape) maxHeight - padT - padB else maxWidth - padL - padR
        val cell = ContinuousMath.cell(cross.value, crossCount)
        val dsi = style == ContinuousStyle.Dsi
        val labelRoom = if (!showLabels) 0f else (if (dsi) LabelRoomDsi else LabelRoomPlain).value
        val plate = ContinuousMath.iconSize(cell, if (dsi) minOf(0.98f, iconScale + 0.14f) else iconScale, labelRoom, capToCell = landscape)
        val extent = ContinuousMath.mainExtent(plate, cell, spacing, labelRoom)
        val extentDp = extent.dp

        val onTap: (AppEntry) -> Unit = remember {
            { app -> latestSounds?.play(UiSound.Select); latestLaunch(app) }
        }
        val onLong: (AppEntry) -> Unit = remember(haptics) {
            { app -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); latestLong(app) }
        }

        val fadeStart = with(density) { (if (landscape) padL else padT).toPx() }
        val fadeEnd = with(density) { (if (landscape) padR else padB).toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .edgeFades(landscape, fadeStart, fadeEnd, with(density) { 14.dp.toPx() })
        ) {
            if (!landscape) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(crossCount),
                    state = gridState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(apps, key = { it.key }, contentType = { "app" }) { app ->
                        ContinuousCell(app, dsi, plate.dp, showLabels, onTap, onLong, Modifier.fillMaxWidth().height(extentDp))
                    }
                }
            } else {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(crossCount),
                    state = gridState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(apps, key = { it.key }, contentType = { "app" }) { app ->
                        ContinuousCell(app, dsi, plate.dp, showLabels, onTap, onLong, Modifier.fillMaxHeight().width(extentDp))
                    }
                }
            }
        }
        if (dsi) {
            DsiScrollBar(
                state = gridState, landscape = landscape, count = apps.size, crossCount = crossCount,
                extentPx = with(density) { extent.dp.toPx() },
                padBeforePx = with(density) { (if (landscape) padL else padT).toPx() },
                padAfterPx = with(density) { (if (landscape) padR else padB).toPx() },
                insetBeforePx = with(density) { (if (landscape) padL else padT).toPx() + 10.dp.toPx() },
                insetAfterPx = with(density) { (if (landscape) padR else padB).toPx() + 10.dp.toPx() },
                crossInsetPx = with(density) { 2.dp.toPx() },
            )
        }
    }
}

/** Fades both ends of the scroll axis (under the status strip / corners). */
private fun Modifier.edgeFades(landscape: Boolean, startPad: Float, endPad: Float, minFade: Float): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val len = if (landscape) size.width else size.height
            val a = maxOf(startPad - 2f, minFade).coerceAtMost(len / 3f)
            val b = maxOf(endPad - 2f, minFade).coerceAtMost(len / 3f)
            val startBrush: Brush
            val endBrush: Brush
            if (landscape) {
                startBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, a)
                endBrush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - b, size.width)
            } else {
                startBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, a)
                endBrush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - b, size.height)
            }
            val startSize = if (landscape) Size(a, size.height) else Size(size.width, a)
            val endTopLeft = if (landscape) Offset(size.width - b, 0f) else Offset(0f, size.height - b)
            val endSize = if (landscape) Size(b, size.height) else Size(size.width, b)
            onDrawWithContent {
                drawContent()
                drawRect(startBrush, size = startSize, blendMode = BlendMode.DstIn)
                drawRect(endBrush, topLeft = endTopLeft, size = endSize, blendMode = BlendMode.DstIn)
            }
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuousCell(
    app: AppEntry,
    dsi: Boolean,
    plate: Dp,
    showLabels: Boolean,
    onTap: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale: State<Float> = animateFloatAsState(if (pressed && dsi) 0.92f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")
    Column(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .semantics { contentDescription = app.label }
            .combinedClickable(interaction, null, onClick = { onTap(app) }, onLongClick = { onLong(app) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (dsi) {
            DsiTile(app, plate)
            if (showLabels) {
                Text(
                    app.label,
                    color = Color(0xFF3B4150), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .widthIn(max = plate)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.88f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            AppIcon(app, plate)
            if (showLabels) {
                Text(
                    app.label, color = HyleColors.Ink, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 4f)),
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = plate + 8.dp),
                )
            }
        }
    }
}

/** A large soft rounded plate (light glass, bottom drop shadow, thin highlight) carrying the icon. */
@Composable
private fun DsiTile(app: AppEntry, plate: Dp) {
    Box(
        Modifier
            .size(plate)
            .drawWithCache {
                val r = size.minDimension * 0.22f
                val corner = CornerRadius(r)
                val body = Brush.verticalGradient(listOf(Color(0xFFFAFBFD), Color(0xFFD5DAE3)))
                val gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.0f)), 0f, size.height * 0.5f)
                val edge = Brush.verticalGradient(listOf(Color.White, Color(0xFFB9C0CC)))
                val sw = 1.2.dp.toPx()
                val glossSize = Size(size.width - sw * 2, size.height * 0.5f)
                val shadowDy = 3.dp.toPx()
                val shadowSteps = 4
                val shadowGrow = 1.5.dp.toPx()
                onDrawBehind {
                    // Soft drop shadow: stacked, growing, fading plates pushed down.
                    for (k in shadowSteps downTo 1) {
                        val g = shadowGrow * k
                        drawRoundRect(
                            Color.Black.copy(alpha = 0.075f),
                            Offset(-g, shadowDy - g + g * 0.6f), Size(size.width + 2 * g, size.height + 2 * g),
                            CornerRadius(r + g),
                        )
                    }
                    drawRoundRect(body, size = size, cornerRadius = corner)
                    drawRoundRect(gloss, Offset(sw, sw), glossSize, CornerRadius(r - sw))
                    drawRoundRect(edge, size = size, cornerRadius = corner, style = Stroke(sw))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(app, plate * 0.72f)
    }
}

/** Thin DSi-style position bar on the trailing edge (bottom edge in landscape). */
@Composable
private fun DsiScrollBar(
    state: LazyGridState,
    landscape: Boolean,
    count: Int,
    crossCount: Int,
    extentPx: Float,
    padBeforePx: Float,
    padAfterPx: Float,
    insetBeforePx: Float,
    insetAfterPx: Float,
    crossInsetPx: Float,
) {
    val alpha: State<Float> = animateFloatAsState(if (state.isScrollInProgress) 0.95f else 0.5f, tween(250), label = "bar")
    Canvas(Modifier.fillMaxSize()) {
        val viewport = if (landscape) size.width else size.height
        val content = ContinuousMath.rows(count, crossCount) * extentPx + padBeforePx + padAfterPx
        if (content <= viewport + 0.5f) return@Canvas
        val frac = ContinuousMath.scrollFraction(
            state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset.toFloat(), crossCount, extentPx, count, viewport, padBeforePx, padAfterPx,
        )
        val thick = 3.dp.toPx()
        val trackLen = (viewport - insetBeforePx - insetAfterPx).coerceAtLeast(1f)
        val thumbLen = (trackLen * ContinuousMath.thumbFraction(count, crossCount, extentPx, viewport, padBeforePx, padAfterPx, 0.1f))
            .coerceAtLeast(thick * 3)
        val thumbPos = insetBeforePx + (trackLen - thumbLen) * frac
        val a = alpha.value
        val trackColor = Color.White.copy(alpha = 0.22f * a)
        val thumbColor = Color.White.copy(alpha = a)
        val rad = CornerRadius(thick / 2)
        if (landscape) {
            val y = size.height - crossInsetPx - thick
            drawRoundRect(trackColor, Offset(insetBeforePx, y), Size(trackLen, thick), rad)
            drawRoundRect(thumbColor, Offset(thumbPos, y), Size(thumbLen, thick), rad)
        } else {
            val x = size.width - crossInsetPx - thick
            drawRoundRect(trackColor, Offset(x, insetBeforePx), Size(thick, trackLen), rad)
            drawRoundRect(thumbColor, Offset(x, thumbPos), Size(thick, thumbLen), rad)
        }
    }
}
