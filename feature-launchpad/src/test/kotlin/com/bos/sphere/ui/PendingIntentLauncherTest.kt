package com.bos.sphere.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.HandOff
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.PendingIntentSender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STR-02: a notification's own PendingIntent is sent with the sender's background-start opt-in (Android 14 and later), "no exception"
 * is not read as "opened", and a failed send falls back to the app, and failing that keeps Launchpad open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingIntentLauncherTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val original = PendingIntentLauncher.sender

    @After fun restore() { PendingIntentLauncher.sender = original }

    private fun pi() = PendingIntent.getActivity(app, 1, Intent("demo.OPEN").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)

    private class Recorder(val fail: Exception? = null) : PendingIntentSender {
        val calls = ArrayList<Bundle?>()
        override fun send(pi: PendingIntent, context: Context?, fill: Intent?, options: Bundle?) {
            calls += options
            fail?.let { throw it }
        }
    }

    private fun note(pi: PendingIntent?, auto: Boolean = false, ongoing: Boolean = false) = HubNotification(
        id = "k", key = "k", source = "x", title = "Garv", summary = "Liked your status", timestamp = 1L,
        packageName = "demo.chat", appLabel = "Chat", contentIntent = pi, autoCancel = auto, isOngoing = ongoing,
    )

    private class Seen { var fallback = 0; var closed = 0; var read = 0; val cancelled = ArrayList<String>() }

    private fun open(n: HubNotification, seen: Seen, fallbackWorks: Boolean = true) = LaunchpadOpen.open(
        n, app, launchApp = { seen.fallback++; fallbackWorks }, markRead = { seen.read++ }, cancel = { seen.cancelled += it }, close = { seen.closed++ },
    )

    @Test fun STR_02_options_optInOnApi34() {
        val b = PendingIntentLauncher.options(34)
        assertNotNull(b)
        assertTrue("the bundle carries the background-start mode: ${b!!.keySet().toList()}", b.keySet().any { it.contains("background", true) })
    }

    @Test fun STR_02_options_noneBelowApi34() { assertNull(PendingIntentLauncher.options(33)) }

    @Test fun STR_02_fire_sendsWithTheOptInBundle() {
        val r = Recorder(); PendingIntentLauncher.sender = r
        assertTrue(PendingIntentLauncher.fire(pi(), app))
        assertEquals(1, r.calls.size)
        assertNotNull("API 34 sends with the opt-in", r.calls[0])
    }

    @Test fun STR_02_open_successDoesNotCallTheFallback() {
        PendingIntentLauncher.sender = Recorder()
        val seen = Seen()
        assertEquals(HandOff.Sent, open(note(pi()), seen))
        assertEquals(0, seen.fallback); assertEquals(1, seen.closed); assertEquals(1, seen.read)
    }

    @Test fun STR_02_open_canceledIntentFallsBackToTheApp() {
        PendingIntentLauncher.sender = Recorder(PendingIntent.CanceledException())
        val seen = Seen()
        assertEquals(HandOff.FellBack, open(note(pi()), seen))
        assertEquals(1, seen.fallback); assertEquals(1, seen.closed)
    }

    @Test fun STR_02_open_securityExceptionFallsBackToo() {
        PendingIntentLauncher.sender = Recorder(SecurityException("blocked"))
        val seen = Seen()
        assertEquals(HandOff.FellBack, open(note(pi()), seen))
    }

    @Test fun STR_02_open_noIntentGoesStraightToTheApp() {
        PendingIntentLauncher.sender = Recorder()
        val seen = Seen()
        assertEquals(HandOff.FellBack, open(note(null), seen))
        assertEquals(1, seen.fallback)
    }

    @Test fun STR_02_open_closeHappensOnlyAfterASuccessfulHandOff() {
        PendingIntentLauncher.sender = Recorder(PendingIntent.CanceledException())
        val seen = Seen()
        assertEquals(HandOff.Failed, open(note(pi()), seen, fallbackWorks = false))
        assertEquals("a failed hand-off keeps Comms open", 0, seen.closed)
        assertEquals("but the item is still marked read", 1, seen.read)
        assertFalse(HandOff.Failed.ok)
    }

    @Test fun STR_02_open_autoCancelCancelsOnlyWhenSent() {
        PendingIntentLauncher.sender = Recorder()
        val a = Seen(); open(note(pi(), auto = true), a)
        assertEquals(listOf("k"), a.cancelled)
        val b = Seen(); open(note(pi(), auto = false), b)
        assertTrue(b.cancelled.isEmpty())
        val c = Seen(); open(note(pi(), auto = true, ongoing = true), c)
        assertTrue("an ongoing notification is never cancelled by a tap", c.cancelled.isEmpty())
        PendingIntentLauncher.sender = Recorder(PendingIntent.CanceledException())
        val d = Seen(); open(note(pi(), auto = true), d)
        assertTrue("a fallback launch is not the intent being used", d.cancelled.isEmpty())
    }

    @Test fun STR_02_fire_missingIntentIsAFailure() { assertFalse(PendingIntentLauncher.fire(null)) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PendingIntentLauncherSdk35Test {
    @Test fun STR_02_options_optInOnApi35() { assertNotNull(PendingIntentLauncher.options()) }
}

/** The real platform send on API 34 with the opt-in bundle must not throw for a plain activity PendingIntent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingIntentLauncherPlatformTest {
    @Test fun STR_02_platformSenderAcceptsTheBundle() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pi = PendingIntent.getActivity(app, 2, Intent("demo.OPEN").setPackage(app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        assertTrue(PendingIntentLauncher.fire(pi, app))
    }
}
