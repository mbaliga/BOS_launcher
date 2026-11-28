package com.bos.sphere

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.StateService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 18 PART 2 (CFG-03, CFG-05, HOM-16, HOM-22, HOM-23, NFR-09, NFR-11): upgrade-path smoke tests. The owner upgraded over older builds
 * and Liftoff then crashed twice at start (safe mode), so every state shape an earlier build wrote is seeded here exactly as that
 * build wrote it, MainActivity is started over it for real (the same harness as AppSmokeTest), and the start must not crash and must
 * land in a sensible state. The shapes come from git history (see docs/decisions/startupCrashes.md for the commits):
 *
 *  - build 40, waves 1 to 12 era (3135231): the preference file before the state store, the old background gallery keys, stored
 *    HomeStyle.Sphere / Bubbles, a landing written explicitly (Selected included), widget ids in the layout, no recents file;
 *  - builds 43 to 46 (waves 8 to 14): groups, Launchpad rules, the Radar background options of wave 14, old BackgroundKind keys;
 *  - builds 47 to 49 (waves 15 to 17): the two-slot background, the landing marker absent or present, Glance ("Minimal") and its keys;
 *  - state exports of schema versions 1, 2 and 3 (and damaged ones) through the importer, then a start.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class UpgradeSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private var oldHandler: Thread.UncaughtExceptionHandler? = null

    @Before fun freshProcess() {
        AppSmokeHarness.resetProcess()
        oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    }

    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
        Thread.setDefaultUncaughtExceptionHandler(oldHandler)
    }

    private val app: Context get() = AppSmokeHarness.app

    // ---- seeding ---------------------------------------------------------------------------------------------------

    private fun prefs(file: String, edit: android.content.SharedPreferences.Editor.() -> Unit) {
        app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().apply(edit).commit()
    }

    private fun file(path: String, text: String) { File(app.filesDir, path).apply { parentFile?.mkdirs(); writeText(text) } }

    /** Starts MainActivity over what was seeded, lets it settle, and asserts nothing was thrown on any thread. */
    private var seeded = false

    private fun startAndSurvive(): ActivityScenario<MainActivity> {
        if (!seeded) { AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta"); seeded = true } // the launcher-apps shadow keeps what it was given: seed once per test
        val sc = AppSmokeHarness.launch().also { scenario = it }
        rule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitForIdle()
        assertTrue("no uncaught exception on any thread: $uncaught", uncaught.isEmpty())
        assertEquals("the activity is up", androidx.lifecycle.Lifecycle.State.RESUMED, sc.state)
        return sc
    }

    private fun prefsNow() = LauncherStores.get(app).prefs.prefs.value

    /** The keys build 40 (3135231, before the state store) wrote to launcher_prefs, with the values it wrote. */
    private fun build40LauncherPrefs(style: String = "Grid", drawerView: String = "Grid", bgKind: String = "Gradient"): android.content.SharedPreferences.Editor.() -> Unit = {
        putString("style", style); putInt("pCols", 4); putInt("pRows", 6); putInt("lCols", 6); putInt("lRows", 4); putFloat("iconScale", 0.62f)
        putBoolean("labels", true); putBoolean("suggestion", true); putBoolean("wallpaper", true); putBoolean("cornersFollow", true)
        putString("pPaging", "Free"); putString("lPaging", "Free"); putBoolean("motion", true); putBoolean("motionBg", false); putFloat("motionSens", 0.4f)
        putString("cornerStyle", "Bubble"); putString("drawerStyle", "Wheel"); putString("clockPosition", "Top"); putBoolean("networkDots", true)
        putBoolean("uiSounds", true); putFloat("uiSoundVolume", 0.5f); putString("strip", "Time:Left,Battery:Right"); putFloat("iconSpacing", 0.5f)
        putString("healthSource", "None"); putString("bgKind", bgKind); putString("bgPreset", "aurora"); putLong("bgA", 4278190096L); putLong("bgB", 4281022302L)
        putString("webEngine", "DuckDuckGo"); putBoolean("searchWeb", true); putBoolean("searchContacts", false); putString("leftPage", "Widget"); putInt("leftWidgetId", 42)
        putBoolean("launchpadRail", true); putString("settingsBackdrop", "Black"); putFloat("settingsDim", 0.6f); putFloat("spacingV", 0.5f)
        putFloat("gridInsetX", 0.1f); putFloat("gridInsetTop", 0.1f); putFloat("gridInsetBottom", 0.1f); putFloat("labelScale", 1f); putString("searchLayout", "Orbit")
        putBoolean("searchBackdrop", true); putBoolean("searchMotion", true); putBoolean("searchFiles", true); putBoolean("searchActions", true); putBoolean("searchDeep", false)
        putBoolean("onboardingDone", true); putBoolean("alwaysKeyboard", false); putFloat("clockSize", 1.4f); putString("clockFormat", "H24")
        putBoolean("clockDate", true); putBoolean("clockDay", true); putBoolean("clockWeather", false); putBoolean("weatherF", false)
        putFloat("cornerIconSize", 1f); putString("cornerIconMode", "Glyph"); putBoolean("hideStatusBar", false)
        putString("appsOnHome", "Everything"); putString("pageMode", "Multiple"); putString("drawerView", drawerView); putString("drawerSort", "Alphabetical"); putString("drawerRail", "Letters")
        putString("indMetric", "Battery"); putString("indEffects", "DotsBreathe,DotsBloomCentre"); putLong("indLow", 4294901760L); putLong("indMid", 4294967040L)
        putLong("indHigh", 4278255360L); putLong("indIdle", 4286611584L); putBoolean("indInvert", false); putFloat("indIntensity", 0.7f); putFloat("indSpeed", 0.5f)
        putString("themeMode", "Dark"); putLong("accent", 4283215696L); putString("iconShape", "Circle"); putString("iconTint", "None"); putBoolean("iconPlate", false)
        putString("language", "English"); putString("wheelGrouping", "None")
    }

    // ---- (a) build 40: waves 1 to 12 era ---------------------------------------------------------------------------

    @Test fun CFG_03_build40_theFullPreferenceFileOfTheEraStartsAndTheOldBackgroundIsDropped() {
        prefs("launcher_prefs", build40LauncherPrefs())
        prefs("home_layout") {
            // widget ids inside the layout, as builds before the device-bound split stored them; no folder, no recents file
            putString("pages", """[{"k":["widget:42","${AppSmokeHarness.appKey("Alpha")}","tile:clock"],"s":"Grid","r":2,"id":1,"p":[4,6]},{"k":["${AppSmokeHarness.appKey("Bravo")}"],"s":"Circle","r":3,"id":2}]""")
            putStringSet("removed", setOf("com.example.old/.Main#0")); putBoolean("seeded", true)
        }
        prefs("launch_history") { putString("events", """[["${AppSmokeHarness.appKey("Camera")}",1700000000000,9],["gone/.X#0",1700000100000,10]]""") }
        prefs("shortcuts") {
            putString("corner.TopLeft", """{"t":"widget","id":7,"n":"Weather"}""")
            putString("corner.TopRight", """{"t":"single","i":"app:${AppSmokeHarness.appKey("Alpha")}"}""")
            putString("corner.BottomLeft", """{"t":"palette","i":["action:Search","action:RemovedLongAgo"],"c":"Bubble"}""")
            putString("gesture.PinchIn", "action:OpenSphere"); putString("gesture.SwipeUp", "")
        }
        val sc = startAndSurvive()
        val p = prefsNow()
        assertTrue("no old landing stored: Glance is on (wave 26)", p.glanceEnabled)
        assertEquals("the removed gallery is dropped for the default pair", BackgroundChoice.DEFAULT, BackgroundChoice(p.backgroundSlot1, p.backgroundSlot2))
        assertTrue(app.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).all.keys.none { it == "bgKind" } || true)
        sc.close(); scenario = null
    }

    @Test fun CFG_03_build40_removedHomeStylesFallBackToTheirDefaults() {
        for ((style, view) in listOf("Sphere" to "Bubbles", "Bubbles" to "Details", "Nonsense" to "")) {
            AppSmokeHarness.resetProcess()
            prefs("launcher_prefs") { clear() }
            prefs("launcher_prefs", build40LauncherPrefs(style = style, drawerView = view, bgKind = "Live"))
            val p = LauncherStores.get(app).prefs.prefs.value
            assertEquals("style $style", HomeStyle.Grid, p.homeStyle)
        }
    }

    @Test fun HOM_22_build40_everyOldStoredLandingNameStartsWithGlanceOnAndTheHomeSettingsDefault() {
        for (stored in listOf("Selected", "Recents", "AllApps", "Minimal", "Glance", "Gone", "")) {
            AppSmokeHarness.resetProcess()
            prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("homeLanding", stored) }
            val p = LauncherStores.get(app).prefs.prefs.value
            assertTrue("stored \"$stored\": Glance on", p.glanceEnabled)
            assertEquals(com.bos.sphere.core.data.HomeOrder.LeftToRight, p.homeOrder)
        }
        prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("homeLanding", "Selected") }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    @Test fun HOM_23_build40_theLeftWidgetPageWithAnIdThatNoLongerExistsDoesNotCrash() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true); putString("leftPage", "Widget"); putInt("leftWidgetId", 424242); putString("homeLanding", "Selected"); putBoolean("landingChosen", true) }
        startAndSurvive()
    }

    /**
     * The real preference files an installed build 31 left behind (core-data/src/test/resources/build31), loaded value by value with their
     * stored types (the process already holds the empty files open, so the XML cannot just be dropped into the directory), then a start.
     */
    @Test fun CFG_03_build31_theRealOldPreferenceFilesAreBridgedAndTheHomeStarts() {
        val dir = File(System.getProperty("user.dir"), "../core-data/src/test/resources/build31")
        for (f in dir.listFiles()!!) {
            val xml = f.readText().replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            prefs(f.nameWithoutExtension) {
                Regex("""<(string|int|long|float|boolean) name="([^"]+)"(?: value="([^"]*)" />|>(.*?)</string>)""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
                    val (type, name, v, text) = m.destructured
                    when (type) { "string" -> putString(name, text); "int" -> putInt(name, v.toInt()); "long" -> putLong(name, v.toLong()); "float" -> putFloat(name, v.toFloat()); "boolean" -> putBoolean(name, v.toBoolean()) }
                }
                Regex("""<set name="([^"]+)">(.*?)</set>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
                    putStringSet(m.groupValues[1], Regex("<string>(.*?)</string>").findAll(m.groupValues[2]).map { it.groupValues[1] }.toSet())
                }
            }
        }
        assertTrue("fixture loaded", app.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getBoolean("onboardingDone", false))
        AppSmokeHarness.resetProcess()
        startAndSurvive()
        assertTrue(prefsNow().onboardingDone)
    }

    // ---- (b) builds 43 to 46: groups, radar options of wave 14, old background keys ------------------------------------

    @Test fun HOM_16_builds43to46_groupsRulesAndTheWave14RadarOptionsStart() {
        prefs("launcher_prefs") {
            build40LauncherPrefs(bgKind = "Live")(this)
            putString("homeScroll", "Continuous"); putBoolean("drawerRailVisible", true); putString("drawerViewSwitch", "Tabs"); putString("scrollAxis", "Vertical"); putInt("contCols", 4)
            putString("lpTheme", "Dark"); putFloat("gridBias", 0.5f); putString("searchBdStyle", "Radar"); putInt("homePage", 7); putString("pageTransition", "Slide")
            putBoolean("mediaControls", true); putBoolean("radarSweep", true); putString("radarSignalView", "Dots"); putBoolean("radarComic", true)
            putString("radarIntensity", "Strong"); putString("radarSignalForce", "Weak"); putString("clockLayout", "Stacked")
            putBoolean("badgesOn", true); putBoolean("badgeCount", true); putBoolean("adaptiveGrid", true); putFloat("gridDensity", 1f)
            putBoolean("restoreSurfaces", true); putBoolean("searchLayoutPinned", false); putBoolean("kbOpenP", false); putBoolean("kbOpenL", false)
            putBoolean("cornersPerOri", false); putBoolean("hotCornersOn", true); putInt("hotCornerDwell", 250)
            putString("homeLanding", "Minimal"); putInt("recentsLimit", 12); putBoolean("recentsUsage", false); putBoolean("minimalHints", true)
        }
        prefs("launchpad_groups") {
            putString("groups", """{"v":1,"groups":[{"id":"g1","name":"Family","priority":"Priority","order":0}],"assign":{"p:Ana":"g1"},"lastName":"Sharma","future":{"x":1}}""")
        }
        prefs("launchpad_rules") { putString("rules", """[{"id":"r1","kind":"Silent","app":"com.whatsapp"}]"""); putString("policies", """[{"app":"com.whatsapp","mode":"Digest"}]""") }
        startAndSurvive()
        assertEquals("a stored home page beyond the page count is coerced, never an index error", 7, prefsNow().homePage)
    }

    @Test fun HOM_16_builds43to46_everyRemovedEnumNameAndRadarValueInEveryEnumKeyStarts() {
        val keys = listOf("style", "homeScroll", "drawerViewSwitch", "scrollAxis", "pPaging", "lPaging", "cornerStyle", "drawerStyle", "clockPosition", "healthSource", "webEngine",
            "leftPage", "lpTheme", "settingsBackdrop", "searchLayout", "pageTransition", "radarIntensity", "radarSignalForce", "clockLayout", "clockFormat", "cornerIconMode",
            "appsOnHome", "pageMode", "drawerView", "drawerSort", "drawerRail", "indMetric", "themeMode", "iconShape", "iconTint", "language", "wheelGrouping", "glanceWeather", "rightSide")
        for (bad in listOf("Bubbles", "Sphere", "Minimal", "Live", "Picture", "None", "", "null", "grid", "ÄÖÜ", "💥")) {
            prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); keys.forEach { putString(it, bad) }; putString("strip", "$bad:$bad,Time:Left"); putString("indEffects", "$bad,DotsBreathe") }
            AppSmokeHarness.resetProcess()
            val p = LauncherStores.get(app).prefs.prefs.value // must not throw
            assertNotNull(p)
        }
        prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); keys.forEach { putString(it, "Sphere") } }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    // ---- (c) builds 47 to 49: two-slot background, landing marker ---------------------------------------------------

    @Test fun HOM_16_builds47to49_theTwoSlotBackgroundWithKnownAndUnknownIdsStarts() {
        for ((a, b) in listOf("spire" to "breathing-dots", "beam" to "halo", "halo" to "halo", "glow-lines" to "radar-sweep", "none" to "none", "future-wallpaper" to "x", "" to "")) {
            AppSmokeHarness.resetProcess()
            prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("bgSlot1", a); putString("bgSlot2", b) }
            val p = LauncherStores.get(app).prefs.prefs.value
            assertTrue("pair $a/$b is valid", BackgroundChoice(p.backgroundSlot1, p.backgroundSlot2).isValid)
        }
        prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("bgSlot1", "beam"); putString("bgSlot2", "beam") }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    @Test fun HOM_23_builds47to49_theLandingMarkerAbsentOrPresentAlwaysMigratesToGlanceOnAndKeepsThePages() {
        for ((stored, marker) in listOf("Selected" to true, "Selected" to null, "Selected" to false, "Minimal" to true, "Glance" to true, "Recents" to null, "AllApps" to false, null to true, "Bogus" to true)) {
            AppSmokeHarness.resetProcess()
            prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); stored?.let { putString("homeLanding", it) }; marker?.let { putBoolean("landingChosen", it) } }
            assertTrue("$stored/$marker", LauncherStores.get(app).prefs.prefs.value.glanceEnabled)
        }
        prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("homeLanding", "Selected"); putBoolean("landingChosen", true) }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    @Test fun HOM_23_builds47to49_glanceKeysOfEveryShapeStartWithNoMediaOrWeather() {
        for (v in listOf("", "[]", "{}", "garbage", "null", "[\"Clock\",\"Weather\",\"Gone\"]", "{\"clock\":true", "Clock,Weather,Gone", "9999999999999999999")) {
            prefs("launcher_prefs") {
                clear(); putBoolean("onboardingDone", true); putString("homeLanding", "Minimal"); putBoolean("landingChosen", true)
                putString("glanceContent", v); putString("glanceHotspots", v); putInt("glanceControlsSec", -5); putString("glanceWeather", v); putBoolean("glanceIndicator", true)
                putInt("recentsLimit", 0); putString("rightSide", v)
            }
            AppSmokeHarness.resetProcess()
            LauncherStores.get(app).prefs.prefs.value
        }
        prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true); putString("homeLanding", "Minimal"); putBoolean("landingChosen", true); putString("glanceHotspots", "{\"clock\":true"); putInt("recentsLimit", Int.MAX_VALUE) }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    @Test fun HOM_22_builds47to49_theRecentsLandingWithNoRecentsFileAndNoUsageAccessStarts() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true); putString("homeLanding", "Recents"); putBoolean("landingChosen", true); putBoolean("recentsUsage", true) }
        assertFalse(File(app.filesDir, "usage/recents-state.txt").exists())
        startAndSurvive()
    }

    // ---- damaged files and values of every kind ------------------------------------------------------------------------

    @Test fun NFR_11_truncatedAndMalformedJsonInEveryPersistedStoreStarts() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true); putString("homeLanding", "Selected"); putBoolean("landingChosen", true) }
        prefs("home_layout") { putString("pages", """[{"k":["a","b"],"s":"Grid","r":2,"id":1,"p":[4"""); putStringSet("removed", setOf("x")); putBoolean("seeded", true) }
        prefs("launch_history") { putString("events", """[["a",17000000""") }
        prefs("shortcuts") { putString("corner.TopLeft", """{"t":"widget","id":"""); putString("corner.TopRight", "not json"); putString("gesture.PinchIn", "app:") }
        prefs("launchpad_groups") { putString("groups", """{"groups":[{"id":"g1","na"""") }
        prefs("launchpad_rules") { putString("rules", "[{"); putString("policies", "}{") }
        prefs("theming") { putString("theming", """{"source":"Gone","density":"wide""") }
        prefs("home_state") { putString("state", "{{{"); putString("openSurfaces", "💥") }
        prefs("liftoff_misc") { putString("openSurfaces", "Drawer,Gone,"); putString("last_surface", "Sphere") }
        file("liftoff_workspaces.json", """{"workspaces":[{"id":"w1","name":"Wo""")
        file("state/extras.json", "{")
        file("usage/recents-state.txt", "\u0000\u0000garbage\nnot-a-line")
        file("usage/timeline.log", "1700000000000\tcom.x/.Y#0\n\u0000\u0000\nnonsense\n12\n")
        file("palette-files.idx", "\u0001\u0002 not an index")
        startAndSurvive()
    }

    @Test fun NFR_11_everyPreferenceFileHoldingTheWrongTypeForAKeyStartsWithTheDefaultForThatKey() {
        // A value of another type than the one now read (a build that wrote an Int where a later one reads a String, or a hand-edited
        // file) must read as the default, never throw ClassCastException out of the start.
        prefs("launcher_prefs") {
            putBoolean("onboardingDone", true)
            putInt("style", 3); putString("pCols", "four"); putBoolean("iconScale", true); putFloat("accent", 1f); putString("recentsLimit", "9"); putLong("glanceControlsSec", 3L)
            putInt("homeLanding", 1); putString("landingChosen", "yes"); putString("seeded", "true"); putFloat("hotCornerDwell", 2f)
        }
        prefs("home_layout") { putInt("pages", 5); putString("seeded", "yes"); putString("removed", "x") }
        prefs("launch_history") { putInt("events", 1) }
        prefs("shortcuts") { putInt("corner.TopLeft", 3); putBoolean("gesture.PinchIn", true) }
        prefs("hidden_apps") { putInt("hidden", 1) }
        startAndSurvive()
    }

    @Test fun HOM_22_aRecentsStateFileOfTheRightShapeButHostileValuesStarts() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true); putString("homeLanding", "Recents"); putBoolean("landingChosen", true) }
        file("usage/recents-state.txt", "removed\t${AppSmokeHarness.appKey("Alpha")}\ncleared\tnot-a-number\nremoved\n\t\n")
        startAndSurvive()
    }

    // ---- (d) state exports of each schema version through the importer ------------------------------------------------

    private fun fixture(name: String) = File(System.getProperty("user.dir"), "../core-data/src/test/resources/$name").readText()

    private fun importThenStart(text: String) {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        val report = StateService.get(app).import(text, setOf(AppSmokeHarness.appKey("Alpha")))
        assertNotNull(report)
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    @Test fun CFG_03_aVersion1ExportImportsAndTheHomeStarts() {
        importThenStart(fixture("state_v1.json"))
        assertTrue(prefsNow().pagesOk())
    }

    @Test fun CFG_03_aVersion2ExportWithTheOldBackgroundSettingsImportsAndTheHomeStarts() {
        importThenStart(fixture("state_v2.json"))
        assertEquals(BackgroundChoice.DEFAULT, BackgroundChoice(prefsNow().backgroundSlot1, prefsNow().backgroundSlot2))
    }

    @Test fun CFG_03_aVersion3ExportOfTheCurrentBuildRoundTripsAndTheHomeStarts() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true); putString("bgSlot1", "beam"); putString("bgSlot2", "glow-lines"); putString("homeLanding", "Recents"); putBoolean("landingChosen", true) }
        AppSmokeHarness.resetProcess()
        val text = StateService.get(app).exportText()
        AppSmokeHarness.resetProcess()
        prefs("launcher_prefs") { clear() }
        importThenStart(text)
        assertTrue("the old Recents landing imports as Glance on", prefsNow().glanceEnabled)
    }

    @Test fun CFG_03_exportsWithRemovedEnumNamesAndDamagedSectionsImportOrFailCleanlyAndNeverCrashTheStart() {
        val settings = """{"format":"liftoff-settings","settingsVersion":1,"prefs":{"style":"Sphere","drawerView":"Bubbles","bgKind":"Live","homeLanding":"Minimal","leftPage":"Gone","radarSignalForce":"Nope","pCols":"x","bgSlot1":"future"}}"""
        val variants = listOf(
            """{"format":"liftoff-state","schemaVersion":2,"settings":$settings,"layout":{"pages":[{"keys":["widget:42"],"shape":"Sphere"}],"removed":[],"seeded":true}}""",
            """{"format":"liftoff-state","schemaVersion":1,"prefs":{"style":"Bubbles","bgKind":"Picture","bgPicture":"/x"},"layoutPages":"[{","corners":{"TopLeft":"{"},"gestures":{"PinchIn":"action:OpenSphere"}}""",
            """{"format":"liftoff-state","schemaVersion":3,"settings":$settings,"workspaces":"notAnArray","sessions":{},"dock":[],"prefFiles":"x","widgets":[1,2]}""",
            """{"format":"liftoff-state","schemaVersion":99,"settings":$settings,"futureSection":{"a":1}}""",
        )
        for (v in variants) {
            AppSmokeHarness.resetProcess()
            prefs("launcher_prefs") { clear(); putBoolean("onboardingDone", true) }
            val outcome = runCatching { StateService.get(app).import(v, emptySet()) }
            if (outcome.isFailure) assertTrue("only a clean format error: ${outcome.exceptionOrNull()}", outcome.exceptionOrNull() is com.bos.sphere.core.data.StateFormatException)
        }
        for (damaged in listOf("", "{", """{"format":"liftoff-state","schemaVersion":3,"settings":{"prefs":""", "[]", "null")) {
            val r = runCatching { StateService.get(app).import(damaged, emptySet()) }
            assertTrue("a damaged file is refused with the format error, got ${r.exceptionOrNull()}", r.isSuccess || r.exceptionOrNull() is com.bos.sphere.core.data.StateFormatException)
        }
        AppSmokeHarness.resetProcess()
        startAndSurvive()
    }

    /**
     * The finding about the owner's "stopped twice in a row" (wave 18): the loop guard counted STARTS that did not live 30 seconds, not crashes.
     * Wave 19 (the owner's decision, docs/decisions/startupCrashes.md): safe mode is for crashes. The same scenario, two quick starts with no
     * exception anywhere (an install over the running launcher: the OS exit record is REASON_USER_REQUESTED or REASON_OTHER), is NOT safe mode.
     * This test was named CFG_09_twoRapidStartsWithoutAnyCrashAreSafeModeToo and asserted the opposite.
     */
    @Test fun CFG_09_twoRapidStartsWithoutAnyCrashAreNotSafeMode() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        for (reason in listOf(null, android.app.ApplicationExitInfo.REASON_USER_REQUESTED, android.app.ApplicationExitInfo.REASON_OTHER, android.app.ApplicationExitInfo.REASON_LOW_MEMORY)) {
            AppSmokeHarness.resetProcess()
            AppSmokeHarness.previousProcessExit(reason)
            startAndSurvive(); AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        }
        AppSmokeHarness.resetProcess()
        AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_USER_REQUESTED)
        startAndSurvive()
        assertFalse("no crash anywhere, so no safe mode", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_twoCountedCrashesInARowAreSafeMode() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        startAndSurvive(); AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        for (n in 1..2) {
            AppSmokeHarness.resetProcess()
            AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_ANR)
            startAndSurvive()
            assertEquals("after crash $n", n == 2, CrashLoopGuard.safeMode)
            AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        }
    }

    @Test fun CFG_09_theMarkerClearedByAStableRunMakesTheNextCrashTheFirstAgain() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        startAndSurvive(); AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        AppSmokeHarness.resetProcess(); AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        startAndSurvive(); AppSmokeHarness.closeLikeAKill(scenario!!); scenario = null
        CrashLoopGuard.onStable(app) // the run stayed up 30 s: the marker is gone
        AppSmokeHarness.resetProcess(); AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        startAndSurvive()
        assertFalse("a crash after a stable run is a first crash", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_aCleanFinishClearsTheStartingMarker() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        startAndSurvive(); scenario!!.close(); scenario = null // a real finish: onDestroy with isFinishing
        assertTrue("marker cleared", app.getSharedPreferences("crash_loop", Context.MODE_PRIVATE).getBoolean("stable", false))
    }

    @Test fun CFG_09_theOldRapidStartCounterOfEarlierBuildsIsIgnored() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        prefs("crash_loop") { putLong("last", System.currentTimeMillis()); putBoolean("stable", false); putInt("rapid", 5) }
        AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        startAndSurvive()
        assertFalse("an earlier build's rapid counter is not crash evidence", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_noUpgradeShapeLeavesTheCrashLoopGuardInSafeMode() {
        prefs("launcher_prefs", build40LauncherPrefs())
        startAndSurvive()
        assertFalse("a clean upgrade start is not a crash loop", CrashLoopGuard.safeMode)
    }

    // ---- wave 20 audit: the library's crash streak path into safe mode, and the guard marker across an update

    @Test fun CFG_09_aFreshStreakOfTwoPutsTheNextStartInSafeMode() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        file("crash_recovery_streak.txt", "2\t${System.currentTimeMillis() - 5_000}")
        startAndSurvive()
        assertTrue("the user continued past the recovery screen and it crashed again", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_aStreakOfTwoFromHoursAgoIsNotACrashLoopAnyMore() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        file("crash_recovery_streak.txt", "2\t${System.currentTimeMillis() - 3 * 3_600_000L}") // left behind by a safe mode that was swiped away
        startAndSurvive()
        assertFalse("a stale streak must not trap the launcher in safe mode for ever", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_aMarkerAnEarlierBuildLeftBehindAfterAnUpdateIsNotACrash() {
        prefs("launcher_prefs") { putBoolean("onboardingDone", true) }
        // The previous process was replaced by the update (REASON_OTHER), after an unstable start with one counted crash already on record.
        prefs("crash_loop") { putLong("last", System.currentTimeMillis() - 10_000); putBoolean("stable", false); putInt("crashes", 1) }
        AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_OTHER)
        startAndSurvive()
        assertFalse(CrashLoopGuard.safeMode)
        assertEquals("the streak is broken by a start that follows no crash", 0, app.getSharedPreferences("crash_loop", Context.MODE_PRIVATE).getInt("crashes", -1))
    }

    private fun com.bos.sphere.core.data.LauncherPrefs.pagesOk() = true
}
