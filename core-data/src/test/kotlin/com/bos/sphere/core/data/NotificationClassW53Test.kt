package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 53 (commsCategories, STR-17 and STR-16): the Messages / Notifications / Alerts classification and the pinned detection, table-driven. */
class NotificationClassW53Test {
    private fun n(
        pkg: String, title: String, text: String, category: String? = null, style: String? = null, messaging: Boolean = false,
        importance: Int = 0, priority: Int = 0, people: Boolean = false, shortcut: String? = null, urgent: Boolean = false,
        ongoing: Boolean = false, progress: Boolean = false, history: Boolean = false, key: String = "$pkg|$title",
    ) = HubNotification(
        id = key, key = key, source = "x", title = title, summary = text, timestamp = 1L, packageName = pkg, appLabel = pkg,
        category = category, style = style, isMessaging = messaging, importance = importance, priority = priority, hasPeople = people,
        shortcutId = shortcut, timeSensitive = urgent, isOngoing = ongoing, progress = progress, fromHistory = history,
    )

    private data class Case(val name: String, val n: HubNotification, val want: CommsCategory)

    private val M = CommsCategory.Messages
    private val N = CommsCategory.Notifications
    private val A = CommsCategory.Alerts

    private val table = listOf(
        // OTPs in several languages: an OTP text message is an Alert, not a Message.
        Case("otp en", n("com.google.android.apps.messaging", "AX-HDFCBK", "123456 is your OTP for login. Do not share it with anyone.", "msg", "messaging", true), A),
        Case("otp en google", n("com.google.android.apps.messaging", "Google", "G-482913 is your Google verification code.", "msg", messaging = true), A),
        Case("otp hi", n("com.google.android.apps.messaging", "JD-SBIOTP", "आपका ओटीपी 482913 है। इसे किसी से साझा न करें।", "msg", messaging = true), A),
        Case("otp es", n("com.google.android.apps.messaging", "BBVA", "Tu código de verificación es 7731. No lo compartas con nadie.", "msg", messaging = true), A),
        Case("otp pin", n("com.bank.app", "Bank", "Your PIN 4521 was set. Keep it secret.", null), A),
        // Chats, mail, calls: Messages.
        Case("whatsapp message", n("com.whatsapp", "Mum", "Are you coming on Sunday?", "msg", "messaging", true), M),
        Case("whatsapp group", n("com.whatsapp", "Family group", "Ana: Dinner at 8", "msg", "messaging", true), M),
        Case("gmail", n("com.google.android.gm", "Priya Nair", "Re: Budget for Q3", "email"), M),
        Case("sms plain", n("com.google.android.apps.messaging", "+91 98765 43210", "Call me when you land", "msg", messaging = true), M),
        Case("missed call", n("com.google.android.dialer", "Missed call", "Dad", "missed_call"), M),
        Case("telegram messaging style", n("org.telegram.messenger", "Ravi", "photo", style = "messaging"), M),
        Case("instagram dm via shortcut and person", n("com.instagram.android", "mia", "sent you a message", "social", people = true, shortcut = "dm_mia"), M),
        Case("social mention with people", n("com.twitter.android", "Sam mentioned you", "@you nice", "social", people = true), M),
        // Ads, news, feeds, updates: Notifications.
        Case("amazon promo", n("in.amazon.mShop.android.shopping", "Big Billion Sale", "Up to 70% off on electronics", "promo"), N),
        Case("promo quoting a code is not an otp", n("com.shop", "Weekend offer", "Use code 2024 for 20% off your order", "promo"), N),
        Case("promo code without category still not otp", n("com.shop", "Weekend offer", "Use code 5050 and get 20% off your order today", null), N),
        Case("recommendation", n("com.netflix.mediaclient", "New for you", "Because you watched Dark", "recommendation"), N),
        Case("news app", n("com.nytimes.android", "Breaking", "Markets close higher after rate decision"), N),
        Case("social feed update", n("com.facebook.katana", "Rahul posted a photo", "See what's new", "social"), N),
        Case("app update", n("com.android.vending", "Updates available", "5 apps can be updated", "status"), N),
        Case("system update info", n("com.google.android.gms", "System update", "A new software update is available"), N),
        Case("number alone is not a code", n("com.shop", "Order placed", "Your order 1234 is confirmed"), N),
        // Time-sensitive: Alerts.
        Case("alarm", n("com.google.android.deskclock", "Alarm", "07:00", "alarm"), A),
        Case("timer", n("com.google.android.deskclock", "Timer", "00:05 remaining", "alarm", ongoing = true), A),
        Case("stopwatch", n("com.google.android.deskclock", "Stopwatch", "00:12.4", "stopwatch", ongoing = true), A),
        Case("calendar event", n("com.google.android.calendar", "Standup in 15 minutes", "Room 4", "event"), A),
        Case("reminder", n("com.google.android.keep", "Pick up parcel", "Today 5 pm", "reminder"), A),
        Case("uber arrival", n("com.ubercab", "Your driver is arriving", "Priya is 2 min away in a white Swift", "status", importance = 4), A),
        Case("delivery here", n("com.application.zomato", "Your order has arrived", "Your delivery partner is outside", importance = 4), A),
        Case("delivery low importance stays a notification", n("com.application.zomato", "Your order has arrived", "Your delivery partner is outside", importance = 3), N),
        Case("bank debit", n("com.sbi.app", "SBI", "Rs 4,500.00 debited from A/c XX1234 on 21-11. Not you? Call 1800", "msg", messaging = true), A),
        Case("bank debit usd", n("com.chase", "Chase", "A purchase of \$52.18 was charged to your card ending 4432"), A),
        Case("bank credit es", n("com.bbva", "BBVA", "Compra de \$1,200 en TIENDA con tu tarjeta 4432"), A),
        Case("fraud", n("com.paypal", "PayPal", "Suspicious activity on your account. Review now"), A),
        Case("new sign in", n("com.google.android.gms", "Security alert", "New sign-in on Pixel 8"), A),
        Case("battery warning", n("android", "Battery low", "5% left", "sys", importance = 4), A),
        Case("system warning quiet", n("android", "Tip", "Try dark theme", "sys", importance = 2), N),
        Case("error high importance", n("com.app", "Sync failed", "Tap to retry", "err", importance = 4), A),
        Case("full screen intent flag", n("com.app", "Incoming", "x", urgent = true), A),
        Case("insistent meeting", n("com.app", "Meeting now", "Join", "event"), A),
        // Precedence: an alert beats a message; a promo beats an alert word.
        Case("calendar from chat app is alert", n("com.slack", "Reminder", "Standup", "reminder", messaging = true), A),
        // Ongoing pieces: media is a notification, a call is a message.
        Case("media playback", n("com.spotify.music", "Song", "Artist", "transport", ongoing = true), N),
        Case("ongoing call", n("com.google.android.dialer", "Mum", "Ongoing call", "call", "call", ongoing = true), M),
        Case("download progress", n("com.android.chrome", "Downloading", "report.pdf", "progress", ongoing = true, progress = true), N),
        Case("vpn service", n("de.blinkt.openvpn", "VPN connected", "Tap to disconnect", "service", ongoing = true), N),
        // History rows: isMessaging is true for every one of them and must not make them Messages.
        Case("history promo row", n("com.shop", "Sale", "Up to 50% off", history = true, messaging = true), N),
        Case("history chat row by stored category", n("com.whatsapp", "Mum", "Hi", "msg", history = true, messaging = true), M),
        Case("history otp row", n("com.google.android.apps.messaging", "HDFC", "482913 is your OTP", "msg", history = true, messaging = true), A),
    )

