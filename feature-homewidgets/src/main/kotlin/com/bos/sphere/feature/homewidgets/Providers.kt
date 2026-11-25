package com.bos.sphere.feature.homewidgets

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.bos.sphere.feature.homewidgets.model.WidgetKind

/**
 * Base provider: draws off the main thread (goAsync + the widgets' executor), tells the runtime
 * which widgets exist so it watches only the sources they need, and forgets a removed widget's
 * options. Exported in the manifest, as every App Widget provider must be, so launchers can bind it.
 */
abstract class HomeWidgetProvider internal constructor(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Delivered to manifest receivers (implicit-broadcast exemptions); each can change what
            // the widgets print: the time format and zone, the language, the next alarm.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            -> redraw(context, HomeWidgets.ids(context, kind))
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRuntime.get(context).sync()
        // Only this provider's own widgets. APPWIDGET_UPDATE isn't a protected broadcast, so any app
        // can send it to an exported provider with any ids; drawing another kind's id as this kind
        // would ignore that widget's privacy option (options are keyed by id).
        val mine = HomeWidgets.ids(context, kind).toSet()
        redraw(context, appWidgetIds.filter { it in mine }.toIntArray())
    }

    override fun onEnabled(context: Context) = WidgetRuntime.get(context).sync()

    override fun onDisabled(context: Context) = WidgetRuntime.get(context).sync()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.forget(context, appWidgetIds)
        HomeWidgets.forget(appWidgetIds)
    }

    private fun redraw(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val app = context.applicationContext
        val pending = goAsync()
        HomeWidgets.executor.execute {
            try {
                HomeWidgets.draw(app, kind, ids, force = true)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The VAIO-gate clock: time, date, next alarm. */
class ClockWidget : HomeWidgetProvider(WidgetKind.CLOCK)

/** Battery level and state, with the time it was read. */
class BatteryWidget : HomeWidgetProvider(WidgetKind.BATTERY)

/** What's playing (media sessions, through Sphere's notification access). */
class NowPlayingWidget : HomeWidgetProvider(WidgetKind.NOW_PLAYING)

/** Notification counts per app (never content), through Sphere's notification access. */
class NeedsYouWidget : HomeWidgetProvider(WidgetKind.NEEDS_YOU)

/** Up to six apps the user picks, launched through LauncherApps. */
class AppPadsWidget : HomeWidgetProvider(WidgetKind.PADS)
