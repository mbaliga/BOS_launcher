package com.bos.sphere.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wave 28 (usageTimeline): the Usage view is gone; everything that named it lands on Timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageViewMigrationTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun USG_08_theHubViewsAreAllUnreadReadFilesTimeline() {
        assertEquals(listOf("all", "unread", "read", "files", "timeline"), HubView.entries.map { it.id })
        assertEquals(listOf(HubView.All, HubView.Unread, HubView.Files, HubView.Timeline), HubState(HubPrefs(null), null, true).tabs)
    }

    @Test fun USG_08_aStoredDefaultViewOfUsageBecomesTimelineAndIsRewritten() {
        val sp = ctx.getSharedPreferences("launchpad_hub_w28", 0).also { it.edit().putString("defaultView", "usage").commit() }
        val prefs = HubPrefs(sp)
        assertEquals(HubView.Timeline, prefs.defaultView)
        assertEquals("timeline", sp.getString("defaultView", null))
        assertEquals(HubView.Timeline, HubState(prefs, null, true).view) // the hub opens there
    }

    @Test fun USG_08_aHomeLinkOrQuickSheetOrSearchEntryForUsageOpensTimeline() {
        assertEquals(HubView.Timeline, HubView.forPanel("usage"))
        assertEquals(HubView.Timeline, HubView.fromId("usage"))
        assertEquals(HubView.Files, HubView.forPanel("files"))
    }

    @Test fun USG_08_twoFingerSwipesSkipTheRemovedViewAndStopAtTheEnds() {
        val s = HubState(HubPrefs(null), null, true)
        s.select(HubView.Files)
        assertEquals(HubView.Timeline, s.step(+1)); s.select(HubView.Timeline); assertEquals(null, s.step(+1))
        assertEquals(HubView.Files, s.step(-1))
    }
}
