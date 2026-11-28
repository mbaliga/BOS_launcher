package com.bos.sphere.ui

import com.bos.sphere.core.data.HapticStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 27 (soundPopup): the state mapping of the Sound and Vibration sheet, pure JVM. */
class SoundVibrationStateTest {
    private val v = FakeVolumes()
    private val h = FakeHaptics()
    private fun read() = SoundVibrationState.read(v, h)

    @Test fun SND_01_theFourRowsAreInTheMockUpOrderWithTheSystemMaxima() {
        val s = read()
        assertEquals(listOf(SoundRowKind.Media, SoundRowKind.Notifications, SoundRowKind.Alarms, SoundRowKind.Vibration), s.rows.map { it.kind })
        assertEquals(listOf(15, 7, 7, 3), s.rows.map { it.max })
        assertEquals(listOf(8, 2, 4, 2), s.rows.map { it.value }) // vibration: Medium is step 2
    }

    @Test fun SND_02_aRequestedValueIsClampedToZeroAndMaxAndReadBack() {
        assertEquals(15, read().set(SoundRowKind.Media, 99, v, h).row(SoundRowKind.Media).value)
        assertEquals(0, read().set(SoundRowKind.Alarms, -5, v, h).row(SoundRowKind.Alarms).value)
        assertEquals(listOf(SoundRowKind.Media to 15, SoundRowKind.Alarms to 0), v.writes)
    }

    @Test fun SND_03_stepMovesByOneAndStopsAtTheEnds() {
        var s = read()
        s = s.step(SoundRowKind.Notifications, +1, v, h); assertEquals(3, s.row(SoundRowKind.Notifications).value)
        repeat(20) { s = s.step(SoundRowKind.Notifications, +1, v, h) }
        assertEquals(7, s.row(SoundRowKind.Notifications).value)
        repeat(20) { s = s.step(SoundRowKind.Notifications, -1, v, h) }
        assertEquals(0, s.row(SoundRowKind.Notifications).value)
    }

    @Test fun SND_04_percentIsTheRoundedFraction() {
        assertEquals(53, read().row(SoundRowKind.Media).percent) // 8 of 15
        assertEquals(0, SoundRow(SoundRowKind.Media, 0, 0).percent) // no maximum: no division by zero
    }

    @Test fun SND_05_aLockedNotificationRowIsDisabledWithTheReasonAndIgnoresChanges() {
        v.locked = setOf(SoundRowKind.Notifications)
        val s = read()
        val r = s.row(SoundRowKind.Notifications)
        assertFalse(r.enabled); assertEquals("Do Not Disturb is on", r.reason)
        assertTrue(s.row(SoundRowKind.Media).enabled)
        assertEquals(r.value, s.set(SoundRowKind.Notifications, 7, v, h).row(SoundRowKind.Notifications).value)
        assertTrue(v.writes.isEmpty())
    }

    @Test fun SND_06_aSecurityExceptionFromTheSystemDisablesTheRowInsteadOfCrashing() {
        v.locked = setOf(SoundRowKind.Notifications); v.throwing = true
        val s = read().set(SoundRowKind.Notifications, 6, v, h)
        assertFalse(s.row(SoundRowKind.Notifications).enabled)
        assertEquals("Do Not Disturb is on", s.row(SoundRowKind.Notifications).reason)
        assertEquals(2, s.row(SoundRowKind.Notifications).value)
    }

    @Test fun SND_07_theVibrationRowIsTheLauncherHapticStrengthInFourSteps() {
        var s = read()
        s = s.set(SoundRowKind.Vibration, 0, v, h); assertEquals(HapticStrength.Off, h.strength)
        s = s.set(SoundRowKind.Vibration, 1, v, h); assertEquals(HapticStrength.Light, h.strength)
        s = s.step(SoundRowKind.Vibration, +2, v, h); assertEquals(HapticStrength.Strong, h.strength)
        s = s.step(SoundRowKind.Vibration, +1, v, h); assertEquals(HapticStrength.Strong, h.strength)
        assertEquals(3, s.row(SoundRowKind.Vibration).value)
        assertTrue("no audio stream was written for vibration", v.writes.isEmpty())
    }
}
