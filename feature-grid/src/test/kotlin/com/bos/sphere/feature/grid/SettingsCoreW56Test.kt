package com.bos.sphere.feature.grid

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 56 (settingsCore): the pure rules of search synonyms and pane rows (CFG-28), slider defaults (CFG-29), reset with undo (CFG-30), disabled rows and section ids (CFG-31), Finish setup (CFG-32). */
class SettingsCoreW56Test {
    private val g = Glyphs.Search

    // ---- CFG-28 search ------------------------------------------------------------------------------------------------------------

    private fun cats(): List<SettingCategory> = listOf(
        SettingCategory("Look", g, listOf(
            SettingItem.Choice("Light / dark", g, listOf("Dark", "Light"), "Dark", { it }) {}.keywords("night", "amoled", "theme"),
            SettingItem.Toggle("Quiet mode", g, false, "Also hides the night clock") {},
            SettingItem.Toggle("Interface sounds", g, true) {}.keywords("click", "tones", "vibration"),
            SettingItem.Pane("Export, import and restore", g, "One file") {}.paneEntries(
                PaneEntry("backup.snapshot", "Take a snapshot now", listOf("restore point", "undo")),
                PaneEntry("backup.import", "Import setup…", listOf("load", "file")),
            ),
        )),
    )

    @Test fun CFG_28_aSynonymFindsTheRowItBelongsTo() {
        val idx = SettingsIndex.fromCategories(cats())
        assertEquals("Interface sounds", idx.search("vibration").single().title)
        assertEquals("Light / dark", idx.search("amoled").single().title)
        assertTrue("a synonym that nothing carries finds nothing", idx.search("zebra").isEmpty())
    }

    @Test fun CFG_28_aSynonymScoresBelowTheHintAndTitleButAboveNothing() {
        val idx = SettingsIndex.fromCategories(cats())
        // "night": a synonym of Light / dark, and part of the hint of Quiet mode. The hint hit ranks first.
        assertEquals(listOf("Quiet mode", "Light / dark"), idx.search("night").map { it.title })
        // A title hit beats both.
        val withTitle = SettingsIndex.fromCategories(cats() + SettingCategory("X", g, listOf(SettingItem.Toggle("Night light", g, false) {})))
        assertEquals("Night light", withTitle.search("night").first().title)
        assertTrue(SettingsIndex.KEYWORD_SCORE < 30 && SettingsIndex.KEYWORD_SCORE > 20)
    }

    @Test fun CFG_28_everyWordMustPrefixMatchAKeyword() {
        val idx = SettingsIndex.fromCategories(cats())
        assertEquals(listOf("Light / dark"), idx.search("amo nig").map { it.title })
        assertTrue("both words have to match", idx.search("amoled zebra").isEmpty())
        assertEquals("Interface sounds", idx.search("clic vibra").single().title)
    }

    @Test fun CFG_28_theRowsOfAPaneAreEntriesWithStableIds() {
        val idx = SettingsIndex.fromCategories(cats())
        val hit = idx.search("snapshot").single()
        assertEquals("0/3/backup.snapshot", hit.id)
        assertEquals("backup.snapshot", hit.paneEntryId)
        assertEquals("Export, import and restore", hit.parent)
        assertEquals(3, hit.itemIndex)
        // a synonym of a pane row finds it too
        assertEquals("backup.import", idx.search("load").single().paneEntryId)
        // the pane itself is still one entry
        assertEquals(null, idx.search("export import").first().paneEntryId)
        assertEquals(cats().sumOf { it.items.size } + 2, idx.entries.size)
    }

    @Test fun CFG_28_openingAPaneEntryReachesTheHostWithThePaneRowAndItsId() {
        var opened: SettingEntry? = null
        val src: SettingsResultSource = CategorySettingsSource({ cats() }, { opened = it })
        src.open(src.results("restore point").single())
        assertEquals(3, opened!!.itemIndex)
        assertEquals("backup.snapshot", opened!!.paneEntryId)
    }

    @Test fun CFG_28_theOldAliasesStillWork() {
        val idx = SettingsIndex.fromCategories(listOf(SettingCategory("Side panel", g, listOf(SettingItem.Toggle("Unread strip", g, true) {}))))
        assertEquals(1, idx.search("comms").size) // the category keyword
        val calm = SettingsIndex.fromCategories(listOf(SettingCategory("Look", g, listOf(SettingItem.Toggle("Calm style", g, true) {}))))
        assertEquals(1, calm.search("vignette").size)
    }

