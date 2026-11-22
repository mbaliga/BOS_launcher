package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
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
 * Wave 54 (settingsLines), CFG-21: no boxes. Rows sit on the page background, flat, separated by hairlines that start at the text; a group
 * is a semantic list under a small grey header. Probes read the drawn pixels of a group (not just the semantics) so a filled rounded
 * container cannot come back unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class SettingsLinesW54Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    @After fun tearDown() { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); L10n.setLanguage("en") }

    private var shape = "Rounded"

    private fun categories(): List<SettingCategory> = listOf(
        SettingCategory("Home screen", Glyphs.Apps, listOf(SettingItem.Toggle("Home option", Glyphs.Apps, true) {})),
        SettingCategory(
            "Look", Glyphs.Moon,
            listOf(
                SettingItem.Toggle("Light / dark", Glyphs.Moon, true) {},
                SettingItem.Toggle("Plate", Glyphs.Apps, false) {},
                SettingItem.Toggle("Icon shape toggle", Glyphs.Apps, false) {},
                SettingItem.Choice("Icon shape", Glyphs.Apps, listOf("Rounded", "Square", "Circle"), shape, { it }, hint = "The shape of every icon") { shape = it },
                SettingItem.Action("Reset look", Glyphs.Apps, navigates = true) {},
                SettingItem.Pane("Pane page", Glyphs.Apps, "Value") { androidx.compose.material3.Text("Pane body text", color = HyleColors.InkBright) },
            ),
        ),
        SettingCategory("Gestures", Glyphs.Apps, listOf(SettingItem.Toggle("Swipe", Glyphs.Apps, true) {})),
        SettingCategory("App drawer", Glyphs.Apps, listOf(SettingItem.Toggle("Drawer option", Glyphs.Apps, true) {})),
        SettingCategory("System and sound", Glyphs.Apps, listOf(SettingItem.Toggle("Sound", Glyphs.Apps, true) {})),
    )

    private fun show(initial: Int = -1, rtl: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SettingsSpace(visible = true, categories = categories(), initialCategory = initial, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(outDir, "$name.png").length() > 2000)
    }

    /** The drawn pixels of the window (view draw, like the screenshots) cropped to [r]. */
    private fun pixelsOf(r: androidx.compose.ui.geometry.Rect): Triple<IntArray, Int, Int> {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val x = r.left.toInt().coerceIn(0, bmp.width - 1); val y = r.top.toInt().coerceIn(0, bmp.height - 1)
        val w = (r.width.toInt()).coerceIn(1, bmp.width - x); val h = (r.height.toInt()).coerceIn(1, bmp.height - y)
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, x, y, w, h)
        return Triple(px, w, h)
    }

    private fun groupsOf() = rule.onAllNodesWithTag(OneUiTags.GROUP)

    /** The drawn pixels at the four corners and the edge middles of every group equal the page colour: no filled rounded box. */
    private fun assertNoBox() {
        val page = HyleColors.Background.toArgb()
        val n = groupsOf().fetchSemanticsNodes().size
        assertTrue("there are groups to probe", n > 0)
        for (i in 0 until n) {
            val (px, w, h) = pixelsOf(groupsOf()[i].fetchSemanticsNode().boundsInRoot)
            val probes = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1, 0 to 5, w - 1 to 5, 3 to 3, w - 4 to h - 4)
            for ((x, y) in probes) assertEquals("group $i at $x,$y is the page colour (no box)", page and 0xFFFFFF, px[y * w + x] and 0xFFFFFF)
        }
    }

    @Test fun CFG_21_w54_theRootRowsHaveNoFilledRoundedBox_dark() { show(); assertNoBox() }

    @Test fun CFG_21_w54_theRootRowsHaveNoFilledRoundedBox_light() { HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF); show(); assertNoBox() }

    @Test fun CFG_21_w54_aCategoryPageHasNoBox() { show(initial = 1); assertNoBox() }

    @Test fun CFG_21_w54_aRadioPageHasNoBox() {
        show(initial = 1)
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertNoBox()
    }

    @Test fun CFG_21_w54_aPaneIsFlatToo() {
        show(initial = 1)
        rule.onNode(hasContentDescription("Pane page, Value")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNode(hasText("Pane body text")).assertExists()
        val page = HyleColors.Background.toArgb()
        val b = rule.onNode(hasText("Pane body text")).fetchSemanticsNode().boundsInRoot
        val (px, _, _) = pixelsOf(androidx.compose.ui.geometry.Rect(b.left - 12f, b.top, b.left - 2f, b.bottom))
        assertEquals("beside the pane text is the page, not a box", page and 0xFFFFFF, px[0] and 0xFFFFFF)
    }

    // ---- dividers --------------------------------------------------------------------------------------------------------

    private fun dividersIn(group: Int) = rule.onAllNodes(hasTestTag(OneUiTags.DIVIDER) and hasAnyAncestor(hasTestTag(OneUiTags.GROUP))).fetchSemanticsNodes()
        .filter { d -> groupsOf()[group].fetchSemanticsNode().boundsInRoot.let { b -> d.boundsInRoot.top >= b.top && d.boundsInRoot.bottom <= b.bottom } }.size

    @Test fun CFG_21_w54_dividersSitBetweenRowsAndNotAfterTheLastOne_root() {
        show()
        val sizes = OneUiModel.groups(categories()).map { it.size }
        val nodes = groupsOf().fetchSemanticsNodes()
        assertEquals(sizes.size, nodes.size)
        sizes.forEachIndexed { g, rows ->
            val info = nodes[g].config.getOrNull(SemanticsProperties.CollectionInfo)
            assertEquals("a group is still a semantic list of its rows", rows, info?.rowCount)
            assertEquals("group $g of $rows rows has ${rows - 1} dividers", rows - 1, dividersIn(g))
        }
    }

    @Test fun CFG_21_w54_dividersSitBetweenRowsAndNotAfterTheLastOne_category() {
        show(initial = 1)
        val blocks = OneUiModel.blocks(categories()[1].items, OneUiSections.headers("Look", categories()[1].items)).filterIsInstance<OneUiModel.Block.Group>()
        blocks.forEachIndexed { g, b -> assertEquals(b.rows.size - 1, dividersIn(g)) }
    }

    @Test fun CFG_21_w54_dividerStartsWhereTheTextStarts_ltr() {
        show(initial = 1)
        val d = rule.onAllNodesWithTag(OneUiTags.DIVIDER).onFirst().getUnclippedBoundsInRoot()
        val t = rule.onNode(hasText("Plate"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("divider start equals text start", t.left.value, d.left.value, 0.6f)
        assertTrue("the divider runs to the end margin", d.right.value > 380f)
    }

    @Test fun CFG_21_w54_dividerStartsWhereTheTextStarts_rtl() {
        show(initial = 1, rtl = true)
        val d = rule.onAllNodesWithTag(OneUiTags.DIVIDER).onFirst().getUnclippedBoundsInRoot()
        val t = rule.onNode(hasText("Plate"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("RTL: divider start (the right edge) equals the text start", t.right.value, d.right.value, 0.6f)
        assertTrue("it runs to the end margin", d.left.value < 40f)
    }

    @Test fun CFG_21_w54_theRootDividerStartsAtTheTextNotTheIconTile() {
        show()
        val d = rule.onAllNodesWithTag(OneUiTags.DIVIDER).onFirst().getUnclippedBoundsInRoot()
        val title = rule.onNode(hasText("Look", ignoreCase = true) and hasAnyAncestor(hasTestTag(OneUiTags.ROOT)), useUnmergedTree = true)
        val tb = title.getUnclippedBoundsInRoot()
        assertEquals(tb.left.value, d.left.value, 0.6f)
        assertTrue("the divider is hairline", d.bottom - d.top <= 1.dp)
    }

    @Test fun CFG_21_w54_theDividerIsTheHairlineColourAndAtMostOneDp() {
        show(initial = 1)
        val d = rule.onAllNodesWithTag(OneUiTags.DIVIDER).onFirst()
        d.getUnclippedBoundsInRoot().let { assertTrue(it.bottom - it.top <= 1.dp) }
        val b = d.fetchSemanticsNode().boundsInRoot
        val (px, _, _) = pixelsOf(androidx.compose.ui.geometry.Rect(b.left + 20f, b.top, b.right - 20f, b.top + 1f))
        val c = HyleColors.Hairline; val bg = HyleColors.Background
        val want = c.compositeOver(bg).toArgb()
        val got = px[px.size / 2] and 0xFFFFFF
        for (sh in listOf(0, 8, 16)) assertEquals("the line is the hairline colour over the page", ((want shr sh) and 0xFF).toDouble(), ((got shr sh) and 0xFF).toDouble(), 2.0)
    }

    // ---- focus, search --------------------------------------------------------------------------------------------------

    @Test fun CFG_21_w54_theFocusedRowShowsAFocusRing() {
        val first = androidx.compose.ui.focus.FocusRequester(); val second = androidx.compose.ui.focus.FocusRequester()
        rule.setContent {
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            OneUiGroup(2) {
                OneUiListRow("First row", modifier = Modifier.focusRequester(first), onClick = {})
                OneUiListRow("Second row", modifier = Modifier.focusRequester(second), onClick = {})
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag(OneUiTags.FOCUS_RING, useUnmergedTree = true).assertCountEquals(0)
        rule.runOnUiThread { first.requestFocus() }; rule.waitForIdle()
        rule.onNode(hasText("First row")).assertIsFocused()
        rule.onAllNodesWithTag(OneUiTags.FOCUS_RING, useUnmergedTree = true).assertCountEquals(1)
        rule.runOnUiThread { second.requestFocus() }; rule.waitForIdle()
        rule.onNode(hasText("Second row")).assertIsFocused()
        rule.onAllNodesWithTag(OneUiTags.FOCUS_RING, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun CFG_21_w54_searchResultsUseTheSameFlatListStyle() {
        show()
        rule.onNodeWithTag(OneUiTags.SEARCH).performTextInput("o"); rule.waitForIdle()
        val n = groupsOf().fetchSemanticsNodes().size
        assertEquals("one flat results list", 1, n)
        val rows = groupsOf()[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.CollectionInfo)!!.rowCount
        assertTrue(rows > 1)
        assertEquals(rows - 1, rule.onAllNodesWithTag(OneUiTags.DIVIDER).fetchSemanticsNodes().size)
        assertNoBox()
    }

    // ---- screenshots -----------------------------------------------------------------------------------------------------

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }

    private fun pane(tag: String) { show(initial = 1); rule.onNode(hasContentDescription("Pane page, Value")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle(); shoot("w54-pane-$tag") }

    @Test fun CFG_21_w54_screenshotRootDark() { show(); shoot("w54-root-dark") }
    @Test fun CFG_21_w54_screenshotCategoryDark() { show(initial = 1); shoot("w54-category-dark") }
    @Test fun CFG_21_w54_screenshotPaneDark() { pane("dark") }
    @Test fun CFG_21_w54_screenshotRootLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF); show(); shoot("w54-root-light") }
    @Test fun CFG_21_w54_screenshotCategoryLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF); show(initial = 1); shoot("w54-category-light") }
    @Test fun CFG_21_w54_screenshotPaneLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF); pane("light") }

    private fun radio(tag: String) {
        show(initial = 1)
        rule.onNode(hasContentDescription("Icon shape, Rounded")).performClick(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        shoot("w54-radio-$tag")
    }

    @Test fun CFG_21_w54_radioScreenshotDark() { radio("dark") }

    @Test fun CFG_21_w54_radioScreenshotLight() { HyleTheme.apply(ThemeVariant.Light, 0xFF8E7BFF); radio("light") }
}
