package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/** W33 (commsDebt): the Comms tab and filter survive the activity being recreated; the merge runs off the main thread for a large input and inline for a small one. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class CommsDebtW33Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { CommsMergeConfig.reset() }

    private fun live(i: Int) = HubNotification(id = "l$i", key = "live$i", source = "x", title = "Live $i", summary = "Body $i", timestamp = 1_000L + i, packageName = "p", appLabel = "App")
    private fun hist(i: Int) = HistoryEntry("h$i", "hk$i", "p", "App", "", "Old $i", "Text $i", "", 100L + i, read = true)

    @Test fun CMS_W33_01_theTabAndTheReadFilterComeBackAfterTheActivityIsRecreated() {
        val tester = StateRestorationTester(rule)
        var hub: HubState? = null
        tester.setContent {
            val h = androidx.compose.runtime.saveable.rememberSaveable(saver = hubStateSaver { HubState(HubPrefs(null), null, true, liveAll = true) }) { HubState(HubPrefs(null), null, true, liveAll = true) }
            hub = h
            Text(h.view.id + "/" + h.readFilter.name, Modifier_tag())
        }
        rule.runOnIdle { hub!!.select(HubView.Unread); hub!!.chooseReadFilter(ReadFilter.Read) }
        rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(HubView.Unread, hub!!.view); assertEquals(ReadFilter.Read, hub!!.readFilter) }
    }

    @Test fun CMS_W33_02_aSavedTabThatNoLongerExistsFallsBackToTheDefaultView() {
        val h = HubState(HubPrefs(null), null, panels = false)
        h.restoreState(listOf(HubView.Files.id, "Bogus"))
        assertEquals("Files needs the panels, so the view stays", HubView.All, h.view); assertEquals(ReadFilter.All, h.readFilter)
    }

    @Test fun CMS_W33_03_aSmallInputIsMergedInTheCompositionSoTheResultIsThereAtOnce() {
        val items = (0 until 3).map(::live); val hist = (0 until 3).map(::hist)
        var m: CommsRead.Merged? = null
        rule.setContent { m = rememberCommsMerged(items, hist, emptySet(), enabled = true) }
        rule.waitForIdle()
        assertEquals(6, m!!.items.size)
    }

    private class Queue : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks += block }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun CMS_W33_04_aLargeInputKeepsTheLiveListUntilTheBackgroundMergeFinishes() {
        val q = Queue(); CommsMergeConfig.dispatcher = q; CommsMergeConfig.instantLimit = 4
        val items = (0 until 3).map(::live); val hist = (0 until 8).map(::hist)
        var m: CommsRead.Merged? = null
        rule.setContent { m = rememberCommsMerged(items, hist, emptySet(), enabled = true) }
        rule.waitForIdle()
        assertEquals("before the merge is done only the live rows show", 3, m!!.items.size)
        assertTrue("the merge was handed to the background dispatcher", q.tasks.isNotEmpty())
        q.drain(); rule.waitForIdle()
        assertEquals(11, m!!.items.size)
    }

    @Test fun CMS_W33_05_aNewInputKeepsThePreviousMergedResultWhileTheNextOneIsComputed() {
        val q = Queue(); CommsMergeConfig.dispatcher = q; CommsMergeConfig.instantLimit = 4
        val hist = (0 until 8).map(::hist)
        var n by mutableIntStateOf(2)
        var m: CommsRead.Merged? = null
        rule.setContent { m = rememberCommsMerged((0 until n).map(::live), hist, emptySet(), enabled = true) }
        rule.waitForIdle(); q.drain(); rule.waitForIdle()
        val first = m!!; assertEquals(10, first.items.size)
        rule.runOnIdle { n = 3 }; rule.waitForIdle()
        assertSame("the previous result stays while the new one is computed", first, m)
        q.drain(); rule.waitForIdle()
        assertEquals(11, m!!.items.size)
    }

    @Test fun CMS_W33_06_withTheHubOffTheListIsTheLiveFeedAndNothingIsMerged() {
        val items = (0 until 3).map(::live)
        var m: CommsRead.Merged? = null
        rule.setContent { m = rememberCommsMerged(items, (0 until 5).map(::hist), emptySet(), enabled = false) }
        rule.waitForIdle()
        assertEquals(3, m!!.items.size)
    }

    @Test fun CMS_W33_07_theTestSeamDefaultsToABackgroundDispatcher() {
        CommsMergeConfig.reset()
        assertSame(Dispatchers.Default, CommsMergeConfig.dispatcher)
    }

    private fun Modifier_tag() = androidx.compose.ui.Modifier.testTag("w33.state")
}
