package com.bos.sphere.feature.grid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.bos.sphere.core.design.NotchShape
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockPlacement
import com.bos.sphere.core.data.ClockPlacement.clockPos
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.ControlsPop
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.input.FocusKind
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure text rules of the music line (testable). */
object GlanceText {
    /** The music line's text size in sp; the Glance clock is the same size (owner rule). */
    const val MUSIC_SP = 14f

    /** The one-line music text: "Title · Artist", or the title alone. */
    fun nowPlayingLine(title: String, artist: String) = if (artist.isBlank()) title else "$title · $artist"

    /**
     * W20 audit: the line as drawn. Each part is a bidi isolate, so a right-to-left title next to a Latin artist (or the other way round) keeps
     * its own order and the separator stays between them; with no artist it is the title alone. The spoken text stays [nowPlayingLine].
     */
    fun nowPlayingDisplay(title: String, artist: String) = if (artist.isBlank()) title else "\u2068$title\u2069 · \u2068$artist\u2069"
}

/**
 * Wave 38 (glanceFixed): the Glance page is the CALM page: only the wallpaper (the [underlay] and its scrim) and the home gestures. The time, the date and the
 * weather are gone with their settings (wave 41 brings back ONE big wallpaper clock, drawn in this page); the four hotspots, the music line and the page dots are the fixed chrome the home draws above its pager
 * ([FixedHomeChrome], the dots by the home), so they do not move while this page scrolls away. A standalone surface (tests, previews) may still draw its
 * own one-stop indicator ([indicator] true); the home passes false.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlanceSurface(
    prefs: LauncherPrefs,
    hasLaunchpad: Boolean,
    modifier: Modifier = Modifier,
    onOpenLaunchpad: () -> Unit = {},
    indicator: Boolean = true,
    /** Called when the user touches the surface: the host wakes the indicator it draws. */
    onAwake: () -> Unit = {},
    gestures: HomeGestureSpec? = null,
    dark: Boolean = isDarkSurface(),
    underlay: @Composable () -> Unit = {},
    /** W55: the home draws the wallpaper once below its pager, so the Glance page itself paints no base colour. */
    transparentBase: Boolean = false,
    /** Wave 41: a tap on the wallpaper clock (the home opens the clock app) and its accent colour. */
    onClockTap: () -> Unit = {},
    clockAccent: Color = com.bos.sphere.core.design.HyleColors.Violet,
    /** Wave 50 (glanceClockEdit): applies a change to the stored prefs (the clock's place and options while it is edited), and tells the home when the clock edit mode starts or ends. */
    onPrefsEdit: ((LauncherPrefs) -> LauncherPrefs) -> Unit = {},
    onEditingChange: (Boolean) -> Unit = {},
) {
    var awakeNonce by remember { mutableIntStateOf(0) }
    // Wave 34: with the system live wallpaper chosen the surface has no base colour at all, so the wallpaper behind the window shows through.
    val systemWallpaper = prefs.wallpaper(com.bos.sphere.core.data.WallpaperSurface.Glance).kind == com.bos.sphere.core.data.WallpaperKind.System
    BoxWithConstraints(modifier.fillMaxSize().background(if (systemWallpaper || transparentBase) Color.Transparent else if (dark) Color.Black else Color(0xFFF3F3F6)).testTag("glance.surface")) {
        underlay()
        // The tap layer: the home gestures (swipes, long press) share it; a tap only wakes the indicator.
        var clockBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
        var layerOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        // Wave 50 (glanceClockEdit): the clock edit mode. A long press ON the clock enters it (the top layer hit-tests the clock's bounds); a long press on empty space is the
        // home's config options as on every home. While editing, the top layer drags the clock instead of the home gestures, and the options panel is drawn above it.
        val landscape = maxWidth > maxHeight
        val haptics = rememberLauncherHaptics()
        var editing by remember { mutableStateOf(false) }
        var snapshot by remember { mutableStateOf<LauncherPrefs?>(null) }
        var override by remember { mutableStateOf<ClockPlacement.Pos?>(null) }
        var areaPx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val storedPos = prefs.clockPos(landscape)
        val pos = override ?: storedPos
        val latestPos by rememberUpdatedState(pos)
        val latestArea by rememberUpdatedState(areaPx)
        val latestBounds by rememberUpdatedState(clockBounds)
        val latestOrigin by rememberUpdatedState(layerOrigin)
        val latestEdit by rememberUpdatedState(onPrefsEdit)
        val latestPrefs by rememberUpdatedState(prefs)
        val landscapeNow by rememberUpdatedState(landscape)
        LaunchedEffect(storedPos) { override = null }
        DisposableEffect(editing) { onEditingChange(editing); onDispose { if (editing) onEditingChange(false) } }
        fun enterEdit() {
            if (editing || latestPrefs.clockStyle == ClockStyle.Off) return
            snapshot = latestPrefs; override = null; editing = true
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        fun cancelEdit() {
            val s = snapshot
            if (s != null) latestEdit { with(ClockPlacement) { it.restoreClockFrom(s) } }
            override = null; snapshot = null; editing = false
        }
        fun doneEdit() { override = null; snapshot = null; editing = false }
        fun place(p: ClockPlacement.Pos) { override = p; latestEdit { with(ClockPlacement) { it.withClockPos(landscapeNow, p) } } }
        fun moveBy(dx: Int, dy: Int) {
            val a = latestArea; val b = latestBounds
            place(ClockPlacement.nudge(latestPos, dx, dy, a.width.toFloat(), a.height.toFloat(), b.width, b.height))
        }
        fun sizeBy(d: Int) {
            val e = ClockSize.entries; val n = (e.indexOf(latestPrefs.glanceClockSize) + d).coerceIn(0, e.lastIndex)
            if (n != e.indexOf(latestPrefs.glanceClockSize)) latestEdit { it.copy(glanceClockSize = e[n]) }
        }
        // TalkBack: the clock can be edited and moved without dragging.
        val aEdit = L10n.tr("Edit clock"); val aUp = L10n.tr("Move clock up"); val aDown = L10n.tr("Move clock down"); val aLeft = L10n.tr("Move clock left"); val aRight = L10n.tr("Move clock right")
        val aBig = L10n.tr("Larger clock"); val aSmall = L10n.tr("Smaller clock")
        val clockActions = remember(aEdit, aUp, aDown, aLeft, aRight, aBig, aSmall) {
            listOf(
                CustomAccessibilityAction(aEdit) { enterEdit(); true },
                CustomAccessibilityAction(aUp) { moveBy(0, -1); true }, CustomAccessibilityAction(aDown) { moveBy(0, 1); true },
                CustomAccessibilityAction(aLeft) { moveBy(-1, 0); true }, CustomAccessibilityAction(aRight) { moveBy(1, 0); true },
                CustomAccessibilityAction(aBig) { sizeBy(1); true }, CustomAccessibilityAction(aSmall) { sizeBy(-1); true },
            )
        }
        // Keyboard and controller: arrows move the clock while it is edited (Tab still walks the options), Esc is Cancel.
        DisposableEffect(editing) {
            val h: (android.view.KeyEvent) -> Boolean = h@{ e ->
                if (!editing) return@h false // never swallow a key once the mode is over
                when (e.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { moveBy(-1, 0); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { moveBy(1, 0); true }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { moveBy(0, -1); true }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { moveBy(0, 1); true }
                    android.view.KeyEvent.KEYCODE_ESCAPE -> { cancelEdit(); true }
                    else -> false
                }
            }
            if (editing) LauncherInput.pushKeyHandler(h)
            onDispose { if (editing) LauncherInput.removeKeyHandler(h) }
        }
        androidx.activity.compose.BackHandler(enabled = editing) { cancelEdit() }
        fun onClock(p: Offset) = !latestBounds.isEmpty && latestBounds.contains(p + latestOrigin)
        // The clock is drawn BELOW the tap layer on purpose (a node with its own pointer input would hide the gesture layer under it): the tap layer hit-tests a tap against the clock's bounds.
        // Wave 41: the big wallpaper clock is part of this page (it scrolls away with it). Wave 50: its place is stored (fractions of this area), default the upper-middle.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).onSizeChanged { areaPx = it }) {
            GlanceWallpaperClock(
                prefs.clockStyle, prefs.clockFormat, prefs.glanceClockSize, prefs.glanceClockDate, prefs.glanceClockAccent, dark, clockAccent, onClockTap, onBounds = { clockBounds = it },
                pos = pos, editing = editing, actions = clockActions, onMenu = { enterEdit() },
            )
        }
        if (!editing) Box(
            Modifier.fillMaxSize().homeGestures(gestures) { p -> !onClock(p) }
                .onGloballyPositioned { layerOrigin = it.positionInRoot() }
                .pointerInput(onClockTap) { detectTapGestures(onTap = { p -> if (onClock(p)) onClockTap() else { awakeNonce++; onAwake() } }, onLongPress = { p -> if (onClock(p)) enterEdit() }) }
                // decoration for accessibility: the surface has no content of its own
                .clearAndSetSemantics { },
        ) else Box(
            Modifier.fillMaxSize().testTag(GlanceClockEditTags.LAYER)
                .onGloballyPositioned { layerOrigin = it.positionInRoot() }
                .pointerInput(Unit) {
                    val grab = 16.dp.toPx(); val snapPx = ClockPlacement.SNAP_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val b = latestBounds
                        if (b.isEmpty || !b.inflate(grab).contains(down.position + latestOrigin)) return@awaitEachGesture
                        val first = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() } ?: return@awaitEachGesture
                        val a = latestArea; val aw = a.width.toFloat(); val ah = a.height.toFloat()
                        val (l0, t0) = ClockPlacement.topLeft(latestPos, aw, ah, b.width, b.height)
                        var caughtX = false; var caughtY = false
                        var last: ClockPlacement.Pos? = null
                        drag(first.id) { c ->
                            val d = c.position - first.position
                            val raw = ClockPlacement.fromTopLeft(l0 + d.x, t0 + d.y, aw, ah, b.width, b.height)
                            val s = ClockPlacement.snap(raw, aw, ah, b.width, b.height, snapPx)
                            if ((s.x && !caughtX) || (s.y && !caughtY)) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            caughtX = s.x; caughtY = s.y
                            override = s.pos; last = s.pos
                            c.consume()
                        }
                        last?.let { place(it) }
                    }
                }
                .clearAndSetSemantics { },
        )
        if (editing) {
            // The options panel sits on the far side of the clock so it never covers it: portrait top or bottom, landscape left or right.
            val a = areaPx; val clockH = if (a.height > 0) clockBounds.height / a.height else 0f
            val surfaceH = maxHeight
            val rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            var startOrTop by remember { mutableStateOf(GlanceClockEditRules.panelAtStartOrTop(landscape, storedPos.x, storedPos.y, clockH)) }
            LaunchedEffect(storedPos, landscape) { startOrTop = GlanceClockEditRules.panelAtStartOrTop(landscape, storedPos.x, storedPos.y, clockH) }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
                val panelModifier = if (landscape) Modifier.align(if (startOrTop != rtl) Alignment.CenterStart else Alignment.CenterEnd).width(GlanceClockEditRules.LANDSCAPE_WIDTH).fillMaxHeight()
                else Modifier.align(if (startOrTop) Alignment.TopCenter else Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = surfaceH * GlanceClockEditRules.PORTRAIT_MAX_HEIGHT)
                GlanceClockEditPanel(
                    prefs, onChange = { f -> latestEdit(f) }, onReset = { override = null; latestEdit { with(ClockPlacement) { it.withClockPos(landscapeNow, null) } } },
                    onDone = { doneEdit() }, onCancel = { cancelEdit() }, modifier = panelModifier,
                )
            }
        }
        if (indicator && prefs.glanceIndicator) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PageIndicator(
                count = 0, current = 0, vertical = false, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                onMenu = if (hasLaunchpad) onOpenLaunchpad else null, menuLabel = L10n.tr("Open Comms"), menuTag = "glance.indicator.launchpad",
                rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl, onGlance = {}, glanceCurrent = true, quiet = true, awakeNonce = awakeNonce,
            )
        }
    }
}

