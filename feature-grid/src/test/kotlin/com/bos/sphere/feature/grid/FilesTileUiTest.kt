package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.catalog.FileThumbs
import com.bos.sphere.core.catalog.HomePanelLinks
import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.PanelTab
import com.bos.sphere.core.catalog.RecentFile
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** HOM-15: the Files tile draws its first files: thumbnails where there are any, names where not. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilesTileUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val files = ItemDef("link:files", ItemKind.Link, "Files", HomePanelLinks.uri(PanelTab.Files))

    @Test @Config(sdk = [35])
    fun HOM_15_aFileWithoutAThumbnailShowsItsNameInTheTile() {
        rule.setContent { ItemGlyphTile(files, { null }, 96.dp, fileCells = listOf(FileTileCell("report.pdf", null), FileTileCell("notes.txt", null))) }
        rule.onNodeWithText("report.pdf").assertIsDisplayed()
        rule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test @Config(sdk = [35])
    fun HOM_15_aFileWithAThumbnailDrawsTheImageAndIsNamedForAccessibility() {
        val img = ImageBitmap(8, 8)
        rule.setContent { ItemGlyphTile(files, { null }, 96.dp, fileCells = listOf(FileTileCell("photo.png", img), FileTileCell("doc.pdf", null))) }
        rule.onNodeWithContentDescription("photo.png").assertIsDisplayed()
        rule.onNodeWithText("doc.pdf").assertIsDisplayed()
    }

    @Test @Config(sdk = [35])
    fun HOM_15_withNoFilesTheTileKeepsItsPlainGlyph() {
        rule.setContent { ItemGlyphTile(files, { null }, 96.dp) }
        rule.onNodeWithText("▤").assertIsDisplayed()
    }

    @Test @Config(sdk = [35])
    fun HOM_15_theTileOnlyDrawsFourFilesAndOnlyOnTheFilesTile() {
        val many = (1..9).map { FileTileCell("f$it.txt", null) }
        rule.setContent { ItemGlyphTile(files, { null }, 96.dp, fileCells = many) }
        rule.onNodeWithText("f4.txt").assertIsDisplayed()
        rule.onNodeWithText("f5.txt").assertDoesNotExistCompat()
    }

    @Test @Config(sdk = [35])
    fun HOM_15_aProviderThatCannotGiveAThumbnailYieldsNullNotACrash() {
        val load = FileThumbs.platformLoader(ApplicationProvider.getApplicationContext())
        assertNull(load(RecentFile("content://no.such.provider/document/1", "x.png", "image/png", 1L, "content://no.such.provider")))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() {
    org.junit.Assert.assertTrue(runCatching { assertExists() }.isFailure)
}
