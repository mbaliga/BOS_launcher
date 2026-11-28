package com.bos.sphere.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STR-14: the shared recents source with background events, the Right side setting, its destinations provider and "how long ago". */
class RecentsPanelTest {
    private fun app(n: String, work: Boolean = false, pkg: String = "pkg.$n") = RecentCandidate("$n#${if (work) 10 else 0}", pkg, work)
    private fun ev(key: String, t: Long) = LaunchEvent(key, t, 0)
    private val a = app("a"); private val b = app("b"); private val c = app("c")
    private val all = listOf(a, b, c)

    @Test fun STR_14_anAppThatWentToTheBackgroundLaterSortsAboveOneLaunchedAfterItsLaunch() {
        // a launched at 100, left (paused) at 900; b launched at 500 and still the older sighting: a is newer by when it went to the background.
        val e = HomeRecents.entries(listOf(ev(a.key, 100), ev(b.key, 500)), listOf(UsageSighting("pkg.a", 900, background = true)), all, emptySet())
        assertEquals(listOf(a.key, b.key), e.map { it.key })
        assertEquals(900L, e.first().timeMillis)
    }

    @Test fun STR_14_appsOpenedFromElsewhereAppearOrderedByTheirLastBackgroundEvent() {
        val usage = listOf(
            UsageSighting("pkg.b", 200), UsageSighting("pkg.b", 300, background = true),
            UsageSighting("pkg.c", 400), UsageSighting("pkg.c", 450, background = true),
            UsageSighting("pkg.a", 350, background = true),
        )
        assertEquals(listOf(c.key, a.key, b.key), HomeRecents.entries(emptyList(), usage, all, emptySet()).map { it.key })
    }

    @Test fun STR_14_oneRowPerAppEvenWithManyEventsAndBothSources() {
        val usage = listOf(UsageSighting("pkg.a", 10), UsageSighting("pkg.a", 20, true), UsageSighting("pkg.a", 30), UsageSighting("pkg.a", 40, true))
        val e = HomeRecents.entries(listOf(ev(a.key, 5), ev(a.key, 25)), usage, all, emptySet())
        assertEquals(1, e.size)
        assertEquals(40L, e.single().timeMillis)
    }

    @Test fun STR_14_hiddenSelfRemovedAndClearedAppsNeverAppearAndAnAppBackAfterRemovalComesBack() {
        val own = listOf(ev(a.key, 100), ev(b.key, 200), ev(c.key, 300))
        assertEquals(listOf(c.key, a.key), HomeRecents.entries(own, emptyList(), all, hidden = setOf(b.key)).map { it.key })
        val removed = RecentsState(removedAt = mapOf(c.key to 300L))
        assertEquals(listOf(b.key, a.key), HomeRecents.entries(own, emptyList(), all, emptySet(), removed).map { it.key })
        val later = own + ev(c.key, 400)
        assertEquals(c.key, HomeRecents.entries(later, emptyList(), all, emptySet(), removed).first().key)
        assertTrue(HomeRecents.entries(own, emptyList(), all, emptySet(), RecentsState(clearedAt = 300)).isEmpty())
        assertTrue(HomeRecents.entries(own, listOf(UsageSighting("me", 999, true)), all + app("me", pkg = "me"), emptySet(), selfPackage = "me").none { it.key.startsWith("me") })
    }

    @Test fun STR_14_theLandingAndThePanelShareOneSourceSameOrderSameLimit() {
        val own = (1..60).map { ev("k$it#0", it * 10L) }
        val apps = (1..60).map { RecentCandidate("k$it#0", "pkg.k$it", false) }
        val usage = listOf(UsageSighting("pkg.k3", 5000, background = true))
        for (limit in listOf(8, 24, 48, 200)) {
            val keys = HomeRecents.compute(own, usage, apps, emptySet(), limit = limit)
            assertEquals(keys, HomeRecents.entries(own, usage, apps, emptySet(), limit = limit).map { it.key })
            assertTrue(keys.size <= HomeRecents.clampLimit(limit))
        }
        assertEquals("k3#0", HomeRecents.compute(own, usage, apps, emptySet()).first())
    }

