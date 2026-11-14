package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Modifier

class StateCodecTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun roundTrip(parts: StateParts): StateParts =
        StateCodec.decode(StateDocument.parse(StateCodec.export(parts, 1L).encode())).parts

    @Test
    fun CFG_01_everySettingIsCarried() {
        // Reflection over LauncherPrefs: change each field on its own, push it through export and import, and require
        // the change to arrive. A field added later without a codec path fails here.
        val device = setOf("leftWidgetId", "backgroundPicturePath", "onboardingDone")
        val ctor = LauncherPrefs::class.java.constructors.filter { !it.isSynthetic }.maxByOrNull { it.parameterCount }!!
        // Constructor properties come first among the fields, in declaration order.
        val fields = LauncherPrefs::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.take(ctor.parameterCount)
        fields.forEach { it.isAccessible = true }
        val base = LauncherPrefs()
        val baseVals = fields.map { it.get(base) }
        assertEquals(ctor.parameterTypes.toList(), fields.map { it.type })
        val missing = ArrayList<String>()
        fields.forEachIndexed { i, f ->
            if (f.name in device) return@forEachIndexed
            val carried = variants(baseVals[i], f.type).any { v ->
                val args = baseVals.toMutableList().also { it[i] = v }.toTypedArray()
                val p = ctor.newInstance(*args) as LauncherPrefs
                val back = roundTrip(StateParts(prefs = p)).prefs
                f.get(back) != baseVals[i]
            }
            if (!carried) missing += f.name
        }
        assertEquals("settings not carried by export/import: $missing", emptyList<String>(), missing)
    }

    private fun variants(v: Any?, type: Class<*>): List<Any?> = when {
        v is Boolean -> listOf(!v)
        v is Int -> listOf(v + 1, v + 2, v - 1)
        v is Long -> listOf(v xor 0x123456L)
        v is Float -> listOf(v + 0.1f, v - 0.1f, v * 1.3f, v * 0.7f, v + 3f)
        v is String -> listOf(v + "x")
        v is Enum<*> -> type.enumConstants.filter { it != v }
        v is GridSpec -> listOf(GridSpec(v.columns + 1, v.rows + 1), GridSpec(v.columns - 1, v.rows - 1))
        v is Map<*, *> -> listOf(emptyMap<Any, Any>(), mapOf(StripItem.entries.first() to StripZone.entries.last()))
        v is Set<*> -> listOf(emptySet<Any>(), IndicatorEffect.entries.toSet())
        else -> error("no test variant for ${type.name}")
    }

    @Test
    fun CFG_01_corners_gestures_launchpad_andLayoutRoundTrip() {
        val parts = StateParts(
            layout = HomeLayout(
                pages = listOf(HomePage(listOf(HomeTiles.CLOCK, "a/.A#0"), PageLayout(PageShape.Circle, rings = 3), id = 4)),
                removed = setOf("z/.Z#0"), seeded = true,
            ),
            corners = DefaultCorners + (Corner.TopLeft to CornerSlot.Palette("P", listOf(CornerItem.App("a/.A#0"), CornerItem.Action(CornerActionId.Camera)))),
            gestures = HomeGesture.entries.associateWith { DefaultGestures[it] } + (HomeGesture.DoubleTap to null),
            launchpadRules = listOf(Rule(id = "q", packageName = "p", action = RuleAction.Silent)),
            launchpadPrefs = LaunchpadPrefs(GroupMode.Flat, mapOf("p" to GroupMode.Sender)),
            launchpadGestures = LaunchpadGestures.defaults + (LaunchpadGesture.PinchIn to LaunchpadAction.ShowAll),
        )
        val back = roundTrip(parts)
        assertEquals(parts.layout, back.layout)
        assertEquals(parts.corners, back.corners)
        assertEquals(parts.gestures, back.gestures)
        assertEquals(parts.launchpadRules, back.launchpadRules)
        assertEquals(parts.launchpadPrefs, back.launchpadPrefs)
        assertEquals(parts.launchpadGestures, back.launchpadGestures)
    }

    @Test
    fun CFG_01_exportCarriesNoPersonalContent() {
        // Feed the bridge every store a device has, including usage history and weather location, and check that the
        // file holds none of it (CFG-01, NFR-18).
        val files = StateTestSupport.build31Files()
        val text = StateCodec.export(Build31Bridge.fromPrefFiles(files), 1L).encode()
        assertFalse(text.contains("SENTINEL_HISTORY_KEY"))
        assertFalse(text.contains("SENTINEL_PLACE"))
        assertFalse(text.contains("51.5074"))
        assertTrue(Build31Bridge.FILES.none { it in StateSchema.NEVER_EXPORTED_STORES })
        // Only known sections are written.
        val doc = StateDocument.parse(text)
        assertTrue(StateSchema.KNOWN_TOP.containsAll(doc.json.keys().asSequence().toList()))
        // Device-bound values never leave the device.
        val prefs = doc.json.getJSONObject("settings").getJSONObject("prefs")
        StateSchema.DEVICE_BOUND_PREF_KEYS.forEach { assertFalse("$it exported", prefs.has(it)) }
        assertFalse(text.contains("background_1.img"))
    }

    @Test
    fun CFG_02_widgetsAreNotCarriedAndAreListedForRebinding() {
        val parts = StateParts(
            corners = DefaultCorners + (Corner.TopRight to CornerSlot.Widget(77, "Weather")),
            prefs = LauncherPrefs(leftPage = LeftPage.Widget, leftWidgetId = 9),
        )
        val doc = StateCodec.export(parts, 1L)
        assertFalse(doc.encode().contains("77"))
        val back = StateCodec.decode(doc).parts
        assertEquals(CornerSlot.Empty, back.corners[Corner.TopRight])
        assertEquals(setOf("corner.TopRight", "leftPage"), back.extras.widgets.map { it.slot }.toSet())
        assertEquals(-1, back.prefs.leftWidgetId)
    }

    @Test
    fun CFG_02_missingAppsStayAsPlaceholdersThatResolveWhenInstalled() {
        val layout = HomeLayout(listOf(HomePage(listOf(HomeTiles.CLOCK, "have/.A#0", "gone/.B#0"))))
        val missing = StateCodec.unresolvedApps(layout, installed = setOf("have/.A#0"))
        assertEquals(setOf("gone/.B#0"), missing)
        // Kept in the stored layout through export and import, so installing the app puts it back where it was.
        val back = roundTrip(StateParts(layout = layout)).layout
        assertEquals(layout.pages.single().keys, back.pages.single().keys)
        assertTrue(StateCodec.unresolvedApps(back, setOf("have/.A#0", "gone/.B#0")).isEmpty())
    }

    @Test
    fun CFG_03_unknownSettingsAndOpaqueSectionsSurviveTheStoreRoundTrip() {
        val ws = JSONArray().put(JSONObject().put("name", "Work").put("future", "x"))
        val parts = StateParts(
            extras = StateExtras(
                opaque = mapOf(StateSchema.K_WORKSPACES to ws, StateSchema.K_ALIASES to JSONArray().put("a")),
                unknownTop = JSONObject().put("zzz", 1),
                unknownPrefs = JSONObject().put("flavourNext", "yes"),
                keyboardShortcuts = "Search=-",
            ),
        )
        val back = roundTrip(parts)
        assertEquals(ws.toString(), back.extras.opaque[StateSchema.K_WORKSPACES].toString())
        assertEquals(1, back.extras.unknownTop.getInt("zzz"))
        assertEquals("yes", back.extras.unknownPrefs.getString("flavourNext"))
        assertEquals("Search=-", back.extras.keyboardShortcuts)
    }

    @Test
    fun CFG_03_exportedFileIsCurrentAndVersioned() {
        val j = StateCodec.export(StateParts(), 99L, "liftoff").json
        assertEquals(StateSchema.FORMAT, j.getString("format"))
        assertEquals(StateSchema.CURRENT, j.getInt("schemaVersion"))
        assertEquals(99L, j.getLong("exportedAt"))
        for (s in listOf("layout", "settings", "shortcuts", "launchpad", "paletteAliases", "workspaces", "sessions", "widgets")) assertTrue(s, j.has(s))
    }

    @Test
    fun CFG_04_applyThenExportIsStable_andImportKeepsDeviceBoundValues() {
        val src = StateTestSupport.Process(extrasFile = tmp.newFile("a.json"))
        val dst = StateTestSupport.Process(extrasFile = tmp.newFile("b.json"))
        // The destination has its own widget and picture.
        dst.prefs.update { it.copy(leftWidgetId = 5, backgroundPicturePath = "/mine.img") }
        dst.shortcuts.setCorner(Corner.TopRight, CornerSlot.Widget(3, "Mine"))
        val files = StateTestSupport.build31Files()
        val parts = Build31Bridge.fromPrefFiles(files)
        src.applier.apply(parts)
        val doc = StateCodec.export(src.applier.current(), 1L)
        val dec = StateCodec.decode(doc)
        dst.applier.apply(dec.parts, dec.present)
        assertEquals(HomeStyle.Psp, dst.prefs.prefs.value.homeStyle)
        assertEquals(5, dst.prefs.prefs.value.leftWidgetId)
        assertEquals("/mine.img", dst.prefs.prefs.value.backgroundPicturePath)
        assertTrue(dst.prefs.prefs.value.onboardingDone)
        assertEquals(CornerSlot.Widget(3, "Mine"), dst.shortcuts.corners.value[Corner.TopRight])
        assertEquals(src.layout.layout.value.pages.map { it.keys }, dst.layout.layout.value.pages.map { it.keys })
        assertEquals(1, dst.launchpad.rules.size)
        assertEquals(GroupMode.App, dst.launchpad.mode)
        assertEquals(GroupMode.Sender, dst.launchpad.per["com.example.chat"])
    }

    @Test
    fun CFG_11_settingsOnlyImportLeavesLayoutAlone() {
        val dst = StateTestSupport.Process(extrasFile = tmp.newFile("c.json"))
        dst.layout.setPages(listOf(HomePage(listOf("keep/.K#0"))))
        val settings = StateCodec.export(StateParts(prefs = LauncherPrefs(homeStyle = HomeStyle.Dsi)), 1L).settingsDocument()!!
        val dec = StateCodec.decode(StateDocument.parse(settings.toString()))
        dst.applier.apply(dec.parts, dec.present)
        assertEquals(HomeStyle.Dsi, dst.prefs.prefs.value.homeStyle)
        assertEquals(listOf("keep/.K#0"), dst.layout.layout.value.pages.single().keys)
        assertNotEquals(emptyList<Rule>(), listOf(1))
    }
}
