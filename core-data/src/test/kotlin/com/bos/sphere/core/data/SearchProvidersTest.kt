package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchProvidersTest {
    @Test fun torchFindsFlashlight() {
        assertEquals(CornerActionId.Flashlight, ActionHits.match("torch").first().actionId)
    }

    @Test fun labelPrefixRanksFirstAndCategoryIsActions() {
        val h = ActionHits.match("cal")
        assertEquals(CornerActionId.Calendar, h.first().actionId)
        assertTrue(h.all { it.category == SearchCategory.Actions && it.kind == HitKind.Action })
    }

    @Test fun synonymsAndInternalFiltering() {
        assertTrue(ActionHits.match("wifi").any { it.actionId == CornerActionId.SystemSettings })
        assertTrue(ActionHits.match("alarm").any { it.actionId == CornerActionId.Clock })
        assertTrue(ActionHits.match("grid").none { it.actionId == CornerActionId.GridDenser })
        assertTrue(ActionHits.match("search").none { it.actionId == CornerActionId.Search })
        assertTrue(ActionHits.match("notif").any { it.actionId == CornerActionId.Notifications })
        assertTrue(ActionHits.match("").isEmpty())
        assertEquals(2, ActionHits.match("a", limit = 2).size)
    }

    @Test fun likeEscape() {
        assertEquals("100\\%\\_a\\\\b", FileSearchLogic.escapeLike("100%_a\\b"))
        assertEquals("%a\\%%", FileSearchLogic.likePattern(" a% "))
    }

    @Test fun kinds() {
        assertEquals(HitKind.Image, FileSearchLogic.kindFromMime("image/png"))
        assertEquals(HitKind.Document, FileSearchLogic.kindFromMime("application/pdf"))
        assertEquals(HitKind.Document, FileSearchLogic.kindFromMime("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(HitKind.Archive, FileSearchLogic.kindFromMime("application/octet-stream", "a.zip"))
        assertEquals(HitKind.Other, FileSearchLogic.kindFromMime(null))
    }

    @Test fun sizes() {
        assertEquals("512 B", FileSearchLogic.formatSize(512))
        assertEquals("1.5 KB", FileSearchLogic.formatSize(1536))
        assertEquals("12 MB", FileSearchLogic.formatSize(12L * 1024 * 1024))
        assertEquals("Reports · 2.0 KB", FileSearchLogic.subtitle("Download/Reports/", 2048))
    }

    @Test fun parseRow() {
        val h = DeepSearchLogic.parseRow(
            "com.fotoxplorr.app",
            mapOf("_id" to "7", "title" to "Receipt", "kind" to "IMAGE", "uri" to "content://x/7", "snippet" to "Total 12", "score" to "3"),
        )
        assertNotNull(h)
        assertEquals("deep:com.fotoxplorr.app:7", h!!.id)
        assertEquals(HitKind.Image, h.kind)
        assertEquals(1f, h.score, 0f)
        assertEquals("com.fotoxplorr.app", h.sourcePackage)
        assertNull(DeepSearchLogic.parseRow("p", mapOf("title" to "no uri")))
        assertEquals("a.pdf", DeepSearchLogic.parseRow("p", mapOf("uri" to "file:///d/a.pdf"))!!.title)
    }

    @Test fun mergeDedupesPreferringDeep() {
        fun hit(id: String, uri: String, s: Float = 0f) = SearchHit(id, SearchCategory.Files, id, uri = uri, score = s)
        val deep = listOf(hit("d1", "u1", 0.2f), hit("d2", "u2", 0.9f))
        val media = listOf(hit("m1", "u1"), hit("m3", "u3"))
        assertEquals(listOf("d2", "d1", "m3"), SearchMerge.mergeFiles(deep, media).map { it.id })
        assertEquals(2, SearchMerge.mergeFiles(deep, media, max = 2).size)
    }
}
