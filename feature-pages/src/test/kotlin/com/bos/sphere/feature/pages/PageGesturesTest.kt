package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SLOP = 10f

private fun two(ax: Float, ay: Float, bx: Float, by: Float, ida: Long = 1, idb: Long = 2) =
    listOf(ida to Pt(ax, ay), idb to Pt(bx, by))

private fun one(x: Float, y: Float, id: Long = 1) = listOf(id to Pt(x, y))

/** Mode 1 (and mode 3's two-finger vertical): translation versus pinch / spread. */
class TwoFingerSwipeTest {

    private fun started(axis: PageAxis = PageAxis.Horizontal) =
        TwoFingerSwipe(axis, SLOP).also { it.update(two(100f, 500f, 200f, 500f)) }

    @Test
    fun parallelSidewaysTravelClaimsTheNextOrPreviousPage() {
        val left = started()
        assertEquals(Verdict.Claimed(1), left.update(two(70f, 500f, 170f, 500f)))
        assertEquals(-30f, left.drag(two(70f, 500f, 170f, 500f)), 0.001f)
        val right = started()
        assertEquals(Verdict.Claimed(-1), right.update(two(130f, 502f, 230f, 498f)))
    }

    @Test
    fun aSlightChangeInFingerDistanceIsStillATranslation() {
        // 5 px closer over 100 px: well inside the tolerance.
        assertEquals(Verdict.Claimed(1), started().update(two(70f, 500f, 165f, 500f)))
    }

    @Test
    fun pinchAndSpreadKeepTheirMeaning() {
        assertEquals(Verdict.Declined, started().update(two(130f, 500f, 170f, 500f))) // pinch in
        assertEquals(Verdict.Declined, started().update(two(80f, 500f, 220f, 500f))) // spread
        // A pinch made while the hand drifts: both move right, but the span grows by 20 %.
        assertEquals(Verdict.Declined, started().update(two(120f, 500f, 240f, 500f)))
    }

    @Test
    fun theTranslationToleranceDecidesBeforeTheCoverflowPinchWould() {
        // The coverflow's pinch fires at a span ratio of 0.78 / 1.28; 0.18 is always met first.
        assertTrue(TwoFingerSwipe.SPAN_TOLERANCE < 1f - 0.78f)
        assertTrue(TwoFingerSwipe.SPAN_TOLERANCE < 1.28f - 1f)
    }

    @Test
    fun smallMovesStayPendingSoTheContentKeepsTheTouch() {
        assertEquals(Verdict.Pending, started().update(two(95f, 500f, 195f, 500f)))
    }

    @Test
    fun aTwoFingerScrollAcrossTheAxisBelongsToTheContent() {
        assertEquals(Verdict.Declined, started().update(two(100f, 470f, 200f, 470f)))
    }

    @Test
    fun theVerticalAxisClaimsUpAsNextAndDownAsPrevious() {
        assertEquals(Verdict.Claimed(1), started(PageAxis.Vertical).update(two(100f, 460f, 200f, 462f)))
        assertEquals(Verdict.Claimed(-1), started(PageAxis.Vertical).update(two(101f, 540f, 199f, 540f)))
        // In vertical mode, sideways two-finger travel is not a page gesture.
        assertEquals(Verdict.Declined, started(PageAxis.Vertical).update(two(60f, 500f, 160f, 500f)))
    }

    @Test
    fun fingersTravellingDifferentWaysNeverClaim() {
        // Apart (a spread, here): never a page turn.
        assertFalse(started().update(two(85f, 500f, 215f, 500f)) is Verdict.Claimed)
        val t = started()
        // One finger moves left past slop, the other stays: not both past slop.
        assertFalse(t.update(two(80f, 500f, 195f, 500f)) is Verdict.Claimed)
    }

    @Test
    fun aThirdFingerOrALiftBeforeTheClaimDeclines() {
        val three = started()
        assertEquals(Verdict.Declined, three.update(two(90f, 500f, 190f, 500f) + (3L to Pt(300f, 500f))))
        val lift = started()
        assertEquals(Verdict.Declined, lift.update(one(100f, 500f)))
        val swap = started()
        assertEquals(Verdict.Declined, swap.update(two(70f, 500f, 170f, 500f, ida = 1, idb = 3)))
    }

    @Test
    fun measurementStartsWhenTheSecondFingerLands() {
        val s = TwoFingerSwipe(PageAxis.Horizontal, SLOP)
        // The first finger alone may scrub far; that is the coverflow's.
        assertEquals(Verdict.Pending, s.update(one(100f, 500f)))
        assertEquals(Verdict.Pending, s.update(one(400f, 500f)))
        assertEquals(Verdict.Pending, s.update(two(400f, 500f, 500f, 500f)))
        assertEquals(Verdict.Claimed(1), s.update(two(370f, 500f, 470f, 500f)))
    }