    // ---- CFG-29 sliders -----------------------------------------------------------------------------------------------------------

    private fun slider(amount: Float, default: Float?, auto: Boolean = false, step: Float = 0f) =
        SettingItem.Slider("Icon size", g, amount, 0.5f..0.95f, "x", default = default, auto = auto, step = step) {}

    @Test fun CFG_29_theDefaultActionShowsOnlyWhileTheValueDiffers() {
        assertFalse(slider(0.82f, 0.82f).offersDefault)
        assertTrue(slider(0.7f, 0.82f).offersDefault)
        assertFalse("no default known, no action", slider(0.7f, null).offersDefault)
        assertFalse("a value one rounding step away from the default is the default", slider(0.8201f, 0.82f).offersDefault)
        assertTrue(slider(0.84f, 0.82f).offersDefault)
    }

    @Test fun CFG_29_aDerivedPreferenceIsAutoAndOffersNoDefault() {
        val s = slider(0.7f, 0.82f, auto = true)
        assertFalse(s.offersDefault)
        assertTrue(s.auto)
    }

    @Test fun CFG_29_theTickSitsWhereTheDefaultIsInTheRange() {
        assertEquals(0f, SettingsMath.fraction(0.5f, 0.5f..0.95f), 1e-6f)
        assertEquals(1f, SettingsMath.fraction(0.95f, 0.5f..0.95f), 1e-6f)
        assertEquals(0.7111f, SettingsMath.fraction(0.82f, 0.5f..0.95f), 1e-3f)
        assertEquals("outside the range is clamped to the end", 1f, SettingsMath.fraction(3f, 0f..1f), 0f)
        assertEquals(0f, SettingsMath.fraction(1f, 1f..1f), 0f)
    }

    @Test fun CFG_29_withKeepsEverythingAndAddsTheDefaultAndTheUnit() {
        val s = slider(0.7f, null).keywords("size").rowId("home.iconSize").with(default = 0.82f, valueText = "70%")
        assertEquals(0.82f, s.default!!, 0f)
        assertEquals("70%", s.valueText)
        assertEquals("home.iconSize", s.meta.id)
        assertEquals(listOf("size"), s.meta.keywords)
        assertEquals(0.7f, s.amount, 0f)
    }

    // ---- CFG-30 reset -------------------------------------------------------------------------------------------------------------

    private class FakeSnapshots(var ok: Boolean = true) : SettingsSnapshots {
        val log = ArrayList<String>()
        override fun take(reason: String): String? { log += "take:$reason"; return if (ok) "snap-1" else null }
        override fun restore(id: String): Boolean { log += "restore:$id"; return true }
    }

    @Test fun CFG_30_aSnapshotIsTakenBeforeAnythingChangesAndUndoRestoresIt() = runBlocking {
        val snaps = FakeSnapshots(); var changed = false
        val r = SettingsResetFlow.run(snaps, "before reset all settings") { changed = true; snaps.log += "apply" }
        assertTrue(changed)
        assertEquals(listOf("take:before reset all settings", "apply"), snaps.log)
        assertEquals("snap-1", r.snapshotId)
        val back = SettingsResetFlow.undo(snaps, r)!!
        assertEquals(listOf("take:before reset all settings", "apply", "restore:snap-1"), snaps.log)
        assertNull("nothing left to undo", back.snapshotId)
    }

    @Test fun CFG_30_noSnapshotNoReset() = runBlocking {
        val snaps = FakeSnapshots(ok = false); var changed = false
        val r = SettingsResetFlow.run(snaps, "x") { changed = true }
        assertFalse("nothing is changed when the safety net could not be made", changed)
        assertNull(r.snapshotId)
    }

    @Test fun CFG_30_aPageResetIsASnapshotFirstActionWithTheStackedConfirm() {
        var n = 0
        val cat = SettingCategory("Look", g, emptyList(), reset = { n++ })
        val a = SettingsResetFlow.pageAction(cat)!!
        assertTrue(a.meta.snapshotFirst)
        assertEquals(SettingsResetFlow.PAGE_TITLE, a.title)
        assertNotNull(a.confirm)
        assertTrue("the confirmation says user data is not touched", a.confirm!!.contains("not touched"))
        a.onClick(); assertEquals(1, n)
        assertNull("a page with nothing to reset has no row", SettingsResetFlow.pageAction(SettingCategory("About", g, emptyList())))
    }