/**
 * W20 audit (NFR-15): the home scrim is sized so the theme's `InkBright` and `Ink` reach AA over every background; the Glance inks ARE those
 * two (the date, the weather, the music line), no dimmer one, so the scrim covers them. Light values are the light theme's tokens.
 */
object GlanceInk {
    fun clock(dark: Boolean): Color = if (dark) Color(0xFFE7E9F0) else Color(0xFF0E1018)
    fun secondary(dark: Boolean): Color = if (dark) Color(0xFFC9CDD6) else Color(0xFF2A2E3A)

    /**
     * Wave 60 (polish60, CFG-75 follow-up): a tight, near-opaque halo behind Glance text, so the clock and the music line stay legible over a
     * wallpaper the app cannot sample a colour from (a picture or the system's own live wallpaper, see `WallpaperSurfaces.kt`'s comment that
     * "a picture or the system wallpaper has no colour the app knows"). The scrim (`HomeScrim`/`HomeLegibility`) already covers the kinds the
     * app paints itself (solid, gradient, signal); this covers the rest without reverting the wave 59 marquee edge fade, which stays an alpha
     * mask on the text itself. Opposite tone of the ink (black halo behind the dark theme's light ink, white behind the light theme's dark
     * ink), centred (no offset, so it reads the same at every edge of a scrolling line), close enough to the glyph to stay a halo, not a glow.
     * At this alpha a pixel right next to a glyph is overwhelmingly the halo colour, so it clears WCAG's 3:1 against literal white or black
     * (`GlanceLegibilityW60Test`, `GlanceMusicLineContrastW60PixelTest`).
     */
    fun shadow(dark: Boolean): Shadow = Shadow(if (dark) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f), Offset.Zero, 10f)
}

