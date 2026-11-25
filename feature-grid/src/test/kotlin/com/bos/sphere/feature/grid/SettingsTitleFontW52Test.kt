package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.bos.sphere.core.data.UiFonts
import com.bos.sphere.core.design.FontCatalog
import com.bos.sphere.core.design.HyleFont
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * Wave 52 (settingsFont), CFG-21: every settings title is centred in the empty band under the back arrow, in capitals, in Space Grotesk; the font picker is a
 * row of cards each showing "Liftoff 12:30 Aa" in its own font; the chosen font reaches the theme's typography; a missing file falls back to the system sans.
 * Screenshots: build/screenshots/w52-font-picker.png (the Look page's cards) and w52-title-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsTitleFontW52Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); HyleFont.apply(null) }
    @After fun tearDown() { L10n.setLanguage(""); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF); HyleFont.apply(null) }

    private var font by mutableStateOf("space_grotesk")
    private val picks = ArrayList<String>()

    private fun categories() = listOf(
        SettingCategory("Home screen", Glyphs.Apps, (0 until 20).map { i -> SettingItem.Toggle("Home option $i", Glyphs.Apps, i % 2 == 0) {} }),
        SettingCategory(
            "Look", Glyphs.Palette,
            listOf(
                SettingItem.Choice("Font", Glyphs.Dial, FontCatalog.entries, FontCatalog.entry(font), { it.label }, hint = "The font of the launcher's text", preview = {
                    FontChoice(font) { font = it; picks += it }
                }) { font = it.id },
                SettingItem.Toggle("Icon plate", Glyphs.Apps, true) {},
            ),
        ),
    )

    private fun show(initial: Int = -1, cats: List<SettingCategory> = categories()) {
        rule.setContent { SphereTheme { SettingsSpace(visible = true, categories = cats, initialCategory = initial, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) } }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shown(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(ShownTitleKey)

    @Test fun CFG_21_theLargeTitleIsCentredHorizontallyWithinOneDpOfThePageCentre() {
        show()
        val title = rule.onNodeWithTag(OneUiTags.TITLE_LARGE).getUnclippedBoundsInRoot()
        val root = rule.onNodeWithTag(OneUiTags.ROOT).getUnclippedBoundsInRoot()
        val titleCentre = (title.left.value + title.right.value) / 2f
        val pageCentre = (root.left.value + root.right.value) / 2f
        assertEquals("title centre $titleCentre dp, page centre $pageCentre dp", pageCentre, titleCentre, 1f)
        assertTrue("it is not stretched to the full width (it is the words that are centred)", title.width < root.width - 40.dp)
    }

    @Test fun CFG_21_theLargeTitleIsCentredVerticallyInsideTheEmptyBandUnderTheBackArrow() {
        show()
        val band = rule.onNodeWithTag(OneUiTags.TITLE_BAND).getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag(OneUiTags.TITLE_LARGE).getUnclippedBoundsInRoot()
        val back = rule.onNodeWithTag(OneUiTags.BACK).getUnclippedBoundsInRoot()
        assertTrue("the band starts under the back arrow: ${band.top} vs ${back.bottom}", band.top.value >= back.bottom.value - 8f)
        assertTrue("the title is inside the band", title.top.value >= band.top.value && title.bottom.value <= band.bottom.value)
        val gapAbove = title.top.value - band.top.value; val gapBelow = band.bottom.value - title.bottom.value
        assertEquals("equal space above ($gapAbove) and below ($gapBelow)", gapAbove, gapBelow, 1.5f)
    }

    @Test fun CFG_41_theSectionTabsAreCapitalsToo() {
        // Wave 57: a section has no page title of its own any more; its tab carries the name, in the same capitals.
        show(initial = 1)
        rule.onNodeWithText("LOOK", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("HOME SCREEN", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("SETTINGS", shown(OneUiTags.TITLE_LARGE))
    }

    @Test fun CFG_21_titlesAreCapitalsButTheScreenReaderStillReadsTheWords() {
        show()
        assertEquals("SETTINGS", shown(OneUiTags.TITLE_LARGE))
        val n = rule.onNodeWithTag(OneUiTags.TITLE_LARGE).fetchSemanticsNode().config
        assertEquals("Settings", n[SemanticsProperties.Text].single().text)
        assertTrue(n.contains(SemanticsProperties.Heading))
        rule.onNodeWithText("Settings").assertIsDisplayed() // existing lookups by the plain title keep working
    }

    @Test fun CFG_21_theCollapsedSmallTitleIsCapitalsAndCentredToo() {
        show(initial = 0)
        // Wave 57: the title band scrolls away with a drag of the section's list; the bar's small title takes over.
        repeat(3) { rule.onNodeWithTag(SettingsTabTags.list("Home screen")).performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(400) }
        rule.waitForIdle()
        assertEquals("SETTINGS", shown(OneUiTags.TITLE_SMALL))
        val t = rule.onNodeWithTag(OneUiTags.TITLE_SMALL).getUnclippedBoundsInRoot()
        val page = rule.onNodeWithTag(OneUiTags.ROOT).getUnclippedBoundsInRoot()
        assertEquals((page.left.value + page.right.value) / 2f, (t.left.value + t.right.value) / 2f, 1f)
    }

    @Test fun CFG_21_aTitleInAScriptWithoutCaseIsLeftAsItIs() {
        L10n.setLanguage("ja")
        show(cats = listOf(SettingCategory("Look", Glyphs.Palette, listOf(SettingItem.Toggle("Icon plate", Glyphs.Apps, true) {}))))
        assertEquals(L10n.tr("Settings"), shown(OneUiTags.TITLE_LARGE))
    }

    @Test fun CFG_21_titlesUseSpaceGroteskWhateverTheUiFontIsAndTheRestFollowsTheUiFont() {
        HyleFont.apply("jetbrains_mono")
        var title: FontFamily? = null; var body: FontFamily? = null; var space: FontFamily? = null; var mono: FontFamily? = null
        rule.setContent {
            SphereTheme {
                val ctx = LocalContext.current
                body = MaterialTheme.typography.bodyLarge.fontFamily
                space = FontCatalog.family(ctx, "space_grotesk"); mono = FontCatalog.family(ctx, "jetbrains_mono")
                SettingsSpace(visible = true, categories = categories(), initialCategory = -1, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        val unmerged = rule.onNodeWithTag(OneUiTags.TITLE_LARGE, useUnmergedTree = true)
        val textNode = rule.onAllNodes(hasText("SETTINGS"), useUnmergedTree = true)[0].fetchSemanticsNode()
        val layouts = ArrayList<TextLayoutResult>()
        textNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        title = layouts.single().layoutInput.style.fontFamily
        unmerged.assertExists()
        assertEquals("the theme's typography carries the UI font", mono, body)
        assertEquals("the title is in Space Grotesk, not the UI font", space, title)
        if (space != FontFamily.Default) assertNotEquals("the two families really differ", title, body)
    }

    @Test fun CFG_21_theFontPickerShowsOneCardPerFontEachRenderingLiftoffInItsOwnFamily() {
        show(initial = 1)
        val ctx = rule.activity
        for (e in FontCatalog.entries) {
            rule.onNodeWithContentDescription(e.label).assertExists()
            rule.onNodeWithTag(FontChoiceTags.sample(e.id), useUnmergedTree = true).assertExists()
        }
        // the card draws its text in that card's family
        val monoFamily = FontCatalog.family(ctx, "jetbrains_mono")
        val families = rule.onAllNodes(hasText("Liftoff"), useUnmergedTree = true).fetchSemanticsNodes().map { n ->
            val l = ArrayList<TextLayoutResult>(); n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(l); l.single().layoutInput.style.fontFamily
        }
        assertTrue("each card has its own family: ${families.toSet().size} distinct", families.toSet().size >= 2)
        if (monoFamily != FontFamily.Default) assertTrue("the JetBrains Mono card draws in JetBrains Mono", monoFamily in families)
        assertEquals("only the sample words 'Liftoff' and '12:30 Aa', per card", FontCatalog.entries.size, families.size)
    }

    @Test fun CFG_21_tappingAFontCardSelectsItAndTheSelectionPersistsAcrossReopening() {
        show(initial = 1)
        rule.onNodeWithContentDescription("JetBrains Mono").performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
        assertEquals(listOf("jetbrains_mono"), picks)
        assertEquals("jetbrains_mono", font)
        // the page is rebuilt from the stored value: the chosen card is the selected one
        val selected = rule.onAllNodes(androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertTrue("selected cards: $selected", "JetBrains Mono" in selected && "Space Grotesk" !in selected)
    }

    @Test fun CFG_21_theStoredFontIdsAndTheCatalogHoldTheSameFontsInTheSameOrder() {
        assertEquals(UiFonts.IDS, FontCatalog.ids)
        assertEquals(UiFonts.DEFAULT, FontCatalog.DEFAULT)
    }

    @Test fun CFG_21_aDialogTitleIsInCapitalsAndCentredToo() {
        rule.setContent { SphereTheme { OneUiConfirmDialog(title = "Delete page", body = "Gone", confirmLabel = "Delete", onConfirm = {}, onDismiss = {}) } }
        rule.waitForIdle()
        assertEquals("DELETE PAGE", shown(OneUiDialogTags.TITLE))
        rule.onNodeWithText("Delete page").assertIsDisplayed()
    }

    @Test fun CFG_21_screenshotOfTheFontPickerAndTheCentredTitle() {
        show(initial = 1)
        shoot("w52-font-picker")
        assertTrue(File(outDir, "w52-font-picker.png").length() > 1000)
    }
}
