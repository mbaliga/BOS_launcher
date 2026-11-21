package com.bos.sphere.feature.grid

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * W45 (editChrome, HOM-12): the pure numbers of the single-page home edit mode: the live page at scale 0.88 with a scrim around it, the 56 dp top bar,
 * the 72 dp tray, how far the fixed chrome is dimmed. Plain JVM maths: the window and the insets go in (px), rectangles and a layer frame come out.
 */
object EditChromeGeometry {
    const val SCALE = 0.88f
    /** Landscape has no height to spare: the page shrinks further, never below this, to stay clear of the bar and the tray. */
    const val MIN_LANDSCAPE_SCALE = 0.60f
    const val TOP_BAR_DP = 56f
    const val TRAY_DP = 72f
    const val BUTTON_DP = 48f
    const val PAGE_CORNER_DP = 20f
    const val TITLE_SP = 14f
    const val HINT_DP = 32f
    /** The gap between the tray and what lies under it (the system inset or the dock). */
    const val TRAY_MARGIN_DP = 8f
    const val SCRIM_ALPHA = 0.35f
    const val DOT_DP = 2f
    const val DOT_ALPHA = 0.18f
    const val DOT_NEAR_ALPHA = 0.40f
    /** W48: on the light theme the dots are dark ink (white vanishes on a light wallpaper) and a little stronger. */
    const val DOT_ALPHA_LIGHT = 0.34f
    const val DOT_NEAR_ALPHA_LIGHT = 0.62f
    /** The four hotspots, the music line: dimmed to this and inert. The page dots too, but they stay the page switcher. */
    const val FIXED_DIM = 0.40f
    const val DOCK_DIM = 0.70f
    /** An enabled button is at full ink, a disabled one at this. */
    const val DISABLED_ALPHA = 0.38f
    const val ENTER_MS = 300
    const val REDUCED_MS = 120
    const val EXIT_MS = 250
    const val HINT_FADE_MS = 150
    const val HAIRLINE_ALPHA = 0.12f

    /** The bottom of the top bar in window px: the safe top inset plus 56 dp. */
    fun barBottomPx(safeTopPx: Float, density: Float): Float = safeTopPx + TOP_BAR_DP * density

    /**
     * W47 (editIntegrate): the room the item action bar must leave to the chrome, in dp, as (top, bottom): the top bar (the safe inset plus 56 dp) and the hint line
     * under it while it shows, and the tray (72 dp above [trayBottomDp], the dock's or the page dots' room). [tray] false (the page editor, which has no tray) leaves only the safe top.
     */
    fun actionBarReserveDp(safeTopDp: Float, hint: Boolean, trayBottomDp: Float, tray: Boolean = true, hintTopDp: Float = safeTopDp + TOP_BAR_DP): Pair<Float, Float> =
        (if (hint) hintTopDp + HINT_DP else safeTopDp + TOP_BAR_DP) to (if (tray) trayBottomDp + TRAY_DP else 0f)

    /** W48 (editGaps): the music line's strip under the top bar (the shield's 48 dp), dimmed like the hotspots. */
    const val MUSIC_STRIP_DP = 48f
    /** The extra room kept between the chrome and a dimmed hotspot. */
    const val HOTSPOT_GAP_DP = 8f

    /** The side of a hotspot's touch square at the page's scale, in px (the shield swallows exactly this square at each page corner). */
    fun hotspotSidePx(page: PageFrame, density: Float): Float = FixedHomeChrome.HOTSPOT_TOUCH_DP * page.scale * density

    /**
     * W48: the top of the hint line in window px. The hint used to sit right under the top bar, over the page's top corners (the dimmed hotspots) and the
     * music line's strip. It now starts below both: under the bar, under the strip and under the top hotspot squares (the lower of the three).
     */
    fun hintTopPx(page: PageFrame, safeTopPx: Float, density: Float): Float {
        val barBottom = barBottomPx(safeTopPx, density)
        return max(barBottom, max(page.rect.top + hotspotSidePx(page, density), barBottom + MUSIC_STRIP_DP * density))
    }

