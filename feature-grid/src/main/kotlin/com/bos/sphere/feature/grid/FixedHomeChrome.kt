package com.bos.sphere.feature.grid

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.ControlsPop
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MusicLineWidth
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay

/**
 * Wave 38 (glanceFixed): the pure rules of the fixed chrome layer.
 *
 * The four hotspots, the music line and the page dots belong to the HOME, not to a page: they are drawn once above the pager, so a swipe between Glance and
 * any page (or the scroll) moves only the content under them. [chromeBoundsAreStable] is the contract the tests hold on to: whatever the pager offset, the
 * layer's bounds are the window's, so nothing in it can scroll.
 */
object FixedHomeChrome {
    /** The music line shows on EVERY page while something plays and the setting is on (no other condition: not the page, not the pager offset). */
    fun musicShown(showMusic: Boolean, media: MediaInfo?): Boolean = showMusic && media != null

    /** The media listener only needs to run while the music line can show. */
    fun listensToMedia(showMusic: Boolean): Boolean = showMusic

    /** Side margin of the music line when no top hotspot is drawn: the line runs edge to edge, with this much breathing room. */
    const val EDGE_MARGIN_DP = 12f

    /**
     * The widest the music line may be in a window [windowDp] wide. A top hotspot reserves [GlanceNotch.TITLE_SIDE_DP] on its side (the line stays centred, so the wider
     * reserve wins) and the line is capped at 280 dp; with no top hotspot at all it runs edge to edge (window width less [EDGE_MARGIN_DP] each side, no cap).
     * Wave 59 (glanceTop): [topLeft] / [topRight] mean a hotspot that DRAWS there (enabled and assigned, [com.bos.sphere.core.data.GlanceHotspots.drawnOnScreen]),
     * and [MusicLineWidth.FullWidth] always runs edge to edge (the corner icons then sit over the faded ends; their touch squares stay on top).
     */
    fun titleWidthDp(windowDp: Float, topLeft: Boolean, topRight: Boolean, width: MusicLineWidth = MusicLineWidth.FitAroundCorners): Float =
        if (width == MusicLineWidth.FullWidth || (!topLeft && !topRight)) (windowDp - 2 * EDGE_MARGIN_DP).coerceAtLeast(120f)
        else (windowDp - 2 * GlanceNotch.TITLE_SIDE_DP).coerceIn(120f, 280f)

    /** Wave 59: with Full width, the line is clear (transparent) for this much at a drawn top corner, under the corner glyph, then fades in over [UNDER_CORNER_FADE_DP]. */
    const val UNDER_CORNER_CLEAR_DP = 46f
    const val UNDER_CORNER_FADE_DP = 28f

    /** A hotspot's reserved touch area (dp): at least the 48 dp minimum, so it always wins its corner over the pager. */
    const val HOTSPOT_TOUCH_DP = 64f

    /** The pager gets the touch everywhere outside the hotspots' corner squares: true when [x],[y] (dp, window space) is outside all of them. */
    fun pagerOwns(x: Float, y: Float, w: Float, h: Float, corners: Set<Corner>): Boolean = corners.none { c ->
        val inX = if (c == Corner.TopLeft || c == Corner.BottomLeft) x < HOTSPOT_TOUCH_DP else x > w - HOTSPOT_TOUCH_DP
        val inY = if (c == Corner.TopLeft || c == Corner.TopRight) y < HOTSPOT_TOUCH_DP else y > h - HOTSPOT_TOUCH_DP
        inX && inY
    }
}

/**
 * The fixed chrome above the home pager (wave 41: no clock any more, the clock is part of the Glance page): the music line with its pop-in controls notch at the top and the four (or fewer) hotspots in the corners.
 * It fills the window and has no scroll or pager offset of its own, so it stays exactly in place on every page and during a swipe. The music line is
 * the one from the old Glance notch (same text, 14 sp, marquee, pop-in controls), now shown whenever [media] is active and [showMusic] is on.
 * Layers bottom to top: the music line, the controls notch, the hotspots. Composition order is the TalkBack order (now playing, controls, hotspots).
 */
