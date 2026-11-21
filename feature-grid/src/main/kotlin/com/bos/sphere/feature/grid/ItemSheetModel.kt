package com.bos.sphere.feature.grid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.data.LauncherPrefs

/**
 * Wave 45 (editSheet), HOM-07 / HOM-12: the single, small seam between "an item is selected in edit mode" and the item action bar plus
 * item sheet. [EditSelectionState] (the handles stream's selection, the one selection of home edit mode) implements it: a tap, a long press and the
 * keyboard all select through it, and the bar and the sheet only read it. Nothing here knows how selection is made.
 */
interface ItemSelectionSource {
    /** The selected item's key (an app key, `tile:clock`, a folder / widget / shortcut / link key), or null. */
    val selectedKey: String?
    /** The item's bounds in window pixels, or null while unknown. */
    val bounds: Rect?
    /** True while the item is being dragged or resized: the action bar steps out of the way (a ghost runs under it). */
    val dragging: Boolean get() = false
    fun clear()
}

/** What kind of item the sheet is for. */
enum class SheetKind { Clock, SettingsTile, App, Folder, Widget, Shortcut, Link }

/** One row of an item sheet; the order of a list is the order on screen. */
enum class SheetOption {
    ClockStyle, ClockFormat, ClockDate,
    OpenApp, AppInfo, ChangeIcon, HideApp, Uninstall,
    FolderOpen, FolderRename, FolderDissolve,
    WidgetReconfigure, WidgetSize,
    OpenItem,
}

/** What the host can do for the item, so the sheet never lists a row that would do nothing. */
data class SheetCaps(
    val canReconfigure: Boolean = false,
    val canUninstall: Boolean = true,
    val canChangeIcon: Boolean = true,
    val canResizeWidget: Boolean = true,
)

/** The clock tile's options: analog or digital face, 12 / 24 hour for the digital face, the day line. */
data class ClockTileOptions(val analog: Boolean = false, val format: ClockFormat = ClockFormat.System, val date: Boolean = true) {
    companion object {
        fun of(p: LauncherPrefs) = ClockTileOptions(p.clockTileAnalog, p.clockTileFormat, p.clockTileDate)
    }
}

val LocalClockTileOptions = androidx.compose.runtime.compositionLocalOf { ClockTileOptions() }

object ItemSheetModel {
    fun kindOf(key: String): SheetKind = when {
        key == HomeTiles.CLOCK -> SheetKind.Clock
        HomeTiles.isTile(key) -> SheetKind.SettingsTile
        else -> when (ItemKeys.kind(key)) {
            ItemKind.Folder -> SheetKind.Folder
            ItemKind.Widget -> SheetKind.Widget
            ItemKind.Shortcut -> SheetKind.Shortcut
            ItemKind.Link, ItemKind.Group -> SheetKind.Link
            else -> SheetKind.App
        }
    }

    /** The rows of the sheet for [kind]. The clock's 12 / 24 hour row only exists for the digital face. */
    fun options(kind: SheetKind, clock: ClockTileOptions = ClockTileOptions(), caps: SheetCaps = SheetCaps()): List<SheetOption> = when (kind) {
        SheetKind.Clock -> buildList { add(SheetOption.ClockStyle); if (!clock.analog) add(SheetOption.ClockFormat); add(SheetOption.ClockDate) }
        SheetKind.App -> buildList {
            add(SheetOption.OpenApp); add(SheetOption.AppInfo)
            if (caps.canChangeIcon) add(SheetOption.ChangeIcon)
            add(SheetOption.HideApp)
            if (caps.canUninstall) add(SheetOption.Uninstall)
        }
        SheetKind.Folder -> listOf(SheetOption.FolderOpen, SheetOption.FolderRename, SheetOption.FolderDissolve)
        SheetKind.Widget -> buildList { if (caps.canReconfigure) add(SheetOption.WidgetReconfigure); if (caps.canResizeWidget) add(SheetOption.WidgetSize) }
        SheetKind.Shortcut, SheetKind.Link -> listOf(SheetOption.OpenItem)
        SheetKind.SettingsTile -> listOf(SheetOption.OpenItem)
    }

    /** The bar's Resize button is only for items that can change size. */
    fun canResize(kind: SheetKind) = kind == SheetKind.Widget

    /** The English title of the sheet; callers pass it through L10n. */
    fun title(kind: SheetKind): String = when (kind) {
        SheetKind.Clock -> "Clock tile"; SheetKind.SettingsTile -> "Settings tile"; SheetKind.App -> "App"; SheetKind.Folder -> "Folder"
        SheetKind.Widget -> "Widget"; SheetKind.Shortcut -> "Shortcut"; SheetKind.Link -> "Link"
    }
}

