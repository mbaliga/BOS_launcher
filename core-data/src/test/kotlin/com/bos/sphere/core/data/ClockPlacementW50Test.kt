package com.bos.sphere.core.data

import com.bos.sphere.core.data.ClockPlacement.clockMoved
import com.bos.sphere.core.data.ClockPlacement.clockPos
import com.bos.sphere.core.data.ClockPlacement.restoreClockFrom
import com.bos.sphere.core.data.ClockPlacement.withClockPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 50 (glanceClockEdit, HOM-23): the pure maths of moving the Glance clock and where it is stored. */
class ClockPlacementW50Test {
    private val aw = 1000f; private val ah = 2000f; private val w = 400f; private val h = 300f

    @Test fun HOM_23_theFactoryPlaceIsTheCentredUpperMiddleSoNothingMovesUntilTheOwnerMovesIt() {
        val d = LauncherPrefs()
        assertEquals(ClockPlacement.Default, d.clockPos(false)); assertEquals(ClockPlacement.Default, d.clockPos(true)); assertFalse(d.clockMoved(false))
        val (l, t) = ClockPlacement.topLeft(ClockPlacement.Default, aw, ah, w, h)
        assertEquals("centred", 300f, l, 0.01f); assertEquals("top at 14 percent of the area", 280f, t, 0.01f)
    }

    @Test fun HOM_23_aPlaceIsFractionsSoItSurvivesAnotherWindowSize() {
        val p = ClockPlacement.fromTopLeft(100f, 600f, aw, ah, w, h)
        assertEquals(0.3f, p.x, 1e-4f); assertEquals(0.3f, p.y, 1e-4f)
        val (l, t) = ClockPlacement.topLeft(p, 2000f, 1000f, 800f, 150f) // a landscape tablet window, a larger clock
        assertEquals(200f, l, 0.01f); assertEquals(300f, t, 0.01f)
    }

    @Test fun HOM_23_theClockIsKeptWhollyInsideTheArea() {
        for (p in listOf(ClockPlacement.Pos(-3f, -3f), ClockPlacement.Pos(0f, 0f), ClockPlacement.Pos(1f, 1f), ClockPlacement.Pos(9f, 9f))) {
            val (l, t) = ClockPlacement.topLeft(p, aw, ah, w, h)
            assertTrue("$p left $l", l >= 0f && l + w <= aw + 0.01f); assertTrue("$p top $t", t >= 0f && t + h <= ah + 0.01f)
            val c = ClockPlacement.clamp(p, aw, ah, w, h)
            val (l2, t2) = ClockPlacement.topLeft(c, aw, ah, w, h)
            assertEquals(l, l2, 0.01f); assertEquals(t, t2, 0.01f)
        }
        val (l, t) = ClockPlacement.topLeft(ClockPlacement.Pos(0.5f, 0.5f), 300f, 200f, 400f, 300f) // a clock bigger than the area sits at the corner
        assertEquals(0f, l, 0f); assertEquals(0f, t, 0f)
    }

    @Test fun HOM_23_aDraggedClockSnapsToTheCentreLinesOnlyWhenNearThem() {
        val near = ClockPlacement.fromTopLeft(310f, 300f, aw, ah, w, h) // centre x 510, centre y 450
        val s = ClockPlacement.snap(near, aw, ah, w, h, 20f)
        assertTrue(s.x); assertFalse(s.y); assertEquals(0.5f, s.pos.x, 1e-4f)
        val mid = ClockPlacement.fromTopLeft(310f, 845f, aw, ah, w, h) // centre y 995: near 1000
        val m = ClockPlacement.snap(mid, aw, ah, w, h, 20f)
        assertTrue(m.x && m.y); assertEquals(0.5f, m.pos.x, 1e-4f); assertEquals((1000f - h / 2f) / ah, m.pos.y, 1e-4f)
        val far = ClockPlacement.snap(ClockPlacement.fromTopLeft(100f, 300f, aw, ah, w, h), aw, ah, w, h, 20f)
        assertFalse(far.x); assertFalse(far.y); assertEquals(0.15f, far.pos.y, 1e-4f)
    }

