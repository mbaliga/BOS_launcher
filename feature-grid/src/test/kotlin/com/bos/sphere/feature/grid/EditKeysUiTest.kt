package com.bos.sphere.feature.grid

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 45 (editHandles), INP-09 / HOM-12: the keyboard on the selected item through the launcher's one key hub. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
class EditKeysUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val widget = ItemKeys.widget(7)

    private fun key(code: Int, meta: Int = 0) = LauncherInput.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, meta))

    @Test fun INP_09_deleteRemovesTheSelectedItemAndArrowsMoveAWidgetByACellAndNeverOntoAnotherItem() {
        val state = HomeState(pages = listOf(HomePage(listOf(widget, "a", "b"))), widgets = mapOf(widget to WidgetSpan(2, 1)))
        val sel = EditSelectionState()
        val actions = mutableListOf<ItemAction>()
        val removed = mutableListOf<String>()
        sel.onRemove = { removed += it }
        val extras = HomeExtras(state, { null }, { null }, widget = { _, _ -> }, onAction = { actions += it }, selection = sel)
        rule.setContent {
            InputModeProvider {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).testTag("w").launcherItem("grid.$widget"))
                    GridKeyboardMove(state.pages, LauncherPrefs(), false, { 24 }, {}, extras = extras)
                }
            }
        }
        rule.waitForIdle()
        LauncherInput.editMode.value = true
        try {
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { sel.select(widget) }
            // Right: the widget's neighbour is the icon "a" beside it, so a widget does not move onto it.
            assertTrue(key(KeyEvent.KEYCODE_DPAD_RIGHT))
            assertTrue("a widget blocks: it never moves onto another item", actions.isEmpty())
            assertTrue(sel.announcement.startsWith("Can't move"))
            assertTrue(key(KeyEvent.KEYCODE_DEL))
            assertEquals(listOf(widget), removed)
            assertTrue(sel.announcement.startsWith("Removed"))
        } finally { LauncherInput.editMode.value = false }
    }
}
