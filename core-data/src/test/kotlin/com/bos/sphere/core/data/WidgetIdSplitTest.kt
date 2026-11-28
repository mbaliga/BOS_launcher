package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFR-18: widget ids held in home arrangement items never reach the backed-up files or an export; they become re-add markers. */
class WidgetIdSplitTest {
    private val raw = "P\t1\tGrid\t2\t-\t-\twidget:12\u001eapp:x\u001ewidget:stack-a\nI\twidget%3A12\tWidget\tClock\tcom.x/.W\t0\nI\twidget:stack-a\tWidget\tS\t1;widget:12,widget:130\t0\nD\twidget:7\n"

    @Test fun NFR_18_everyWidgetIdBecomesANegativeMarkerAndStackNamesAreLeftAlone() {
        val c = WidgetIdSplit.sanitize(raw)
        assertFalse(WidgetIdSplit.hasIds(c))
        assertTrue(c.contains("widget:-13\u001eapp:x\u001ewidget:stack-a"))
        assertTrue(c.contains("1;widget:-13,widget:-131"))
        assertTrue(c.contains("D\twidget:-8"))
        assertEquals("sanitising twice changes nothing", c, WidgetIdSplit.sanitize(c))
        assertTrue(WidgetIdSplit.isMarker("widget:-13")); assertFalse(WidgetIdSplit.isMarker("widget:13"))
    }

    @Test fun NFR_18_theBackedUpFileHoldsMarkersAndTheDeviceFileHoldsTheRealValueAndASecondStoreReadsIt() {
        val main = MapSharedPreferences(); val device = MapSharedPreferences()
        WidgetIdSplit.putString(main, device, "home_state", "state:a", raw)
        assertFalse(WidgetIdSplit.hasIds(main.getString("state:a", "")!!))
        assertEquals(raw, device.getString("home_state:state:a", null))
        assertEquals(raw, WidgetIdSplit.getString(main, device, "home_state", "state:a"))
        // Same for a string set.
        val set = setOf("widget:5", "com.a/.A#0")
        WidgetIdSplit.putStringSet(main, device, "home_layout", "removed", set)
        assertEquals(setOf("widget:-6", "com.a/.A#0"), main.getStringSet("removed", null))
        assertEquals(set, WidgetIdSplit.getStringSet(main, device, "home_layout", "removed"))
    }

    @Test fun NFR_18_aRestoreThatBringsBackOnlyTheMainFileGivesMarkersSoHom07RebindsThem() {
        val main = MapSharedPreferences(); val device = MapSharedPreferences()
        WidgetIdSplit.putString(main, device, "home_extras", "extras", raw)
        val freshDevice = MapSharedPreferences()
        val back = WidgetIdSplit.getString(main, freshDevice, "home_extras", "extras")!!
        assertFalse(WidgetIdSplit.hasIds(back)); assertTrue(back.contains("widget:-13"))
    }

    @Test fun NFR_18_aMainValueChangedByAnImportWinsOverAStaleDeviceCopy() {
        val main = MapSharedPreferences(); val device = MapSharedPreferences()
        WidgetIdSplit.putString(main, device, "home_state", "state:a", raw)
        main.edit().putString("state:a", "P\t9\tGrid\t2\t-\t-\tapp:imported\n").apply()
        assertEquals("P\t9\tGrid\t2\t-\t-\tapp:imported\n", WidgetIdSplit.getString(main, device, "home_state", "state:a"))
    }

    @Test fun NFR_18_aValueAnOlderBuildStoredWithRealIdsIsMovedToTheDeviceFileOnFirstRead() {
        val main = MapSharedPreferences(); val device = MapSharedPreferences()
        main.edit().putString("extras", raw).putStringSet("removed", setOf("widget:3")).apply()
        assertEquals(raw, WidgetIdSplit.getString(main, device, "home_extras", "extras"))
        assertFalse("no ids left in the backed-up file", WidgetIdSplit.hasIds(main.getString("extras", "")!!))
        assertEquals(raw, device.getString("home_extras:extras", null))
        assertEquals(setOf("widget:3"), WidgetIdSplit.getStringSet(main, device, "home_layout", "removed"))
        assertEquals(setOf("widget:-4"), main.getStringSet("removed", null))
        assertEquals("a second read is stable", raw, WidgetIdSplit.getString(main, device, "home_extras", "extras"))
    }

