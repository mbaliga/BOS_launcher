package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.MediaInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** NFR-14: key rows and strips still render, show their text and stay inside the screen width at 200% font scale. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h800dp-port-xhdpi")
class FontScaleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun AtScale(scale: Float, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
            Column(Modifier.testTag("root").fillMaxWidth().padding(16.dp)) { content() }
        }
    }

    private val longLabel = "Show the weather and the next calendar event on the clock"

    @Test fun NFR_14_toggleRowRendersAtTwoHundredPercent() {
        rule.setContent { AtScale(2f) { ToggleRow(longLabel, true, {}, hint = "Uses approximate location") } }
        rule.onNodeWithText(longLabel).assertIsDisplayed()
        rule.onNodeWithText("Uses approximate location").assertIsDisplayed()
        rule.onNodeWithTag("root").assertWidthIsEqualTo(411.dp)
    }

    @Test fun NFR_14_textGrowsInsteadOfBeingClippedAtTwoHundredPercent() {
        var scale by mutableFloatStateOf(1f)
        rule.setContent { AtScale(scale) { ToggleRow(longLabel, true, {}); ActionRow("Language", summary = "English") {} } }
        rule.waitForIdle()
        val b1 = rule.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val normal = (b1.bottom - b1.top).value
        scale = 2f
        rule.waitForIdle()
        val b2 = rule.onNodeWithTag("root").getUnclippedBoundsInRoot()
        val big = (b2.bottom - b2.top).value
        assertTrue("height at 200% ($big) should exceed 100% ($normal)", big > normal)
        rule.onNodeWithText(longLabel).assertIsDisplayed()
    }

    @Test fun NFR_14_mediaStripRendersAtTwoHundredPercent() {
        rule.setContent {
            AtScale(2f) {
                MediaStrip(MediaInfo("A Really Long Track Title That Must Be Cut Off", "An Artist", "demo", playing = true, canPrev = true, canNext = true), {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("root").assertWidthIsEqualTo(411.dp)
        val b = rule.onNodeWithTag("root").getUnclippedBoundsInRoot()
        assertTrue((b.bottom - b.top).value >= 52f)
    }

    @Test fun NFR_14_stepperAndActionRowShowTheirLabelsAtTwoHundredPercent() {
        rule.setContent { AtScale(2f) { Stepper("Columns", 4, 2..8) {}; ActionRow("Backup and restore", summary = "Export") {} } }
        rule.onNodeWithText("Columns").assertIsDisplayed()
        rule.onNode(hasText("Backup and restore")).assertIsDisplayed()
    }
}
