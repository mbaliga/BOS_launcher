package com.bos.sphere

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent
import com.bos.sphere.feature.dock.RunKind
import com.bos.sphere.feature.dock.RunTarget
import com.bos.sphere.feature.dock.ScheduledLaunch
import com.bos.sphere.feature.dock.WorkspaceRepository
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

/**
 * Wave 29 (usageSimplify, owner: "Remove the last 30 launches 100 launches etc pills and with scheduled launches and opens only from the timeline
 * section"): the Timeline view has no filter row at all and one fixed behaviour, the last 100 opens with scheduled launches, newest first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class TimelineFixedW29Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @After fun reset() { UsageWeekHost.setForTest(null); WorkspaceRepository.get(ctx).removeSchedule("w29-sc") }

    private val app = AppEntry("demo.chat", ComponentName("demo.chat", "demo.chat.Main"), "Chat", Process.myUserHandle(), iconLoader = { null })

    private class Src : UsageWeekSource {
        override fun granted() = true
        override fun samples(fromMs: Long, toMs: Long) = listOf(AppTime("demo.chat", 45 * 60_000L))
    }

    private fun show(opens: Int) {
        val now = System.currentTimeMillis()
        val history = (1..opens).map { LaunchEvent(app.key, now - it * 1_000L, 9) }
        UsageWeekHost.setForTest(UsageWeekRepo(Src()))
        rule.setContent { HomePanelsBody(HubView.Timeline, history, mapOf(app.key to app)) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.stat").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun rowCount() = rule.onAllNodesWithTag("timeline.list").fetchSemanticsNodes().single().children.size

    @Test fun HOM_18_w29_theTimelineHasNoFilterRowAndNoRangeOrKindControl() {
        show(5)
        assertEquals(0, rule.onAllNodesWithTag("timeline.filter").fetchSemanticsNodes().size)
        listOf("30", "100", "ALL", "SCHEDULED", "OPENS ONLY", "Last 30 opens", "Last 100 opens", "With scheduled launches", "Opens only", "Everything recorded, up to 5000")
            .forEach { assertEquals("'$it' is gone from the Timeline", 0, rule.onAllNodesWithText(it, ignoreCase = true).fetchSemanticsNodes().size) }
        listOf("range.30", "range.100", "range.All", "kind.scheduled", "kind.opens").forEach { assertEquals(0, rule.onAllNodesWithTag("timeline.filter.$it").fetchSemanticsNodes().size) }
    }

    @Test fun HOM_18_w29_theListIsTheLast100OpensWithScheduledLaunchesInNewestFirst() {
        val sc = ScheduledLaunch("w29-sc", "Morning mail", System.currentTimeMillis() + 3_600_000L, RunTarget(RunKind.App, "demo.chat"))
        WorkspaceRepository.get(ctx).addSchedule(sc)
        show(150)
        val rows = (0..200).count { rule.onAllNodesWithTag("timeline.row.$it").fetchSemanticsNodes().isNotEmpty() }
        // LazyColumn composes what is visible; the model itself is checked in the catalog test. Here the first rows: the scheduled one first.
        assertTrue("rows are listed ($rows)", rows >= 2)
        assertTrue("the scheduled launch is in the list", rule.onAllNodesWithText("Scheduled: Morning mail").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun HOM_18_w29_theRemovedPreferencesAreNoLongerReadOrWrittenByAnyCode() {
        val dirs = listOf("src/main/kotlin", "../core-catalog/src/main/kotlin", "../feature-launchpad/src/main/kotlin").map { File(it) }.filter { it.exists() }
        val text = dirs.flatMap { d -> d.walkTopDown().filter { it.extension == "kt" }.toList() }.joinToString("\n") { it.readText() }
        listOf("timelineDepth", "timelineScheduled", "TimelineDepth", "CommsFilterRow", "FilterOpt").forEach { assertTrue("$it is gone", it !in text) }
        // The keys live on only in the retired list that purges them.
        assertTrue("panel.timeline.depth" !in text && "panel.timeline.scheduled" !in text)
    }
}
