package com.bos.sphere.core.input

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------- puck by keyboard and wheel (INP-18)

enum class PuckMode { AZ, Category, Recent;
    fun next(backwards: Boolean = false) = entries[Math.floorMod(ordinal + if (backwards) -1 else 1, entries.size)]
}

/** [labels] are the groups around the wheel in order; [focused] the one at the middle; [item] the app within it. */
data class PuckState(val mode: PuckMode, val labels: List<String>, val focused: Int, val item: Int = 0, val itemCount: Int = 0)

sealed interface PuckEffect {
    data class FocusGroup(val index: Int) : PuckEffect
    data class SetMode(val mode: PuckMode) : PuckEffect
    data class SelectItem(val index: Int) : PuckEffect
    data object Launch : PuckEffect
    /** The Radar button in the middle of the hub. */
    data object Search : PuckEffect
    /** The nth ring action (0-based). */
    data class Action(val index: Int) : PuckEffect
    data object Close : PuckEffect
}

/** A key as the puck sees it. [ch] is the typed letter or digit, if any. */
data class PuckKey(val keyCode: Int, val shift: Boolean = false, val ch: Char? = null)

/**
 * The puck's keyboard contract. Left/Right (and the scroll wheel) rotate, a typed letter jumps to the
 * group starting with it, Tab cycles A-Z, Category and Recent, Up/Down walk the apps of the focused
 * group, Enter launches, Esc closes.
 */
object PuckKeys {
    fun onKey(s: PuckState, k: PuckKey): PuckEffect? = when (k.keyCode) {
        Keys.DPAD_LEFT -> step(s, -1)
        Keys.DPAD_RIGHT -> step(s, +1)
        Keys.DPAD_UP -> if (s.item > 0) PuckEffect.SelectItem(s.item - 1) else null
        Keys.DPAD_DOWN -> if (s.item < s.itemCount - 1) PuckEffect.SelectItem(s.item + 1) else null
        Keys.TAB -> PuckEffect.SetMode(s.mode.next(k.shift))
        Keys.ENTER, Keys.DPAD_CENTER, Keys.NUMPAD_ENTER -> if (s.itemCount > 0) PuckEffect.Launch else null
        Keys.ESCAPE, Keys.BACK -> PuckEffect.Close
        Keys.SLASH -> PuckEffect.Search
        else -> when (val c = k.ch ?: Keys.letterOf(k.keyCode)) {
            null -> null
            in '1'..'9' -> PuckEffect.Action(c - '1')
            else -> if (c.isLetter()) jump(s, c) else null
        }
    }

    /** Keys the open puck owns, whether or not they change anything, so they never leak to the home underneath. */
    fun owns(keyCode: Int, ch: Char?): Boolean = keyCode in setOf(Keys.DPAD_LEFT, Keys.DPAD_RIGHT, Keys.DPAD_UP, Keys.DPAD_DOWN, Keys.TAB,
        Keys.ENTER, Keys.DPAD_CENTER, Keys.NUMPAD_ENTER, Keys.ESCAPE, Keys.BACK, Keys.SLASH) || Keys.isLetter(keyCode) || (ch != null && (ch.isLetterOrDigit()))

    private fun step(s: PuckState, d: Int): PuckEffect? {
        val i = (s.focused + d).coerceIn(0, max(0, s.labels.lastIndex))
        return if (s.labels.isEmpty() || i == s.focused) null else PuckEffect.FocusGroup(i)
    }

    /** The group a typed letter lands on: the first label starting with it, else the next one after it alphabetically. */
    fun jump(s: PuckState, ch: Char): PuckEffect? {
        val c = ch.uppercaseChar()
        val exact = s.labels.indexOfFirst { it.firstOrNull()?.uppercaseChar() == c }
        val i = if (exact >= 0) exact else if (s.mode == PuckMode.AZ) s.labels.indexOfFirst { (it.firstOrNull()?.uppercaseChar() ?: ' ') > c } else -1
        return if (i < 0 || i == s.focused) null else PuckEffect.FocusGroup(i)
    }

    /**
     * Scroll-wheel notches become rotation steps. [carry] is the leftover between events so a
     * trackpad's fine deltas still add up to whole steps; returns steps (sign = direction) and the new carry.
     */
    fun wheelSteps(delta: Float, carry: Float, perStep: Float = 1f): Pair<Int, Float> {
        val total = carry + delta
        val steps = (total / perStep).toInt()
        return steps to (total - steps * perStep)
    }
}

