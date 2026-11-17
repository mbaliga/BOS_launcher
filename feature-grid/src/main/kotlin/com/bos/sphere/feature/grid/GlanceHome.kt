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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.bos.sphere.core.design.HyleColors
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
        return (base * fontScale.coerceIn(1f, 2f)).coerceAtMost(w * 0.28f).coerceAtMost(h * 0.42f)
    }

    /** The one-line music text: "Title · Artist", or the title alone. */
    fun nowPlayingLine(title: String, artist: String) = if (artist.isBlank()) title else "$title · $artist"
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
    val ink = if (dark) HyleColors.InkBright else Color(0xFF101016)
    val inkDim = if (dark) Color(0xFF9A9AA6) else Color(0xFF55555F)

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
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GlanceCentre(prefs, weather, items, w, h, ink, inkDim, onClockTap, Modifier.align(Alignment.Center))
            // Music at the top: a stable slot (its height is reserved), the line only while something plays.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).height(40.dp)) {
                if (playing && media != null) {
                    NowPlayingLine(media, inkDim, !reduced, shown = !pop.visible, onActivate = { tapSurface() })
                }
            }
            if (playing && media != null) {
                ControlsPill(pop.visible, reduced, media, ink, Modifier.align(Alignment.TopCenter),
                    onFingerDown = { pop = pop.fingerDown() }, onFingerUp = { pop = pop.fingerUp(SystemClock.uptimeMillis(), hideMs) },
                    onPrevious = { interact(); onPrevious() }, onPlayPause = { interact(); onPlayPause() }, onNext = { interact(); onNext() })
            }
            GlanceHotspotLayer(hotspots, appsByKey, onRun, tint = inkDim)
            if (prefs.glanceIndicator) GlanceIndicator(hasLaunchpad, rightDestinations, awakeNonce, reduced, ink, Modifier.align(Alignment.BottomCenter))
        }
    }
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
            modifier.padding(horizontal = 76.dp).clip(RoundedCornerShape(24.dp))
                .launcherItem("glance.clock", focusable = true, onActivate = onClockTap, ringRadiusDp = 24f)
                .clickable(onClickLabel = tapLabel, role = Role.Button, onClick = onClockTap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTime) {
                Text(
                    time, color = ink, modifier = Modifier.semantics { testTag = "glance.time" }, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { sizeDp.dp.toSp() }, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum", letterSpacing = (-1).sp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingLine(media: MediaInfo, tint: Color, marquee: Boolean, shown: Boolean, onActivate: () -> Unit) {
    var overflow by remember(media.title, media.artist) { mutableStateOf(false) }
    val line = GlanceText.nowPlayingLine(media.title, media.artist)
    val spoken = L10n.tr("Now playing: %s", line)
    val showLabel = L10n.tr("Show playback controls")
    Row(
        Modifier.widthIn(max = 280.dp).graphicsLayer { alpha = if (shown) 1f else 0f }
            .launcherItem("glance.nowplaying", focusable = true, onActivate = onActivate, ringRadiusDp = 16f)
            .clickable(onClickLabel = showLabel, role = Role.Button, onClick = onActivate)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Glyphs.Music, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            line, color = tint, fontSize = 14.sp, maxLines = 1, softWrap = false,
            overflow = if (marquee && overflow) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow },
            modifier = if (marquee && overflow) Modifier.basicMarquee(iterations = 3) else Modifier,
        )
    }
}

/**
 * The notch-like black pill that drops from the top edge: Previous, Play/Pause and Next (48 dp targets) above the title. A spring
 * scale and translate (about 250 ms); with reduced motion a fade. It stays composed only while it is on or animating out.
 */
@Composable
private fun ControlsPill(
    visible: Boolean, reduced: Boolean, media: MediaInfo, ink: Color, modifier: Modifier,
    onFingerDown: () -> Unit, onFingerUp: () -> Unit, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit,
) {
    val fingerDown by androidx.compose.runtime.rememberUpdatedState(onFingerDown)
    val fingerUp by androidx.compose.runtime.rememberUpdatedState(onFingerUp)
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(visible, reduced) {
        if (visible) {
            composed = true
            if (reduced) progress.animateTo(1f, tween(120)) else progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 520f))
        } else {
            if (reduced) progress.animateTo(0f, tween(90)) else progress.animateTo(0f, tween(160))
            composed = false
        }
    }
    if (!composed) return
    val line = GlanceText.nowPlayingLine(media.title, media.artist)
    Column(
        modifier.graphicsLayer {
            val p = progress.value
            if (reduced) alpha = p.coerceIn(0f, 1f) else {
                alpha = (p * 2f).coerceIn(0f, 1f)
                val s = 0.72f + 0.28f * p
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                translationY = -(1f - p) * size.height
            }
        }
            .widthIn(min = 232.dp, max = 320.dp)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .pointerInput(Unit) { awaitEachGesture {
                // A finger on the pill (even one a button consumed) holds the timer; lifting it restarts the timer.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); fingerDown()
                do { val ev = awaitPointerEvent(PointerEventPass.Final) } while (ev.changes.any { it.pressed })
                fingerUp()
            } }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlanceControlButton("glance.prev", MediaGlyphs.Previous, L10n.tr("Previous track"), media.canPrev, onPrevious)
            GlanceControlButton("glance.playpause", if (media.playing) MediaGlyphs.Pause else MediaGlyphs.Play, if (media.playing) L10n.tr("Pause") else L10n.tr("Play"), true, onPlayPause, big = true)
            GlanceControlButton("glance.next", MediaGlyphs.Next, L10n.tr("Next track"), media.canNext, onNext)
        }
        Text(line, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp, top = 2.dp))
    }
}

@Composable
private fun GlanceControlButton(id: String, glyph: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, big: Boolean = false) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .launcherItem(id, focusable = enabled, onActivate = onClick, ringRadiusDp = 24f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, null, tint = Color.White, modifier = Modifier.size(if (big) 26.dp else 22.dp)) }
}

/** The small, quiet row at the bottom centre: Launchpad (list glyph), home (current, brighter), the right-side dots. Mirrors in RTL. */
@Composable
internal fun GlanceIndicator(hasLaunchpad: Boolean, right: List<RightDestination>, awakeNonce: Int, reduced: Boolean, ink: Color, modifier: Modifier = Modifier) {
    var awake by remember { mutableStateOf(true) }
    LaunchedEffect(awakeNonce) { awake = true; delay(4000); awake = false }
    val a by animateFloatAsState(if (awake) 1f else 0.38f, tween(if (reduced) 0 else 600), label = "glanceIndicator")
    val spoken = buildString {
        append(L10n.tr("Glance home"))
        if (hasLaunchpad) append(". ").append(L10n.tr("Launchpad is on the left edge"))
        // Honest wording: say what the right edge really is (the recents panel or the dock rail), not always "recent apps".
        for (d in right) append(". ").append(L10n.tr(if (d == RightDestination.Recents) "Recent apps are on the right edge" else "Dock is on the right edge"))
    }
    Row(
        modifier.padding(bottom = 18.dp).graphicsLayer { alpha = a }.semantics { contentDescription = spoken }.clearAndSetSemanticsKeep(spoken),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (hasLaunchpad) Icon(Glyphs.Menu, null, tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
        Icon(Glyphs.Home, null, tint = ink, modifier = Modifier.size(18.dp))
        if (right.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            for (d in right) Box(Modifier.size(5.dp).background(ink.copy(alpha = 0.55f), CircleShape))
        }
    }
}

private fun Modifier.clearAndSetSemanticsKeep(text: String): Modifier = this.clearAndSetSemantics { contentDescription = text }

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