    // ---- CFG-31 disabled rows and section ids -------------------------------------------------------------------------------------

    @Test fun CFG_31_aDisabledRowKeepsItsPlaceAndSaysWhy() {
        val t = SettingItem.Toggle("Count in badges", g, false) {}.disabledUnless(false, "Notification badges are off")
        assertEquals("Notification badges are off", t.meta.disabledReason)
        assertFalse(t.isEnabled)
        val on = SettingItem.Toggle("Count in badges", g, false) {}.disabledUnless(true, "x")
        assertTrue(on.isEnabled)
        assertEquals("Count in badges, Unavailable, Needs Circle layout", Disabled.description("Count in badges", "Needs Circle layout", "Unavailable"))
    }

    @Test fun CFG_31_aDisabledRowIsStillFoundByTheSearchAndSaysWhy() {
        val idx = SettingsIndex.fromCategories(listOf(SettingCategory("Home screen", g, listOf(
            SettingItem.Stepper("Rings", g, 2, 1..3) {}.disabledUnless(false, "Needs Circle layout"),
        ))))
        val hit = idx.search("rings").single()
        assertEquals("Needs Circle layout", hit.disabledReason)
    }

    @Test fun CFG_31_aSectionStartsAtARowIdNotAtItsEnglishTitle() {
        val items = listOf<SettingItem>(
            SettingItem.Toggle("Calm home", g, true) {}.rowId("home.glance"),
            SettingItem.Toggle("Anything", g, true) {},
            SettingItem.Choice("Reihenfolge", g, listOf("a"), "a", { it }) {}.rowId("home.order"),
        )
        // The row is named "Reihenfolge" here (a translation): the id still finds it.
        val h = OneUiSections.headers("Home screen", items)
        assertEquals(setOf(0, 2), h.keys)
        assertEquals("Pages", h[0]); assertEquals("Layout", h[2])
        assertEquals(2, OneUiSections.find(items, "home.order"))
        assertEquals("a row without an id is still found by its title", 1, OneUiSections.find(items, "Anything"))
    }

    @Test fun CFG_31_aSectionHeaderSurvivesItsFirstRowBeingConditional() {
        val first = SettingItem.Choice("Clock style", g, listOf("Off"), "Off", { it }) {}.rowId("home.clockStyle").disabled("Clock style is Off")
        val h = OneUiSections.headers("Home screen", listOf(first))
        assertEquals("Glance", h[0])
    }

    // ---- CFG-32 Finish setup ------------------------------------------------------------------------------------------------------

    @Test fun CFG_32_onlyWhatIsUnfinishedCounts() {
        val s = SetupState(defaultHome = false, notificationAccess = true, usageAccess = false, dndAccess = false, dndActionBound = false)
        assertEquals(listOf(SetupStep.DefaultHome, SetupStep.Usage), FinishSetup.pending(s))
        assertEquals("a Do Not Disturb access without a Do Not Disturb action is not asked for", 3, FinishSetup.rows(s).size)
        val bound = s.copy(dndActionBound = true)
        assertEquals(listOf(SetupStep.DefaultHome, SetupStep.Usage, SetupStep.DoNotDisturb), FinishSetup.pending(bound))
        assertEquals(4, FinishSetup.rows(bound).size)
    }

    @Test fun CFG_32_theRowIsGoneOnceEverythingIsDone() {
        assertNull(FinishSetup.category(SetupState.Complete) {})
        assertTrue(FinishSetup.isComplete(SetupState.Complete))
        val done = SetupState(true, true, true, dndAccess = false, dndActionBound = false)
        assertNull("DND access is only needed with a DND action", FinishSetup.category(done) {})
        assertNotNull(FinishSetup.category(done.copy(dndActionBound = true)) {})
    }

    @Test fun CFG_32_theSummaryCountsWhatIsLeft() {
        val s = SetupState(false, false, true, true, false)
        val c = FinishSetup.category(s) {}!!
        assertEquals("2 left", (c.items.single() as SettingItem.Pane).value)
        assertEquals(OneUiModel.FINISH_SETUP, OneUiModel.key(c.title))
        assertEquals("it is no row of the groups", emptyList<List<Int>>(), OneUiModel.groups(listOf(c)))
        assertEquals(0, OneUiModel.finishSetupIndex(listOf(c)))
        assertEquals(-1, OneUiModel.finishSetupIndex(emptyList()))
    }
}
