package com.bos.sphere.feature.homewidgets

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import com.bos.sphere.feature.homewidgets.model.WidgetKind

/**
 * Where widget taps that leave Sphere land: launch an app (through `LauncherApps`, so work-profile
 * apps open in their profile) or open Sphere's notification-access setting. Not exported, no UI: it
 * checks the target, starts it and finishes. Its own task affinity keeps Sphere's home task (when
 * Sphere isn't the home app) from coming forward in between.
 */
class WidgetTapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { handle(intent) }
        finish()
    }

    private fun handle(intent: Intent) {
        val la = getSystemService(LauncherApps::class.java)
        when (intent.action) {
            ACTION_LAUNCH_APP -> {
                val cn = intent.getStringExtra(EXTRA_COMPONENT)?.let { ComponentName.unflattenFromString(it) }
                val user = getSystemService(UserManager::class.java)
                    ?.getUserForSerialNumber(intent.getLongExtra(EXTRA_USER_SERIAL, -1L))
                // Only an activity LauncherApps lists as launchable for that profile, now.
                val ok = cn != null && user != null &&
                    la.getActivityList(cn.packageName, user).any { it.componentName == cn }
                if (ok) {
                    la.startMainActivity(cn, user, intent.sourceBounds, null)
                } else {
                    Toast.makeText(this, R.string.hw_app_unavailable, Toast.LENGTH_SHORT).show()
                    HomeWidgets.refresh(this, WidgetKind.PADS)
                }
            }
            ACTION_LAUNCH_PACKAGE -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
                // The profile the notification came from (a Needs you row); Sphere's own otherwise.
                val serial = intent.getLongExtra(EXTRA_USER_SERIAL, -1L)
                val user = if (serial < 0) Process.myUserHandle() else getSystemService(UserManager::class.java)?.getUserForSerialNumber(serial)
                val target = if (pkg.isEmpty() || user == null) null else runCatching { la.getActivityList(pkg, user).firstOrNull() }.getOrNull()
                if (target != null) {
                    la.startMainActivity(target.componentName, target.user, intent.sourceBounds, null)
                } else {
                    // No launcher entry (a system component, or gone): Sphere's hub lists it instead.
                    HomeWidgets.homeComponent(this)?.let { home ->
                        startActivity(
                            Intent(Intent.ACTION_MAIN).setComponent(home)
                                .putExtra(HomeWidgets.EXTRA_OPEN, HomeWidgets.OPEN_HUB)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            ACTION_ACCESS -> {
                val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, Sources.listenerComponent(this).flattenToString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { startActivity(detail) }.isFailure) runCatching { startActivity(list) }
            }
        }
    }

    internal companion object {
        const val ACTION_LAUNCH_APP = "com.bos.sphere.homewidgets.LAUNCH_APP"
        const val ACTION_LAUNCH_PACKAGE = "com.bos.sphere.homewidgets.LAUNCH_PACKAGE"
        const val ACTION_ACCESS = "com.bos.sphere.homewidgets.NOTIFICATION_ACCESS"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_USER_SERIAL = "userSerial"
        const val EXTRA_PACKAGE = "package"
    }
}
