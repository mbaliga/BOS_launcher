package com.bos.sphere.core.quality

import android.app.Application
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionPrefsReaderTest {
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test fun NFR_14_readsTheSystemAnimationScales() {
        val r = app.contentResolver
        Settings.Global.putFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertEquals(MorphMode.CrossFade, MotionPrefs.read(r).morphMode)
        Settings.Global.putFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(r, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        assertEquals(MorphMode.Morph, MotionPrefs.read(r).morphMode)
    }
}
