package com.bos.sphere.feature.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.bos.sphere.core.data.AppEntry
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.EditChromeHint
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay

/** Test tags of the single-page home edit chrome (W45). */
object EditChromeTags {
    const val BAR = "edit.bar"
    const val TITLE = "edit.title"
    const val CANCEL = "edit.cancel"
    const val DONE = "edit.done"
    const val UNDO = "edit.undo"
    const val REDO = "edit.redo"
    const val HINT = "edit.hint"
    const val TRAY = "edit.tray"
    const val CHIP_APPS = "edit.tray.apps"
    const val CHIP_WIDGETS = "edit.tray.widgets"
    const val CHIP_FOLDERS = "edit.tray.folders"
    const val CHIP_PAGE = "edit.tray.page"
    const val BROWSER = "edit.browser"
    const val BROWSER_DONE = "edit.browser.done"
    const val SHIELD = "edit.fixed.shield"
}

/**
 * W45: the one rule of Cancel, shared by the button, Esc and system Back: nothing changed, leave at once; something changed, ask first.
 * [confirming] is the open dialog.
 */
@Stable
class HomeEditChromeState {
    var confirming by mutableStateOf(false)
    fun requestCancel(hasChanges: Boolean, onCancel: () -> Unit) { if (hasChanges) confirming = true else onCancel() }
    fun dismissConfirm() { confirming = false }
}

/** The progress of the edit mode's entry and exit: 300 ms emphasized in, 250 ms out; 120 ms with reduced motion. A State read inside draw and graphicsLayer lambdas only. */
@Composable
fun rememberEditChromeProgress(active: Boolean): State<Float> {
    val reduced = rememberMotionPrefs().reduced
    val anim = remember { Animatable(0f) }
    LaunchedEffect(active, reduced) {
        val ms = if (reduced) EditChromeGeometry.REDUCED_MS else if (active) EditChromeGeometry.ENTER_MS else EditChromeGeometry.EXIT_MS
        anim.animateTo(if (active) 1f else 0f, tween(ms, easing = EMPHASIZED))
    }
    return anim.asState()
}

private val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The page frame of the edit mode for the window the home really has ([sizePx] is the measured size of the home's root, so the scrim, the outline and the layer
 * agree with the layout whatever the configuration says; before the first measure the configuration's size stands in) and the current insets.
 */
@Composable
fun rememberEditChromePage(sizePx: androidx.compose.ui.unit.IntSize = androidx.compose.ui.unit.IntSize.Zero): EditChromeGeometry.PageFrame {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val top = WindowInsets.safeDrawing.getTop(d)
    val w = if (sizePx.width > 0) sizePx.width.toFloat() else cfg.screenWidthDp * d.density
    val h = if (sizePx.height > 0) sizePx.height.toFloat() else cfg.screenHeightDp * d.density
    return remember(w, h, d.density, top) { EditChromeGeometry.pageFrame(w, h, top.toFloat(), d.density) }
}

/**
 * W45: what lies BEHIND the scaled page: the app's dark backdrop and the 35% black scrim, with the page's rectangle left out (the page is the live home layer,
 * drawn above this one). Draw only. [progress] is the entry progress.
 */
@Composable
fun EditChromeBackdrop(progress: State<Float>, page: EditChromeGeometry.PageFrame, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().testTag("edit.backdrop")) {
        val p = progress.value
        if (p <= 0.001f) return@Canvas
        val r = page.rect
        val radius = EditChromeGeometry.PAGE_CORNER_DP.dp.toPx()
        val hole = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left, r.top, r.right, r.bottom, CornerRadius(radius))) }
        clipPath(hole, ClipOp.Difference) {
            drawRect(Brush.verticalGradient(listOf(HyleColors.Background, HyleColors.Surface.copy(alpha = 0.9f))), alpha = p)
            drawRect(Color.Black.copy(alpha = EditChromeGeometry.scrimAlpha(p)))
        }
    }
}

