package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (wave 45): the hint under the edit bar shows for the first three entries into edit mode only. */
class EditChromeHintTest {
    @Test fun HOM_12_theHintIsForTheFirstThreeEntriesOnly() {
        assertTrue(EditChromeHint.shownOn(0)); assertTrue(EditChromeHint.shownOn(1)); assertTrue(EditChromeHint.shownOn(2))
        assertFalse(EditChromeHint.shownOn(3)); assertFalse(EditChromeHint.shownOn(40))
    }

    @Test fun HOM_12_theCounterIsCappedAndNeverNegative() {
        assertEquals(1, EditChromeHint.next(0)); assertEquals(4, EditChromeHint.next(3)); assertEquals(4, EditChromeHint.next(1_000))
        assertEquals(1, EditChromeHint.next(-5))
        assertFalse("a negative stored value counts as none, it does not hide the hint forever", EditChromeHint.shownOn(-1))
    }

    @Test fun HOM_12_theStoreCountsEntriesAndSurvivesARestart() {
        val sp = MapSharedPreferences()
        val seen = (1..5).map { EditChromeStore(sp).registerEntry() } // a new store object each time: a restart
        assertEquals(listOf(true, true, true, false, false), seen)
        assertEquals(EditChromeHint.MAX_SHOWS + 1, EditChromeStore(sp).entries())
    }

    @Test fun HOM_12_aGarbageCounterReadsAsZero() {
        val sp = MapSharedPreferences(mapOf("entries" to "not a number"))
        assertEquals(0, EditChromeStore(sp).entries())
        assertTrue(EditChromeStore(sp).registerEntry())
    }

    @Test fun HOM_12_theHintFileIsDeviceLocal() {
        assertTrue(EditChromeStore.FILE in StateSchema.BACKUP_EXCLUDED_PREF_FILES)
    }
}
