package com.bos.sphere.core.signals

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/** One foreground event from the usage log, as plain data. */
data class UsageEv(val packageName: String, val timeMs: Long, val foreground: Boolean)

/** Pure recents rules (DCK-02): newest first, one entry per app, never the excluded ones, and only apps seen in the foreground. */
object RecentsLogic {
    fun recents(events: List<UsageEv>, exclude: Set<String>, limit: Int): List<String> =
        events.asSequence()
            .filter { it.foreground && it.packageName !in exclude }
            .sortedByDescending { it.timeMs }
            .map { it.packageName }
            .distinct()
            .take(limit)
            .toList()
}

/**
 * Recent apps from [UsageStatsManager], which needs the usage-access grant. This lists what was used lately,
 * never what is running: the platform gives a launcher no way to enumerate live tasks (spec section 3).
 */
class UsageRecents(private val context: Context) {
    private val appContext = context.applicationContext

    fun hasAccess(): Boolean = runCatching {
        val ops = appContext.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Opens the system usage-access screen (the "enable" row in the dock). */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun recents(limit: Int = 12, windowMs: Long = 3L * 24 * 60 * 60 * 1000, now: Long = System.currentTimeMillis()): List<String> {
        if (!hasAccess()) return emptyList()
        return runCatching {
            val usm = appContext.getSystemService(UsageStatsManager::class.java)
            val q = usm.queryEvents(now - windowMs, now)
            val out = ArrayList<UsageEv>()
            val e = UsageEvents.Event()
            while (q.hasNextEvent()) {
                q.getNextEvent(e)
                @Suppress("DEPRECATION")
                val fg = e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                out += UsageEv(e.packageName, e.timeStamp, fg)
            }
            RecentsLogic.recents(out, setOf(appContext.packageName), limit)
        }.getOrDefault(emptyList())
    }
}