/** The 1 dp hairline round the page (12% white, the page's 20 dp radius), drawn above it. No touch, no semantics. */
@Composable
private fun PageHairline(progress: State<Float>, page: EditChromeGeometry.PageFrame) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val p = progress.value
        if (p <= 0.001f) return@Canvas
        val r = page.rect
        drawRoundRect(Color.White.copy(alpha = EditChromeGeometry.HAIRLINE_ALPHA * p), Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(EditChromeGeometry.PAGE_CORNER_DP.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

private object EditGlyphs {
    private fun g(name: String, mirror: Boolean, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = false).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()
    val Undo = g("undo", false) { moveTo(9f, 5f); lineTo(4f, 10f); lineTo(9f, 15f); moveTo(4f, 10f); lineTo(14f, 10f); quadTo(20f, 10f, 20f, 15f); quadTo(20f, 20f, 14f, 20f); lineTo(10f, 20f) }
    val Redo = g("redo", true) { moveTo(15f, 5f); lineTo(20f, 10f); lineTo(15f, 15f); moveTo(20f, 10f); lineTo(10f, 10f); quadTo(4f, 10f, 4f, 15f); quadTo(4f, 20f, 10f, 20f); lineTo(14f, 20f) }
}

/**
 * W45 (editChrome, HOM-12): the chrome of the single-page home edit mode. A 56 dp top bar in the safe area (Cancel, "Editing page N of M", Undo, Redo, the one
 * Done), the hint line under it (first three entries only), and a 72 dp tray at the bottom (Apps, Widgets, Folders, and "+ Page" when pages can be added).
 * The live page is the home's own layer, scaled by [EditChromeGeometry.frame]; this composable draws only the controls and the page's outline.
 *
 * Nothing here changes the arrangement: the callbacks do. Cancel restores the entry snapshot (the caller does that in [onCancel]); it asks first only when
 * [hasChanges]. [touchSignal] changes on any touch of the screen (the hint goes away on the first). [announce] speaks to screen readers.
 */
@Composable
fun HomeEditChrome(
    visible: Boolean,
    progress: State<Float>,
    page: EditChromeGeometry.PageFrame,
    pageIndex: Int,
    pageCount: Int,
    undoCount: Int,
    redoCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    hasChanges: Boolean,
    showHint: Boolean,
    showAddPage: Boolean,
    trayBottomDp: Float,
    state: HomeEditChromeState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onFolders: () -> Unit,
    onAddPage: () -> Unit,
    onOpenPageEditor: () -> Unit,
    modifier: Modifier = Modifier,
    touchSignal: Int = 0,
    announce: (String) -> Unit = rememberAnnouncer(),
    /** W51: the tray's Apps and Widgets chips open the visual carousel in place above the tray: these are its data and its two add paths (a tap places the item on the page being edited). */
    apps: List<AppEntry> = emptyList(),
    onHome: Set<String> = emptySet(),
    hasWork: Boolean = false,
    isWork: (AppEntry) -> Boolean = { false },
    onAddApp: (AppEntry) -> Unit = {},
    onAddWidget: (WidgetChoice) -> Unit = {},
    widgetSource: @Composable () -> List<WidgetChoice>? = { rememberWidgetChoices() },
) {
    if (!visible) return
    val (n, m) = EditChromeText.titleArgs(pageIndex, pageCount)
    val title = L10n.tr("Editing page %d of %d", n, m)
    val latestN by rememberUpdatedState(n)
    val latestM by rememberUpdatedState(m)
    // Announced once per entry (the chrome is composed only while editing).
    LaunchedEffect(Unit) { announce(L10n.tr("Editing home page %d of %d", latestN, latestM)) }
    val reduced = rememberMotionPrefs().reduced
    val doneNow by rememberUpdatedState {
        announce(L10n.tr("Saved"))
        onDone()
    }
    val cancel = { state.requestCancel(hasChanges, onCancel) }
    BackHandler(enabled = visible) { cancel() }
    var panel by remember { mutableStateOf(TrayPanel.None) }
    BackHandler(enabled = panel != TrayPanel.None) { panel = TrayPanel.None } // registered last: Back closes the carousel before it cancels the edit
    val focus = remember { FocusRequester() }
    Box(
        modifier.fillMaxSize().testTag("edit.chrome")
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { if (panel != TrayPanel.None) panel = TrayPanel.None else cancel(); true } else false },
    ) {
        PageHairline(progress, page)
        // ---- top bar
        Column56(progress) {
            EditTopBar(
                title = title, undoCount = undoCount, redoCount = redoCount, canUndo = canUndo, canRedo = canRedo,
                onCancel = cancel, onUndo = onUndo, onRedo = onRedo, onDone = { doneNow() }, onTitleClick = onOpenPageEditor,
            )
        }
        // ---- hint
        val dens = LocalDensity.current
        val safeTop = WindowInsets.safeDrawing.getTop(dens).toFloat()
        HintLine(showHint, touchSignal, reduced, progress, EditChromeGeometry.hintTopPx(page, safeTop, dens.density))
        // ---- tray
        EditTray(
            progress, EditChromeGeometry.trayBottomDp(page, dens.density, trayBottomDp), showAddPage, panel, { panel = it }, touchSignal, onFolders, onAddPage,
            apps, onHome, hasWork, isWork, onAddApp, onAddWidget, widgetSource,
        )
    }
    EditCancelDialog(state, onCancel)
}

/**
 * W47 (editIntegrate): THE bar of every edit step: Cancel, the title, Undo, Redo and the one Done. The single-page edit mode ([HomeEditChrome]) and the page editor
 * (in [PageEditorHost]'s top bar slot) draw the same bar; only the title differs ("Editing page N of M" / "Editing pages"). [onTitleClick] is null where the title
 * opens nothing (the page editor is already open). [insets] adds the safe top and side insets (the page editor's slot is already below the status bar).
 */
@Composable
fun EditTopBar(
    title: String,
    undoCount: Int,
    redoCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    insets: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().then(if (insets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)
            .height(EditChromeGeometry.TOP_BAR_DP.dp)
            .padding(horizontal = 4.dp).testTag(EditChromeTags.BAR)
            .semantics { isTraversalGroup = true; traversalIndex = -1f; paneTitle = L10n.tr("Edit toolbar") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarText(L10n.tr("Cancel"), EditChromeTags.CANCEL, onCancel)
        Text(
            title, color = HyleColors.InkBright, fontSize = EditChromeGeometry.TITLE_SP.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.weight(1f).heightIn(min = EditChromeGeometry.BUTTON_DP.dp).wrapCentre()
                .then(if (onTitleClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = L10n.tr("Open the page editor")) { onTitleClick() } else Modifier)
                .testTag(EditChromeTags.TITLE).semantics { heading(); traversalIndex = -2f },
        )
        BarIcon(EditGlyphs.Undo, if (undoCount > 0) L10n.tr("Undo, %d available", undoCount) else L10n.tr("Undo"), canUndo, EditChromeTags.UNDO, onUndo, undoCount, if (undoCount > 0) null else L10n.tr("Nothing to undo"))
        BarIcon(EditGlyphs.Redo, if (redoCount > 0) L10n.tr("Redo, %d available", redoCount) else L10n.tr("Redo"), canRedo, EditChromeTags.REDO, onRedo, redoCount, if (redoCount > 0) null else L10n.tr("Nothing to redo"))
        Text(
            L10n.tr("Done"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp).heightIn(min = EditChromeGeometry.BUTTON_DP.dp).defaultMinSize(minWidth = 64.dp)
                .clip(RoundedCornerShape(50)).background(HyleColors.Violet.copy(alpha = 0.50f))
                .clickable(role = Role.Button) { onDone() }.padding(horizontal = 20.dp).wrapCentre().testTag(EditChromeTags.DONE), // a clickable is focusable and takes Enter / D-pad centre itself
        )
    }
}

/** The "Discard changes?" confirm of Cancel, Back and Esc: shown while [state] is confirming; Discard runs [onCancel]. Shared by the edit chrome and the page editor. */
@Composable
fun EditCancelDialog(state: HomeEditChromeState, onCancel: () -> Unit) {
    if (!state.confirming) return
    ConfirmDialog(
        title = L10n.tr("Discard changes?"),
        text = L10n.tr("Everything you changed since you started editing is undone."),
        confirm = L10n.tr("Discard"), dismiss = L10n.tr("Keep editing"),
        onConfirm = { state.dismissConfirm(); onCancel() }, onDismiss = { state.dismissConfirm() },
        destructive = true,
    )
}

/** A remembered speaker for screen readers: the host view's announcement (no accessibility service involved). */
@Composable
fun rememberAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return remember(view) { { text: String -> @Suppress("DEPRECATION") view.announceForAccessibility(text) } }
}

@Composable
private fun Column56(progress: State<Float>, content: @Composable () -> Unit) {
    // The bar fades in with the entry and swallows touches over itself (the page's top band and the music line lie under it).
    Box(
        Modifier.fillMaxWidth().graphicsLayer { alpha = progress.value }
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .pointerInput(Unit) { detectTapGestures { } },
    ) { content() }
}

private fun Modifier.wrapCentre(): Modifier = this.then(Modifier.wrapContentAligned())
private fun Modifier.wrapContentAligned(): Modifier = this.then(Modifier.wrapContentHeight(Alignment.CenterVertically))

@Composable
private fun BarText(label: String, tag: String, onClick: () -> Unit) {
    Text(
        label, color = HyleColors.InkBright, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.heightIn(min = EditChromeGeometry.BUTTON_DP.dp).defaultMinSize(minWidth = EditChromeGeometry.BUTTON_DP.dp)
            .clip(RoundedCornerShape(50)).clickable(role = Role.Button) { onClick() }.padding(horizontal = 12.dp).wrapCentre().testTag(tag),
    )
}

/** W48: the bar's Undo / Redo with a small history badge (the count, 9+ above nine) at the corner; the spoken description carries the count, a disabled one says why ([why]). */
@Composable
private fun BarIcon(glyph: ImageVector, description: String, enabled: Boolean, tag: String, onClick: () -> Unit, count: Int = 0, why: String? = null) {
    Box(
        Modifier.size(EditChromeGeometry.BUTTON_DP.dp).clip(RoundedCornerShape(50)).clickable(enabled = enabled, role = Role.Button) { onClick() }
            .testTag(tag).semantics { contentDescription = description; if (why != null) stateDescription = why },
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, null, tint = Color.White, modifier = Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else EditChromeGeometry.DISABLED_ALPHA })
        if (count > 0) Text(
            if (count > 9) "9+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp).defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .background(HyleColors.Violet, RoundedCornerShape(50)).padding(horizontal = 4.dp).wrapCentre().testTag(tag + ".badge").clearAndSetSemantics { },
        )
    }
}

