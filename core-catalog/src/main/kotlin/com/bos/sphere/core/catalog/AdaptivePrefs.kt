package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.LauncherPrefs
import kotlin.math.min

/**
 * HOM-11: the live link between window size, the user's density setting and the grids the home
 * pages use. With [LauncherPrefs.adaptiveGrid] off the stored portrait and landscape grids apply
 * unchanged; with it on both are derived from the current window (app area inside the uniform
 * frame, see HomeFrame) and [LauncherPrefs.gridDensity].
 */
object AdaptivePrefs {
    private const val FRAME_FRACTION = 0.148f

    /** The app area (dp) for a window: inset by the frame band on every side. */
    fun areaDp(widthDp: Float, heightDp: Float): Pair<Float, Float> {
        val band = min(widthDp, heightDp) * FRAME_FRACTION
        return (widthDp - 2 * band).coerceAtLeast(0f) to (heightDp - 2 * band).coerceAtLeast(0f)
    }

    /** The grid for the current window shape, with the other orientation computed from the swapped window. */
    fun grids(widthDp: Float, heightDp: Float, density: Float): Pair<GridSpec, GridSpec> {
        val short = min(widthDp, heightDp)
        val long = maxOf(widthDp, heightDp)
        val (pw, ph) = areaDp(short, long)
        val (lw, lh) = areaDp(long, short)
        return AdaptiveGrid.dims(pw, ph, density) to AdaptiveGrid.dims(lw, lh, density)
    }

    /** [p] with its portrait and landscape grids replaced by the derived ones when adaptive mode is on. */
    fun effective(p: LauncherPrefs, widthDp: Int, heightDp: Int): LauncherPrefs {
        if (!p.adaptiveGrid || widthDp <= 0 || heightDp <= 0) return p
        val (portrait, landscape) = grids(widthDp.toFloat(), heightDp.toFloat(), p.gridDensity)
        return if (portrait == p.portraitGrid && landscape == p.landscapeGrid) p else p.copy(portraitGrid = portrait, landscapeGrid = landscape)
    }
}
