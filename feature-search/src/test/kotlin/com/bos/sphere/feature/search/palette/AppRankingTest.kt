package com.bos.sphere.feature.search.palette

import dev.aarso.search.palette.AppMatcher
import dev.aarso.search.palette.AppRecord
import dev.aarso.search.palette.Ranker
import dev.aarso.search.palette.UsageRecord
import dev.aarso.search.palette.UsageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppRankingTest {
    private val apps = listOf(
        AppRecord("com.google.maps/0", "Google Maps", "com.google.android.apps.maps"),
        AppRecord("com.google.mail/0", "Gmail", "com.google.android.gm"),
        AppRecord("org.cal/0", "Calendar", "com.android.calendar"),
        AppRecord("org.cam/0", "Camera", "com.android.camera"),
    )

    private fun rank(q: String, list: List<AppRecord> = apps, ranker: Ranker = Ranker()) = AppRanking.rank(AppMatcher(list), q, ranker)

    @Test fun PAL_02_liveRankingFindsByInitialsAliasPackageAndOneTypo() {
        assertEquals("com.google.maps/0", rank("gm").first())
        assertEquals("org.cal/0", rank("calender").first()) // one typo
        val aliased = apps.map { if (it.key == "org.cal/0") it.copy(alias = "agenda") else it }
        assertEquals("org.cal/0", rank("agenda", aliased).first())
        assertTrue(rank("android.gm").contains("com.google.mail/0"))
        assertTrue(rank("zzzzz").isEmpty())
    }

    @Test fun PAL_02_operatorQueriesStayWithTheQueryLanguage() {
        assertTrue(AppRanking.isOperatorQuery("type:games"))
        assertTrue(AppRanking.isOperatorQuery("\"google maps\""))
        assertTrue(AppRanking.isOperatorQuery("maps -mail"))
        assertFalse(AppRanking.isOperatorQuery("google maps"))
        assertFalse(AppRanking.isOperatorQuery("gm"))
    }

    @Test fun PAL_09_aSelectionLiftsAnAppAmongEquals() {
        val store = UsageStore()
        val ranker = Ranker(store, clock = { 1_000_000L })
        val before = rank("ca", ranker = ranker)
        val other = apps.first { it.key in listOf("org.cal/0", "org.cam/0") && it.key != before.first() }
        repeat(5) { store.record(AppRanking.usageKey(other), 1_000_000L) }
        assertEquals(other.key, rank("ca", ranker = ranker).first())
        ranker.reset()
        assertEquals(before, rank("ca", ranker = ranker))
    }

    @Test fun PAL_09_launchHistoryFromTheHomeIsSeededIntoTheRanker() {
        val store = UsageStore().apply { put("app:org.cam/0", UsageRecord(10, 1_000_000L)) }
        assertEquals("org.cam/0", rank("ca", ranker = Ranker(store, clock = { 1_000_000L })).first())
    }

    @Test fun PAL_17_onlyGuardedExportedProvidersWithTheAuthorityRuleAreAccepted() {
        val p = "org.example.notes"
        assertTrue(ThirdPartyProviders.acceptable(p, "$p.search", ThirdPartyProviders.PERMISSION, true))
        assertFalse(ThirdPartyProviders.acceptable(p, "$p.search", null, true)) // not permission guarded
        assertFalse(ThirdPartyProviders.acceptable(p, "$p.search", "android.permission.READ_CONTACTS", true))
        assertFalse(ThirdPartyProviders.acceptable(p, "other.authority", ThirdPartyProviders.PERMISSION, true))
        assertFalse(ThirdPartyProviders.acceptable(p, "$p.search", ThirdPartyProviders.PERMISSION, false))
    }

    @Test fun PAL_06_radarCodeHasNoNetworkClient() {
        // Radar and the palette only build a URL for the browser; nothing under these sources opens a connection.
        val banned = listOf("HttpURLConnection", "java.net.URL(", "okhttp", "OkHttp", "Socket(", "openConnection", "WebView")
        for (dir in listOf("src/main", "../feature-grid/src/main", "../search-engine/src/main")) {
            File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" && (!dir.contains("feature-grid") || Regex("(Search|Radar|PaletteSettings).*\\.kt").matches(it.name)) }.forEach { f ->
                val text = f.readText()
                for (b in banned) assertFalse("${f.name} uses $b", text.contains(b))
            }
        }
    }
}