    /**
     * W48: where the tray's bottom edge may be (dp above the window's bottom): the requested one ([requestedDp]: the dock's or the page dots' room), unless the
     * tray would then lie over the page's bottom hotspot squares; then it is raised to sit above them (with [HOTSPOT_GAP_DP]). In landscape the page ends above
     * the tray, so nothing changes there.
     */
    fun trayBottomDp(page: PageFrame, density: Float, requestedDp: Float): Float {
        val h = page.windowHeightPx
        if (h <= 0f) return requestedDp
        val side = hotspotSidePx(page, density)
        val trayTop = h - (requestedDp + TRAY_DP) * density; val trayBottom = h - requestedDp * density
        val overlaps = trayTop < page.rect.bottom && trayBottom > page.rect.bottom - side
        if (!overlaps) return requestedDp
        return max(requestedDp, (h - page.rect.bottom + side) / density + HOTSPOT_GAP_DP)
    }

    /** The top of the tray in window px, when its bottom edge is [bottomOffsetPx] above the window's bottom (the safe inset, or the dock's top). */
    fun trayTopPx(heightPx: Float, bottomOffsetPx: Float, density: Float): Float = heightPx - bottomOffsetPx - TRAY_DP * density

    /** The page as the edit mode shows it: [scale] and the vertical translation of the home layer (its pivot is its own centre), and the rectangle it covers in window px. */
    data class PageFrame(val scale: Float, val translationY: Float, val rect: EdgeRect, val windowHeightPx: Float = 0f)

    /**
     * Portrait: scale 0.88 exactly. The page is centred, then eased down by half of what the top bar would otherwise cover (never past the window's bottom), so the
     * bar overlaps only the page's empty frame band. Landscape (wider than tall): the scale is the largest that clears the bar and a tray, between
     * [MIN_LANDSCAPE_SCALE] and 0.88, because 56 dp plus 72 dp of chrome is a third of a 360 dp high window.
     */
    fun pageFrame(widthPx: Float, heightPx: Float, safeTopPx: Float, density: Float): PageFrame {
        val w = widthPx.coerceAtLeast(1f); val h = heightPx.coerceAtLeast(1f)
        val barBottom = barBottomPx(safeTopPx, density)
        val landscape = w > h
        val trayRoom = (TRAY_DP + TRAY_MARGIN_DP) * density
        val scale = if (landscape) ((h - barBottom - trayRoom) / h).coerceIn(MIN_LANDSCAPE_SCALE, SCALE) else SCALE
        val ph = h * scale; val pw = w * scale
        val centredTop = (h - ph) / 2f
        val top = min(centredTop + 0.5f * max(0f, barBottom - centredTop), if (landscape) h - trayRoom - ph else h - ph).coerceAtLeast(0f)
        val left = (w - pw) / 2f
        return PageFrame(scale, (top + ph / 2f) - h / 2f, EdgeRect(left, top, left + pw, top + ph), h)
    }

    /** The layer frame at [progress] 0 (full screen) .. 1 (edit). Reduced motion: no scale animation, the page is at rest from the first frame and the scrim cross-fades with [EditCardGeometry.Frame.carouselAlpha]. */
    fun frame(page: PageFrame, progress: Float, reduced: Boolean): EditCardGeometry.Frame {
        val p = progress.coerceIn(0f, 1f)
        if (reduced) {
            val on = p > 0f
            return EditCardGeometry.Frame(if (on) page.scale else 1f, 0f, if (on) page.translationY else 0f, if (on) PAGE_CORNER_DP else 0f, p, if (on) 1f else 0f)
        }
        fun lerp(a: Float, b: Float) = a + (b - a) * p
        return EditCardGeometry.Frame(lerp(1f, page.scale), 0f, lerp(0f, page.translationY), PAGE_CORNER_DP * p, p, p)
    }

