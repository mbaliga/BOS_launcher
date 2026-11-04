package com.bos.sphere.feature.homewidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.bos.sphere.feature.homewidgets.model.WidgetKind
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Sphere's own home-screen widgets, the ones any launcher can place (Pixel, One UI, Nova, Sphere
 * itself): gate clock, battery, now playing, needs you, app pads. docs/widgets.md has the design.
 *
 * Process-wide: [init] runs from the Application, so the live sources ([WidgetRuntime]) start
 * whenever Sphere's process does, also when the system starts it only to bind the notification
 * listener (Sphere isn't the home app). Redraws are pushed from there and from the providers; no
 * widget polls. All drawing happens on one background thread.
 */
object HomeWidgets {
    /** Extra on the home activity's intent: where a widget tap should land. */
    const val EXTRA_OPEN = "com.bos.sphere.extra.OPEN"

    /** [EXTRA_OPEN] value: open home with the hub (the "Needs you" feed) pulled down. */
    const val OPEN_HUB = "hub"

    private const val TAG = "SphereHomeWidgets"

    @Volatile
    private var homeActivity: ComponentName? = null

    internal val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sphere-home-widgets").apply { isDaemon = true }
    }

    /** Last drawn content per widget id, so a change that alters nothing shown isn't redrawn. */
    private val lastDrawn = HashMap<Int, String>()

    /** From `Application.onCreate`: names the activity widget taps open and starts the live sources. */
    fun init(context: Context, home: Class<out Activity>) {
        homeActivity = ComponentName(context, home)
        WidgetRuntime.get(context).sync()
        onFontScale(context, context.resources.configuration.fontScale)
    }

    /**
     * From [init] and `Application.onConfigurationChanged`: the widgets' size keys depend on the
     * font scale (`WidgetTiers.atFontScale`), so when it differs from the one the widgets were last
     * drawn with (also across process restarts: it is stored), every widget is redrawn.
     */
    fun onFontScale(context: Context, fontScale: Float) {
        val app = context.applicationContext
        executor.execute {
            if (WidgetPrefs.fontScale(app) != fontScale) {
                WidgetPrefs.setFontScale(app, fontScale)
                WidgetKind.entries.forEach { draw(app, it, ids(app, it), force = true) }
            }
        }
    }

    /** Redraws every placed widget of [kinds] (all kinds when empty), off the main thread. */
    fun refresh(context: Context, vararg kinds: WidgetKind) {
        val app = context.applicationContext
        val which = if (kinds.isEmpty()) WidgetKind.entries.toTypedArray() else kinds
        executor.execute { which.forEach { draw(app, it, ids(app, it), force = true) } }
    }

    internal fun homeComponent(context: Context): ComponentName? =
        homeActivity ?: context.packageManager.getLaunchIntentForPackage(context.packageName)?.component

    internal fun providerClass(kind: WidgetKind): Class<out AppWidgetProvider> = when (kind) {
        WidgetKind.CLOCK -> ClockWidget::class.java
        WidgetKind.BATTERY -> BatteryWidget::class.java
        WidgetKind.NOW_PLAYING -> NowPlayingWidget::class.java
        WidgetKind.NEEDS_YOU -> NeedsYouWidget::class.java
        WidgetKind.PADS -> AppPadsWidget::class.java
    }

    /** The kind a provider component belongs to, if it is one of Sphere's. */
    internal fun kindOf(context: Context, provider: ComponentName?): WidgetKind? {
        if (provider == null || provider.packageName != context.packageName) return null
        return WidgetKind.entries.firstOrNull { providerClass(it).name == provider.className }
    }

    internal fun ids(context: Context, kind: WidgetKind): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, providerClass(kind)))
    }.getOrDefault(IntArray(0))

    /**
     * Draws [ids] of [kind] now (call on [executor]). [force] false skips a widget whose content is
     * the same as last drawn (media and notification events often change nothing it shows).
     */
    internal fun draw(context: Context, kind: WidgetKind, ids: IntArray, force: Boolean) {
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val drawn: List<Pair<Int, Drawn>> = try {
            Renderers.render(context, kind, ids)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't draw $kind", e)
            ids.map { it to Drawn(Renderers.error(context, kind, it), "error") }
        }
        for ((id, d) in drawn) {
            val same = synchronized(lastDrawn) { lastDrawn[id] == d.key }
            if (!force && same) continue
            runCatching { manager.updateAppWidget(id, d.views) }
                .onSuccess { synchronized(lastDrawn) { lastDrawn[id] = d.key } }
                .onFailure { Log.w(TAG, "updateAppWidget failed for $kind", it) }
        }
    }

    internal fun forget(ids: IntArray) = synchronized(lastDrawn) { ids.forEach { lastDrawn.remove(it) } }
}

/** One widget's views and a key for what they show (without the time they were drawn). */
internal class Drawn(val views: RemoteViews, val key: String)
