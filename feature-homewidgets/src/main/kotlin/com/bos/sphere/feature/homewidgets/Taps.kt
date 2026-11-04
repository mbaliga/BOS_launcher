package com.bos.sphere.feature.homewidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.bos.sphere.feature.homewidgets.model.MediaCommand
import com.bos.sphere.feature.homewidgets.model.PadRef
import com.bos.sphere.feature.homewidgets.model.WidgetAction

/**
 * Every PendingIntent a widget carries. All are explicit (Sphere's own components, no URL scheme),
 * immutable, and have a request code unique per widget id + action. Taps that leave Sphere go
 * through [WidgetTapActivity], which checks the target before launching it; taps that change
 * something go through [WidgetActionReceiver]. Neither is exported.
 */
internal object Taps {
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Sphere's home, optionally with the hub pulled down. */
    fun openHome(c: Context, id: Int, hub: Boolean): PendingIntent? {
        val home = HomeWidgets.homeComponent(c) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).setComponent(home)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hub) intent.putExtra(HomeWidgets.EXTRA_OPEN, HomeWidgets.OPEN_HUB)
        return PendingIntent.getActivity(c, WidgetAction.requestCode(id, WidgetAction.OPEN), intent, FLAGS)
    }

    /** The system's notification-access setting for Sphere. */
    fun notificationAccess(c: Context, id: Int): PendingIntent =
        tap(c, id, WidgetAction.OPEN, WidgetTapActivity.ACTION_ACCESS) {}

    /**
     * An app by package: its main activity in the profile with [userSerial] (null: Sphere's own
     * profile); Sphere's hub when it has none there.
     */
    fun launchPackage(c: Context, id: Int, action: WidgetAction, packageName: String, userSerial: Long?): PendingIntent =
        tap(c, id, action, WidgetTapActivity.ACTION_LAUNCH_PACKAGE) {
            putExtra(WidgetTapActivity.EXTRA_PACKAGE, packageName)
            if (userSerial != null && userSerial >= 0) putExtra(WidgetTapActivity.EXTRA_USER_SERIAL, userSerial)
        }

    /** One chosen launcher entry, in its own profile. */
    fun launchApp(c: Context, id: Int, action: WidgetAction, ref: PadRef): PendingIntent =
        tap(c, id, action, WidgetTapActivity.ACTION_LAUNCH_APP) {
            putExtra(WidgetTapActivity.EXTRA_COMPONENT, ref.component)
            putExtra(WidgetTapActivity.EXTRA_USER_SERIAL, ref.userSerial)
        }

    /** This widget's options screen (pads: choose apps). */
    fun options(c: Context, id: Int): PendingIntent {
        val intent = Intent(c, WidgetOptionsActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, WidgetAction.requestCode(id, WidgetAction.CONFIGURE), intent, FLAGS)
    }

    fun media(c: Context, id: Int, action: WidgetAction, packageName: String, command: MediaCommand): PendingIntent {
        val intent = Intent(c, WidgetActionReceiver::class.java)
            .setAction(WidgetActionReceiver.ACTION_MEDIA)
            .putExtra(WidgetActionReceiver.EXTRA_PACKAGE, packageName)
            .putExtra(WidgetActionReceiver.EXTRA_COMMAND, command.name)
        return PendingIntent.getBroadcast(c, WidgetAction.requestCode(id, action), intent, FLAGS)
    }

    fun refreshBattery(c: Context, id: Int): PendingIntent {
        val intent = Intent(c, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_REFRESH_BATTERY)
        return PendingIntent.getBroadcast(c, WidgetAction.requestCode(id, WidgetAction.REFRESH), intent, FLAGS)
    }

    private inline fun tap(c: Context, id: Int, action: WidgetAction, what: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(c, WidgetTapActivity::class.java).setAction(what)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .apply(extras)
        return PendingIntent.getActivity(c, WidgetAction.requestCode(id, action), intent, FLAGS)
    }
}
