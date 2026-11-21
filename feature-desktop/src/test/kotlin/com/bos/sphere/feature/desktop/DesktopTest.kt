package com.bos.sphere.feature.desktop

import com.bos.sphere.core.launch.DropReason
import com.bos.sphere.core.launch.LaunchResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun phone() = TrackedDisplay(0, "Built-in", 1080, 2400, 420, true, true, "res:2400x1080:built-in")
private fun monitor(id: Int = 2, w: Int = 3840, h: Int = 2160, dpi: Int = 240, host: Boolean? = true, name: String = "DELL U2720Q") =
    TrackedDisplay(id, name, w, h, dpi, false, host, DisplayIdentity.key(name, w, h))

class DisplayTrackerTest {
    @Test fun EXT_01_connectAndDisconnectAreSynchronousAndLoseNoState() {
        var s = TrackerState()
        s = DisplayTracker.reduce(s, DisplayEvent.Added(phone())).first
        val (s2, e2) = DisplayTracker.reduce(s, DisplayEvent.Added(monitor()))
        assertEquals(listOf<DisplayEffect>(DisplayEffect.Connected(monitor())), e2)
        assertEquals(setOf(0, 2), s2.displays.keys)
        val (s3, e3) = DisplayTracker.reduce(s2, DisplayEvent.Removed(2))
        assertEquals(listOf<DisplayEffect>(DisplayEffect.Disconnected(monitor())), e3)
        assertEquals(setOf(0), s3.displays.keys)
        assertEquals(phone(), s3.primary)
    }

    @Test fun EXT_01_duplicateAndUnknownEventsAreHarmless() {
        val s = DisplayTracker.reduce(TrackerState(), DisplayEvent.Added(monitor())).first
        assertTrue(DisplayTracker.reduce(s, DisplayEvent.Added(monitor())).second.isEmpty())
        assertTrue(DisplayTracker.reduce(s, DisplayEvent.Removed(99)).second.isEmpty())
        assertEquals(DisplayEffect.Connected(monitor(3)), DisplayTracker.reduce(s, DisplayEvent.Changed(monitor(3))).second.single())
    }

    @Test fun EXT_01_syncRecoversFromMissedCallbacksAndReportsResize() {
        var s = DisplayTracker.sync(TrackerState(), listOf(phone(), monitor(2), monitor(3))).first
        val (s2, e) = DisplayTracker.sync(s, listOf(phone(), monitor(3, w = 1920, h = 1080)))
        assertEquals(setOf(0, 3), s2.displays.keys)
        assertTrue(e.any { it is DisplayEffect.Disconnected && it.display.id == 2 })
        assertTrue(e.any { it is DisplayEffect.Updated && it.display.id == 3 })
    }

    @Test fun EXT_01_flappingCableNeverLeavesStaleDisplays() {
        var s = TrackerState()
        repeat(200) { i ->
            s = if (i % 2 == 0) DisplayTracker.reduce(s, DisplayEvent.Added(monitor())).first else DisplayTracker.reduce(s, DisplayEvent.Removed(2)).first
        }
        assertTrue(s.displays.isEmpty())
    }

    @Test fun EXT_11_mirroredDisplayIsNeverEligible() {
        val mirror = monitor(5, 1080, 2400, host = false)
        assertTrue(DisplayTracker.looksMirrored(mirror, phone()))
        assertFalse(DisplayTracker.looksMirrored(monitor(), phone()))
        val m = mirror.copy(mirrored = true)
        assertFalse(m.eligible)
        assertEquals(OfferDecision.Skip(SkipReason.Mirrored), DesktopPolicy.onConnected(m, OfferSetting.Always, true, false, false))
    }
}

class DesktopPolicyTest {
    private val d = monitor()

    @Test fun EXT_02_firstConnectionIsOfferedOnceThenSettingDecides() {
        assertEquals(OfferDecision.Prompt, DesktopPolicy.onConnected(d, OfferSetting.Ask, false, false, false))
        assertEquals(OfferDecision.Prompt, DesktopPolicy.onConnected(d, OfferSetting.Always, false, false, false))
        assertEquals(OfferDecision.OpenNow, DesktopPolicy.onConnected(d, OfferSetting.Always, true, false, false))
        assertEquals(OfferDecision.Prompt, DesktopPolicy.onConnected(d, OfferSetting.Ask, true, false, false))
        assertEquals(OfferDecision.Skip(SkipReason.Never), DesktopPolicy.onConnected(d, OfferSetting.Never, true, false, false))
        assertEquals(OfferDecision.Skip(SkipReason.Never), DesktopPolicy.onConnected(d, OfferSetting.Never, false, false, false))
    }

