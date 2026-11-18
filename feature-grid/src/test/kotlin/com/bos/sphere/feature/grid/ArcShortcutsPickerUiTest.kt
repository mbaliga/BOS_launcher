package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W28 arcShortcuts: the shortcut picker shows 0 to 4 slots, adds, removes, reorders, and stops at four. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcShortcutsPickerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val apps = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot").mapIndexed { i, n ->
        AppEntry("p.$i", ComponentName("p.$i", "M"), n, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt()) }
    }
    private var keys by mutableStateOf(emptyList<String>())

    private fun show(initial: List<String> = emptyList()) {
        keys = initial
        rule.setContent { Column { ArcShortcutsPicker(apps, keys, onChange = { keys = it }) } }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_20_the_picker_starts_empty_with_four_empty_slots_and_adds_up_to_four() {
        show()
        rule.onNodeWithText("Empty slot 1").assertExists(); rule.onNodeWithText("Empty slot 4").assertExists()
        for (n in listOf("Alpha", "Bravo", "Charlie", "Delta")) { rule.onNodeWithText(n).performClick(); rule.waitForIdle() }
        assertEquals(apps.take(4).map { it.key }, keys)
        rule.onNodeWithText("The wheel holds at most 4 shortcuts.").assertExists()
        rule.onNodeWithText("Echo").assertDoesNotExist() // nothing left to add: the list is gone once four are chosen
        assertEquals(4, keys.size)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_20_a_chosen_shortcut_can_move_later_and_be_removed() {
        show(apps.take(3).map { it.key })
        rule.onNodeWithText("Alpha").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Move later").performClick(); rule.waitForIdle()
        assertEquals(listOf(apps[1].key, apps[0].key, apps[2].key), keys)
        rule.onNodeWithText("Remove").performClick(); rule.waitForIdle()
        assertEquals(listOf(apps[1].key, apps[2].key), keys)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_20_more_than_four_stored_keys_show_only_four() {
        show(apps.map { it.key })
        rule.onNodeWithText("Delta").assertExists()
        rule.onNodeWithText("Echo").assertDoesNotExist()
    }
}