    @Test
    fun aClaimHoldsForTheRestOfTheGesture() {
        val s = started()
        s.update(two(70f, 500f, 170f, 500f))
        assertEquals(Verdict.Claimed(1), s.update(two(10f, 500f, 300f, 500f)))
        assertEquals(Verdict.Claimed(1), s.update(one(10f, 500f)))
    }
}

/** Mode 2 on full pages: edge strips inside the system back-gesture zones. */
class EdgeSwipeTest {

    @Test
    fun stripsSitJustInsideTheSystemGestureInsets() {
        // 1000 px wide, 30 px system-gesture insets, 24 px strips.
        assertEquals(0, EdgeSwipe.sideAt(10f, 1000f, 30f, 30f, 24f)) // the system's back zone
        assertEquals(-1, EdgeSwipe.sideAt(40f, 1000f, 30f, 30f, 24f))
        assertEquals(0, EdgeSwipe.sideAt(60f, 1000f, 30f, 30f, 24f))
        assertEquals(1, EdgeSwipe.sideAt(960f, 1000f, 30f, 30f, 24f))
        assertEquals(0, EdgeSwipe.sideAt(980f, 1000f, 30f, 30f, 24f))
        // Three-button navigation: no insets, the strips start at the edges.
        assertEquals(-1, EdgeSwipe.sideAt(0f, 1000f, 0f, 0f, 24f))
        assertEquals(1, EdgeSwipe.sideAt(999f, 1000f, 0f, 0f, 24f))
    }

    @Test
    fun inwardSwipesTurnThePage() {
        assertEquals(Verdict.Claimed(-1), EdgeSwipe(-1, Pt(40f, 500f), SLOP).update(one(80f, 505f)))
        assertEquals(Verdict.Claimed(1), EdgeSwipe(1, Pt(960f, 500f), SLOP).update(one(900f, 498f)))
    }

    @Test
    fun outwardVerticalAndTwoFingerTouchesAreTheContents() {
        assertEquals(Verdict.Declined, EdgeSwipe(-1, Pt(40f, 500f), SLOP).update(one(10f, 500f)))
        assertEquals(Verdict.Declined, EdgeSwipe(-1, Pt(40f, 500f), SLOP).update(one(45f, 560f)))
        assertEquals(Verdict.Declined, EdgeSwipe(1, Pt(960f, 500f), SLOP).update(two(940f, 500f, 500f, 500f)))
        assertEquals(Verdict.Declined, EdgeSwipe(0, Pt(500f, 500f), SLOP).verdict)
    }

    @Test
    fun aTapInTheStripStaysPending() {
        assertEquals(Verdict.Pending, EdgeSwipe(-1, Pt(40f, 500f), SLOP).update(one(44f, 503f)))
    }
}

/** Mode 3 on home: which vertical drags mean "page" and which mean "hub". */
class HomeSwipeUpTest {
    private val start = Pt(500f, 800f)

    @Test
    fun upIsTheNextPage() {
        assertEquals(Verdict.Claimed(1), HomeSwipeUp(start, SLOP).update(one(505f, 760f)))
    }

    @Test
    fun downIsTheHubPullAndSidewaysIsTheScrub() {
        assertEquals(Verdict.Declined, HomeSwipeUp(start, SLOP).update(one(505f, 840f)))
        assertEquals(Verdict.Declined, HomeSwipeUp(start, SLOP).update(one(560f, 790f)))
    }

    @Test
    fun theTieBreakMatchesTheSurface() {
        // |dx| == |dy|: the surface calls it vertical (not a scrub), so up is the page.
        assertEquals(Verdict.Claimed(1), HomeSwipeUp(start, SLOP).update(one(530f, 770f)))
    }

    @Test
    fun onlyWhenHomeIsAtRestAndClearOfTheBottomGestureInset() {
        assertTrue(HomeSwipeUp.eligible(start, 1000f, 48f, hubFraction = 0f, zoom = 0f))
        assertFalse(HomeSwipeUp.eligible(Pt(500f, 980f), 1000f, 48f, 0f, 0f)) // starts in the system's zone
        assertFalse(HomeSwipeUp.eligible(start, 1000f, 48f, hubFraction = 0.5f, zoom = 0f)) // hub open: closing it
        assertFalse(HomeSwipeUp.eligible(start, 1000f, 48f, hubFraction = 0f, zoom = 0.3f)) // globe
    }

