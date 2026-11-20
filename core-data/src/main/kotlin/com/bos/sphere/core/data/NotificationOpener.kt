package com.bos.sphere.core.data

import android.app.PendingIntent
import android.content.Context
import android.content.pm.LauncherApps

/**
 * W42 (notifDeepLink): a bounded, in-memory memory of the newest content intent seen per notification key, so a row whose notification has left the
 * shade (dismissed in Comms, cleared by the user, cancelled by the app) can still open the notification's own screen. A PendingIntent stays valid
 * after the notification is cancelled unless its app cancelled it; sending a cancelled one throws and the opener falls on to the next step.
 * Least recently used goes first. Never persisted (a PendingIntent cannot be).
 */
class RecentIntents<T : Any>(private val max: Int = MAX) {
    private class Slot<T>(val time: Long, val value: T)
    private val map = object : LinkedHashMap<String, Slot<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Slot<T>>?) = size > max
    }

    /** Remembers [value] for [key] posted at [time]. An older post than the one held is ignored (stale); a newer or equal one replaces it. */
    @Synchronized fun put(key: String, time: Long, value: T): Boolean {
        val held = map[key]
        if (held != null && held.time > time) return false
        map[key] = Slot(time, value)
        return true
    }

    /** The intent for [key] unless it is older than [time] (a post this memory has not seen, so its intent would be stale). */
    @Synchronized fun find(key: String, time: Long): T? = map[key]?.takeIf { it.time >= time }?.value

    @get:Synchronized val size: Int get() = map.size
    @Synchronized fun clear() = map.clear()

    companion object { const val MAX = 300 }
}

/** The process-wide memory, filled from the live feed ([NotificationProvider]). */
object NotificationIntents {
    val recent = RecentIntents<PendingIntent>()

    /** Records every live notification's content intent (ongoing and progress ones are not history rows, so they are not kept). */
    fun observe(feed: List<HubNotification>) {
        for (n in feed) { val pi = n.contentIntent ?: continue; if (!n.isOngoing && !n.progress) recent.put(n.key, n.timestamp, pi) }
    }
}

/** The ways a tapped notification can be opened, in the order they are tried. */
enum class OpenStep { Live, Cached, Shortcut, App }

/** Pure order and run of the steps (STR-02, W42). */
object OpenPlan {
    /** live intent, then the remembered intent, then the conversation shortcut, then the app's launcher activity. */
    fun steps(hasLive: Boolean, hasCached: Boolean, hasShortcut: Boolean, hasPackage: Boolean): List<OpenStep> = buildList {
        if (hasLive) add(OpenStep.Live)
        if (hasCached) add(OpenStep.Cached)
        if (hasShortcut) add(OpenStep.Shortcut)
        if (hasPackage) add(OpenStep.App)
    }

    /** Tries [steps] in order; the first that works decides. App is [HandOff.FellBack], the others [HandOff.Sent]; none working is [HandOff.Failed]. */
    fun run(steps: List<OpenStep>, attempt: (OpenStep) -> Boolean): NotificationOpener.Result {
        for (s in steps) if (runCatching { attempt(s) }.getOrDefault(false)) return NotificationOpener.Result(if (s == OpenStep.App) HandOff.FellBack else HandOff.Sent, s)
        return NotificationOpener.Result(HandOff.Failed, null)
    }
}

/** Test seam: starts a published conversation shortcut. */
fun interface ShortcutStarter {
    /** Throws when the shortcut is gone or this app may not start it. */
    fun start(context: Context, packageName: String, shortcutId: String, userId: Int)
}

/**
 * W42: the one way to open what a notification points at, for Comms and the Timeline: the notification's live intent, else the last intent
 * remembered for it, else the app's conversation shortcut (for chats that published one), else the app itself.
 */
object NotificationOpener {
    class Result(val handOff: HandOff, val step: OpenStep?) { val ok get() = handOff.ok }

    @Volatile var shortcuts: ShortcutStarter = ShortcutStarter { context, pkg, id, userId ->
        val apps = context.getSystemService(LauncherApps::class.java) ?: error("no LauncherApps")
        val user = apps.profiles.firstOrNull { it.hashCode() == userId } ?: android.os.Process.myUserHandle() // a UserHandle's hash is its user id (profileId)
        apps.startShortcut(pkg, id, null, null, user) // this app is the home app, which is what the platform requires
    }

    /** Opens [n] (a live row or a history-only row). [launchApp] starts the app's launcher activity and says whether it started. */
    fun open(n: HubNotification, context: Context?, launchApp: (String) -> Boolean): Result =
        open(n.key, n.timestamp, n.packageName, n.contentIntent, n.shortcutId, n.profileId, context, launchApp)

    /** Opens a recorded history entry (the Timeline, a history row). */
    fun open(e: HistoryEntry, context: Context?, launchApp: (String) -> Boolean): Result =
        open(e.key, e.timeMillis, e.packageName, null, e.shortcutId.ifBlank { null }, e.userId, context, launchApp)

    private fun open(
        key: String, time: Long, pkg: String, live: PendingIntent?, shortcutId: String?, userId: Int, context: Context?, launchApp: (String) -> Boolean,
    ): Result {
        val cached = NotificationIntents.recent.find(key, time)?.takeIf { it != live }
        val canShortcut = context != null && pkg.isNotBlank() && !shortcutId.isNullOrBlank()
        val steps = OpenPlan.steps(live != null, cached != null, canShortcut, pkg.isNotBlank())
        return OpenPlan.run(steps) { step ->
            when (step) {
                OpenStep.Live -> PendingIntentLauncher.fire(live, context)
                OpenStep.Cached -> PendingIntentLauncher.fire(cached, context)
                OpenStep.Shortcut -> { shortcuts.start(context!!, pkg, shortcutId!!, userId); true }
                OpenStep.App -> launchApp(pkg)
            }
        }
    }
}