    @Test fun EXT_02_nothingIsOfferedWhereTasksCannotRunOrPlatformGivesHome() {
        assertEquals(OfferDecision.Skip(SkipReason.NotEligible), DesktopPolicy.onConnected(monitor(host = false), OfferSetting.Always, true, false, false))
        assertEquals(OfferDecision.Skip(SkipReason.NotEligible), DesktopPolicy.onConnected(monitor(host = null), OfferSetting.Always, true, false, false))
        assertEquals(OfferDecision.Skip(SkipReason.NotEligible), DesktopPolicy.onConnected(phone(), OfferSetting.Always, true, false, false))
        assertEquals(OfferDecision.Skip(SkipReason.PlatformProvidesHome), DesktopPolicy.onConnected(d, OfferSetting.Always, false, true, false))
        assertEquals(OfferDecision.Skip(SkipReason.AlreadyOpen), DesktopPolicy.onConnected(d, OfferSetting.Always, true, false, true))
        assertTrue(DesktopPolicy.platformProvidesHome(setOf(0, 2), 2))
        assertFalse(DesktopPolicy.platformProvidesHome(setOf(0), 2))
    }

    @Test fun EXT_06_disconnectClosesByDefaultAndMigratesWithoutDuplicatingHome() {
        val s = SurfaceInfo(2, 2, d.identity)
        assertEquals(DisconnectAction.Close, DesktopPolicy.onDisconnected(s, 2, listOf(s), 0, migrate = false))
        assertEquals(DisconnectAction.MigrateToPrimary, DesktopPolicy.onDisconnected(s, 2, listOf(s), 0, migrate = true))
        // Another surface already sits on the primary display: close instead of duplicating.
        val other = SurfaceInfo(0, 0, "x")
        assertEquals(DisconnectAction.Close, DesktopPolicy.onDisconnected(s, 2, listOf(s, other), 0, migrate = true))
        // A surface of another display is untouched.
        assertEquals(DisconnectAction.None, DesktopPolicy.onDisconnected(other, 2, listOf(s, other), 0, migrate = true))
    }

    @Test fun EXT_06_twoMigratingSurfacesLeaveOnlyOneOnPrimary() {
        val a = SurfaceInfo(2, 2, "a"); val b = SurfaceInfo(3, 3, "b")
        val first = DesktopPolicy.onDisconnected(a, 2, listOf(a, b), 0, true)
        assertEquals(DisconnectAction.MigrateToPrimary, first)
        val aMoved = a.copy(currentDisplayId = 0)
        assertEquals(DisconnectAction.Close, DesktopPolicy.onDisconnected(b, 3, listOf(aMoved, b), 0, true))
    }

    @Test fun EXT_06_migratedSurfaceYieldsWhenItsDisplayReturns() {
        val migrated = SurfaceInfo(2, 0, d.identity)
        assertTrue(DesktopPolicy.migratedShouldYield(migrated, 0, d))
        assertFalse(DesktopPolicy.migratedShouldYield(migrated, 0, monitor(name = "Other")))
        assertFalse(DesktopPolicy.migratedShouldYield(SurfaceInfo(2, 2, d.identity), 0, d))
    }
}

class DisplayIdentityTest {
    @Test fun EXT_09_usesProductInfoWhenAvailable() {
        val a = DisplayIdentity.key("HDMI-1", 3840, 2160, "DEL", "A0B1", 2020)
        val b = DisplayIdentity.key("DisplayPort", 1920, 1080, "DEL", "A0B1", 2020)
        assertEquals(a, b)
        assertNotEquals(a, DisplayIdentity.key("HDMI-1", 3840, 2160, "SAM", "A0B1", 2020))
    }

    @Test fun EXT_09_fallsBackToResolutionAndNameAndIgnoresRotation() {
        val a = DisplayIdentity.key("Lenovo Monitor", 1920, 1080)
        assertEquals(a, DisplayIdentity.key("lenovo  monitor", 1080, 1920))
        assertNotEquals(a, DisplayIdentity.key("Lenovo Monitor", 3840, 2160))
        assertTrue(a.startsWith("res:"))
    }

