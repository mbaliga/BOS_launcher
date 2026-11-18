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
import com.bos.sphere.core.data.GlanceContent
import com.bos.sphere.core.data.GlanceItem
import com.bos.sphere.core.data.GlanceWeather
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination
import com.bos.sphere.core.data.WeatherCodes
import com.bos.sphere.core.data.WeatherInfo
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.input.FocusKind
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure text rules of the Glance centre (testable). */
object GlanceText {
    /** The time pattern: 12 or 24 hour by the setting or the system, never seconds. */
    fun timePattern(format: ClockFormat, system24: Boolean): String = ClockText.timePattern(format, system24)

    /** The clock size in dp for a window of [w] x [h] dp at [fontScale]: it scales with the font but never outgrows the window. */
    fun clockSizeDp(w: Float, h: Float, fontScale: Float): Float {
        val base = minOf(w * 0.26f, h * 0.30f, 124f)
        // W20 audit: and never wider than the room between the two 48 dp edge handles ("88:88" is about 2.6 em wide, tabular digits).
        val room = ((w - 2f * CLOCK_SIDE_CLEAR_DP) / CLOCK_EM_WIDTH).coerceAtLeast(24f)
        return (base * fontScale.coerceIn(1f, 2f)).coerceAtMost(w * 0.28f).coerceAtMost(h * 0.42f).coerceAtMost(room)
    }

    /** Clear space on each side of the clock: the 48 dp edge handle plus a margin. */
    const val CLOCK_SIDE_CLEAR_DP = 56f
    /** The widest "HH:mm" in tabular digits, in em (four digits, the colon and the negative letter spacing), with a little air. */
    const val CLOCK_EM_WIDTH = 2.7f

    /** The one-line music text: "Title · Artist", or the title alone. */
    fun nowPlayingLine(title: String, artist: String) = if (artist.isBlank()) title else "$title · $artist"

    /**
     * W20 audit: the line as drawn. Each part is a bidi isolate, so a right-to-left title next to a Latin artist (or the other way round) keeps
     * its own order and the separator stays between them; with no artist it is the title alone. The spoken text stays [nowPlayingLine].
     */
    fun nowPlayingDisplay(title: String, artist: String) = if (artist.isBlank()) title else "\u2068$title\u2069 · \u2068$artist\u2069"
}