    /** From the edit page to the page editor's card ([t] 0..1): scale, translation and corner interpolate; the swing and alpha come from [b]. */
    fun blend(a: EditCardGeometry.Frame, b: EditCardGeometry.Frame, t: Float): EditCardGeometry.Frame {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun l(x: Float, y: Float) = x + (y - x) * t
        return b.copy(scale = l(a.scale, b.scale), translationX = l(a.translationX, b.translationX), translationY = l(a.translationY, b.translationY), cornerDp = l(a.cornerDp, b.cornerDp), carouselAlpha = l(a.carouselAlpha, b.carouselAlpha), shadowAlpha = l(a.shadowAlpha, b.shadowAlpha))
    }

    /** The scrim's alpha at [progress] (0 .. [SCRIM_ALPHA]); [carouselAlpha] of the frame is the same progress. */
    fun scrimAlpha(progress: Float): Float = SCRIM_ALPHA * progress.coerceIn(0f, 1f)
}

/** W45: "Editing page N of M"; [page] is 0-based, the count at least 1. */
object EditChromeText {
    fun titleArgs(page: Int, count: Int): Pair<Int, Int> { val m = count.coerceAtLeast(1); return (page.coerceIn(0, m - 1) + 1) to m }
}

/**
 * W45: the grid the edit mode draws. [active] is the rectangle of the active drag or selection, in the same px as the page grid (the page area, origin at the
 * page's own top-left; the grid's [GridGeometry] uses the same space): the handles agent sets it while an item is dragged or resized and clears it with null.
 * The dots within one cell of it brighten from [EditChromeGeometry.DOT_ALPHA] to [EditChromeGeometry.DOT_NEAR_ALPHA]. Write it from gesture callbacks,
 * never from composition. The dots read it inside their draw lambda, so a drag never recomposes the page.
 */
@Stable
class EditGridState {
    var active: EdgeRect? by mutableStateOf(null)
        private set
    fun update(rect: EdgeRect?) { active = rect }
    fun clear() { active = null }

    /** W47: the dots layer's own coordinates (set by the layer), so a ghost can convert its box to the page grid's px whatever the page's scale. */
    @Volatile var layer: androidx.compose.ui.layout.LayoutCoordinates? = null
}

/** The grid state of the page being edited; null outside edit mode (no dots). Provided by the home screen. */
val LocalEditGrid = compositionLocalOf<EditGridState?> { null }

/** W45: where the grid dots are and how bright. */
object EditGridDots {
    /** Every cell corner of a [g] grid: (columns + 1) by (rows + 1) points. */
    fun corners(g: GridGeometry): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>((g.columns + 1) * (g.rows + 1))
        for (r in 0..g.rows) for (c in 0..g.columns) out += (g.originX + c * g.pitchX) to (g.originY + r * g.pitchY)
        return out
    }

    /** The distance from (x, y) to [r] (0 inside it). */
    fun distance(x: Float, y: Float, r: EdgeRect): Float {
        val dx = max(max(r.left - x, x - r.right), 0f)
        val dy = max(max(r.top - y, y - r.bottom), 0f)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** 18% normally, 40% within one cell ([pitchX] / [pitchY], the larger) of [active]. */
    fun alpha(x: Float, y: Float, active: EdgeRect?, pitchX: Float, pitchY: Float, light: Boolean = false): Float =
        if (active != null && distance(x, y, active) <= max(abs(pitchX), abs(pitchY))) (if (light) EditChromeGeometry.DOT_NEAR_ALPHA_LIGHT else EditChromeGeometry.DOT_NEAR_ALPHA)
        else (if (light) EditChromeGeometry.DOT_ALPHA_LIGHT else EditChromeGeometry.DOT_ALPHA)

    /** The dot's colour: white on the dark theme, the dark ink on the light one. */
    fun ink(light: Boolean): androidx.compose.ui.graphics.Color = if (light) androidx.compose.ui.graphics.Color(0xFF14161F) else androidx.compose.ui.graphics.Color.White
}
