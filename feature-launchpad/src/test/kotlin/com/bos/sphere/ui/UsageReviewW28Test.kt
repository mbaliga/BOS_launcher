package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.bos.sphere.core.design.L10n
import com.bos.sphere.ui.viz.VizBar
import com.bos.sphere.ui.viz.VizStepColumn
import com.bos.sphere.ui.viz.VizSteps
import com.bos.sphere.ui.viz.VizTexture
import com.bos.sphere.ui.viz.VizTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 28 adversarial review of the usage block: language, RTL, large text, saved state, dead strings. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class UsageReviewW28Test : LaunchpadShots() {
    @After fun reset() { L10n.setLanguage("en") }

    private val tone = VizTone.Night
    private val cols = listOf(30, 60, 20, 0, 80, 40, 100).mapIndexed { i, v ->
        VizStepColumn("D$i", v * 60_000.0, "${v}m", "day $i", accent = i == 6)
    }

    private fun shot(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    private fun bright(p: Int) = ((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255) > 600
    private fun yellow(p: Int) = ((p shr 16) and 255) > 200 && ((p shr 8) and 255) > 200 && (p and 255) < 120

    @Test fun USG_30_dayNamesAndInitialsFollowTheAppLanguageNotTheDeviceLocale() {
        L10n.setLanguage("ja")
        val w = UsageShaping.week(UsageWeekRepo(UsageFixtures.FakeSource(), zone = { UsageFixtures.zone }).load(UsageFixtures.now), UsageFixtures.zone, UsageFixtures.today)
        // 4 March 2024 was a Monday: the Japanese app language names it 月, whatever the device locale is.
        assertEquals("月", w.columns.first { it.date.dayOfMonth == 4 }.initial)
    }

    @Test fun USG_31_inARightToLeftLayoutTheColumnsAreDrawnWhereTheirTapTargetsAre() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(tone.ground).padding(horizontal = 20.dp)) {
                    VizSteps(cols, 6, {}, tone)
                }
            }
        }
        val d = rule.density.density
        val b = rule.onNodeWithTag("viz.steps.col.6").getUnclippedBoundsInRoot()
        val bmp = shot()
        val x = (((b.left.value + b.right.value) / 2f) * d).toInt()
        val top = (rule.onNodeWithTag("viz.steps.col.6").getUnclippedBoundsInRoot().top.value * d).toInt()
        val hits = (top until top + (54 * d).toInt()).count { y -> yellow(bmp.getPixel(x, y)) }
        assertTrue("today's accent column is under its own tap target in RTL ($hits yellow pixels)", hits > 8)
    }

    @Test fun USG_32_inARightToLeftLayoutTheBarsStartFromTheRightEdgeTheReadingStart() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(tone.ground).padding(horizontal = 20.dp)) {
                    Box(Modifier.testTag("bar")) { VizBar(0.5f, VizTexture.Solid, tone) }
                }
            }
        }
        val d = rule.density.density
        val b = rule.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val bmp = shot()
        val y = (((b.top.value + b.bottom.value) / 2f) * d).toInt()
        val left = ((b.left.value + 4) * d).toInt(); val right = ((b.right.value - 4) * d).toInt()
        assertTrue("the bar fills from the right edge", bright(bmp.getPixel(right, y)))
        assertFalse("and leaves the left end to the track", bright(bmp.getPixel(left, y)))
    }

    @Test fun USG_33_atFontScaleTwoTheStepsGrowSoTheirTextIsNotClipped() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.fillMaxSize().background(tone.ground).padding(horizontal = 20.dp)) {
                    androidx.compose.foundation.layout.Column {
                        VizSteps(cols, 6, {}, tone, tagPrefix = "s")
                    }
                }
            }
        }
        fun h(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
        val steps = rule.onAllNodesWithTag("s.col.0").fetchSemanticsNodes().size
        assertEquals(1, steps)
        val col = h("s.col.0")
        assertTrue("the steps need room for a 28 sp figure above and 22 sp names below, got $col dp", col >= 60f + 31f)
    }

    @Test fun USG_34_theStatHeaderWrapsInsteadOfClippingAtFontScaleTwoOnANarrowBlock() {
        rule.setContent {
            Themed(true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.background(tone.ground).width(300.dp)) {
                        UsageTimelineBlock(UsageFixtures.repo(), UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.stat").fetchSemanticsNodes().isNotEmpty() }
        val figure = rule.onNodeWithTag("viz.stat.figure", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the figure stays inside the 300 dp block (right edge ${figure.right.value})", figure.right.value <= 300.5f)
    }

    @Test fun USG_35_theTableViewSurvivesARotationLikeTheSelectedDay() {
        val t = StateRestorationTester(rule)
        t.setContent { Themed(true) { UsageTimelineBlock(UsageFixtures.repo(), UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.stat").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { rule.onNodeWithTag("usage.chart").fetchSemanticsNode().config[SemanticsActions.CustomActions].single().action() }
        rule.waitForIdle()
        t.emulateSavedInstanceStateRestore()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.table").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, rule.onAllNodesWithTag("usage.steps.col.0").fetchSemanticsNodes().size)
    }

    @Test fun USG_36_theTranslationsOfTheRemovedUsagePanelAreGone() {
        val root = listOf(File("../core-design/src/main/kotlin/com/bos/sphere/core/design"), File("core-design/src/main/kotlin/com/bos/sphere/core/design")).first { it.exists() }
        val text = root.listFiles()!!.filter { it.name.endsWith(".kt") }.joinToString("\n") { it.readText() }
        assertFalse("no screen shows this text any more", text.contains("Usage access is off. It lets the launcher"))
        assertFalse(text.contains("\"Open usage access settings\""))
    }
}
