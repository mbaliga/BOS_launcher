package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay

private val CARD_CORNER = EditCardGeometry.CORNER_DP.dp

/** The page editor's layout for this window: the top bar's slot and the bottom bar's room reserved, mirrored for RTL and for the right-to-left page order. */
@Composable
fun rememberPageEditorLayout(continuous: Boolean, orderRtl: Boolean): PageEditorGeometry.Layout {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = WindowInsets.safeDrawing.getTop(d)
    val bottom = WindowInsets.safeDrawing.getBottom(d)
    return remember(cfg.screenWidthDp, cfg.screenHeightDp, d.density, d.fontScale, rtl, orderRtl, top, bottom) {
        PageEditorGeometry.layout(
            cfg.screenWidthDp * d.density, cfg.screenHeightDp * d.density, d.density, d.fontScale, PageEditorGeometry.mirrored(rtl, orderRtl),
            topInsetPx = top.toFloat(), bottomInsetPx = bottom.toFloat(),
        )
    }
}

/**
 * Wave 36 (pageEditor), laid out anew in wave 45 (pageEditorLayout): the page editor. Everything that lives around the live page while it is
 * shrunken: the strip of page cards, the tabs with their content, the Glance card, the drag ghost seam and the undo notice. The live page itself is
 * the home's own layer (see [editCardLayer]) in the focus slot, so it stays directly editable, and it is never cropped.
 *
 * Portrait: the top bar's slot ([topBar] fills it), the horizontal strip, the live page, the tabs, their content in a fixed-height panel. Landscape and
 * wide windows: the strip is a column at the end side. Both Pages modes share it. Tapping a card reports where the card was ([onSelectPage] with its
 * offset from the live card's centre) so the live layer can swing in from there.
 */
