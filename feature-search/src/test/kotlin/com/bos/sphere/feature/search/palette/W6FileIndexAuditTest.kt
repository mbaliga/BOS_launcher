package com.bos.sphere.feature.search.palette

import org.junit.Assert.assertEquals
import org.junit.Test

/** Wave 6 audit of PAL-11: what the user revoked must leave the index. */
class W6FileIndexAuditTest {
    @Test fun PAL_11_a_revoked_media_permission_drops_the_media_entries_and_a_live_grant_keeps_its_own() {
        val sources = setOf("media", "tree:content://a", "tree:content://b")
        assertEquals(listOf("media", "tree:content://b"), FileIndexer.staleSources(sources, false, setOf("tree:content://a")).sorted())
        assertEquals(emptyList<String>(), FileIndexer.staleSources(sources, true, sources))
        assertEquals(listOf("tree:content://a"), FileIndexer.staleSources(setOf("media", "tree:content://a"), true, emptySet()))
    }
}
