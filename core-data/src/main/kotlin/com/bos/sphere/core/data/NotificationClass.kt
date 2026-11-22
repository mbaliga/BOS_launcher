package com.bos.sphere.core.data

/**
 * Wave 53 (commsCategories): the three Comms categories and the pure functions that sort a notification into one of them, and
 * that decide whether it is a pinned (ongoing) one. No Android types: the inputs are the plain fields of [HubNotification], the
 * platform category strings are written out as constants so the whole thing runs on the JVM.
 *
 * Definitions (the owner's wording, kept in docs/commsCategories.md and as the captions of the menu):
 * - Messages: notifications addressed to the user personally (chat, SMS, email, calls, mentions, direct replies).
 * - Notifications: app notifications that do not address the user (promos, news, feed updates, app updates, general info).
 * - Alerts: time-sensitive (OTPs, alarms, timers, reminders and calendar events, arrivals, security and bank alerts, warnings).
 *
 * Order when several apply: the owner's Treat-as rule first; then a promo or recommendation category is Notifications (an ad that quotes a
 * "code 4821" is not an OTP); then Alerts beat Messages beat Notifications, so an OTP text message is an Alert.
 */
enum class CommsCategory(val id: String) {
    Messages("messages"), Notifications("notifications"), Alerts("alerts");

    companion object {
        fun fromId(id: String?): CommsCategory? = entries.firstOrNull { it.id == id }
    }
}

object NotificationClass {
    // android.app.Notification.CATEGORY_* written out (they are plain strings).
    const val CAT_MESSAGE = "msg"; const val CAT_EMAIL = "email"; const val CAT_CALL = "call"; const val CAT_MISSED_CALL = "missed_call"
    const val CAT_SOCIAL = "social"; const val CAT_ALARM = "alarm"; const val CAT_REMINDER = "reminder"; const val CAT_EVENT = "event"
    const val CAT_STOPWATCH = "stopwatch"; const val CAT_ERROR = "err"; const val CAT_SYSTEM = "sys"; const val CAT_STATUS = "status"
    const val CAT_PROMO = "promo"; const val CAT_RECOMMENDATION = "recommendation"; const val CAT_TRANSPORT = "transport"
    const val CAT_PROGRESS = "progress"; const val CAT_NAVIGATION = "navigation"; const val CAT_SERVICE = "service"
    const val CAT_WORKOUT = "workout"; const val CAT_LOCATION = "location_sharing"

    /** Android's IMPORTANCE_HIGH (heads-up); the deprecated notification priority PRIORITY_HIGH is 1. */
    const val IMPORTANCE_HIGH = 4

    private val ALERT_CATEGORIES = setOf(CAT_ALARM, CAT_REMINDER, CAT_EVENT, CAT_STOPWATCH)
    private val WARNING_CATEGORIES = setOf(CAT_ERROR, CAT_SYSTEM, CAT_STATUS)
    private val MESSAGE_CATEGORIES = setOf(CAT_MESSAGE, CAT_EMAIL, CAT_CALL, CAT_MISSED_CALL)
    private val PROMO_CATEGORIES = setOf(CAT_PROMO, CAT_RECOMMENDATION)

    fun isHighImportance(n: HubNotification) = n.importance >= IMPORTANCE_HIGH || n.priority >= 1

    /** The category of [n] by its own signals alone (no user rule). */
    fun classify(n: HubNotification): CommsCategory {
        val cat = n.category.orEmpty()
        if (cat in PROMO_CATEGORIES) return CommsCategory.Notifications
        if (isAlert(n, cat)) return CommsCategory.Alerts
        if (isMessage(n, cat)) return CommsCategory.Messages
        return CommsCategory.Notifications
    }

    private fun isAlert(n: HubNotification, cat: String): Boolean {
        if (cat in ALERT_CATEGORIES) return true
        if (n.timeSensitive) return true
        if (cat in WARNING_CATEGORIES && isHighImportance(n)) return true
        val text = n.title + "\n" + n.summary
        if (looksLikeOtp(text)) return true
        if (looksLikeSecurityOrBank(text)) return true
        // A delivery or ride that says it is here, from an app that marked it as urgent (heads-up).
        if (isHighImportance(n) && ARRIVAL.containsMatchIn(text.lowercase())) return true
        return false
    }

