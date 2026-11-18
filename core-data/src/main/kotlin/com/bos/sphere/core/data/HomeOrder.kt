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
 * Wave 28 (homeUnify): the home is ONE horizontal pager. Its stops are [Glance when it is on] then [the pages stop(s)]: with Pages Paginated
 * the stops after Glance are the pages themselves (one per page, nested in the same pager), with Continuous one scroll surface, with Pages Off none
 * (unless Glance is also off: then the first page is the only stop). Pure navigation, so each rule is stated and tested once.
 */
object HomeDeck {
    /** What home shows on start and on the Home button: stop 0, which is Glance when it is on, else the first page. */
    fun startSurface(glanceOn: Boolean): HomeShown = if (glanceOn) HomeShown.Glance else HomeShown.Pages

    /** Whether a pages stop exists in the outer pager: Pages is on, or Glance is off (then the first page IS home). */
    fun hasPagesStop(glanceOn: Boolean, mode: HomePages): Boolean = mode != HomePages.Off || !glanceOn

    /** How many stops the outer pager has. */
    fun outerCount(glanceOn: Boolean, mode: HomePages): Int = (if (glanceOn) 1 else 0) + (if (hasPagesStop(glanceOn, mode)) 1 else 0)

    /** The outer pager index of the pages stop, or null when there is none. */
    fun pagesIndex(glanceOn: Boolean, mode: HomePages): Int? = if (hasPagesStop(glanceOn, mode)) (if (glanceOn) 1 else 0) else null

    /** The surface the outer pager shows at [index]. */
    fun surfaceAt(glanceOn: Boolean, index: Int): HomeShown = if (glanceOn && index == 0) HomeShown.Glance else HomeShown.Pages

    /** How many pages are drawn: all of them, but with Pages Off (and Glance off) only the first. They are stored, never dropped. */
    fun visiblePages(mode: HomePages, total: Int): Int = if (mode == HomePages.Off) minOf(1, total) else total

    /**
     * The number of dots ONE page indicator draws: Glance (when on) is the first, then one per page when Paginated, one for the scroll when
     * Continuous, the first page alone when Pages is Off and Glance is off.
     */
    fun stops(glanceOn: Boolean, mode: HomePages, pageCount: Int): Int =
        (if (glanceOn) 1 else 0) + when (mode) {
            HomePages.Off -> if (glanceOn) 0 else 1
            HomePages.Continuous -> 1
            HomePages.Paginated -> pageCount.coerceAtLeast(1)
        }

    /**
     * Whether the ONE indicator row is drawn: several stops (Glance counts), or Glance alone with its indicator preference on. The bottom stack
     * the dock clears reads this same answer, so the rail never sits on a row that is drawn (a Glance indicator preference that is off hides the
     * row only when there is nothing else to count).
     */
    fun indicatorShown(glanceOn: Boolean, glanceIndicatorPref: Boolean, mode: HomePages, pageCount: Int): Boolean =
        stops(glanceOn, mode, pageCount) > 1 || (glanceOn && glanceIndicatorPref)

    /**
     * Whether a swipe back at the first page / the start of the scroll hands over to Glance. Only when Glance is on (Comms opens from its screen
     * edge, never from a swipe in the middle of a page) and never while editing (an icon being dragged toward the start edge is not a swipe) or while a card is open.
     */
    fun backSwipeToGlance(glanceOn: Boolean, editing: Boolean, cardOpen: Boolean): Boolean = glanceOn && !editing && !cardOpen

    /** The dot lit for outer pager [outerIndex] and inner page [pageIndex]. */
    fun lit(glanceOn: Boolean, outerIndex: Int, pageIndex: Int, mode: HomePages): Int =
        if (glanceOn && outerIndex == 0) 0 else (if (glanceOn) 1 else 0) + (if (mode == HomePages.Paginated) pageIndex else 0)

    /** Where a tap on dot [dot] goes: the outer stop and the inner page (null for Glance and for the scroll). */
    fun target(glanceOn: Boolean, mode: HomePages, dot: Int): Pair<Int, Int?> =
        if (glanceOn && dot == 0) 0 to null else (if (glanceOn) 1 else 0) to (if (mode == HomePages.Paginated) dot - (if (glanceOn) 1 else 0) else null)

    /** The result of "Add page": the new page list and the index of the page that was added (the one to show). */
    data class Added(val pages: List<HomePage>, val index: Int)

    /**
     * "Add page" (the edit card, the quick sheet). From Glance ([afterIndex] null) the page goes after the last one; when the
     * home has no page yet, or only the one untouched blank page a fresh install starts with, that blank page IS page 1 (it is shown, no
     * second blank page is stacked on it). From a page ([afterIndex] set) the new blank page goes right after it.
     */
    fun addPage(pages: List<HomePage>, afterIndex: Int?): Added = when {
        pages.isEmpty() -> Added(HomeLayoutMath.insertPage(pages, -1), 0)
        afterIndex == null && pages.size == 1 && pages[0].keys.isEmpty() -> Added(pages, 0)
        afterIndex == null -> Added(HomeLayoutMath.insertPage(pages, pages.size - 1), pages.size)
        else -> Added(HomeLayoutMath.insertPage(pages, afterIndex), (afterIndex + 1).coerceIn(0, pages.size))
    }
}
