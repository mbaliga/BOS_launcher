package com.bos.sphere.feature.grid

/**
 * CFG-13: Ground Control's cross-media-bar by keys. Left/Right change category, Up/Down move between settings,
 * Enter edits, Esc backs out. Touch, pointer, keyboard and controller (D-pad, A, B) all map to [GKey], so the
 * model is the same everywhere and in both orientations.
 */
enum class GKey { Left, Right, Up, Down, Enter, Escape }

/** What a row does when it is edited. */
enum class EditKind {
    /** Enter flips it. */ Flip,
    /** Enter runs it or opens its page. */ Activate,
    /** Enter starts adjusting with Left/Right/Up/Down; Enter or Esc ends. */ Adjust,
}

data class GState(
    val category: Int = 0,
    /** The row the keys are on; -1 while the cursor is on the category bar. */
    val item: Int = -1,
    val editing: Boolean = false,
    /** A row's page is open; Esc closes it first. */
    val paneOpen: Boolean = false,
    val searching: Boolean = false,
)

sealed interface GEffect {
    data object None : GEffect
    data object Close : GEffect
    data object CategoryChanged : GEffect
    data object Rejected : GEffect
    data class Flip(val item: Int) : GEffect
    data class Activate(val item: Int) : GEffect
    /** Step a row's value: [delta] is -1 or +1. */
    data class Adjust(val item: Int, val delta: Int) : GEffect
    data object ClosePane : GEffect
    data object CloseSearch : GEffect
}

data class GResult(val state: GState, val effect: GEffect = GEffect.None)

object GroundControlNav {
    /**
     * [categoryCount] categories; [itemCount] rows in the current category (or in the search results);
     * [kindOf] says how row `i` is edited.
     */
    fun reduce(s: GState, key: GKey, categoryCount: Int, itemCount: Int, kindOf: (Int) -> EditKind): GResult {
        if (s.paneOpen) return if (key == GKey.Escape) GResult(s.copy(paneOpen = false), GEffect.ClosePane) else GResult(s)
        if (s.editing) {
            return when (key) {
                GKey.Enter, GKey.Escape -> GResult(s.copy(editing = false))
                GKey.Left, GKey.Down -> GResult(s, GEffect.Adjust(s.item, -1))
                GKey.Right, GKey.Up -> GResult(s, GEffect.Adjust(s.item, +1))
            }
        }
        return when (key) {
            GKey.Right, GKey.Left -> {
                if (s.searching) return GResult(s)
                val to = (s.category + if (key == GKey.Right) 1 else -1)
                if (to !in 0 until categoryCount) GResult(s, GEffect.Rejected)
                else GResult(s.copy(category = to, item = if (s.item >= 0) 0 else -1), GEffect.CategoryChanged)
            }
            GKey.Down -> if (itemCount == 0) GResult(s) else GResult(s.copy(item = (s.item + 1).coerceAtMost(itemCount - 1)))
            GKey.Up -> GResult(s.copy(item = (s.item - 1).coerceAtLeast(-1)))
            GKey.Enter -> {
                if (s.item !in 0 until itemCount) GResult(s)
                else when (kindOf(s.item)) {
                    EditKind.Flip -> GResult(s, GEffect.Flip(s.item))
                    EditKind.Activate -> GResult(s, GEffect.Activate(s.item))
                    EditKind.Adjust -> GResult(s.copy(editing = true))
                }
            }
            GKey.Escape -> when {
                s.searching -> GResult(s.copy(searching = false, item = -1), GEffect.CloseSearch)
                else -> GResult(s, GEffect.Close)
            }
        }
    }

    fun kindOf(item: SettingItem): EditKind = when (item) {
        is SettingItem.Toggle -> EditKind.Flip
        is SettingItem.Slider, is SettingItem.Stepper -> EditKind.Adjust
        is SettingItem.Choice<*> -> if (item.preview != null) EditKind.Activate else EditKind.Adjust
        is SettingItem.Action, is SettingItem.Pane -> EditKind.Activate
    }

    /** Steps a row's value by [delta] (-1 or +1) as the keys do: sliders by their step, steppers by one, choices through their options. True when something changed. */
    fun adjust(item: SettingItem, delta: Int): Boolean = when (item) {
        is SettingItem.Slider -> {
            val step = if (item.step > 0f) item.step else SettingsMath.autoStep(item.range)
            // Wide fraction ranges take ten steps to cross; keys move in steps of 5% so they are usable.
            val stride = if ((item.range.endInclusive - item.range.start) / step > 40f) step * 5f else step
            val next = SettingsMath.snap(item.amount + delta * stride, item.range, step)
            if (next != item.amount) { item.onChange(next); true } else false
        }
        is SettingItem.Stepper -> {
            val next = (item.count + delta).coerceIn(item.range.first, item.range.last)
            if (next != item.count) { item.onChange(next); true } else false
        }
        is SettingItem.Choice<*> -> {
            @Suppress("UNCHECKED_CAST") val c = item as SettingItem.Choice<Any?>
            if (c.options.size < 2) false else {
                val at = c.options.indexOf(c.selected)
                c.onSelect(c.options[SettingsMath.stepIndex(c.options.size, at, delta)])
                true
            }
        }
        is SettingItem.Toggle -> { item.onChange(!item.checked); true }
        is SettingItem.Action, is SettingItem.Pane -> false
    }
}
