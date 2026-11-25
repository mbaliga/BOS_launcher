package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Wave 57 (profiles), CFG-47: the offline app group suggester, table-driven with real package names. */
class AppGroupSuggesterW57Test {
    private fun app(pkg: String, label: String = pkg.substringAfterLast('.'), category: Int = -1, work: Boolean = false) = SuggestApp("$pkg/.Main#0", pkg, label, category, work)

    /** package, label, declared category, expected group (null: no group). */
    private val table: List<Pair<SuggestApp, SuggestionKind?>> = listOf(
        app("com.google.android.gm", "Gmail") to SuggestionKind.Work,
        app("com.microsoft.office.outlook", "Outlook") to SuggestionKind.Work,
        app("com.google.android.calendar", "Calendar") to SuggestionKind.Work,
        app("com.Slack", "Slack") to SuggestionKind.Work,
        app("com.microsoft.teams", "Teams") to SuggestionKind.Work,
        app("us.zoom.videomeetings", "Zoom") to SuggestionKind.Work,
        app("com.google.android.apps.docs.editors.docs", "Docs") to SuggestionKind.Work,
        app("com.example.notes", "Notes", category = AppGroupSuggester.CATEGORY_PRODUCTIVITY) to SuggestionKind.Work,
        app("com.whatsapp", "WhatsApp") to SuggestionKind.Personal,
        app("org.telegram.messenger", "Telegram") to SuggestionKind.Personal,
        app("com.instagram.android", "Instagram") to SuggestionKind.Personal,
        app("com.google.android.apps.photos", "Photos") to SuggestionKind.Personal,
        app("com.example.social", "Friends", category = AppGroupSuggester.CATEGORY_SOCIAL) to SuggestionKind.Personal,
        app("com.google.android.youtube", "YouTube") to SuggestionKind.Media,
        app("com.spotify.music", "Spotify") to SuggestionKind.Media,
        app("com.netflix.mediaclient", "Netflix") to SuggestionKind.Media,
        app("au.com.shiftyjelly.pocketcasts", "Pocket Casts") to SuggestionKind.Media,
        app("com.example.radio", "Radio", category = AppGroupSuggester.CATEGORY_AUDIO) to SuggestionKind.Media,
        app("com.google.android.apps.maps", "Maps") to SuggestionKind.Travel,
        app("com.ubercab", "Uber") to SuggestionKind.Travel,
        app("com.waze", "Waze") to SuggestionKind.Travel,
        app("com.example.trails", "Trails", category = AppGroupSuggester.CATEGORY_MAPS) to SuggestionKind.Travel,
        app("com.phonepe.app", "PhonePe") to SuggestionKind.Money,
        app("net.one97.paytm", "Paytm") to SuggestionKind.Money,
        app("com.google.android.apps.nbu.paisa.user", "Google Pay") to SuggestionKind.Money,
        app("in.org.npci.upiapp", "BHIM") to SuggestionKind.Money,
        app("com.example.mybank", "My Bank") to SuggestionKind.Money, // the word bank
        app("com.example.wallet", "QuickPay") to SuggestionKind.Money, // a word that ends with pay
        app("com.example.payments", "Wallet") to SuggestionKind.Money, // a package word that starts with pay
        app("com.supercell.clashofclans", "Clash of Clans") to SuggestionKind.Games,
        app("com.example.puzzle", "Puzzle", category = AppGroupSuggester.CATEGORY_GAME) to SuggestionKind.Games,
        app("com.google.android.calculator", "Calculator") to SuggestionKind.Utilities,
        app("com.google.android.deskclock", "Clock") to SuggestionKind.Utilities,
        app("com.example.flashlight", "Torch") to SuggestionKind.Utilities,
        app("com.example.magnifier", "Magnifier", category = AppGroupSuggester.CATEGORY_ACCESSIBILITY) to SuggestionKind.Utilities,
        app("com.example.unknown", "Something") to null,
        app("com.example.display", "Display") to null, // "display" holds neither bank nor a pay word
        app("com.example.news", "Daily", category = AppGroupSuggester.CATEGORY_NEWS) to null,
    )

    @Test fun CFG_47_everyTableRowLandsInItsGroup() {
        val wrong = table.filter { (a, k) -> AppGroupSuggester.classify(a) != k }.map { (a, k) -> "${a.packageName} -> ${AppGroupSuggester.classify(a)} (want $k)" }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun CFG_47_aWorkProfileAppIsAWorkAppWhateverItIs() {
        assertEquals(SuggestionKind.Work, AppGroupSuggester.classify(app("com.whatsapp", "WhatsApp", work = true)))
        assertEquals(SuggestionKind.Work, AppGroupSuggester.classify(app("com.example.unknown", "Something", work = true)))
    }

    @Test fun CFG_47_thePackageTablesBeatTheDeclaredCategory() {
        // YouTube declares video, but even a wrong declaration does not move a known package
        assertEquals(SuggestionKind.Media, AppGroupSuggester.classify(app("com.google.android.youtube", "YouTube", category = AppGroupSuggester.CATEGORY_GAME)))
        assertEquals(SuggestionKind.Work, AppGroupSuggester.classify(app("com.Slack", "Slack", category = AppGroupSuggester.CATEGORY_SOCIAL)))
    }

    @Test fun CFG_47_suggestionsNeedTwoAppsListBestFirstAndPreselectAtMostFour() {
        val s = AppGroupSuggester.suggest(table.map { it.first })
        assertEquals(SuggestionKind.entries.toList(), s.map { it.kind })
        val work = s.first { it.kind == SuggestionKind.Work }
        assertEquals("known packages in table order, then the rest by name", listOf("Gmail", "Outlook", "Calendar", "Teams", "Slack", "Zoom", "Docs", "Notes"), work.apps.map { it.label })
        assertEquals(4, work.preselected.size)
        val only = AppGroupSuggester.suggest(listOf(app("com.spotify.music", "Spotify"), app("com.whatsapp", "WhatsApp"), app("org.telegram.messenger", "Telegram")))
        assertEquals("one media app is not a group", listOf(SuggestionKind.Personal), only.map { it.kind })
        assertTrue(AppGroupSuggester.suggest(emptyList()).isEmpty())
    }

    @Test fun CFG_47_calmAppsAreMessagingAndAlertsOnly() {
        val calm = AppGroupSuggester.calmPackages(table.map { it.first })
        assertTrue("com.whatsapp" in calm && "org.telegram.messenger" in calm && "com.google.android.deskclock" in calm && "com.google.android.calendar" in calm)
        assertFalse("com.instagram.android" in calm)
        assertFalse("com.google.android.youtube" in calm)
    }

    @Test fun CFG_47_theSuggesterNeverTouchesTheNetworkOrAndroid() {
        val src = File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }, "core-data/src/main/kotlin/com/bos/sphere/core/data/AppGroupSuggester.kt").readText()
        listOf("java.net", "okhttp", "HttpURLConnection", "URL(", "import android", "android.content", "Socket").forEach { assertFalse("the suggester uses $it", src.contains(it)) }
        assertNull(AppGroupSuggester.classify(app("", "")))
    }
}
