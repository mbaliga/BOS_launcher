package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CommsScale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W33 (commsDebt): the tab row at Comms size 125 percent and at font scale 2 on a 360 dp phone: it tightens, then scrolls, and the selected tab stays in sight. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [35])
class CommsTabFitW33Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { com.bos.sphere.core.data.CommsScaleSettings.resetForTest() }

    private fun state() = HubState(HubPrefs(null), null, true)

    private fun host(hub: HubState, percent: Int, fontScale: Float) = rule.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale), LocalLp provides LpDark) {
            CommsScaleHost(percent) { Box(Modifier.fillMaxWidth()) { HubSwitcher(hub) } }
        }
    }

    private fun inSight(tag: String) {
        val row = rule.onNodeWithTag("hub.switcher").fetchSemanticsNode().boundsInRoot
        val tab = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag ${tab.left}..${tab.right} inside the row ${row.left}..${row.right}", tab.left >= row.left - 1f && tab.right <= row.right + 1f)
    }

    @Test fun CMS_W33_10_theTabRowFitsAt100PercentWithoutTighteningOrScrolling() {
        val hub = state(); host(hub, 100, 1f); rule.waitForIdle()
        hub.tabs.forEach { inSight("hub.view.${it.id}") }
    }

    @Test fun CMS_W33_11_at125PercentEveryTabIsReachableAndTheSelectedOneIsInSight() {
        val hub = state(); host(hub, 125, 1f); rule.waitForIdle()
        hub.select(hub.tabs.last()); rule.waitForIdle()
        inSight("hub.view.${hub.tabs.last().id}")
        hub.select(hub.tabs.first()); rule.waitForIdle()
        inSight("hub.view.${hub.tabs.first().id}")
    }

    @Test fun CMS_W33_12_atFontScale2TheSelectedTabIsInSightForEveryTab() {
        val hub = state(); host(hub, 100, 2f); rule.waitForIdle()
        hub.tabs.forEach { v -> hub.select(v); rule.waitForIdle(); inSight("hub.view.${v.id}") }
    }

    @Test fun CMS_W33_13_at125PercentAndFontScale2TheSelectedTabIsInSight() {
        val hub = state(); host(hub, 125, 2f); rule.waitForIdle()
        hub.tabs.forEach { v -> hub.select(v); rule.waitForIdle(); inSight("hub.view.${v.id}") }
    }

    // ---- the pure plan ----
    private val chars = listOf(3, 6, 5, 8); private val label = listOf(30f, 70f, 55f, 95f); private val slot = listOf(60f, 100f, 80f, 120f)
    private fun plan(avail: Float) = CommsTabFit.plan(label, chars, slot, 8f, 20f, avail, 1f, 17f, targetPx = 48f)

    @Test fun CMS_W33_14_roomKeepsTheSlotsThenGivesUpTheSlotsThenTightensThenScrolls() {
        assertTrue(plan(1000f).useSlots); assertEquals(0f, plan(1000f).trackingSp, 0f)
        val noSlots = label.indices.sumOf { maxOf(label[it] + 16f, 48f).toDouble() }.toFloat() + 20f
        assertFalse("below the slots' total the slots go", plan(noSlots + 1f).useSlots)
        val p = plan(noSlots - 10f); assertTrue(p.trackingSp < 0f); assertFalse(p.scrolls)
        assertTrue("no tighter than 0.06 em", p.trackingSp >= -0.06f * 17f - 1e-3f)
        val tight = plan(noSlots - 100f); assertTrue(tight.scrolls); assertEquals(-0.06f * 17f, tight.trackingSp, 1e-3f)
        assertEquals(1.25f, CommsScale.factor(125), 1e-3f)
    }
}
