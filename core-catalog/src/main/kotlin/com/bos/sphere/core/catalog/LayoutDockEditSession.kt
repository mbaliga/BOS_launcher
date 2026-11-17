package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout

/** The dock list an edit saw: the workspace it belongs to and its pins (encoded refs). */
data class DockPinsView(val workspaceId: String, val pins: List<String>)

/**
 * HOM-12: how an undo applies a pin change to the rail as it is now. [revert] takes the rail from
 * [after] back to [before] for the edit that made that change. When the rail is still exactly [after]
 * the answer is [before]. When the rail was edited since, only the edit's own part is undone: refs the
 * edit added are taken out, refs it removed are put back (at their old place, or the end), and every
 * other pin or order the rail has now is left alone, so an unrelated undo never reverts a rail edit.
 */
object DockPinMerge {
    fun revert(current: List<String>, before: List<String>, after: List<String>): List<String> {
        if (current == after) return before
        val added = after.toSet() - before.toSet()
        val result = current.filter { it !in added }.toMutableList()
        for (r in before) {
            if (r in after.toSet() || r in result) continue
            result.add(before.indexOf(r).coerceAtMost(result.size), r)
        }
        return result
    }
}

/** What an undo or redo asks the screen to write back. [pins] is null when the rail stays as it is. */
data class EditRestore(val layout: HomeLayout, val workspaceId: String?, val pins: List<String>?)

/**
 * HOM-12: edit mode over the page arrangement and the dock pins together. One undo stack (20 changes)
 * for both. An entry carries a pin change only when that edit changed the pins; a layout-only edit
 * never touches the rail on undo, and a pin change made outside any entry (the rail edited, a workspace
 * switched) is merged by [DockPinMerge] instead of overwritten.
 *
 * The layout and the pins arrive as separate observations; two observations of different kinds within
 * [coalesceMs] belong to one user edit (moving an item to the dock removes it from the page and pins it)
 * and share one entry. Pin edits made while the edit mode is on are edits of this session, including a
 * rail edit between two home edits, which is its own entry undone in order.
 *
 * Styles that scroll (list, XMB, DSi, continuous) have no page arrangement: their layout never changes,
 * so their entries are pin-only and undo restores just the rail.
 */
class LayoutDockEditSession(
    initial: HomeLayout,
    initialPins: DockPinsView,
    private val limit: Int = UndoStack.DEFAULT_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
    private val coalesceMs: Long = COALESCE_MS,
) {
    private class Entry(
        val layoutBefore: HomeLayout, var layoutAfter: HomeLayout,
        var pinsBefore: List<String>?, var pinsAfter: List<String>?, val workspaceId: String,
        var at: Long,
    ) {
        val layoutChanged get() = layoutBefore != layoutAfter
        val pinsChanged get() = pinsBefore != null
    }

    private val past = ArrayDeque<Entry>()
    private val future = ArrayDeque<Entry>()
    private var layout = initial
    private var pins = initialPins
    var active: Boolean = true
        private set

    val current: HomeLayout get() = layout
    val currentPins: DockPinsView get() = pins
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoDepth: Int get() = past.size

    fun exit() { active = false; past.clear(); future.clear() }

    /**
     * HOM-12: the arrangement was replaced by something that is not an edit (a window size class change on rotation or folding, which
     * swaps in that class's own arrangement). The history of the old one cannot be undone into the new one, so it ends here and
     * [layout] / [pins] become the baseline; the store's echo of the same layout is then ignored by [observe].
     */
    fun rebase(layout: HomeLayout, pins: DockPinsView) {
        if (!active) return
        past.clear(); future.clear()
        this.layout = layout
        this.pins = pins
    }

    private fun push(e: Entry) { past.addLast(e); while (past.size > limit) past.removeFirst(); future.clear() }

    private fun joinable(): Entry? = past.lastOrNull()?.takeIf { now() - it.at <= coalesceMs }

    /** A layout the store published. False for an echo of our own write. */
    fun observe(new: HomeLayout): Boolean {
        if (!active || new == layout) return false
        val open = joinable()
        if (open != null && open.pinsChanged && !open.layoutChanged) {
            open.layoutAfter = new
            // layoutBefore is the state before the pin-only entry; keep it so one undo returns both.
        } else push(Entry(layout, new, null, null, pins.workspaceId, now()))
        layout = new
        return true
    }

    /** The dock list the rail now has. False when nothing changed or the active workspace changed (rebased, not an edit). */
    fun observePins(new: DockPinsView): Boolean {
        if (!active) return false
        if (new.workspaceId != pins.workspaceId) { pins = new; return false }
        if (new.pins == pins.pins) return false
        val open = joinable()
        if (open != null && open.layoutChanged && !open.pinsChanged && open.workspaceId == new.workspaceId) {
            open.pinsBefore = pins.pins; open.pinsAfter = new.pins
        } else if (open != null && open.pinsChanged && open.workspaceId == new.workspaceId && open.layoutChanged) {
            open.pinsAfter = new.pins // a second pin step of the same gesture
        } else push(Entry(layout, layout, pins.pins, new.pins, new.workspaceId, now()))
        pins = new
        return true
    }

    fun undo(): EditRestore? {
        val e = past.removeLastOrNull() ?: return null
        future.addLast(e)
        layout = e.layoutBefore
        return restore(e, e.layoutBefore) { cur -> DockPinMerge.revert(cur, e.pinsBefore!!, e.pinsAfter!!) }
    }

    fun redo(): EditRestore? {
        val e = future.removeLastOrNull() ?: return null
        past.addLast(e)
        layout = e.layoutAfter
        return restore(e, e.layoutAfter) { cur -> DockPinMerge.revert(cur, e.pinsAfter!!, e.pinsBefore!!) }
    }

    private fun restore(e: Entry, to: HomeLayout, pinsFor: (List<String>) -> List<String>): EditRestore {
        e.at = Long.MIN_VALUE / 2 // a later observation never joins an entry that was undone or redone
        if (!e.pinsChanged || pins.workspaceId != e.workspaceId) return EditRestore(to, null, null)
        val next = pinsFor(pins.pins)
        pins = pins.copy(pins = next)
        return EditRestore(to, e.workspaceId, next)
    }

    companion object { const val COALESCE_MS = 400L }
}
