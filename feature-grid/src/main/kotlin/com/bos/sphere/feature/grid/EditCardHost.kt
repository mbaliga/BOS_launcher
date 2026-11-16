package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay

/** The edit card's target for the current window: banner room above, the edit bar below, RTL mirrored (HOM-12, INP). */
@Composable
fun rememberEditCardTarget(side: CardSide? = null): EditCardGeometry.Target {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = WindowInsets.safeDrawing.getTop(d)
    val bottom = WindowInsets.safeDrawing.getBottom(d)
    return remember(cfg.screenWidthDp, cfg.screenHeightDp, d.density, rtl, top, bottom, side) {
        EditCardGeometry.target(
            cfg.screenWidthDp * d.density, cfg.screenHeightDp * d.density, rtl,
            topReservePx = top + 52f * d.density, bottomReservePx = bottom + 68f * d.density, addBandPx = (if (cfg.screenWidthDp > cfg.screenHeightDp) 136f else 132f) * d.density, side = side,
        )
    }
}

/** A line of feedback with an Undo button (page deleted). */
class UndoNotice(val message: String, val onUndo: () -> Unit, val id: Long = System.nanoTime())

/**
 * HOM-12 / HOM-01: everything that lives in the background while the home is a card: a quiet gradient, the
 * [PageCarousel] in the column the card leaves free, the [AddCarousel] strip, the drag ghost and the undo notice. The
 * home itself is not drawn here, it is the live layer next to this (see [editCardLayer]). Esc leaves edit mode.
 */
@Composable
fun EditCardHost(
    progress: State<Float>,
    reduced: Boolean,
    target: EditCardGeometry.Target,
    pages: List<HomePage>,
    current: () -> Int,
    appsByKey: Map<String, AppEntry>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    apps: List<AppEntry>,
    onHome: Set<String>,
    hasWork: Boolean,
    isWork: (AppEntry) -> Boolean,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveTo: (Int, Int) -> Unit,
    onAddPage: () -> Unit,
    onAddApp: (AppEntry) -> Unit,
    onWidget: () -> Unit,
    onFolder: () -> Unit,
    onShortcut: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onGroup: (() -> Unit)? = null,
    notice: UndoNotice?,
    onNoticeDone: () -> Unit,
    focusAdd: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun alphaNow() = EditCardGeometry.frame(target, progress.value, reduced).carouselAlpha
    val d = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val drag = remember { AddCarouselDrag() }
    val focus = remember { FocusRequester() }
    val show by remember { androidx.compose.runtime.derivedStateOf { progress.value > 0.01f } }
    // The filter field only exists once the card is up, so the request waits for it (and for a frame to lay it out).
    LaunchedEffect(focusAdd, show) { if (focusAdd && show) { androidx.compose.runtime.withFrameNanos { }; runCatching { focus.requestFocus() } } }
    var hostOrigin by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // The rectangles are physical screen px, so the host lays out left to right and each panel gets the real direction back for its own content.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInWindow() }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onExit(); true } else false },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = alphaNow() }
                .background(Brush.verticalGradient(listOf(HyleColors.Background, HyleColors.Surface.copy(alpha = 0.9f)))),
        )
        fun Modifier.place(r: EdgeRect) = this
            .absoluteOffset { androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()) }
            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
            .graphicsLayer { alpha = alphaNow() }
        if (show) {
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            PageCarousel(
                pages = pages, current = current(), appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape,
                onSelect = onSelect, onDelete = onDelete, onMove = onMove, onMoveTo = onMoveTo, onAdd = onAddPage,
                modifier = Modifier.place(target.carousel), aspect = (target.card.width / target.card.height).coerceIn(0.3f, 2.6f), compact = target.landscape,
            )
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            AddCarousel(
                apps = apps, onHome = onHome, hasWork = hasWork, isWork = isWork, onAddApp = onAddApp,
                onWidget = onWidget, onFolder = onFolder, onShortcut = onShortcut, onLink = onLink, onFile = onFile,
                cardRect = { target.card }, drag = drag, focusRequester = focus,
                modifier = Modifier.place(target.addBand), compact = target.landscape, onGroup = onGroup,
            )
            }
            drag.app?.let { a ->
                val p = drag.pos - hostOrigin
                Box(Modifier.absoluteOffset { androidx.compose.ui.unit.IntOffset((p.x - 28.dp.toPx()).toInt(), (p.y - 28.dp.toPx()).toInt()) }) { AppIcon(a, 56.dp) }
            }
            if (notice != null) {
                LaunchedEffect(notice.id) { delay(6000); onNoticeDone() }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp).background(HyleColors.Surface, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = notice.message },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(notice.message, color = HyleColors.InkBright, fontSize = 13.sp)
                    CarouselButton(L10n.tr("Undo"), onClick = { notice.onUndo(); onNoticeDone() }, prominent = true)
                }
            }
        }
    }
    }
}
