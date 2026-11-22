package com.bos.sphere.core.data

import kotlin.math.abs

/**
 * Wave 50 (glanceClockEdit): where the big Glance clock sits and the pure maths of moving it. A place is two fractions of the USABLE area (the window inside the safe
 * insets): the clock's CENTRE x and its TOP y, so it survives rotation and other window sizes. The defaults ([DEFAULT_X], [DEFAULT_Y]) are the factory place
 * (centred, top at 14 percent), so nothing changes until the user moves the clock; a broken stored value falls back to them. Everything is clamped so the whole clock stays inside the area.
 */
object ClockPlacement {
    const val DEFAULT_X = 0.5f
    const val DEFAULT_Y = 0.14f
    /** A nudge by a TalkBack, keyboard or controller step, as a fraction of the area. */
    const val STEP = 0.05f
    /** How near (dp) a dragged clock must be to a centre line for it to snap there. */
    const val SNAP_DP = 10f

    data class Pos(val x: Float, val y: Float)

    val Default = Pos(DEFAULT_X, DEFAULT_Y)

    /** A stored float read back: out-of-range or broken values become [default]. */
    fun stored(v: Float, default: Float): Float = if (v.isNaN() || v.isInfinite() || v < 0f || v > 1f) default else v

    fun of(x: Float, y: Float): Pos = Pos(stored(x, DEFAULT_X), stored(y, DEFAULT_Y))

    fun isDefault(p: Pos) = abs(p.x - DEFAULT_X) < 1e-4f && abs(p.y - DEFAULT_Y) < 1e-4f

    /** The top-left of a [w] by [h] clock at [p] in an [areaW] by [areaH] area, kept inside it (a clock bigger than the area sits at 0). */
    fun topLeft(p: Pos, areaW: Float, areaH: Float, w: Float, h: Float): Pair<Float, Float> {
        val left = (p.x * areaW - w / 2f).coerceIn(0f, (areaW - w).coerceAtLeast(0f))
        val top = (p.y * areaH).coerceIn(0f, (areaH - h).coerceAtLeast(0f))
        return left to top
    }

    /** The fractions of a clock whose top-left is ([left], [top]); clamped inside the area. An unmeasured area gives the default. */
    fun fromTopLeft(left: Float, top: Float, areaW: Float, areaH: Float, w: Float, h: Float): Pos {
        if (areaW <= 0f || areaH <= 0f) return Default
        val l = left.coerceIn(0f, (areaW - w).coerceAtLeast(0f)); val t = top.coerceIn(0f, (areaH - h).coerceAtLeast(0f))
        return Pos(((l + w / 2f) / areaW).coerceIn(0f, 1f), (t / areaH).coerceIn(0f, 1f))
    }

    /** [p] kept inside the area for a clock of this size. */
    fun clamp(p: Pos, areaW: Float, areaH: Float, w: Float, h: Float): Pos { val (l, t) = topLeft(p, areaW, areaH, w, h); return fromTopLeft(l, t, areaW, areaH, w, h) }

    /** [pos] after snapping; [x] / [y] say which centre line caught the clock (the caller ticks the haptic once when one starts to). */
    data class Snap(val pos: Pos, val x: Boolean, val y: Boolean)

    /** Snaps the clock's centre to the vertical and horizontal centre lines of the area when within [thresholdPx]. */
    fun snap(p: Pos, areaW: Float, areaH: Float, w: Float, h: Float, thresholdPx: Float): Snap {
        if (areaW <= 0f || areaH <= 0f) return Snap(p, false, false)
        val (l, t) = topLeft(p, areaW, areaH, w, h)
        val cx = l + w / 2f; val cy = t + h / 2f
        val sx = abs(cx - areaW / 2f) <= thresholdPx
        val sy = abs(cy - areaH / 2f) <= thresholdPx
        val nl = if (sx) areaW / 2f - w / 2f else l
        val nt = if (sy) areaH / 2f - h / 2f else t
        return Snap(fromTopLeft(nl, nt, areaW, areaH, w, h), sx, sy)
    }

    /** One step in a direction: dx / dy are -1, 0 or 1. The result is clamped. */
    fun nudge(p: Pos, dx: Int, dy: Int, areaW: Float, areaH: Float, w: Float, h: Float): Pos =
        clamp(Pos(p.x + dx * STEP, p.y + dy * STEP), areaW, areaH, w, h)

    /** The stored fractions for the orientation. */
    fun LauncherPrefs.clockPos(landscape: Boolean): Pos = if (landscape) of(glanceClockXLand, glanceClockYLand) else of(glanceClockX, glanceClockY)

    /** [this] with the clock placed at [p] (or back at the factory place when [p] is null) for the orientation. */
    fun LauncherPrefs.withClockPos(landscape: Boolean, p: Pos?): LauncherPrefs {
        val x = p?.x ?: DEFAULT_X; val y = p?.y ?: DEFAULT_Y
        return if (landscape) copy(glanceClockXLand = x, glanceClockYLand = y) else copy(glanceClockX = x, glanceClockY = y)
    }

    /** True when the clock was moved in the orientation. */
    fun LauncherPrefs.clockMoved(landscape: Boolean): Boolean = !isDefault(clockPos(landscape))

    /** The clock settings the edit mode can change (the snapshot Cancel restores). */
    fun LauncherPrefs.restoreClockFrom(o: LauncherPrefs): LauncherPrefs = copy(
        clockStyle = o.clockStyle, clockFormat = o.clockFormat, glanceClockDate = o.glanceClockDate, glanceClockAccent = o.glanceClockAccent, glanceClockSize = o.glanceClockSize,
        glanceClockX = o.glanceClockX, glanceClockY = o.glanceClockY, glanceClockXLand = o.glanceClockXLand, glanceClockYLand = o.glanceClockYLand,
    )
}
