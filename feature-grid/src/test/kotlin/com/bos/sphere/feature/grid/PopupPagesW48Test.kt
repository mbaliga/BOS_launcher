package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 48 (popupPages), CFG-21: the editors and the single app picker are full One UI pages (the large title in its band under the back
 * arrow, no popup card), the footer buttons stay under the content, back leaves without applying.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PopupPagesW48Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val log = ArrayList<String>()
    private fun app(label: String) = AppEntry("demo.$label", ComponentName("demo.$label", "M"), label, Process.myUserHandle()) { ColorDrawable(0xFF2F6BFF.toInt()) }

    @Before fun setUp() { L10n.setLanguage("en"); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }

    @Test fun CFG_21_w48_anEditorIsAPageWithALargeTitleAndNoDialogCard() {
        rule.setContent { GlassDialog("Drawer shortcut 1", { log += "back" }, footer = { TextButtonPill("Save", primary = true) { log += "save" } }) { androidx.compose.material3.Text("BODY") } }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.EDITOR_PAGE).assertIsDisplayed()
        rule.onNodeWithTag(OneUiTags.TITLE_LARGE).assertIsDisplayed()
        assertEquals("no popup card", 0, rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).fetchSemanticsNodes().size)
        rule.onNodeWithText("BODY").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf("save"), log)
    }

    @Test fun CFG_21_w48_theBackArrowOfAnEditorLeavesWithoutSaving() {
        rule.setContent { GlassDialog("Gesture", { log += "back" }, footer = { TextButtonPill("Save") { log += "save" } }) { androidx.compose.material3.Text("BODY") } }
        rule.onNodeWithTag(OneUiTags.BACK).performClick()
        assertEquals(listOf("back"), log)
    }

    @Test fun CFG_21_w48_theSingleAppPickerIsAPageWithSearchAndPicksOneApp() {
        val picked = ArrayList<String>()
        rule.setContent { AppPickerDialog("Music app", listOf(app("Alpha"), app("Bravo")), suggested = setOf("demo.Bravo"), onPick = { picked += it.label }, onDismiss = { log += "dismiss" }, resetLabel = "Ask me each time", onReset = { log += "reset" }) }
        rule.waitForIdle()
        rule.onNodeWithTag(OneUiDialogTags.EDITOR_PAGE).assertIsDisplayed()
        rule.onNodeWithText("SUGGESTED").assertExists()
        rule.onNodeWithText("Search apps").assertExists()
        rule.onNodeWithText("Bravo").performClick()
        assertEquals(listOf("Bravo"), picked)
        rule.onNodeWithText("Ask me each time").performClick()
        assertEquals(listOf("reset"), log)
        assertTrue(rule.onAllNodesWithTag(OneUiDialogTags.DIALOG).fetchSemanticsNodes().isEmpty())
    }
}
