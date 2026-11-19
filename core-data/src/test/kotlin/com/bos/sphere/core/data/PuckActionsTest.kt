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

    @Test fun CFG_15_aFreshInstallFillsTheFourSectionsFlashlightAlarmBellAndDnd() {
        assertEquals(
            listOf(CornerActionId.Flashlight, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.DoNotDisturb).map { PuckSlot.Action(it) },
            PuckActions.DEFAULT,
        )
        assertEquals(PuckActions.DEFAULT, PuckActions.decode(null))
        assertEquals(PuckActions.DEFAULT, PuckActionsStore(MapSharedPreferences()).slots.value)
    }

    @Test fun CFG_15_anySlotCanHoldRadarButNeverTheDiscItself() {
        assertTrue(CornerActionId.Search in PuckActions.AVAILABLE)
        assertFalse(CornerActionId.AllApps in PuckActions.AVAILABLE)
        assertEquals(listOf(A, E, E, E), PuckActions.normalize(listOf(A, PuckSlot.Action(CornerActionId.AllApps))))
        assertEquals(PuckSlot.Action(CornerActionId.Search), PuckActions.with(PuckActions.DEFAULT, 2, PuckSlot.Action(CornerActionId.Search))[2])
    }

    @Test fun CFG_15_theListIsAlwaysExactlyFourLong() {
        assertEquals(4, PuckActions.normalize(emptyList()).size)
        assertEquals(4, PuckActions.normalize(List(9) { A }).size)
        assertEquals(PuckActions.DEFAULT, PuckActions.with(PuckActions.DEFAULT, 7, B))
    }

    @Test fun CFG_15_aSlotHoldsAnActionAnAppOrNothingAndRoundTrips() {
        val l = listOf(C, PuckSlot.App("com.demo/.Main#0"), E, A)
        assertEquals(l, PuckActions.decode(PuckActions.encode(l)))
        assertEquals(listOf(A, E, E, E), PuckActions.decode("a:Flashlight\na:Nope\n-"))
        assertEquals(listOf(E, E, E, E), PuckActions.decode(""))
    }

    @Test fun CFG_15_changingOneSlotLeavesTheOthers() {
        val l = PuckActions.with(PuckActions.DEFAULT, 1, PuckSlot.App("k"))
        assertEquals(PuckActions.DEFAULT[0], l[0]); assertEquals(PuckSlot.App("k"), l[1]); assertEquals(PuckActions.DEFAULT[3], l[3])
        assertEquals(E, PuckActions.with(l, 1, E)[1])
    }

    @Test fun CFG_15_migrationTakesTheFirstFourOfTheOldRingInOrder() {
        val old = listOf(CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Maps, CornerActionId.Camera, CornerActionId.Email)
        assertEquals(old.take(4).map { PuckSlot.Action(it) }, PuckActions.migrate(old))
    }

    @Test fun CFG_15_migrationFillsAShortOldRingWithTheDefaultsNotAlreadyThere() {
        val got = PuckActions.migrate(listOf(CornerActionId.Clock, CornerActionId.Phone))
        assertEquals(listOf(CornerActionId.Clock, CornerActionId.Phone, CornerActionId.Flashlight, CornerActionId.Notifications).map { PuckSlot.Action(it) }, got)
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
        assertEquals(listOf(CornerActionId.Maps, CornerActionId.Flashlight, CornerActionId.Camera, CornerActionId.Phone).map { PuckSlot.Action(it) }, store.slots.value)
        assertFalse(sp.contains("actions"))
        assertTrue(sp.contains("slots"))
        assertEquals(store.slots.value, PuckActionsStore(sp).slots.value)
    }

    @Test fun CFG_15_storePersistsAcrossProcessesAndResets() {
        val sp = MapSharedPreferences()
        PuckActionsStore(sp).set(0, C)
        val again = PuckActionsStore(sp)
        assertEquals(C, again.slots.value[0])
        again.reset()
        assertEquals(PuckActions.DEFAULT, PuckActionsStore(sp).slots.value)
    }

    @Test fun CFG_15_theChosenSectionsTravelInTheStateFileAndAnOldExportIsMigrated() {
        val a = MapSharedPreferences(mapOf("slots" to PuckActions.encode(listOf(C, PuckSlot.App("x"), E, A))))
        val b = MapSharedPreferences()
        val src = PrefFilesSource({ if (it == "puck_actions") a else MapSharedPreferences() })
        val dst = PrefFilesSource({ if (it == "puck_actions") b else MapSharedPreferences() })
        dst.write(emptyMap(), src.readObjects(), setOf(StateSchema.K_PREF_FILES))
        assertEquals(listOf(C, PuckSlot.App("x"), E, A), PuckActionsStore(b).slots.value)

        val old = MapSharedPreferences(mapOf("actions" to "Maps,Flashlight"))
        assertEquals(listOf(C, A), PuckActionsStore(old).slots.value.take(2))
    }

    @Test fun CFG_15_aSectionWhoseActionCannotWorkOnThisDeviceIsUnavailable() {
        assertFalse(PuckAvailability.of(CornerActionId.Flashlight, hasFlash = false, hasPolicyAccess = true))
        assertTrue(PuckAvailability.of(CornerActionId.Flashlight, hasFlash = true, hasPolicyAccess = false))
        assertFalse(PuckAvailability.of(CornerActionId.DoNotDisturb, hasFlash = true, hasPolicyAccess = false))
        assertTrue(PuckAvailability.of(CornerActionId.DoNotDisturb, hasFlash = false, hasPolicyAccess = true))
        for (id in listOf(CornerActionId.Search, CornerActionId.Clock, CornerActionId.Notifications, CornerActionId.Phone)) assertTrue(PuckAvailability.of(id, hasFlash = false, hasPolicyAccess = false))
    }
}
