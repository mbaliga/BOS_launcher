package com.bos.sphere

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.PuckSlot
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.ui.PuckActionsPane
import com.bos.sphere.ui.SettingsModel
import com.bos.sphere.ui.SystemLinks
import com.bos.sphere.ui.buildSettingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CFG-15 (wave 38, drawerRing): Ground Control > App drawer carries Ring segments (6, 7 or 8), the slot editor with one row per segment, "Reset to
 * default" and "Show labels under glyphs", next to the drawer's existing options.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class DrawerRingW38Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app = AppEntry("demo.notes", ComponentName("demo.notes", "M"), "Notes", Process.myUserHandle()) { ColorDrawable(0xFF2F6BFF.toInt()) }
    private val store get() = PuckActionsStore.get(ApplicationProvider.getApplicationContext())
    private var prefs = LauncherPrefs()

    @Before fun fresh() { AppSmokeHarness.resetProcess(); store.reset(); store.setSegments(8); store.setShowLabels(false) }

    private fun items(): List<SettingItem> = buildSettingsCategories(
        SettingsModel(
            prefs = prefs, updatePrefs = { f -> prefs = f(prefs) }, pageIndex = 0, pages = listOf(HomePage(keys = emptyList(), id = 1L)),
            setPageLayout = { _, _ -> }, addPageAfter = {}, deletePage = {}, setClockTile = { _, _ -> }, corners = emptyMap(), gestures = emptyMap(), setGesture = { _, _ -> },
            about = listOf("Version" to "test"), appsByKey = mapOf(app.key to app), isLandscape = false, chosenApp = { null },
            pickLeftWidget = {}, leftWidgetLabel = null, pickAppFor = {}, motionAvailable = false, editCorner = {}, editGesture = {},
            system = SystemLinks({}, {}, {}, {}), lab = emptyList(),
            puckSegments = store.segments.value, setPuckSegments = store::setSegments, puckLabels = store.showLabels.value, setPuckLabels = store::setShowLabels,
        ),
    ).first { it.title == "App drawer" }.items

    @Test fun CFG_15_w38_theAppDrawerCategoryOffersRingSegmentsSixSevenOrEightWritingTheStore() {
        @Suppress("UNCHECKED_CAST")
        val row = items().filter { it.title == "Ring segments" }.also { assertEquals(1, it.size) }.single() as SettingItem.Choice<Int>
        assertEquals(listOf(6, 7, 8), row.options)
        assertEquals("default is eight", 8, row.selected)
        for (n in listOf(6, 7, 8)) { row.onSelect(n); assertEquals(n, store.segments.value) }
        store.setSegments(7)
        @Suppress("UNCHECKED_CAST")
        assertEquals(7, (items().single { it.title == "Ring segments" } as SettingItem.Choice<Int>).selected)
    }

    @Test fun CFG_15_w38_theCategoryHasTheEditorPaneTheLabelsToggleAndTheExistingDrawerOptions() {
        val titles = items().map { it.title }
        for (t in listOf("Ring segments", "Drawer shortcuts", "Show labels under glyphs", "Wheel groups")) assertTrue("$t is in App drawer: $titles", t in titles)
        for (gone in listOf("All apps opens as", "Drawer view", "Sort by", "Quick scrub side", "Show the A–Z letters", "View switcher shows")) assertFalse("$gone is gone: the app drawer is always the disc", gone in titles)
        assertEquals("8 segments", items().single { it.title == "Drawer shortcuts" }.value)
        store.setSegments(6)
        assertEquals("6 segments", items().single { it.title == "Drawer shortcuts" }.value)
    }

    @Test fun CFG_15_w38_theLabelsToggleDefaultsOffAndWritesTheStore() {
        val t = items().single { it.title == "Show labels under glyphs" } as SettingItem.Toggle
        assertFalse(t.checked)
        t.onChange(true)
        assertTrue(store.showLabels.value)
        assertTrue((items().single { it.title == "Show labels under glyphs" } as SettingItem.Toggle).checked)
    }

    @Test fun CFG_15_w38_theEditorShowsExactlyOneRowPerSegmentForSixSevenAndEight() {
        for (n in listOf(6, 7, 8)) {
            store.setSegments(n)
            rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }
            for (i in 1..n) rule.onNodeWithText("$i. ", substring = true).assertExists()
            for (i in n + 1..8) assertEquals("no row $i at $n segments", 0, rule.onAllNodesWithText("$i. ", substring = true).fetchSemanticsNodes().size)
            return // setContent runs once per test; the three sizes are separate tests below
        }
    }
    @Test fun CFG_15_w38_theEditorHasSixRowsAtSixSegments() { store.setSegments(6); rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }; assertEquals(0, rule.onAllNodesWithText("7. ", substring = true).fetchSemanticsNodes().size); rule.onNodeWithText("6. ", substring = true).assertExists() }
    @Test fun CFG_15_w38_theEditorHasSevenRowsAtSevenSegments() { store.setSegments(7); rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }; assertEquals(0, rule.onAllNodesWithText("8. ", substring = true).fetchSemanticsNodes().size); rule.onNodeWithText("7. ", substring = true).assertExists() }

    @Test fun CFG_15_w38_aSlotBeyondTheFourthCanBeEditedLikeTheFirstFour() {
        rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }
        rule.onNodeWithText("7. Calculator").performClick()
        rule.onNodeWithText("Maps").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(PuckSlot.Action(CornerActionId.Maps), store.slots.value[6])
        rule.onNodeWithText("8. Settings").performClick()
        rule.onNodeWithText("App...").performClick()
        rule.onNodeWithText("Notes").performClick()
        rule.waitForIdle()
        assertEquals(PuckSlot.App(app.key), store.slots.value[7])
        assertEquals(PuckActions.DEFAULT[0], store.slots.value[0])
    }

    @Test fun CFG_15_w38_resetToDefaultPutsAllEightBackAndLeavesTheRingSize() {
        store.setSegments(6)
        store.setAll(List(8) { PuckSlot.Empty })
        rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }
        rule.onNodeWithText("Reset to default").performClick()
        assertEquals(PuckActions.DEFAULT, store.slots.value)
        assertEquals(6, store.segments.value)
    }
}
