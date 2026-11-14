package com.bos.sphere.feature.desktop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.bos.sphere.core.capabilities.CapabilityRegistry
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.LaunchExecutor
import com.bos.sphere.core.launch.LaunchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Wires the pure pieces to the platform: tracks displays, decides what to offer, opens the desktop surface
 * through [LaunchExecutor] and keeps a "reopen" notification while a usable display is connected (EXT-01, EXT-02).
 * One instance per process; started by the home activity or the desktop activity, whichever comes first.
 */
class DesktopController private constructor(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val source = DisplaySource(app)
    private val sp = app.getSharedPreferences("desktop_surface", Context.MODE_PRIVATE)
    val profiles = DesktopProfileRepository(object : KeyValue {
        override fun get(key: String) = sp.getString(key, null)
        override fun put(key: String, value: String) { sp.edit().putString(key, value).apply() }
    })
    private val executor = LaunchExecutor(app, caps = { currentCaps() })

    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state
    private val _effects = MutableSharedFlow<DisplayEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<DisplayEffect> = _effects
    @Volatile private var started = false

    var offerSetting: OfferSetting
        get() = runCatching { OfferSetting.valueOf(sp.getString(KEY_SETTING, null) ?: "") }.getOrDefault(OfferSetting.Ask)
        set(v) { sp.edit().putString(KEY_SETTING, v.name).apply() }

    /** EXT-06: migrate to the primary display on disconnect instead of closing. Default is close. */
    var migrateOnDisconnect: Boolean
        get() = sp.getBoolean(KEY_MIGRATE, false)
        set(v) { sp.edit().putBoolean(KEY_MIGRATE, v).apply() }

    fun start() {
        if (started) return
        started = true
        scope.launch { source.changes().collect { apply(it) } }
        scope.launch { DesktopSurfaces.homeDisplays.collect { refreshReopen() } }
        scope.launch { DesktopSurfaces.surfaces.collect { refreshReopen() } }
    }

    /** One display callback becomes one synchronous reduce, then effects (EXT-01). */
    private fun apply(snapshot: List<TrackedDisplay>) {
        val (next, effects) = DisplayTracker.sync(_state.value, snapshot)
        _state.value = next
        for (e in effects) {
            _effects.tryEmit(e)
            when (e) {
                is DisplayEffect.Connected -> onConnected(e.display)
                is DisplayEffect.Disconnected -> { cancelNotifications(e.display.id) }
                is DisplayEffect.Updated -> Unit
            }
        }
        refreshReopen()
    }

    private fun onConnected(d: TrackedDisplay) {
        val decision = DesktopPolicy.onConnected(
            d, offerSetting, offeredBefore = offered(d.identity),
            platformProvidesHome = DesktopPolicy.platformProvidesHome(DesktopSurfaces.homeDisplayIds(), d.id),
            surfaceOpenHere = DesktopSurfaces.openOn(d.id),
        )
        when (decision) {
            OfferDecision.OpenNow -> {
                openSurface(d.id)
                // A launch from the background may be silently blocked; if nothing registers shortly, fall back to the offer.
                scope.launch { delay(2500); if (!DesktopSurfaces.openOn(d.id) && _state.value.displays.containsKey(d.id)) postOffer(d) }
            }
            OfferDecision.Prompt -> { markOffered(d.identity); postOffer(d) }
            is OfferDecision.Skip -> Unit
        }
    }

    /** Opens the desktop surface on [displayId] through core-launch (EXT-02). */
    fun openSurface(displayId: Int): LaunchResult {
        val req = DesktopLaunch.request(app.packageName, SURFACE_CLASS, 0, displayId)
        return executor.launch(req).also { showNotices(it) }
    }

    /** Opens an app on [displayId]; reports a dropped placement once per reason (EXT-07, WSP-01). */
    fun launchApp(packageName: String, className: String?, userId: Int, displayId: Int): LaunchResult =
        executor.launch(DesktopLaunch.request(packageName, className, userId, displayId)).also { showNotices(it) }

    /** Entry point for a palette action or dock item (EXT-02): reopen the surface on the first usable display. */
    fun reopenAction(): ReopenAction? {
        val d = _state.value.eligible.firstOrNull { !DesktopSurfaces.openOn(it.id) } ?: return null
        return ReopenAction(L10n.tr("Open desktop on %s", d.name)) { openSurface(d.id) }
    }

    data class ReopenAction(val label: String, val run: () -> Unit)

    private fun showNotices(r: LaunchResult) {
        executor.freshNotices(r).forEach { Toast.makeText(app, L10n.tr(it.message), Toast.LENGTH_SHORT).show() }
    }

    private fun currentCaps() = DesktopLaunch.caps(
        _state.value,
        boundsHonoured = runCatching { CapabilityRegistry(app).snapshot().desktopWindowing == true }.getOrDefault(false),
    )

    // ------------------------------------------------------------------ offered-once bookkeeping
    private fun offered(identity: String) = sp.getStringSet(KEY_OFFERED, emptySet())?.contains(identity) == true
    private fun markOffered(identity: String) {
        sp.edit().putStringSet(KEY_OFFERED, (sp.getStringSet(KEY_OFFERED, emptySet()) ?: emptySet()) + identity).apply()
    }

    // ------------------------------------------------------------------ notifications
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, L10n.tr("Desktop on external display"), NotificationManager.IMPORTANCE_LOW))
    }

    private fun surfaceIntent(d: TrackedDisplay): PendingIntent {
        val intent = Intent().setComponent(ComponentName(app.packageName, SURFACE_CLASS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        // A notification cannot go through LaunchExecutor; it carries the same public launch-display option.
        val opts = android.app.ActivityOptions.makeBasic().also { it.launchDisplayId = d.id }.toBundle()
        return PendingIntent.getActivity(app, d.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, opts)
    }

    private fun setting(d: TrackedDisplay, s: OfferSetting): PendingIntent =
        PendingIntent.getBroadcast(
            app, d.id * 10 + s.ordinal + 1,
            Intent(app, DesktopOfferReceiver::class.java).putExtra(EXTRA_SETTING, s.name).putExtra(EXTRA_DISPLAY, d.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun postOffer(d: TrackedDisplay) {
        runCatching {
            ensureChannel()
            val n = android.app.Notification.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_slideshow)
                .setContentTitle(L10n.tr("Open the desktop on %s?", d.name))
                .setContentText(L10n.tr("Liftoff can show its desktop surface on this display."))
                .setContentIntent(surfaceIntent(d))
                .addAction(android.app.Notification.Action.Builder(null, L10n.tr("Open"), surfaceIntent(d)).build())
                .addAction(android.app.Notification.Action.Builder(null, L10n.tr("Always"), setting(d, OfferSetting.Always)).build())
                .addAction(android.app.Notification.Action.Builder(null, L10n.tr("Never"), setting(d, OfferSetting.Never)).build())
                .setAutoCancel(true)
                .build()
            nm.notify(OFFER_ID_BASE + d.id, n)
        }
    }

    /** While a usable display is connected and no surface and no system-provided home is there, keep a reopen notification (EXT-02). */
    private fun refreshReopen() {
        runCatching {
            for (d in _state.value.eligible) {
                val needed = !DesktopSurfaces.openOn(d.id) && d.id !in DesktopSurfaces.homeDisplayIds() && offerSetting != OfferSetting.Never
                if (needed && !offerShown(d.id)) {
                    ensureChannel()
                    val n = android.app.Notification.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_menu_slideshow)
                        .setContentTitle(L10n.tr("Desktop closed on %s", d.name))
                        .setContentText(L10n.tr("Tap to open the Liftoff desktop again."))
                        .setContentIntent(surfaceIntent(d))
                        .setOngoing(true)
                        .build()
                    nm.notify(REOPEN_ID_BASE + d.id, n)
                    reopenShown += d.id
                } else if (!needed && offerShown(d.id)) {
                    nm.cancel(REOPEN_ID_BASE + d.id); reopenShown -= d.id
                }
                // EXT-10: while a surface is open on this display, offer the phone as trackpad and keyboard.
                val open = DesktopSurfaces.openOn(d.id)
                if (open && d.id !in padShown) {
                    ensureChannel()
                    val pad = PendingIntent.getActivity(
                        app, PAD_ID_BASE + d.id, Intent().setComponent(ComponentName(app.packageName, PAD_CLASS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    nm.notify(PAD_ID_BASE + d.id, android.app.Notification.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_menu_slideshow)
                        .setContentTitle(L10n.tr("Use this phone as trackpad"))
                        .setContentText(L10n.tr("Controls the Liftoff desktop on %s only.", d.name))
                        .setContentIntent(pad).setOngoing(true).build())
                    padShown += d.id
                } else if (!open && d.id in padShown) { nm.cancel(PAD_ID_BASE + d.id); padShown -= d.id }
            }
        }
    }
    private val reopenShown = mutableSetOf<Int>()
    private val padShown = mutableSetOf<Int>()
    private fun offerShown(id: Int) = id in reopenShown

    private fun cancelNotifications(id: Int) {
        runCatching { nm.cancel(REOPEN_ID_BASE + id); nm.cancel(OFFER_ID_BASE + id); nm.cancel(PAD_ID_BASE + id); reopenShown -= id; padShown -= id }
    }

    /** Applies a choice made on the offer notification. */
    internal fun applyOfferChoice(setting: OfferSetting, displayId: Int) {
        offerSetting = setting
        runCatching { nm.cancel(OFFER_ID_BASE + displayId) }
        if (setting == OfferSetting.Always) _state.value.displays[displayId]?.takeIf { it.eligible && !DesktopSurfaces.openOn(it.id) }?.let { openSurface(it.id) }
        refreshReopen()
    }

    companion object {
        const val SURFACE_CLASS = "com.bos.sphere.DesktopActivity"
        const val EXTRA_SETTING = "setting"
        const val EXTRA_DISPLAY = "display"
        private const val CHANNEL = "desktop_surface"
        private const val OFFER_ID_BASE = 7300
        private const val REOPEN_ID_BASE = 7400
        private const val PAD_ID_BASE = 7500
        const val PAD_CLASS = "com.bos.sphere.PhoneInputActivity"
        private const val KEY_SETTING = "offer_setting"
        private const val KEY_MIGRATE = "migrate_on_disconnect"
        private const val KEY_OFFERED = "offered_identities"

        @Volatile private var instance: DesktopController? = null
        fun get(context: Context): DesktopController =
            instance ?: synchronized(this) { instance ?: DesktopController(context.applicationContext as Application).also { instance = it } }
    }
}

/** Handles the Always and Never buttons of the offer notification. */
class DesktopOfferReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = runCatching { OfferSetting.valueOf(intent.getStringExtra(DesktopController.EXTRA_SETTING) ?: "") }.getOrNull() ?: return
        DesktopController.get(context).applyOfferChoice(s, intent.getIntExtra(DesktopController.EXTRA_DISPLAY, -1))
    }
}
