package com.bos.sphere.feature.homewidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.ContextCompat
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.feature.homewidgets.model.BatteryModel
import com.bos.sphere.feature.homewidgets.model.WidgetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The live sources behind the widgets, watched only while a widget that needs them is placed and
 * only for as long as Sphere's process runs (no alarms, no jobs, no polling):
 *
 * - battery: `ACTION_BATTERY_CHANGED` (context-registered; it can't be declared in a manifest),
 *   redrawn when level / state / plug change or the printed "as of" time is 15 minutes old;
 * - notifications: Sphere's listener ([NotificationProvider.active], package + flags only) for
 *   Needs you, and its connect / disconnect for both access-dependent widgets;
 * - media: `MediaSessionManager` sessions and their playback / metadata callbacks (needs access);
 * - apps: `LauncherApps` callbacks and the hidden-apps set, for pads, app names, and the apps
 *   Needs you and Now playing never name.
 *
 * When the process is gone, the widgets keep their last drawing, which prints its "as of" time
 * where the data can lapse. Main thread only; drawing goes to [HomeWidgets.executor].
 */
internal class WidgetRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // --- coalesced redraws ---

    private val pending = HashSet<WidgetKind>()
    private val forced = HashSet<WidgetKind>()
    private val flush = Runnable {
        val kinds = pending.toList()
        val force = forced.toSet()
        pending.clear()
        forced.clear()
        HomeWidgets.executor.execute { kinds.forEach { HomeWidgets.draw(app, it, HomeWidgets.ids(app, it), force = it in force) } }
    }

    /**
     * Redraw [kind] soon; bursts of events (a session's state and metadata, notification storms)
     * become one draw. [force]: draw even if the content key is unchanged (an app update can
     * change an icon, which the key doesn't hold).
     */
    fun request(kind: WidgetKind, force: Boolean = false) {
        pending += kind
        if (force) forced += kind
        main.removeCallbacks(flush)
        main.postDelayed(flush, DEBOUNCE_MS)
    }

    /** Starts or stops each source to match the widgets placed now. Call on the main thread. */
    fun sync() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { sync() }
            return
        }
        val placed = WidgetKind.entries.filter { HomeWidgets.ids(app, it).isNotEmpty() }.toSet()
        battery(WidgetKind.BATTERY in placed)
        notifications(WidgetKind.NEEDS_YOU in placed || WidgetKind.NOW_PLAYING in placed)
        media(WidgetKind.NOW_PLAYING in placed && Sources.notificationAccess(app))
        apps(WidgetKind.PADS in placed || WidgetKind.NEEDS_YOU in placed || WidgetKind.NOW_PLAYING in placed)
    }

    // --- battery ---

    private var batteryOn = false
    private var batteryKey: String? = null
    private var batteryAt = 0L
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val reading = Sources.battery(app, intent)
            val now = System.currentTimeMillis()
            if (BatteryModel.shouldRedraw(batteryKey, batteryAt, reading, now)) {
                batteryKey = BatteryModel.redrawKey(reading)
                batteryAt = now
                request(WidgetKind.BATTERY)
            }
        }
    }

    private fun battery(on: Boolean) {
        if (on == batteryOn) return
        batteryOn = on
        if (on) {
            batteryKey = null // the sticky broadcast delivered on registering redraws at once
            ContextCompat.registerReceiver(app, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            runCatching { app.unregisterReceiver(batteryReceiver) }
        }
    }

    // --- notifications (and notification access) ---

    private var notificationsJob: Job? = null
    private var lastAccess: Boolean? = null

    private fun notifications(on: Boolean) {
        if (on == (notificationsJob != null)) return
        if (!on) {
            notificationsJob?.cancel()
            notificationsJob = null
            lastAccess = null
            return
        }
        notificationsJob = scope.launch {
            // Emits on connect / disconnect (null) and whenever the active set changes.
            NotificationProvider.active.collect {
                request(WidgetKind.NEEDS_YOU)
                val access = Sources.notificationAccess(app)
                if (access != lastAccess) {
                    lastAccess = access
                    media(access && HomeWidgets.ids(app, WidgetKind.NOW_PLAYING).isNotEmpty())
                    request(WidgetKind.NOW_PLAYING)
                }
            }
        }
    }

    // --- media sessions ---

    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val watched = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private fun media(on: Boolean) {
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return
        if (on == (sessionsListener != null)) return
        if (on) {
            val l = MediaSessionManager.OnActiveSessionsChangedListener { list ->
                watch(list.orEmpty())
                request(WidgetKind.NOW_PLAYING)
            }
            val ok = runCatching { msm.addOnActiveSessionsChangedListener(l, Sources.listenerComponent(app), main) }.isSuccess
            if (!ok) return
            sessionsListener = l
            watch(Sources.activeControllers(app).orEmpty())
        } else {
            sessionsListener?.let { runCatching { msm.removeOnActiveSessionsChangedListener(it) } }
            sessionsListener = null
            watch(emptyList())
        }
    }

    private fun watch(controllers: List<MediaController>) {
        val keep = controllers.associateBy { it.sessionToken }
        watched.keys.filter { it !in keep }.forEach { token ->
            watched.remove(token)?.let { (mc, cb) -> runCatching { mc.unregisterCallback(cb) } }
        }
        for ((token, mc) in keep) {
            if (token in watched) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = request(WidgetKind.NOW_PLAYING)
                override fun onMetadataChanged(metadata: MediaMetadata?) = request(WidgetKind.NOW_PLAYING)
                override fun onSessionDestroyed() = request(WidgetKind.NOW_PLAYING)
            }
            runCatching { mc.registerCallback(cb, main) }.onSuccess { watched[token] = mc to cb }
        }
    }

    // --- launchable apps and the hidden set ---

    private var appsOn = false
    private val appsCallback = object : LauncherApps.Callback() {
        private fun changed() {
            request(WidgetKind.PADS, force = true) // icons may have changed
            request(WidgetKind.NEEDS_YOU)
            request(WidgetKind.NOW_PLAYING)
        }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed()
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed()
    }

    // Held as a field: SharedPreferences keeps only weak references to its listeners.
    private val hiddenListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        request(WidgetKind.PADS)
        request(WidgetKind.NEEDS_YOU)
        request(WidgetKind.NOW_PLAYING)
    }

    private fun apps(on: Boolean) {
        if (on == appsOn) return
        appsOn = on
        val la = app.getSystemService(LauncherApps::class.java)
        val hidden = app.getSharedPreferences(HiddenAppsStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (on) {
            runCatching { la?.registerCallback(appsCallback, main) }
            hidden.registerOnSharedPreferenceChangeListener(hiddenListener)
        } else {
            runCatching { la?.unregisterCallback(appsCallback) }
            hidden.unregisterOnSharedPreferenceChangeListener(hiddenListener)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 400L

        @Volatile
        private var instance: WidgetRuntime? = null

        fun get(context: Context): WidgetRuntime =
            instance ?: synchronized(this) { instance ?: WidgetRuntime(context.applicationContext).also { instance = it } }
    }
}
