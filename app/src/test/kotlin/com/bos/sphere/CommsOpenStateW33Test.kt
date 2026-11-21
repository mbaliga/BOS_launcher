package com.bos.sphere

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bos.sphere.ui.HubPrefs
import com.bos.sphere.ui.HubState
import com.bos.sphere.ui.HubView
import com.bos.sphere.ui.ReadFilter
import com.bos.sphere.ui.hubStateSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W33 (commsDebt): Comms stays open under an opened app; if the launcher is killed meanwhile it comes back open, and a Home press closes it. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class CommsOpenStateW33Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun HOM_W33_01_theCommsOpenStateSurvivesTheActivityBeingRecreated() {
        val tester = StateRestorationTester(rule)
        var open: androidx.compose.runtime.MutableState<Boolean>? = null
        tester.setContent { open = rememberCommsOpen(0); Text(if (open!!.value) "open" else "closed") }
        rule.runOnIdle { assertFalse(open!!.value); open!!.value = true }
        rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertTrue("restored open", open!!.value) }
    }

    @Test fun HOM_W33_02_aHomePressClosesCommsAndTheClosedStateIsWhatIsSaved() {
        val tester = StateRestorationTester(rule)
        var signal by mutableIntStateOf(0)
        var open: androidx.compose.runtime.MutableState<Boolean>? = null
        tester.setContent { open = rememberCommsOpen(signal); Text("x") }
        rule.runOnIdle { open!!.value = true }
        rule.runOnIdle { signal = 1 }; rule.waitForIdle()
        rule.runOnIdle { assertFalse("Home closed it", open!!.value) }
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertFalse(open!!.value) }
    }

    @Test fun HOM_W33_03_aNonZeroSignalAtFirstCompositionIsNotAPressSoARestoredCommsStaysOpen() {
        val tester = StateRestorationTester(rule)
        var open: androidx.compose.runtime.MutableState<Boolean>? = null
        tester.setContent { open = rememberCommsOpen(3); Text("x") }
        rule.runOnIdle { open!!.value = true }; rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertTrue(open!!.value) }
    }

    @Test fun HOM_W33_04_theCommsTabAndFilterSurviveTheRecreationToo() {
        val tester = StateRestorationTester(rule)
        var hub: HubState? = null
        tester.setContent {
            hub = androidx.compose.runtime.saveable.rememberSaveable(saver = hubStateSaver { HubState(HubPrefs(null), null, true, liveAll = true) }) { HubState(HubPrefs(null), null, true, liveAll = true) }
            Text("x")
        }
        rule.runOnIdle { hub!!.select(HubView.Unread); hub!!.chooseReadFilter(ReadFilter.Read) }; rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(HubView.Unread, hub!!.view); assertEquals(ReadFilter.Read, hub!!.readFilter) }
    }
}
