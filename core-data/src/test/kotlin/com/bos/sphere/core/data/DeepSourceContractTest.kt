package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PAL-22: the launcher side of docs/SEARCH_PROVIDER.md. */
class DeepSourceContractTest {
    @Test fun PAL_22_authorityIsPackageDotSearchForFylzAndFotoz() {
        assertEquals("io.github.mbaliga.fylz.search", DeepSearchLogic.authority("io.github.mbaliga.fylz"))
        assertEquals("com.fotoxplorr.app.search", DeepSearchLogic.authority("com.fotoxplorr.app"))
        assertTrue(DeepSearchLogic.knownSources.any { it.contains("fylz") } && DeepSearchLogic.knownSources.any { it.contains("fotoxplorr") })
    }

    @Test fun PAL_22_documentedColumnsMapToAHit() {
        val h = DeepSearchLogic.parseRow(
            "com.fotoxplorr.app",
            mapOf("_id" to "7", "title" to "Receipt", "uri" to "content://x/7", "snippet" to "Total 12.40", "kind" to "image", "mime" to "image/jpeg", "thumb" to "content://x/t7", "score" to "0.9", "subtitle" to "Camera"),
        )!!
        assertEquals(HitKind.Image, h.kind); assertEquals("Total 12.40", h.snippet); assertEquals("com.fotoxplorr.app", h.sourcePackage)
        assertEquals(SearchCategory.Files, h.category); assertEquals(0.9f, h.score, 0.001f)
        assertNull(DeepSearchLogic.parseRow("p", mapOf("title" to "no uri")))
    }

    @Test fun PAL_22_noEmptyCategoryWhenTheAppsAreAbsent() {
        assertTrue(SearchMerge.categories(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty())
        val onlyMedia = SearchMerge.categories(emptyList(), listOf(SearchHit("m", SearchCategory.Files, "m", uri = "content://m")), emptyList(), emptyList())
        assertEquals(setOf(SearchCategory.Files), onlyMedia.keys)
    }

    @Test fun PAL_22_extraProviderHitsLandInTheirOwnCategory() {
        val calc = SearchHit("calc", SearchCategory.Actions, "8", copyText = "8", score = 1f)
        val file = SearchHit("f", SearchCategory.Files, "f.txt", uri = "content://f")
        val m = SearchMerge.categories(emptyList(), emptyList(), emptyList(), listOf(calc, file))
        assertEquals("8", m[SearchCategory.Actions]!!.single().copyText)
        assertEquals("f.txt", m[SearchCategory.Files]!!.single().title)
    }
}
