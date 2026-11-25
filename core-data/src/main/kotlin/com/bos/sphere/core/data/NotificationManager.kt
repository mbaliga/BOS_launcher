package com.bos.sphere.core.data

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real notification feed from the system, triaged and formatted for display in the Hub.
 * Requires `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the app to be set
 * as the default listener (system Settings > Notifications > Notification access).
 *
 * Emits a live [Flow<List<HubNotification>>] as notifications arrive and are dismissed.
 */
data class HubNotification(
    val id: String,
    val key: String,
    val source: String,      // "Messages", "Calendar", etc.
    val title: String,
    val summary: String,
    val icon: Int = 0,       // drawable res; 0 for default
    val timestamp: Long,
    val actions: List<HubAction> = emptyList(),
)

data class HubAction(
    val label: String,
    val action: suspend () -> Unit = {},
)

/**
 * One active notification reduced to what a count needs: the posting package, the profile it was
 * posted in and two flags. No title, text, people or extras. Feeds the pages runtime's
 * `launcher.notifications` channel and Sphere's Needs you home-screen widget (counts and app names
 * only). [userSerial] is the posting profile's `UserManager` serial number (a personal-profile
 * listener also receives the work profile's notifications), or -1 when it couldn't be read.
 */
data class ActiveNotification(
    val packageName: String,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val userSerial: Long = -1L,
)

/**
 * Singleton provider for the notification stream. The actual service runs in the system
 * notification daemon; this manager provides a Flow API for composables.
 */
object NotificationProvider {
    private var listener: NotificationListenerService? = null
    private val _notifications = MutableStateFlow<List<HubNotification>>(emptyList())

    /**
     * The current triaged notifications. A StateFlow, so a collector that starts late (the hub
     * opening, perhaps from a Needs you widget tap) gets the list at once instead of waiting for
     * the next notification to be posted or removed. Empty while the listener isn't connected.
     */
    val notifications: StateFlow<List<HubNotification>> = _notifications.asStateFlow()

    internal fun onNotificationsChanged(notifs: List<HubNotification>) {
        _notifications.value = notifs
    }

    internal fun setListener(svc: NotificationListenerService?) {
        listener = svc
    }

    private val _active = MutableStateFlow<List<ActiveNotification>?>(null)

    /**
     * Every active notification as [ActiveNotification] (package and flags only), or null until the
     * listener has connected (notification access not granted, or not bound yet).
     */
    val active: StateFlow<List<ActiveNotification>?> = _active.asStateFlow()

    internal fun onActiveChanged(list: List<ActiveNotification>?) {
        _active.value = list
    }
}

/**
 * System notification listener. Must be bound via manifest + user permission grant.
 * Filters for "interesting" notifications (messages, calendar, alerts, app updates).
 *
 * To enable on a device:
 * - System Settings > Notifications > Notification access (if available)
 * - Grant "Sphere Launcher" permission to access notifications
 */
class SphereNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        NotificationProvider.setListener(this)
    }

    override fun onDestroy() {
        NotificationProvider.setListener(null)
        NotificationProvider.onActiveChanged(null)
        NotificationProvider.onNotificationsChanged(emptyList())
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Both feeds start from what is already in the shade, not from the next post.
        updateNotifications()
    }

    override fun onListenerDisconnected() {
        NotificationProvider.onActiveChanged(null)
        NotificationProvider.onNotificationsChanged(emptyList())
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    /** Package, profile and flags of every active notification, for counts (no content). */
    private fun publishActive(active: Array<StatusBarNotification>?) {
        val um = getSystemService(UserManager::class.java)
        val serials = HashMap<android.os.UserHandle, Long>()
        val facts = active?.map { sbn ->
            val flags = sbn.notification?.flags ?: 0
            ActiveNotification(
                packageName = sbn.packageName,
                isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0,
                userSerial = serials.getOrPut(sbn.user) { runCatching { um?.getSerialNumberForUser(sbn.user) ?: -1L }.getOrDefault(-1L) },
            )
        }
        NotificationProvider.onActiveChanged(facts)
    }

    private fun updateNotifications() {
        // Throws if the listener isn't connected (yet, or any more).
        val active = runCatching { activeNotifications }.getOrNull()
        publishActive(active)
        val current = active?.mapNotNull { sbn ->
            parseNotification(sbn)
        }?.sortedByDescending { it.timestamp } ?: emptyList()
        NotificationProvider.onNotificationsChanged(current)
    }

    private fun parseNotification(sbn: StatusBarNotification): HubNotification? {
        val n = sbn.notification ?: return null
        // getCharSequence: apps often post styled titles, which getString would read as null.
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Filter: only show messaging, calendar, and "interesting" notifications
        val tag = sbn.tag ?: ""
        val pkg = sbn.packageName
        val source = categorizeSource(pkg, tag, n)

        return HubNotification(
            id = "${pkg}:${sbn.key}",
            key = sbn.key,
            source = source,
            title = title,
            summary = text,
            timestamp = sbn.postTime,
            actions = parseActions(n),
        )
    }

    private fun categorizeSource(pkg: String, tag: String, n: Notification): String {
        return when {
            pkg.contains("message", ignoreCase = true) ||
            pkg.contains("sms", ignoreCase = true) ||
            pkg.contains("chat", ignoreCase = true) -> "Messages"

            pkg.contains("calendar", ignoreCase = true) -> "Calendar"

            pkg.contains("mail", ignoreCase = true) ||
            pkg.contains("gmail", ignoreCase = true) -> "Email"

            pkg.contains("play", ignoreCase = true) ||
            pkg.contains("store", ignoreCase = true) -> "App Store"

            n.category == Notification.CATEGORY_ALARM -> "Alarms"
            n.category == Notification.CATEGORY_REMINDER -> "Reminders"
            n.category == Notification.CATEGORY_SOCIAL -> "Social"

            else -> "Notifications"
        }
    }

    private fun parseActions(n: Notification): List<HubAction> {
        // Actions are system-level; for M3 MVP, we'll just list them by label.
        // Full action dispatch (reply, snooze, etc.) is M4+ with PendingIntent wiring.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            n.actions?.mapNotNull { action ->
                HubAction(label = action.title.toString())
            } ?: emptyList()
        } else {
            emptyList()
        }
    }

    companion object {
        /**
         * Whether notification access is granted to this listener (user has enabled it in system
         * settings). Listener access, not Do Not Disturb access (`isNotificationPolicyAccessGranted`,
         * which this used to check by mistake).
         */
        fun isEnabled(context: Context): Boolean {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return runCatching {
                nm?.isNotificationListenerAccessGranted(ComponentName(context, SphereNotificationListener::class.java)) == true
            }.getOrDefault(false)
        }

        /** Intent to open system notification access settings (helpful for onboarding). */
        fun notificationAccessIntent(): Intent =
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }
}
