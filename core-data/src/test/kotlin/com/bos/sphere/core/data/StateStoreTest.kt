package com.bos.sphere.core.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---- CFG-04 snapshots -------------------------------------------------------------------

    private fun store(max: Int = 5, clock: () -> Long) = SnapshotStore(File(tmp.root, "snaps"), max, clock)

    @Test
    fun CFG_04_keepsOnlyTheLastFiveSnapshots() {
        var t = 1_000L
        val s = store { t++ }
        repeat(8) { s.take("edit $it", "{\"n\":$it}") }
        val list = s.list()
        assertEquals(5, list.size)
        assertEquals(listOf(7, 6, 5, 4, 3), list.map { s.read(it.id)!!.filter(Char::isDigit).toInt() })
        assertEquals("edit 7", list.first().reason)
    }

    @Test
    fun CFG_04_identicalStateIsNotSnapshottedTwiceButForceDoes() {
        var t = 1L
        val s = store { t++ }
        assertNotNull(s.take("a", "{\"x\":1,\"exportedAt\":1}"))
        assertNull(s.take("b", "{\"x\":1,\"exportedAt\":2}"))
        assertNotNull(s.take("before import", "{\"x\":1,\"exportedAt\":3}", force = true))
        assertEquals(2, s.list().size)
    }

    @Test
    fun CFG_04_restoreBringsBackTheSnapshottedStateInOneStep() {
        val p = StateTestSupport.Process(extrasFile = tmp.newFile("e.json"))
        var t = 1L
        val snaps = store { t++ }
        p.prefs.update { it.copy(homeStyle = HomeStyle.List) }
        p.layout.setPages(listOf(HomePage(listOf("one/.A#0"))))
        val good = snaps.take("good", StateCodec.export(p.applier.current(), 1L).encode())!!
        // Destructive edit: snapshot first, then change.
        snaps.take("before delete page", StateCodec.export(p.applier.current(), 2L).encode(), force = true)
        p.prefs.update { it.copy(homeStyle = HomeStyle.Dsi) }
        p.layout.setPages(emptyList())
        // One call restores.
        val dec = StateCodec.decode(StateDocument.parse(snaps.read(good.id)!!))
        p.applier.apply(dec.parts, dec.present)
        assertEquals(HomeStyle.List, p.prefs.prefs.value.homeStyle)
        assertEquals(listOf("one/.A#0"), p.layout.layout.value.pages.single().keys)
    }

    @Test
    fun CFG_04_snapshotIdsCannotEscapeTheFolder() {
        val s = store { 1L }
        assertNull(s.read("../x.json"))
    }

    // ---- CFG-09 safe mode ---------------------------------------------------------------------

    @Test
    fun CFG_09_safeModeBootsAMinimalGridAndOffersTheLastSnapshot() {
        val infos = listOf(SnapshotStore.Info("old", 10, "x", 1), SnapshotStore.Info("new", 20, "y", 1))
        val offer = SafeMode.plan(true, infos)
        assertTrue(offer.showMinimalGrid)
        assertEquals("new", offer.restoreFrom?.id)
        assertFalse(SafeMode.widgetsEnabled(true))
        assertNull(SafeMode.plan(true, emptyList()).restoreFrom)
        val normal = SafeMode.plan(false, infos)
        assertFalse(normal.showMinimalGrid)
        assertNull(normal.restoreFrom)
        assertTrue(SafeMode.widgetsEnabled(false))
    }

    @Test
    fun CFG_09_twoStartsWithin30SecondsEnterSafeMode() {
        val (s1, safe1) = CrashLoopMath.decide(null, 0)
        assertFalse(safe1)
        val (_, safe2) = CrashLoopMath.decide(s1, 20_000)
        assertTrue(safe2)
        val (_, safe3) = CrashLoopMath.decide(CrashLoopMath.markStable(s1), 20_000)
        assertFalse(safe3)
    }

    // ---- build 31 bridge ----------------------------------------------------------------------

    @Test
    fun CFG_01_build31FixtureRoundTripsThroughTheStateFile() {
        val files = StateTestSupport.build31Files()
        val parts = Build31Bridge.fromPrefFiles(files)
        // What build 31 showed:
        assertEquals(HomeStyle.Psp, parts.prefs.homeStyle)
        assertEquals(HomeScroll.Continuous, parts.prefs.homeScroll)
        assertEquals(0.62f, parts.prefs.iconScale, 0f)
        assertEquals(mapOf(StripItem.Time to StripZone.Left, StripItem.Battery to StripZone.Right), parts.prefs.strip)
        assertEquals(BackgroundKind.System, parts.prefs.backgroundKind)
        assertEquals(UiLanguage.German, parts.prefs.language)
        assertEquals(3, parts.layout.pages.first().keys.size)
        assertEquals(PageShape.Circle, parts.layout.pages[1].layout.shape)
        assertEquals(setOf("com.example.old/.Main#0"), parts.layout.removed)
        assertEquals(CornerSlot.Widget(77, "Weather"), parts.corners[Corner.TopRight])
        assertEquals("Quick", (parts.corners[Corner.BottomLeft] as CornerSlot.Palette).name)
        assertEquals(null, parts.gestures[HomeGesture.PinchIn])
        assertEquals(GroupMode.App, parts.launchpadPrefs.defaultMode)
        // Through the file and back: every section equal except the device-bound values, which must not travel.
        val back = StateCodec.decode(StateDocument.parse(StateCodec.export(parts, 1L).encode())).parts
        val expectedPrefs = parts.prefs.copy(leftWidgetId = -1, backgroundPicturePath = "", onboardingDone = true)
        assertEquals(expectedPrefs, back.prefs)
        assertEquals(parts.layout, back.layout)
        assertEquals(parts.corners + (Corner.TopRight to CornerSlot.Empty), back.corners)
        assertEquals(parts.gestures, back.gestures)
        assertEquals(parts.launchpadRules, back.launchpadRules)
        assertEquals(parts.launchpadPrefs, back.launchpadPrefs)
        assertEquals(parts.launchpadGestures, back.launchpadGestures)
        assertEquals("kept", back.extras.unknownPrefs.getString("someFutureKey"))
        assertEquals(listOf("corner.TopRight", "leftPage"), back.extras.widgets.map { it.slot })
    }

    @Test
    fun CFG_01_bridgeOfAnEmptyInstallIsTheDefaultSetup() {
        val parts = Build31Bridge.fromPrefFiles(emptyMap())
        assertEquals(LauncherPrefsStore(MapSharedPreferences()).prefs.value, parts.prefs)
        assertEquals(DefaultCorners, parts.corners)
    }

    // ---- extras store -------------------------------------------------------------------------

    @Test
    fun CFG_03_extrasStoreKeepsOtherStreamsSections() {
        val f = File(tmp.root, "x/extras.json")
        val s = StateExtrasStore(f)
        s.setSection(StateSchema.K_SESSIONS, JSONArray().put("s1"))
        assertEquals("s1", StateExtrasStore(f).section(StateSchema.K_SESSIONS).getString(0))
        assertEquals(0, s.section(StateSchema.K_WORKSPACES).length())
    }

    // ---- NFR-11 process death -----------------------------------------------------------------

    @Test
    fun NFR_11_aNewProcessSeesTheSameStateFromTheStore() {
        val a = StateTestSupport.Process(extrasFile = tmp.newFile("pd.json"))
        a.prefs.update { it.copy(homeStyle = HomeStyle.Psp, homePage = 2, themeMode = ThemeMode.Light) }
        a.layout.setPages(listOf(HomePage(listOf("x/.X#0"), PageLayout(PageShape.Coverflow), id = 7)))
        a.shortcuts.setCorner(Corner.BottomRight, CornerSlot.Single(CornerItem.App("x/.X#0")))
        a.shortcuts.setGesture(HomeGesture.DoubleTap, null)
        a.extras.setSection(StateSchema.K_WORKSPACES, JSONArray().put("w"))
        // "Process death": every in-memory object is dropped; only the persisted preference maps and the extras file remain.
        val b = StateTestSupport.Process(a.prefsSp, a.layoutSp, a.shortcutsSp, a.launchpad, tmp.root.resolve("pd.json"))
        assertEquals(a.prefs.prefs.value, b.prefs.prefs.value)
        assertEquals(a.layout.layout.value, b.layout.layout.value)
        assertEquals(a.shortcuts.corners.value, b.shortcuts.corners.value)
        assertEquals(a.shortcuts.gestures.value, b.shortcuts.gestures.value)
        assertEquals("w", b.extras.section(StateSchema.K_WORKSPACES).getString(0))
        // And the whole thing re-exports identically.
        assertEquals(
            StateCodec.export(a.applier.current(), 1L).encode(),
            StateCodec.export(b.applier.current(), 1L).encode(),
        )
    }

    @Test
    fun CFG_11_settingsOnlyImportKeepsExtrasAlreadyHeld() {
        val p = StateTestSupport.Process(extrasFile = tmp.newFile("so.json"))
        p.extras.write(StateExtras(unknownTop = org.json.JSONObject().put("futureThing", 7)))
        val dec = StateCodec.decode(StateDocument.parse(SettingsCodec.encode(LauncherPrefs()).toString()))
        p.applier.apply(dec.parts, dec.present)
        assertEquals(7, p.extras.read().unknownTop.getInt("futureThing"))
    }

    // ---- NFR-18 backup exclusion --------------------------------------------------------------

    private fun appFile(rel: String): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return File(d!!, "app/src/main/$rel")
    }

    @Test
    fun NFR_18_backupRulesExcludeUsageIndexesAndNotificationContent() {
        val manifest = appFile("AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        for (f in listOf("res/xml/data_extraction_rules.xml", "res/xml/backup_rules.xml")) {
            val x = appFile(f).readText()
            for (n in StateSchema.NEVER_EXPORTED_STORES.filter { it != "liftoff_misc" } + "search_habits") {
                assertTrue("$f must exclude $n", x.contains("$n.xml"))
            }
            assertTrue("$f must exclude the database domain", x.contains("domain=\"database\""))
            assertTrue("$f must exclude snapshots", x.contains("state/snapshots"))
            assertTrue("$f must exclude indexes", x.contains("index"))
            assertTrue("$f must exclude notification content", x.contains("notifications"))
        }
        // data extraction rules cover both cloud backup and device transfer.
        val dx = appFile("res/xml/data_extraction_rules.xml").readText()
        assertTrue(dx.contains("<cloud-backup") && dx.contains("<device-transfer"))
    }
}
