package com.bos.sphere.core.data

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real notification feed from the system, formatted for the Hub / Launchpad.
 * Requires `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the user to grant
 * notification access (system Settings > Notifications > Notification access).
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
    val packageName: String = "",
    val appLabel: String = "",
    val unread: Boolean = true,
    val contentIntent: PendingIntent? = null,
    val isOngoing: Boolean = false,
    val category: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val groupKey: String? = null,
    /** Latest message's sender for MessagingStyle, else the title. */
    val sender: String? = null,
    /** Conversation / chat title (MessagingStyle conversation title, else the title). */
    val conversation: String? = null,
    val isMessaging: Boolean = false,
    val priority: Int = 0,
    /** True when the notification carries a progress bar. */
    val progress: Boolean = false,
    /** "inbox", "bigtext", "messaging", "media", "call", "bigpicture"... or null. */
    val style: String? = null,
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
    private val _state = MutableStateFlow<List<HubNotification>>(emptyList())

    /** Current notifications (newest first). Replays the latest value to new collectors. */
    val notifications: Flow<List<HubNotification>> get() =
        kotlinx.coroutines.flow.combine(_state, displayFilter) { list, allowed -> if (allowed == null) list else list.filter { it.packageName in allowed } }

    /** Display-only filter set by the active workspace (WSP-08): the apps allowed to show; null shows everything. */
    val displayFilter = MutableStateFlow<Set<String>?>(null)

    internal fun onNotificationsChanged(notifs: List<HubNotification>) {
        _state.value = notifs
    }

    internal fun setListener(svc: NotificationListenerService?) {
        listener = svc
        if (svc == null) _state.value = emptyList()
    }

    /** Remove one notification from the system shade (no-op if the listener is not bound). */
    fun dismiss(key: String) {
        runCatching { listener?.cancelNotification(key) }
    }

    /** Clear every clearable notification (no-op if the listener is not bound). */
    fun clearAll() {
        runCatching { listener?.cancelAllNotifications() }
    }
}

/**
 * System notification listener. Must be bound via manifest + user permission grant.
 * Emits every kind of notification from every app (messages, mail, calls, media, progress,
 * system...). Skipped: our own ongoing notices, other apps' bare "service running" ongoing
 * notices (anything ongoing that is not a call / media / progress / navigation / alarm), and
 * group summaries whose children are present (pure duplicates).
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

    override fun onListenerConnected() {
        updateNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    private val labelCache = HashMap<String, String>()

    private fun updateNotifications() {
        val all = runCatching { activeNotifications }.getOrNull()?.toList() ?: emptyList()
        val groupsWithChildren = all
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .map { it.packageName to it.notification.group }
            .toSet()
        val current = all.mapNotNull { sbn ->
            val n = sbn.notification
            val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            if (summary && (sbn.packageName to n.group) in groupsWithChildren) null
            else runCatching { parseNotification(sbn) }.getOrNull()
        }.sortedByDescending { it.timestamp }
        NotificationProvider.onNotificationsChanged(current)
    }

    private fun appLabelOf(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    @Suppress("DEPRECATION")
    private fun parseNotification(sbn: StatusBarNotification): HubNotification? {
        val n = sbn.notification ?: return null
        val pkg = sbn.packageName
        val ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        val cat = n.category
        if (ongoing) {
            if (pkg == packageName) return null
            if (cat !in KEEP_ONGOING_CATEGORIES && !hasProgress(n)) return null
        }
        val ex = n.extras
        val titleRaw = (ex.getCharSequence(Notification.EXTRA_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString()?.trim().orEmpty()
        var text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()

        var style = ex.getString(Notification.EXTRA_TEMPLATE)
            ?.substringAfterLast('$')?.removeSuffix("Style")?.lowercase()?.ifEmpty { null }
        if (cat == Notification.CATEGORY_CALL) style = "call"
        val messaging = style == "messaging" || cat == Notification.CATEGORY_MESSAGE

        var sender: String? = null
        var conversation: String? = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.ifBlank { null }
        if (style == "messaging") {
            val last = runCatching {
                Notification.MessagingStyle.Message
                    .getMessagesFromBundleArray(ex.getParcelableArray(Notification.EXTRA_MESSAGES))
            }.getOrNull().orEmpty().lastOrNull()
            if (last != null) {
                sender = (last.senderPerson?.name ?: last.sender)?.toString()?.ifBlank { null }
                if (!last.text.isNullOrBlank()) text = last.text.toString()
            }
        }
        if (text.isEmpty() && style == "inbox") {
            text = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString().orEmpty()
        }
        val title = titleRaw.ifEmpty { sender ?: text.ifEmpty { return null } }
        if (sender == null) sender = titleRaw.ifEmpty { null }
        if (conversation == null) conversation = titleRaw.ifEmpty { null }

        val channelName = runCatching {
            val r = Ranking()
            if (currentRanking.getRanking(sbn.key, r)) r.channel?.name?.toString() else null
        }.getOrNull()

        return HubNotification(
            id = "${pkg}:${sbn.key}",
            key = sbn.key,
            source = categorizeSource(pkg, sbn.tag ?: "", n),
            title = title,
            summary = text,
            timestamp = if (sbn.postTime > 0) sbn.postTime else n.`when`,
            actions = parseActions(n),
            packageName = pkg,
            appLabel = appLabelOf(pkg),
            unread = true,
            contentIntent = n.contentIntent,
            isOngoing = ongoing,
            category = cat,
            channelId = n.channelId,
            channelName = channelName,
            groupKey = n.group,
            sender = sender,
            conversation = conversation,
            isMessaging = messaging,
            priority = n.priority,
            progress = hasProgress(n),
            style = style,
        )
    }

    private fun hasProgress(n: Notification): Boolean =
        n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

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

    private fun parseActions(n: Notification): List<HubAction> =
        n.actions?.mapNotNull { action ->
            action.title?.toString()?.let { HubAction(label = it) }
        } ?: emptyList()

    companion object {
        /** True when the user has granted this app notification access. */
        fun isEnabled(context: Context): Boolean {
            val flat = runCatching {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            }.getOrNull() ?: return false
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }

        private val KEEP_ONGOING_CATEGORIES = setOf(
            Notification.CATEGORY_CALL, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_ALARM, Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_WORKOUT,
        )

        /** Intent to open system notification access settings (helpful for onboarding). */
        fun notificationAccessIntent(): Intent =
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }
}