    @Test fun NFR_18_theLayoutStoreKeepsRealIdsForTheLiveHomeAndOnlyMarkersInTheBackedUpFile() {
        val main = MapSharedPreferences(); val device = MapSharedPreferences()
        val store = HomeLayoutStore(main, device)
        store.replace(HomeLayout(pages = listOf(HomePage(keys = listOf("widget:21", "a/.A#0"), id = 1L)), removed = setOf("widget:22"), seeded = true))
        assertFalse(WidgetIdSplit.hasIds(main.getString("pages", "")!!))
        assertFalse((main.getStringSet("removed", null) ?: emptySet<String>()).any { WidgetIdSplit.hasIds(it) })
        val again = HomeLayoutStore(main, device).layout.value
        assertEquals(listOf("widget:21", "a/.A#0"), again.pages.first().keys)
        assertEquals(setOf("widget:22"), again.removed)
        val restored = HomeLayoutStore(main, MapSharedPreferences()).layout.value
        assertEquals(listOf("widget:-22", "a/.A#0"), restored.pages.first().keys)
    }

    @Test fun NFR_18_theExportCarriesNoWidgetIdFromTheLayoutOrFromTheArrangementPreferenceFiles() {
        val layout = HomeLayout(pages = listOf(HomePage(keys = listOf("widget:21", "a/.A#0"), id = 1L)), removed = setOf("widget:22"), seeded = true)
        val doc = StateCodec.export(StateParts(layout = layout), 0L, "test")
        assertFalse("export text: ${doc.encode()}", Regex("widget:\\d").containsMatchIn(doc.encode()))
        val decoded = StateCodec.decode(StateDocument.parse(doc.encode()))
        assertEquals(listOf("widget:-22", "a/.A#0"), decoded.parts.layout.pages.first().keys)
        // An older file that still holds an id is cleaned on import as well.
        val old = JSONObject(doc.encode()); old.getJSONObject("layout").put("pages", org.json.JSONArray(HomeLayoutCodec.encode(layout.pages)))
        assertEquals(listOf("widget:-22", "a/.A#0"), StateCodec.decode(StateDocument.parse(old.toString())).parts.layout.pages.first().keys)

        val files = mapOf("home_state" to MapSharedPreferences().also { it.edit().putString("state:compact-compact", raw).putBoolean("flag", true).apply() },
            "home_extras" to MapSharedPreferences().also { it.edit().putString("extras", raw).apply() },
            "home_tags" to MapSharedPreferences().also { it.edit().putString("t", "widget:5 is a tag text").apply() })
        val src = PrefFilesSource({ files.getValue(it) }, listOf("home_state", "home_extras", "home_tags"))
        val out = src.readObjects().getValue(StateSchema.K_PREF_FILES).toString()
        assertFalse(WidgetIdSplit.hasIds(out.replace("widget:5 is a tag text", "")))
        assertTrue("files that are not home arrangements are untouched", out.contains("widget:5 is a tag text"))
        // Import of a value with ids writes markers.
        val target = mapOf("home_state" to MapSharedPreferences())
        val incoming = JSONObject().put("home_state", JSONObject().put("state:x", JSONObject().put("t", "s").put("v", raw)))
        PrefFilesSource({ target.getValue(it) }, listOf("home_state")).write(emptyMap(), mapOf(StateSchema.K_PREF_FILES to incoming), setOf(StateSchema.K_PREF_FILES))
        val stored = target.getValue("home_state").getString("state:x", null)
        assertNotNull(stored); assertFalse(WidgetIdSplit.hasIds(stored!!))
    }
}
