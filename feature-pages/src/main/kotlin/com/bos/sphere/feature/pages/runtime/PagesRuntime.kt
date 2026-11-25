package com.bos.sphere.feature.pages.runtime

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.SphereNotificationListener
import com.bos.sphere.feature.pages.Envelope
import com.bos.sphere.feature.pages.LauncherChannelData
import com.bos.sphere.feature.pages.NotificationFact
import com.bos.sphere.feature.pages.PageFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Someone listening on channels: a live D-AC widget's host bridge. */
interface ChannelSubscriber {
    /** The widget's id (an envelope's `from`), so a widget never receives its own publications. */
    val widgetId: String

    fun deliver(envelope: Map<String, Any?>)
}

/**
 * Channels between live widgets inside the launcher (what one widget `publish`es on a channel it
 * declared in `writes`, the others that declared it in `reads` and called `on` receive). In
 * process, main thread; nothing leaves the device.
 */
class ChannelBus {
    private val subs = HashMap<String, LinkedHashSet<ChannelSubscriber>>()

    fun subscribe(channel: String, s: ChannelSubscriber) {
        subs.getOrPut(channel) { LinkedHashSet() } += s
    }

    fun unsubscribe(s: ChannelSubscriber) {
        subs.values.forEach { it -= s }
        subs.values.removeAll { it.isEmpty() }
    }

    fun publish(envelope: Map<String, Any?>, from: ChannelSubscriber) {
        val channel = envelope["channel"] as? String ?: return
        subs[channel]?.toList()?.forEach { if (it !== from) it.deliver(envelope) }
    }
}

/**
 * The channels the launcher itself publishes (and nothing else publishes yet): `launcher.clock`
 * (each minute), `launcher.battery` (on change) and `launcher.notifications` (counts and app names
 * only, never content; needs notification access). Each source runs only while some live widget
 * listens to it, and every new listener gets the current value at once. Main thread.
 */
class LauncherChannels(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val subs = HashMap<String, LinkedHashSet<ChannelSubscriber>>()
    private val last = HashMap<String, Map<String, Any?>>()
    private val labels = HashMap<String, String>()

    private val tick = object : Runnable {
        override fun run() {
            publishClock()
            main.postDelayed(this, LauncherChannelData.millisToNextMinute(System.currentTimeMillis()))
        }
    }
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = publishBattery(intent)
    }
    private var notificationsJob: Job? = null

    fun subscribe(channel: String, s: ChannelSubscriber) {
        val set = subs.getOrPut(channel) { LinkedHashSet() }
        val first = set.isEmpty()
        if (!set.add(s)) return
        // The first listener starts the source, which publishes its current value to everyone;
        // later listeners get the last value at once.
        if (first) start(channel) else last[channel]?.let { s.deliver(it) }
    }

    fun unsubscribe(s: ChannelSubscriber) {
        for ((channel, set) in subs) {
            if (set.remove(s) && set.isEmpty()) stop(channel)
        }
    }

    private fun start(channel: String) {
        when (channel) {
            LauncherChannelData.CLOCK -> {
                publishClock()
                main.postDelayed(tick, LauncherChannelData.millisToNextMinute(System.currentTimeMillis()))
            }
            LauncherChannelData.BATTERY -> {
                val sticky = ContextCompat.registerReceiver(
                    app, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                sticky?.let { publishBattery(it) }
            }
            LauncherChannelData.NOTIFICATIONS -> {
                notificationsJob = scope.launch {
                    NotificationProvider.active.collect { publishNotifications(it) }
                }
            }
        }
    }

    private fun stop(channel: String) {
        last.remove(channel)
        when (channel) {
            LauncherChannelData.CLOCK -> main.removeCallbacks(tick)
            LauncherChannelData.BATTERY -> runCatching { app.unregisterReceiver(battery) }
            LauncherChannelData.NOTIFICATIONS -> {
                notificationsJob?.cancel()
                notificationsJob = null
            }
        }
    }

    private fun publish(channel: String, ttl: Int, data: Map<String, Any?>) {
        val envelope = Envelope.build(channel, System.currentTimeMillis(), ZoneId.systemDefault(), "launcher", ttl, data)
        last[channel] = envelope
        subs[channel]?.toList()?.forEach { it.deliver(envelope) }
    }

    private fun publishClock() {
        publish(
            LauncherChannelData.CLOCK,
            LauncherChannelData.CLOCK_TTL,
            LauncherChannelData.clock(System.currentTimeMillis(), ZoneId.systemDefault(), DateFormat.is24HourFormat(app)),
        )
    }

    private fun publishBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        publish(LauncherChannelData.BATTERY, LauncherChannelData.BATTERY_TTL, LauncherChannelData.battery(level * 100 / scale, charging))
    }

    private fun publishNotifications(active: List<com.bos.sphere.core.data.ActiveNotification>?) {
        val access = hasNotificationAccess()
        val facts = active.orEmpty().map { NotificationFact(it.packageName, labelOf(it.packageName), it.isGroupSummary, it.isOngoing) }
        publish(
            LauncherChannelData.NOTIFICATIONS,
            LauncherChannelData.NOTIFICATIONS_TTL,
            LauncherChannelData.notifications(facts, access && active != null),
        )
    }

    private fun hasNotificationAccess(): Boolean = runCatching {
        app.getSystemService(android.app.NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(app, SphereNotificationListener::class.java))
    }.getOrDefault(false)

    private fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}

/** Hosts each html page asked for while it was live: reached (network on) or blocked. */
data class NetLog(val reached: Set<String> = emptySet(), val blocked: Set<String> = emptySet())

/**
 * Process-wide runtime services shared by every page view: imported files, the channel bus, the
 * launcher's own channels, and the per-page network log (in memory only: it names the hosts a page
 * reached or was refused this session, for the page's info; nothing is persisted or sent anywhere).
 */
class PagesRuntime private constructor(context: Context) {
    val files = PageFiles(context)
    val bus = ChannelBus()
    val channels = LauncherChannels(context)

    private val _net = MutableStateFlow<Map<String, NetLog>>(emptyMap())
    val net: StateFlow<Map<String, NetLog>> = _net.asStateFlow()

    /** Called from the WebView's request thread. */
    fun recordReached(pageId: String, host: String) {
        if (host.isEmpty()) return
        _net.update { m -> m + (pageId to (m[pageId] ?: NetLog()).let { it.copy(reached = it.reached + host) }) }
    }

    fun recordBlocked(pageId: String, host: String) {
        if (host.isEmpty()) return
        _net.update { m -> m + (pageId to (m[pageId] ?: NetLog()).let { it.copy(blocked = it.blocked + host) }) }
    }

    fun forgetNet(pageId: String) {
        _net.update { it - pageId }
    }

    companion object {
        @Volatile
        private var instance: PagesRuntime? = null

        fun get(context: Context): PagesRuntime =
            instance ?: synchronized(this) { instance ?: PagesRuntime(context.applicationContext).also { instance = it } }
    }
}
