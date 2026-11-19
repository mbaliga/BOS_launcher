package com.bos.sphere

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.ui.HubPanel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W33 (commsDebt): the pull-down notification shade (HubPanel) rows carry the Comms list's read/unread styling. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class HubPanelReadStyleW33Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun n(key: String) = HubNotification(id = key, key = key, source = "Chat", title = "Title $key", summary = "Body $key", timestamp = 1L, packageName = "p", appLabel = "Chat")
    @After fun tearDown() { NotificationProvider.publishForTest(emptyList()); LaunchpadSettings.unbindForTest() }

    private fun show() {
        android.provider.Settings.Secure.putString(rule.activity.contentResolver, "enabled_notification_listeners", "${rule.activity.packageName}/com.bos.sphere.core.data.SphereNotificationListener")
        LaunchpadSettings.init(rule.activity)
        NotificationProvider.publishForTest(listOf(n("a"), n("b")))
        rule.setContent { SphereTheme { HubPanel(fraction = 1f) } }
        rule.waitForIdle()
    }
    private fun has(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun textColorWeight(tag: String): Pair<Long, Int?> {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val sem = node.config.getOrNull(SemanticsProperties.Text)
        assertTrue(sem != null)
        val getter = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)!!.action!!
        val out = ArrayList<androidx.compose.ui.text.TextLayoutResult>(); getter(out)
        val st = out.first().layoutInput.style
        return st.color.value.toLong() to st.fontWeight?.weight
    }

    @Test fun HOM_W33_10_anUnreadShadeRowHasTheDotAndTheSemiBoldHeadlineAReadRowHasNeither() {
        show()
        LaunchpadSettings.markRead("b"); rule.waitForIdle(); rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
        assertTrue("unread row a has the dot", has("hub.shade.unreadDot.a"))
        assertTrue("read row b has none", !has("hub.shade.unreadDot.b"))
        val (ca, wa) = textColorWeight("hub.shade.title.a"); val (cb, wb) = textColorWeight("hub.shade.title.b")
        assertEquals(600, wa); assertEquals(400, wb)
        assertNotEquals("the read headline is quieter", ca, cb)
        assertNotEquals(textColorWeight("hub.shade.summary.a").first, textColorWeight("hub.shade.summary.b").first)
    }

    @Test fun HOM_W33_11_theRowsSayUnreadOrReadToTalkBack() {
        show(); LaunchpadSettings.markRead("b"); rule.waitForIdle()
        val states = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes().map { it.config[SemanticsProperties.StateDescription] }
        assertEquals(listOf("Unread", "Read"), states)
    }
}