    private fun isMessage(n: HubNotification, cat: String): Boolean {
        if (cat in MESSAGE_CATEGORIES) return true
        // A history row's isMessaging only says it had a sender and a text (every row has); its stored category carries what the notification was.
        if (!n.fromHistory && (n.isMessaging || n.style == "messaging")) return true
        if (cat == CAT_SOCIAL && (n.hasPeople || !n.shortcutId.isNullOrBlank())) return true // a mention or a reply to the user, not a feed update
        if (!n.shortcutId.isNullOrBlank() && n.hasPeople) return true // a conversation shortcut with a person behind it
        return false
    }

    // ---- OTP and security heuristics (local, conservative, no network) ----------------------------------------------------------

    private val CODE = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")
    private val OTP_WORDS = listOf(
        "otp", "one-time", "one time", "verification", "verify", "passcode", "pass code", "security code", "login code", "sign-in code", "confirmation code",
        "code", "pin", "do not share", "don't share",
        "código", "codigo", "clave", "verificación", "verificacion", "contraseña de un solo uso", "no lo compartas",
        "ओटीपी", "कोड", "सत्यापन", "पिन", "साझा न करें",
    )
    private val PROMO_WORDS = listOf("% off", "off your", "discount", "coupon", "cashback", "promo", "sale ends", "save ", "descuento", "छूट", "ऑफर")

    /** True for a text that carries a 4 to 8 digit code and says what the code is for (OTP, verification code, PIN, in English, Spanish or Hindi). */
    fun looksLikeOtp(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.lowercase()
        if (!CODE.containsMatchIn(t)) return false
        if (PROMO_WORDS.any { t.contains(it) }) return false
        if (OTP_WORDS.none { word -> if (word.length <= 3 && word.all { it.isLetter() && it.code < 128 }) Regex("(?<![a-z])$word(?![a-z])").containsMatchIn(t) else t.contains(word) }) return false
        // A run that follows a currency sign or a currency word is an amount, not a code.
        val onlyAmounts = CODE.findAll(t).all { m ->
            val before = t.substring(maxOf(0, m.range.first - 6), m.range.first)
            AMOUNT_BEFORE.containsMatchIn(before)
        }
        return !onlyAmounts
    }
    private val AMOUNT_BEFORE = Regex("(rs\\.?|inr|usd|eur|₹|\\$|€|£|amount|amt)\\s*$")

    private val SECURITY = listOf(
        "security alert", "suspicious", "unauthorized", "unauthorised", "fraud", "new sign-in", "new login", "login attempt", "sign-in attempt",
        "password was changed", "password has been changed", "account has been locked", "was blocked", "alerta de seguridad", "actividad sospechosa",
        "no autorizado", "सुरक्षा अलर्ट", "संदिग्ध",
    )
    private val DEBIT_CREDIT = Regex("(debited|credited|withdrawn|spent|purchase of|payment of|transaction of|charged|cargo de|compra de|retiro de|डेबिट|क्रेडिट|निकासी)")
    private val MONEY = Regex("((rs\\.?|inr|usd|eur|₹|\\$|€|£)\\s*[0-9][0-9,]*(\\.[0-9]+)?)|([0-9][0-9,]*(\\.[0-9]+)?\\s*(rs\\.?|inr|usd|eur|₹|€|£))")

    /** A fraud or sign-in warning, or a bank movement with an amount ("Rs 4,500 debited from A/c ..."). */
    fun looksLikeSecurityOrBank(text: String): Boolean {
        val t = text.lowercase()
        if (SECURITY.any { t.contains(it) }) return true
        return DEBIT_CREDIT.containsMatchIn(t) && MONEY.containsMatchIn(t)
    }

    private val ARRIVAL = Regex("(is arriving|has arrived|have arrived|is here|is outside|arriving now|arrives in [0-9]+ min|ha llegado|está llegando|llegó|पहुंच गया|पहुंच रहा)")