/** The controls timeout in milliseconds (setting seconds, clamped). */
object GlanceControlsMs { fun of(seconds: Int): Long = com.bos.sphere.core.data.GlanceControls.clampSeconds(seconds) * 1000L }

/**
 * Wave 59 (glanceTop, CFG-75): the marquee's edge fade is an ALPHA MASK on the text (offscreen layer, a gradient drawn with [BlendMode.DstIn]), never a colour
 * painted over it, so a light, dark, live or picture wallpaper shows through unchanged. Only a clipped edge fades: the end while the text overflows, the start
 * once the text has started to move.
 */
object MarqueeFade {
    const val FADE_DP = 14f
    const val START_DELAY_MS = 1200

    /** Which edges fade (start, end) for a line that [scrolling] (overflows and moves) and has [moved] off its start. */
    fun edges(scrolling: Boolean, moved: Boolean): Pair<Boolean, Boolean> = (scrolling && moved) to scrolling
}

/**
 * Fades the content to transparent over [fadePx] at its start and/or end edge ([rtl]: the start is the right edge); with [clearPx] the outermost
 * [clearPx] are fully transparent first (the line under a corner icon). Paints no colour: an alpha mask over an offscreen layer.
 */
internal fun Modifier.fadeClippedEdges(start: Boolean, end: Boolean, fadePx: Float, rtl: Boolean, clearPx: Float = 0f): Modifier =
    if (!start && !end) this
    else this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val c = clearPx.coerceIn(0f, size.width / 2f)
            val f = fadePx.coerceAtMost(size.width / 2f - c).coerceAtLeast(1f)
            val left = if (rtl) end else start
            val right = if (rtl) start else end
            val dstIn = androidx.compose.ui.graphics.BlendMode.DstIn
            if (left) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), c, c + f), size = androidx.compose.ui.geometry.Size(c + f, size.height), blendMode = dstIn)
            if (right) drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - c - f, size.width - c), topLeft = Offset(size.width - c - f, 0f),
                size = androidx.compose.ui.geometry.Size(c + f, size.height), blendMode = dstIn,
            )
        }

