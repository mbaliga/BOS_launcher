package com.bos.sphere.feature.grid

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min

/**
 * CFG-18 (wave 39): the pure geometry of the floating preview window. Everything is in dp, in the window's own coordinates
 * (x to the right, y down; physical, not start/end). No Android types, so every rule is tested on the JVM.
 *
 * The window is a title bar on top of the scaled home. Minimised, it is only a small pill.
 */
object PreviewWindowGeometry {
    const val BAR_DP = 48f
    /** The bar (and so the window) never gets narrower than this, so the title and the two buttons always fit. */
    const val BAR_MIN_W = 136f
    const val PILL_W = 112f
    const val PILL_H = 40f
    /** At least this much of the bar stays inside the window when the preview is bigger than the window it floats over. */
    const val REACH = 48f
    const val MARGIN = 12f
    /** The default anchor sits this far under the top: below the Ground Control header, as the fixed dock did. */
    const val TOP_DEFAULT = 64f
    /** Arrow keys move, and Shift+arrows resize, by this much. */
    const val KEY_STEP = 16f
    const val SNAP_DIST = 32f
    const val MAX_FRACTION = 0.7f

    data class Size(val w: Float, val h: Float)
    data class Pos(val x: Float, val y: Float)

    /** Shortest allowed long side of the home picture: the dock's own minimum. */
    val MIN_LONG: Float get() = DOCK_MIN
    private const val DOCK_MIN = 72f

    /** Largest long side: [MAX_FRACTION] of the window's shorter side (never below the minimum). */
    fun maxLong(areaW: Float, areaH: Float): Float = max(DOCK_MIN, min(areaW, areaH) * MAX_FRACTION)

    fun clampLong(long: Float, areaW: Float, areaH: Float): Float =
        if (long.isNaN()) DOCK_MIN else long.coerceIn(DOCK_MIN, maxLong(areaW, areaH))

    /** The long side the dock always had (see [DockGeometry.size]); what the window starts at. */
    fun defaultLong(areaW: Float, areaH: Float, homeW: Int, homeH: Int): Float {
        val b = DockGeometry.size(areaW, areaH, homeW, homeH)
        return max(b.widthDp, b.heightDp)
    }

    fun contentSize(long: Float, aspect: Float): Size =
        if (aspect >= 1f) Size(long, long / aspect) else Size(long * aspect, long)

    fun windowSize(long: Float, aspect: Float, minimised: Boolean): Size {
        if (minimised) return Size(PILL_W, PILL_H)
        val c = contentSize(long, aspect)
        return Size(max(c.w, BAR_MIN_W), BAR_DP + c.h)
    }

    /** Top-end under the header; mirrored to top-start in a right-to-left locale. */
    fun defaultPos(areaW: Float, w: Float, rtl: Boolean): Pos = Pos(if (rtl) MARGIN else areaW - w - MARGIN, TOP_DEFAULT)

    private fun lo(area: Float, size: Float) = if (size <= area) 0f else -(size - REACH)
    private fun hi(area: Float, size: Float) = if (size <= area) area - size else area - REACH

    /** Fully inside the area when it fits; otherwise it overhangs but at least [REACH] of it (the bar) stays inside. */
    fun clamp(x: Float, y: Float, w: Float, h: Float, areaW: Float, areaH: Float): Pos =
        Pos(x.coerceIn(lo(areaW, w), hi(areaW, w)), y.coerceIn(lo(areaH, h), hi(areaH, h)))

    /** On release: an axis within [SNAP_DIST] of an edge margin snaps to it; the nearest wins. The result is clamped. */
    fun snap(x: Float, y: Float, w: Float, h: Float, areaW: Float, areaH: Float): Pos {
        fun axis(v: Float, area: Float, size: Float): Float {
            val a = MARGIN
            val b = area - size - MARGIN
            val da = kotlin.math.abs(v - a)
            val db = kotlin.math.abs(v - b)
            return when {
                b < a -> v
                da <= db && da <= SNAP_DIST -> a
                db < da && db <= SNAP_DIST -> b
                else -> v
            }
        }
        val c = clamp(x, y, w, h, areaW, areaH)
        return clamp(axis(c.x, areaW, w), axis(c.y, areaH, h), w, h, areaW, areaH)
    }

