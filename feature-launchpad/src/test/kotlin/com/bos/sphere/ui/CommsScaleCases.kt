package com.bos.sphere.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CommsScale
import com.bos.sphere.core.data.CommsScaleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 28 (commsLayout): the Comms size at 75, 100 and 125 percent: the geometry scales with it, the pinch changes it live, touch targets stay at 48 dp or
 * more of real screen, the content stays out from under the home card, the setting persists; screenshots `build/screenshots/comms-<orientation>-scale-<n>.png`.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class CommsScaleCases(orientation: String) : CommsLayoutCases(orientation) {
    private fun dpx() = rule.activity.resources.displayMetrics.density
    private fun boundsPx(tag: String, unmerged: Boolean = true) = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot
    private fun rowPitchPx(): Float = boundsPx("lp.row.k1").top - boundsPx("lp.row.k0").top

    @Test fun CMS_10_theRowPitchIs70dpTimesTheFactorAt75And100And125Percent() {
        render()
        for (pct in listOf(75, 100, 125)) {
            CommsScaleSettings.set(pct); rule.waitForIdle()
            assertEquals("row pitch at $pct percent", 70f * CommsScale.factor(pct) * dpx(), rowPitchPx(), 1f)
            val icon = boundsPx("lp.badge.k0")
            assertEquals("icon at $pct percent", 38f * CommsScale.factor(pct) * dpx(), icon.right - icon.left, 1f)
            assertEquals("left margin at $pct percent", 32f * CommsScale.factor(pct) * dpx(), icon.left, 1f)
            val title = rule.onNodeWithTag("lp.title.k0", true).fetchSemanticsNode().boundsInRoot
            assertEquals("text starts 16 dp after the icon at $pct percent", icon.right + 16f * CommsScale.factor(pct) * dpx(), title.left, 1.5f)
        }
    }

    @Test fun CMS_11_pinchNoLongerChangesTheSizeItFiltersByReadStateInstead() {
        render()
        assertEquals(100, CommsScaleSettings.percent.value)
        rule.onNodeWithTag("lp.root").performTouchInput { pinch(center + Offset(-100f, 0f), center + Offset(-110f, 0f), center + Offset(100f, 0f), center + Offset(110f, 0f)) }
        rule.waitForIdle()
        assertEquals("wave 29: a pinch is the read filter, not the size", 100, CommsScaleSettings.percent.value)
        rule.onNodeWithTag("lp.readFilter").assertExists() // pinched apart: Read only (every row of this fixture is unread, so none shows)
        rule.onNodeWithTag("lp.readFilter").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("lp.readFilter").assertDoesNotExist()
        assertEquals(100, CommsScaleSettings.percent.value)
    }

    @Test fun CMS_12_touchTargetsStayAtLeast48dpAt75Percent() {
        render(scale = 75)
        val min = 48f * dpx() - 1f
        val menu = boundsPx("lp.menu"); assertTrue("menu button ${menu.width}x${menu.height}", menu.width >= min && menu.height >= min)
        for (g in listOf("volume", "battery", "signal", "wifi")) { val b = boundsPx("lp.sys.$g"); assertTrue("$g target is ${b.height} px high", b.height >= min) }
        val row = boundsPx("lp.row.k0"); assertTrue("a row is ${row.height} px, 70 dp x 0.75 = 52.5 dp", row.height >= min)
    }

    @Test fun CMS_12_theMinimumTouchTargetAroundSmallerThingsIs48dpOfRealScreenAtEverySize() {
        val seen = HashMap<Int, Float>()
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                for (p in listOf(75, 100, 125)) CommsScaleHost(p) { val d = LocalDensity.current; seen[p] = with(d) { LocalViewConfiguration.current.minimumTouchTargetSize.width.toPx() } }
            }
        }
        rule.waitForIdle()
        for (p in listOf(75, 100, 125)) assertEquals("minimum touch target at $p percent", 48f * dpx(), seen[p]!!, 1f)
    }

    @Test fun CMS_15_theContentStaysLeftOfTheHomeCardAndLongTitlesEllipsizeAtEverySize() {
        val long = feed(4).map { if (it.key == "k0") it.copy(title = "A very long headline that has to be cut at the right margin " + "xyz ".repeat(20), summary = "") else it }
        render(long)
        for (pct in listOf(75, 125)) {
            CommsScaleSettings.set(pct); rule.waitForIdle()
            val f = CommsScale.factor(pct)
            val limit = rule.activity.resources.displayMetrics.widthPixels - 0.07f * (rule.activity.resources.displayMetrics.widthPixels / dpx()) * dpx() // the card's sliver starts here
            val time = boundsPx("lp.time.k0"); assertTrue("time right edge ${time.right} under the card ($limit) at $pct", time.right <= limit - 17f * dpx() * f)
            val title = boundsPx("lp.title.k0"); assertTrue("title right edge ${title.right} at $pct", title.right <= limit - 17f * dpx() * f)
            assertTrue("a long title is one line high at $pct", title.height < 30f * dpx() * f)
        }
    }

    @Test fun CMS_16_theSettingPersistsInItsOwnPrefFileAndGarbageFallsBackToTheDefault() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        CommsScaleSettings.resetForTest(); CommsScaleSettings.init(ctx)
        CommsScaleSettings.set(115)
        CommsScaleSettings.resetForTest(); assertEquals(100, CommsScaleSettings.percent.value)
        CommsScaleSettings.init(ctx)
        assertEquals("read back from the file", 115, CommsScaleSettings.percent.value)
        ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).edit().putString(CommsScaleSettings.KEY, "banana").commit()
        CommsScaleSettings.resetForTest(); CommsScaleSettings.init(ctx)
        assertEquals(100, CommsScaleSettings.percent.value)
        ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).edit().putInt(CommsScaleSettings.KEY, 93).commit()
        CommsScaleSettings.resetForTest(); CommsScaleSettings.init(ctx)
        assertEquals("off a detent: the nearest", 95, CommsScaleSettings.percent.value)
        ctx.getSharedPreferences(CommsScaleSettings.FILE, 0).edit().clear().commit()
    }

    // ---- screenshots: 75, 100 and 125 percent, the screen and the menu ----
    @Test fun CMS_17_screenshotsAt75And100And125Percent() {
        render(); rule.onNodeWithTag("hub.view.unread").performClick(); rule.waitForIdle()
        for (pct in listOf(75, 100, 125)) { CommsScaleSettings.set(pct); rule.waitForIdle(); shoot("comms-$orientation-scale-$pct") }
    }

    @Test fun CMS_17_menuScreenshotsAt75And125Percent() {
        render(); openMenu()
        for (pct in listOf(75, 125)) { CommsScaleSettings.set(pct); rule.waitForIdle(); shoot("comms-$orientation-scale-$pct-menu") }
    }
}
