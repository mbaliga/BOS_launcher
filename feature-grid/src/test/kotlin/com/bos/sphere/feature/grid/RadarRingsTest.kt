package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent
import dev.aarso.search.palette.StableOrder
import dev.aarso.search.palette.UsageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Wave 18 (PAL-09, PAL-12, PAL-18): the Orbit rings are tiers of use. Pure logic: which result sits on which ring. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class RadarRingsTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private fun app(label: String) = AppEntry("p.$label", ComponentName("p.$label", "M"), label, Process.myUserHandle()) { null }

    // ---- ring capacity and assignment

    @Test fun PAL_18_ringCapacitiesGrowOutward() {
        assertEquals(listOf(5, 9, 13, 17), (0 until RadarRings.MAX_RINGS).map { RadarRings.capacity(it) })
    }

    @Test fun PAL_18_theInnermostRingHoldsTheBestResultsAndEachRingFillsBeforeTheNext() {
        val limits = RadarRings.limits(listOf(99, 99, 99, 99))
        val rings = RadarRings.assign(40, limits)
        assertEquals(40, rings.size)
        assertEquals(List(5) { 0 }, rings.take(5))
        assertEquals(List(9) { 1 }, rings.drop(5).take(9))
        assertEquals(List(13) { 2 }, rings.drop(14).take(13))
        assertEquals(List(13) { 3 }, rings.drop(27))
        assertEquals("rank order never goes back to an inner ring", rings.sorted(), rings)
    }

    @Test fun PAL_18_aRingNeverTakesMoreThanItsGeometryHasRoomFor() {
        // Ring 0 has room for 3 on this screen, ring 1 for 6, rings 2 and 3 for none.
        val limits = RadarRings.limits(listOf(3, 6, 0, 0))
        assertEquals(listOf(3, 6, 0, 0), limits)
        val rings = RadarRings.assign(20, limits)
        assertEquals(9, rings.size) // the other 11 results have no slot and are not drawn
        assertEquals(List(3) { 0 } + List(6) { 1 }, rings)
        assertEquals(listOf(0, 0), RadarRings.assign(2, limits)) // few results stay on the inner ring
        assertEquals(emptyList<Int>(), RadarRings.assign(0, limits))
    }

    @Test fun PAL_18_aFewResultsAllSitOnTheFirstRing() {
        assertEquals(List(3) { 0 }, RadarRings.assign(3, RadarRings.limits(listOf(99, 99, 99, 99))))
    }

    @Test fun PAL_18_tiersAreNamedForTalkBackFromInnermostOut() {
        assertEquals(listOf("Most used", "Often used", "Sometimes", "Rarely"), RadarRings.tierNames)
        assertEquals("Most used", RadarRings.tierName(0))
        assertEquals("Rarely", RadarRings.tierName(3))
        assertEquals("Rarely", RadarRings.tierName(9)) // clamped
    }

    @Test fun PAL_18_slotsOnARingAreEvenlySpacedStartingAboveTheCentre() {
        val a = RadarRings.evenAngles(5, 0)
        assertEquals(5, a.size)
        assertEquals(-Math.PI / 2, a[0], 1e-9)
        assertEquals(2 * Math.PI / 5, a[1] - a[0], 1e-9)
        // The next ring is turned half a step so its tiles do not line up with the inner ring's.
        assertEquals(Math.PI / 9, RadarRings.evenAngles(9, 1)[0] + Math.PI / 2, 1e-9)
        assertTrue(RadarRings.evenAngles(0, 0).isEmpty())
    }

    // ---- frequency over a decaying window

    @Test fun PAL_09_aRecentHabitOutweighsAnOldOne() {
        // Five launches a year ago against two this week.
        val old = List(5) { LaunchEvent("old", now - 365 * day, 9) }
        val fresh = List(2) { LaunchEvent("fresh", now - 2 * day, 9) }
        val s = RadarHabits.scores(old + fresh, emptyMap(), now)
        assertTrue(s.getValue("app:fresh") > s.getValue("app:old"))
    }

    @Test fun PAL_09_aLaunchCountsHalfAsMuchEveryTwoWeeks() {
        assertEquals(1.0, RadarHabits.weight(now, now), 1e-9)
        assertEquals(0.5, RadarHabits.weight(now - 14 * day, now), 1e-9)
        assertEquals(0.25, RadarHabits.weight(now - 28 * day, now), 1e-9)
        assertEquals(1.0, RadarHabits.weight(now + day, now), 1e-9) // a clock step back never boosts
    }

    @Test fun PAL_09_theLaunchLogAndWhatRadarLearnedAreNotCountedTwice() {
        // Three launches, all made from Radar: they are in both sources.
        val log = List(3) { LaunchEvent("x", now, 9) }
        val learned = mapOf("app:x" to UsageRecord(3, now), "contact:7" to UsageRecord(4, now))
        val s = RadarHabits.scores(log, learned, now)
        assertEquals(3.0, s.getValue("app:x"), 1e-9)
        assertEquals(4.0, s.getValue("contact:7"), 1e-9)
    }

    @Test fun PAL_18_appsComeOutMostUsedFirstAndAppsNeverOpenedAreLeftOut() {
        val a = app("A"); val b = app("B"); val c = app("C"); val never = app("Never")
        val log = List(1) { LaunchEvent(a.key, now - day, 9) } + List(4) { LaunchEvent(b.key, now - day, 9) } + List(2) { LaunchEvent(c.key, now - day, 9) }
        val ranked = RadarHabits.rankApps(listOf(a, b, c, never), recent = listOf(c, b, a), scores = RadarHabits.scores(log, emptyMap(), now))
        assertEquals(listOf("B", "C", "A"), ranked.map { it.label })
    }

    @Test fun PAL_18_equallyUsedAppsKeepTheNewestFirstAndTheOrderIsStable() {
        val a = app("A"); val b = app("B"); val c = app("C")
        val scores = mapOf("app:${a.key}" to 2.0, "app:${b.key}" to 2.0, "app:${c.key}" to 2.0)
        val first = RadarHabits.rankApps(listOf(a, b, c), recent = listOf(c, a, b), scores = scores).map { it.label }
        assertEquals(listOf("C", "A", "B"), first)
        repeat(5) { assertEquals(first, RadarHabits.rankApps(listOf(c, b, a), recent = listOf(c, a, b), scores = scores).map { it.label }) }
    }

    @Test fun PAL_18_nothingOpenedYetMeansNoTilesSoTheHintShows() {
        assertTrue(RadarHabits.rankApps(listOf(app("A")), emptyList(), emptyMap()).isEmpty())
    }

    // ---- stability while typing (PAL-12)

    @Test fun PAL_12_anItemKeepsItsRingWhileItsRankIsUnchanged() {
        val limits = RadarRings.limits(listOf(99, 99, 99, 99))
        val shown = (0 until 20).map { "k$it" }
        // A late result arrives that ranks last: nothing above it moves, so no ring changes.
        val merged = StableOrder.merge(shown, shown + "late", selectedKey = "k3") { it }
        val before = shown.zip(RadarRings.assign(shown.size, limits)).toMap()
        val after = merged.zip(RadarRings.assign(merged.size, limits)).toMap()
        shown.forEach { assertEquals("ring of $it", before.getValue(it), after.getValue(it)) }
        assertEquals(3, merged.indexOf("k3"))
    }

    @Test fun PAL_12_aRingChangesOnlyWhenTheRankCrossesARingBoundary() {
        val limits = RadarRings.limits(listOf(99, 99, 99, 99))
        val shown = (0 until 20).map { "k$it" }
        // A newcomer ranks first: everything shifts one place, so only the item that was last on a ring moves outward.
        val ideal = listOf("new") + shown
        val before = shown.zip(RadarRings.assign(shown.size, limits)).toMap()
        val after = ideal.zip(RadarRings.assign(ideal.size, limits)).toMap()
        val moved = shown.filter { before.getValue(it) != after.getValue(it) }
        assertEquals(listOf("k4", "k13"), moved) // ring 0 -> 1 and ring 1 -> 2
        // Ties are stable: ranking the same input again gives the same rings.
        assertEquals(after, ideal.zip(RadarRings.assign(ideal.size, limits)).toMap())
    }

    // ---- keyboard walks ring by ring (PAL-07)

    @Test fun PAL_07_arrowKeysWalkTheRingsInOrderFromTheInnermost() {
        val limits = RadarRings.limits(listOf(99, 99, 99, 99))
        val rings = RadarRings.assign(30, limits)
        var nav = RadarNav(rings.size)
        val visited = ArrayList<Int>()
        repeat(rings.size) {
            nav = RadarKeyboard.reduce(nav, RKey.Down).nav
            visited += rings[nav.selected]
        }
        assertEquals("walking down visits ring 0 first, then outward, never back", visited.sorted(), visited)
        assertEquals(0, visited.first())
        assertEquals(setOf(0, 1, 2, 3), visited.toSet())
        // And back up again.
        repeat(rings.size) { nav = RadarKeyboard.reduce(nav, RKey.Up).nav }
        assertEquals(0, nav.selected)
    }

    // ---- wave 20 audit

    @Test fun PAL_09_aTenThousandEntryLaunchLogScoresQuicklyAndExactly() {
        val log = List(10_000) { LaunchEvent(if (it % 2 == 0) "even" else "odd", now - (it % 30) * day / 3, 9) }
        val t0 = System.nanoTime()
        val s = RadarHabits.scores(log, emptyMap(), now)
        assertTrue("scoring 10,000 launches took ${(System.nanoTime() - t0) / 1_000_000} ms", (System.nanoTime() - t0) / 1_000_000 < 500)
        assertEquals(2, s.size)
        assertTrue(s.getValue("app:even") > s.getValue("app:odd")) // the even ones include the age-zero launches
    }

    @Test fun PAL_09_aClockStepBackNeverGivesANegativeOrInfiniteScore() {
        val log = listOf(LaunchEvent("x", now + 400 * day, 9), LaunchEvent("y", Long.MIN_VALUE / 2, 9), LaunchEvent("z", now, 9))
        val s = RadarHabits.scores(log, mapOf("app:l" to UsageRecord(3, Long.MAX_VALUE / 2)), now)
        s.values.forEach { assertTrue("$it", it.isFinite() && it >= 0.0) }
        assertEquals(1.0, s.getValue("app:x"), 1e-9)
        assertEquals(3.0, s.getValue("app:l"), 1e-9)
    }

    @Test fun PAL_18_hiddenAndUninstalledAppsNeverGetATileWhateverTheirHistory() {
        val shown = app("Shown"); val hidden = app("Hidden") // the caller passes only visible, installed apps
        val log = List(50) { LaunchEvent(hidden.key, now - day, 9) } + List(2) { LaunchEvent(shown.key, now - day, 9) } + List(9) { LaunchEvent("gone.pkg/Main#0", now, 9) }
        val ranked = RadarHabits.rankApps(listOf(shown), recent = listOf(shown), scores = RadarHabits.scores(log, emptyMap(), now))
        assertEquals(listOf("Shown"), ranked.map { it.label })
    }

    @Test fun PAL_18_aWorkProfileCopyOfAnAppIsAnotherEntryWithItsOwnUse() {
        val a = AppEntry("p.a", ComponentName("p.a", "M"), "A", Process.myUserHandle()) { null }
        val work = AppEntry("p.a", ComponentName("p.a", "M"), "A", android.os.UserHandle.getUserHandleForUid(10 * 100000 + 10001)) { null }
        assertTrue("the user is part of the key", a.key != work.key)
        val log = List(3) { LaunchEvent(work.key, now - day, 9) } + List(1) { LaunchEvent(a.key, now - day, 9) }
        assertEquals(listOf(work.key, a.key), RadarHabits.rankApps(listOf(a, work), emptyList(), RadarHabits.scores(log, emptyMap(), now)).map { it.key })
    }

    @Test fun PAL_18_ringsNeverAssignMoreResultsThanTheyHoldAndZeroRoomGivesNothing() {
        assertTrue(RadarRings.assign(0, RadarRings.limits(listOf(9, 9, 9, 9))).isEmpty())
        assertTrue(RadarRings.assign(10, RadarRings.limits(emptyList())).isEmpty())
        assertEquals(44, RadarRings.assign(10_000, RadarRings.limits(listOf(99, 99, 99, 99))).size)
        assertEquals(listOf("Most used", "Often used", "Sometimes", "Rarely"), (0..3).map { RadarRings.tierName(it) })
        assertEquals("Rarely", RadarRings.tierName(99)); assertEquals("Most used", RadarRings.tierName(-3))
    }
}