    @Test fun STR_14_theEntriesFlowIsComputedFromTheSameInputsAndReadsUsageOnlyWhenOn() = runBlocking {
        var reads = 0
        val flowOn = HomeRecents.entriesFlow(
            flowOf(listOf(ev(a.key, 100))), flowOf(Unit), { reads++; listOf(UsageSighting("pkg.b", 500, background = true)) }, flowOf(all), flowOf(emptySet()),
            flowOf(RecentsState()), flowOf(24), flowOf(RecentsProfile.Both), flowOf(true),
        ).first()
        assertEquals(listOf(b.key, a.key), flowOn.map { it.key }); assertEquals(1, reads)
        val flowOff = HomeRecents.entriesFlow(
            flowOf(listOf(ev(a.key, 100))), flowOf(Unit), { reads++; emptyList() }, flowOf(all), flowOf(emptySet()),
            MutableStateFlow(RecentsState()), flowOf(24), flowOf(RecentsProfile.Both), flowOf(false),
        ).first()
        assertEquals(listOf(a.key), flowOff.map { it.key }); assertEquals("usage not read when off", 1, reads)
    }

    @Test fun STR_14_rightSideDefaultsToTheRecentsPanelAndParsesNamesSafely() {
        assertEquals(RightSide.RecentsPanel, LauncherPrefs().rightSide)
        assertEquals(RightSide.DockRail, RightSide.parse("DockRail"))
        assertEquals(RightSide.RecentsPanel, RightSide.parse(null))
        assertEquals(RightSide.RecentsPanel, RightSide.parse("FromTheFuture"))
        assertEquals(RightSide.Off, RightSide.parse("Off"))
    }

    @Test fun STR_14_theRightSideSettingIsAPlainPrefAndRoundTrips() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.copy(rightSide = RightSide.Off) }
        assertEquals("Off", sp.getString("rightSide", null))
        assertEquals(RightSide.Off, LauncherPrefsStore(sp).prefs.value.rightSide)
    }

    @Test fun STR_14_destinationsProviderTellsTheGlanceIndicatorWhatTheRightSideOffers() {
        assertEquals(listOf(RightDestination.Recents), RightSideDestinations.of(RightSide.RecentsPanel))
        assertEquals(listOf(RightDestination.Dock), RightSideDestinations.of(RightSide.DockRail))
        assertTrue(RightSideDestinations.of(RightSide.Off).isEmpty())
        assertTrue(RightSideDestinations.opensRecentsPanel(RightSide.RecentsPanel))
        assertFalse(RightSideDestinations.opensRecentsPanel(RightSide.DockRail))
        assertTrue(RightSideDestinations.edgeRevealsDock(RightSide.DockRail))
        assertFalse(RightSideDestinations.edgeRevealsDock(RightSide.RecentsPanel))
    }

    @Test fun STR_14_howLongAgoIsBucketedAndNeverNegative() {
        val now = 10_000_000_000L
        assertEquals(AgoBucket.JustNow, RecentsAgo.bucket(now, now - 30_000))
        assertEquals(AgoBucket.JustNow, RecentsAgo.bucket(now, now + 5_000))
        assertEquals(AgoBucket.Minutes(5), RecentsAgo.bucket(now, now - 5 * 60_000))
        assertEquals(AgoBucket.Hours(2), RecentsAgo.bucket(now, now - 125 * 60_000))
        assertEquals(AgoBucket.Days(3), RecentsAgo.bucket(now, now - 3L * 24 * 60 * 60_000 - 1))
    }

    @Test fun STR_14_openRecentsIsAnAssignableCornerActionAndACommand() {
        assertTrue(CornerActionId.OpenRecents.internal)
        assertEquals("Open recents", CornerActionId.OpenRecents.label)
        assertEquals("OpenRecents", CornerActionId.valueOf("OpenRecents").name)
    }
}
