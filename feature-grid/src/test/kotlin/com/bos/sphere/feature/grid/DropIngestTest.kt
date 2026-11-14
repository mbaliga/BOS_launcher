package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.HomeState
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.data.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropIngestTest {
    @Test fun INP_11_a_dropped_link_becomes_a_link_item() {
        val r = DropIngest.ingest(listOf(DropClip(text = "https://example.org/a?b=1")))
        val d = r.items.single()
        assertEquals(ItemKind.Link, d.kind); assertEquals("https://example.org/a?b=1", d.target); assertEquals("example.org", d.label)
        assertEquals(ItemKeys.link("https://example.org/a?b=1"), d.key)
    }

    @Test fun INP_11_text_holding_a_link_uses_the_link_and_other_text_becomes_a_search() {
        val inText = DropIngest.ingest(listOf(DropClip(text = "see https://example.org/x. thanks"))).items.single()
        assertEquals("https://example.org/x", inText.target)
        val plain = DropIngest.ingest(listOf(DropClip(text = "blue heron"))).items.single()
        assertTrue(plain.target.startsWith("https://duckduckgo.com/?q=blue+heron"))
        assertEquals("blue heron", plain.label)
    }

    @Test fun INP_11_a_content_uri_becomes_a_file_item_named_by_the_source() {
        val r = DropIngest.ingest(listOf(DropClip(uri = "content://com.x.files/doc/report.pdf", label = "Report.pdf")))
        val d = r.items.single()
        assertTrue(d.isFile); assertEquals("Report.pdf", d.label)
        assertEquals("report.pdf", DropIngest.ingest(listOf(DropClip(uri = "content://a/b/report.pdf"))).items.single().label)
    }

    @Test fun INP_11_schemes_that_leak_paths_or_run_code_are_ignored() {
        val r = DropIngest.ingest(listOf(DropClip(uri = "file:///sdcard/a.txt"), DropClip(uri = "javascript:alert(1)"), DropClip(uri = "intent://x#Intent;end"), DropClip()))
        assertTrue(r.items.isEmpty()); assertEquals(4, r.ignored)
    }

    @Test fun INP_11_the_launchers_own_app_drag_comes_through_as_an_app_key() {
        val r = DropIngest.ingest(listOf(DropClip(text = "com.a/.Main#0", mime = DropIngest.APP_MIME)))
        assertEquals(listOf("com.a/.Main#0"), r.appKeys); assertTrue(r.items.isEmpty())
    }

    @Test fun INP_11_duplicates_collapse_and_a_drop_is_capped() {
        val same = DropIngest.ingest(List(3) { DropClip(text = "https://example.org") })
        assertEquals(1, same.items.size)
        val many = DropIngest.ingest((1..20).map { DropClip(text = "https://e$it.org") })
        assertEquals(DropIngest.MAX_ITEMS, many.items.size); assertEquals(12, many.ignored)
    }

    @Test fun INP_11_dropped_items_are_added_to_the_home_arrangement_once() {
        val r = DropIngest.ingest(listOf(DropClip(text = "https://example.org"), DropClip(text = "https://example.net")))
        val s0 = HomeState(pages = listOf(HomePage(listOf("a"))))
        val s1 = HomeDropStore.apply(s0, r)
        assertEquals(listOf("a", ItemKeys.link("https://example.org"), ItemKeys.link("https://example.net")), s1.pages[0].keys)
        assertEquals(s1, HomeDropStore.apply(s1, r))
        assertEquals("https://example.org", s1.items[ItemKeys.link("https://example.org")]!!.target)
    }
}
