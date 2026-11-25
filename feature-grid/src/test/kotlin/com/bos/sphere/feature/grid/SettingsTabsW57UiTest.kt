package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 57 (settingsTabs), CFG-41: the six section tabs in the real host. A tap and a sideways swipe switch the section and the selected tab (and its
 * line) follow; the section shown is remembered for the next visit; a deep link opens its tab and flashes the row; Ctrl+Tab, Ctrl+Shift+Tab and
 * the controller's L1 / R1 switch sections; right-to-left mirrors the swipe. The pure rules (indicator, key steps, back) are on the JVM below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class SettingsTabsW57UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    private val tabs = SettingsTabs.KEYS
    private var session by mutableIntStateOf(0)
    private val memory = SettingsTabMemory.Local()

    private fun cats() = tabs.map { t -> SettingCategory(t, Glyphs.Apps, (0 until 12).map { i -> SettingItem.Toggle("$t row $i", Glyphs.Apps, i % 2 == 0, "Hint $i") {} }) }

    private fun show(initial: Int = -1, row: Int = -1, rtl: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SettingsSpace(visible = true, categories = cats(), initialCategory = initial, sounds = null, onClose = {}, session = session, initialRow = row, tabMemory = memory, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    private fun selected(): String? = tabs.firstOrNull { t ->
        rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
    }
    private fun shown(t: String) = rule.onAllNodesWithTagCompat(SettingsTabTags.list(t))
    private fun rule_settle() { rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }

    @Test fun CFG_41_aTapOnATabShowsThatSection() {
        show()
        assertEquals("Home", selected())
        rule.onNodeWithTag(SettingsTabTags.tab("Radar"), useUnmergedTree = true).performClick(); rule_settle()
        assertEquals("Radar", selected())
        assertTrue(shown("Radar"))
        rule.onNodeWithTag(SettingsTabTags.list("Radar")).assertExistsCompat()
    }

    @Test fun CFG_41_aSidewaysSwipeMovesToTheNextSectionAndTheTabFollows() {
        show()
        rule.onNodeWithTag(SettingsTabTags.PAGER).performTouchInput { swipeLeft() }; rule_settle()
        assertEquals("Look", selected())
        rule.onNodeWithTag(SettingsTabTags.PAGER).performTouchInput { swipeLeft() }; rule_settle()
        assertEquals("Apps", selected())
        rule.onNodeWithTag(SettingsTabTags.PAGER).performTouchInput { swipeRight() }; rule_settle()
        assertEquals("Look", selected())
    }

    @Test fun CFG_41_inRightToLeftTheSwipeAndTheTabOrderMirror() {
        show(rtl = true)
        rule.onNodeWithTag(SettingsTabTags.PAGER).performTouchInput { swipeRight() }; rule_settle()
        assertEquals("the next section is to the left in right-to-left", "Look", selected())
        val x = { t: String -> rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value }
        assertTrue(x("Home") > x("Look") && x("Look") > x("Apps"))
    }

    @Test fun CFG_41_theIndicatorLineSitsUnderTheSelectedTab() {
        show()
        rule.onNodeWithTag(SettingsTabTags.tab("Apps"), useUnmergedTree = true).performClick(); rule_settle()
        val bmp = shoot("w57-tabs-indicator")
        val d = rule.activity.resources.displayMetrics.density
        fun lineAt(t: String): Int {
            val b = rule.onNodeWithTag(SettingsTabTags.tab(t), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val cx = ((b.left.value + b.right.value) / 2f * d).toInt()
            val y = (b.bottom.value * d - d).toInt()
            return bmp.getPixel(cx, y)
        }
        fun accentish(c: Int) = android.graphics.Color.blue(c) > 200 && android.graphics.Color.red(c) in 110..170
        assertTrue("the accent line is under APPS: ${Integer.toHexString(lineAt("Apps"))}", accentish(lineAt("Apps")))
        assertTrue("and not under HOME: ${Integer.toHexString(lineAt("Home"))}", !accentish(lineAt("Home")))
    }

    @Test fun CFG_41_theSectionShownLastIsRememberedForTheNextVisit() {
        show()
        rule.onNodeWithTag(SettingsTabTags.tab("Side panel"), useUnmergedTree = true).performClick(); rule_settle()
        assertEquals("Side panel", memory.get())
        rule.runOnIdle { session++ } // closing and opening Settings again starts a fresh host
        rule_settle()
        assertEquals("Side panel", selected())
    }

    @Test fun CFG_41_aDeepLinkOpensItsTabAndFlashesTheRow() {
        show(initial = 3, row = 9)
        assertEquals("Radar", selected())
        rule.onNode(isSelected() and hasText("Radar row 9", substring = true)).assertExistsCompat()
        rule.mainClock.advanceTimeBy(2600); rule.waitForIdle()
        assertEquals("the flash goes after a moment", 0, rule.onAllNodes(isSelected() and hasText("Radar row 9", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun CFG_41_ctrlTabCtrlShiftTabAndL1R1SwitchSections() {
        show()
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Tab) } }; rule_settle()
        assertEquals("Look", selected())
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } } }; rule_settle()
        assertEquals("Home", selected())
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } } }; rule_settle()
        assertEquals("wraps to the last section", "System", selected())
        rule.onRoot().performKeyInput { pressKey(Key.ButtonR1) }; rule_settle()
        assertEquals("Home", selected())
        rule.onRoot().performKeyInput { pressKey(Key.ButtonR1) }; rule_settle()
        rule.onRoot().performKeyInput { pressKey(Key.ButtonR1) }; rule_settle()
        assertEquals("Apps", selected())
        rule.onRoot().performKeyInput { pressKey(Key.ButtonL1) }; rule_settle()
        assertEquals("Look", selected())
    }

    private fun shoot(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCompat(tag: String) = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}

