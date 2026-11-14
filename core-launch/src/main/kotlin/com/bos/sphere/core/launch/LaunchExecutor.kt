package com.bos.sphere.core.launch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.UserHandle
import kotlinx.coroutines.delay

/** Result of a launch: what was applied and what was dropped, so the UI can say so (WSP-01). */
data class LaunchResult(val started: Boolean, val resolution: Resolution)

/**
 * The only place that starts other apps. Uses public APIs only: [ActivityOptions.setLaunchDisplayId],
 * [ActivityOptions.setLaunchBounds] and [Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT]; never a windowing-mode option.
 */
class LaunchExecutor(
    private val context: Context,
    private val caps: () -> LaunchCaps,
    private val userFor: (Int) -> UserHandle? = { null },
    /** A view of the launcher window, so a launch with a source rectangle can open out of it (WSP-06). */
    private val rootView: () -> android.view.View? = { null },
) {
    private val notices = DropNotices()

    fun launch(req: LaunchRequest): LaunchResult {
        val res = LaunchResolver.resolve(req, caps())
        val ok = runCatching { start(req, res) }.isSuccess
        return LaunchResult(ok, res)
    }

    /** Drop reasons not yet shown this session. */
    fun freshNotices(result: LaunchResult): List<DropReason> = notices.fresh(result.resolution.dropped)

    /** Launches the members one after another, re-checking each result (WSP-05). */
    suspend fun launchGroup(group: AppGroup, gapMs: Long = 350): List<LaunchResult> =
        launchPlan(GroupPlan(GroupMode.Bounds, group.members, emptyList()), gapMs).results

    /** Runs a planned group or restored session in sequence; a step that fails is retried once (WSP-05, WSP-09). */
    suspend fun launchPlan(plan: GroupPlan, gapMs: Long = 350): GroupOutcome = GroupRunner.run(plan, gapMs, { delay(it) }) { launch(it) }

    private fun start(req: LaunchRequest, res: Resolution) {
        val opts = animationOptions(req.source)
        res.displayId?.let { opts.launchDisplayId = it }
        res.bounds?.let { opts.setLaunchBounds(Rect(it.left, it.top, it.right, it.bottom)) }
        val t = req.target
        val user = userFor(t.userId)
        val component = t.className?.let { ComponentName(t.packageName, it) }
        if (res.adjacent || user == null || component == null) {
            val intent = (component?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(it) }
                ?: context.packageManager.getLaunchIntentForPackage(t.packageName) ?: error("no launch intent"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (res.adjacent) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK else 0)
            req.source?.let { intent.sourceBounds = Rect(it.left, it.top, it.right, it.bottom) }
            context.startActivity(intent, opts.toBundle())
        } else {
            val src = req.source?.let { Rect(it.left, it.top, it.right, it.bottom) }
            context.getSystemService(LauncherApps::class.java).startMainActivity(component, user, src, opts.toBundle())
        }
    }

    /** The app opens out of the tapped item using the public clip-reveal option; without a window or source it is a plain launch. */
    private fun animationOptions(source: Bounds?): ActivityOptions {
        val view = rootView()
        if (source != null && view != null && view.isAttachedToWindow) {
            runCatching {
                val at = IntArray(2).also { view.getLocationOnScreen(it) }
                val r = SourceRects.relativeTo(source, at[0], at[1])
                return ActivityOptions.makeClipRevealAnimation(view, r.left, r.top, r.right - r.left, r.bottom - r.top)
            }
        }
        return ActivityOptions.makeBasic()
    }
}