/**
 * The song title (W25): one line, centred when it fits; when it is wider than its slot it scrolls as a marquee with both edges faded.
 * With reduced motion it does not scroll: it shows the beginning with an ellipsis. The text carries the test tag "glance.title.marquee"
 * or "glance.title.static" (fits, or reduced motion) so the state is observable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingLine(media: MediaInfo, tint: Color, marquee: Boolean, onActivate: () -> Unit, maxWidth: Dp = 280.dp, dark: Boolean = isDarkSurface()) {
    val line = GlanceText.nowPlayingLine(media.title, media.artist)
    val display = GlanceText.nowPlayingDisplay(media.title, media.artist)
    val spoken = L10n.tr("Now playing: %s", line)
    val showLabel = L10n.tr("Show playback controls")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // Wave 60 (polish60): the halo that keeps the title readable over a wallpaper colour the app cannot sample (GlanceInk.shadow); it does not
    // change the glyphs' metrics, so measuring with it costs nothing extra.
    val shadow = GlanceInk.shadow(dark)
    val style = androidx.compose.ui.text.TextStyle(color = tint, fontSize = GlanceText.MUSIC_SP.sp, shadow = shadow)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The text's room: the slot minus the 16 dp note glyph, its 8 dp gap and the 12 dp padding on each side.
    val room = with(density) { (maxWidth - 16.dp - 8.dp - 24.dp).roundToPx() }
    val natural = remember(display, density.fontScale) { measurer.measure(display, style, softWrap = false, maxLines = 1).size.width }
    val overflow = natural > room
    val scrolling = marquee && overflow
    val fade = with(density) { MarqueeFade.FADE_DP.dp.toPx() }
    // Wave 59 (glanceTop): the start edge is clipped only once the text has started to move (the marquee waits 1.2 s at its start first).
    var moved by remember(display, scrolling) { mutableStateOf(false) }
    LaunchedEffect(display, scrolling) { if (scrolling) { kotlinx.coroutines.delay(MarqueeFade.START_DELAY_MS.toLong()); moved = true } }
    val (fadeStart, fadeEnd) = MarqueeFade.edges(scrolling, moved)
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(
        Modifier.widthIn(max = maxWidth)
            .launcherItem("glance.nowplaying", focusable = true, onActivate = onActivate, ringRadiusDp = 16f)
            .clickable(onClickLabel = showLabel, role = Role.Button, onClick = onActivate)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Glyphs.Music, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            display, style = style, maxLines = 1, softWrap = false,
            overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag(if (scrolling) "glance.title.marquee" else "glance.title.static")
                .then(
                    if (scrolling) Modifier
                        // Wave 59 (glanceTop): the TEXT fades to transparent at a clipped edge (an alpha mask), so any wallpaper shows through; no colour is painted.
                        .fadeClippedEdges(fadeStart, fadeEnd, fade, rtl)
                        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MarqueeFade.START_DELAY_MS, repeatDelayMillis = 1200, spacing = MarqueeSpacing(32.dp))
                    else Modifier,
                ),
        )
    }
}

/**
 * The controls notch (W22): a black tab that hangs from the very top edge of the window (drawn from y = 0, behind the status bar and
 * the display cutout), its top corners flaring out in concave curves and its bottom corners rounded. It holds only Previous,
 * Play/Pause and Next as thin outline glyphs (48 dp targets, below the cutout); the title stays in the now-playing line under it.
 * Drops from above the edge with a spring (about 250 ms) and retracts the same way; reduced motion is a fade. Composed only while on
 * or animating out. [cutoutTop] pushes the icons down, never the shape.
 */
