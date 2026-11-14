package com.bos.sphere.ui

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.NotificationActions
import com.bos.sphere.core.data.ReplyLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** STR-02: a real Notification with a RemoteInput action is parsed and the reply travels through its own PendingIntent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchpadReplyPathTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun notification(): Notification {
        val reply = PendingIntent.getBroadcast(app, 1, Intent("demo.REPLY").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val mute = PendingIntent.getBroadcast(app, 2, Intent("demo.MUTE").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)
        val input = RemoteInput.Builder("answer").setLabel("Reply to Sam").build()
        return Notification.Builder(app, "c").setSmallIcon(android.R.drawable.sym_def_app_icon)
            .addAction(Notification.Action.Builder(null, "Reply", reply).addRemoteInput(input).build())
            .addAction(Notification.Action.Builder(null, "Mute", mute).build())
            .build()
    }

    @Test fun STR_02_remoteInputIsParsedIntoAReplyTargetAndPlainActionsStay() {
        val actions = NotificationActions.parse(notification())
        assertEquals(listOf("Reply", "Mute"), actions.map { it.label })
        val reply = ReplyLogic.pick(actions)
        assertNotNull(reply)
        assertEquals("answer", reply!!.reply!!.resultKey)
        assertEquals("Reply to Sam", reply.reply!!.hint)
        assertEquals(listOf("Mute"), ReplyLogic.plain(actions).map { it.label })
    }

    @Test fun STR_02_theReplyTextArrivesInTheNotificationsOwnIntent() {
        val reply = ReplyLogic.pick(NotificationActions.parse(notification()))!!
        assertTrue(ReplyLogic.send(app, reply, "On my way"))
        val sent = shadowOf(app).broadcastIntents.single { it.action == "demo.REPLY" }
        val results = RemoteInput.getResultsFromIntent(sent)
        assertEquals("On my way", results.getCharSequence("answer").toString())
    }

    @Test fun STR_02_blankTextOrAMissingIntentSendsNothing() {
        val reply = ReplyLogic.pick(NotificationActions.parse(notification()))!!
        assertFalse(ReplyLogic.send(app, reply, "  "))
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == "demo.REPLY" })
        assertNull(ReplyLogic.pick(NotificationActions.parse(Notification.Builder(app, "c").setSmallIcon(android.R.drawable.sym_def_app_icon).build())))
    }
}
