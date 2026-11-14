package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBehaviourTest {
    private fun note(pkg: String, profile: Int, ongoing: Boolean = false, ts: Long = 1L) = HubNotification(
        id = "$pkg$profile$ts", key = "$pkg$profile$ts", source = "x", title = "t", summary = "s", timestamp = ts,
        packageName = pkg, profileId = profile, isOngoing = ongoing,
    )

    // ---- HOM-09
    @Test
    fun HOM_09_workProfileNotificationsBadgeOnlyTheWorkApp() {
        val feed = listOf(note("a", 0), note("a", 0), note("a", 10), note("b", 10, ongoing = true))
        val badges = Badges.compute(Badges.forHub(feed))
        assertEquals(2, badges[Badges.keyFor("a", 0)]?.count)
        assertEquals(1, badges[Badges.keyFor("a", 10)]?.count)
        assertNull(badges[Badges.keyFor("b", 10)])
    }

    @Test
    fun HOM_09_previewIsPerProfileNewestFirst() {
        val feed = listOf(note("a", 0, ts = 1), note("a", 10, ts = 2), note("a", 10, ts = 3))
        assertEquals(listOf(3L, 2L), Badges.previewFor(feed, "a", 10).map { it.timestamp })
        assertEquals(listOf(1L), Badges.previewFor(feed, "a", 0).map { it.timestamp })
    }

    @Test
    fun HOM_09_toggleDefaultsAreOnAndDotOnlyWhenCountOff() {
        val p = LauncherPrefs()
        assertTrue(p.badgesOn && p.badgeCount)
        assertEquals("", Badge(5).label(showCount = p.copy(badgeCount = false).badgeCount))
        assertEquals("5", Badge(5).label(showCount = p.badgeCount))
    }

    // ---- HOM-10
    private fun rec(k: String, suspended: Boolean = false, v: Long = 0) = AppRecord(k, k.substringBefore('/'), k, suspended = suspended, versionStamp = v)

    @Test
    fun HOM_10_everyKindOfPackageChangeIsADiffAndRemovalLeavesNoGap() {
        val before = listOf(rec("a/x"), rec("b/x"), rec("c/x"), rec("d/x"))
        val after = listOf(rec("a/x", v = 2), rec("c/x", suspended = true), rec("d/x"), rec("e/x"))
        val diff = PackageReconcile.diff(before, after)
        assertEquals(listOf("e/x"), diff.added.map { it.key })
        assertEquals(listOf("b/x"), diff.removed.map { it.key })
        assertEquals(setOf("a/x", "c/x"), diff.changed.map { it.key }.toSet()) // update and suspend
        assertEquals(setOf("a/x", "c/x", "b/x"), diff.iconKeysToEvict())
        val layout = HomeLayout(listOf(HomePage(listOf("a/x", "b/x", "c/x", "d/x"), id = 1)), seeded = true)
        val pages = HomeLayoutMath.visualPages(layout, after.map { it.key }, { 20 }, autoPlace = false)
        assertEquals(listOf("a/x", "c/x", "d/x"), pages.single().keys) // b is gone, the rest close up
    }

    @Test
    fun HOM_10_refreshCoalescerBoundsTheDelayBelowOneSecond() {
        val c = RefreshCoalescer()
        val first = c.onEvent(1000)
        for (t in 1001L..1300L step 10) c.onEvent(t) // a bulk install: dozens of callbacks
        assertTrue(first - 1000 < 1000)
        assertFalse(c.due(1100))
        assertTrue(c.due(first))
    }

    // ---- HOM-11
    @Test
    fun HOM_11_eachSizeClassKeepsItsOwnArrangementAndNewOnesStartFromTheNearest() {
        val portrait = SizeClass.of(400, 850)
        val landscape = SizeClass.of(850, 400)
        val a = HomeLayout(listOf(HomePage(listOf("a", "b"), id = 1)), seeded = true)
        var l = LayoutsBySizeClass()
        val (first, l1) = l.switchTo(portrait, a, null)
        assertEquals(a, first); l = l1
        // rotate: the landscape class is new, so it starts as a copy; positions are not lost
        val (seeded, l2) = l.switchTo(landscape, a, portrait)
        assertEquals(a, seeded); l = l2
        // edit in landscape, rotate back: portrait is untouched
        val edited = HomeLayout(listOf(HomePage(listOf("b", "a"), id = 1)), seeded = true)
        val (back, l3) = l.switchTo(portrait, edited, landscape)
        assertEquals(a, back)
        val (again, _) = l3.switchTo(landscape, back, portrait)
        assertEquals(edited, again)
    }

    @Test
    fun HOM_11_gridFollowsWindowAndDensityWhenAdaptiveIsOn() {
        val off = LauncherPrefs()
        assertEquals(off, AdaptivePrefs.effective(off, 411, 891))
        val on = off.copy(adaptiveGrid = true)
        val phone = AdaptivePrefs.effective(on, 411, 891)
        val tablet = AdaptivePrefs.effective(on, 1000, 1400)
        assertTrue(tablet.portraitGrid.columns > phone.portraitGrid.columns)
        assertEquals(phone.portraitGrid.columns, AdaptivePrefs.effective(on, 891, 411).portraitGrid.columns) // rotating gives the same portrait grid
        val dense = AdaptivePrefs.effective(on.copy(gridDensity = 1.5f), 411, 891)
        assertTrue(dense.portraitGrid.columns >= phone.portraitGrid.columns && dense.portraitGrid.rows > phone.portraitGrid.rows)
    }

    // ---- HOM-12
    private fun layoutOf(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), PageLayout(), 1)), seeded = true)

    @Test
    fun HOM_12_undoAndRedoOverTheLastTwentyChanges() {
        val s = LayoutEditSession(layoutOf("k0"))
        for (i in 1..25) assertTrue(s.observe(layoutOf("k$i")))
        assertEquals(20, s.undoDepth)
        var n = 0
        while (s.undo() != null) n++
        assertEquals(20, n)
        assertEquals(layoutOf("k5"), s.current) // changes 1..5 fell off the history
        assertEquals(layoutOf("k6"), s.redo())
    }

    @Test
    fun HOM_12_ourOwnUndoWriteIsNotRecordedAsAChange() {
        val s = LayoutEditSession(layoutOf("a"))
        s.observe(layoutOf("b"))
        val back = s.undo()!!
        assertFalse(s.observe(back)) // the store echoes the undo
        assertTrue(s.canRedo)
        s.exit()
        assertFalse(s.active)
        assertFalse(s.observe(layoutOf("z")))
    }
}
