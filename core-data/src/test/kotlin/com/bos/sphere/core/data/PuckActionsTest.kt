package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuckActionsTest {
    private val A = PuckSlot.Action(CornerActionId.Flashlight)
    private val B = PuckSlot.Action(CornerActionId.Camera)
    private val C = PuckSlot.Action(CornerActionId.Maps)
    private val E = PuckSlot.Empty
    private fun ids(l: List<PuckSlot>) = l.map { (it as? PuckSlot.Action)?.id }

    @Test fun CFG_15_aFreshInstallFillsEightSegmentsFlashlightAlarmBellDndRadarCameraCalculatorSettings() {
        assertEquals(
            listOf(
                CornerActionId.Flashlight, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb,
                CornerActionId.Search, CornerActionId.Camera, CornerActionId.Calculator, CornerActionId.SystemSettings,
            ).map { PuckSlot.Action(it) },
            PuckActions.DEFAULT,
        )
        assertEquals(PuckActions.DEFAULT, PuckActions.decode(null))
        val s = PuckActionsStore(MapSharedPreferences())
        assertEquals(PuckActions.DEFAULT, s.slots.value)
        assertEquals(8, s.segments.value); assertFalse(s.showLabels.value)
        assertTrue("every default is a choosable action", PuckActions.DEFAULT_IDS.all { it in PuckActions.AVAILABLE })
    }

    @Test fun CFG_15_anySlotCanHoldRadarButNeverTheDiscItself() {
        assertTrue(CornerActionId.Search in PuckActions.AVAILABLE)
        assertFalse(CornerActionId.AllApps in PuckActions.AVAILABLE)
        assertEquals(listOf(A) + List(7) { E }, PuckActions.normalize(listOf(A, PuckSlot.Action(CornerActionId.AllApps))))
        assertEquals(PuckSlot.Action(CornerActionId.Search), PuckActions.with(PuckActions.DEFAULT, 2, PuckSlot.Action(CornerActionId.Search))[2])
    }

    @Test fun CFG_15_theListIsAlwaysExactlyEightLongWhateverTheRingSize() {
        assertEquals(8, PuckActions.normalize(emptyList()).size)
        assertEquals(8, PuckActions.normalize(List(11) { A }).size)
        assertEquals(PuckActions.DEFAULT, PuckActions.with(PuckActions.DEFAULT, 8, B))
        assertEquals(B, PuckActions.with(PuckActions.DEFAULT, 7, B)[7])
    }

    @Test fun CFG_15_aSlotHoldsAnActionAnAppOrNothingAndRoundTrips() {
        val l = listOf(C, PuckSlot.App("com.demo/.Main#0"), E, A, B, E, C, E)
        assertEquals(l, PuckActions.decode(PuckActions.encode(l)))
        assertEquals(8, PuckActions.encode(l).split('\n').size)
        assertEquals(List(8) { E }, PuckActions.decode(""))
    }

    @Test fun CFG_15_w38_aFourSlotSaveIsCompletedWithDefaultsNotAlreadyThereAndKeepsTheUsersFour() {
        val four = listOf(C, PuckSlot.App("k"), E, A)
        val got = PuckActions.decode("a:Maps\np:k\n-\na:Flashlight")
        assertEquals(four, got.take(4))
        assertEquals("the new four are defaults the user does not have: not Flashlight again", listOf(CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb, CornerActionId.Search), ids(got.drop(4)))
        assertEquals(PuckActions.DEFAULT, PuckActions.decode("a:Flashlight\na:Clock\na:Notifications\na:DoNotDisturb"))
    }

    @Test fun CFG_15_w38_completionNeverRunsOutOfActionsAndIsIdempotent() {
        val all = PuckActions.DEFAULT_IDS.take(4).map { PuckSlot.Action(it) } + listOf(B, PuckSlot.Action(CornerActionId.Search), PuckSlot.Action(CornerActionId.Calculator))
        val done = PuckActions.completeFromDefaults(all)
        assertEquals(8, done.size); assertEquals(all, done.take(7))
        assertEquals("an extra action when the defaults are used up", PuckSlot.Action(CornerActionId.SystemSettings), done[7])
        assertEquals(done, PuckActions.completeFromDefaults(done))
        val covered = PuckActions.DEFAULT_IDS.map { PuckSlot.Action(it) }.take(4) + listOf(B, C) // two short, everything default used
        assertEquals(8, PuckActions.completeFromDefaults(covered).size)
    }

    @Test fun CFG_15_changingOneSlotLeavesTheOthers() {
        val l = PuckActions.with(PuckActions.DEFAULT, 1, PuckSlot.App("k"))
        assertEquals(PuckActions.DEFAULT[0], l[0]); assertEquals(PuckSlot.App("k"), l[1]); assertEquals(PuckActions.DEFAULT[7], l[7])
        assertEquals(E, PuckActions.with(l, 1, E)[1])
    }

    @Test fun CFG_15_migrationTakesTheFirstFourOfTheOldRingInOrderAndFillsTheRest() {
        val old = listOf(CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Maps, CornerActionId.Camera, CornerActionId.Email)
        val got = PuckActions.migrate(old)
        assertEquals(old.take(4).map { PuckSlot.Action(it) }, got.take(4))
        assertEquals(8, got.size)
        assertEquals("Camera is already there, so the fill skips it", listOf(CornerActionId.Flashlight, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb), ids(got.drop(4)))
    }

    @Test fun CFG_15_migrationFillsAShortOldRingWithTheDefaultsNotAlreadyThere() {
        val got = PuckActions.migrate(listOf(CornerActionId.Clock, CornerActionId.Phone))
        assertEquals(listOf(CornerActionId.Clock, CornerActionId.Phone, CornerActionId.Flashlight, CornerActionId.Notifications).map { PuckSlot.Action(it) }, got.take(4))
        assertEquals(8, got.size)
    }

    @Test fun CFG_15_migrationOfNothingOrAnEmptiedRingGivesTheDefaults() {
        assertEquals(PuckActions.DEFAULT, PuckActions.migrate(null))
        assertEquals(PuckActions.DEFAULT, PuckActions.migrate(emptyList()))
        assertNull(PuckActions.decodeLegacy(null))
        assertEquals(listOf(CornerActionId.Flashlight), PuckActions.decodeLegacy("Nope,Flashlight,Flashlight,Search,AllApps"))
    }

    @Test fun CFG_15_storeMigratesTheWave29KeyOnceAndRemovesIt() {
        val sp = MapSharedPreferences(mapOf("actions" to "Maps,Flashlight,Camera,Phone,Email"))
        val store = PuckActionsStore(sp)
        assertEquals(listOf(CornerActionId.Maps, CornerActionId.Flashlight, CornerActionId.Camera, CornerActionId.Phone).map { PuckSlot.Action(it) }, store.slots.value.take(4))
        assertEquals(8, store.slots.value.size)
        assertFalse(sp.contains("actions"))
        assertTrue(sp.contains("slots"))
        assertEquals(store.slots.value, PuckActionsStore(sp).slots.value)
    }

    @Test fun CFG_15_w38_aStoreWithTheWave30FourSlotsIsRewrittenAsEightOnceAndSecondLoadChangesNothing() {
        val sp = MapSharedPreferences(mapOf("slots" to "p:demo.k\n-\na:Maps\na:Phone"))
        val first = PuckActionsStore(sp).slots.value
        assertEquals(listOf(PuckSlot.App("demo.k"), E, C, PuckSlot.Action(CornerActionId.Phone)), first.take(4))
        assertEquals(8, first.size)
        assertEquals("rewritten in the store", 8, sp.getString("slots", null)!!.split('\n').size)
        val raw = sp.getString("slots", null)
        val second = PuckActionsStore(sp).slots.value
        assertEquals(first, second); assertEquals(raw, sp.getString("slots", null))
        assertEquals(8, sp.getString("slots", null)!!.split('\n').size)
    }

    @Test fun CFG_15_w38_theRingSizeIsSixSevenOrEightAndDefaultsToEight() {
        assertEquals(listOf(6, 7, 8), PuckActions.SEGMENT_CHOICES)
        assertEquals(6, PuckActions.clampSegments(2)); assertEquals(8, PuckActions.clampSegments(99)); assertEquals(7, PuckActions.clampSegments(7))
        val sp = MapSharedPreferences()
        val s = PuckActionsStore(sp)
        s.setSegments(6)
        assertEquals(6, s.segments.value); assertEquals(6, PuckActionsStore(sp).segments.value)
        s.setSegments(40)
        assertEquals(8, s.segments.value)
        assertEquals("a garbled stored size reads as a valid one", 8, PuckActionsStore(MapSharedPreferences(mapOf("segments" to 3))).segments.value.let { if (it in 6..8) 8 else -1 })
    }

    @Test fun CFG_15_w38_goingFromEightToSixAndBackKeepsTheLastTwoSlots() {
        val s = PuckActionsStore(MapSharedPreferences())
        s.set(6, B); s.set(7, C)
        s.setSegments(6); s.setSegments(8)
        assertEquals(B, s.slots.value[6]); assertEquals(C, s.slots.value[7])
        s.setSegments(7)
        assertEquals("seven shows the first seven; the eighth is kept", C, s.slots.value[7])
    }

    @Test fun CFG_15_w38_theLabelsToggleDefaultsOffPersistsAndSurvivesReload() {
        val sp = MapSharedPreferences()
        val s = PuckActionsStore(sp)
        assertFalse(s.showLabels.value)
        s.setShowLabels(true)
        assertTrue(PuckActionsStore(sp).showLabels.value)
        sp.edit().putBoolean("labels", false).putInt("segments", 7).apply()
        s.reload()
        assertFalse(s.showLabels.value); assertEquals(7, s.segments.value)
    }

    @Test fun CFG_15_storePersistsAcrossProcessesAndResets() {
        val sp = MapSharedPreferences()
        PuckActionsStore(sp).set(0, C)
        val again = PuckActionsStore(sp)
        assertEquals(C, again.slots.value[0])
        again.setSegments(6)
        again.reset()
        assertEquals(PuckActions.DEFAULT, PuckActionsStore(sp).slots.value)
        assertEquals("reset leaves the ring size alone", 6, PuckActionsStore(sp).segments.value)
    }

    @Test fun CFG_15_theChosenSegmentsTravelInTheStateFileAndAnOldExportIsMigrated() {
        val a = MapSharedPreferences(mapOf("slots" to PuckActions.encode(listOf(C, PuckSlot.App("x"), E, A)), "segments" to 6, "labels" to true))
        val b = MapSharedPreferences()
        val src = PrefFilesSource({ if (it == "puck_actions") a else MapSharedPreferences() })
        val dst = PrefFilesSource({ if (it == "puck_actions") b else MapSharedPreferences() })
        dst.write(emptyMap(), src.readObjects(), setOf(StateSchema.K_PREF_FILES))
        val got = PuckActionsStore(b)
        assertEquals(listOf(C, PuckSlot.App("x"), E, A), got.slots.value.take(4))
        assertEquals(6, got.segments.value); assertTrue(got.showLabels.value)

        val old = MapSharedPreferences(mapOf("actions" to "Maps,Flashlight"))
        assertEquals(listOf(C, A), PuckActionsStore(old).slots.value.take(2))
    }

    @Test fun CFG_15_aSectionWhoseActionCannotWorkOnThisDeviceIsUnavailable() {
        assertFalse(PuckAvailability.of(CornerActionId.Flashlight, hasFlash = false, hasPolicyAccess = true))
        assertTrue(PuckAvailability.of(CornerActionId.Flashlight, hasFlash = true, hasPolicyAccess = false))
        assertFalse(PuckAvailability.of(CornerActionId.DoNotDisturb, hasFlash = true, hasPolicyAccess = false))
        assertTrue(PuckAvailability.of(CornerActionId.DoNotDisturb, hasFlash = false, hasPolicyAccess = true))
        for (id in listOf(CornerActionId.Search, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.Phone, CornerActionId.Camera, CornerActionId.Calculator, CornerActionId.SystemSettings)) assertTrue(PuckAvailability.of(id, hasFlash = false, hasPolicyAccess = false))
    }
}
