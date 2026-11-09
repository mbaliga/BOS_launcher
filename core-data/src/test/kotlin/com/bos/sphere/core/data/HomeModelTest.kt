package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTest {

    // --- corner mapping -------------------------------------------------------------------

    @Test
    fun `rotation 0 maps physical corners onto the same screen corners`() {
        Corner.entries.forEach { assertEquals(it, CornerMapping.screenCornerOf(it, 0)) }
    }

    @Test
    fun `rotation 90 matches the reference portrait-to-landscape screenshots`() {
        // Portrait: TL camera, TR music, BR search, BL apps.
        // Landscape (ROTATION_90): TL music, TR search, BR apps, BL camera.
        assertEquals(Corner.BottomLeft, CornerMapping.screenCornerOf(Corner.TopLeft, 1))
        assertEquals(Corner.TopLeft, CornerMapping.screenCornerOf(Corner.TopRight, 1))
        assertEquals(Corner.TopRight, CornerMapping.screenCornerOf(Corner.BottomRight, 1))
        assertEquals(Corner.BottomRight, CornerMapping.screenCornerOf(Corner.BottomLeft, 1))
    }

    @Test
    fun `physicalCornerAt inverts screenCornerOf for every rotation`() {
        for (r in 0..3) for (c in Corner.entries) {
            assertEquals(c, CornerMapping.physicalCornerAt(CornerMapping.screenCornerOf(c, r), r))
        }
        assertEquals(Corner.BottomRight, CornerMapping.screenCornerOf(Corner.TopLeft, 2))
    }

    // --- layout ---------------------------------------------------------------------------

    private val apps = (1..7).map { "app$it" }
    private fun cap(n: Int): (PageLayout) -> Int = { n }
    private fun pages(vararg keys: List<String>) = keys.map { HomePage(it) }
    private fun List<HomePage>.keys() = map { it.keys }

    @Test
    fun `unseeded layout flows all apps across pages of the given capacity`() {
        val out = HomeLayoutMath.visualPages(HomeLayout(), apps, cap(3))
        assertEquals(listOf(listOf("app1", "app2", "app3"), listOf("app4", "app5", "app6"), listOf("app7")), out.keys())
    }

    @Test
    fun `visual pages drop unknown and duplicate keys, append new apps, honour removals`() {
        val layout = HomeLayout(
            pages = pages(listOf("app3", "gone", "app1"), listOf("app1", "app2")),
            removed = setOf("app5"),
            seeded = true,
        )
        val out = HomeLayoutMath.visualPages(layout, apps, cap(10))
        assertEquals(listOf(listOf("app3", "app1"), listOf("app2", "app4", "app6", "app7")), out.keys())
    }

    @Test
    fun `shrinking capacity splits a stored page, keeping its layout`() {
        val circle = PageLayout(shape = PageShape.Circle, rings = 1)
        val layout = HomeLayout(pages = listOf(HomePage(apps, circle)), seeded = true)
        val out = HomeLayoutMath.visualPages(layout, apps, cap(2))
        assertEquals(4, out.size)
        assertEquals(apps, out.flatMap { it.keys })
        assertTrue(out.all { it.layout == circle })
    }

    @Test
    fun `per-page capacity is honoured`() {
        val small = PageLayout(portrait = GridSpec(2, 1))
        val layout = HomeLayout(pages = listOf(HomePage(apps.take(4), small), HomePage(apps.drop(4))), seeded = true)
        val capOf: (PageLayout) -> Int = { if (it == small) 2 else 20 }
        val out = HomeLayoutMath.visualPages(layout, apps, capOf)
        assertEquals(listOf(listOf("app1", "app2"), listOf("app3", "app4"), listOf("app5", "app6", "app7")), out.keys())
    }

    @Test
    fun `blank pages are kept and an empty inventory still yields one page`() {
        assertEquals(listOf(emptyList<String>()), HomeLayoutMath.visualPages(HomeLayout(), emptyList(), cap(4)).keys())
        val layout = HomeLayout(pages = pages(listOf("app1"), emptyList()), seeded = true)
        assertEquals(2, HomeLayoutMath.visualPages(layout, listOf("app1"), cap(4)).size)
    }

    @Test
    fun `a page emptied only by hidden apps is not drawn`() {
        val layout = HomeLayout(pages = pages(listOf("hidden"), listOf("app1")), seeded = true)
        assertEquals(listOf(listOf("app1")), HomeLayoutMath.visualPages(layout, listOf("app1"), cap(4)).keys())
    }

    @Test
    fun `visual splits fold back into one stored page so rotation does not fragment`() {
        val stored = listOf(HomePage(apps, id = 7L))
        val visual = HomeLayoutMath.visualPages(HomeLayout(stored, seeded = true), apps, cap(3))
        assertEquals(3, visual.size)
        val back = HomeLayoutMath.toStored(visual, stored, emptySet())
        assertEquals(listOf(HomePage(apps, id = 7L)), back)
    }

    @Test
    fun `saving keeps hidden apps in their old place`() {
        val stored = listOf(HomePage(listOf("a", "hidden", "b"), id = 1L), HomePage(listOf("c"), id = 2L))
        val visual = HomeLayoutMath.visualPages(HomeLayout(stored, seeded = true), listOf("a", "b", "c"), cap(9))
        val edited = HomeLayoutMath.move(visual, "c", 0, 0, cap(9))
        val back = HomeLayoutMath.toStored(edited, stored, setOf("hidden"))
        assertEquals(listOf("c", "a", "hidden", "b"), back.flatMap { it.keys })
    }

    @Test
    fun `new pages get fresh ids and duplicate ids are normalised`() {
        val p = listOf(HomePage(listOf("a"), id = 4L))
        assertEquals(5L, HomeLayoutMath.move(p, "x", 1, 0, cap(4))[1].id)
        assertEquals(5L, HomeLayoutMath.insertPage(p, 0)[1].id)
        val ids = HomeLayoutMath.normalizeIds(listOf(HomePage(id = 3L), HomePage(id = 3L), HomePage())).map { it.id }
        assertEquals(ids.toSet().size, 3)
    }

    @Test
    fun `reflow fills gaps left by a density increase and drops emptied pages`() {
        val p = pages(listOf("a", "b"), listOf("c", "d"), listOf("e"))
        assertTrue(HomeLayoutMath.hasGaps(p, cap(4)))
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("e")), HomeLayoutMath.reflow(p, cap(4)).keys())
        assertEquals(listOf(listOf("a", "b", "c", "d", "e")), HomeLayoutMath.reflow(p, cap(9)).keys())
        assertTrue(!HomeLayoutMath.hasGaps(pages(listOf("a", "b"), listOf("c")), cap(2)))
    }

    @Test
    fun `pinned-only home does not auto-place apps`() {
        val layout = HomeLayout(pages = pages(listOf("app1")), seeded = true)
        assertEquals(listOf(listOf("app1")), HomeLayoutMath.visualPages(layout, apps, cap(9), autoPlace = false).keys())
        assertEquals(listOf(listOf<String>()), HomeLayoutMath.visualPages(HomeLayout(), apps, cap(9), autoPlace = false).keys())
    }

    @Test
    fun `tiles survive resolution even though they are not apps`() {
        val layout = HomeLayout(pages = pages(listOf("app1", HomeTiles.CLOCK)), seeded = true)
        assertEquals(listOf("app1", HomeTiles.CLOCK), HomeLayoutMath.visualPages(layout, listOf("app1"), cap(4))[0].keys)
    }

    @Test
    fun `move within a page reorders`() {
        assertEquals(listOf(listOf("b", "c", "a")), HomeLayoutMath.move(pages(listOf("a", "b", "c")), "a", 0, 2, cap(3)).keys())
    }

    @Test
    fun `move into a full page cascades the overflow forward`() {
        val out = HomeLayoutMath.move(pages(listOf("a", "b"), listOf("c", "d")), "d", 0, 0, cap(2))
        assertEquals(listOf(listOf("d", "a"), listOf("b", "c")), out.keys())
    }

    @Test
    fun `overflow page inherits the spilling page's layout`() {
        val circle = PageLayout(shape = PageShape.Circle)
        val out = HomeLayoutMath.move(listOf(HomePage(listOf("a", "b"), circle)), "c", 0, 0, cap(2))
        assertEquals(listOf(listOf("c", "a"), listOf("b")), out.keys())
        assertEquals(circle, out[1].layout)
    }

    @Test
    fun `moving past the end creates a page and only the emptied source page is dropped`() {
        val p = pages(listOf("a"), listOf("b"), emptyList())
        assertEquals(listOf(listOf("b"), emptyList(), listOf("a")), HomeLayoutMath.move(p, "a", 3, 0, cap(4)).keys())
        // Preview keeps the emptied source page so indices stay stable.
        assertEquals(
            listOf(emptyList(), listOf("b"), emptyList(), listOf("a")),
            HomeLayoutMath.move(p, "a", 3, 0, cap(4), dropEmpty = false).keys(),
        )
    }

    @Test
    fun `add prefers the given page and falls back to the last`() {
        val p = pages(listOf("a", "b"), listOf("c"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "x")), HomeLayoutMath.add(p, "x", 0, cap(2)).keys())
        assertEquals(listOf(listOf("a", "b", "x"), listOf("c")), HomeLayoutMath.add(p, "x", 0, cap(3)).keys())
        assertEquals(listOf(listOf("b"), listOf("c")), HomeLayoutMath.remove(p, "a").keys())
    }

    @Test
    fun `page insert, delete and relayout`() {
        val p = pages(listOf("a", "b", "c"), listOf("d"))
        assertEquals(listOf(listOf("a", "b", "c"), emptyList(), listOf("d")), HomeLayoutMath.insertPage(p, 0).keys())
        assertEquals(listOf(listOf("a", "b", "c", "d")), HomeLayoutMath.deletePage(p, 1, cap(9)).keys())
        assertEquals(listOf(listOf("a")), HomeLayoutMath.deletePage(pages(listOf("a")), 0, cap(9)).keys()) // sole page is kept
        val small = PageLayout(portrait = GridSpec(2, 1))
        val relaid = HomeLayoutMath.setLayout(p, 0, small) { if (it == small) 2 else 9 }
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), relaid.keys())
        assertEquals(small, relaid[0].layout)
    }

    @Test
    fun `circle capacity is a centre plus rings of 6n`() {
        assertEquals(7, PageLayout.circleCapacity(1))
        assertEquals(19, PageLayout.circleCapacity(2))
        assertEquals(37, PageLayout.circleCapacity(3))
    }

    // --- codec ----------------------------------------------------------------------------

    @Test
    fun `slots round-trip through the codec`() {
        val slots = listOf(
            CornerSlot.Empty,
            CornerSlot.Single(CornerItem.Action(CornerActionId.Camera)),
            CornerSlot.Single(CornerItem.App("com.x/.Main#0")),
            CornerSlot.Palette("Media", listOf(CornerItem.Action(CornerActionId.Music), CornerItem.App("a/b#10"))),
            CornerSlot.Complication(ComplicationKind.Battery),
            CornerSlot.ComplicationCluster(listOf(ComplicationKind.Date, ComplicationKind.NextAlarm)),
            CornerSlot.Widget(42, "Weather"),
        )
        slots.forEach { assertEquals(it, ShortcutCodec.decodeSlot(ShortcutCodec.encodeSlot(it))) }
        assertNull(ShortcutCodec.decodeSlot("not json"))
        val layoutPages = listOf(
            HomePage(listOf("a", "b"), PageLayout(PageShape.Circle, rings = 3)),
            HomePage(listOf("c"), PageLayout(portrait = GridSpec(4, 6), landscape = GridSpec(7, 3))),
            HomePage(),
        )
        assertEquals(layoutPages, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layoutPages)))
        assertNull(ShortcutCodec.decodeItem("action:NoSuchThing"))
    }

    // --- gestures / suggestion ------------------------------------------------------------

    @Test
    fun `swipes along the paging axis are reserved for paging`() {
        assertTrue(HomeGesture.SwipeUp.usableWith(PagingAxis.Horizontal))
        assertTrue(!HomeGesture.SwipeUp.usableWith(PagingAxis.Vertical))
        assertTrue(HomeGesture.SwipeLeft.usableWith(PagingAxis.Vertical))
        assertTrue(HomeGesture.PinchIn.usableWith(PagingAxis.Vertical))
    }

    @Test
    fun `suggester favours same-hour recent launches and respects exclusions`() {
        val day = 86_400_000L
        val now = 100 * day
        val events = listOf(
            LaunchEvent("morning", now - 1 * day, 8),
            LaunchEvent("morning", now - 2 * day, 8),
            LaunchEvent("evening", now - 1 * day, 20),
            LaunchEvent("evening", now - 2 * day, 20),
            LaunchEvent("evening", now - 3 * day, 21),
        )
        val all = setOf("morning", "evening")
        assertEquals("morning", AppSuggester.suggest(events, now, 8, all, emptySet()))
        assertEquals("evening", AppSuggester.suggest(events, now, 20, all, emptySet()))
        assertEquals("evening", AppSuggester.suggest(events, now, 8, all, setOf("morning")))
        assertNull(AppSuggester.suggest(events, now, 8, setOf("other"), emptySet()))
    }
}

