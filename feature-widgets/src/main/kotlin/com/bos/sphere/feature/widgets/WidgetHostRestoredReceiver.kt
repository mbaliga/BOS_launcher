package com.bos.sphere.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a backup restore the system re-creates this host's widgets under new ids and sends
 * `ACTION_APPWIDGET_HOST_RESTORED` with the old and new ids, possibly more than once (each time
 * only with the records it hasn't sent yet). Placements restored with the app's SharedPreferences
 * (`allowBackup` is true) were marked restore-pending when first loaded; those this broadcast names
 * get their new ids, the rest keep waiting (see [WidgetHosting.onHostRestored]). Getting the
 * hosting instance here also performs that first load if the launcher hasn't started yet.
 *
 * Not exported: the action is a protected system broadcast, which reaches non-exported receivers.
 */
class WidgetHostRestoredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED) return
        if (intent.getIntExtra(AppWidgetManager.EXTRA_HOST_ID, -1) != WidgetHosting.HOST_ID) return
        val oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: return
        val newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
        WidgetHosting.get(context).onHostRestored(oldIds, newIds)
    }
}