/** Widget size presets in cells, clipped to the page grid; the current span is "selected" when it equals one of them. */
object WidgetSizePresets {
    private val ALL = listOf(2 to 1, 2 to 2, 4 to 2)
    fun forGrid(cols: Int, rows: Int): List<Pair<Int, Int>> = ALL.filter { (c, r) -> c <= cols && r <= rows }.ifEmpty { listOf(1 to 1) }
    fun label(p: Pair<Int, Int>) = "${p.first} × ${p.second}"
}

/**
 * The item action bar's placement: below the item, else above it, else clamped into [area]; never over the handles (the item's rect grown by
 * [handleReach]) nor over any rect of [avoid] (the dock, the page dots). The bar stays fully inside [area] horizontally.
 */
object ActionBarMath {
    data class Placement(val x: Float, val y: Float, val below: Boolean)

    fun place(item: Rect, barW: Float, barH: Float, area: Rect, avoid: List<Rect>, gap: Float, handleReach: Float): Placement {
        val maxX = (area.right - barW).coerceAtLeast(area.left)
        val x = (item.center.x - barW / 2f).coerceIn(area.left, maxX)
        val reach = Rect(item.left - handleReach, item.top - handleReach, item.right + handleReach, item.bottom + handleReach)
        fun rectAt(y: Float) = Rect(x, y, x + barW, y + barH)
        fun ok(y: Float): Boolean {
            if (y < area.top || y + barH > area.bottom) return false
            val r = rectAt(y)
            return avoid.none { it.overlaps(r) }
        }
        val below = reach.bottom + gap
        if (ok(below)) return Placement(x, below, true)
        val above = reach.top - gap - barH
        if (ok(above)) return Placement(x, above, false)
        // No room either side: sit inside the area just under the item's top, as clamped as the area allows.
        val y = (below).coerceIn(area.top, (area.bottom - barH).coerceAtLeast(area.top))
        return Placement(x, y, y >= item.center.y)
    }

    /**
     * W48 (editGaps): the placement on the scroll surface (Pages Continuous), where the cells touch each other. The bar never covers [item] nor the cell under it
     * ([under]: the item moved down by 1.3 heights, its label and the gap), so the icon the person may tap next stays reachable. It sits ABOVE the item first,
     * flips BELOW the cell under it, then to the top or bottom strip of [area]; the candidate with the least overlap of the kept-clear rects wins when none is free.
     * Stays inside [area] and clear of [avoid].
     */
    /** The cell under an icon is taken as this many icon heights tall (its label and padding included on top of [labelRoom]): the pitch of the scroll surface is about 1.95 icons. */
    const val UNDER_FACTOR = 1.3f

    fun placeOnScroll(item: Rect, barW: Float, barH: Float, area: Rect, avoid: List<Rect>, gap: Float, labelRoom: Float): Placement {
        // A side dock (an avoid rect that spans the area's full height at one edge) narrows the room sideways, so the bar slides clear of it.
        var left = area.left; var right = area.right
        avoid.forEach { r -> if (r.top <= area.top + 1f && r.bottom >= area.bottom - 1f) { if (r.left <= area.left + 1f) left = maxOf(left, r.right) else if (r.right >= area.right - 1f) right = minOf(right, r.left) } }
        val maxX = (right - barW).coerceAtLeast(left)
        val x = (item.center.x - barW / 2f).coerceIn(left, maxX)
        val under = Rect(item.left, item.bottom, item.right, item.bottom + labelRoom + gap + UNDER_FACTOR * item.height)
        val keep = listOf(item.inflate(gap / 2f), under.copy(top = under.top - gap / 2f))
        fun rectAt(y: Float) = Rect(x, y, x + barW, y + barH)
        fun inside(y: Float) = y >= area.top && y + barH <= area.bottom
        fun overlapArea(r: Rect, o: Rect): Float {
            val w = minOf(r.right, o.right) - maxOf(r.left, o.left); val h = minOf(r.bottom, o.bottom) - maxOf(r.top, o.top)
            return if (w > 0f && h > 0f) w * h else 0f
        }
        val candidates = listOf(
            item.top - gap - barH to false,
            under.bottom + gap to true,
            area.top to false,
            area.bottom - barH to true,
        ).map { (y, below) -> y.coerceIn(area.top, (area.bottom - barH).coerceAtLeast(area.top)).let { c -> Triple(c, below, inside(y)) } }
        candidates.firstOrNull { (y, _, fits) -> fits && keep.none { it.overlaps(rectAt(y)) } && avoid.none { it.overlaps(rectAt(y)) } }
            ?.let { return Placement(x, it.first, it.second) }
        val best = candidates.minBy { (y, _, _) -> keep.sumOf { overlapArea(rectAt(y), it).toDouble() } + avoid.sumOf { overlapArea(rectAt(y), it).toDouble() } * 4.0 }
        return Placement(x, best.first, best.second)
    }
}
