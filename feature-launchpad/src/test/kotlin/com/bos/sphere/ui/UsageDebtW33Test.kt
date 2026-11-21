package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W33 (commsDebt): the usage rows are 48 dp touch targets with compact ink, and a day with no recorded time says so. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class UsageDebtW33Test : LaunchpadShots() {
    @After fun reset() { L10n.setLanguage("en") }

    private fun host() {
        rule.setContent {
            Themed(true) {
                Column(Modifier.fillMaxSize().background(LocalLp.current.bg).padding(horizontal = 20.dp)) {
                    UsageTimelineBlock(UsageFixtures.repo(), UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                }
            }
        }
        rule.waitUntil(10_000) { has("usage.stat") }
    }
    private fun has(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun h(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }

    @Test fun USG_W33_01_everyTappableRowIsAtLeast48dpHighWhileTheIconAndBarStayCompact() {
        host()
        (0..4).forEach { assertTrue("row $it is ${h("usage.row.$it")} dp", h("usage.row.$it") >= 47.5f) }
        val icon = rule.onNodeWithTag("usage.row.icon.0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the ink did not grow", 20f, icon.bottom.value - icon.top.value, 0.5f)
        val block = h("usage.block")
        assertTrue("the block is $block dp high, at most 380", block <= 380f)
    }

    @Test fun USG_W33_02_atCommsSize125TheRowsAreStill48dpOfRealScreen() {
        CommsScaleSettings.set(125)
        rule.setContent {
            Themed(true) {
                CommsScaleHost(125) {
                    Column(Modifier.fillMaxSize().background(LocalLp.current.bg).padding(horizontal = 20.dp)) {
                        UsageTimelineBlock(UsageFixtures.repo(), UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                    }
                }
            }
        }
        rule.waitUntil(10_000) { has("usage.stat") }
        val realDp = (0..4).map { h("usage.row.$it") * 1.25f }
        realDp.forEachIndexed { i, v -> assertTrue("row $i is $v real dp", v >= 47.5f) }
    }

    @Test fun USG_W33_03_aSelectedDayWithNoRecordedTimeShowsAQuietMessageInsteadOfNothing() {
        host()
        rule.onNodeWithTag("usage.steps.col.3").performClick(); rule.waitForIdle()
        assertTrue("the message shows", has("usage.rows.none"))
        assertEquals("No recorded time", rule.onNodeWithTag("usage.rows.none").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
        assertFalse("no ranked row", has("usage.row.0"))
        assertTrue("the message is a 48 dp line", h("usage.rows.none") >= 47.5f)
        rule.onNodeWithTag("usage.steps.col.6").performClick(); rule.waitForIdle()
        assertFalse("a day with time has no message", has("usage.rows.none"))
        assertTrue(has("usage.row.0"))
    }

    @Test fun USG_W33_04_theMessageIsTranslated() {
        L10n.setLanguage("de"); host()
        rule.onNodeWithTag("usage.steps.col.3").performClick(); rule.waitForIdle()
        assertEquals("Keine erfasste Zeit", rule.onNodeWithTag("usage.rows.none").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
    }
}