// ---------------------------------------------------------------- puck sizing (INP-19)

/** One ring button position relative to the wheel centre, in dp. */
data class PuckSlot(val radiusDp: Float, val angleDeg: Float)

data class PuckLayoutResult(
    val hubRadiusDp: Float,
    val searchSizeDp: Float,
    val actionSizeDp: Float,
    val slots: List<PuckSlot>,
    /** How many ring actions fit without overlap or clipping at this window size; the settings count limit follows it. */
    val capacity: Int,
    val chipRadiusDp: Float,
)

/**
 * Hub and ring spacing derived from the window (INP-19). Everything is in dp. The ring buttons sit on
 * rings inside the hub, in the visible quadrant; sizes shrink with the hub so nothing overlaps the Radar
 * button in the centre, each other or the group labels at [PuckLayoutResult.chipRadiusDp].
 */
object PuckLayout {
    const val GAP = 3f
    const val MIN_SEARCH = 36f
    const val MIN_ACTION = 30f
    const val MAX_ACTION_COUNT = 9
    /** Labels sit on an arc this far inside the hub's edge; buttons stay clear of it. */
    const val LABEL_BAND = 14f

    fun hubRadius(minSideDp: Float) = min(minSideDp * 0.34f, 190f)

    fun compute(minSideDp: Float, actionCount: Int): PuckLayoutResult {
        val hub = hubRadius(minSideDp)
        val search = (hub * 0.42f).coerceIn(MIN_SEARCH, 56f)
        val action = (hub * 0.30f).coerceIn(MIN_ACTION, 42f)
        val chip = hub - LABEL_BAND
        // Ring 1 clears the Radar button; ring 2 stops short of the group labels.
        val r1 = search / 2f + GAP + action / 2f
        val r2 = r1 + action + GAP
        val outerLimit = chip - 2f - action / 2f
        // Buttons keep a little way in from the two screen-edge radii of the quadrant.
        val lo = 8f; val hi = 82f
        fun fit(r: Float): Int {
            if (r > outerLimit) return 0
            val pitch = Math.toDegrees(((action + GAP).toDouble() / r)).toFloat()
            return ((hi - lo) / pitch).toInt() + 1
        }
        val n1 = fit(r1); val n2 = fit(r2)
        val cap = min(n1 + n2, MAX_ACTION_COUNT)
        val count = min(actionCount, cap)
        val on1 = min(count, n1); val on2 = count - on1
        fun spread(n: Int, r: Float) = List(n) { i ->
            val a = if (n == 1) 45f else lo + (hi - lo) * i / (n - 1)
            PuckSlot(r, a)
        }
        val slots = spread(on1, r1) + spread(on2, r2)
        return PuckLayoutResult(hub, search, action, slots, cap, chip)
    }

    /** Smallest centre-to-centre distance between any two buttons (and the Radar button), minus their radii. Positive means no overlap. */
    fun clearance(l: PuckLayoutResult): Float {
        data class P(val x: Float, val y: Float, val r: Float)
        val pts = listOf(P(0f, 0f, l.searchSizeDp / 2f)) + l.slots.map {
            val rad = Math.toRadians(it.angleDeg.toDouble())
            P((it.radiusDp * cos(rad)).toFloat(), (it.radiusDp * sin(rad)).toFloat(), l.actionSizeDp / 2f)
        }
        var best = Float.MAX_VALUE
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            val d = kotlin.math.hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y) - pts[i].r - pts[j].r
            best = min(best, d)
        }
        return best
    }

    /** True when every slot's button lies fully inside the window quadrant, given the wheel centre sits [edgeInsetDp] in from both edges. */
    fun insideWindow(l: PuckLayoutResult, minSideDp: Float, edgeInsetDp: Float = 63f): Boolean = l.slots.all {
        val rad = Math.toRadians(it.angleDeg.toDouble())
        val x = it.radiusDp * cos(rad) + edgeInsetDp; val y = it.radiusDp * sin(rad) + edgeInsetDp
        val h = l.actionSizeDp / 2f
        x - h >= 0 && y - h >= 0 && x + h <= minSideDp && y + h <= minSideDp
    }
}
