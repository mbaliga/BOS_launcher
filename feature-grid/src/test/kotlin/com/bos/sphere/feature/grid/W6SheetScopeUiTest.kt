package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bos.sphere.core.input.KeyLabels
import com.bos.sphere.core.input.ShortcutRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 6 audit: the sheet states where launcher shortcuts apply (INP-04 constraint) and that Meta chords are the system's. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class W6SheetScopeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_04_the_shortcut_sheet_says_shortcuts_apply_only_while_a_launcher_surface_has_focus() {
        rule.setContent { ShortcutSheet(ShortcutRegistry(), onDismiss = {}, reload = {}, save = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(KeyLabels.SCOPE_NOTE).assertExists()
        rule.onNodeWithText(KeyLabels.META_NOTE).assertExists()
    }
}