class NetworkStatusMathTest {
    @Test
    fun `airplane mode is grey even if wifi is on`() {
        assertEquals(NetworkKind.Airplane, NetworkStatusMath.classify(true, true, true, -50, null, true).kind)
        assertEquals(NetworkKind.Airplane, NetworkStatusMath.classify(true, false, false, null, null, false).kind)
    }

    @Test
    fun `no connection is None`() {
        assertEquals(NetworkKind.None, NetworkStatusMath.classify(false, false, false, null, 4, false).kind)
    }

    @Test
    fun `strength drives good versus poor`() {
        assertEquals(NetworkKind.Good, NetworkStatusMath.classify(false, true, true, -55, null, true).kind)
        assertEquals(NetworkKind.Poor, NetworkStatusMath.classify(false, true, true, -85, null, true).kind)
        assertEquals(NetworkKind.Good, NetworkStatusMath.classify(false, true, true, null, 4, false).kind)
        assertEquals(NetworkKind.Poor, NetworkStatusMath.classify(false, true, true, null, 1, false).kind)
        // Unvalidated (captive portal / no internet) caps at poor.
        assertEquals(NetworkKind.Poor, NetworkStatusMath.classify(false, true, false, -50, null, true).kind)
    }

    // --- gesture availability per home surface ------------------------------------------------