    @Test fun EXT_09_profilesAreKeyedPerDisplayAndSurviveReconnect() {
        val repo = DesktopProfileRepository(MapKeyValue())
        val k1 = DisplayIdentity.key("A", 1920, 1080); val k2 = DisplayIdentity.key("B", 3840, 2160)
        repo.save(k1, DesktopProfile(listOf(PlacedItem("app", "x/y#1", 10f, 20f, 70f, 70f)), 200, DockMode.Left))
        assertEquals(DesktopProfile(), repo.load(k2))
        // "Reconnect": a fresh repository over the same storage restores arrangement, density and dock mode.
        val p = repo.load(k1)
        assertEquals(200, p.densityOverrideDpi); assertEquals(DockMode.Left, p.dockMode); assertEquals(1, p.items.size)
    }
}

class ProfileCodecTest {
    @Test fun EXT_03_codecRoundTripsAwkwardIds() {
        val p = DesktopProfile(
            listOf(PlacedItem("app", "com.a/.Main#0|;,\\x", 1.5f, 2f, 3f, 4f), PlacedItem("widget", "clock", 0f, 0f, 200f, 90f)),
            null, DockMode.Hidden,
        )
        assertEquals(p, ProfileCodec.decode(ProfileCodec.encode(p)))
    }

    @Test fun EXT_03_garbageDecodesToDefaultNotACrash() {
        assertNull(ProfileCodec.decode("nonsense"))
        assertNull(ProfileCodec.decode(null))
        assertEquals(DesktopProfile(), DesktopProfileRepository(MapKeyValue(mutableMapOf("profile:k" to "v1|dock=Nope"))).load("k"))
    }

    @Test fun EXT_03_displaysHaveIndependentArrangement() {
        val repo = DesktopProfileRepository(MapKeyValue())
        repo.update("a") { it.copy(items = it.items + PlacedItem("app", "one", 0f, 0f, 1f, 1f)) }
        repo.update("b") { it.copy(dockMode = DockMode.Right) }
        assertEquals(1, repo.load("a").items.size); assertEquals(DockMode.Bottom, repo.load("a").dockMode)
        assertEquals(0, repo.load("b").items.size)
    }
}

class DesktopMetricsTest {
    private fun check(w: Int, h: Int, dpi: Int, insets: InsetsPx = InsetsPx.None, mode: DockMode = DockMode.Bottom, home: Boolean = false) {
        val l = DesktopMetrics.compute(w, h, dpi, insets, mode, home)
        val window = RectPx(0, 0, w, h)
        assertTrue("work inside window", window.contains(l.workArea))
        l.dock?.let { d ->
            assertTrue("dock inside window", window.contains(d))
            assertFalse("dock overlaps work area", d.intersects(l.workArea))
            // EXT-08: never into the system inset regions.
            assertFalse(d.intersects(RectPx(0, h - insets.bottom, w, h)) && insets.bottom > 0)
            assertFalse(d.intersects(RectPx(0, 0, w, insets.top)) && insets.top > 0)
            assertFalse(d.intersects(RectPx(0, 0, insets.left, h)) && insets.left > 0)
            assertFalse(d.intersects(RectPx(w - insets.right, 0, w, h)) && insets.right > 0)
        }
        assertTrue(l.columns >= 1 && l.rows >= 1)
        // Nothing oversized: icons and cells fit the window and the grid fits the work area.
        assertTrue(l.iconDp * 4 <= minOf(l.widthDp, l.heightDp) + 100f)
        assertTrue(l.cellDp * l.scale * l.columns <= l.workArea.width + l.cellDp * l.scale)
        assertTrue(l.iconDp >= 24f)
    }

    @Test fun EXT_04_1080pAndFourKAtCommonDensities() {
        for (dpi in listOf(120, 160, 213, 240, 280, 320)) { check(1920, 1080, dpi); check(3840, 2160, dpi) }
    }

    @Test fun EXT_04_layoutFollowsOwnDensityNotThePhones() {
        val a = DesktopMetrics.compute(3840, 2160, 240)
        val b = DesktopMetrics.compute(3840, 2160, 320)
        assertNotEquals(a.widthDp, b.widthDp)
        assertEquals(SizeClass.Expanded, a.sizeClass)
        val o = DesktopMetrics.compute(3840, 2160, 240, densityOverrideDpi = 320)
        assertEquals(b.widthDp, o.widthDp, 0.01f)
    }

