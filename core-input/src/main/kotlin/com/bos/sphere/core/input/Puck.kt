package com.bos.sphere.core.input

import kotlin.math.min

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
        Keys.MOVE_HOME -> if (s.labels.isEmpty() || s.focused == 0) null else PuckEffect.FocusGroup(0)
        Keys.MOVE_END -> if (s.labels.isEmpty() || s.focused == s.labels.lastIndex) null else PuckEffect.FocusGroup(s.labels.lastIndex)
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
    fun owns(keyCode: Int, ch: Char?): Boolean = keyCode in setOf(Keys.DPAD_LEFT, Keys.DPAD_RIGHT, Keys.DPAD_UP, Keys.DPAD_DOWN, Keys.TAB, Keys.MOVE_HOME, Keys.MOVE_END,
        Keys.ENTER, Keys.DPAD_CENTER, Keys.NUMPAD_ENTER, Keys.ESCAPE, Keys.BACK, Keys.SLASH) || Keys.isLetter(keyCode) || (ch != null && (ch.isLetterOrDigit()))

    private fun step(s: PuckState, d: Int): PuckEffect? {
        // Wave 30 (drawerLoop): the groups are a ring, so right of the last is the first and left of the first is the last.
        if (s.labels.isEmpty()) return null
        val i = Math.floorMod(s.focused + d, s.labels.size)
        return if (i == s.focused) null else PuckEffect.FocusGroup(i)
    }

    /** The group a typed letter lands on: the first label starting with it, else the next one after it alphabetically. */
    fun jump(s: PuckState, ch: Char): PuckEffect? {
        val c = ch.uppercaseChar()
        val exact = s.labels.indexOfFirst { it.firstOrNull()?.uppercaseChar() == c }
        // Past the last letter the jump continues round the ring: to '#' (between Z and A), then to the first group. The rotation takes the short way round.
        // Wave 33: the ring runs A..Z, '#', A, so a letter past the last letter label lands on '#' when there is one (else the first group).
        val i = if (exact >= 0) exact else if (s.mode == PuckMode.AZ) s.labels.indexOfFirst { (it.firstOrNull()?.uppercaseChar() ?: ' ') > c }.let {
            if (it >= 0) it else if (s.labels.isEmpty()) -1 else s.labels.indexOf("#").takeIf { h -> h >= 0 } ?: 0
        } else -1
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

/**
 * Hub and label radii derived from the window (INP-19). Everything is in dp. The shortcut sections (feature-grid `PuckSectionsGeometry`) and the
 * group labels are placed from these two radii; the old ring slots and capacity went with the round buttons (wave 30, removed wave 33).
 */
data class PuckLayoutResult(val hubRadiusDp: Float, val chipRadiusDp: Float)

object PuckLayout {
    /** Labels sit on an arc this far inside the hub's edge. */
    const val LABEL_BAND = 14f

    fun hubRadius(minSideDp: Float) = min(minSideDp * 0.34f, 190f)

    fun compute(minSideDp: Float): PuckLayoutResult {
        val hub = hubRadius(minSideDp)
        return PuckLayoutResult(hub, hub - LABEL_BAND)
    }
}