    @Test fun STR_17_classificationTable() {
        val bad = table.filter { NotificationClass.classify(it.n) != it.want }.map { "${it.name}: want ${it.want} got ${NotificationClass.classify(it.n)}" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun STR_17_theTableCoversEveryCategoryWithManyCases() {
        for (c in CommsCategory.entries) assertTrue("$c has at least 8 cases", table.count { it.want == c } >= 8)
    }

    @Test fun STR_17_otpHeuristicIsConservative() {
        listOf(
            "123456 is your OTP", "Your verification code is 4821", "Use 998877 as your login code", "ओटीपी 123456", "Su código es 123456",
            "Your one-time password: 77231904", "G-123456 is your Google verification code",
        ).forEach { assertTrue(it, NotificationClass.looksLikeOtp(it)) }
        listOf(
            "", "Your code is ready", "Order 1234 confirmed", "Call 1800123456 now", "Rs 1234 is your code balance", "Use code 5050 for 20% off", "Meeting at 2024", "Your code is 123456789012",
            "Your pin is 123", "The score is 3-2 in the 4th round",
        ).forEach { assertFalse(it, NotificationClass.looksLikeOtp(it)) }
    }

    @Test fun STR_17_securityAndBankHeuristic() {
        assertTrue(NotificationClass.looksLikeSecurityOrBank("Rs.500 debited from your account"))
        assertTrue(NotificationClass.looksLikeSecurityOrBank("Unauthorized login attempt"))
        assertFalse("a debit word without an amount is not enough", NotificationClass.looksLikeSecurityOrBank("How to read a debit card statement"))
        assertFalse(NotificationClass.looksLikeSecurityOrBank("Your order of 3 items shipped"))
    }

    // ---- the owner's override ----

    private val chat = n("com.whatsapp", "Mum", "Hi", "msg", "messaging", true, key = "c1")

    @Test fun STR_17_aTreatAsRuleBeatsTheOwnSignals() {
        assertEquals(M, NotificationClass.categoryOf(chat, emptyList()))
        assertEquals(A, NotificationClass.categoryOf(chat, listOf(Rule.create(RuleAction.AsAlerts, packageName = "com.whatsapp", sender = "Mum"))))
        assertEquals(N, NotificationClass.categoryOf(chat, listOf(Rule.create(RuleAction.AsNotifications, packageName = "com.whatsapp"))))
    }

    @Test fun STR_17_theMostSpecificTreatAsRuleWinsAndTiesGoToTheLatest() {
        val app = Rule.create(RuleAction.AsNotifications, packageName = "com.whatsapp")
        val sender = Rule.create(RuleAction.AsAlerts, packageName = "com.whatsapp", sender = "Mum")
        assertEquals(A, NotificationClass.categoryOf(chat, listOf(app, sender)))
        assertEquals(A, NotificationClass.categoryOf(chat, listOf(sender, app)))
        val later = Rule.create(RuleAction.AsMessages, packageName = "com.whatsapp")
        assertEquals("equal specificity: the later rule", M, NotificationClass.categoryOf(chat, listOf(app, later)))
        assertNull(NotificationClass.override(chat, listOf(Rule.create(RuleAction.Hide, packageName = "com.whatsapp"))))
    }

    @Test fun STR_17_aTreatAsRuleDoesNotHideOrSilenceAnything() {
        val r = listOf(Rule.create(RuleAction.AsAlerts, packageName = "com.whatsapp"))
        assertFalse(LaunchpadLogic.isHidden(chat, r)); assertFalse(LaunchpadLogic.isSilent(chat, r)); assertFalse(LaunchpadLogic.isPinned(chat, r))
        assertEquals("Treat notifications from com.whatsapp as Alerts", r[0].describe(null))
    }

    @Test fun STR_17_theRuleActionsRoundTripThroughTheStore() {
        val rules = listOf(RuleAction.AsMessages, RuleAction.AsNotifications, RuleAction.AsAlerts).map { Rule.create(it, packageName = "p.q") }
        assertEquals(rules.map { it.action }, LaunchpadJson.decodeRules(LaunchpadJson.encodeRules(rules)).map { it.action })
    }

    @Test fun STR_17_theNewPrefsRoundTripAndOlderExportsDefaultToOnAndAll() {
        val p = LaunchpadPrefs(GroupMode.Flat, pinnedAtTop = false, defaultCategory = CommsCategory.Alerts)
        assertEquals(p, LaunchpadJson.decodePrefs(LaunchpadJson.encodePrefs(p)))
        val old = LaunchpadJson.decodePrefs("""{"default":"Flat","perApp":{}}""")
        assertTrue(old.pinnedAtTop); assertNull(old.defaultCategory)
        assertTrue(LaunchpadJson.decodePrefs(LaunchpadJson.encodePrefs(LaunchpadPrefs())).pinnedAtTop)
    }

    // ---- pinned ----

    @Test fun STR_16_pinnedDetectionTable() {
        val pinned = listOf(
            n("com.spotify.music", "Song", "Artist", "transport", ongoing = true),
            n("com.spotify.music", "Song", "Artist", "transport"), // a media notification the system keeps, flags or not
            n("com.google.android.apps.maps", "Navigating", "5 min", "navigation"),
            n("com.google.android.deskclock", "Stopwatch", "00:12", "stopwatch"),
            n("com.google.android.dialer", "Mum", "Ongoing call", "call", "call"),
            n("com.android.chrome", "Downloading", "report.pdf", "progress", progress = true),
            n("de.blinkt.openvpn", "VPN", "connected", ongoing = true),
            n("com.app", "Syncing", "x", "service"),
            n("com.app", "Anything", "x", ongoing = true), // FLAG_ONGOING_EVENT, FLAG_FOREGROUND_SERVICE or FLAG_NO_CLEAR
        )
        pinned.forEach { assertTrue(it.title, NotificationClass.isPinned(it)) }
        val notPinned = listOf(
            n("com.whatsapp", "Mum", "Hi", "msg", "messaging", true),
            n("com.google.android.dialer", "Dad", "Missed call", "missed_call"),
            n("com.google.android.dialer", "Dad", "Incoming", "call"), // a call notification without the call style is not an active call
            n("com.android.chrome", "Download complete", "report.pdf", "progress"), // a finished download: no progress bar
            n("com.spotify.music", "Song", "Artist", "transport", history = true, ongoing = true), // a history row: the notification is gone
        )
        notPinned.forEach { assertFalse(it.title, NotificationClass.isPinned(it)) }
    }

    @Test fun STR_16_splitPinnedPullsOutTheBlockAndHonoursHideRulesSnoozeAndTheSetting() {
        val media = n("com.spotify.music", "Song", "A", "transport", ongoing = true, key = "k1").copy(timestamp = 10)
        val vpn = n("de.blinkt.openvpn", "VPN", "on", ongoing = true, key = "k2").copy(timestamp = 20)
        val mail = n("com.google.android.gm", "Hi", "x", "email", key = "k3")
        val all = listOf(mail, media, vpn)
        val (p, rest) = NotificationClass.splitPinned(all, emptyList(), 100, true)
        assertEquals(listOf("k2", "k1"), p.map { it.key }); assertEquals(listOf("k3"), rest.map { it.key })
        val (p0, rest0) = NotificationClass.splitPinned(all, emptyList(), 100, false)
        assertTrue(p0.isEmpty()); assertEquals(3, rest0.size)
        val hidden = NotificationClass.splitPinned(all, listOf(Rule.create(RuleAction.Hide, packageName = "de.blinkt.openvpn")), 100, true)
        assertEquals(listOf("k1"), hidden.first.map { it.key })
        assertTrue("the Hide rule still removes it later, in the view builder", LaunchpadLogic.visible(hidden.second, listOf(Rule.create(RuleAction.Hide, packageName = "de.blinkt.openvpn")), 100).none { it.key == "k2" })
        val snoozed = NotificationClass.splitPinned(all, emptyList(), 100, true, mapOf("k1" to 500L))
        assertEquals(listOf("k2"), snoozed.first.map { it.key })
    }

    @Test fun STR_16_pinnedRowsNeverCountTowardsTheUnreadIndicator() {
        val media = n("com.spotify.music", "Song", "A", "transport", ongoing = true, key = "k1")
        val mail = n("com.google.android.gm", "Hi", "x", "email", key = "k3")
        assertEquals(1, LaunchpadLogic.unreadTotal(listOf(media, mail), emptyList(), emptySet(), 0))
    }
}
