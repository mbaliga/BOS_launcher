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
    /** The notification asked to be cancelled when its content intent is used (Notification.FLAG_AUTO_CANCEL). */
    val autoCancel: Boolean = false,
    /** HOM-09: the profile (user id) the notification belongs to, 0 for the owner. */
    val profileId: Int = 0,
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
    /** W42: the conversation shortcut the app published for this notification (Notification.getShortcutId), the deep link when the intent is gone. */
    val shortcutId: String? = null,
)

data class HubAction(
    val label: String,
    val action: suspend () -> Unit = {},
    /** The notification's own PendingIntent for this action (null when it has none). */
    val intent: PendingIntent? = null,
    /** Set when the action takes inline text (a RemoteInput): Launchpad shows a reply row for it. */
    val reply: ReplyTarget? = null,
)

/** The text field a notification action offers. [inputs] are the platform RemoteInputs the answer is written into. */
class ReplyTarget(
    val inputs: Array<out android.app.RemoteInput>,
    val resultKey: String,
    val hint: String?,
    val freeForm: Boolean = true,
)

/** Pure choice and validation for inline replies (STR-02). */
object ReplyLogic {
    /** The action to offer as the reply row: the first free-form one, else the first with any input. */
    fun pick(actions: List<HubAction>): HubAction? =
        actions.firstOrNull { it.reply?.freeForm == true && it.intent != null } ?: actions.firstOrNull { it.reply != null && it.intent != null }

    fun canSend(text: String) = text.isNotBlank()

    /** Actions other than the reply, in the notification's own order, that can actually be sent. */
    fun plain(actions: List<HubAction>): List<HubAction> = actions.filter { it.reply == null && it.intent != null }

    /**
     * Sends [text] through [action]'s own PendingIntent using the platform RemoteInput result route.
     * Returns false when the intent is gone (the app cancelled the notification meanwhile).
     */
    fun send(context: Context, action: HubAction, text: String): Boolean {
        val target = action.reply ?: return false
        val pi = action.intent ?: return false
        if (!canSend(text)) return false
        return runCatching {
            val fill = Intent()
            val results = android.os.Bundle().apply { putCharSequence(target.resultKey, text) }
            android.app.RemoteInput.addResultsToIntent(target.inputs, fill, results)
            check(PendingIntentLauncher.fire(pi, context, fill))
        }.isSuccess
    }

    /** Runs a non-reply action through its own PendingIntent. */
    fun run(action: HubAction): Boolean = PendingIntentLauncher.fire(action.intent)
}

/**
 * Singleton provider for the notification stream. The actual service runs in the system
 * notification daemon; this manager provides a Flow API for composables.
 */
/**
 * Wave 23 sweep: the one bound instance of a service. The platform may create the next instance before the old one's onDestroy runs
 * (a rebind after the user toggles notification access), and a late release by the OLD instance must not unbind the NEW one.
 */
internal class BoundSlot<T : Any> {
    @Volatile var current: T? = null
        private set
    fun bind(instance: T) { current = instance }
    /** Releases only when [instance] is still the bound one; true when it was. */
    fun release(instance: T): Boolean { if (current !== instance) return false; current = null; return true }
    /** Wave 23 second review: whether [instance] is the bound one; a late callback of an old instance (its disconnect, a last update) must not act for the new one. */
    fun owns(instance: T): Boolean = current === instance
}

object NotificationProvider {
    private val slot = BoundSlot<NotificationListenerService>()
    private val _state = MutableStateFlow<List<HubNotification>>(emptyList())

    /** Current notifications (newest first). Replays the latest value to new collectors. */
    val notifications: Flow<List<HubNotification>> get() =
        kotlinx.coroutines.flow.combine(_state, displayFilter) { list, allowed -> if (allowed == null) list else list.filter { it.packageName in allowed } }

    /**
     * The same list as [notifications] right now, readable without collecting. A collector that starts with this as its
     * initial value never sees an empty first frame, so nothing already waiting looks new (STR-09).
     */
    val snapshot: List<HubNotification> get() = _state.value.let { list -> displayFilter.value?.let { allowed -> list.filter { it.packageName in allowed } } ?: list }

    /** Display-only filter set by the active workspace (WSP-08): the apps allowed to show; null shows everything. */
    val displayFilter = MutableStateFlow<Set<String>?>(null)

    /** Test seam (the listener service has no system behind it in a unit test): publishes [list] as the current feed. */
    fun publishForTest(list: List<HubNotification>) { _state.value = list }

    internal fun onNotificationsChanged(notifs: List<HubNotification>) {
        _state.value = notifs
        NotificationIntents.observe(notifs)
        NotificationHistory.observe(notifs)
    }

    internal fun bindListener(svc: NotificationListenerService) = slot.bind(svc)

    /** The list as [svc] reads it; ignored when [svc] is an old instance that a newer one has replaced. */
    internal fun listedBy(svc: NotificationListenerService, notifs: List<HubNotification>) { if (slot.owns(svc)) { _state.value = notifs; NotificationIntents.observe(notifs); NotificationHistory.observe(notifs) } }

    /** [svc] lost its connection: its list is no longer live. A late disconnect of an old instance leaves the new one's list alone. */
    internal fun disconnectedBy(svc: NotificationListenerService) { if (slot.owns(svc)) _state.value = emptyList() }

    /** The service went away. Only the instance that is bound now may clear the feed. */
    internal fun releaseListener(svc: NotificationListenerService) { if (slot.release(svc)) _state.value = emptyList() }

    /** Remove one notification from the system shade (no-op if the listener is not bound). */
    fun dismiss(key: String) {
        runCatching { slot.current?.cancelNotification(key) }
    }

    /** Clear every clearable notification (no-op if the listener is not bound). */
    fun clearAll() {
        runCatching { slot.current?.cancelAllNotifications() }
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
        NotificationHistory.init(applicationContext) // wave 25: history records every post from here on
        NotificationProvider.bindListener(this)
    }

    override fun onDestroy() {
        NotificationProvider.releaseListener(this)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        MediaNow.onAccessMayHaveChanged()
        updateNotifications()
    }

    /** Access was turned off or the system dropped the binding: what was listed is no longer live, so it must not stay on screen. */
    override fun onListenerDisconnected() {
        NotificationProvider.disconnectedBy(this)
        super.onListenerDisconnected()
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
        NotificationProvider.listedBy(this, current)
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
            profileId = sbn.user.hashCode(),
            contentIntent = n.contentIntent,
            autoCancel = (n.flags and android.app.Notification.FLAG_AUTO_CANCEL) != 0,
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
            shortcutId = n.shortcutId?.ifBlank { null },
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

    private fun parseActions(n: Notification): List<HubAction> = NotificationActions.parse(n)

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