    // ---- the owner's override ---------------------------------------------------------------------------------------------------

    /** The category the user chose for [n] with a Treat-as rule (the most specific, ties to the latest), or null. */
    fun override(n: HubNotification, rules: List<Rule>): CommsCategory? {
        val best = rules.withIndex()
            .filter { it.value.action.category != null && it.value.matches(n) }
            .maxWithOrNull(compareBy<IndexedValue<Rule>> { it.value.specificity }.thenBy { it.index })
        return best?.value?.action?.category
    }

    /** What Comms shows for [n]: the user's rule when there is one, else [classify]. */
    fun categoryOf(n: HubNotification, rules: List<Rule>): CommsCategory = override(n, rules) ?: classify(n)

    /**
     * The rule behind a row's "Make this an Alert" chip: for a chat the sender in its app, else the notification's channel in its app (an app's
     * delivery notices and its promos usually have separate channels), else the whole app. The most specific Treat-as rule wins, so a narrower one
     * overrides an app-wide one later.
     */
    fun ruleFor(n: HubNotification, c: CommsCategory): Rule {
        val action = when (c) { CommsCategory.Messages -> RuleAction.AsMessages; CommsCategory.Notifications -> RuleAction.AsNotifications; CommsCategory.Alerts -> RuleAction.AsAlerts }
        val pkg = n.packageName.ifBlank { null }
        val sender = LaunchpadLogic.senderOf(n)
        return when {
            n.isMessaging && !n.fromHistory && sender.isNotBlank() -> Rule.create(action, pkg, sender = sender)
            !n.channelId.isNullOrBlank() -> Rule.create(action, pkg, channel = n.channelId)
            else -> Rule.create(action, pkg)
        }
    }

    // ---- pinned (ongoing) -------------------------------------------------------------------------------------------------------

    /** Categories whose notifications are by definition a running thing the system keeps until it ends. */
    private val STICKY_CATEGORIES = setOf(CAT_TRANSPORT, CAT_NAVIGATION, CAT_STOPWATCH, CAT_SERVICE, CAT_PROGRESS, CAT_CALL, CAT_WORKOUT, CAT_LOCATION)

    /**
     * Wave 53: a pinned notification is one the system does not let the user swipe away while it lasts: FLAG_ONGOING_EVENT, FLAG_FOREGROUND_SERVICE or
     * FLAG_NO_CLEAR (all folded into [HubNotification.isOngoing] by the listener), or a live notification of a sticky category (media playback,
     * navigation, a running timer or stopwatch, a download with a progress bar, an active call, a service, a workout, location sharing).
     * A row that is only in the history (its notification has left the shade) is never pinned.
     */
    fun isPinned(n: HubNotification): Boolean {
        if (n.fromHistory) return false
        if (n.isOngoing) return true
        val cat = n.category.orEmpty()
        return cat in STICKY_CATEGORIES && (cat != CAT_CALL || n.style == "call") && (cat != CAT_PROGRESS || n.progress)
    }

    /**
     * Splits [items] into the pinned block (visible after Hide and Auto-clear rules and not snoozed) and the rest; with [enabled] false nothing is
     * pulled out (the pinned ones then sit in their normal groups, still undismissable).
     */
    fun splitPinned(items: List<HubNotification>, rules: List<Rule>, now: Long, enabled: Boolean, snoozedUntil: Map<String, Long> = emptyMap()): Pair<List<HubNotification>, List<HubNotification>> {
        if (!enabled) return emptyList<HubNotification>() to items
        val pinned = LaunchpadLogic.visible(items.filter { isPinned(it) && !SnoozeLogic.isSnoozed(it.key, snoozedUntil, now) }, rules, now)
            .sortedByDescending { it.timestamp }
        val keys = pinned.mapTo(HashSet()) { it.key }
        return pinned to items.filter { it.key !in keys }
    }

    /** The pinned block shows this many rows; more collapse to a compact row with "Show all". */
    const val PINNED_COMPACT_AFTER = 3
}
