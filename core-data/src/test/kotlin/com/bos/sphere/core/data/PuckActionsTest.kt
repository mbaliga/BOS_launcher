package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuckActionsTest {
    private val A = CornerActionId.Flashlight
    private val B = CornerActionId.Camera
    private val C = CornerActionId.Maps

    @Test fun CFG_15_theShippedSetIsTheDefaultAndNeverIncludesRadarOrAllApps() {
        assertEquals(5, PuckActions.DEFAULT.size)
        assertFalse(CornerActionId.Search in PuckActions.AVAILABLE)
        assertFalse(CornerActionId.AllApps in PuckActions.AVAILABLE)
        assertTrue(PuckActions.DEFAULT.all { it in PuckActions.AVAILABLE })
        assertEquals(PuckActions.DEFAULT, PuckActions.decode(null))
    }

    @Test fun CFG_15_theUserChoosesWhichActionsAndTheirOrder() {
        var l = emptyList<CornerActionId>()
        l = PuckActions.add(l, A); l = PuckActions.add(l, B); l = PuckActions.add(l, C)
        assertEquals(listOf(A, B, C), l)
        assertEquals(listOf(B, A, C), PuckActions.move(l, 0, +1))
        assertEquals(listOf(C, A, B), PuckActions.move(l, 2, -2))
        assertEquals(l, PuckActions.move(l, 0, -1))
        assertEquals(listOf(A, C), PuckActions.remove(l, 1))
        assertEquals(l, PuckActions.remove(l, 9))
        assertEquals(l, PuckActions.add(l, A))
        assertEquals(l, PuckActions.add(l, CornerActionId.Search))
    }

    @Test fun CFG_15_theCountIsCappedAtTheCeilingTheLayoutHonours() {
        var l = emptyList<CornerActionId>()
        for (id in PuckActions.AVAILABLE) l = PuckActions.add(l, id)
        assertEquals(PuckActions.MAX, l.size)
        assertEquals(l, PuckActions.add(l, PuckActions.candidates(l).first()))
        assertEquals(PuckActions.MAX, PuckActions.sanitize(PuckActions.AVAILABLE + PuckActions.AVAILABLE).size)
        assertEquals(PuckActions.AVAILABLE.size - PuckActions.MAX, PuckActions.candidates(l).size)
    }

    @Test fun CFG_15_savedListRoundTripsAndAnEmptyRingIsKept() {
        val l = listOf(C, A)
        assertEquals(l, PuckActions.decode(PuckActions.encode(l)))
        assertEquals(emptyList<CornerActionId>(), PuckActions.decode(""))
        assertEquals(listOf(A), PuckActions.decode("Nope,Flashlight,Flashlight,Search"))
    }

    @Test fun CFG_15_storePersistsAcrossProcessesAndResets() {
        val sp = MapSharedPreferences()
        PuckActionsStore(sp).set(listOf(C, A))
        val again = PuckActionsStore(sp)
        assertEquals(listOf(C, A), again.actions.value)
        again.reset()
        assertEquals(PuckActions.DEFAULT, PuckActionsStore(sp).actions.value)
    }

    @Test fun CFG_15_theChosenActionsTravelInTheStateFile() {
        val a = MapSharedPreferences(mapOf("actions" to "Maps,Flashlight"))
        val b = MapSharedPreferences()
        val src = PrefFilesSource({ if (it == "puck_actions") a else MapSharedPreferences() })
        val dst = PrefFilesSource({ if (it == "puck_actions") b else MapSharedPreferences() })
        val objs = src.readObjects()
        dst.write(emptyMap(), objs, setOf(StateSchema.K_PREF_FILES))
        assertEquals(listOf(C, A), PuckActionsStore(b).actions.value)
    }
}
