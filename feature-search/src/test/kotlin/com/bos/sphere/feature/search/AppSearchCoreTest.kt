package com.bos.sphere.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchCoreTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun e(key: String, label: String, pkg: String = "x.$key", cat: String = "Other", used: Long = 0, n: Int = 0, work: Boolean = false) =
        SearchEntry(key, label, pkg, cat, used, n, work)

    private val apps = listOf(
        e("fb", "Facebook", "com.facebook.katana", "Social", now - 2 * day, 40),
        e("fit", "Fitbit", "com.fitbit.FitbitMobile", "Other", now - 20 * day, 3),
        e("chess", "Chess Free", "com.chess", "Games", now - 1 * day, 12),
        e("pubg", "Battlegrounds", "com.pubg", "Games", now - 9 * day, 7),
        e("maps", "Maps", "com.google.android.apps.maps", "Maps", 0, 0),
        e("mail", "Mail", "com.mail", "Productivity", now - 3_600_000, 90, work = true),
        e("notes", "Notes", "com.notes", "Productivity", 0, 0),
    )
    private val index = AppSearchCore().Index(apps)

    private fun keys(q: String) = index.search(q, now)

    @Test fun `typing a prefix or part of a name finds it`() {
        assertEquals("fb", keys("face").first())
        assertTrue("fit" in keys("fitb"))
    }

    @Test fun `the package name matches too`() {
        assertTrue("maps" in keys("google"))
    }

    @Test fun `type facet filters by category`() {
        assertEquals(setOf("chess", "pubg"), keys("type:games").toSet())
        assertEquals(setOf("chess", "pubg"), keys("kind:game").toSet())
    }

    @Test fun `used facet understands relative dates`() {
        assertEquals(listOf("mail"), keys("used:today"))
        assertTrue("chess" in keys("used:yesterday"))
        assertTrue("pubg" !in keys("used:>3d"))
    }

    @Test fun `launch counts and is flags`() {
        assertEquals(setOf("fb", "mail", "chess", "pubg"), keys("launches:>5").toSet())
        assertEquals(setOf("mail"), keys("is:work").toSet())
        assertEquals(setOf("maps", "notes"), keys("is:never").toSet())
    }

    @Test fun `facets combine with text and negation`() {
        assertEquals(listOf("chess"), keys("type:games chess"))
        assertTrue("pubg" !in keys("type:games -battle"))
        assertEquals(setOf("fb", "mail"), keys("fa OR mai").toSet())
    }

    @Test fun `natural language is tried when the plain query finds nothing`() {
        assertEquals(setOf("chess", "pubg"), keys("games").toSet())
        assertEquals(listOf("chess"), keys("games I used yesterday"))
        assertEquals(listOf("maps"), keys("maps")) // a plain name still wins over the kind noun
    }

    @Test fun `blank and gibberish are safe`() {
        assertEquals(emptyList<String>(), keys("   "))
        assertEquals(emptyList<String>(), keys("zzzzzz"))
        keys("type:"); keys("used:???"); keys("((("); keys("/[/")
    }

    @Test fun `habit breaks ties between equal matches`() {
        val a = listOf(e("a1", "Camera One", used = 0, n = 0), e("a2", "Camera Two", used = 0, n = 50))
        assertEquals("a2", AppSearchCore().Index(a).search("camera", now).first())
    }
}