    @Test
    fun aSecondFingerDeclines() {
        assertEquals(Verdict.Declined, HomeSwipeUp(start, SLOP).update(two(500f, 790f, 600f, 790f)))
    }
}

class PageTurnTest {

    @Test
    fun dragFollowsTheFingerWithARubberBandAtTheEnds() {
        assertEquals(1.3f, PageTurn.dragPosition(1, -300f, 1000f, 3), 0.0001f)
        assertEquals(-0.05f, PageTurn.dragPosition(0, 200f, 1000f, 3), 0.0001f)
        assertEquals(2.12f, PageTurn.dragPosition(2, -500f, 1000f, 3), 0.0001f)
        assertEquals(-PageTurn.MAX_OVERDRAG, PageTurn.dragPosition(0, 5000f, 1000f, 3), 0.0001f)
    }

    @Test
    fun settleByDistanceOrFlingOnePageAtMost() {
        assertEquals(2, PageTurn.settleTarget(1, 1.3f, 0f, 1000f, 3))
        assertEquals(1, PageTurn.settleTarget(1, 1.1f, 0f, 1000f, 3))
        assertEquals(0, PageTurn.settleTarget(1, 0.7f, 0f, 1000f, 3))
        assertEquals(2, PageTurn.settleTarget(1, 1.1f, -1500f, 1000f, 3)) // flung toward next
        assertEquals(0, PageTurn.settleTarget(1, 1.3f, 1500f, 1000f, 3)) // flung back: the fling wins
        assertEquals(2, PageTurn.settleTarget(2, 2.12f, 0f, 1000f, 3)) // past the last page: stays
        assertEquals(0, PageTurn.settleTarget(0, -0.1f, 3000f, 1000f, 3))
    }

    @Test
    fun neighboursNeverWrap() {
        assertNull(PageTurn.neighbour(0, -1, 3))
        assertEquals(1, PageTurn.neighbour(0, 1, 3))
        assertNull(PageTurn.neighbour(2, 1, 3))
        assertNull(PageTurn.neighbour(1, 0, 3))
    }

    @Test
    fun onlyTheCurrentPageAndItsNeighboursStayAlive() {
        assertEquals(0..1, PageTurn.liveWindow(0f, 5))
        assertEquals(1..3, PageTurn.liveWindow(2f, 5))
        assertEquals(1..3, PageTurn.liveWindow(2.4f, 5)) // mid-turn: pages 2 and 3 both inside
        assertEquals(2..4, PageTurn.liveWindow(2.6f, 5))
        assertEquals(3..4, PageTurn.liveWindow(4f, 5))
        assertEquals(0..0, PageTurn.liveWindow(0f, 1))
    }

    @Test
    fun restAndCurrent() {
        assertTrue(PageTurn.atRest(2f))
        assertFalse(PageTurn.atRest(2.3f))
        assertEquals(2, PageTurn.current(2.4f, 5))
        assertEquals(4, PageTurn.current(9f, 5))
    }
}

/** Mode 2 on home: past the coverflow's ends, beyond a resistance threshold. */
class PastEndTest {

    @Test
    fun ordinaryScrubbingNeverTurnsThePage() {
        // The coverflow's rubber band stops drawing at 0.6 columns past an end.
        assertEquals(0, PastEnd.direction(0.6f))
        assertEquals(0, PastEnd.direction(-0.6f))
        assertEquals(0, PastEnd.direction(1.39f))
    }

    @Test
    fun aDeliberatePullDoes() {
        assertEquals(1, PastEnd.direction(1.4f))
        assertEquals(-1, PastEnd.direction(-2f))
    }

    @Test
    fun theHintStages() {
        assertEquals(PastEnd.Stage.None, PastEnd.stage(0.5f))
        assertEquals(PastEnd.Stage.Approaching, PastEnd.stage(0.7f))
        assertEquals(PastEnd.Stage.Approaching, PastEnd.stage(-0.9f))
        assertEquals(PastEnd.Stage.Ready, PastEnd.stage(1.4f))
    }

    @Test
    fun reducedMotionIsTheAnimatorScaleAtZero() {
        assertTrue(MotionPolicy.reduceMotion(0f))
        assertFalse(MotionPolicy.reduceMotion(0.5f))
        assertFalse(MotionPolicy.reduceMotion(1f))
    }
}

/**
 * Releasing a two-finger page swipe: Android lifts the fingers one event apart (POINTER_UP, then UP),
 * so the event in between has only one finger down. It must not move the page or feed a 0 drag into
 * the velocity, or a forward swipe snaps back or turns the wrong way.
 */