/**
 * HOM-23 (wave 16, owner request): the Glance home. A near-empty, quiet surface: the clock and date centred, the music that is
 * playing at the top (a tap pops the controls in, inactivity pops them out), an optional weather ambience behind the clock, up
 * to four hotspots in the corners and a small surface indicator at the bottom. One composition that rotates with the display.
 *
 * Layers, bottom to top: the base, the weather ambience, [underlay] (the two-slot signal background and its scrim, so the glow
 * lines and the dot grid stay readable over the weather), the gesture and tap layer, the centre, the music line, the controls,
 * the hotspots, the indicator. The composition order is the TalkBack order (clock, date, weather, now playing, controls, hotspots).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GlanceSurface(
    prefs: LauncherPrefs,
    weather: WeatherInfo?,
    media: MediaInfo?,
    hotspots: List<Hotspot>,
    appsByKey: Map<String, AppEntry>,
    rightDestinations: List<RightDestination>,
    hasLaunchpad: Boolean,
    onRun: (CornerItem, Corner) -> Unit,
    onClockTap: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /** Wave 17: the indicator's Launchpad glyph and its right-hand dots are real buttons. */
    onOpenLaunchpad: () -> Unit = {},
    onOpenRight: () -> Unit = {},
    gestures: HomeGestureSpec? = null,
    dark: Boolean = isDarkSurface(),
    underlay: @Composable () -> Unit = {},
) {
    val items = prefs.glanceContent
    val motion = rememberMotionPrefs()
    val reduced = motion.reduced
    val context = LocalContext.current
    val active = rememberRadarActive()
    val hideMs = GlanceControlsMs.of(prefs.glanceControlsSeconds)
    val ink = GlanceInk.clock(dark)
    val inkDim = GlanceInk.secondary(dark)

    // The pop-in controls: one pure state value (see ControlsPop), a timer that fires at its hideAt.
    var pop by remember { mutableStateOf(ControlsPop()) }
    val playing = media != null && GlanceItem.NowPlaying in items
    LaunchedEffect(playing, active) { if (!playing || !active) pop = pop.dismiss() }
    LaunchedEffect(pop) {
        if (pop.visible && !pop.fingerDown && pop.hideAt != ControlsPop.NEVER) {
            delay((pop.hideAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            pop = pop.expired(pop.hideAt)
        }
    }
    var awakeNonce by remember { mutableIntStateOf(0) }
    fun interact() { pop = pop.interact(SystemClock.uptimeMillis(), hideMs); awakeNonce++ }
    fun tapSurface() { pop = pop.tapSurface(SystemClock.uptimeMillis(), playing, hideMs); awakeNonce++ }

    val effect = if (GlanceContent.showsWeather(items)) GlanceWeather.effectOf(weather, GlanceWeather.isDaylight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) else null

    BoxWithConstraints(modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFFF3F3F6))) {
        val w = maxWidth.value; val h = maxHeight.value
        GlanceWeatherLayer(effect, prefs.glanceWeatherIntensity, dark)
        underlay()
        // The tap layer: a tap on empty surface pops the controls in (or out); the home gestures (swipes, long press) share it.
        Box(
            Modifier.fillMaxSize().homeGestures(gestures) { true }
                .pointerInput(Unit) { detectTapGestures(onTap = { tapSurface() }) }
                // decoration for accessibility: the keyboard and TalkBack way to the controls is the music line (a button)
                .clearAndSetSemantics { },
        )
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cutoutTop = with(density) { WindowInsets.displayCutout.getTop(density).toDp() }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GlanceCentre(prefs, weather, items, w, h, ink, inkDim, onClockTap, Modifier.align(Alignment.Center))
            // Music at the top: a stable slot (its height is reserved), the line only while something plays. W22: the line always sits below
            // the notch's bottom edge (the notch hangs over the top of the screen), so it never moves and the notch never covers it.
            // W25: the title keeps its own place (fixed padding under the status area) while the controls are away; when they come in it is pushed down
            // by the notch body (spring, snap with reduced motion) and moves back when they leave, so it is never behind or touching the notch.
            val push by animateDpAsState(
                if (playing && pop.visible) GlanceNotch.TITLE_PUSH_DP.dp else 0.dp,
                if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 420f), label = "glanceTitlePush",
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = GlanceNotch.TITLE_TOP_DP.dp).offset { IntOffset(0, push.roundToPx()) }.height(40.dp).testTag("glance.title.slot")) {
                if (playing && media != null) {
                    NowPlayingLine(media, inkDim, !reduced, onActivate = { tapSurface() }, maxWidth = (w - 2 * GlanceNotch.TITLE_SIDE_DP).coerceIn(120f, 280f).dp, edge = if (dark) Color.Black else Color(0xFFF3F3F6))
                }
            }
        }
        // The notch is drawn from y = 0 of the window (behind the status bar and the cutout), outside the safe area.
        if (playing && media != null) {
            ControlsNotch(pop.visible, reduced, media, w, cutoutTop, Modifier.align(Alignment.TopCenter),
                onFingerDown = { pop = pop.fingerDown() }, onFingerUp = { pop = pop.fingerUp(SystemClock.uptimeMillis(), hideMs) },
                onPrevious = { interact(); onPrevious() }, onPlayPause = { interact(); onPlayPause() }, onNext = { interact(); onNext() })
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GlanceHotspotLayer(hotspots, appsByKey, onRun, tint = inkDim)
            if (prefs.glanceIndicator) GlanceIndicator(hasLaunchpad, rightDestinations, awakeNonce, reduced, ink, Modifier.align(Alignment.BottomCenter), onOpenLaunchpad, onOpenRight)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceCentre(
    prefs: LauncherPrefs, weather: WeatherInfo?, items: Set<GlanceItem>, w: Float, h: Float,
    ink: Color, inkDim: Color, onClockTap: () -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val fontScale = LocalConfiguration.current.fontScale
    val now = rememberNow()
    val is24 = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val showTime = GlanceItem.Time in items
    val showDate = GlanceItem.Date in items
    val showWeather = GlanceItem.Weather in items && weather != null
    val showBattery = GlanceItem.Battery in items
    if (!showTime && !showDate && !showWeather && !showBattery) return
    val time = remember(now, prefs.clockFormat, is24) { SimpleDateFormat(GlanceText.timePattern(prefs.clockFormat, is24), locale).format(now) }
    val date = remember(now) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(now) }
    val sizeDp = GlanceText.clockSizeDp(w, h, fontScale)
    val dateSp = (sizeDp * 0.2f).coerceIn(15f, 26f)
    val tapLabel = L10n.tr("Open clock")
    // The clock is not mirrored in right-to-left: digits keep their order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier.padding(horizontal = GlanceText.CLOCK_SIDE_CLEAR_DP.dp).clip(RoundedCornerShape(24.dp))
                .launcherItem("glance.clock", focusable = true, onActivate = onClockTap, ringRadiusDp = 24f)
                .clickable(onClickLabel = tapLabel, role = Role.Button, onClick = onClockTap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTime) {
                // W20 audit: the estimate above can be a little off for a font with wide digits: if the text still overflows its box it shrinks
                // by a tenth until it fits (a state write from the text layout callback, the documented pattern), so it is never clipped.
                var fit by remember(sizeDp) { mutableFloatStateOf(1f) }
                Text(
                    time, color = ink, modifier = Modifier.semantics { testTag = "glance.time" }, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    onTextLayout = { if (it.hasVisualOverflow && fit > 0.3f) fit *= 0.9f },
                    style = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (sizeDp * fit).dp.toSp() }, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum", letterSpacing = (-1).sp),
                )
            }
            if (showDate || showWeather) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.Center) {
                    if (showDate) Text(date, modifier = Modifier.semantics { testTag = "glance.date" }, color = inkDim, fontSize = (if (showTime) dateSp else dateSp * 1.8f).sp, textAlign = TextAlign.Center)
                    if (showWeather && weather != null) {
                        Text(
                            (if (showDate) "· " else "") + weather.temperature(prefs.weatherFahrenheit), color = inkDim, fontSize = (if (showTime) dateSp else dateSp * 1.8f).sp,
                            modifier = Modifier.semantics { contentDescription = L10n.tr(WeatherCodes.description(weather.code)) + ", " + weather.temperature(prefs.weatherFahrenheit) },
                        )
                    }
                }
            }
            if (showBattery) {
                val pct = rememberBatteryPercent()
                if (pct >= 0) Text(L10n.tr("Battery %d%%", pct), color = inkDim, fontSize = (dateSp * 0.8f).sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * The song title (W25): one line, centred when it fits; when it is wider than its slot it scrolls as a marquee with both edges faded.
 * With reduced motion it does not scroll: it shows the beginning with an ellipsis. The text carries the test tag "glance.title.marquee"
 * or "glance.title.static" (fits, or reduced motion) so the state is observable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingLine(media: MediaInfo, tint: Color, marquee: Boolean, onActivate: () -> Unit, maxWidth: Dp = 280.dp, edge: Color = Color.Black) {
    val line = GlanceText.nowPlayingLine(media.title, media.artist)
    val display = GlanceText.nowPlayingDisplay(media.title, media.artist)
    val spoken = L10n.tr("Now playing: %s", line)
    val showLabel = L10n.tr("Show playback controls")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(color = tint, fontSize = 14.sp)
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
            display, color = tint, fontSize = 14.sp, maxLines = 1, softWrap = false,
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
    var composed by remember { mutableStateOf(false) }
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
        modifier.graphicsLayer {
            val p = progress.value
            if (reduced) alpha = p.coerceIn(0f, 1f) else translationY = -(1f - p) * height.toPx()
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

/**
 * The small, quiet row at the bottom centre: Launchpad (list glyph), home (current, brighter, not interactive), the right-side dots.
 * Wave 17: the list glyph and each dot are buttons (48 dp targets, labels) that open Launchpad and the right-hand destination.
 * Mirrors in RTL.
 */
@Composable
internal fun GlanceIndicator(
    hasLaunchpad: Boolean, right: List<RightDestination>, awakeNonce: Int, reduced: Boolean, ink: Color, modifier: Modifier = Modifier,
    onOpenLaunchpad: () -> Unit = {}, onOpenRight: () -> Unit = {},
) {
    var awake by remember { mutableStateOf(true) }
    LaunchedEffect(awakeNonce) { awake = true; delay(4000); awake = false }
    val a by animateFloatAsState(if (awake) 1f else 0.38f, tween(if (reduced) 0 else 600), label = "glanceIndicator")
    Row(
        modifier.padding(bottom = 4.dp).graphicsLayer { alpha = a },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (hasLaunchpad) IndicatorButton("glance.indicator.launchpad", L10n.tr("Open Launchpad"), onOpenLaunchpad) { Icon(Glyphs.Menu, null, tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(16.dp)) }
        // The current surface: not interactive, one spoken line.
        Box(Modifier.size(32.dp).semantics { contentDescription = L10n.tr("Glance home") }, contentAlignment = Alignment.Center) { Icon(Glyphs.Home, null, tint = ink, modifier = Modifier.size(18.dp)) }
        for (d in right) {
            val label = L10n.tr(if (d == RightDestination.Recents) "Open recents" else "Show the dock")
            IndicatorButton("glance.indicator.right.${d.name}", label, onOpenRight) { Box(Modifier.size(5.dp).background(ink.copy(alpha = 0.55f), CircleShape)) }
        }
    }
}

@Composable
private fun IndicatorButton(tag: String, label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
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

@Composable
internal fun rememberBatteryPercent(): Int {
    val context = LocalContext.current
    var pct by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        fun read(i: Intent?) {
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
            pct = if (level < 0) -1 else (level * 100f / scale).roundToInt().coerceIn(0, 100)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) = read(i) }
        read(runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED) }.getOrNull())
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return pct
}
