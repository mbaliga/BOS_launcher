package com.bos.sphere.feature.dock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.UserManager
import android.view.View
import android.widget.Toast
import com.bos.sphere.core.capabilities.CapabilityInputs
import com.bos.sphere.core.capabilities.CapabilityRegistry
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Bounds
import com.bos.sphere.core.launch.DisplayRef
import com.bos.sphere.core.launch.GroupOutcome
import com.bos.sphere.core.launch.GroupPlan
import com.bos.sphere.core.launch.GroupPlanner
import com.bos.sphere.core.launch.LaunchCaps
import com.bos.sphere.core.launch.LaunchCapsFactory
import com.bos.sphere.core.launch.LaunchExecutor
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchResult
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.launch.SessionRestorer
import com.bos.sphere.core.launch.SpikeResults

/**
 * The launcher's one doorway to [LaunchExecutor] (WSP-01): every app launch in the app comes through here, so placement
 * that had to be dropped is reported once per session per reason in a brief, non-blocking toast.
 */
class AppLauncher(
    context: Context,
    private val rootView: () -> View? = { null },
    private val notify: (String) -> Unit = { msg -> Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show() },
) {
    private val app = context.applicationContext
    private val registry = CapabilityRegistry(app)
    val spikes = SpikeResults.from(app)
    private var cached: CapabilityInputs? = null
    private var cachedAt = 0L

    private val executor = LaunchExecutor(
        app, caps = { caps() },
        userFor = { hash -> app.getSystemService(UserManager::class.java).userProfiles.firstOrNull { it.hashCode() == hash } },
        rootView = rootView,
    )

    /** Device snapshot, reused for a couple of seconds so a tap never waits on a full capability read. */
    fun inputs(): CapabilityInputs {
        val now = System.currentTimeMillis()
        return cached?.takeIf { now - cachedAt < 2_000 } ?: registry.snapshot().also { cached = it; cachedAt = now }
    }

    fun caps(): LaunchCaps = LaunchCapsFactory.from(inputs(), spikes)
    fun displays(): List<DisplayRef> = LaunchCapsFactory.displayRefs(inputs())
    fun displayNames(): Map<Int, String> = displays().associate { it.id to it.name }
    fun defaultDisplayId(): Int = inputs().displays.firstOrNull { it.isDefault }?.id ?: 0
    fun displaySize(id: Int): Pair<Int, Int> = inputs().displays.firstOrNull { it.id == id }?.let { it.widthPx to it.heightPx } ?: (1080 to 1920)

    fun targetOf(entry: AppEntry) = LaunchTarget(entry.packageName, entry.componentName.className, entry.user.hashCode())

    fun launch(req: LaunchRequest): LaunchResult {
        val r = executor.launch(req)
        executor.freshNotices(r).forEach { notify(L10n.tr(it.message)) }
        if (!r.started) notify(L10n.tr("That app could not be opened"))
        return r
    }

    /** A plain launch from a tapped item; [source] is the item's rectangle on screen so the app opens out of it (WSP-06). */
    fun launch(entry: AppEntry, source: Rect? = null): LaunchResult =
        launch(LaunchRequest(targetOf(entry), source = source?.let { Bounds(it.left, it.top, it.right, it.bottom) }))

    /** Opens a group by its plan for this device; returns what was opened and the switcher for the rest (WSP-05). */
    suspend fun runGroup(group: AppGroup, targetDisplay: Int? = null): GroupOutcome {
        val (w, h) = displaySize(targetDisplay ?: defaultDisplayId())
        val plan = GroupPlanner.plan(group, w, h, caps(), targetDisplay)
        return runPlan(group.name, plan)
    }

    suspend fun restore(session: Session): List<String> {
        val rp = SessionRestorer.plan(session, displays(), defaultDisplayId())
        runPlan(session.name, rp.plan)
        notify(L10n.tr(rp.notice))
        rp.report.forEach { notify(L10n.tr(it)) }
        return rp.report
    }

    private suspend fun runPlan(name: String, plan: GroupPlan): GroupOutcome {
        val out = executor.launchPlan(plan)
        out.results.forEach { r -> executor.freshNotices(r).forEach { notify(L10n.tr(it.message)) } }
        if (out.failed.isNotEmpty()) notify(L10n.tr("%d app(s) in %s did not open", out.failed.size, name))
        if (out.switcher.isNotEmpty()) QuickSwitcher.post(app, name, out.switcher)
        return out
    }
}

/**
 * On a device with no split support a group opens its first app and the rest are offered here: a notification with one
 * action per remaining app (WSP-05). Up to three remain, which is the number of actions a notification shows.
 */
object QuickSwitcher {
    private const val CHANNEL = "liftoff_switcher"

    fun post(context: Context, groupName: String, rest: List<LaunchRequest>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Group switcher", NotificationManager.IMPORTANCE_LOW))
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon).setContentTitle(groupName).setContentText("Open the rest of the group")
            .setAutoCancel(true)
        rest.take(3).forEachIndexed { i, r ->
            val intent = launchIntent(context, r.target) ?: return@forEachIndexed
            val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(r.target.packageName, 0)).toString() }.getOrDefault(r.target.packageName)
            val pi = PendingIntent.getActivity(context, (groupName + r.target.packageName).hashCode() + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(Notification.Action.Builder(null, label, pi).build())
        }
        runCatching { nm.notify(("switcher:" + groupName).hashCode(), b.build()) }
    }

    fun launchIntent(context: Context, t: LaunchTarget): Intent? =
        (t.className?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(t.packageName, it)) }
            ?: context.packageManager.getLaunchIntentForPackage(t.packageName))?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

