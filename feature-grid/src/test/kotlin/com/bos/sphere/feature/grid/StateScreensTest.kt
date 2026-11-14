package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.OnboardingFlow
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.input.PuckLayout
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

/** CFG-06 onboarding, CFG-09 safe mode and CFG-05 landing on a setting: rendered through Robolectric, screenshots in build/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class StateScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage(""); SettingsFocus.clear() }

    private fun shoot(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun drawn(b: Bitmap): Int {
        val bg = b.getPixel(2, 2); var n = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) if (b.getPixel(x, y) != bg) n++
        return n
    }

    private fun lum(b: Bitmap): Double {
        var s = 0.0; var n = 0
        for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) { val p = b.getPixel(x, y); s += 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255); n++ }
        return s / n
    }

    private fun onboarding(step: Int, light: Boolean, prefs: LauncherPrefs = LauncherPrefs(), msg: String? = null, next: () -> Unit = {}, skip: () -> Unit = {}) {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF6D5BFF)
        rule.setContent {
            OnboardingView(step, prefs, msg, onStyle = {}, onSetDefault = {}, onImport = {}, onNext = next, onSkip = skip)
        }
    }

    // ---- CFG-06 ---------------------------------------------------------------------------------------------------

    @Test fun CFG_06_firstCardIsMakeItHomeAndAsksForNoPermission() {
        onboarding(0, false)
        rule.onNodeWithText("Make it home").assertIsDisplayed()
        rule.onNodeWithText("Set as default").assertIsDisplayed()
        assertTrue(OnboardingFlow.upFrontGrants.isEmpty())
        assertTrue("card draws content", drawn(shoot("cfg06-onboarding-home-dark")) > 400)
    }

    @Test fun CFG_06_secondCardOffersTheHomeStylesAndTheThirdOffersImport() {
        onboarding(1, false, LauncherPrefs(homeStyle = HomeStyle.Grid))
        rule.onNodeWithText("Pick a look").assertIsDisplayed()
        rule.onNodeWithText("Press and hold anywhere to change things.").assertIsDisplayed()
        assertTrue(drawn(shoot("cfg06-onboarding-style-dark")) > 400)
    }

    @Test fun CFG_06_thirdCardImportsAndFinishes() {
        onboarding(2, false, msg = "That file could not be imported: x")
        rule.onNodeWithText("Bring your setup").assertIsDisplayed()
        rule.onNodeWithText("Import a setup file…").assertIsDisplayed()
        rule.onNodeWithText("That file could not be imported: x").assertIsDisplayed()
        rule.onNodeWithText("Done").assertIsDisplayed()
        assertTrue(drawn(shoot("cfg06-onboarding-import-dark")) > 400)
    }

    @Test fun CFG_06_nextAdvancesAndSkipLeaves() {
        var nexts = 0; var skips = 0
        onboarding(0, false, next = { nexts++ }, skip = { skips++ })
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Skip").performClick()
        assertEquals(1, nexts); assertEquals(1, skips)
    }

    @Test fun CFG_06_lightThemeIsLight() {
        onboarding(0, true)
        val b = shoot("cfg06-onboarding-home-light")
        assertTrue("light card is light (${lum(b)})", lum(b) > 150)
    }

    // ---- CFG-09 ---------------------------------------------------------------------------------------------------

    private val apps = listOf("Zebra", "apple", "Mango", "Camera").mapIndexed { i, l -> SafeApp("k$i", l) }

    private fun safeMode(restore: String?, message: String? = null, open: (SafeApp) -> Unit = {}, onRestore: () -> Unit = {}, carry: () -> Unit = {}, light: Boolean = false) {
        HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF6D5BFF)
        rule.setContent { SafeModeView(apps, restore, message, open, onRestore, carry) }
    }

    @Test fun CFG_09_safeModeShowsAMinimalGridWithRestoreAndCarryOn() {
        var restored = 0; var carried = 0; var opened: String? = null
        safeMode("1/1/26 9:00", open = { opened = it.label }, onRestore = { restored++ }, carry = { carried++ })
        rule.onNodeWithText("Safe mode").assertIsDisplayed()
        rule.onNodeWithText("Restore last snapshot (1/1/26 9:00)").performClick()
        rule.onNodeWithText("Carry on").performClick()
        rule.onNodeWithText("Mango").performClick()
        assertEquals(1, restored); assertEquals(1, carried); assertEquals("Mango", opened)
        assertTrue(drawn(shoot("cfg09-safemode-dark")) > 400)
    }

    @Test fun CFG_09_withoutASnapshotItSaysSoAndOffersNoRestore() {
        safeMode(null, message = "Restore failed: x")
        rule.onNodeWithText("There is no snapshot to restore yet.").assertIsDisplayed()
        rule.onNodeWithText("Restore failed: x").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTextCount("Restore last snapshot"))
        assertTrue(drawn(shoot("cfg09-safemode-nosnap-dark")) > 400)
    }

    @Test fun CFG_09_safeModeLightIsLight() {
        safeMode("now", light = true)
        assertTrue(lum(shoot("cfg09-safemode-light")) > 150)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(prefix: String) =
        onAllNodes(androidx.compose.ui.test.hasText(prefix, substring = true)).fetchSemanticsNodes().size

    // ---- CFG-05: landing on the exact setting ---------------------------------------------------------------------

    private fun settings(initial: Int = 0) {
        val items = (0 until 12).map { SettingItem.Toggle("Option $it", Glyphs.Apps, false) {} }
        rule.setContent { SettingsSpace(visible = true, categories = listOf(SettingCategory("Home", Glyphs.Apps, items)), initialCategory = initial, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) }
    }

    @Test fun CFG_05_aRadarSettingsResultLandsOnItsRowWithTheCursorOnIt() {
        SettingsFocus.request(9)
        settings()
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        rule.onNode(isSelected() and hasAnyDescendant(hasText("Option 9")), useUnmergedTree = true).assertIsDisplayed()
        assertEquals("the request is used once", SettingsFocus.NONE, SettingsFocus.take())
        shoot("cfg05-settings-landed-on-row")
    }

    @Test fun CFG_05_withoutARequestNothingIsSelected() {
        settings()
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        assertEquals(0, rule.onAllNodes(isSelected() and hasAnyDescendant(hasText("Option", substring = true)), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ---- CFG-15 -----------------------------------------------------------------------------------------------------

    @Test fun CFG_15_theStoredCeilingEqualsTheLayoutsCeilingAndEveryShippedActionFitsAPhone() {
        assertEquals(PuckLayout.MAX_ACTION_COUNT, PuckActions.MAX)
        assertTrue(PuckLayout.compute(360f, PuckActions.MAX).capacity >= PuckActions.DEFAULT.size)
        // INP-19: the ring never draws more buttons than fit, whatever the list holds.
        for (side in listOf(240f, 320f, 411f, 800f)) {
            val r = PuckLayout.compute(side, PuckActions.MAX)
            assertTrue("$side: ${r.slots.size} <= ${r.capacity}", r.slots.size <= r.capacity)
            assertTrue("$side clear of overlap", PuckLayout.clearance(r) > 0f || r.slots.isEmpty())
        }
    }

    @Test fun CFG_15_everyChoosableActionHasAGlyph() {
        for (id in PuckActions.AVAILABLE) Glyphs.forAction(id)
    }
}
