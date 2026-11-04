package com.bos.sphere.feature.search

import com.bos.sphere.core.data.WebEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchModelTest {
    private val cats = listOf("Games", "Audio", "Social Media")

    @Test fun plainWordOffersNameAndPackage() {
        val s = suggest("chess", cats)
        assertTrue(s.any { it is Suggestion.Text && it.label.startsWith("Name contains") && it.insertion == "\"chess\"" })
        assertTrue(s.any { it is Suggestion.Text && it.insertion == "pkg:chess" })
    }

    @Test fun categoryAndKeyCompletion() {
        assertTrue(suggest("ga", cats).any { it.label == "Kind: Games" && it.insertion == "type:Games" })
        assertTrue(suggest("ty", cats).any { it is Suggestion.Key && it.insertion == "type:" })
        assertTrue(suggest("us", cats).any { it is Suggestion.Key && it.insertion == "used:" })
        assertTrue(suggest("la", cats).any { it is Suggestion.Key && it.insertion == "launches:" })
        assertTrue(suggest("soc", cats).any { it.insertion == "type:\"Social Media\"" })
    }

    @Test fun valuesAfterKey() {
        val used = suggest("used:", cats).map { it.label }
        assertEquals(listOf("Used: today", "Used: yesterday", "Used: this week", "Used: last 30 days"), used)
        assertEquals(listOf("is:work"), suggest("is:w", cats).map { it.insertion })
        assertEquals(listOf("type:Games"), suggest("type:g", cats).map { it.insertion })
        assertEquals(emptyList<Suggestion>(), suggest("zzz:", cats))
    }

    @Test fun prefixIsKeptAndCapped() {
        assertTrue(suggest("chess used:", cats).all { it.insertion.startsWith("chess used:") })
        assertTrue(suggest("a", cats).size <= 6)
        assertEquals(emptyList<Suggestion>(), suggest("", cats))
        assertEquals(emptyList<Suggestion>(), suggest("!g cats", cats))
        assertEquals(emptyList<Suggestion>(), suggest("chess ", cats))
    }

    @Test fun appsContactsWebOrder() {
        val s = sections("chess", WebEngine.DuckDuckGo, listOf("Chess"), listOf("Chesney"))
        assertEquals(listOf(SectionKind.Apps, SectionKind.Contacts, SectionKind.Web), s.order)
        assertEquals(3, s.web.size)
        assertTrue(s.web[0] is WebRow.Search)
        assertTrue(s.web[1] is WebRow.PlayStore)
        val hints = s.web[2] as WebRow.BangHints
        assertEquals(4, hints.chips.size)
        assertEquals("!g chess", hints.chips[0].rewritten)
    }

    @Test fun noHintsForOtherEngines() {
        val s = sections("chess", WebEngine.Google, listOf("x"), emptyList<String>())
        assertTrue(s.web.none { it is WebRow.BangHints })
        assertEquals(listOf(SectionKind.Apps, SectionKind.Web), s.order)
    }

    @Test fun bangPutsWebFirst() {
        val s = sections("!yt cats", WebEngine.DuckDuckGo, listOf("YT"), emptyList<String>())
        assertEquals(SectionKind.Web, s.order.first())
        val b = s.web[0] as WebRow.BangSearch
        assertTrue(b.label.startsWith("Search with !yt"))
        assertTrue(b.url.startsWith("https://duckduckgo.com/?q=%21yt"))
    }

    @Test fun urlPutsOpenFirst() {
        val s = sections("example.com", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>())
        assertEquals(SectionKind.Web, s.order.first())
        assertEquals(WebRow.Open("Open example.com", "https://example.com"), s.web[0])
    }

    @Test fun emptyQueryAndPermission() {
        val e = sections("", WebEngine.DuckDuckGo, listOf("a"), emptyList<String>())
        assertTrue(e.web.isEmpty())
        val p = sections("bob", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>(), hasContactsPermission = false)
        assertTrue(p.askContactsPermission)
        assertEquals(listOf(SectionKind.Contacts, SectionKind.Web), p.order)
        val off = sections("bob", WebEngine.DuckDuckGo, emptyList<String>(), emptyList<String>(), searchWeb = false, searchContacts = false)
        assertTrue(off.order.isEmpty())
    }
}
