package com.bos.sphere.core.data

/**
 * Wave 57 (profiles), CFG-47: what the suggester knows about one launcher app. [category] is `ApplicationInfo.category`
 * (-1 when the app declares none); [work] is true for an app of the work profile.
 */
data class SuggestApp(val key: String, val packageName: String, val label: String, val category: Int = -1, val work: Boolean = false)

/** The groups the suggester can propose. */
enum class SuggestionKind(val label: String) {
    Work("Work apps"),
    Personal("Personal apps"),
    Media("Media"),
    Travel("Travel and maps"),
    Money("Money"),
    Games("Games"),
    Utilities("Utilities"),
}

/** One proposed group: every app that matched, best first. The user edits the list before anything is saved. */
data class GroupSuggestion(val kind: SuggestionKind, val apps: List<SuggestApp>) {
    /** The apps an App group made from this suggestion starts with (the store's groups hold 2 to 4 apps). */
    val preselected: List<SuggestApp> get() = apps.take(AppGroupSuggester.GROUP_MAX)
}

/**
 * A local, offline classifier: package-name tables of well-known apps, the work profile, the words "bank" and "pay", and the
 * category the app declares. It never reads the network, never reads app content, and holds no state. Precedence: work profile,
 * then the package tables, then the money words, then the declared category, then the utility words; an app lands in one group.
 */
object AppGroupSuggester {
    const val GROUP_MIN = 2
    const val GROUP_MAX = 4

    // ApplicationInfo.CATEGORY_* (stable public constants, written out so the tables read without Android).
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_IMAGE = 3
    const val CATEGORY_SOCIAL = 4
    const val CATEGORY_NEWS = 5
    const val CATEGORY_MAPS = 6
    const val CATEGORY_PRODUCTIVITY = 7
    const val CATEGORY_ACCESSIBILITY = 8