@Composable
private fun HintLine(show: Boolean, touchSignal: Int, reduced: Boolean, progress: State<Float>, topPx: Float) {
    if (!show) return
    var shown by remember { mutableStateOf(true) }
    val first = remember { touchSignal }
    LaunchedEffect(Unit) { delay(EditChromeHint.FADE_AFTER_MS); shown = false }
    LaunchedEffect(touchSignal) { if (touchSignal != first) shown = false }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(shown) { if (!shown) fade.animateTo(0f, tween(if (reduced) 0 else EditChromeGeometry.HINT_FADE_MS)) }
    if (!shown && fade.value <= 0.001f) return
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).absoluteOffset { androidx.compose.ui.unit.IntOffset(0, topPx.toInt()) }
            .graphicsLayer { alpha = fade.value * progress.value }.testTag(EditChromeTags.HINT),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            L10n.tr("Drag to move. Tap an item to resize or change it."), color = HyleColors.InkBright, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** W51: which carousel the tray shows above its chips. */
enum class TrayPanel { None, Apps, Widgets }

@Composable
private fun EditTray(
    progress: State<Float>, bottomDp: Float, showAddPage: Boolean, panel: TrayPanel, setPanel: (TrayPanel) -> Unit, touchSignal: Int, onFolders: () -> Unit, onAddPage: () -> Unit,
    apps: List<AppEntry>, onHome: Set<String>, hasWork: Boolean, isWork: (AppEntry) -> Boolean, onAddApp: (AppEntry) -> Unit, onAddWidget: (WidgetChoice) -> Unit,
    widgetSource: @Composable () -> List<WidgetChoice>?,
) {
    // A touch anywhere else closes the carousel (it never blocks the touch: the page still gets it, so a drag of an item starts at once). A touch inside the tray (the carousel or the chips) does not.
    val inside = remember { booleanArrayOf(false) }
    val first = remember { touchSignal }
    LaunchedEffect(touchSignal) {
        if (touchSignal != first && panel != TrayPanel.None && !inside[0]) setPanel(TrayPanel.None)
        inside[0] = false
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val panelH = (maxHeight.value * 0.40f).coerceIn(170f, 300f).dp // of the window the chrome really fills
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(bottom = bottomDp.dp)
                .graphicsLayer { alpha = progress.value },
        ) {
            // Only the carousel and the chips count as "inside" (the tray's gaps let touches through to what lies under them, as before).
            val markInside = Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); inside[0] = true } }
            if (panel != TrayPanel.None) {
                val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(panelH).clip(shape).background(Color(0xF2121520), shape).border(1.dp, HyleColors.Hairline, shape)
                        .then(markInside).testTag(EditChromeTags.BROWSER).semantics { isTraversalGroup = true; paneTitle = L10n.tr(if (panel == TrayPanel.Apps) "Add apps" else "Add widgets") },
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.tr(if (panel == TrayPanel.Apps) "Add apps" else "Add widgets"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Text(
                            L10n.tr("Done"), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.heightIn(min = EditChromeGeometry.BUTTON_DP.dp).defaultMinSize(minWidth = EditChromeGeometry.BUTTON_DP.dp).clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button) { setPanel(TrayPanel.None) }.padding(horizontal = 14.dp).wrapCentre().testTag(EditChromeTags.BROWSER_DONE),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (panel == TrayPanel.Apps) AppsContent(apps, onHome, hasWork, isWork, null, null, onAddApp)
                        else WidgetBrowser(onPick = onAddWidget, choices = widgetSource())
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(EditChromeGeometry.TRAY_DP.dp)
                    .testTag(EditChromeTags.TRAY) // the chips take their own touches; the gaps let the page dots and the page below them through
                    .semantics { isTraversalGroup = true },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(L10n.tr("Apps"), EditChromeTags.CHIP_APPS, null, panel == TrayPanel.Apps, markInside) { setPanel(if (panel == TrayPanel.Apps) TrayPanel.None else TrayPanel.Apps) }
                Chip(L10n.tr("Widgets"), EditChromeTags.CHIP_WIDGETS, null, panel == TrayPanel.Widgets, markInside) { setPanel(if (panel == TrayPanel.Widgets) TrayPanel.None else TrayPanel.Widgets) }
                Chip(L10n.tr("Folders"), EditChromeTags.CHIP_FOLDERS, null, false, markInside) { setPanel(TrayPanel.None); onFolders() }
                if (showAddPage) Chip(L10n.tr("+ Page"), EditChromeTags.CHIP_PAGE, L10n.tr("Add page"), false, markInside) { onAddPage() }
            }
        }
    }
}

