package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 6 audit of PAL-17 and PAL-22: a provider's rows and authority are not trusted blindly. */
class W6DeepSearchAuditTest {
    @Test fun PAL_17_an_authority_counts_only_when_the_approved_package_owns_it() {
        assertTrue(DeepSearchLogic.ownsAuthority("com.fotoxplorr.app", "com.fotoxplorr.app"))
        assertFalse(DeepSearchLogic.ownsAuthority("com.fotoxplorr.app", "evil.app"))
        assertFalse(DeepSearchLogic.ownsAuthority("com.fotoxplorr.app", null))
    }

    @Test fun PAL_17_rows_with_an_intent_or_script_or_bare_path_uri_are_dropped_and_content_file_and_web_rows_stay() {
        fun row(u: String) = DeepSearchLogic.parseRow("p", mapOf("title" to "t", "uri" to u))
        assertNull(row("intent:#Intent;end"))
        assertNull(row("javascript:alert(1)"))
        assertNull(row("/just/a/path"))
        assertEquals("content://p.search/1", row("content://p.search/1")?.uri)
        assertEquals("https://example.org/a", row("HTTPS://example.org/a".lowercase())?.uri)
        assertEquals("file:///d/a.pdf", row("file:///d/a.pdf")?.uri)
        assertNull(DeepSearchLogic.parseRow("p", mapOf("title" to "t", "uri" to "content://a/1", "thumb" to "javascript:x"))?.thumbUri)
    }
}
