package com.bos.sphere.feature.dock

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.bos.sphere.core.launch.LaunchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs a [RunTarget] with a visible launcher (an activity the user tapped into); used by notification Open actions and automation. */
object RunTargets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun run(context: Context, target: RunTarget, launcher: AppLauncher = AppLauncher(context)) {
        val mgr = WorkspaceRepository.get(context)
        val state = mgr.current
        when (target.kind) {
            RunKind.Workspace -> mgr.switchTo(target.id)
            RunKind.Group -> state.groups.firstOrNull { it.id == target.id }?.let { g -> scope.launch { launcher.runGroup(g) } }
            RunKind.Session -> state.sessions.firstOrNull { it.id == target.id }?.let { s -> scope.launch { launcher.restore(s) } }
            RunKind.App -> AppKeys.target(target.id)?.let { launcher.launch(LaunchRequest(it)) }
        }
    }
}

internal object ScheduleNotifications {
    private const val CHANNEL = "liftoff_scheduled"
    const val EXTRA_LABEL = "com.bos.sphere.extra.LABEL"

    fun runIntent(context: Context, target: RunTarget): Intent =
        Intent(context, RunActivity::class.java).setAction(AutomationIntents.ACTION_RUN)
            .putExtra(AutomationIntents.EXTRA_KIND, target.kind.name).putExtra(AutomationIntents.EXTRA_ID, target.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A notification with an Open action. The tap that follows is what lets the launch happen: nothing opens on its own (WSP-11). */
    fun post(context: Context, label: String, target: RunTarget) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scheduled launches", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(context, (target.kind.name + target.id).hashCode(), runIntent(context, target), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon).setContentTitle(label).setContentText("Tap Open to start it")
            .setContentIntent(pi).setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Open", pi).build())
            .build()
        runCatching { nm.notify((target.kind.name + target.id).hashCode(), n) }
    }
}

object Scheduler {
    private fun pending(context: Context, sl: ScheduledLaunch): PendingIntent =
        PendingIntent.getBroadcast(
            context, sl.id.hashCode(),
            Intent(context, ScheduledLaunchReceiver::class.java).putExtra(AutomationIntents.EXTRA_ID, sl.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Inexact, doze-friendly alarm: it needs no exact-alarm permission, and the notification is posted near the chosen time. */
    fun schedule(context: Context, sl: ScheduledLaunch) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sl.atMillis, pending(context, sl)) }
    }

    fun cancel(context: Context, sl: ScheduledLaunch) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, sl)) }
    }

    fun rescheduleAll(context: Context, now: Long = System.currentTimeMillis()) {
        WorkspaceRepository.get(context).current.schedules.filter { it.atMillis > now }.forEach { schedule(context, it) }
    }
}

class ScheduledLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mgr = WorkspaceRepository.get(context)
        val sl = mgr.current.schedules.firstOrNull { it.id == intent.getStringExtra(AutomationIntents.EXTRA_ID) } ?: return
        ScheduleNotifications.post(context, sl.label, sl.target)
        mgr.removeSchedule(sl.id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rescheduleAll(context)
    }
}

/**
 * Entry for other tools (WSP-12). Switching a workspace changes launcher state only, so it runs directly. Opening apps from a
 * background broadcast is blocked by the system, so for a group or session the receiver posts the Open notification instead.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = AutomationIntents.parse(
            intent.action, intent.getStringExtra(AutomationIntents.EXTRA_NAME),
            intent.getStringExtra(AutomationIntents.EXTRA_KIND), intent.getStringExtra(AutomationIntents.EXTRA_ID),
        ) ?: return
        val mgr = WorkspaceRepository.get(context)
        val target = AutomationIntents.resolve(raw, mgr.current) ?: return
        if (target.kind == RunKind.Workspace) mgr.switchTo(target.id)
        else ScheduleNotifications.post(context, "Open " + (intent.getStringExtra(AutomationIntents.EXTRA_NAME) ?: target.id), target)
    }
}

/** Transparent trampoline behind notification Open actions and explicit activity intents; it runs the target and finishes. */
class RunActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = AutomationIntents.parse(
            intent.action, intent.getStringExtra(AutomationIntents.EXTRA_NAME),
            intent.getStringExtra(AutomationIntents.EXTRA_KIND), intent.getStringExtra(AutomationIntents.EXTRA_ID),
        )
        val target = raw?.let { AutomationIntents.resolve(it, WorkspaceRepository.get(this).current) }
        if (target == null) Toast.makeText(this, "Nothing to open", Toast.LENGTH_SHORT).show()
        else RunTargets.run(this, target)
        finish()
    }
}