class ReleaseTest {

    private val extent = 1000f
    private val fling = 1500f

    /** A two-finger swipe toward the next page, fed through the classifier and [DragFeed] like the pager does. */
    private fun forwardSwipe(): Pair<TwoFingerSwipe, List<Pair<Long, Float>>> {
        val swipe = TwoFingerSwipe(PageAxis.Horizontal, SLOP)
        val feed = DragFeed()
        val samples = ArrayList<Pair<Long, Float>>()
        swipe.update(two(600f, 500f, 700f, 500f))
        var t = 0L
        for (x in listOf(580f, 540f, 480f, 400f, 320f)) {
            t += 8
            val ps = two(x, 500f, x + 100f, 500f)
            if (swipe.verdict == Verdict.Pending) swipe.update(ps)
            feed.feed(t, swipe.liveDrag(ps))?.let { samples += t to it }
        }
        assertEquals(Verdict.Claimed(1), swipe.verdict)
        // POINTER_UP: finger 2 has lifted, finger 1 is still down.
        t += 6
        val afterLift = one(318f, 500f, id = 1)
        assertNull(swipe.liveDrag(afterLift))
        feed.feed(t, swipe.liveDrag(afterLift))?.let { samples += t to it }
        return swipe to samples
    }

    @Test
    fun liftingOneFingerKeepsTheLastTwoFingerDrag() {
        val (swipe, _) = forwardSwipe()
        assertEquals(-280f, swipe.drag(one(318f, 500f, id = 1)), 0.001f)
        assertEquals(-280f, swipe.drag(emptyList()), 0.001f)
    }

    @Test
    fun theFingersAreFoundByIdNotByOrder() {
        val swipe = TwoFingerSwipe(PageAxis.Horizontal, SLOP)
        swipe.update(two(600f, 500f, 700f, 500f))
        // Same two pointers reported in the other order: the drag is the same.
        assertEquals(-20f, swipe.drag(listOf(2L to Pt(680f, 500f), 1L to Pt(580f, 500f))), 0.001f)
    }

    @Test
    fun aSwipeEndingPointerUpThenUpSettlesOnTheNextPage() {
        val (_, samples) = forwardSwipe()
        // Nothing was recorded for the POINTER_UP event: the last sample is still the swipe's.
        assertEquals(-280f, samples.last().second, 0.001f)
        val (t1, d1) = samples[samples.size - 2]
        val (t2, d2) = samples.last()
        val velocity = (d2 - d1) / (t2 - t1) * 1000f // px/s, negative = toward next
        val position = PageTurn.dragPosition(1, d2, extent, 3)
        assertEquals(2, PageTurn.settleTarget(1, position, velocity, fling, 3))
    }

    @Test
    fun theOldZeroSampleWouldHaveTurnedBackwards() {
        // What happened before the fix: the POINTER_UP event fed drag = 0, snapping the position back
        // to the start page and adding a sample that reads as a fling the other way.
        val velocity = (0f - -280f) / 6f * 1000f
        assertEquals(0, PageTurn.settleTarget(1, 1f, velocity, fling, 3))
    }

    @Test
    fun aSwipeThatRestedBeforeLiftingIsNotAFling() {
        val feed = DragFeed()
        assertEquals(-280f, feed.feed(100, -280f)!!, 0f)
        // The fingers rested 200 ms, then one lifted: one stationary sample at the lift time.
        assertEquals(-280f, feed.feed(300, null)!!, 0f)
        assertNull(feed.feed(310, null))
    }

    @Test
    fun singleFingerClassifiersFollowTheirOwnFinger() {
        val edge = EdgeSwipe(side = 1, start = Pt(1000f, 500f), slopPx = SLOP)
        edge.update(one(1000f, 500f, id = 7))
        edge.update(one(960f, 500f, id = 7))
        assertEquals(Verdict.Claimed(1), edge.verdict)
        // A second finger lands first in the list: the drag still follows finger 7.
        assertEquals(-60f, edge.liveDrag(listOf(3L to Pt(10f, 10f), 7L to Pt(940f, 500f)))!!, 0.001f)
        assertNull(edge.liveDrag(one(10f, 10f, id = 3)))

        val up = HomeSwipeUp(Pt(500f, 800f), SLOP)
        up.update(one(500f, 800f, id = 4))
        up.update(one(500f, 760f, id = 4))
        assertEquals(Verdict.Claimed(1), up.verdict)
        assertEquals(-40f, up.liveDrag(one(500f, 760f, id = 4))!!, 0.001f)
        assertNull(up.liveDrag(emptyList()))
    }
}
