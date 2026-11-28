package com.bos.sphere.feature.grid

import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.bos.sphere.core.catalog.CellEdit
import com.bos.sphere.core.catalog.CellLimits
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.ProviderLimitsMath
import com.bos.sphere.core.design.L10n

/**
 * Wave 45 (editHandles), HOM-07 / HOM-12 / NFR-13: the non-drag way to edit one item of a grid page: move by one cell, make wider /
 * narrower / taller / shorter within the provider's limits, remove. The accessibility actions, the keyboard (arrows, Shift + arrows,
 * Delete) and the tests all go through here; every result is announced ("Moved to row 2, column 3", "Resized to 3 by 2",
 * "Can't resize: at the maximum") and applied through the host's one edit path ([HomeExtras.onAction] / [HomeExtras.onResizeTo]).
 */
class ItemEditor(
    private val key: String,
    private val extras: HomeExtras,
    private val keys: List<String>,
    private val pageIndex: Int,
    private val cols: Int,
    private val rows: Int,
    /** Right-to-left home order: logical column 0 is the right-most one, so "left" is one column further. */
    private val mirror: Boolean = false,
    private val originMoves: Boolean = true,
    private val onRemove: () -> Unit = {},
) {
    private val isWidget get() = ItemKeys.kind(key) == ItemKind.Widget

    fun limits(): CellLimits {
        val span = extras.spanOf(key)
        return ProviderLimitsMath.toCells(extras.limitsOf(key), extras.selection.pitchDpX, extras.selection.pitchDpY, cols, rows, span)
    }

    /** Moves one cell by ([dRight], [dDown]) in screen directions. True when it moved. */
    fun move(dRight: Int, dDown: Int): Boolean {
        val r = CellEdit.move(keys, extras::spanOf, cols, rows, key, if (mirror) -dRight else dRight, dDown)
        if (!r.ok) {
            extras.selection.announce(when (r.reason) {
                CellEdit.Reason.Blocked -> L10n.tr("Can't move: another item is in the way")
                else -> L10n.tr("Can't move: at the edge of the page")
            })
            return false
        }
        extras.onAction(ItemAction.Move(key, pageIndex, r.index))
        val at = r.rect!!
        extras.selection.announce(L10n.tr("Moved to row %d, column %d", at.row + 1, at.col + 1))
        return true
    }

    /** Resizes by whole cells (right and down grow). Widgets only; true when it resized. */
    fun resize(dCols: Int, dRows: Int): Boolean {
        if (!isWidget) return false
        val r = CellEdit.resizeStep(keys, extras::spanOf, cols, rows, key, limits(), dCols, dRows, originMoves)
        if (!r.ok) {
            extras.selection.announce(when (r.reason) {
                CellEdit.Reason.AtMinimum -> L10n.tr("Can't resize: at the minimum")
                CellEdit.Reason.Blocked -> L10n.tr("Can't resize: another item is in the way")
                CellEdit.Reason.NotAllowed -> L10n.tr("Can't resize: this widget cannot change that way")
                else -> L10n.tr("Can't resize: at the maximum")
            })
            return false
        }
        val at = r.rect!!
        extras.onResizeTo(key, at, r.moveOrigin)
        extras.selection.announce(L10n.tr("Resized to %d by %d", at.cols, at.rows))
        return true
    }

    fun remove() {
        val label = extraLabelOrKey()
        onRemove()
        extras.selection.announce(L10n.tr("Removed %s", label))
    }

    private fun extraLabelOrKey(): String = extras.state.items[key]?.label ?: extras.state.folders[ItemKeys.folderId(key) ?: ""]?.name ?: key

    /** The custom accessibility actions of the item node while editing. */
    fun actions(): List<CustomAccessibilityAction> = buildList {
        add(CustomAccessibilityAction(L10n.tr("Move left")) { move(-1, 0); true })
        add(CustomAccessibilityAction(L10n.tr("Move right")) { move(1, 0); true })
        add(CustomAccessibilityAction(L10n.tr("Move up")) { move(0, -1); true })
        add(CustomAccessibilityAction(L10n.tr("Move down")) { move(0, 1); true })
        if (isWidget) {
            add(CustomAccessibilityAction(L10n.tr("Make wider")) { resize(1, 0); true })
            add(CustomAccessibilityAction(L10n.tr("Make narrower")) { resize(-1, 0); true })
            add(CustomAccessibilityAction(L10n.tr("Make taller")) { resize(0, 1); true })
            add(CustomAccessibilityAction(L10n.tr("Make shorter")) { resize(0, -1); true })
        }
        add(CustomAccessibilityAction(L10n.tr("Remove")) { remove(); true })
    }

}
