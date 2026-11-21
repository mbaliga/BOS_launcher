package com.bos.sphere.core.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Test seam: the one call that really hands a PendingIntent to the system. */
fun interface PendingIntentSender {
    /** Sends [pi]; throws [PendingIntent.CanceledException] when the owner cancelled it, SecurityException when refused. */
    fun send(pi: PendingIntent, context: Context?, fill: Intent?, options: Bundle?)
}

/** What happened to an attempt to open something through a PendingIntent (STR-02). */
enum class HandOff {
    /** The system accepted the PendingIntent. */
    Sent,
    /** The PendingIntent was missing or refused, and the app's own launcher activity was started instead. */
    FellBack,
    /** Neither worked: the caller must keep its screen and say so. */
    Failed;

    val ok get() = this != Failed
}

/**
 * STR-02: the one place that fires a PendingIntent that belongs to ANOTHER app (notification content and action intents,
 * media session activities).
 *
 * Why: from Android 14 a PendingIntent only starts an activity from the background when its SENDER opts in with
 * [ActivityOptions.setPendingIntentBackgroundActivityStartMode]. Without the opt-in send() returns normally while the
 * system silently drops the launch, so "no exception" must never be read as "opened". Every send site goes through here.
 */
object PendingIntentLauncher {
    /** Replaceable in tests. */
    @Volatile var sender: PendingIntentSender = PendingIntentSender { pi, context, fill, options ->
        pi.send(context, 0, fill, null, null, null, options)
    }

    /** The sender's opt-in bundle on Android 14 and later, else null (the option does not exist before). */
    fun options(sdkInt: Int = Build.VERSION.SDK_INT): Bundle? {
        if (sdkInt < 34) return null
        return runCatching {
            val o = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            // W42: Android 15 also looks at the mode of the PendingIntent's creator; a trampoline (broadcast or service that starts the
            // activity) is started with the creator's mode, which must not default to blocking.
            if (sdkInt >= 35) runCatching { o.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED) }
            o.toBundle()
        }.getOrNull()
    }

    /** Sends [pi] with the opt-in. True only when it was actually handed to the system. */
    fun fire(pi: PendingIntent?, context: Context? = null, fill: Intent? = null): Boolean {
        if (pi == null) return false
        return try {
            sender.send(pi, context, fill, options())
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Opens through [pi]; when that does not work, [fallback] starts the app's launcher activity (it returns whether it did).
     * [Failed] means neither worked.
     */
    fun open(pi: PendingIntent?, context: Context? = null, fallback: () -> Boolean): HandOff {
        if (fire(pi, context)) return HandOff.Sent
        return if (runCatching(fallback).getOrDefault(false)) HandOff.FellBack else HandOff.Failed
    }
}
