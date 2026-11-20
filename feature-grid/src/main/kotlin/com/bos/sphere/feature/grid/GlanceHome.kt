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
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.ControlsPop
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.wallpaper
import com.bos.sphere.core.data.RightDestination
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
 * weather are gone with their settings; the four hotspots, the clock, the music line and the page dots are the fixed chrome the home draws above its pager
 * ([FixedHomeChrome], the dots by the home), so they do not move while this page scrolls away. A standalone surface (tests, previews) may still draw its
 * own one-stop indicator ([indicator] true); the home passes false.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlanceSurface(
    prefs: LauncherPrefs,
    rightDestinations: List<RightDestination>,
    hasLaunchpad: Boolean,
    modifier: Modifier = Modifier,
    onOpenLaunchpad: () -> Unit = {},
    onOpenRight: () -> Unit = {},
    indicator: Boolean = true,
    /** Called when the user touches the surface: the host wakes the indicator it draws. */
    onAwake: () -> Unit = {},
    gestures: HomeGestureSpec? = null,
    dark: Boolean = isDarkSurface(),
    underlay: @Composable () -> Unit = {},
) {
    var awakeNonce by remember { mutableIntStateOf(0) }
    // Wave 34: with the system live wallpaper chosen the surface has no base colour at all, so the wallpaper behind the window shows through.
    val systemWallpaper = prefs.wallpaper(com.bos.sphere.core.data.WallpaperSurface.Glance).kind == com.bos.sphere.core.data.WallpaperKind.System
    BoxWithConstraints(modifier.fillMaxSize().background(if (systemWallpaper) Color.Transparent else if (dark) Color.Black else Color(0xFFF3F3F6)).testTag("glance.surface")) {
        underlay()
        // The tap layer: the home gestures (swipes, long press) share it; a tap only wakes the indicator.
        Box(
            Modifier.fillMaxSize().homeGestures(gestures) { true }
                .pointerInput(Unit) { detectTapGestures(onTap = { awakeNonce++; onAwake() }) }
                // decoration for accessibility: the surface has no content of its own
                .clearAndSetSemantics { },
        )
        if (indicator && prefs.glanceIndicator) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PageIndicator(
                count = 0, current = 0, vertical = false, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                onMenu = if (hasLaunchpad) onOpenLaunchpad else null, menuLabel = L10n.tr("Open Comms"), menuTag = "glance.indicator.launchpad",
                rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl, onGlance = {}, glanceCurrent = true, right = rightDestinations, onRight = onOpenRight, quiet = true, awakeNonce = awakeNonce,
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
}

/** The controls timeout in milliseconds (setting seconds, clamped). */
object GlanceControlsMs { fun of(seconds: Int): Long = com.bos.sphere.core.data.GlanceControls.clampSeconds(seconds) * 1000L }

/**
 * The song title (W25): one line, centred when it fits; when it is wider than its slot it scrolls as a marquee with both edges faded.
 * With reduced motion it does not scroll: it shows the beginning with an ellipsis. The text carries the test tag "glance.title.marquee"
 * or "glance.title.static" (fits, or reduced motion) so the state is observable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingLine(media: MediaInfo, tint: Color, marquee: Boolean, onActivate: () -> Unit, maxWidth: Dp = 280.dp, edge: Color = Color.Black) {
    val line = GlanceText.nowPlayingLine(media.title, media.artist)
    val display = GlanceText.nowPlayingDisplay(media.title, media.artist)
    val spoken = L10n.tr("Now playing: %s", line)
    val showLabel = L10n.tr("Show playback controls")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(color = tint, fontSize = GlanceText.MUSIC_SP.sp)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The text's room: the slot minus the 16 dp note glyph, its 8 dp gap and the 12 dp padding on each side.
    val room = with(density) { (maxWidth - 16.dp - 8.dp - 24.dp).roundToPx() }
    val natural = remember(display, density.fontScale) { measurer.measure(display, style, softWrap = false, maxLines = 1).size.width }
    val overflow = natural > room
    val scrolling = marquee && overflow
    val fade = with(density) { 14.dp.toPx() }
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
            display, color = tint, fontSize = GlanceText.MUSIC_SP.sp, maxLines = 1, softWrap = false,
            overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag(if (scrolling) "glance.title.marquee" else "glance.title.static")
                .then(
                    if (scrolling) Modifier
                        .drawWithContent {
                            drawContent()
                            // Both edges fade into the surface colour (no blend mode, no offscreen layer).
                            val f = fade.coerceAtMost(size.width / 2f)
                            drawRect(Brush.horizontalGradient(listOf(edge, Color.Transparent), 0f, f), size = androidx.compose.ui.geometry.Size(f, size.height))
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, edge), size.width - f, size.width), topLeft = Offset(size.width - f, 0f), size = androidx.compose.ui.geometry.Size(f, size.height))
                        }
                        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1200, spacing = MarqueeSpacing(32.dp))
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
                val path = NotchShape.path(size.width, height.toPx(), GlanceNotch.FLARE_DP.dp.toPx(), GlanceNotch.CORNER_DP.dp.toPx(), GlanceNotch.SHOULDER_DP.dp.toPx())
                drawPath(path, Color.Black)
                // The one allowed extra: a hairline lighter edge so the black tab still reads on a black surface. W25: it fades out toward the top
                // edge (zero alpha at y = 0 and along the shoulders' start), so there is no line along the top and the notch melts into the status area.
                val fadeEnd = GlanceNotch.SHOULDER_DP.dp.toPx() * 1.4f
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
            GlanceControlButton("glance.playpause", if (media.playing) OutlineMediaGlyphs.Pause else OutlineMediaGlyphs.Play, if (media.playing) L10n.tr("Pause") else L10n.tr("Play"), true, onPlayPause)
            GlanceControlButton("glance.next", OutlineMediaGlyphs.Forward, L10n.tr("Next"), media.canNext, onNext)
        } }
    }
}

@Composable
private fun GlanceControlButton(id: String, glyph: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(GlanceNotch.TARGET_DP.dp).clip(CircleShape)
            .testTag(id)
            .launcherItem(id, focusable = enabled, onActivate = onClick, ringRadiusDp = 24f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.TopCenter,
    ) { Icon(glyph, null, tint = Color.White, modifier = Modifier.padding(top = GlanceNotch.ICON_TOP_DP.dp).size(GlanceNotch.ICON_DP.dp)) }
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
