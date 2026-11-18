package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.AppFlags
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.core.data.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 3, home behaviour: HOM-06, HOM-10, HOM-11, HOM-15, HOM-17, HOM-18. */
class HomeInfoW3Test {

    // ---- HOM-10 archived state
    @Test
    fun HOM_10_anArchivedAppIsALauncherEntryWhoseInstalledFlagIsClear() {
        assertTrue(AppFlags.archived(0))
        assertFalse(AppFlags.archived(android.content.pm.ApplicationInfo.FLAG_INSTALLED))
        assertTrue(AppFlags.suspended(android.content.pm.ApplicationInfo.FLAG_SUSPENDED or android.content.pm.ApplicationInfo.FLAG_INSTALLED))
    }

    @Test
    fun HOM_10_archivedAppStaysOnHomeDimmedMarkedAndLaunchable() {
        val look = AppPresentation.of(suspended = false, archived = true, profilePaused = false)
        assertEquals(AppMark.Archived, look.mark)
        assertTrue(look.alpha < 1f)
        assertTrue("launching restores an archived app", look.launchable)
        assertEquals(AppMark.None, AppPresentation.of(false, false, false).mark)
    }

    @Test
    fun HOM_10_archiveAndRestoreAreChangesThatEvictTheIconAndKeepThePosition() {
        val normal = AppRecord("a/x#0", "a", "A")
        val archived = normal.copy(archived = true)
        val diff = PackageReconcile.diff(listOf(normal), listOf(archived))
        assertEquals(listOf(archived), diff.changed)
        assertEquals(setOf("a/x#0"), diff.iconKeysToEvict())
        // still listed: nothing is removed, so the page keeps the key where it was
        val pages = listOf(HomePage(listOf("b/x#0", "a/x#0", "c/x#0"), PageLayout(), 1))
        val state = HomeState(pages = pages)
        assertEquals(state, PackageReconcile.applyToState(state, diff) { 12 })
        val back = PackageReconcile.diff(listOf(archived), listOf(normal))
        assertEquals(listOf(normal), back.changed)
    }

    // ---- HOM-06
    @Test
    fun HOM_06_pausedWorkProfileDimsItsAppsAndBlocksLaunchWhileSuspendedAndArchivedKeepTheirOwnRules() {
        val paused = AppPresentation.of(suspended = false, archived = false, profilePaused = true)
        assertEquals(AppMark.ProfilePaused, paused.mark)
        assertFalse(paused.launchable)
        assertEquals(AppMark.Suspended, AppPresentation.of(true, true, false).mark)
        assertEquals(AppMark.ProfilePaused, AppPresentation.of(true, true, true).mark)
    }

    @Test
    fun HOM_06_pauseChipFollowsTheProfileState() {
        assertNull(WorkProfileState.chipLabel(null)) // no work profile: no chip
        assertEquals("Pause work apps", WorkProfileState.chipLabel(false))
        assertEquals("Resume work apps", WorkProfileState.chipLabel(true))
    }

    @Test
    fun HOM_06_privateSpaceStillOnlyWhenUnlockedOnApi35() {
        val locked = ProfileInfo(10, ProfileKind.Private, quiet = true)
        assertFalse(ProfilePolicy.visible(locked, 35, true))
        assertTrue(ProfilePolicy.visible(locked.copy(quiet = false), 35, true))
        assertFalse(ProfilePolicy.visible(locked.copy(quiet = false), 34, true))
    }

    // ---- HOM-11
    @Test
    fun HOM_11_pinchStepsTheDensitySettingUntilTheGridReallyChangesAndStopsAtTheBounds() {
        val (w, h) = 411 to 891
        val d0 = 1f
        val denser = AdaptivePrefs.stepDensity(d0, +1, w, h)
        assertTrue(denser > d0)
        assertNotEquals(AdaptivePrefs.grids(w.toFloat(), h.toFloat(), d0), AdaptivePrefs.grids(w.toFloat(), h.toFloat(), denser))
        val sparser = AdaptivePrefs.stepDensity(d0, -1, w, h)
        assertTrue(sparser < d0)
        assertNotEquals(AdaptivePrefs.grids(w.toFloat(), h.toFloat(), d0), AdaptivePrefs.grids(w.toFloat(), h.toFloat(), sparser))
        assertEquals(2f, AdaptivePrefs.stepDensity(2f, +1, w, h), 0.0001f)
        assertEquals(0.5f, AdaptivePrefs.stepDensity(0.5f, -1, w, h), 0.0001f)
        assertEquals(1.3f, AdaptivePrefs.stepDensity(1.3f, 0, w, h), 0.0001f)
    }

    private val portrait = SizeClass.of(400, 850)
    private val landscape = SizeClass.of(850, 400)
    private fun layout(vararg keys: String) = HomeLayout(listOf(HomePage(keys.toList(), PageLayout(), 1)), seeded = true)

    @Test
    fun HOM_11_foldersAndWidgetsHavePerClassPositionsThatSurviveTheStore() {
        val inPortrait = layout("a/x#0", "folder:f1", "widget:7", "b/x#0")
        var l = LayoutsBySizeClass()
        val (_, l1) = l.switchTo(portrait, inPortrait, null); l = l1
        // landscape starts from a copy, then the user moves the folder to the front there
        val (seeded, l2) = l.switchTo(landscape, inPortrait, portrait); l = l2
        assertEquals(inPortrait, seeded)
        val moved = layout("folder:f1", "a/x#0", "widget:7", "b/x#0")
        val (backPortrait, l3) = l.switchTo(portrait, moved, landscape)
        assertEquals(inPortrait, backPortrait) // portrait untouched by the landscape edit
        assertEquals(moved, l3.switchTo(landscape, backPortrait, portrait).first)
    }