    private fun touch() = HomeGesture.entries.filter { it.kind == GestureKind.Touch }

    @Test
    fun `paged home keeps the old paging rule`() {
        val h = LauncherPrefs(portraitPaging = PagingAxis.Horizontal, leftPage = LeftPage.Off).surface(landscape = false)
        assertTrue(h.paged && h.hasDensity)
        assertEquals(setOf(HomeGesture.PinchIn, HomeGesture.PinchOut, HomeGesture.SwipeUp, HomeGesture.SwipeDown, HomeGesture.DoubleTap), touch().filter { it.usableOn(h) }.toSet())
        val v = LauncherPrefs(portraitPaging = PagingAxis.Vertical, leftPage = LeftPage.Off).surface(landscape = false)
        assertEquals(setOf(HomeGesture.PinchIn, HomeGesture.PinchOut, HomeGesture.SwipeLeft, HomeGesture.SwipeRight, HomeGesture.DoubleTap), touch().filter { it.usableOn(v) }.toSet())
        for (g in touch()) assertEquals(g.usableWith(PagingAxis.Vertical), g.usableOn(v))
    }

    @Test
    fun `a left page claims swipe right on a vertically paged home only`() {
        val v = LauncherPrefs(portraitPaging = PagingAxis.Vertical, leftPage = LeftPage.Launchpad).surface(landscape = false)
        assertEquals("opens the left page", HomeGesture.SwipeRight.unavailableReason(v))
        assertNull(HomeGesture.SwipeLeft.unavailableReason(v))
        // Scrolling homes open it from the edge only, so swipe right elsewhere stays a gesture.
        val dsi = LauncherPrefs(homeStyle = HomeStyle.Dsi, leftPage = LeftPage.Launchpad).surface(landscape = false)
        assertNull(HomeGesture.SwipeRight.unavailableReason(dsi))
    }

