package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A = "p000000000000000a"
private const val B = "p000000000000000b"
private const val C = "p000000000000000c"
private const val D = "p000000000000000d"

private fun list(vararg pages: Page) = PageList(listOf(Page.Home) + pages)

class PageListCodecTest {

    private val sample = list(
        Page(A, PageKind.Web, title = "News", url = "https://example.org/"),
        Page(B, PageKind.Html, isPackage = true, rev = 2),
        Page(C, PageKind.Html, title = "Orrery", isPackage = false, network = true, rev = 1),
        Page(D, PageKind.AppWidget, widgetSlot = 3),
    )

    @Test
    fun roundTripsEveryKindAndField() {
        val decoded = PageListCodec.decode(PageListCodec.encode(sample))
        assertEquals(sample, decoded.list)
        assertEquals(DecodeStatus.Current, decoded.status)
        assertEquals(PageListCodec.CURRENT_SCHEMA, decoded.schema)
    }

    @Test
    fun homeIsImplicitAndNeverStored() {
        val text = PageListCodec.encode(sample)
        assertFalse(text.contains("\"home\""))
        assertTrue(text.startsWith("{\"schema\":1,"))
    }

    @Test
    fun nothingStoredMeansHomeOnly() {
        for (text in listOf(null, "", "   ")) {
            val d = PageListCodec.decode(text)
            assertEquals(PageList.HomeOnly, d.list)
            assertEquals(DecodeStatus.Empty, d.status)
        }
    }

    @Test
    fun garbageIsUnreadableNotAnEmptyList() {
        for (text in listOf("not json", "[1,2]", "{\"pages\":5}", "{\"schema\":1}")) {
            val d = PageListCodec.decode(text)
            assertEquals(text, PageList.HomeOnly, d.list)
            assertEquals(text, DecodeStatus.Unreadable, d.status)
        }
    }

    @Test
    fun aDocumentWithoutSchemaIsReadAsSchemaOne() {
        val d = PageListCodec.decode("""{"pages":[{"id":"$A","kind":"web","url":"https://a.org"}]}""")
        assertEquals(DecodeStatus.Current, d.status)
        assertEquals(list(Page(A, PageKind.Web, url = "https://a.org/")), d.list)
    }

    @Test
    fun unknownKindsAndDamagedEntriesSurviveARewrite() {
        val text = """{"schema":1,"pages":[
            {"id":"$A","kind":"hologram","beam":7},
            {"id":"$B","kind":"web","url":"javascript:alert(1)"},
            {"id":"home","kind":"web","url":"https://a.org"},
            {"id":"$C","kind":"appwidget","slot":0},
            {"id":"$D","kind":"web","url":"https://ok.example/"}
        ]}"""
        val d = PageListCodec.decode(text)
        assertEquals(list(Page(D, PageKind.Web, url = "https://ok.example/")), PageList(d.list.pages))
        assertEquals(4, d.list.unknown.size)
        // Rewritten and read again: the unknown entries are still there, verbatim.
        val again = PageListCodec.decode(PageListCodec.encode(d.list))
        assertEquals(d.list, again.list)
        assertEquals(7L, again.list.unknown.first()["beam"])
    }

    @Test
    fun aNewerSchemaIsReadBestEffortAndFlagged() {
        val text = """{"schema":3,"pages":[{"id":"$A","kind":"web","url":"https://a.org/","future":true},{"id":"$B","kind":"stack"}]}"""
        val d = PageListCodec.decode(text)
        assertEquals(DecodeStatus.Newer, d.status)
        assertEquals(3, d.schema)
        assertEquals(list(Page(A, PageKind.Web, url = "https://a.org/")).pages, d.list.pages)
        assertEquals(1, d.list.unknown.size)
    }

