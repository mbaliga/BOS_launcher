package com.bos.sphere

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.PuckActions
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.PuckSlot
import com.bos.sphere.ui.PuckActionsPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CFG-15 (wave 30, puckSections; wave 38 drawerRing: eight slots, N rows shown): the Drawer shortcuts editor, slot rows with an action list, an app picker and Empty. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PuckActionsPaneW30Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app = AppEntry("demo.notes", ComponentName("demo.notes", "M"), "Notes", Process.myUserHandle()) { ColorDrawable(0xFF2F6BFF.toInt()) }
    private val store get() = PuckActionsStore.get(ApplicationProvider.getApplicationContext())

    @Before fun fresh() {
        AppSmokeHarness.resetProcess()
        store.reset()
        rule.setContent { Column { PuckActionsPane(mapOf(app.key to app)) } }
    }

    @Test fun CFG_15_w30_theEditorListsOneRowPerSegmentWithTheDefaultsInThem() {
        for (i in 1..8) rule.onNodeWithText("$i.", substring = true).assertExists()
        rule.onNodeWithText("1. Flashlight").assertExists()
        rule.onNodeWithText("4. Do not disturb").assertExists()
    }

    @Test fun CFG_15_w30_choosingAnActionForASlotChangesOnlyThatSlot() {
        rule.onNodeWithText("2. Clock").performClick()
        rule.onNodeWithText("Maps").performClick()
        rule.waitForIdle()
        assertEquals(PuckSlot.Action(CornerActionId.Maps), store.slots.value[1])
        assertEquals(PuckActions.DEFAULT[0], store.slots.value[0])
        rule.onNodeWithText("2. Maps").assertExists()
    }

    @Test fun CFG_15_w30_aSlotCanBeRadarAnAppOrEmpty() {
        rule.onNodeWithText("1. Flashlight").performClick()
        rule.onNodeWithText("Radar").performClick()
        assertEquals(PuckSlot.Action(CornerActionId.Search), store.slots.value[0])
        rule.onNodeWithText("3. Notifications").performClick()
        rule.onNodeWithText("App...").performClick()
        rule.onNodeWithText("Notes").performClick()
        rule.waitForIdle()
        assertEquals(PuckSlot.App(app.key), store.slots.value[2])
        rule.onNodeWithText("4. Do not disturb").performClick()
        rule.onNodeWithText("Empty").performClick()
        assertEquals(PuckSlot.Empty, store.slots.value[3])
        rule.onNodeWithText("4. Empty").assertExists()
    }

    @Test fun CFG_15_w30_resetPutsTheDefaultsBack() {
        store.setAll(listOf(PuckSlot.Empty, PuckSlot.Empty, PuckSlot.Empty, PuckSlot.Empty))
        rule.waitForIdle()
        rule.onNodeWithText("Reset to default").performClick()
        assertTrue(store.slots.value == PuckActions.DEFAULT)
    }
}
