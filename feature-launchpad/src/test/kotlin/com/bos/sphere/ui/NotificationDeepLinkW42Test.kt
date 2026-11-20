package com.bos.sphere.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.HandOff
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.NotificationIntents
import com.bos.sphere.core.data.NotificationOpener
import com.bos.sphere.core.data.OpenStep
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.PendingIntentSender
import com.bos.sphere.core.data.ShortcutStarter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** W42 (notifDeepLink): a tapped notification lands on the notification, also when its row has left the shade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationDeepLinkW42Test {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val senderBefore = PendingIntentLauncher.sender
    private val shortcutsBefore = NotificationOpener.shortcuts

    private class Sent(val canceled: Set<PendingIntent> = emptySet()) : PendingIntentSender {
        val sent = ArrayList<PendingIntent>()
        override fun send(pi: PendingIntent, context: Context?, fill: Intent?, options: Bundle?) {
            if (pi in canceled) throw PendingIntent.CanceledException()
            sent += pi
        }
    }
    private class Shortcuts(val works: Boolean = true) : ShortcutStarter {
        val started = ArrayList<Triple<String, String, Int>>()
        override fun start(context: Context, packageName: String, shortcutId: String, userId: Int) {
            if (!works) throw IllegalStateException("no such shortcut")
            started += Triple(packageName, shortcutId, userId)
        }
    }

    @Before fun clean() { NotificationIntents.recent.clear() }
    @After fun restore() { PendingIntentLauncher.sender = senderBefore; NotificationOpener.shortcuts = shortcutsBefore; NotificationIntents.recent.clear() }

    private fun pi(code: Int) = PendingIntent.getActivity(app, code, Intent("demo.OPEN$code").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun row(live: PendingIntent?, shortcut: String? = null, key: String = "k", time: Long = 100) = HubNotification(
        id = key, key = key, source = "x", title = "Ann", summary = "hi", timestamp = time, packageName = "demo.chat", appLabel = "Chat",
        contentIntent = live, shortcutId = shortcut, profileId = 0,
    )

    @Test fun STR_02_w42_aLiveRowFiresItsOwnIntent() {
        val s = Sent(); PendingIntentLauncher.sender = s
        val p = pi(1)
        val r = NotificationOpener.open(row(p), app) { error("the app must not start") }
        assertEquals(OpenStep.Live, r.step); assertEquals(listOf(p), s.sent)
    }

    @Test fun STR_02_w42_aHistoryOnlyRowFiresTheRememberedIntent() {
        val s = Sent(); PendingIntentLauncher.sender = s
        val p = pi(2)
        NotificationIntents.observe(listOf(row(p, key = "chat1", time = 100)))
        val gone = CommsRead.fromEntry(HistoryEntry("chat1|100", "chat1", "demo.chat", "Chat", "Ann", "Ann", "hi", "Ann", 100, dismissed = true))
        assertNull("the history row itself has no intent", gone.contentIntent)
        val r = NotificationOpener.open(gone, app) { error("the app must not start") }
        assertEquals(OpenStep.Cached, r.step); assertEquals(HandOff.Sent, r.handOff); assertEquals(listOf(p), s.sent)
    }

    @Test fun STR_02_w42_theFeedFillsTheMemoryAndSkipsOngoingRows() {
        NotificationIntents.observe(listOf(row(pi(3), key = "a"), row(pi(4), key = "b").copy(isOngoing = true), row(null, key = "c")))
        assertNotNull(NotificationIntents.recent.find("a", 100))
        assertNull(NotificationIntents.recent.find("b", 100)); assertNull(NotificationIntents.recent.find("c", 100))
    }

    @Test fun STR_02_w42_aCancelledRememberedIntentFallsToTheShortcut() {
        val p = pi(5)
        PendingIntentLauncher.sender = Sent(canceled = setOf(p))
        val sc = Shortcuts(); NotificationOpener.shortcuts = sc
        NotificationIntents.observe(listOf(row(p, key = "chat2")))
        val gone = row(null, shortcut = "conv-7", key = "chat2")
        val r = NotificationOpener.open(gone, app) { error("the app must not start") }
        assertEquals(OpenStep.Shortcut, r.step); assertEquals(listOf(Triple("demo.chat", "conv-7", 0)), sc.started)
    }

    @Test fun STR_02_w42_aHistoryEntryOpensItsShortcutAfterARestart() {
        PendingIntentLauncher.sender = Sent()
        val sc = Shortcuts(); NotificationOpener.shortcuts = sc // the memory is empty: a new process
        val e = HistoryEntry("c|1", "c", "demo.chat", "Chat", "Ann", "Ann", "hi", "Ann", 1, shortcutId = "conv-9", userId = 10)
        val r = NotificationOpener.open(e, app) { error("the app must not start") }
        assertEquals(OpenStep.Shortcut, r.step); assertEquals(listOf(Triple("demo.chat", "conv-9", 10)), sc.started)
    }

    @Test fun STR_02_w42_noShortcutFallsToTheApp() {
        PendingIntentLauncher.sender = Sent()
        val sc = Shortcuts(works = false); NotificationOpener.shortcuts = sc
        val launched = ArrayList<String>()
        val withShortcut = NotificationOpener.open(row(null, shortcut = "gone"), app) { launched += it; true }
        assertEquals(HandOff.FellBack, withShortcut.handOff); assertEquals(OpenStep.App, withShortcut.step)
        val without = NotificationOpener.open(row(null), app) { launched += it; true }
        assertEquals(OpenStep.App, without.step)
        assertEquals(listOf("demo.chat", "demo.chat"), launched)
    }

    @Test fun STR_02_w42_failureKeepsTheCallerInformed() {
        PendingIntentLauncher.sender = Sent(); NotificationOpener.shortcuts = Shortcuts(works = false)
        val r = NotificationOpener.open(row(null, shortcut = "x"), app) { false }
        assertEquals(HandOff.Failed, r.handOff); assertFalse(r.ok)
    }

    @Test fun STR_02_w42_laterPostOfTheSameChatReplacesTheRememberedIntent() {
        val s = Sent(); PendingIntentLauncher.sender = s
        val first = pi(6); val second = pi(7)
        NotificationIntents.observe(listOf(row(first, key = "chat3", time = 100)))
        NotificationIntents.observe(listOf(row(second, key = "chat3", time = 200)))
        NotificationOpener.open(row(null, key = "chat3", time = 100), app) { false }
        assertEquals(listOf(second), s.sent)
    }

    @Test fun STR_02_w42_commsOpenUsesTheOpenerAndOnlyClosesWhenSomethingOpened() {
        val s = Sent(); PendingIntentLauncher.sender = s
        val p = pi(8); NotificationIntents.observe(listOf(row(p, key = "chat4")))
        var closed = 0
        val r = LaunchpadOpen.open(row(null, key = "chat4"), app, launchApp = { false }, markRead = {}, cancel = {}, close = { closed++ })
        assertEquals(HandOff.Sent, r); assertEquals(1, closed)
        NotificationIntents.recent.clear()
        val bad = LaunchpadOpen.open(row(null, key = "chat4"), app, launchApp = { false }, markRead = {}, cancel = {}, close = { closed++ })
        assertEquals(HandOff.Failed, bad); assertEquals("a failed hand-off keeps Comms open", 1, closed)
    }

    @Test fun STR_02_w42_options_creatorModeOnlyFromApi35() {
        val b34 = PendingIntentLauncher.options(34)!!
        assertTrue(b34.keySet().none { it.contains("Creator", true) })
        assertNull(PendingIntentLauncher.options(33))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDeepLinkSdk35W42Test {
    @Test fun STR_02_w42_options_api35AlsoAllowsTheCreatorToStartActivities() {
        val b = PendingIntentLauncher.options(35)!!
        val keys = b.keySet().toList()
        assertTrue("sender opt-in kept: $keys", keys.any { it.contains("pendingIntent.backgroundActivityAllowed", true) })
        assertTrue("creator opt-in added: $keys", keys.any { it.contains("pendingIntentCreatorBackgroundActivityStartMode", true) })
    }
}
