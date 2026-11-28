package com.bos.sphere

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LaunchEvent
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

/**
 * Wave 28 (usageTimeline): the real Timeline body of the hub with the usage block in it: the block is the first thing in the list, above the first
 * timeline row; without usage access it offers the grant action and the list still works; the Usage view itself no longer exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class UsageTimelineW28Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { UsageWeekHost.setForTest(null) }

    private val app = AppEntry("demo.chat", ComponentName("demo.chat", "demo.chat.Main"), "Chat", Process.myUserHandle(), iconLoader = { null })
    private val history = listOf(LaunchEvent(app.key, System.currentTimeMillis() - 60_000, 9))

    private class Src(val ok: Boolean) : UsageWeekSource {
        override fun granted() = ok
        override fun samples(fromMs: Long, toMs: Long) = listOf(AppTime("demo.chat", 45 * 60_000L), AppTime("demo.maps", 20 * 60_000L))
    }

    private fun show() = rule.setContent { HomePanelsBody(HubView.Timeline, history, mapOf(app.key to app)) }

    @Test fun USG_30_theUsageBlockIsTheFirstItemOfTheTimelineAboveItsFirstRow() {
        UsageWeekHost.setForTest(UsageWeekRepo(Src(true))); show()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.stat").fetchSemanticsNodes().isNotEmpty() }
        val block = rule.onNodeWithTag("usage.block").getUnclippedBoundsInRoot()
        val row = rule.onNodeWithTag("timeline.row.0").getUnclippedBoundsInRoot()
        assertTrue("block ends at ${block.bottom}, first row starts at ${row.top}", block.bottom.value <= row.top.value)
        assertEquals("the block is in the same column as the list", rule.onNodeWithTag("timeline.list").getUnclippedBoundsInRoot().left.value, block.left.value, 0.5f)
        assertEquals(7, (0..6).count { rule.onAllNodesWithTag("usage.steps.col.$it").fetchSemanticsNodes().isNotEmpty() })
    }

    @Test fun USG_31_withoutUsageAccessTheTimelineShowsTheAllowActionAndStillListsItsRows() {
        UsageWeekHost.setForTest(UsageWeekRepo(Src(false))); show()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.noAccess").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("usage.allow").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("usage.steps.col.0").fetchSemanticsNodes().size)
        rule.onNodeWithTag("timeline.row.0").assertIsDisplayed()
    }

    @Test fun USG_32_theOldUsagePanelBodyIsGone() {
        val src = java.io.File("src/main/kotlin/com/bos/sphere/HomePanels.kt").readText()
        assertTrue("no UsagePanel composable", "fun UsagePanel" !in src && "sliceColor" !in src)
    }
}
