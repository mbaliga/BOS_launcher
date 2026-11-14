package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SearchLayout
import com.bos.sphere.core.input.InputMode
import com.bos.sphere.core.input.MenuAction

/** PAL-07: keyboard operation of the palette, as a pure state machine so it is the same in all three layouts. */
enum class RKey { Up, Down, Left, Right, Enter, Tab, ShiftTab, Escape, Menu }

/**
 * [count] results can be walked; [selected] is -1 until the user moves into the list, so Enter still runs the
 * typed query's top hit exactly as before. While [menuOpen], the keys drive the action list instead.
 */
data class RadarNav(
    val count: Int,
    val selected: Int = -1,
    val menuOpen: Boolean = false,
    val menuIndex: Int = 0,
) {
    val active get() = selected in 0 until count
    fun withCount(n: Int): RadarNav {
        if (n == count) return this
        val sel = if (selected >= n) n - 1 else selected
        return copy(count = n, selected = sel, menuOpen = menuOpen && sel >= 0)
    }
}

sealed interface RadarEffect {
    data object None : RadarEffect
    data class Run(val index: Int) : RadarEffect
    data class OpenMenu(val index: Int) : RadarEffect
    data class RunMenu(val index: Int, val menuIndex: Int) : RadarEffect
    data object CloseMenu : RadarEffect
    data object Dismiss : RadarEffect
    /** Move the category wheel by [delta] entries. */
    data class WheelMove(val delta: Int) : RadarEffect
}

/** [consumed] false hands the key back to the text field (caret movement, Enter = Go). */
data class RadarKeyResult(val nav: RadarNav, val effect: RadarEffect = RadarEffect.None, val consumed: Boolean = true)

/** What Left/Right may do while the caret is in the query: the wheel only gets them when the caret has nowhere to go. */
data class CaretContext(val queryBlank: Boolean = true, val atStart: Boolean = true, val atEnd: Boolean = true) {
    fun wheelMayTake(right: Boolean) = queryBlank || (if (right) atEnd else atStart)
}

object RadarKeyboard {
    /** [menuSize] is the number of entries the action list of result `i` has. */
    fun reduce(nav: RadarNav, key: RKey, caret: CaretContext = CaretContext(), menuSize: (Int) -> Int = { 1 }): RadarKeyResult {
        if (nav.menuOpen) return reduceMenu(nav, key, menuSize(nav.selected))
        return when (key) {
            RKey.Down -> if (nav.count == 0) RadarKeyResult(nav) else RadarKeyResult(nav.copy(selected = (nav.selected + 1).coerceAtMost(nav.count - 1)))
            RKey.Up -> if (!nav.active) RadarKeyResult(nav) else RadarKeyResult(nav.copy(selected = (nav.selected - 1).coerceAtLeast(0)))
            RKey.Enter -> if (nav.active) RadarKeyResult(nav, RadarEffect.Run(nav.selected)) else RadarKeyResult(nav, consumed = false)
            RKey.Tab, RKey.Menu -> when {
                nav.active -> RadarKeyResult(nav.copy(menuOpen = true, menuIndex = 0), RadarEffect.OpenMenu(nav.selected))
                nav.count > 0 -> RadarKeyResult(nav.copy(selected = 0, menuOpen = true, menuIndex = 0), RadarEffect.OpenMenu(0))
                else -> RadarKeyResult(nav) // focus stays in the palette
            }
            RKey.ShiftTab -> RadarKeyResult(nav)
            RKey.Right -> when {
                nav.active -> RadarKeyResult(nav.copy(menuOpen = true, menuIndex = 0), RadarEffect.OpenMenu(nav.selected))
                caret.wheelMayTake(true) -> RadarKeyResult(nav, RadarEffect.WheelMove(+1))
                else -> RadarKeyResult(nav, consumed = false)
            }
            RKey.Left -> when {
                nav.active -> RadarKeyResult(nav, RadarEffect.WheelMove(-1))
                caret.wheelMayTake(false) -> RadarKeyResult(nav, RadarEffect.WheelMove(-1))
                else -> RadarKeyResult(nav, consumed = false)
            }
            RKey.Escape -> RadarKeyResult(nav, RadarEffect.Dismiss)
        }
    }

    private fun reduceMenu(nav: RadarNav, key: RKey, size: Int): RadarKeyResult = when (key) {
        RKey.Down -> RadarKeyResult(nav.copy(menuIndex = (nav.menuIndex + 1).coerceAtMost((size - 1).coerceAtLeast(0))))
        RKey.Up -> RadarKeyResult(nav.copy(menuIndex = (nav.menuIndex - 1).coerceAtLeast(0)))
        RKey.Enter -> RadarKeyResult(nav.copy(menuOpen = false), RadarEffect.RunMenu(nav.selected, nav.menuIndex.coerceIn(0, (size - 1).coerceAtLeast(0))))
        RKey.Escape, RKey.Left, RKey.ShiftTab, RKey.Menu -> RadarKeyResult(nav.copy(menuOpen = false, menuIndex = 0), RadarEffect.CloseMenu)
        RKey.Tab, RKey.Right -> RadarKeyResult(nav) // swallowed so focus cannot wander
    }
}

// ---------------------------------------------------------------- the action list of a result (PAL-07, PAL-08)

enum class ResultKind { App, Person, File, Action, Web, Setting, Other }

/** One entry of a result's action list: a core-input [MenuAction], or Radar's own preview. */
data class ResultMenuEntry(val label: String, val action: MenuAction?, val preview: Boolean = false)

object ResultMenus {
    /** Built on the shared context-menu model for apps; everything else offers Open and Preview. */
    fun forKind(kind: ResultKind, canUninstall: Boolean = true, otherDisplays: Int = 0, adjacentSupported: Boolean = false): List<ResultMenuEntry> {
        val base = if (kind == ResultKind.App) {
            com.bos.sphere.core.input.ContextMenus.onMenuKey(
                com.bos.sphere.core.input.MenuContext(isApp = true, canUninstall = canUninstall, otherDisplays = otherDisplays, adjacentSupported = adjacentSupported),
            ).filter { it.enabled }.map { ResultMenuEntry(it.action.label, it.action) }
        } else listOf(ResultMenuEntry(MenuAction.Open.label, MenuAction.Open))
        return base + ResultMenuEntry("Preview", null, preview = true)
    }
}

// ---------------------------------------------------------------- layout policy (PAL-18)

object RadarLayoutPolicy {
    /** With a keyboard or pointer driving, results default to Flat unless the user pinned a layout. */
    fun effective(preferred: SearchLayout, pinned: Boolean, mode: InputMode): SearchLayout =
        if (!pinned && (mode == InputMode.Keyboard || mode == InputMode.Pointer)) SearchLayout.Flat else preferred
}