    /** Productivity: mail, calendar, documents, chat and meetings for work. */
    val WORK_PACKAGES: List<String> = listOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar",
        "com.microsoft.teams", "com.Slack", "us.zoom.videomeetings", "com.cisco.webex.meetings", "com.google.android.apps.dynamite",
        "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.officehubrow", "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint", "com.microsoft.office.onenote",
        "com.microsoft.skydrive", "com.dropbox.android", "com.notion.id", "com.atlassian.android.jira.core", "com.trello", "com.asana.app", "com.todoist",
        "com.microsoft.todos", "com.google.android.keep", "com.evernote", "com.linkedin.android", "com.samsung.android.email.provider",
    )

    /** Messaging (also used by the Calm starter): chats, SMS, email and calls. */
    val MESSAGING_PACKAGES: List<String> = listOf(
        "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "jp.naver.line.android", "com.viber.voip", "com.tencent.mm", "com.kakao.talk", "com.discord",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
    )

    /** Social feeds and photos (with [MESSAGING_PACKAGES], the Personal group). */
    val SOCIAL_PACKAGES: List<String> = listOf(
        "com.instagram.android", "com.facebook.katana", "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically", "com.reddit.frontpage",
        "com.pinterest", "com.bereal.ft", "com.google.android.apps.photos", "com.sec.android.gallery3d",
    )

    val MEDIA_PACKAGES: List<String> = listOf(
        "com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music", "com.google.android.apps.youtube.music", "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus", "in.startv.hotstar", "com.apple.android.music", "com.soundcloud.android", "com.google.android.apps.podcasts",
        "au.com.shiftyjelly.pocketcasts", "com.audible.application", "com.amazon.mp3", "tv.twitch.android.app", "com.plexapp.android", "org.videolan.vlc", "com.google.android.videos",
    )

    val TRAVEL_PACKAGES: List<String> = listOf(
        "com.google.android.apps.maps", "com.waze", "com.ubercab", "com.olacabs.customer", "com.lyft.android", "com.airbnb.android", "com.booking",
        "com.tripadvisor.tripadvisor", "com.makemytrip", "com.citymapper.app.release", "com.here.app.maps", "net.osmand", "com.expedia.bookings", "in.redbus.android",
        "cris.org.in.prs.ima",
    )

    /** Payments, UPI and banks that the "bank" and "pay" words would miss. */
    val MONEY_PACKAGES: List<String> = listOf(
        "com.google.android.apps.nbu.paisa.user", "com.google.android.apps.walletnfcrel", "com.phonepe.app", "net.one97.paytm", "com.paypal.android.p2pmobile",
        "com.venmo", "com.squareup.cash", "in.org.npci.upiapp", "com.sbi.lotusintouch", "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.chase.sig.android",
        "com.infonow.bofa", "com.wf.wellsfargomobile", "com.revolut.revolut", "com.transferwise.android", "com.robinhood.android", "com.coinbase.android",
        "com.zerodha.kite3", "com.samsung.android.spay",
    )

    val GAME_PACKAGES: List<String> = listOf(
        "com.supercell.clashofclans", "com.king.candycrushsaga", "com.mojang.minecraftpe", "com.tencent.ig", "com.dts.freefireth", "com.roblox.client",
        "com.chess", "com.nianticlabs.pokemongo",
    )

    val UTILITY_PACKAGES: List<String> = listOf(
        "com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.android.deskclock", "com.google.android.apps.nbu.files", "com.android.documentsui", "com.sec.android.app.myfiles", "com.android.settings",
        "com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera", "com.google.android.apps.recorder", "com.android.chrome",
        "org.mozilla.firefox", "com.brave.browser", "com.sec.android.app.sbrowser",
    )

    /** Alarms, reminders and emergency alerts: what the Calm starter lets through besides messaging. */
    val ALERT_PACKAGES: List<String> = listOf(
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock", "com.google.android.calendar", "com.samsung.android.calendar",
        "com.android.calendar", "com.google.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver",
    )

    private val TABLES: List<Pair<SuggestionKind, List<String>>> = listOf(
        SuggestionKind.Work to WORK_PACKAGES,
        SuggestionKind.Personal to MESSAGING_PACKAGES + SOCIAL_PACKAGES,
        SuggestionKind.Media to MEDIA_PACKAGES,
        SuggestionKind.Travel to TRAVEL_PACKAGES,
        SuggestionKind.Money to MONEY_PACKAGES,
        SuggestionKind.Games to GAME_PACKAGES,
        SuggestionKind.Utilities to UTILITY_PACKAGES,
    )

    private val UTILITY_WORDS = listOf("calculator", "clock", "files", "filemanager", "flashlight", "torch", "recorder", "scanner", "compass", "camera", "browser")

    /** "bank" anywhere in the package or the label, or a word of either that starts or ends with "pay" (Paytm, PayPal, GPay, Samsung Pay). */
    fun looksLikeMoney(app: SuggestApp): Boolean {
        val pkg = app.packageName.lowercase()
        val label = app.label.lowercase()
        if ("bank" in pkg || "bank" in label) return true
        val words = pkg.split('.', '_', '-') + label.split(' ', '-', '_', '.')
        return words.any { w -> w.length >= 3 && (w.startsWith("pay") || w.endsWith("pay")) }
    }

    /** Where [app] goes, or null when nothing says. */
    fun classify(app: SuggestApp): SuggestionKind? {
        if (app.work) return SuggestionKind.Work
        TABLES.firstOrNull { (_, pkgs) -> app.packageName in pkgs }?.let { return it.first }
        if (looksLikeMoney(app)) return SuggestionKind.Money
        when (app.category) {
            CATEGORY_GAME -> return SuggestionKind.Games
            CATEGORY_AUDIO, CATEGORY_VIDEO -> return SuggestionKind.Media
            CATEGORY_IMAGE, CATEGORY_SOCIAL -> return SuggestionKind.Personal
            CATEGORY_MAPS -> return SuggestionKind.Travel
            CATEGORY_PRODUCTIVITY -> return SuggestionKind.Work
            CATEGORY_ACCESSIBILITY -> return SuggestionKind.Utilities
        }
        val pkg = app.packageName.lowercase()
        if (UTILITY_WORDS.any { it in pkg }) return SuggestionKind.Utilities
        return null
    }

    /** Rank inside a group: the known packages in table order, then the rest by label. */
    private fun rank(kind: SuggestionKind, app: SuggestApp): Int {
        val table = TABLES.first { it.first == kind }.second
        val i = table.indexOf(app.packageName)
        return if (i >= 0) i else table.size
    }

    /** Every group with at least [GROUP_MIN] apps, in [SuggestionKind] order. One entry per package (a clone in the work profile counts as Work). */
    fun suggest(apps: List<SuggestApp>): List<GroupSuggestion> {
        val byKind = apps.groupBy { classify(it) }
        return SuggestionKind.entries.mapNotNull { k ->
            val list = byKind[k].orEmpty().sortedWith(compareBy<SuggestApp> { rank(k, it) }.thenBy { it.label.lowercase() })
            if (list.size >= GROUP_MIN) GroupSuggestion(k, list) else null
        }
    }

    /** The packages of the apps of [kind] (for a calm-centre scope). */
    fun packagesOf(apps: List<SuggestApp>, kind: SuggestionKind): Set<String> = apps.filter { classify(it) == kind }.mapTo(LinkedHashSet()) { it.packageName }

    /** The Calm starter's calm-centre apps: the messaging and alert apps installed (the calm centre filters by app, not by Comms category). */
    fun calmPackages(apps: List<SuggestApp>): Set<String> =
        apps.filter { !it.work && (it.packageName in MESSAGING_PACKAGES || it.packageName in ALERT_PACKAGES) }.mapTo(LinkedHashSet()) { it.packageName }
}