    @Test fun EXT_05_downToCompactSizeClass() {
        // maximised, a freeform window, half split and a narrow split pane on a 160 dpi display
        for ((w, h) in listOf(1920 to 1080, 1200 to 800, 960 to 1080, 600 to 900, 480 to 640, 360 to 400)) check(w, h, 160)
        assertEquals(SizeClass.Compact, DesktopMetrics.compute(480, 640, 160).sizeClass)
        assertEquals(SizeClass.Medium, DesktopMetrics.compute(700, 800, 160).sizeClass)
        assertEquals(SizeClass.Expanded, DesktopMetrics.compute(960, 1080, 160).sizeClass)
        for (m in DockMode.entries) check(360, 400, 160, mode = m)
    }

    @Test fun EXT_08_dockStaysClearOfTheSystemTaskbarOnEveryEdge() {
        check(1920, 1080, 160, InsetsPx(bottom = 48), home = true)
        check(1920, 1080, 160, InsetsPx(left = 56), mode = DockMode.Left, home = true)
        check(1920, 1080, 160, InsetsPx(right = 56), mode = DockMode.Right, home = true)
        check(3840, 2160, 240, InsetsPx(top = 24, bottom = 96), home = true)
        val l = DesktopMetrics.compute(1920, 1080, 160, InsetsPx(bottom = 48), systemHomePresent = true)
        assertTrue(l.dock!!.bottom <= 1080 - 48 - 8)
        val without = DesktopMetrics.compute(1920, 1080, 160, InsetsPx(bottom = 48), systemHomePresent = false)
        assertEquals(1080 - 48, without.dock!!.bottom)
    }

    @Test fun EXT_08_hiddenDockLeavesWholeUsableArea() {
        val l = DesktopMetrics.compute(1920, 1080, 160, InsetsPx(bottom = 48), DockMode.Hidden)
        assertNull(l.dock); assertEquals(RectPx(0, 0, 1920, 1032), l.workArea)
    }
}

class FreeformPlacementTest {
    private val box = FRect(0f, 0f, 400f, 300f)

    @Test fun HOM_13_snapsToContainerAndNeighboursWithGuides() {
        val other = FRect(100f, 100f, 180f, 160f)
        val r = FreeformPlacement.snap(FRect(183f, 2f, 243f, 52f), listOf(other), box, 6f)
        assertEquals(180f, r.rect.left, 0.001f)
        assertEquals(0f, r.rect.top, 0.001f)
        assertTrue(r.guides.contains(Guide(true, 180f)))
        assertTrue(r.guides.contains(Guide(false, 0f)))
    }

    @Test fun HOM_13_noSnapOutsideThresholdAndAlwaysInsideContainer() {
        val r = FreeformPlacement.snap(FRect(37f, 41f, 87f, 91f), emptyList(), box, 4f)
        assertEquals(37f, r.rect.left, 0.001f)
        val out = FreeformPlacement.snap(FRect(-50f, 500f, 0f, 550f), emptyList(), box, 4f).rect
        assertTrue(out.inside(box))
    }

    @Test fun HOM_13_centreAlignmentGuide() {
        val other = FRect(100f, 0f, 200f, 50f)
        val r = FreeformPlacement.snap(FRect(130f, 100f, 170f, 140f), listOf(other), box, 6f)
        assertEquals(150f, r.rect.cx, 0.001f)
        assertTrue(r.guides.contains(Guide(true, 150f)))
    }

    @Test fun HOM_13_widgetsNeverOverlapEachOther() {
        val rnd = Random(7)
        val widgets = mutableListOf<FRect>()
        repeat(200) {
            val w = 40f + rnd.nextFloat() * 80f; val h = 30f + rnd.nextFloat() * 60f
            val want = FRect(0f, 0f, w, h).at(rnd.nextFloat() * 400f, rnd.nextFloat() * 300f)
            val placed = FreeformPlacement.placeWidget(want, widgets, box, 6f)
            if (placed != null) {
                assertTrue(placed.rect.inside(box))
                assertTrue("overlap", widgets.none { it.overlaps(placed.rect) })
                widgets += placed.rect
            }
        }
        assertTrue(widgets.size > 3)
        for (i in widgets.indices) for (j in i + 1 until widgets.size) assertFalse(widgets[i].overlaps(widgets[j]))
    }

    @Test fun HOM_13_overlappingDropMovesToNearestFreeSpot() {
        val existing = listOf(FRect(100f, 100f, 200f, 180f))
        val r = FreeformPlacement.placeWidget(FRect(110f, 110f, 190f, 170f), existing, box, 0f)!!
        assertFalse(existing[0].overlaps(r.rect))
        // flush against the existing widget, not thrown to a far corner
        assertTrue(kotlin.math.hypot(r.rect.left - 110f, r.rect.top - 110f) < 120f)
    }

