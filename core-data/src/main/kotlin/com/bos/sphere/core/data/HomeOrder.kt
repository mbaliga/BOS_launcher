package com.bos.sphere.core.data

/** A physical screen edge (not a layout direction: the home draws in physical coordinates whatever the app language is). */
enum class PhysicalSide { Left, Right;
    val opposite: PhysicalSide get() = if (this == Left) Right else Left
}

/**
 * Wave 26 (HOM-01, HOM-23): the one abstraction of the home's direction. "Left to right" (the default) has the Launchpad on the left edge
 * and pages / columns progressing from left to right; "Right to left" mirrors all of it: the Launchpad on the right edge, the Recents panel
 * on the left, columns filled from the right, pages ordered right to left, swipe directions mirrored, the Glance hotspots' left and right roles
 * swapped. It is independent of the app language: the home is laid out in physical coordinates, so every function here takes and returns
 * physical x and physical sides, and the four combinations (order x language direction) draw the same.
 */
enum class HomeOrder(val label: String) {
    LeftToRight("Left to right"),
    RightToLeft("Right to left");

    val rtl: Boolean get() = this == RightToLeft

    /** The edge the Launchpad (notifications) handle, slide-in and shrunken-home card belong to. */
    val launchpadSide: PhysicalSide get() = if (rtl) PhysicalSide.Right else PhysicalSide.Left

    /** The edge of the Recents panel (or the dock rail swipe): the other one. */
    val recentsSide: PhysicalSide get() = launchpadSide.opposite

    /** The edge pages and columns start at, and where a scroll begins. */
    val startSide: PhysicalSide get() = launchpadSide

    /** The sign of the finger's horizontal movement that goes FORWARD (to the next page / column): left (negative) normally, right in R-to-L. */
    val forwardDragSign: Int get() = if (rtl) 1 else -1

    /** True when a horizontal finger movement of [dx] px is a forward swipe (towards the next page). */
    fun isForwardDrag(dx: Float): Boolean = dx * forwardDragSign > 0f

    /** True when [dx] moves towards the Launchpad edge (the swipe that opens the Launchpad from the first page). */
    fun isTowardsLaunchpad(dx: Float): Boolean = !isForwardDrag(dx) && dx != 0f

    /** The physical x of the left edge of a [width] wide item whose logical start is [logicalStart] in a [total] wide row (columns fill from the start side). */
    fun physicalLeft(logicalStart: Float, width: Float, total: Float): Float = if (rtl) total - logicalStart - width else logicalStart

    /** The physical position (0 = leftmost) of logical page / column [index] among [count]. */
    fun visualIndex(index: Int, count: Int): Int = if (rtl) count - 1 - index else index

    /** The reverse of [visualIndex] (it is its own inverse). */
    fun logicalIndex(visual: Int, count: Int): Int = visualIndex(visual, count)

    /** The physical corner that plays the role [c] has in left to right (top-left stays the first hotspot: it becomes the top-right one when mirrored). */
    fun corner(c: Corner): Corner = if (!rtl) c else when (c) {
        Corner.TopLeft -> Corner.TopRight
        Corner.TopRight -> Corner.TopLeft
        Corner.BottomLeft -> Corner.BottomRight
        Corner.BottomRight -> Corner.BottomLeft
    }

    /** The scroll offset (px) a horizontal scroll of [max] px starts at: the start edge. */
    fun initialScroll(max: Int): Int = if (rtl) max else 0

    companion object {
        fun parse(name: String?): HomeOrder = entries.firstOrNull { it.name == name } ?: LeftToRight
    }
}

/**
 * The home deck: [Glance] first when it is on, then the pages. Pure navigation, so "forward from Glance goes to page 1" and "with Glance off the
 * first page is what home shows" are each stated and tested once.
 */
object HomeDeck {
    /** What home shows on start and on the Home button. */
    fun startSurface(glanceOn: Boolean): HomeShown = if (glanceOn) HomeShown.Glance else HomeShown.Pages

    /** The surface after a forward swipe from [from]: Glance goes to the pages; the pages stay (the pager itself moves between its pages). */
    fun forward(from: HomeShown): HomeShown = HomeShown.Pages

    /** The surface after a backward swipe at the first page: Glance when it is on, else none (the Launchpad edge takes it). */
    fun backwardFromFirstPage(glanceOn: Boolean): HomeShown? = if (glanceOn) HomeShown.Glance else null

    /** Turning Glance off while it is showing moves to the pages. */
    fun afterGlanceSetting(glanceOn: Boolean, shown: HomeShown): HomeShown = if (!glanceOn && shown == HomeShown.Glance) HomeShown.Pages else shown

    /** The result of "Add page": the new page list and the index of the page that was added (the one to show). */
    data class Added(val pages: List<HomePage>, val index: Int)

    /**
     * "Add page" (quick sheet, Ground Control, the add card). From Glance ([afterIndex] null) the page goes after the last one; when the
     * home has no page yet, or only the one untouched blank page a fresh install starts with, that blank page IS page 1 (it is shown, no
     * second blank page is stacked on it). From a page ([afterIndex] set) the new blank page goes right after it.
     */
    fun addPage(pages: List<HomePage>, afterIndex: Int?): Added = when {
        pages.isEmpty() -> Added(HomeLayoutMath.insertPage(pages, -1), 0)
        afterIndex == null && pages.size == 1 && pages[0].keys.isEmpty() -> Added(pages, 0)
        afterIndex == null -> Added(HomeLayoutMath.insertPage(pages, pages.size - 1), pages.size)
        else -> Added(HomeLayoutMath.insertPage(pages, afterIndex), (afterIndex + 1).coerceIn(0, pages.size))
    }

    /** The number of stops a page indicator counts: Glance (when on) is the first stop, then one per page (the scroll counts as one stop). */
    fun stops(glanceOn: Boolean, pageCount: Int, scroll: Boolean): Int = (if (glanceOn) 1 else 0) + (if (scroll) 1 else pageCount.coerceAtLeast(1))
}