@Composable
fun FixedHomeChrome(
    media: MediaInfo?,
    showMusic: Boolean,
    controlsSeconds: Int,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onAwake: () -> Unit = {},
    /** The four (or fewer) hotspots, drawn above the music line: the home passes its corner layer, so the chosen corner style (Holo, Sony discs, bubbles) applies. */
    corners: @Composable () -> Unit = {},
    dark: Boolean = isDarkSurface(),
    /** Whether a hotspot is DRAWN in the top-left and top-right screen corners (enabled and holding something); with neither, the music line runs edge to edge (owner request). */
    topLeftHotspot: Boolean = true,
    topRightHotspot: Boolean = true,
    /** Wave 59: the "Music line width" setting (fit around the drawn corners, or always full width). */
    musicWidth: MusicLineWidth = MusicLineWidth.FitAroundCorners,
    /** Wave 55: the regular battery and signal indicators ([RegularIndicators]), drawn under the music line (or in its place when nothing plays); null = not shown. */
    regular: Pair<IndicatorReading, IndicatorReading>? = null,
    low: Long = 0xFFFF4D4D,
) {
    val motion = rememberMotionPrefs()
    val reduced = motion.reduced
    val active = rememberRadarActive()
    val hideMs = GlanceControlsMs.of(controlsSeconds)
    val inkDim = GlanceInk.secondary(dark)
    val playing = FixedHomeChrome.musicShown(showMusic, media)

    // The pop-in controls: one pure state value (see ControlsPop), a timer that fires at its hideAt.
    var pop by remember { mutableStateOf(ControlsPop()) }
    LaunchedEffect(playing, active) { if (!playing || !active) pop = pop.dismiss() }
    LaunchedEffect(pop) {
        if (pop.visible && !pop.fingerDown && pop.hideAt != ControlsPop.NEVER) {
            delay((pop.hideAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            pop = pop.expired(pop.hideAt)
        }
    }
    fun interact() { pop = pop.interact(SystemClock.uptimeMillis(), hideMs); onAwake() }
    fun tapLine() { pop = pop.tapSurface(SystemClock.uptimeMillis(), playing, hideMs); onAwake() }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().testTag("home.chrome")) {
        val w = maxWidth.value
        val density = LocalDensity.current
        val cutoutTop = with(density) { WindowInsets.displayCutout.getTop(density).toDp() }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // The title keeps its own place (fixed padding under the status area) while the controls are away; when they come in it is pushed down by the
            // notch body (spring, snap with reduced motion) and moves back when they leave, so it is never behind or touching the notch.
            val push by animateDpAsState(
                if (playing && pop.visible) GlanceNotch.TITLE_PUSH_DP.dp else 0.dp,
                if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 420f), label = "chromeTitlePush",
            )
            if (regular != null) {
                RegularIndicators(regular.first, regular.second, dark, Modifier.align(Alignment.TopCenter).padding(top = RegularIndicatorGeometry.topDp(playing && media != null).dp).offset { IntOffset(0, push.roundToPx()) }, low = low)
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = GlanceNotch.TITLE_TOP_DP.dp).offset { IntOffset(0, push.roundToPx()) }.height(40.dp).testTag("glance.title.slot")) {
                if (playing && media != null) {
                    // Wave 59: with Full width a drawn top corner sits over the line's end: that end of the whole line (glyph and text) fades out under it,
                    // so the corner icon reads clearly (an alpha mask, no colour; physical sides, like the corners).
                    val under = musicWidth == MusicLineWidth.FullWidth
                    val fadeUnder = with(density) { FixedHomeChrome.UNDER_CORNER_FADE_DP.dp.toPx() }
                    val clearUnder = with(density) { FixedHomeChrome.UNDER_CORNER_CLEAR_DP.dp.toPx() }
                    Box((if (under) Modifier.fillMaxWidth() else Modifier).fadeClippedEdges(under && topLeftHotspot, under && topRightHotspot, fadeUnder, rtl = false, clearPx = clearUnder), contentAlignment = Alignment.Center) {
                    NowPlayingLine(media, inkDim, !reduced, onActivate = { tapLine() }, maxWidth = FixedHomeChrome.titleWidthDp(w, topLeftHotspot, topRightHotspot, musicWidth).dp, dark = dark)
                    }
                }
            }
        }
        // The notch is drawn from y = 0 of the window (behind the status bar and the cutout), outside the safe area.
        if (playing && media != null) {
            ControlsNotch(pop.visible, reduced, media, w, cutoutTop, Modifier.align(Alignment.TopCenter),
                onFingerDown = { pop = pop.fingerDown() }, onFingerUp = { pop = pop.fingerUp(SystemClock.uptimeMillis(), hideMs) },
                onPrevious = { interact(); onPrevious() }, onPlayPause = { interact(); onPlayPause() }, onNext = { interact(); onNext() })
        }
        corners()
    }
}