@Composable
internal fun ControlsNotch(
    visible: Boolean, reduced: Boolean, media: MediaInfo, areaWidthDp: Float, cutoutTop: Dp, modifier: Modifier,
    onFingerDown: () -> Unit, onFingerUp: () -> Unit, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit,
) {
    val fingerDown by androidx.compose.runtime.rememberUpdatedState(onFingerDown)
    val fingerUp by androidx.compose.runtime.rememberUpdatedState(onFingerUp)
    val progress = remember { Animatable(0f) }
    // W42: composed from the very first frame the notch is wanted (not one frame later from an effect), so a tap is never dropped before it exists.
    var composed by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduced) {
        if (visible) {
            composed = true
            if (reduced) progress.animateTo(1f, tween(120)) else progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f))
        } else {
            if (reduced) progress.animateTo(0f, tween(90)) else progress.animateTo(0f, tween(180))
            composed = false
        }
    }
    if (!composed) return
    val height = GlanceNotch.heightDp(cutoutTop.value).dp
    Box(
        // W42: the pop-in is DRAWN only (a graphicsLayer translation or alpha would also move / fade the hit area): the three targets sit at their
        // final place and take taps from the first frame, while the spring only changes where the black tab is painted.
        modifier.drawWithContent {
            val p = progress.value
            val dy = if (reduced) 0f else -(1f - p) * height.toPx()
            val a = if (reduced) p.coerceIn(0f, 1f) else 1f
            if (a < 1f) {
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint().apply { alpha = a })
                drawContent()
                drawContext.canvas.restore()
            } else translate(0f, dy) { this@drawWithContent.drawContent() }
        }
            .testTag("glance.notch")
            // Wave 23: the box, the drawn shape and the 48 dp targets are the same height, so a tap outside the black falls through to the home card.
            .width(GlanceNotch.widthDp(areaWidthDp).dp).height(cutoutTop + GlanceNotch.TARGET_DP.dp)
            .drawBehind {
                // W60 (owner: trace the reference shape exactly): hs is left at its path() default (h - corner), not GlanceNotch.SHOULDER_DP,
                // so the shoulder curve always lands exactly where the bottom corner's straight run starts, whatever the cutout height is on
                // this device. A fixed shoulder shorter than that left a straight vertical seam in the middle of the shape - a kink the
                // reference image's single smooth bulge does not have.
                val path = NotchShape.path(size.width, height.toPx(), GlanceNotch.FLARE_DP.dp.toPx(), GlanceNotch.CORNER_DP.dp.toPx())
                drawPath(path, Color.Black)
                // The one allowed extra: a hairline lighter edge so the black tab still reads on a black surface. W25: it fades out toward the top
                // edge (zero alpha at y = 0 and along the shoulders' start), so there is no line along the top and the notch melts into the status area.
                val fadeEnd = (height.toPx() - GlanceNotch.CORNER_DP.dp.toPx()) * 1.4f
                drawPath(
                    path, Brush.verticalGradient(0f to Color.Transparent, 0.35f * fadeEnd / size.height to Color.Transparent, fadeEnd / size.height to Color.White.copy(alpha = 0.16f), 1f to Color.White.copy(alpha = 0.16f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
            .pointerInput(Unit) { awaitEachGesture {
                // A finger on the notch (even one a button consumed) holds the timer; lifting it restarts the timer.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); fingerDown()
                do { val ev = awaitPointerEvent(PointerEventPass.Final) } while (ev.changes.any { it.pressed })
                fingerUp()
            } },
    ) {
        // The three 48 dp targets, evenly spread over the straight body, starting below the cutout. A target is as tall as the 48 dp
        // body below the cutout; its icon sits in the upper part, the spare height is only touch area (inside this Box's bounds).
        // Transport order is not mirrored in right-to-left (rewind left, forward right, like every player).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(
            Modifier.padding(top = cutoutTop, start = GlanceNotch.FLARE_DP.dp, end = GlanceNotch.FLARE_DP.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top,
        ) {
            GlanceControlButton("glance.prev", OutlineMediaGlyphs.Rewind, L10n.tr("Previous"), media.canPrev, onPrevious)
            // The glyph answers the tap at once: it flips to what the tap asked for, and the player's own state confirms it (the flip is
            // dropped when the real state arrives, or after PENDING_MS if the player never changed: a failed command shows as the old glyph).
            var pending by remember(media.playing, media.title) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(pending) { if (pending != null) { delay(GlanceNotch.PENDING_MS); pending = null } }
            val shownPlaying = pending ?: media.playing
            GlanceControlButton(
                "glance.playpause", if (shownPlaying) OutlineMediaGlyphs.Pause else OutlineMediaGlyphs.Play, if (shownPlaying) L10n.tr("Pause") else L10n.tr("Play"), true,
                { pending = !shownPlaying; onPlayPause() }, morph = true,
            )
            GlanceControlButton("glance.next", OutlineMediaGlyphs.Forward, L10n.tr("Next"), media.canNext, onNext)
        } }
    }
}

@Composable
private fun GlanceControlButton(id: String, glyph: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, morph: Boolean = false) {
    Box(
        Modifier.size(GlanceNotch.TARGET_DP.dp).clip(CircleShape)
            .testTag(id)
            .launcherItem(id, focusable = enabled, onActivate = onClick, ringRadiusDp = 24f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (morph) androidx.compose.animation.AnimatedContent(
            glyph, label = "glanceControlGlyph",
            transitionSpec = { (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(140), initialScale = 0.6f)).togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) + androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(100), targetScale = 0.6f)) },
        ) { g -> Icon(g, null, tint = Color.White, modifier = Modifier.padding(top = GlanceNotch.ICON_TOP_DP.dp).size(GlanceNotch.ICON_DP.dp)) }
        else Icon(glyph, null, tint = Color.White, modifier = Modifier.padding(top = GlanceNotch.ICON_TOP_DP.dp).size(GlanceNotch.ICON_DP.dp))
    }
}

@Composable
internal fun IndicatorButton(tag: String, label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).testTag(tag)
            .launcherItem(tag, focusable = true, onActivate = onClick, ringRadiusDp = 24f)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/** The hotspots: [MinimalHotspotLayer] with the assigned action's glyph drawn in each corner. */
@Composable
internal fun GlanceHotspotLayer(hotspots: List<Hotspot>, appsByKey: Map<String, AppEntry>, onRun: (CornerItem, Corner) -> Unit, tint: Color) {
    MinimalHotspotLayer(hotspots = hotspots, appsByKey = appsByKey, showHints = false, onRun = onRun, showIcons = true, iconTint = tint)
}