@Composable
private fun Chip(label: String, tag: String, description: String?, on: Boolean, extra: Modifier, onClick: () -> Unit) {
    Text(
        label, color = if (on) HyleColors.OnAccent else HyleColors.InkBright, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.heightIn(min = EditChromeGeometry.BUTTON_DP.dp).then(extra).clip(RoundedCornerShape(50))
            .background(if (on) HyleColors.Violet else Color(0xF2161922)).border(1.dp, if (on) HyleColors.Violet else Color(0x40FFFFFF), RoundedCornerShape(50))
            .clickable(role = Role.Button) { onClick() }.padding(horizontal = 18.dp).wrapCentre().testTag(tag)
            .semantics { if (description != null) contentDescription = description; selected = on },
    )
}

/**
 * W45: the fixed chrome while editing. The four hotspots and the music line live inside the scaled page, so four squares at the PAGE's corners (and a strip under the
 * bar for the music line) swallow touch: they stay drawn (dimmed by the caller to [EditChromeGeometry.FIXED_DIM]) but cannot fire during a handle drag. The page
 * dots are NOT covered: they are the page switcher.
 */
@Composable
fun EditFixedChromeShield(page: EditChromeGeometry.PageFrame, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    val side = FixedHomeChrome.HOTSPOT_TOUCH_DP * page.scale
    val sidePx = side * d.density
    Box(modifier.fillMaxSize().testTag(EditChromeTags.SHIELD).clearAndSetSemantics { }) {
        val r = page.rect
        @Composable fun corner(x: Float, y: Float) = Box(Modifier.absoluteOffset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }.size(side.dp).pointerInput(Unit) { detectTapGestures { } })
        corner(r.left, r.top); corner(r.right - sidePx, r.top); corner(r.left, r.bottom - sidePx); corner(r.right - sidePx, r.bottom - sidePx)
        Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = EditChromeGeometry.TOP_BAR_DP.dp).size(280.dp, 48.dp).pointerInput(Unit) { detectTapGestures { } })
    }
}
