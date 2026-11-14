package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopePrefixesTest {
    @Test
    fun PAL_19_prefixesParseAndRoundTrip() {
        assertEquals(SearchCategory.Apps to "mail", ScopePrefixes.parse("a mail"))
        assertEquals(SearchCategory.Files to "tax 2025", ScopePrefixes.parse("f  tax 2025"))
        assertEquals(SearchCategory.Actions to "dark", ScopePrefixes.parse("s dark"))
        assertEquals(SearchCategory.Contacts to "ann", ScopePrefixes.parse("c:ann"))
        assertEquals(SearchCategory.Apps to "", ScopePrefixes.parse("a "))
        // No prefix: untouched. A letter that is just the start of a word is not a prefix.
        assertEquals(null to "mail", ScopePrefixes.parse("mail"))
        assertEquals(null to "apple", ScopePrefixes.parse("apple"))
        assertEquals(null to "a", ScopePrefixes.parse("a"))
        assertEquals(null to "x foo", ScopePrefixes.parse("x foo"))
        for (c in SearchCategory.entries) assertEquals(c to "q", ScopePrefixes.parse(ScopePrefixes.prefixFor(c) + " q"))
    }

    @Test
    fun PAL_19_wheelAndPrefixAreOneSetting() {
        // Wheel -> text.
        assertEquals("f mail", ScopePrefixes.applyWheel("mail", SearchCategory.Files))
        assertEquals("c mail", ScopePrefixes.applyWheel("a mail", SearchCategory.Contacts))
        assertEquals("mail", ScopePrefixes.applyWheel("a mail", null))
        assertEquals("", ScopePrefixes.applyWheel("", SearchCategory.Files)) // empty field: the wheel alone holds it
        assertEquals("f ", ScopePrefixes.applyWheel("a ", SearchCategory.Files))
        // Text -> wheel.
        assertEquals(SearchCategory.Apps, ScopePrefixes.effectiveScope("a mail", SearchCategory.Web))
        assertEquals(SearchCategory.Web, ScopePrefixes.effectiveScope("mail", SearchCategory.Web))
        assertNull(ScopePrefixes.effectiveScope("mail", null))
        // Both directions agree.
        val text = ScopePrefixes.applyWheel("budget", SearchCategory.Files)
        assertEquals(SearchCategory.Files, ScopePrefixes.effectiveScope(text, null))
        assertEquals("budget", ScopePrefixes.parse(text).second)
    }

    @Test
    fun PAL_19_prefixesAreEditable() {
        val m = ScopePrefixes.DEFAULT.with(SearchCategory.Files, 'd')!!
        assertEquals(SearchCategory.Files to "x", ScopePrefixes.parse("d x", m))
        assertEquals(null to "f x", ScopePrefixes.parse("f x", m))
        assertNull(ScopePrefixes.DEFAULT.with(SearchCategory.Files, 'a')) // already apps
        assertEquals(m.letters, ScopePrefixes.Mapping.decode(m.encode()).letters)
        assertEquals(ScopePrefixes.DEFAULT.letters, ScopePrefixes.Mapping.decode("garbage").letters)
    }
}
