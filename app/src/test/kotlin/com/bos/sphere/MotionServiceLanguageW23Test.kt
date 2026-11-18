package com.bos.sphere

import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.UiLanguage
import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CFG-19, INP-15 (wave 23 second review): the motion-gesture service applies the chosen app language itself. A sticky restart or a boot
 * brings the process up with only the service, so its toasts and its notification followed the system language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotionServiceLanguageW23Test {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @org.junit.Before fun freshProcess() { AppSmokeHarness.resetProcess() }

    @After fun reset() {
        LauncherStores.get(app).prefs.update { it.copy(language = UiLanguage.System) }
        L10n.setLanguage("en")
        AppSmokeHarness.resetProcess()
    }

    @Test fun CFG_19_theServiceAloneAppliesTheChosenLanguage() {
        LauncherStores.get(app).prefs.update { it.copy(language = UiLanguage.German) }
        L10n.setLanguage("") // a process that only the service started: no activity applied the language
        val service = Robolectric.buildService(MotionGestureService::class.java).create().startCommand(0, 1).get()
        assertEquals("de", L10n.effectiveLanguage())
        val n = shadowOf(service).lastForegroundNotification
        assertEquals("Bewegungsgesten an", n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        val channel = app.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel("motion")
        assertEquals("Bewegungsgesten", channel.name.toString())
        service.onDestroy()
    }
}
