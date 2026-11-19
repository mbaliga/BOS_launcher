package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.GrantHooks
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.L10n
import com.bos.sphere.ui.viz.VizTexture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 28 (usageTimeline): the usage block above the Timeline: where it sits, what a tap on a column does, the missing-access and empty states,
 * the table view, the accessible names, and screenshots (`build/screenshots/usage-*.png`, dark and light, granted, access missing, empty).
 * Wave 29 (usageSimplify): the stack and legend became a ranked list of rows; the tests below follow that (the block's layout and overlap rules are
 * in [UsageSimplifyW29Cases]).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class UsageBlockCases(private val orientation: String) : LaunchpadShots() {
    @After fun reset() { GrantHooks.interceptor = null; L10n.setLanguage("en") }

    private fun host(repo: UsageWeekRepo, dark: Boolean = true) {
        rule.setContent {
            Themed(dark) {
                Column(Modifier.fillMaxSize().background(LocalLp.current.bg).padding(horizontal = 20.dp)) {
                    UsageTimelineBlock(repo, UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                    Text("first timeline row", color = LocalLp.current.ink, modifier = Modifier.testTag("timeline.row.0"))
                }
            }
        }
    }

    private fun waitFor(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun count(prefix: String) = (0..9).count { rule.onAllNodesWithTag("$prefix.$it").fetchSemanticsNodes().isNotEmpty() }
    private fun desc(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
    private fun granted() = host(UsageFixtures.repo()).also { waitFor("usage.stat") }

    private fun capture(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "usage-$name-$orientation.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    // ---- placement and size ----

    @Test fun USG_10_theBlockIsAboveTheFirstTimelineRowAndAboutAsHighAsTheOwnerAsked() {
        granted()
        val block = rule.onNodeWithTag("usage.block").getUnclippedBoundsInRoot(); val row = rule.onNodeWithTag("timeline.row.0").getUnclippedBoundsInRoot()
        assertTrue("block ${block.bottom} above the first row ${row.top}", block.bottom.value <= row.top.value + 0.5f)
        val h = block.bottom.value - block.top.value
        assertTrue("the block is $h dp high (wave 29: the day line, the week, five 48 dp rows (W33) and a hairline)", h in 250f..380f)
        assertEquals("same horizontal margin as the list", 20f, block.left.value, 0.5f)
        assertEquals("a quiet header line with the total", "Today, 2 h 34 min", desc("usage.stat"))
    }

    // ---- the chart ----

    @Test fun USG_11_tappingAColumnSelectsItsDayAndTheRowsFollow() {
        granted()
        assertEquals(5, count("usage.row"))
        assertEquals("Browser, 1 h 10 min", desc("usage.row.0")); assertEquals("Other, 9 min", desc("usage.row.4"))
        rule.onNodeWithTag("usage.steps.col.4").performClick(); rule.waitForIdle()
        assertEquals("Fri, 1 h 50 min", desc("usage.stat"))
        assertEquals("two apps that day, nothing folded", 2, count("usage.row"))
        assertEquals("Browser, 20 min", desc("usage.row.1"))
        rule.onNodeWithTag("usage.steps.col.5").performClick(); rule.waitForIdle()
        assertEquals("Yesterday, 2 h 5 min", desc("usage.stat"))
    }

    @Test fun USG_11_theSelectedColumnIsMarkedAndOnlyOneIs() {
        granted()
        fun selected() = (0..6).filter { rule.onNodeWithTag("usage.steps.col.$it").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(listOf(6), selected())
        rule.onNodeWithTag("usage.steps.col.2").performClick(); rule.waitForIdle()
        assertEquals(listOf(2), selected())
    }

    @Test fun USG_12_withoutUsageAccessTheBlockOffersTheExistingAllowActionInsteadOfAChart() {
        var asked: GrantKind? = null
        GrantHooks.interceptor = { k, _ -> asked = k }
        host(UsageFixtures.repo(UsageFixtures.FakeSource(granted = false))); waitFor("usage.noAccess")
        assertEquals("no axes, no columns, no rows", 0, count("usage.steps.col") + count("usage.row"))
        rule.onNodeWithTag("usage.allow").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("usage.allow").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.let { assertEquals("Allow usage access", it.joinToString("")) }
        rule.onNodeWithTag("usage.allow").performClick(); rule.waitForIdle()
        assertEquals(GrantKind.UsageAccess, asked)
        capture("missing-dark")
    }

    @Test fun USG_13_withNoDataTheBlockIsOneQuietLine() {
        host(UsageFixtures.repo(UsageFixtures.FakeSource(data = emptyMap()))); waitFor("usage.empty")
        assertEquals(0, count("usage.steps.col") + count("usage.row"))
        rule.onNodeWithTag("usage.empty").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.let { assertEquals("No screen time recorded yet.", it.joinToString("")) }
        assertTrue(rule.onNodeWithTag("usage.block").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value } < 80f)
        capture("empty-dark")
    }

    // ---- accessibility ----

    @Test fun USG_14_theChartHasAnAccessibleSummaryAndAShowAsTableAction() {
        granted()
        val d = desc("usage.chart")!!
        assertTrue(d, d.startsWith("Screen time, last 7 days."))
        assertTrue("every day: $d", listOf("Mon", "Tue", "Wed", "Thu", "Fri").all { it in d } && "Yesterday, 2 h 5 min" in d && "Today, 2 h 34 min" in d)
        assertTrue("selected day and its top apps: $d", "Selected: Today, 2 h 34 min" in d && "Browser, 1 h 10 min" in d && "percent" !in d)
        val a = rule.onNodeWithTag("usage.chart").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Show as table"), a.map { it.label })
    }

    @Test fun USG_15_showAsTableSwapsTheChartForADaysAndDurationsTableAndBack() {
        granted()
        fun act() = rule.onNodeWithTag("usage.chart").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        rule.runOnUiThread { act().action() }; rule.waitForIdle()
        assertEquals("the action now offers the chart", "Show as chart", act().label)
        assertEquals(0, count("usage.steps.col"))
        rule.onNodeWithTag("usage.table").assertIsDisplayed()
        assertEquals(7, count("usage.table.row"))
        assertEquals("Today, 2 h 34 min", desc("usage.table.row.6")); assertEquals("Thu, <1 min", desc("usage.table.row.3"))
        assertEquals("the ranked rows stay", 5, count("usage.row"))
        rule.runOnUiThread { act().action() }; rule.waitForIdle()
        assertEquals(7, count("usage.steps.col"))
    }

    @Test fun USG_16_columnsAre48dpWideTapTargetsAndRowsAre48dpHighTargetsWithAppAndDuration() {
        granted()
        (0..6).forEach { i -> val b = rule.onNodeWithTag("usage.steps.col.$i").getUnclippedBoundsInRoot(); assertTrue("column $i is ${b.right.value - b.left.value} dp wide", b.right.value - b.left.value >= 47.5f) }
        assertEquals("Browser, 1 h 10 min", desc("usage.row.0"))
        (0..4).forEach { rule.onNodeWithTag("usage.row.$it").assertTouchHeightIsEqualTo(48.dp) }
    }

    @Test fun USG_17_theBlockAnimatesNothingSoReducedMotionHasNothingToTurnOff() {
        val src = listOf("UsageTimelineBlock.kt", "viz/VizSteps.kt", "viz/VizBars.kt", "viz/VizTable.kt", "viz/VizStat.kt", "viz/VizTextures.kt", "viz/Viz.kt")
        val root = listOf(File("src/main/kotlin/com/bos/sphere/ui"), File("feature-launchpad/src/main/kotlin/com/bos/sphere/ui")).first { it.exists() }
        src.forEach { f ->
            val text = File(root, f).readText()
            listOf(Regex("animate\\w*As"), Regex("Animatable\\("), Regex("InfiniteTransition"), Regex("infiniteRepeatable"), Regex("withFrameNanos"), Regex("tween\\("), Regex("spring\\("), Regex("AnimatedVisibility"), Regex("Crossfade"), Regex("updateTransition"))
                .forEach { assertFalse("$f must not use $it", it.containsMatchIn(text)) }
        }
    }

    @Test fun USG_18_identityIsTextureNotHueInTheBarsButTheRowsCarryTheAppIcon() {
        granted()
        val bmp = capture("granted-dark")
        val d = rule.density.density
        var coloured = 0; var ink = 0
        for (i in 0..4) {
            val b = rule.onNodeWithTag("usage.row.bar.$i", useUnmergedTree = true).getUnclippedBoundsInRoot()
            for (y in (b.top.value * d).toInt() until (b.bottom.value * d).toInt()) for (x in (b.left.value * d).toInt() until (b.right.value * d).toInt()) {
                val p = bmp.getPixel(x, y); val r = (p shr 16) and 255; val g = (p shr 8) and 255; val bl = p and 255
                if (maxOf(r, g, bl) - minOf(r, g, bl) > 45) coloured++ else if (r + g + bl > 450) ink++
            }
        }
        assertEquals("no hue inside the bars", 0, coloured); assertTrue("the bars are drawn in the ink", ink > 100)
        assertEquals(listOf(VizTexture.Solid, VizTexture.Hatch, VizTexture.Dots, VizTexture.Fade, VizTexture.Stipple), UsageFixtures.week().rows(6).map { it.texture })
    }

    // ---- screenshots (read by eye; the assertions only prove they were taken and are not blank) ----

    @Test fun USG_19_screenshotDarkGrantedAndSelected() {
        host(UsageFixtures.repo(), true); waitFor("usage.stat"); capture("granted-dark")
        rule.onNodeWithTag("usage.steps.col.4").performClick(); capture("granted-selected-dark")
    }

    @Test fun USG_19_screenshotLightGrantedAndSelected() {
        host(UsageFixtures.repo(), false); waitFor("usage.stat"); capture("granted-light")
        rule.onNodeWithTag("usage.steps.col.4").performClick(); capture("granted-selected-light")
    }

    @Test fun USG_19_screenshotLightMissingAccess() {
        host(UsageFixtures.repo(UsageFixtures.FakeSource(granted = false)), dark = false); waitFor("usage.noAccess"); capture("missing-light")
    }

    @Test fun USG_19_screenshotLightEmpty() {
        host(UsageFixtures.repo(UsageFixtures.FakeSource(data = emptyMap())), dark = false); waitFor("usage.empty"); capture("empty-light")
    }
}