    @Test
    fun `scrolling homes free the cross axis and follow the scroll direction setting`() {
        val dsiPortrait = LauncherPrefs(homeStyle = HomeStyle.Dsi, scrollAxis = ScrollAxis.Long).surface(landscape = false)
        assertEquals(SurfaceScroll.Vertical, dsiPortrait.scroll)
        assertTrue(HomeGesture.SwipeLeft.usableOn(dsiPortrait) && HomeGesture.SwipeRight.usableOn(dsiPortrait))
        assertTrue(!HomeGesture.SwipeUp.usableOn(dsiPortrait) && !HomeGesture.SwipeDown.usableOn(dsiPortrait))
        val dsiLandscape = LauncherPrefs(homeStyle = HomeStyle.Dsi, scrollAxis = ScrollAxis.Long).surface(landscape = true)
        assertEquals(SurfaceScroll.Horizontal, dsiLandscape.scroll)
        assertTrue(HomeGesture.SwipeUp.usableOn(dsiLandscape) && !HomeGesture.SwipeLeft.usableOn(dsiLandscape))
        val continuous = LauncherPrefs(homeScroll = HomeScroll.Continuous, scrollAxis = ScrollAxis.Horizontal).surface(landscape = false)
        assertEquals(HomeSurface(SurfaceScroll.Horizontal, paged = false, hasDensity = true), continuous)
        assertTrue(!LauncherPrefs(homeScroll = HomeScroll.Continuous).isPaged && LauncherPrefs().isPaged)
    }

    @Test
    fun `PSP scrolls both ways so only pinch and double tap remain`() {
        val psp = LauncherPrefs(homeStyle = HomeStyle.Psp).surface(landscape = false)
        assertEquals(setOf(HomeGesture.PinchIn, HomeGesture.PinchOut, HomeGesture.DoubleTap), touch().filter { it.usableOn(psp) }.toSet())
        // Motion gestures never depend on the surface.
        HomeGesture.entries.filter { it.kind == GestureKind.Motion }.forEach { assertNull(it.unavailableReason(psp)) }
    }
}
