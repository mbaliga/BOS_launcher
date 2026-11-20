package com.bos.sphere.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HapticStrength
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 27 (soundPopup): the Sound and Vibration sheet with a fake [VolumeController]. Screenshots go to
 * build/screenshots/sound-vibration-*.png (compare with the owner's mock-up).
 */
@OptIn(ExperimentalTestApi::class)
abstract class SoundVibrationSheetCases(private val orientation: String) : LaunchpadShots() {
    private val volumes = FakeVolumes()
    private val haptics = FakeHaptics(HapticStrength.Light)
    private var dismissed = 0

    @After fun reset() { ReducedMotionTesting.force(null) }

    private fun show(v: FakeVolumes = volumes) {
        rule.setContent { Themed(true) { SoundVibrationSheet(onDismiss = { dismissed++ }, volumes = v, haptics = haptics) } }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)

    @Test fun SND_30_w37_theTitleUsesTheSharedPopupTitleTokenOf28spLightCentred() {
        show()
        val out = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        tag("snd.title").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        val st = out[0].layoutInput.style
        assertEquals(com.bos.sphere.core.design.PopupTokens.TitleSize, st.fontSize); assertEquals(28f, st.fontSize.value, 0f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Light, st.fontWeight)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, st.textAlign)
    }

    @Test fun SND_20_theSheetSitsAtTheBottomWithTheTitleAndFourSlidersOf48dp() {
        show()
        rule.onNodeWithText("Sound and Vibration").assertIsDisplayed()
        val root = rule.onRoot().getUnclippedBoundsInRoot(); val sheet = tag("snd.sheet").getUnclippedBoundsInRoot()
        assertTrue("sheet bottom ${sheet.bottom} is the screen bottom ${root.bottom}", (root.bottom - sheet.bottom).value < 56f)
        assertTrue("sheet stays on screen", sheet.top >= root.top)
        for (k in SoundRowKind.entries) {
            val b = tag("snd.slider.${k.name.lowercase()}").getUnclippedBoundsInRoot()
            assertTrue("${k.name} slider is at least 48 dp tall: $b", (b.bottom - b.top) >= 48.dp)
        }
        tag("snd.divider").assertIsDisplayed()
        rule.onNodeWithText("Music, Video and Games").assertIsDisplayed()
        rule.onNodeWithText("Alarms").assertIsDisplayed()
        rule.onNodeWithText("Launcher vibration").assertIsDisplayed()
        shoot("sound-vibration-$orientation")
    }

    @Test fun SND_21_draggingAThumbToTheRightEdgeSetsTheMaximumAndTheVolumeIsWritten() {
        show()
        tag("snd.slider.media").performTouchInput { down(center); moveTo(androidx.compose.ui.geometry.Offset(right - 1f, centerY)); up() }
        rule.waitForIdle()
        assertEquals(15, volumes.volumes[SoundRowKind.Media])
    }

    @Test fun SND_22_tappingTheTrackJumpsToThatPosition() {
        show()
        tag("snd.slider.alarms").performTouchInput { down(androidx.compose.ui.geometry.Offset(left + 1f, centerY)); up() }
        rule.waitForIdle()
        assertEquals(0, volumes.volumes[SoundRowKind.Alarms])
    }

    @Test fun SND_23_arrowKeysStepAndHomeAndEndJump() {
        show()
        tag("snd.slider.media").requestFocus()
        tag("snd.slider.media").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle(); assertEquals(9, volumes.volumes[SoundRowKind.Media])
        tag("snd.slider.media").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        tag("snd.slider.media").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle(); assertEquals(7, volumes.volumes[SoundRowKind.Media])
        tag("snd.slider.media").performKeyInput { pressKey(Key.MoveEnd) }
        rule.waitForIdle(); assertEquals(15, volumes.volumes[SoundRowKind.Media])
        tag("snd.slider.media").performKeyInput { pressKey(Key.MoveHome) }
        rule.waitForIdle(); assertEquals(0, volumes.volumes[SoundRowKind.Media])
    }

    @Test fun SND_24_talkBackSeesProgressWithPercentAndCanSetItAndStepIt() {
        show()
        val row = rule.onNodeWithTag("snd.row.media")
        val node = row.fetchSemanticsNode()
        val info = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertNotNull(info); assertEquals(8f, info!!.current, 0f); assertEquals(0f..15f, info.range); assertEquals(14, info.steps)
        assertEquals("53 percent", node.config.getOrNull(SemanticsProperties.StateDescription))
        assertTrue(node.config.contains(SemanticsActions.SetProgress))
        row.performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        rule.waitForIdle()
        assertEquals(12, volumes.volumes[SoundRowKind.Media])
        assertEquals("80 percent", rule.onNodeWithTag("snd.row.media").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun SND_25_theVibrationRowChangesTheLauncherHapticStrengthAndSaysSo() {
        show()
        assertEquals("Light", rule.onNodeWithTag("snd.row.vibration").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        tag("snd.row.vibration").performScrollTo()
        rule.onNodeWithTag("snd.row.vibration").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        rule.waitForIdle()
        assertEquals(HapticStrength.Strong, haptics.strength)
        tag("snd.second.vibration").assertIsDisplayed()
    }

    @Test fun SND_26_aDoNotDisturbLockedNotificationRowIsDisabledWithTheReasonAndStaysAlive() {
        val locked = FakeVolumes(locked = setOf(SoundRowKind.Notifications))
        show(locked)
        rule.onNodeWithText("Do Not Disturb is on").assertIsDisplayed()
        rule.onNodeWithTag("snd.row.notifications").assertIsNotEnabled()
        rule.onNodeWithTag("snd.row.notifications").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        tag("snd.slider.notifications").performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(2, locked.volumes[SoundRowKind.Notifications])
        assertTrue(locked.writes.none { it.first == SoundRowKind.Notifications })
        tag("snd.slider.media").assertIsDisplayed()
        shoot("sound-vibration-dnd-$orientation")
    }

    @Test fun SND_27_aSecurityExceptionOnTheFirstChangeTurnsTheRowIntoTheDisabledOne() {
        val v = FakeVolumes(locked = setOf(SoundRowKind.Notifications), throwing = true)
        show(v)
        rule.onNodeWithTag("snd.row.notifications").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        rule.waitForIdle()
        rule.onNodeWithText("Do Not Disturb is on").assertIsDisplayed()
        rule.onNodeWithTag("snd.row.notifications").assertIsNotEnabled()
    }

    @Test fun SND_28_escapeTheScrimAndBackAllDismiss() {
        show()
        tag("snd.root").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle(); assertEquals(1, dismissed)
        tag("snd.scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }
        rule.waitForIdle(); assertEquals(2, dismissed)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle(); assertEquals(3, dismissed)
        tag("snd.sheet").performClick() // a tap inside the sheet does not reach the scrim
        rule.waitForIdle(); assertEquals(3, dismissed)
    }

    @Test fun SND_29_aDragDownOnTheSheetDismissesIt() {
        show()
        tag("snd.title").performTouchInput { swipeDown(startY = centerY, endY = centerY + 500f, durationMillis = 300) }
        rule.waitForIdle(); assertEquals(1, dismissed)
    }

    @Test fun SND_30_withAnimationsOffTheSheetAppearsWithoutASlide() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        rule.setContent { Themed(true) { SoundVibrationSheet(onDismiss = {}, volumes = volumes, haptics = haptics) } }
        rule.mainClock.advanceTimeBy(250); rule.waitForIdle()
        val root = rule.onRoot().getUnclippedBoundsInRoot(); val sheet = tag("snd.sheet").getUnclippedBoundsInRoot()
        assertTrue("faded in at its final place", (root.bottom - sheet.bottom).value < 56f)
    }

    @Test fun SND_31_theCommsClockOpensTheSheet() {
        rule.setContent { Themed(true) { Body(grouped) } }
        rule.waitForIdle()
        assertFalse(rule.onAllNodesWithTagExists("snd.sheet"))
        rule.onNodeWithTag("lp.clock").performClick()
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        tag("snd.sheet").assertIsDisplayed()
        shoot("sound-vibration-over-comms-$orientation")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(t: String) =
        androidx.compose.ui.test.SemanticsNodeInteractionCollection::class.java.let { onAllNodes(androidx.compose.ui.test.hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
}