    @Test fun HOM_13_widgetThatFitsNowhereIsRejectedAndTouchingIsAllowed() {
        val full = listOf(FRect(0f, 0f, 400f, 300f))
        assertNull(FreeformPlacement.placeWidget(FRect(0f, 0f, 50f, 50f), full, box, 6f))
        assertNull(FreeformPlacement.placeWidget(FRect(0f, 0f, 500f, 50f), emptyList(), box, 6f))
        assertFalse(FRect(0f, 0f, 10f, 10f).overlaps(FRect(10f, 0f, 20f, 10f)))
        assertNotNull(FreeformPlacement.placeWidget(FRect(0f, 0f, 50f, 50f), listOf(FRect(50f, 0f, 100f, 50f)), box, 0f))
    }
}

class PhoneInputTest {
    @Test fun EXT_10_cursorMovesWithAccelerationAndStaysOnTheSurface() {
        val c = CursorModel(1000f, 500f)
        val x0 = c.x
        c.move(5f, 0f); val slow = c.x - x0
        val x1 = c.x
        c.move(50f, 0f); val fast = c.x - x1
        assertTrue(fast > slow * 10)
        c.move(100000f, 100000f)
        assertEquals(1000f, c.x, 0.001f); assertEquals(500f, c.y, 0.001f)
        c.move(-100000f, -100000f)
        assertEquals(0f, c.x, 0.001f)
        c.resize(200f, 100f)
        assertTrue(c.x <= 200f && c.y <= 100f)
    }

    @Test fun EXT_10_hitTestPicksTopmostItem() {
        val items = listOf("under" to FRect(0f, 0f, 100f, 100f), "over" to FRect(50f, 50f, 150f, 150f))
        assertEquals("over", HitTest.itemAt(60f, 60f, items))
        assertEquals("under", HitTest.itemAt(10f, 10f, items))
        assertNull(HitTest.itemAt(500f, 500f, items))
    }

    @Test fun EXT_10_commandsAreDroppedUnlessADesktopSurfaceIsFocused() {
        DesktopInputBus.setFocused(false)
        assertFalse(DesktopInputBus.send(InputCommand.Click))
        DesktopInputBus.setFocused(true)
        assertTrue(DesktopInputBus.send(InputCommand.Click))
        DesktopInputBus.setFocused(false)
    }
}

class DesktopLaunchTest {
    private fun state() = DisplayTracker.sync(TrackerState(), listOf(phone(), monitor(2), monitor(4, host = false))).first

    @Test fun EXT_07_launchTargetsEitherDisplay() {
        val caps = DesktopLaunch.caps(state(), boundsHonoured = false)
        val toExt = LaunchResolver.resolve(DesktopLaunch.request("p", null, 0, 2), caps)
        assertEquals(2, toExt.displayId); assertTrue(toExt.dropped.isEmpty())
        val toPhone = LaunchResolver.resolve(DesktopLaunch.request("p", null, 0, 0), caps)
        assertEquals(0, toPhone.displayId); assertTrue(toPhone.dropped.isEmpty())
        assertEquals(0, DesktopLaunch.otherDisplay(state(), 2))
        assertEquals(2, DesktopLaunch.otherDisplay(state(), 0))
    }

    @Test fun EXT_07_refusingOrMissingDisplayFallsBackWithTheNotice() {
        val caps = DesktopLaunch.caps(state(), boundsHonoured = false)
        assertEquals(listOf(DropReason.DisplayCannotHost), LaunchResolver.resolve(DesktopLaunch.request("p", null, 0, 4), caps).dropped)
        val gone = LaunchResolver.resolve(DesktopLaunch.request("p", null, 0, 9), caps)
        assertEquals(listOf(DropReason.DisplayMissing), gone.dropped); assertNull(gone.displayId)
    }

    @Test fun EXT_02_desktopSurfaceIsLaunchedWithTheDisplayIdThroughCoreLaunch() {
        val r = DesktopLaunch.request("com.bos.sphere", DesktopController.SURFACE_CLASS, 0, 2)
        assertEquals(2, r.displayId); assertEquals("com.bos.sphere.DesktopActivity", r.target.className)
        assertEquals(2, LaunchResolver.resolve(r, DesktopLaunch.caps(state(), false)).displayId)
    }
}
