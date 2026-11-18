package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode

/** INP-13 (wave 23 sweep): the microphone is released when the screen pauses, even if the press was never released (a switch of app while listening). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoicePauseW23UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private class Rec : OnDeviceRecogniser {
        var starts = 0; var stops = 0
        override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) { starts++ }
        override fun stop() { stops++ }
        override fun release() = Unit
    }

    @Test @org.robolectric.annotation.Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi") fun INP_13_pausingTheScreenStopsAListeningMicrophone() {
        Shadows.shadowOf(rule.activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val rec = Rec()
        val owner = object : LifecycleOwner { val reg = LifecycleRegistry.createUnsafe(this); override val lifecycle: Lifecycle get() = reg }
        owner.reg.currentState = Lifecycle.State.RESUMED
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { PushToTalkButton(onText = {}, availableOverride = true, recogniserOverride = rec) }
        }
        rule.onNodeWithContentDescription("Speak to search").performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals(1, rec.starts)
        assertEquals(0, rec.stops)
        rule.runOnUiThread { owner.reg.currentState = Lifecycle.State.STARTED } // RESUMED -> STARTED is ON_PAUSE
        rule.waitForIdle()
        assertTrue("the microphone was still open after the screen paused", rec.stops >= 1)
    }
}
