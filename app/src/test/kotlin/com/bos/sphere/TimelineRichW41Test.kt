package com.bos.sphere

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.NotificationHistory
import com.bos.sphere.core.data.NotificationHistoryStore
import com.bos.sphere.core.data.StoredTimelineEvent
import com.bos.sphere.core.data.TimelineEventStore
import com.bos.sphere.core.data.TimelineEvents
import com.bos.sphere.core.data.TimelineKind
import com.bos.sphere.ui.AppTime
import com.bos.sphere.ui.HubView
import com.bos.sphere.ui.UsageWeekRepo
import com.bos.sphere.ui.UsageWeekSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor

/** Wave 41 (HOM-18): the Comms Timeline reads the real stores: notification history (with its category), the event store, and a tap marks read and opens the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1200dp-xhdpi")
class TimelineRichW41Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val direct = Executor { it.run() }
    private val app = AppEntry("demo.chat", ComponentName("demo.chat", "demo.chat.Main"), "Chat", Process.myUserHandle(), iconLoader = { null })

    private class Src : UsageWeekSource { override fun granted() = true; override fun samples(fromMs: Long, toMs: Long) = listOf(AppTime("demo.chat", 60_000L)) }

    @After fun reset() { UsageWeekHost.setForTest(null); NotificationHistory.setForTest(null); TimelineEvents.setForTest(null) }

    @Test fun HOM_18_w41_theTimelineShowsRecordedNotificationsAndStoredEventsWithTheirIconsAndATapOpensTheAppAndMarksRead() {
        val dir = File(ctx.cacheDir, "w41-" + System.nanoTime()).apply { mkdirs() }
        val hist = NotificationHistoryStore(File(dir, "h.log"), direct)
        NotificationHistory.setForTest(hist)
        val events = TimelineEventStore(File(dir, "e.log"), direct)
        TimelineEvents.setForTest(events)
        val now = System.currentTimeMillis()
        hist.observe(listOf(HubNotification(id = "1", key = "k1", source = "Chat", title = "Mum", summary = "Dinner at 7?", timestamp = now - 60_000, packageName = "demo.chat", appLabel = "Chat", category = "msg", sender = "Mum")))
        events.record(StoredTimelineEvent(now - 120_000, TimelineKind.AppHidden, "demo.chat", "Chat"))
        UsageWeekHost.setForTest(UsageWeekRepo(Src()))
        val launched = ArrayList<String>()
        rule.setContent { HomePanelsBody(HubView.Timeline, emptyList(), mapOf(app.key to app), onHide = {}) { launched += it.packageName } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("timeline.row.0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the notification is a Message row with its own tile", rule.onAllNodesWithTag("timeline.icon.Message.0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the unread dot agrees with the history", rule.onAllNodesWithTag("timeline.dot.0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the hide the user did is on the timeline", rule.onAllNodesWithText("Hid Chat", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithTag("timeline.icon.AppHidden.1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("timeline.row.0", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(listOf("demo.chat"), launched)
        assertTrue("the tap marked the history entry read", hist.entries.value.single().read)
    }
}