/** Wave 57 (CFG-41): the pure rules of the tabs. */
class SettingsTabsW57ModelTest {
    @Test fun CFG_41_theIndicatorSlidesBetweenTwoTabsWithTheSwipe() {
        val lefts = listOf(0f, 100f, 250f); val widths = listOf(100f, 150f, 120f)
        assertEquals(0f to 100f, TabIndicator.place(0f, lefts, widths))
        assertEquals(50f to 125f, TabIndicator.place(0.5f, lefts, widths))
        assertEquals(250f to 120f, TabIndicator.place(2f, lefts, widths))
        assertEquals("past the last tab it stays on it", 250f to 120f, TabIndicator.place(3.4f, lefts, widths))
        assertEquals(null, TabIndicator.place(0f, emptyList(), emptyList()))
        assertEquals(1f, TabIndicator.brightness(1, 1f)); assertEquals(0.5f, TabIndicator.brightness(1, 0.5f)); assertEquals(0f, TabIndicator.brightness(3, 1f))
    }

    @Test fun CFG_41_theSixLabelsTightenBeforeTheRowScrolls() {
        val six = listOf(80f, 76f, 76f, 96f, 170f, 120f); val chars = listOf(4, 4, 4, 5, 10, 6)
        assertEquals("room to spare: the full style", 12f to 1.2f, TabIndicator.fit(six, chars, 2000f, 2f, 2f))
        val tight = TabIndicator.fit(six, chars, 822f, 2f, 2f)!!
        assertTrue("a phone width tightens the padding: $tight", tight.first < 12f)
        assertEquals("far too narrow: the row scrolls", null, TabIndicator.fit(six, chars, 500f, 2f, 2f))
    }

    @Test fun CFG_41_theKeysThatSwitchSections() {
        assertEquals(SettingsTabs.KeyStep.Next, SettingsTabs.keyStep(SettingsTabs.KEYCODE_TAB, ctrl = true, shift = false))
        assertEquals(SettingsTabs.KeyStep.Previous, SettingsTabs.keyStep(SettingsTabs.KEYCODE_TAB, ctrl = true, shift = true))
        assertEquals("a plain Tab moves the focus, not the section", null, SettingsTabs.keyStep(SettingsTabs.KEYCODE_TAB, ctrl = false, shift = false))
        assertEquals(SettingsTabs.KeyStep.Next, SettingsTabs.keyStep(SettingsTabs.KEYCODE_BUTTON_R1, ctrl = false, shift = false))
        assertEquals(SettingsTabs.KeyStep.Previous, SettingsTabs.keyStep(SettingsTabs.KEYCODE_BUTTON_L1, ctrl = false, shift = false))
        assertEquals(android.view.KeyEvent.KEYCODE_TAB, SettingsTabs.KEYCODE_TAB)
        assertEquals(android.view.KeyEvent.KEYCODE_BUTTON_L1, SettingsTabs.KEYCODE_BUTTON_L1)
        assertEquals(android.view.KeyEvent.KEYCODE_BUTTON_R1, SettingsTabs.KEYCODE_BUTTON_R1)
        assertEquals(1, SettingsTabs.step(0, 6, true)); assertEquals(5, SettingsTabs.step(0, 6, false)); assertEquals(0, SettingsTabs.step(5, 6, true))
    }

    @Test fun CFG_41_settingsOpensOnTheRequestedTabElseTheRememberedOneElseTheFirst() {
        val c = SettingsTabs.KEYS.map { SettingCategory(it, Glyphs.Apps, emptyList()) } + SettingCategory("Finish setup", Glyphs.Apps, emptyList())
        val tabs = SettingsTabs.tabIndices(c)
        assertEquals("Finish setup is not a tab", (0..5).toList(), tabs)
        assertEquals(2, SettingsTabs.initialTab(c, tabs, 2, "Radar"))
        assertEquals(3, SettingsTabs.initialTab(c, tabs, -1, "Radar"))
        assertEquals(0, SettingsTabs.initialTab(c, tabs, -1, null))
        assertEquals("an unknown memory falls back to the first", 0, SettingsTabs.initialTab(c, tabs, -1, "Gone"))
    }
}
