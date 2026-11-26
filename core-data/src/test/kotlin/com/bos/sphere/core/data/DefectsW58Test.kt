package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 58 (defects58): the logic fixes of the code audit, one rule per test (CFG-61 to CFG-63). */
class DefectsW58Test {
    // ---- CFG-61 clock format ----------------------------------------------------------------------------------------------------

    @Test fun CFG_61_systemIsTheDefaultClockFormatAndIsNamedForWhatItDoes() {
        assertEquals(ClockFormat.System, LauncherPrefs().clockFormat)
        assertEquals(ClockFormat.System, ClockFormat.entries.first())
        assertEquals("System (12/24 by device)", ClockFormat.System.label)
    }

    // ---- CFG-62 settings logic ----------------------------------------------------------------------------------------------------

    @Test fun CFG_62_pinchDensityRemembersTheBindingsItReplacesAndRestoresThem() {
        val custom = mapOf(HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Camera), HomeGesture.PinchOut to CornerItem.App("com.mail/.Main#0"))
        val on = PinchDensityToggle.on(custom)
        assertEquals(CornerItem.Action(CornerActionId.GridDenser), on.bindings[HomeGesture.PinchIn])
        assertEquals(custom, on.saved)
        val off = PinchDensityToggle.off(on.saved)
        assertEquals(custom, off.bindings)
        assertNull(off.saved)
        // Nothing remembered: the defaults (pinch in = Radar, pinch out = nothing).
        assertEquals(mapOf(HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Search), HomeGesture.PinchOut to null), PinchDensityToggle.off(null).bindings)
    }

    @Test fun CFG_62_theRememberedPinchBindingsSurviveARestart() {
        val sp = MapSharedPreferences()
        ShortcutStore(sp).savePinch(mapOf(HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Camera), HomeGesture.PinchOut to null))
        assertEquals(mapOf(HomeGesture.PinchIn to CornerItem.Action(CornerActionId.Camera), HomeGesture.PinchOut to null), ShortcutStore(sp).savedPinch())
        ShortcutStore(sp).savePinch(null)
        assertNull(ShortcutStore(sp).savedPinch())
    }

    @Test fun CFG_62_swipeLeftIsUnboundByDefaultAndAnOlderInstallKeepsRadarOnlyWhereItWorks() {
        assertNull(DefaultGestures[HomeGesture.SwipeLeft])
        assertNull("a fresh install has no swipe-left binding", ShortcutStore(MapSharedPreferences()).gestures.value[HomeGesture.SwipeLeft])
        // The default sideways paged home: swipe left could never fire, so the new default applies.
        val dead = MapSharedPreferences()
        assertFalse(GestureDefaultsW58.migrate(dead, LauncherPrefs(homePages = HomePages.Paginated), existing = true))
        assertNull(ShortcutStore(dead).gestures.value[HomeGesture.SwipeLeft])
        // A home where swipe left fires (pages turned up and down): Radar is written down, so nothing changes for that user.
        val live = MapSharedPreferences()
        assertTrue(GestureDefaultsW58.migrate(live, LauncherPrefs(homePages = HomePages.Paginated, portraitPaging = PagingAxis.Vertical), existing = true))
        assertEquals(CornerItem.Action(CornerActionId.Search), ShortcutStore(live).gestures.value[HomeGesture.SwipeLeft])
        // An explicit choice is never touched; the migration runs once; a fresh install only gets the marker.
        val chosen = MapSharedPreferences().also { it.edit().putString("gesture.SwipeLeft", "").commit() }
        assertFalse(GestureDefaultsW58.migrate(chosen, LauncherPrefs(homePages = HomePages.Paginated, portraitPaging = PagingAxis.Vertical), existing = true))
        assertEquals("", chosen.getString("gesture.SwipeLeft", null))
        assertFalse(GestureDefaultsW58.migrate(live, LauncherPrefs(), existing = true))
        val fresh = MapSharedPreferences()
        assertFalse(GestureDefaultsW58.migrate(fresh, LauncherPrefs(homePages = HomePages.Paginated, portraitPaging = PagingAxis.Vertical), existing = false))
        assertFalse(fresh.contains("gesture.SwipeLeft"))
    }

    @Test fun CFG_62_appsOnHomeIsGoneFromTheSettingsAndTheMigrationStillReadsTheOldValue() {
        assertTrue("no appsOnHome field", LauncherPrefs::class.java.declaredFields.none { it.name == "appsOnHome" })
        assertFalse("not exported", SettingsCodec.encode(LauncherPrefs()).getJSONObject("prefs").has("appsOnHome"))
        val imported = SettingsCodec.decode(org.json.JSONObject().put("prefs", org.json.JSONObject().put("appsOnHome", "Everything")))
        assertFalse("an old file's value is dropped, not kept as an unknown field", imported.unknown.has("appsOnHome"))
        val old = MapSharedPreferences(mapOf("style" to "Grid", "appsOnHome" to "Everything"))
        val store = LauncherPrefsStore(old)
        assertTrue(store.legacyAppsEverything())
        store.dropLegacyAppsOnHome()
        assertFalse(old.contains("appsOnHome"))
        assertFalse("a fresh install never had All apps", LauncherPrefsStore(MapSharedPreferences()).legacyAppsEverything())
        assertFalse(LauncherPrefsStore(MapSharedPreferences(mapOf("style" to "Grid", "appsOnHome" to "PinnedOnly"))).legacyAppsEverything())
    }

    @Test fun CFG_62_gridContinuousIsASidewaysSurfaceInBothOrientations() {
        val p = LauncherPrefs(homeStyle = HomeStyle.Grid, homePages = HomePages.Continuous)
        for (landscape in listOf(false, true)) {
            val s = p.surface(landscape)
            assertEquals(SurfaceScroll.Horizontal, s.scroll)
            assertEquals("the scroll", HomeGesture.SwipeLeft.unavailableReason(s)?.let { "the scroll" })
            assertNull("swipe up works on the column scroll", HomeGesture.SwipeUp.unavailableReason(s))
        }
        // DSi keeps its own axis setting.
        assertEquals(SurfaceScroll.Vertical, LauncherPrefs(homeStyle = HomeStyle.Dsi, scrollAxis = ScrollAxis.Vertical).surface(false).scroll)
    }

    @Test fun CFG_62_theThemeUndoSurvivesProcessDeath() {
        val sp = MapSharedPreferences()
        ThemeUndo.attach(sp)
        val look = ThemeLook.snapshot(
            LauncherPrefs(themeMode = ThemeMode.Light, accentColor = 0xFF22AA66, iconShape = IconShape.entries.last(), calmStyle = CalmStyle.Beam, iconPlate = false)
                .withWallpaper(WallpaperSurface.Glance, Wallpaper(kind = WallpaperKind.Gradient, gradient = GradientSpec(0xFF102030, 0xFF405060, 90))),
            DockStyle.entries.last(),
        )
        ThemeUndo.remember(look)
        ThemeUndo.clear(); ThemeUndo.attach(MapSharedPreferences()) // another process, empty file: nothing
        assertNull(ThemeUndo.snapshot.value)
        ThemeUndo.attach(sp) // the process with the kept file, after a restart
        assertNull("clear() forgot it in the file too", ThemeUndo.snapshot.value)
        ThemeUndo.remember(look)
        ThemeUndo.attach(sp)
        assertEquals(look, ThemeUndo.snapshot.value)
        ThemeUndo.clear()
        ThemeUndo.attach(sp)
        assertNull(ThemeUndo.snapshot.value)
    }

    @Test fun CFG_62_aProfileWithoutALeftWidgetKeepsTheGlobalLeftPage() {
        val widgetLeft = LauncherPrefs(leftPage = LeftPage.Widget, leftWidgetId = 12)
        val after = ProfileLeftWidget.apply(widgetLeft, null)
        assertEquals(LeftPage.Widget, after.leftPage)
        assertEquals(LauncherPrefs().leftWidgetId, after.leftWidgetId)
        assertEquals(LeftPage.Widget to 30, ProfileLeftWidget.apply(LauncherPrefs(leftPage = LeftPage.Launchpad), 30).let { it.leftPage to it.leftWidgetId })
        assertEquals(LauncherPrefs(leftPage = LeftPage.Launchpad), ProfileLeftWidget.apply(LauncherPrefs(leftPage = LeftPage.Launchpad), null))
    }

    // ---- CFG-63 first run ----------------------------------------------------------------------------------------------------

    @Test fun CFG_63_theOnboardingLookHasAVisibleEffect() {
        val fresh = LauncherPrefs(homePages = HomePages.Off, glanceEnabled = true)
        assertEquals(OnboardingLook.GlanceOnly, OnboardingLook.of(fresh))
        val list = OnboardingFlow.applyLook(fresh, OnboardingLook.List)
        assertEquals(HomeStyle.List, list.homeStyle); assertEquals(HomePages.Paginated, list.homePages)
        assertEquals(OnboardingLook.List, OnboardingLook.of(list))
        val back = OnboardingFlow.applyLook(list, OnboardingLook.GlanceOnly)
        assertEquals(HomePages.Off, back.homePages); assertTrue(back.glanceEnabled)
        assertEquals("Continuous stays Continuous", HomePages.Continuous, OnboardingFlow.applyLook(fresh.copy(homePages = HomePages.Continuous), OnboardingLook.Grid).homePages)
    }

    @Test fun CFG_63_glanceShowsItsLongPressHintOnceAfterOnboarding() {
        assertTrue(FirstRunHints.showsGlanceHint(HomeShown.Glance, shownBefore = false, onboardingDone = true, editing = false))
        assertFalse(FirstRunHints.showsGlanceHint(HomeShown.Glance, shownBefore = true, onboardingDone = true, editing = false))
        assertFalse("not over onboarding", FirstRunHints.showsGlanceHint(HomeShown.Glance, shownBefore = false, onboardingDone = false, editing = false))
        assertFalse("pages keep their own hint", FirstRunHints.showsGlanceHint(HomeShown.Pages, shownBefore = false, onboardingDone = true, editing = false))
        val sp = MapSharedPreferences()
        assertFalse(EditChromeStore(sp).glanceHintShown())
        EditChromeStore(sp).markGlanceHintShown()
        assertTrue("remembered across a restart", EditChromeStore(sp).glanceHintShown())
    }
}