    @Test fun HOM_23_aStepMovesByAFixedFractionAndNeverLeavesTheArea() {
        val p = ClockPlacement.nudge(ClockPlacement.Pos(0.5f, 0.3f), 1, -1, aw, ah, w, h)
        assertEquals(0.55f, p.x, 1e-4f); assertEquals(0.25f, p.y, 1e-4f)
        val edge = ClockPlacement.nudge(ClockPlacement.Pos(0.2f, 0.0f), -1, -1, aw, ah, w, h)
        assertEquals(0.2f, edge.x, 1e-4f); assertEquals(0f, edge.y, 1e-4f) // the left edge is a centre of 0.2 for a 400 wide clock
        assertEquals(0f, ClockPlacement.nudge(ClockPlacement.Pos(0.2f, 0.5f), -1, 0, aw, ah, w, h).x - 0.2f, 1e-4f)
    }

    @Test fun HOM_23_aBadStoredValueIsTheFactoryPlace() {
        assertEquals(0.14f, ClockPlacement.stored(Float.NaN, 0.14f), 0f); assertEquals(0.14f, ClockPlacement.stored(7f, 0.14f), 0f); assertEquals(0.25f, ClockPlacement.stored(0.25f, 0.14f), 0f)
        assertEquals(ClockPlacement.Default, ClockPlacement.of(Float.NaN, -1f))
        assertEquals(ClockPlacement.Pos(0.2f, ClockPlacement.DEFAULT_Y), ClockPlacement.of(0.2f, -1f))
        assertTrue(ClockPlacement.isDefault(ClockPlacement.Default)); assertFalse(ClockPlacement.isDefault(ClockPlacement.Pos(0.5f, 0.2f)))
    }

    @Test fun HOM_23_thePlaceIsStoredPerOrientationAndSurvivesARestart() {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { it.withClockPos(false, ClockPlacement.Pos(0.3f, 0.6f)).withClockPos(true, ClockPlacement.Pos(0.7f, 0.2f)) }
        val back = LauncherPrefsStore(sp).prefs.value
        assertEquals(ClockPlacement.Pos(0.3f, 0.6f), back.clockPos(false)); assertEquals(ClockPlacement.Pos(0.7f, 0.2f), back.clockPos(true))
        assertTrue(back.clockMoved(false) && back.clockMoved(true))
        LauncherPrefsStore(sp).update { it.withClockPos(true, null) }
        val reset = LauncherPrefsStore(sp).prefs.value
        assertEquals("portrait kept", ClockPlacement.Pos(0.3f, 0.6f), reset.clockPos(false)); assertEquals(ClockPlacement.Default, reset.clockPos(true)); assertFalse(reset.clockMoved(true))
        val garbage = LauncherPrefsStore(MapSharedPreferences(mapOf("glanceClockX" to 4f, "glanceClockY" to Float.NaN))).prefs.value
        assertEquals(ClockPlacement.Default, garbage.clockPos(false))
    }

    @Test fun HOM_23_cancelRestoresTheClockSettingsAndThePlaceButNothingElse() {
        val before = LauncherPrefs(glanceClockSize = ClockSize.Small, glanceClockDate = true).withClockPos(false, ClockPlacement.Pos(0.4f, 0.4f))
        val edited = before.copy(clockStyle = ClockStyle.Word, glanceClockSize = ClockSize.Large, glanceClockAccent = true, clockFormat = ClockFormat.Hour24, glanceClockDate = false, showMusic = !before.showMusic)
            .withClockPos(false, null).withClockPos(true, ClockPlacement.Pos(0.1f, 0.1f))
        val back = edited.restoreClockFrom(before)
        assertEquals(before.copy(showMusic = !before.showMusic), back)
    }
}
