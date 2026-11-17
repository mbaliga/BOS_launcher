package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {
    @Test fun urls() {
        assertTrue(WebSearch.looksLikeUrl("example.com"))
        assertTrue(WebSearch.looksLikeUrl("https://a.b/c?d=1"))
        assertTrue(WebSearch.looksLikeUrl("www.site.org/path"))
        assertTrue(WebSearch.looksLikeUrl("localhost:8080"))
        assertFalse(WebSearch.looksLikeUrl("hello world.com"))
        assertFalse(WebSearch.looksLikeUrl("chess"))
        assertFalse(WebSearch.looksLikeUrl("3.14"))
        assertFalse(WebSearch.looksLikeUrl("me@example.com"))
        assertFalse(WebSearch.looksLikeUrl(""))
    }

    @Test fun toUrl() {
        assertEquals("https://example.com", WebSearch.toUrl(" example.com "))
        assertEquals("http://x.org", WebSearch.toUrl("http://x.org"))
    }


    @Test fun playStore() {
        assertEquals("https://play.google.com/store/search?q=a+b&c=apps", WebSearch.playStoreUrl("a b"))
        assertEquals("market://search?q=chess&c=apps", WebSearch.playStoreMarketUri("chess"))
    }
}