    @Test
    fun migrationsRunStepByStepUpToTheCurrentSchema() {
        // A test-only "schema 0" that called the address "address": the step renames it.
        val steps = mapOf<Int, (Map<String, Any?>) -> Map<String, Any?>>(
            0 to { doc ->
                @Suppress("UNCHECKED_CAST")
                val pages = (doc["pages"] as List<Map<String, Any?>>).map { p ->
                    p.toMutableMap().apply { remove("address")?.let { put("url", it) } }
                }
                doc + mapOf("schema" to 1L, "pages" to pages)
            },
        )
        val old = """{"schema":0,"pages":[{"id":"$A","kind":"web","address":"https://old.example"}]}"""
        val d = PageListCodec.decode(old, steps)
        assertEquals(DecodeStatus.Current, d.status)
        assertEquals(0, d.schema)
        assertEquals(list(Page(A, PageKind.Web, url = "https://old.example/")), d.list)
        // Without a step from schema 0 there is no way forward: unreadable, not silently empty.
        assertEquals(DecodeStatus.Unreadable, PageListCodec.decode(old).status)
    }

    @Test
    fun aPackageNeverDecodesWithItsNetworkOn() {
        val text = """{"schema":1,"pages":[{"id":"$A","kind":"html","package":true,"network":true}]}"""
        assertFalse(PageListCodec.decode(text).list.pages[1].network)
    }

    @Test
    fun duplicateIdsKeepTheFirst() {
        val text = """{"pages":[{"id":"$A","kind":"web","url":"https://one.example"},{"id":"$A","kind":"web","url":"https://two.example"}]}"""
        assertEquals(list(Page(A, PageKind.Web, url = "https://one.example/")), PageListCodec.decode(text).list)
    }

    @Test
    fun titlesAreCleaned() {
        val text = """{"pages":[{"id":"$A","kind":"web","url":"https://a.org","title":"  Two\n  lines  "}]}"""
        assertEquals("Two lines", PageListCodec.decode(text).list.pages[1].title)
    }
}

class PageEditsTest {

    private val abc = list(
        Page(A, PageKind.Web, url = "https://a.org/"),
        Page(B, PageKind.Html, isPackage = false),
        Page(C, PageKind.Html, isPackage = true),
    )

    @Test
    fun normalizeKeepsHomeFirstOnceAndDropsDuplicates() {
        val messy = PageList(listOf(Page(A, PageKind.Web, url = "https://a.org/"), Page.Home, Page.Home, Page(A, PageKind.Web, url = "https://b.org/")))
        assertEquals(list(Page(A, PageKind.Web, url = "https://a.org/")), PageEdits.normalize(messy))
    }

    @Test
    fun addAppendsAndIgnoresDuplicatesAndHome() {
        val d = Page(D, PageKind.Web, url = "https://d.org/")
        assertEquals(D, PageEdits.add(abc, d).pages.last().id)
        assertEquals(abc, PageEdits.add(abc, Page(A, PageKind.Web, url = "https://x.org/")))
        assertEquals(abc, PageEdits.add(abc, Page.Home))
    }

    @Test
    fun homeCannotBeRenamedMovedOrRemoved() {
        assertEquals(abc, PageEdits.rename(abc, Page.HOME_ID, "Mine"))
        assertEquals(abc, PageEdits.move(abc, Page.HOME_ID, 2))
        assertEquals(abc, PageEdits.remove(abc, Page.HOME_ID))
    }

    @Test
    fun moveStaysAfterHome() {
        assertEquals(listOf(Page.HOME_ID, A, C, B), PageEdits.move(abc, C, -1).pages.map { it.id })
        assertEquals(listOf(Page.HOME_ID, A, B, C), PageEdits.move(abc, A, -5).pages.map { it.id })
        assertEquals(listOf(Page.HOME_ID, B, C, A), PageEdits.move(abc, A, 9).pages.map { it.id })
        assertEquals(listOf(Page.HOME_ID, C, A, B), PageEdits.moveTo(abc, C, 0).pages.map { it.id })
    }