@Composable
fun PageEditorHost(
    progress: State<Float>,
    reduced: Boolean,
    layout: PageEditorGeometry.Layout,
    continuous: Boolean,
    pages: List<HomePage>,
    current: Int,
    glanceOn: Boolean,
    glanceFocus: Boolean,
    swing: () -> PageEditorGeometry.Swing,
    appsByKey: Map<String, AppEntry>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    apps: List<AppEntry>,
    onHome: Set<String>,
    hasWork: Boolean,
    isWork: (AppEntry) -> Boolean,
    drag: AddCarouselDrag,
    onSelectPage: (page: Int, dx: Float, dy: Float) -> Unit,
    onSelectGlance: (dx: Float, dy: Float) -> Unit,
    onAddPage: (dx: Float, dy: Float) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    /** Drag, key or action reorder: the page at [from] goes to position [to]. */
    onReorder: (Int, Int) -> Unit,
    onAddApp: (AppEntry) -> Unit,
    onDropApp: (AppEntry, Int) -> Unit,
    onWidget: () -> Unit,
    onFolder: () -> Unit,
    onShortcut: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onGroup: (() -> Unit)?,
    onPrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    notice: UndoNotice?,
    onNoticeDone: () -> Unit,
    focusAdd: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** INTEGRATION POINT: the edit chrome's top bar (Cancel, "Editing page N of M", Undo, Redo, Done) is drawn in the 56 dp slot at the top ([PageEditorGeometry.Layout.topBar]); null leaves the slot empty. */
    topBar: (@Composable () -> Unit)? = null,
) {
    fun alphaNow() = EditCardGeometry.frame(layout.target, progress.value, reduced).carouselAlpha
    val d = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val focus = remember { FocusRequester() }
    val show by remember { androidx.compose.runtime.derivedStateOf { progress.value > 0.01f } }
    var tab by rememberSaveable { mutableStateOf(EditorTab.Apps) }
    // The Glance has no apps or widgets: its card brings the Settings tab (its own options) forward.
    LaunchedEffect(glanceFocus) { if (glanceFocus) tab = EditorTab.Settings }
    LaunchedEffect(focusAdd, show) { if (focusAdd && show) { tab = EditorTab.Apps; androidx.compose.runtime.withFrameNanos { }; runCatching { focus.requestFocus() } } }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val stripState = rememberLazyListState()

    // The drop targets in host px: the live card (the page being edited) and every laid-out, clipped card of the strip (their rectangles are recorded as they are placed).
    val rects = remember { HashMap<Int, EdgeRect>() }
    fun dropTargets(): List<PageEditorGeometry.DropTarget> {
        val out = ArrayList<PageEditorGeometry.DropTarget>()
        if (!glanceFocus) out += PageEditorGeometry.DropTarget(current, layout.card)
        for ((page, r) in rects) {
            if (page !in pages.indices || (page == current && !glanceFocus)) continue
            val local = EdgeRect(r.left - hostOrigin.x, r.top - hostOrigin.y, r.right - hostOrigin.x, r.bottom - hostOrigin.y)
            val clipped = PageEditorGeometry.visible(local, layout.strip) ?: continue
            out += PageEditorGeometry.DropTarget(page, clipped)
        }
        return out
    }
    val latestDrop by androidx.compose.runtime.rememberUpdatedState(onDropApp)
    drag.onDrop = { app, windowPos ->
        val p = windowPos - hostOrigin
        PageEditorGeometry.hit(dropTargets(), p.x, p.y)?.let { latestDrop(app, it) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    // ---- the top bar's slot (the edit chrome draws there) ----
                    if (topBar != null) Box(Modifier.place(layout.topBar).testTag("page-editor-topbar-slot")) { topBar() }
                    // ---- tabs and their content ----
                    Column(Modifier.place(layout.tabs).testTag("page-editor-tabs"), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        PageEditorTabRow(tab, { tab = it }, modifier = Modifier.height(with(d) { layout.tabRow.height.toDp() }).padding(horizontal = 8.dp))
                        PageEditorContent(
                            tab = tab, page = if (glanceFocus) null else current, pageCount = pages.size, prefs = prefs, apps = apps, onHome = onHome, hasWork = hasWork, isWork = isWork,
                            drag = drag, focusRequester = focus, onAddApp = onAddApp, onWidget = onWidget, onFolder = onFolder, onShortcut = onShortcut, onLink = onLink, onFile = onFile, onGroup = onGroup,
                            settings = PageEditorSettingsActions(onPrefs), modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    // ---- the strip ----
                    PageStrip(
                        layout = layout, state = stripState, pages = pages, current = current, glanceOn = glanceOn, glanceFocus = glanceFocus,
                        appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape, reduced = reduced, hostOrigin = { hostOrigin }, rects = rects,
                        onSelectPage = onSelectPage, onSelectGlance = onSelectGlance, onAddPage = onAddPage, onDelete = onDelete, onMove = onMove, onReorder = onReorder,
                        modifier = Modifier.place(layout.strip),
                    )
                    // ---- the Glance as the focus card ----
                    if (glanceOn && glanceFocus) {
                        Box(
                            Modifier.place(layout.card).graphicsLayer {
                                val s = swing()
                                translationX = s.translationX; translationY = s.translationY; rotationX = s.rotationX; rotationY = s.rotationY
                                scaleX = s.scale; scaleY = s.scale; alpha = s.alpha * alphaNow(); cameraDistance = PageEditorGeometry.CAMERA_DISTANCE_DP * density
                            }.testTag("page-editor-glance-focus"),
                        ) { GlanceCardFace(prefs, Modifier.fillMaxSize(), big = true) }
                    }
                }
                if (notice != null) {
                    LaunchedEffect(notice.id) { delay(6000); onNoticeDone() }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp).background(HyleColors.Surface, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = notice.message },
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(notice.message, color = HyleColors.InkBright, fontSize = 13.sp)
                        CarouselButton(L10n.tr("Undo"), onClick = { notice.onUndo(); onNoticeDone() }, prominent = true)
                    }
                }
            }
        }
    }
}

/** The dragged app's icon under the finger. Drawn by the home above everything (the editor's own layer lies under the live page); it takes no touches. */
@Composable
fun PageEditorGhost(drag: AddCarouselDrag, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        drag.app?.let { a ->
            val p = drag.pos - origin
            Box(Modifier.absoluteOffset { androidx.compose.ui.unit.IntOffset((p.x - 28.dp.toPx()).toInt(), (p.y - 28.dp.toPx()).toInt()) }.testTag("page-editor-ghost")) { AppIcon(a, 56.dp) }
        }
    }
}

/** The Glance as a card: its wallpaper-like gradient, its name and a dot at each hotspot that is on. */
@Composable
internal fun GlanceCardFace(prefs: LauncherPrefs, modifier: Modifier, big: Boolean) {
    val shape = RoundedCornerShape(CARD_CORNER)
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF3A3F7A), Color(0xFF14172B))), shape)) {
        Text(L10n.tr("Glance"), color = Color.White, fontSize = if (big) 22.sp else 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        Corner.entries.filter { it in prefs.glanceHotspots }.forEach { c ->
            val a = when (c) { Corner.TopLeft -> Alignment.TopStart; Corner.TopRight -> Alignment.TopEnd; Corner.BottomLeft -> Alignment.BottomStart; Corner.BottomRight -> Alignment.BottomEnd }
            Box(Modifier.align(a).padding(12.dp).size(10.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)))
        }
    }
}
