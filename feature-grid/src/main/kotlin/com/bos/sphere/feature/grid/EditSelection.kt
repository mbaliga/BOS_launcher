package com.bos.sphere.feature.grid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind

/** What is selected on the home page in edit mode: the item's id, its type and its bounds in the window (px). */
data class SelectedItem(val key: String, val kind: ItemKind, val bounds: Rect?)

/**
 * Wave 45 (editHandles), HOM-12: THE selection model of home edit mode, one for every item type (app, folder, shortcut, link,
 * tile, widget). A tap selects, a second tap or a tap on empty space deselects, leaving edit mode clears it.
 *
 * The hook the item action bar (Resize, Remove, Settings) builds on: read [selected] (id, type, bounds) to place the bar, and
 * call [remove] / [duplicate]; the host or the home sets [onRemove] / [onDuplicate]. [announcement] is the text of the live
 * region that tells TalkBack what an action did.
 */
class EditSelectionState {
    var selectedKey by mutableStateOf<String?>(null)
        private set
    var bounds by mutableStateOf<Rect?>(null)
        private set
    var announcement by mutableStateOf("")
        private set

    /** Set by the home / host: remove the item from home. */
    @Volatile var onRemove: (String) -> Unit = {}
    /** Set by the host when duplicating is possible (null: the action is not offered). */
    @Volatile var onDuplicate: ((String) -> Unit)? = null

    /** The cell pitch of the page being edited, in dp, reported by the page so that the keyboard hooks can convert provider limits. */
    @Volatile var pitchDpX: Float = 80f
    @Volatile var pitchDpY: Float = 80f

    /** True while no item action bar exists: the home then draws a minimal 48 dp Remove button under the selection. */
    var fallbackBar by mutableStateOf(true)

    private var announceCount = 0

    val selected: SelectedItem? get() = selectedKey?.let { SelectedItem(it, ItemKeys.kind(it), bounds) }

    fun isSelected(key: String): Boolean = selectedKey == key
    fun select(key: String) { if (selectedKey != key) { selectedKey = key; bounds = null } }
    /** A tap: selects, or deselects when [key] already is the selection. Returns the new selection. */
    fun toggle(key: String): String? { if (selectedKey == key) clear() else select(key); return selectedKey }
    fun clear() { selectedKey = null; bounds = null }
    fun report(key: String, rect: Rect) { if (key == selectedKey && bounds != rect) bounds = rect }

    fun remove() { selectedKey?.let { onRemove(it) } }
    fun duplicate() { selectedKey?.let { k -> onDuplicate?.invoke(k) } }

    /** Says [message] in the live region (a zero-width toggle makes a repeated message announce again). */
    fun announce(message: String) { announceCount++; announcement = if (announceCount % 2 == 0) message + "​" else message }
}
