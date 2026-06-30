package com.bos.sphere.core.data

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
 * Singleton provider for the notification stream. The actual service runs in the system
 * notification daemon; this manager provides a Flow API for composables.
 */
object NotificationProvider {
    private var listener: NotificationListenerService? = null
    private val listeners = mutableListOf<(List<HubNotification>) -> Unit>()

    val notifications: Flow<List<HubNotification>> = callbackFlow {
        val callback: (List<HubNotification>) -> Unit = { notifs ->
            trySend(notifs)
        }
        listeners.add(callback)
        awaitClose { listeners.remove(callback) }
    }

    internal fun onNotificationsChanged(notifs: List<HubNotification>) {
        listeners.forEach { it(notifs) }
    }

    internal fun setListener(svc: NotificationListenerService?) {
        listener = svc
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
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    private fun updateNotifications() {
        val current = activeNotifications?.mapNotNull { sbn ->
            parseNotification(sbn)
        }?.sortedByDescending { it.timestamp } ?: emptyList()
        NotificationProvider.onNotificationsChanged(current)
    }

    private fun parseNotification(sbn: StatusBarNotification): HubNotification? {
        val n = sbn.notification ?: return null
        val title = n.extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = n.extras.getString(Notification.EXTRA_TEXT) ?: ""

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
        /** Check if notification access is granted (user has enabled it in system settings). */
        fun isEnabled(context: Context): Boolean {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                nm?.isNotificationPolicyAccessGranted == true
            } else {
                false
            }
        }

        /** Intent to open system notification access settings (helpful for onboarding). */
        fun notificationAccessIntent(): Intent =
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }
}
