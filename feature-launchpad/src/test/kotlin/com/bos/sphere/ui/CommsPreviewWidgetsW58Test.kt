package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.LaunchpadTheme
import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 58 (studioPreview), CFG-73: the side panel Preview shows the panel's Widgets section as placeholder cards when the section is on (no real widget
 * is hosted in the Preview), at the section's place: above the Comms column when Widgets is the first section, below it otherwise; off shows none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class CommsPreviewWidgetsW58Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun setUp() { L10n.setLanguage("en") }

    private fun show(on: Boolean, slot: WidgetsSlot) {
        rule.setContent { Box(Modifier.fillMaxSize()) { CommsPreview(LaunchpadTheme.Dark, widgets = on to slot) } }
        rule.waitForIdle()
    }
    private fun block() = rule.onAllNodesWithTag(PREVIEW_WIDGETS_TAG, useUnmergedTree = true).fetchSemanticsNodes()

    @Test fun CFG_73_theWidgetsSectionShowsAsSampleCardsBelowTheCommsColumn() {
        show(true, WidgetsSlot.Last)
        assertEquals(1, block().size)
        val d = block().single().config[SemanticsProperties.ContentDescription].joinToString()
        assertEquals("Widgets section, sample cards: All · Unread · Files · Timeline · Widgets", d)
        val top = rule.onNodeWithTag(PREVIEW_WIDGETS_TAG, useUnmergedTree = true).getBoundsInRoot().top
        assertTrue("below the Comms column (top at $top)", top > 300.dp)
        assertTrue("the sample rows are still there", rule.onAllNodesWithText(L10n.tr("Sample message"), substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun CFG_73_asTheFirstSectionItSitsAboveTheCommsColumn() {
        show(true, WidgetsSlot.First)
        assertTrue("at the top of the panel", rule.onNodeWithTag(PREVIEW_WIDGETS_TAG, useUnmergedTree = true).getBoundsInRoot().top <= 12.dp)
    }

    @Test fun CFG_73_withTheSectionOffThereAreNoWidgetCards() {
        show(false, WidgetsSlot.Last)
        assertTrue(block().isEmpty())
    }
}