    /**
     * A drag of the bottom-end handle by ([dx], [dy]) dp (physical): the scale that makes the picture's corner follow the finger,
     * so the aspect is kept. In a right-to-left layout the handle sits bottom-left, so a drag to the left grows the window.
     */
    fun resizedLong(long: Float, aspect: Float, dx: Float, dy: Float, rtl: Boolean, areaW: Float, areaH: Float): Float {
        val c = contentSize(long, aspect)
        val dxs = if (rtl) -dx else dx
        val denom = c.w * c.w + c.h * c.h
        val scale = 1f + (dxs * c.w + dy * c.h) / denom
        return clampLong(long * scale.coerceAtLeast(0.05f), areaW, areaH)
    }

    /** After the window changed width from [oldW] to [newW]: left-to-right keeps the left edge, right-to-left keeps the right edge. */
    fun keepAnchor(x: Float, oldW: Float, newW: Float, rtl: Boolean): Float = if (rtl) x + (oldW - newW) else x
}

/** What is remembered for one orientation (NaN = not set, the default is used) plus the shared minimised flag. */
data class PreviewWindowSaved(val x: Float = Float.NaN, val y: Float = Float.NaN, val long: Float = Float.NaN) {
    val isDefault: Boolean get() = x.isNaN() && y.isNaN() && long.isNaN()
}

/** Text form of [PreviewWindowSaved] for the preference file: "x,y,long", NaN for a missing number; any garbage reads as the default. */
object PreviewWindowCodec {
    fun encode(s: PreviewWindowSaved): String = "${num(s.x)},${num(s.y)},${num(s.long)}"
    private fun num(v: Float) = if (v.isNaN() || v.isInfinite()) "-" else v.toString()

    fun decode(raw: String?): PreviewWindowSaved {
        val p = raw?.split(',') ?: return PreviewWindowSaved()
        if (p.size != 3) return PreviewWindowSaved()
        fun f(s: String) = s.trim().toFloatOrNull()?.takeIf { it.isFinite() } ?: Float.NaN
        return PreviewWindowSaved(f(p[0]), f(p[1]), f(p[2]))
    }
}

/**
 * The preference file of the preview window (device-local UI state: positions are meaningless on another device, so the file is
 * excluded from backup and transfer, see StateSchema.BACKUP_EXCLUDED_PREF_FILES). Opened with TolerantPrefs.
 */
class PreviewWindowStore(private val sp: SharedPreferences) {
    fun load(landscape: Boolean): PreviewWindowSaved = PreviewWindowCodec.decode(runCatching { sp.getString(key(landscape), null) }.getOrNull())
    fun save(landscape: Boolean, s: PreviewWindowSaved) { sp.edit().putString(key(landscape), PreviewWindowCodec.encode(s)).apply() }
    fun minimised(): Boolean = runCatching { sp.getBoolean(KEY_MIN, false) }.getOrDefault(false)
    fun saveMinimised(v: Boolean) { sp.edit().putBoolean(KEY_MIN, v).apply() }

    companion object {
        const val FILE = "preview_window"
        private const val KEY_MIN = "minimised"
        private fun key(landscape: Boolean) = if (landscape) "land" else "port"
    }
}

/** The live state of the window while it is on screen. Positions are written only from gesture and key handlers, never during composition. */
class PreviewWindowModel(private val store: PreviewWindowStore?, val landscape: Boolean) {
    private val first = store?.load(landscape) ?: PreviewWindowSaved()
    var x by mutableFloatStateOf(first.x)
    var y by mutableFloatStateOf(first.y)
    var long by mutableFloatStateOf(first.long)
    var minimised by mutableStateOf(store?.minimised() ?: false)

    fun persist() {
        store?.save(landscape, PreviewWindowSaved(x, y, long))
        store?.saveMinimised(minimised)
    }

    fun resetPosition() { x = Float.NaN; y = Float.NaN; persist() }
}
