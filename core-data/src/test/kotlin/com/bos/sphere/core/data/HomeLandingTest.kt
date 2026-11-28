package com.bos.sphere.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

/** HOM-22: the home landing setting, the Recent landing's source, the Minimal hotspots, and what is private. */
class HomeLandingTest {
    @get:Rule val tmp = TemporaryFolder()

    private class QueueExecutor : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    private fun app(n: String, work: Boolean = false, pkg: String = "pkg.$n") = RecentCandidate("$n#${if (work) 10 else 0}", pkg, work)
    private fun ev(key: String, t: Long) = LaunchEvent(key, t, 0)
    private val a = app("a"); private val b = app("b"); private val c = app("c"); private val w = app("w", work = true)
    private val all = listOf(a, b, c, w)

    // ---- the settings (wave 26: Glance on / off, the pages mode, the order; the landing selector is gone)

    @Test fun HOM_22_aFreshInstallHasGlanceOnLeftToRightAndNoModeChosenYetAndEachSettingIsPersisted() {
        val d = LauncherPrefs()
        assertTrue(d.glanceEnabled); assertEquals(HomeOrder.LeftToRight, d.homeOrder); assertEquals(HomePages.Off, d.homePages)
        val sp = MapSharedPreferences()
        val store = LauncherPrefsStore(sp)
        store.update { it.copy(glanceEnabled = false, homeOrder = HomeOrder.RightToLeft, homePages = HomePages.Continuous) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertFalse(back.glanceEnabled); assertEquals(HomeOrder.RightToLeft, back.homeOrder); assertEquals(HomePages.Continuous, back.homePages)
        assertEquals("RightToLeft", sp.getString("homeOrder", null))
    }

    @Test fun HOM_22_anUnknownStoredOrderFallsBackToLeftToRightAndTheRecentsLimitIsClamped() {
        val sp = MapSharedPreferences(mapOf("homeOrder" to "Sideways", "recentsLimit" to 500))
        val p = LauncherPrefsStore(sp).prefs.value
        assertEquals(HomeOrder.LeftToRight, p.homeOrder)
        assertEquals(HomeRecents.MAX_LIMIT, p.recentsLimit)
        assertEquals(HomeRecents.MIN_LIMIT, LauncherPrefsStore(MapSharedPreferences()).apply { update { it.copy(recentsLimit = 1) } }.prefs.value.recentsLimit)
    }

    @Test fun HOM_22_everyOldLandingValueMigratesToGlanceOnAndTheRetiredKeysAreDeletedOnTheNextSave() {
        for (old in listOf("Selected", "Recents", "AllApps", "Minimal", "Glance", "Hologram")) {
            val sp = MapSharedPreferences(mapOf("homeLanding" to old, "landingChosen" to true))
            val store = LauncherPrefsStore(sp)
            assertTrue("old landing $old", store.prefs.value.glanceEnabled)
            store.update { it.copy(recentsLimit = 12) }
            assertEquals(null, sp.getString("homeLanding", null)); assertFalse(sp.contains("landingChosen"))
            assertTrue(LauncherPrefsStore(sp).prefs.value.glanceEnabled)
        }
        // A stored new value always wins over the old landing.
        assertFalse(LauncherPrefsStore(MapSharedPreferences(mapOf("homeLanding" to "Recents", "glanceEnabled" to false))).prefs.value.glanceEnabled)
    }

    @Test fun HOM_22_thePagesSettingHasExactlyThreeValuesOffPaginatedContinuous() {
        assertEquals(listOf("Off", "Paginated", "Continuous"), HomePages.entries.map { it.label })
    }

    @Test fun HOM_22_theOldScrollOrPaginatedChoiceMigratesToThePagesSettingAndAnExistingHomeNeverLosesItsPages() {
        // chose (or had) Scroll -> Continuous; chose Paginated -> Paginated
        assertEquals(HomePages.Continuous, HomeModelMigration.pagesMode(null, "Continuous", null, false))
        assertEquals(HomePages.Continuous, HomeModelMigration.pagesMode(null, "Continuous", true, true))
        assertEquals(HomePages.Paginated, HomeModelMigration.pagesMode(null, "Paged", true, false))
        // nothing chosen: Off with no pages, Paginated when the home already has built pages
        assertEquals(HomePages.Off, HomeModelMigration.pagesMode(null, null, null, false))
        assertEquals(HomePages.Off, HomeModelMigration.pagesMode(null, "Paged", false, false))
        assertEquals(HomePages.Paginated, HomeModelMigration.pagesMode(null, "Paged", false, true))
        // a stored new value always wins
        assertEquals(HomePages.Off, HomeModelMigration.pagesMode("Off", "Continuous", true, true))
        assertEquals(HomePages.Continuous, HomeModelMigration.pagesMode("Continuous", null, null, false))
        assertEquals(HomePages.Paginated, HomeModelMigration.pagesMode("Nonsense", null, true, false))
        // what counts as built pages
        assertFalse(HomeModelMigration.hasBuiltPages(emptyList())); assertFalse(HomeModelMigration.hasBuiltPages(listOf(HomePage(id = 1L))))
        assertTrue(HomeModelMigration.hasBuiltPages(listOf(HomePage(id = 1L), HomePage(id = 2L)))); assertTrue(HomeModelMigration.hasBuiltPages(listOf(HomePage(keys = listOf("x"), id = 1L))))
    }

    @Test fun HOM_22_anOldInstallIsMigratedThroughTheStoreAndTheRetiredKeysAreGoneAfterTheNextSave() {
        // old Scroll
        val sp = MapSharedPreferences(mapOf("homeScroll" to "Continuous", "pagesModeChosen" to true))
        val store = LauncherPrefsStore(sp)
        assertEquals(HomePages.Continuous, store.prefs.value.homePages)
        store.migratePages(emptyList())
        assertEquals("Continuous", sp.getString("homePages", null)); assertFalse(sp.contains("homeScroll")); assertFalse(sp.contains("pagesModeChosen"))
        assertEquals(HomePages.Continuous, LauncherPrefsStore(sp).prefs.value.homePages)
        // never chosen, but the home has pages with apps: Paginated, nothing hidden
        val sp2 = MapSharedPreferences(mapOf("homeScroll" to "Paged"))
        val s2 = LauncherPrefsStore(sp2)
        s2.migratePages(listOf(HomePage(keys = listOf("a"), id = 1L)))
        assertEquals(HomePages.Paginated, s2.prefs.value.homePages)
        // never chosen and no pages: Off; an already migrated install is left alone
        val s3 = LauncherPrefsStore(MapSharedPreferences()); s3.migratePages(emptyList()); assertEquals(HomePages.Off, s3.prefs.value.homePages)
        val sp4 = MapSharedPreferences(mapOf("homePages" to "Off")); LauncherPrefsStore(sp4).migratePages(listOf(HomePage(keys = listOf("a"), id = 1L)))
        assertEquals("Off", sp4.getString("homePages", null))
    }

    @Test fun HOM_22_theHomeSettingsAreExportedAndAnOlderFileImportsAsTheDefaults() {
        val doc = SettingsCodec.encode(LauncherPrefs(glanceEnabled = false, homeOrder = HomeOrder.RightToLeft, homePages = HomePages.Paginated, recentsLimit = 40, recentsUseUsageAccess = false))
        val prefs = doc.getJSONObject("prefs")
        assertEquals("RightToLeft", prefs.getString("homeOrder")); assertFalse(prefs.getBoolean("glanceEnabled")); assertEquals("Paginated", prefs.getString("homePages"))
        val back = SettingsCodec.decode(doc).prefs
        assertFalse(back.glanceEnabled); assertEquals(HomeOrder.RightToLeft, back.homeOrder); assertEquals(HomePages.Paginated, back.homePages)
        assertEquals(40, back.recentsLimit); assertFalse(back.recentsUseUsageAccess)
        val old = SettingsCodec.encode(LauncherPrefs()); old.getJSONObject("prefs").remove("homeOrder"); old.getJSONObject("prefs").remove("glanceEnabled")
        assertEquals(HomeOrder.LeftToRight, SettingsCodec.decode(old).prefs.homeOrder); assertTrue(SettingsCodec.decode(old).prefs.glanceEnabled)
    }

    // ---- rules per surface

    @Test fun HOM_22_editModeBelongsToThePagesAndGlanceLongPressesToTheQuickSheet() {
        for (l in HomeShown.entries) {
            val pages = l == HomeShown.Pages
            assertEquals(pages, HomeShownRules.allowsEditMode(l))
            assertEquals(if (pages) HomeShownRules.LongPress.EditHome else HomeShownRules.LongPress.QuickSheet, HomeShownRules.longPress(l))
            assertEquals(pages, HomeShownRules.hasPages(l))
            assertEquals(pages, HomeShownRules.usesHomeStyle(l))
        }
        assertEquals(HomeShown.Pages, HomeShownRules.editHomePagesSurface)
        assertTrue(HomeShownRules.isHotspotOnly(HomeShown.Glance))
        assertTrue(HomeShownRules.showsEmptyHomeHint(HomeShown.Pages, true))
        assertFalse(HomeShownRules.showsEmptyHomeHint(HomeShown.Glance, true))
        assertFalse(HomeShownRules.showsEmptyHomeHint(HomeShown.Pages, false))
    }

    // ---- Minimal hotspots

    @Test fun HOM_22_INP_16_INP_17_minimalGivesAFactoryCornerItsHotspotDefaultAndKeepsTheUsersOwn() {
        val onFactory = MinimalHotspots.effective(DefaultCorners)
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.Search)), onFactory[Corner.TopLeft])
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.OpenLaunchpad)), onFactory[Corner.TopRight])
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.AllApps)), onFactory[Corner.BottomLeft])
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.LauncherSettings)), onFactory[Corner.BottomRight])
        assertEquals(4, onFactory.size)
        val mine = DefaultCorners + (Corner.TopRight to CornerSlot.Single(CornerItem.App("my.app#0"))) + (Corner.BottomLeft to CornerSlot.Empty)
        val eff = MinimalHotspots.effective(mine)
        assertEquals(CornerSlot.Single(CornerItem.App("my.app#0")), eff[Corner.TopRight])
        assertEquals(CornerSlot.Empty, eff[Corner.BottomLeft])
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.Search)), eff[Corner.TopLeft])
        // Nothing was written: the stored assignments are unchanged.
        assertEquals(CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)), mine[Corner.TopLeft])
    }

    @Test fun HOM_22_INP_16_everyMinimalDefaultIsAnInternalActionTheHomeRuns() {
        for ((_, s) in MinimalHotspots.defaults) assertTrue(((s as CornerSlot.Single).item as CornerItem.Action).id.internal)
        assertEquals(setOf(Corner.TopLeft, Corner.TopRight, Corner.BottomLeft, Corner.BottomRight), MinimalHotspots.defaults.keys)
    }

    @Test fun HOM_22_INP_07_theLandingCycleActionIsGoneAndOpenLaunchpadStaysInternal() {
        assertTrue(CornerActionId.OpenLaunchpad.internal)
        assertTrue(CornerActionId.entries.none { it.label == "Cycle home landing" })
    }

    // ---- recents rules

    @Test fun HOM_22_recentsAreNewestFirstOnePerAppAndBounded() {
        val own = listOf(ev(a.key, 10), ev(b.key, 20), ev(a.key, 30), ev(c.key, 5))
        assertEquals(listOf(a.key, b.key, c.key), HomeRecents.compute(own, emptyList(), all, emptySet()))
        val many = (1..60).map { app("x$it") }
        val ev60 = many.mapIndexed { i, x -> ev(x.key, i.toLong() + 1) }
        assertEquals(24, HomeRecents.compute(ev60, emptyList(), many, emptySet()).size)
        assertEquals(8, HomeRecents.compute(ev60, emptyList(), many, emptySet(), limit = 2).size)
        assertEquals(48, HomeRecents.compute(ev60, emptyList(), many, emptySet(), limit = 99).size)
        assertEquals(many.last().key, HomeRecents.compute(ev60, emptyList(), many, emptySet()).first())
    }

    @Test fun HOM_22_hiddenAndUninstalledAppsAndLiftoffItselfNeverAppear() {
        val own = listOf(ev(a.key, 10), ev(b.key, 20), ev("gone#0", 30), ev(c.key, 40))
        val r = HomeRecents.compute(own, listOf(UsageSighting("com.self", 50)), all + app("self", pkg = "com.self"), hidden = setOf(b.key), selfPackage = "com.self")
        assertEquals(listOf(c.key, a.key), r)
    }

    @Test fun HOM_22_workAppsAreKeptByDefaultAndCanBeFilteredByProfile() {
        val own = listOf(ev(a.key, 10), ev(w.key, 20))
        assertEquals(listOf(w.key, a.key), HomeRecents.compute(own, emptyList(), all, emptySet()))
        assertEquals(listOf(a.key), HomeRecents.compute(own, emptyList(), all, emptySet(), profile = RecentsProfile.PersonalOnly))
        assertEquals(listOf(w.key), HomeRecents.compute(own, emptyList(), all, emptySet(), profile = RecentsProfile.WorkOnly))
    }

    @Test fun HOM_22_usageEventsAddAppsOpenedElsewhereAndMergeByTime() {
        val own = listOf(ev(a.key, 10), ev(b.key, 20))
        val usage = listOf(UsageSighting(c.packageName, 15), UsageSighting(a.packageName, 30), UsageSighting("not.installed", 99))
        assertEquals(listOf(a.key, b.key, c.key), HomeRecents.compute(own, usage, all, emptySet()))
        // Without the grant there are no sightings: only Liftoff's own history remains.
        assertEquals(listOf(b.key, a.key), HomeRecents.compute(own, emptyList(), all, emptySet()))
    }

    @Test fun HOM_22_aSightingForAPackageInBothProfilesResolvesToTheOneLiftoffLaunchedElseThePersonalOne() {
        val p = RecentCandidate("m#0", "pkg.m", false); val wk = RecentCandidate("m#10", "pkg.m", true)
        assertEquals(listOf("m#10"), HomeRecents.compute(listOf(ev("m#10", 1)), listOf(UsageSighting("pkg.m", 5)), listOf(p, wk), emptySet()))
        assertEquals(listOf("m#0"), HomeRecents.compute(emptyList(), listOf(UsageSighting("pkg.m", 5)), listOf(wk, p), emptySet()))
    }

    @Test fun HOM_22_removeAndClearHideOnlyWhatWasLaunchedBeforeAndAReLaunchBringsTheAppBack() {
        val own = listOf(ev(a.key, 10), ev(b.key, 20), ev(c.key, 30))
        val removed = RecentsState(removedAt = mapOf(b.key to 25))
        assertEquals(listOf(c.key, a.key), HomeRecents.compute(own, emptyList(), all, emptySet(), removed))
        assertEquals(listOf(b.key, c.key, a.key), HomeRecents.compute(own + ev(b.key, 40), emptyList(), all, emptySet(), removed))
        val cleared = RecentsState(clearedAt = 30)
        assertEquals(emptyList<String>(), HomeRecents.compute(own, emptyList(), all, emptySet(), cleared))
        assertEquals(listOf(a.key), HomeRecents.compute(own + ev(a.key, 31), emptyList(), all, emptySet(), cleared))
        // Usage sightings are removed the same way.
        assertEquals(emptyList<String>(), HomeRecents.compute(emptyList(), listOf(UsageSighting(a.packageName, 9)), all, emptySet(), RecentsState(removedAt = mapOf(a.key to 9))))
    }

    @Test fun HOM_22_theRunningLiftoffAppMovesFirstOnlyWhenItIsAlreadyListed() {
        val own = listOf(ev(a.key, 10), ev(b.key, 20), ev(c.key, 30))
        assertEquals(listOf(a.key, c.key, b.key), HomeRecents.compute(own, emptyList(), all, emptySet(), runningFirst = a.key))
        assertEquals(listOf(c.key, b.key, a.key), HomeRecents.compute(own, emptyList(), all, emptySet(), runningFirst = w.key))
        assertEquals(listOf(c.key, b.key, a.key), HomeRecents.compute(own, emptyList(), all, emptySet(), runningFirst = null))
    }

    @Test fun HOM_22_theLiveListUpdatesOnANewLaunchAndAHideAndIsComputedOffTheCallersThread() {
        val own = MutableStateFlow(listOf(ev(a.key, 1)))
        val hidden = MutableStateFlow(emptySet<String>())
        var readThread: String? = null
        val f = HomeRecents.flow(
            own, flowOf(Unit), { readThread = Thread.currentThread().name; emptyList() }, flowOf(all), hidden,
            flowOf(RecentsState()), flowOf(24), flowOf(RecentsProfile.Both), flowOf(true),
        )
        runBlocking {
            assertEquals(listOf(a.key), f.first())
            own.value = own.value + ev(b.key, 2)
            assertEquals(listOf(b.key, a.key), f.first())
            hidden.value = setOf(b.key)
            assertEquals(listOf(a.key), f.first())
        }
        assertNotEquals("main", readThread)
        assertTrue("the usage log is read on a pool thread", readThread != null && readThread != Thread.currentThread().name)
    }

    @Test fun HOM_22_withUsageSwitchedOffTheUsageLogIsNeverRead() {
        var reads = 0
        val f = HomeRecents.flow(flowOf(listOf(ev(a.key, 1))), flowOf(Unit), { reads++; listOf(UsageSighting(b.packageName, 5)) }, flowOf(all), flowOf(emptySet()),
            flowOf(RecentsState()), flowOf(24), flowOf(RecentsProfile.Both), flowOf(false))
        assertEquals(listOf(a.key), runBlocking { f.first() })
        assertEquals(0, reads)
    }

    // ---- the private removals file

    @Test fun HOM_22_theRemovalsFileRoundTripsIsBoundedAndToleratesDamage() {
        val s = RecentsState(100, mapOf("a#0" to 150L, "b#0" to 120L))
        assertEquals(s, RecentsStateCodec.decode(RecentsStateCodec.encode(s)))
        assertEquals(RecentsState(), RecentsStateCodec.decode("garbage\nremoved\tx\nremoved\t5\t\n"))
        val many = RecentsState(0, (1..300).associate { "k$it#0" to it.toLong() })
        assertEquals(RecentsStateCodec.MAX_REMOVED, RecentsStateCodec.decode(RecentsStateCodec.encode(many)).removedAt.size)
        assertEquals(mapOf("a#0" to 150L), RecentsStateCodec.tidy(s).removedAt - "b#0")
        assertFalse("b#0" in RecentsStateCodec.tidy(RecentsState(130, mapOf("b#0" to 120L))).removedAt)
    }

    @Test fun HOM_22_removalsPersistThroughARestartAndAllFileAccessRunsOnTheExecutor() {
        val file = tmp.newFolder("files").resolve("usage/recents-state.txt")
        val io = QueueExecutor()
        val store = RecentsStateStore(file, io)
        assertFalse("nothing touched the disk before the executor ran", file.exists())
        io.drain()
        store.remove("a#0", now = 50)
        assertEquals(50L, store.state.value.removedAt["a#0"])
        assertFalse("the write is queued, not done on the caller", file.exists())
        io.drain()
        val again = RecentsStateStore(file, io); io.drain()
        assertEquals(mapOf("a#0" to 50L), again.state.value.removedAt)
        again.clear(now = 99); io.drain()
        val third = RecentsStateStore(file, io); io.drain()
        assertEquals(RecentsState(clearedAt = 99), third.state.value)
    }

    @Test fun HOM_22_aRemovalMadeBeforeTheFileIsReadIsNotLost() {
        val file = tmp.newFolder("f2").resolve("usage/recents-state.txt")
        file.parentFile!!.mkdirs(); file.writeText(RecentsStateCodec.encode(RecentsState(0, mapOf("old#0" to 5L))))
        val io = QueueExecutor()
        val store = RecentsStateStore(file, io)
        store.remove("new#0", now = 60)
        io.drain()
        assertEquals(setOf("old#0", "new#0"), store.state.value.removedAt.keys)
        assertEquals(setOf("old#0", "new#0"), RecentsStateCodec.decode(file.readText()).removedAt.keys)
    }

    @Test fun HOM_22_theRemovalsFileIsNeverExportedAndUnderADirectoryBackupExcludes() {
        assertTrue(RecentsStateStore.RELATIVE_PATH in StateSchema.NEVER_EXPORTED_FILES)
        assertTrue(StateSchema.BACKUP_EXCLUDED_FILES.any { RecentsStateStore.RELATIVE_PATH.startsWith("$it/") })
        assertTrue(TimelineLogStore.RELATIVE_PATH in StateSchema.NEVER_EXPORTED_FILES)
    }

    @Test fun HOM_22_aHotspotRunsASingleAssignmentOrThePaletteItsFirstItemAndNothingElse() {
        val a = CornerItem.Action(CornerActionId.Search); val b = CornerItem.Action(CornerActionId.AllApps)
        assertEquals(a, MinimalHotspots.itemOf(CornerSlot.Single(a)))
        assertEquals(a, MinimalHotspots.itemOf(CornerSlot.Palette("p", listOf(a, b))))
        assertEquals(null, MinimalHotspots.itemOf(CornerSlot.Palette("p", emptyList())))
        assertEquals(null, MinimalHotspots.itemOf(CornerSlot.Empty))
        assertEquals(null, MinimalHotspots.itemOf(CornerSlot.Widget(1, "w")))
    }
}
