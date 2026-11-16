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

    // ---- the setting

    @Test fun HOM_22_aFreshInstallKeepsSelectedAppsAndTheLandingIsPersistedByName() {
        assertEquals(HomeLanding.Selected, LauncherPrefs().homeLanding)
        val sp = MapSharedPreferences()
        val store = LauncherPrefsStore(sp)
        store.update { it.copy(homeLanding = HomeLanding.Minimal) }
        assertEquals("Minimal", sp.getString("homeLanding", null))
        assertEquals(HomeLanding.Minimal, LauncherPrefsStore(sp).prefs.value.homeLanding)
    }

    @Test fun HOM_22_anUnknownStoredLandingFallsBackToSelectedAndTheLimitIsClamped() {
        val sp = MapSharedPreferences(mapOf("homeLanding" to "Hologram", "recentsLimit" to 500))
        val p = LauncherPrefsStore(sp).prefs.value
        assertEquals(HomeLanding.Selected, p.homeLanding)
        assertEquals(HomeRecents.MAX_LIMIT, p.recentsLimit)
        assertEquals(HomeRecents.MIN_LIMIT, LauncherPrefsStore(MapSharedPreferences()).apply { update { it.copy(recentsLimit = 1) } }.prefs.value.recentsLimit)
        assertEquals(HomeLanding.Selected, HomeLanding.parse(null))
    }

    @Test fun HOM_22_cyclingVisitsTheFourLandingsInOrderAndWraps() {
        var l = HomeLanding.Selected
        val seen = List(5) { l.also { l = l.next() } }
        assertEquals(listOf(HomeLanding.Selected, HomeLanding.Recents, HomeLanding.AllApps, HomeLanding.Minimal, HomeLanding.Selected), seen)
    }

    @Test fun HOM_22_switchingTheLandingChangesNothingElseInThePrefs() {
        val before = LauncherPrefs(homeStyle = HomeStyle.Psp, homeScroll = HomeScroll.Continuous, hotCornersOn = false)
        for (l in HomeLanding.entries) {
            val after = before.copy(homeLanding = l)
            assertEquals(before.homeStyle, after.homeStyle); assertEquals(before.homeScroll, after.homeScroll); assertEquals(before.hotCornersOn, after.hotCornersOn)
        }
    }

    @Test fun HOM_22_theLandingIsAnExportedPlainPrefAndRoundTripsThroughTheSettingsDocument() {
        val doc = SettingsCodec.encode(LauncherPrefs(homeLanding = HomeLanding.Recents, recentsLimit = 40, recentsUseUsageAccess = false, minimalHints = false))
        val prefs = doc.getJSONObject("prefs")
        assertEquals("Recents", prefs.getString("homeLanding"))
        val back = SettingsCodec.decode(doc).prefs
        assertEquals(HomeLanding.Recents, back.homeLanding)
        assertEquals(40, back.recentsLimit)
        assertFalse(back.recentsUseUsageAccess)
        assertFalse(back.minimalHints)
    }

    @Test fun HOM_22_anOlderSettingsFileWithoutTheLandingImportsAsSelectedApps() {
        val doc = SettingsCodec.encode(LauncherPrefs())
        doc.getJSONObject("prefs").remove("homeLanding")
        assertEquals(HomeLanding.Selected, SettingsCodec.decode(doc).prefs.homeLanding)
    }

    // ---- rules per landing

    @Test fun HOM_22_editModeBelongsToSelectedAppsAndOtherLandingsLongPressToTheQuickSheet() {
        for (l in HomeLanding.entries) {
            val sel = l == HomeLanding.Selected
            assertEquals(sel, HomeLandingRules.allowsEditMode(l))
            assertEquals(if (sel) HomeLandingRules.LongPress.EditHome else HomeLandingRules.LongPress.QuickSheet, HomeLandingRules.longPress(l))
            assertEquals(sel, HomeLandingRules.hasPages(l))
        }
        assertEquals(HomeLanding.Selected, HomeLandingRules.editHomePagesLanding)
    }

    @Test fun HOM_22_minimalDrawsNoChromeAndOnlyAllAppsHasTheIndexAndFilter() {
        assertFalse(HomeLandingRules.showsChrome(HomeLanding.Minimal))
        assertFalse(HomeLandingRules.usesHomeStyle(HomeLanding.Minimal))
        assertTrue(HomeLandingRules.isHotspotOnly(HomeLanding.Minimal))
        for (l in listOf(HomeLanding.Selected, HomeLanding.Recents, HomeLanding.AllApps)) { assertTrue(HomeLandingRules.showsChrome(l)); assertTrue(HomeLandingRules.usesHomeStyle(l)) }
        assertEquals(listOf(HomeLanding.AllApps), HomeLanding.entries.filter { HomeLandingRules.hasAlphabetIndex(it) && HomeLandingRules.hasTypeToFilter(it) && HomeLandingRules.hasProfileTabs(it) })
        assertEquals(listOf(HomeLanding.Recents), HomeLanding.entries.filter { HomeLandingRules.hasRecentsMenu(it) })
        assertTrue(HomeLandingRules.showsEmptyHomeHint(HomeLanding.Selected, true))
        assertFalse(HomeLandingRules.showsEmptyHomeHint(HomeLanding.Recents, true))
        assertFalse(HomeLandingRules.showsEmptyHomeHint(HomeLanding.Selected, false))
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

    @Test fun HOM_22_theDotHintShowsOnlyOnMinimalAndOnlyWhenOn() {
        assertTrue(MinimalHotspots.hintsVisible(HomeLanding.Minimal, true))
        assertFalse(MinimalHotspots.hintsVisible(HomeLanding.Minimal, false))
        assertFalse(MinimalHotspots.hintsVisible(HomeLanding.Selected, true))
    }

    @Test fun HOM_22_INP_07_theCycleActionIsAnInternalCornerActionSoItCanBeAssignedToACornerOrGesture() {
        assertTrue(CornerActionId.CycleLanding.internal)
        assertTrue(CornerActionId.OpenLaunchpad.internal)
        assertEquals("Cycle home landing", CornerActionId.CycleLanding.label)
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
}