    @Test
    fun HOM_11_aFolderMadeInOneClassShowsInTheOtherAndADeletedOneLeavesNoGap() {
        val extras = HomeState(folders = mapOf("f1" to Folder("f1", "Work", listOf("c/x#0"))))
        val apps = listOf("a/x#0", "b/x#0", "c/x#0")
        // the other class never knew folder f1: it is placed at the end, the app inside it is not shown twice
        val otherClass = layout("a/x#0", "b/x#0", "c/x#0")
        val shown = HomeLayoutMath.visualPages(HomeResolve.layoutFor(otherClass, extras), HomeResolve.available(apps, extras), capacityOf = { 12 }).flatMap { it.keys }
        assertEquals(listOf("a/x#0", "b/x#0", "folder:f1"), shown)
        // a class that still lists a folder that was dissolved elsewhere shows nothing for it
        val stale = layout("a/x#0", "folder:gone", "b/x#0")
        val after = HomeLayoutMath.visualPages(stale, HomeResolve.available(apps, HomeState()), capacityOf = { 12 }).flatMap { it.keys }
        assertFalse("folder:gone" in after)
        assertEquals(listOf("a/x#0", "b/x#0", "c/x#0"), after)
    }

    @Test
    fun HOM_11_aWideWidgetOnANarrowClassIsSplitOntoAnotherPageNotLost() {
        val spanOf = { k: String -> if (k == "widget:1") WidgetSpan(4, 2) else WidgetSpan(1, 1) }
        val page = HomePage(listOf("a", "widget:1", "b"), PageLayout(), 1)
        val narrow = SpanReflow.reflow(listOf(page), spanOf) { 3 to 4 } // 3 columns: the 4-wide widget cannot fit; it is still kept
        assertEquals(listOf("a", "widget:1", "b").sorted(), narrow.flatMap { it.keys }.sorted())
    }

    // ---- HOM-15, HOM-17, HOM-18
    @Test
    fun HOM_15_panelTileIsALinkWhoseTargetNamesItsTab() {
        PanelTab.entries.forEach { assertEquals(it, HomePanelLinks.tabOf(HomePanelLinks.uri(it))) }
        assertNull(HomePanelLinks.tabOf("https://example.org"))
        assertNull(HomePanelLinks.tabOf("liftoff-panel://nope"))
        val def = ItemDef(ItemKeys.link(HomePanelLinks.uri(PanelTab.Usage)), ItemKind.Link, "Usage", HomePanelLinks.uri(PanelTab.Usage))
        val st = HomeState(pages = listOf(HomePage(listOf(def.key), PageLayout(), 1)), items = mapOf(def.key to def))
        assertEquals(listOf(PanelTab.Usage), HomePanelLinks.placed(st))
        // the key is stable per panel so a second add can be refused by the host
        assertEquals(def.key, ItemKeys.link(HomePanelLinks.uri(PanelTab.Usage)))
    }

    @Test
    fun HOM_15_filesCanBeNarrowedToOnePinnedFolder() {
        val f = { u: String, s: String -> RecentFile(u, u, "", 1, s) }
        val all = listOf(f("1", "A"), f("2", "B"), f("3", "A"))
        assertEquals(3, FilesView.filter(all, null).size)
        assertEquals(listOf("1", "3"), FilesView.filter(all, "A").map { it.uri })
        assertTrue(FilesView.filter(all, "gone").isEmpty())
    }

    @Test
    fun HOM_17_weekRangeStartsSixDaysBeforeMidnightAndTodayAtMidnight() {
        val midnight = 10 * 86_400_000L
        assertEquals(midnight, UsageRange.Today.startMs(midnight))
        assertEquals(midnight - 6 * 86_400_000L, UsageRange.Week.startMs(midnight))
    }

    @Test
    fun HOM_18_theTimelineIsTheLast100OpensWithScheduledLaunchesAndNoFilter() {
        val now = 10_000_000L
        val opens = (1..200).map { LaunchEvent("a", now - it * 1000L, 9) }
        val sched = listOf(TimelineEntry(now + 5, "a", "A", true, "s"))
        val t = TimelineView.build(opens, sched, now) { "A" }
        assertEquals(1 + 100, t.size)
        assertEquals(100, TimelineView.OPENS)
        assertTrue("scheduled launches are always in, soonest first", t.first().scheduled)
        assertTrue("newest first", t.drop(1).zipWithNext().all { (x, y) -> x.timeMs >= y.timeMs })
        assertEquals(now - 1000L, t[1].timeMs)
    }

    @Test
    fun HOM_18_rowsStartANewGroupOnEachNewDay() {
        val d = 86_400_000L
        val entries = listOf(TimelineEntry(d + 5, "", "next", true), TimelineEntry(5, "a", "A", false), TimelineEntry(4, "a", "A", false), TimelineEntry(-d + 1, "a", "A", false))
        val rows = TimelineView.rows(entries, 0)
        assertEquals(listOf(true, true, false, true), rows.map { it.header })
        assertEquals(listOf("Tomorrow", "Today", "Today", "Yesterday"), rows.map { it.day })
    }
}
