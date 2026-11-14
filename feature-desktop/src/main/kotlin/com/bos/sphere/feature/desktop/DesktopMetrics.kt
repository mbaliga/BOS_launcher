package com.bos.sphere.feature.desktop

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Window size class from width in dp, same breakpoints as core-capabilities (EXT-05). */
enum class SizeClass {
    Compact, Medium, Expanded;
    companion object {
        fun of(widthDp: Float): SizeClass = when {
            widthDp < 600f -> Compact
            widthDp < 840f -> Medium
            else -> Expanded
        }
    }
}

data class InsetsPx(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    companion object { val None = InsetsPx() }
}

data class RectPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun intersects(o: RectPx) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun contains(o: RectPx) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom
}

enum class DockEdge { Bottom, Left, Right }

/** Dock preferences remembered per display (EXT-09). */
enum class DockMode(val label: String) { Bottom("Bottom"), Left("Left"), Right("Right"), Hidden("Hidden") }

/** The desktop surface layout, computed only from the window's own metrics (EXT-04). All rects are in window pixels. */
data class DesktopLayout(
    val sizeClass: SizeClass,
    /** Pixels per dp used for this window (display density times any profile override). */
    val scale: Float,
    val widthDp: Float,
    val heightDp: Float,
    val iconDp: Float,
    val cellDp: Float,
    val columns: Int,
    val rows: Int,
    /** Free area for icons and widgets: the window minus system insets and the dock. */
    val workArea: RectPx,
    /** Null when the dock is hidden. Never intersects the system inset regions (EXT-08). */
    val dock: RectPx?,
    val dockThicknessDp: Float,
)

object DesktopMetrics {
    private const val BASE_DPI = 160f

    /**
     * Lays out the desktop for a window of [widthPx] x [heightPx] at [densityDpi].
     *
     * [insets] are the system bars and cutouts reported for this window; the system taskbar shows up there
     * on displays where the system draws one. [systemHomePresent] adds a little extra clearance on the edge
     * the taskbar occupies so the dock never touches it (EXT-08). [densityOverrideDpi] is the per-display
     * profile value (EXT-09).
     */
    fun compute(
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int,
        insets: InsetsPx = InsetsPx.None,
        dockMode: DockMode = DockMode.Bottom,
        systemHomePresent: Boolean = false,
        densityOverrideDpi: Int? = null,
    ): DesktopLayout {
        val dpi = (densityOverrideDpi?.takeIf { it > 0 } ?: densityDpi).coerceAtLeast(60)
        val scale = dpi / BASE_DPI
        val wDp = widthPx / scale
        val hDp = heightPx / scale
        val sc = SizeClass.of(wDp)

        val shortDp = min(wDp, hDp)
        // Icon size follows the size class but never exceeds a fraction of the short side, so a small split window is not overfull.
        val preferredIcon = when (sc) { SizeClass.Compact -> 44f; SizeClass.Medium -> 52f; SizeClass.Expanded -> 60f }
        val icon = min(preferredIcon, max(24f, shortDp / 6f))
        val cell = icon * 1.7f
        val dockThickness = icon + 20f

        // Usable window: inside the insets.
        val usable = RectPx(
            insets.left.coerceIn(0, widthPx),
            insets.top.coerceIn(0, heightPx),
            (widthPx - insets.right).coerceIn(insets.left.coerceIn(0, widthPx), widthPx),
            (heightPx - insets.bottom).coerceIn(insets.top.coerceIn(0, heightPx), heightPx),
        )
        val edge = when (dockMode) { DockMode.Left -> DockEdge.Left; DockMode.Right -> DockEdge.Right; else -> DockEdge.Bottom }
        // A thin dock on a very small window; the dock must leave room for content.
        val clearancePx = if (systemHomePresent) (8f * scale).roundToInt() else 0
        val thickPx = (dockThickness * scale).roundToInt()

        var work = usable
        var dock: RectPx? = null
        if (dockMode != DockMode.Hidden) {
            val maxThick = if (edge == DockEdge.Bottom) usable.height / 3 else usable.width / 3
            val t = min(thickPx, maxThick).coerceAtLeast(0)
            val pad = if (t > 0) clearancePx else 0
            when (edge) {
                DockEdge.Bottom -> {
                    val b = usable.bottom - pad
                    val top = max(usable.top, b - t)
                    dock = RectPx(usable.left, top, usable.right, max(top, b))
                    work = usable.copy(bottom = top)
                }
                DockEdge.Left -> {
                    val l = usable.left + pad
                    val r = min(usable.right, l + t)
                    dock = RectPx(min(l, r), usable.top, r, usable.bottom)
                    work = usable.copy(left = r)
                }
                DockEdge.Right -> {
                    val r = usable.right - pad
                    val l = max(usable.left, r - t)
                    dock = RectPx(l, usable.top, max(l, r), usable.bottom)
                    work = usable.copy(right = l)
                }
            }
        }
        val cellPx = cell * scale
        val cols = max(1, floor(work.width / cellPx).toInt())
        val rows = max(1, floor(work.height / cellPx).toInt())
        return DesktopLayout(sc, scale, wDp, hDp, icon, cell, cols, rows, work, dock, dockThickness)
    }
}