    @Test
    fun renameCleansAndRemoveDrops() {
        assertEquals("Orrery", PageEdits.rename(abc, B, "  Orrery ").byId(B)?.title)
        assertEquals(listOf(Page.HOME_ID, A, C), PageEdits.remove(abc, B).pages.map { it.id })
    }

    @Test
    fun theNetworkSwitchExistsOnlyForSingleFilePages() {
        assertTrue(PageEdits.setNetwork(abc, B, true).byId(B)!!.network)
        assertEquals(abc, PageEdits.setNetwork(abc, C, true)) // a package: never
        assertEquals(abc, PageEdits.setNetwork(abc, A, true)) // a website: always uses the network
    }

    @Test
    fun reimportBumpsRevKeepsPlaceAndForcesPackagesOffline() {
        val on = PageEdits.setNetwork(abc, B, true)
        val again = PageEdits.reimported(on, B, isPackage = false)
        assertEquals(1, again.byId(B)!!.rev)
        assertTrue(again.byId(B)!!.network)
        assertEquals(2, again.indexOf(B))
        val nowPackage = PageEdits.reimported(on, B, isPackage = true)
        assertTrue(nowPackage.byId(B)!!.isPackage)
        assertFalse(nowPackage.byId(B)!!.network)
    }

    @Test
    fun widgetPagesFollowPlacementsOnThePageSurface() {
        val one = list(Page(A, PageKind.AppWidget, widgetSlot = 1))
        val ids = ArrayDeque(listOf(A, B, C)) // A is taken: the generator is asked again
        val synced = PageEdits.syncWidgetPages(one, listOf(3, 1, 2, 2), newId = { ids.removeFirst() })
        assertEquals(listOf(1, 2, 3), synced.pages.drop(1).map { it.widgetSlot })
        assertEquals(listOf(A, B, C), synced.pages.drop(1).map { it.id })
        assertTrue(synced.pages.drop(1).all { it.kind == PageKind.AppWidget && it.title.isEmpty() })
        // A page whose widget is gone stays (it shows "No widget on this page").
        assertEquals(one, PageEdits.syncWidgetPages(one, emptyList(), newId = { error("not needed") }))
    }

    @Test
    fun nextWidgetSlotIsAboveEveryUsedSlot() {
        assertEquals(1, PageEdits.nextWidgetSlot(PageList.HomeOnly, emptyList()))
        val pages = list(Page(A, PageKind.AppWidget, widgetSlot = 4), Page(B, PageKind.AppWidget, widgetSlot = 1))
        assertEquals(5, PageEdits.nextWidgetSlot(pages, listOf(2)))
        assertEquals(10, PageEdits.nextWidgetSlot(pages, listOf(9)))
    }

    @Test
    fun pageIdsAreSixteenHexDigits() {
        assertEquals("p0000000000000001", PageIds.newId { 1L })
        assertEquals("pffffffffffffffff", PageIds.newId { -1L })
        assertTrue(PageIds.isValid(PageIds.newId { 0x1234_5678_9abcL }))
        assertFalse(PageIds.isValid("home"))
        assertFalse(PageIds.isValid("p123"))
        assertFalse(PageIds.isValid("P000000000000000A"))
    }

    @Test
    fun navModeDefaultsToTwoFingerSwipe() {
        assertEquals(NavMode.TwoFingerDots, NavMode.Default)
        assertEquals(NavMode.TwoFingerDots, NavMode.fromWire(null))
        assertEquals(NavMode.TwoFingerDots, NavMode.fromWire("sideways"))
        assertEquals(NavMode.Vertical, NavMode.fromWire("vertical"))
        assertEquals(NavMode.PastEnds, NavMode.fromWire("past-ends"))
    }

    @Test
    fun byIdAndIndexOf() {
        assertEquals(2, abc.indexOf(B))
        assertEquals(-1, abc.indexOf(D))
        assertNull(abc.byId(D))
    }
}
