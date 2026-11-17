package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.SearchLayout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 6 audit of PAL-20: a long-press and a right-click open the preview in Flat, Orbit and Filmstrip. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class W6RadarPreviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private val mail = AppEntry("demo.mail", ComponentName("demo.mail", "demo.mail.Main"), "Mail", Process.myUserHandle()) { null }

    private fun open(layout: SearchLayout, query: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = listOf(mail), recent = listOf(mail), prefs = LauncherPrefs(searchMotion = false, searchLayout = layout, searchLayoutPinned = true), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                // Wave 18: Orbit and Filmstrip at rest show what is used (not the recents list), so Mail has been opened a few times.
                launchHistory = RadarTestHabits.history(listOf(mail)), learnedOverride = emptyMap(), nowMillis = { RadarTestHabits.NOW },
            )
        }
        rule.mainClock.advanceTimeBy(1200)
    }

    private fun previewShown() = rule.onNodeWithText("OPEN").assertExistsOrThrow()

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsOrThrow() = assertExists()

    private fun check(layout: SearchLayout, query: String, mouse: Boolean) {
        open(layout, query)
        val tile = if (layout == SearchLayout.Flat) rule.onAllNodesWithText("Mail").onFirst() else if (layout == SearchLayout.Orbit) rule.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasContentDescription("Mail")) and androidx.compose.ui.test.hasClickAction()).onFirst() else rule.onAllNodesWithContentDescription("Mail").onFirst()
        if (mouse) tile.performMouseInput { rightClick() } else tile.performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(500)
        previewShown()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_long_press_opens_the_preview_in_flat() = check(SearchLayout.Flat, "Mail", mouse = false)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_right_click_opens_the_preview_in_flat() = check(SearchLayout.Flat, "Mail", mouse = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_long_press_opens_the_preview_in_filmstrip() = check(SearchLayout.Filmstrip, "", mouse = false)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_right_click_opens_the_preview_in_filmstrip() = check(SearchLayout.Filmstrip, "", mouse = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_long_press_opens_the_preview_in_orbit() = check(SearchLayout.Orbit, "", mouse = false)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun PAL_20_right_click_opens_the_preview_in_orbit() = check(SearchLayout.Orbit, "", mouse = true)
}
