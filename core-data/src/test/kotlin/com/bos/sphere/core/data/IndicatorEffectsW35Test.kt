package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 35 (signalEffects): the two selections, the migration of the old effect set, the severity mapping and the retired keys. */
class IndicatorEffectsW35Test {
    private fun store(vararg kv: Pair<String, Any>) = MapSharedPreferences(mapOf(*kv))

    @Test fun PAL_24_w35_theDefaultIsAnimatedDotsOnlyAndTheModelHasNoSpaceForAnInvalidPair() {
        val p = LauncherPrefs()
        assertEquals(CalmEffect.AnimatedDots, p.indicatorCalm); assertNull(p.indicatorAlert)
        // The type itself carries the rule: one slot per group, so two calm effects (or two alert effects) cannot be expressed.
        assertEquals(listOf("calm", "alert"), LauncherPrefs::class.java.declaredFields.map { it.name }.filter { it == "indicatorCalm" || it == "indicatorAlert" }.map { if (it == "indicatorCalm") "calm" else "alert" })
        assertEquals(2, CalmEffect.entries.size); assertEquals(2, AlertEffect.entries.size)
        assertEquals(listOf(IndicatorMetric.Signal, IndicatorMetric.Battery), IndicatorMetric.entries)
    }

    @Test fun PAL_24_w35_migrationFromEveryOldSetShape() {
        fun m(raw: String?) = IndicatorMigration.fromLegacy(raw)
        assertNull("never stored: the default stays", m(null))
        assertEquals(IndicatorSelection(null, null), m(""))
        assertEquals(IndicatorSelection(CalmEffect.AnimatedDots, null), m("DotsBreathe"))
        assertEquals(IndicatorSelection(CalmEffect.Bloom, null), m("DotsBloomCentre"))
        assertEquals(IndicatorSelection(CalmEffect.Bloom, null), m("DotsBloomEdges"))
        assertEquals(IndicatorSelection(null, AlertEffect.Vignette), m("Vignette"))
        assertEquals(IndicatorSelection(null, AlertEffect.ComicLines), m("ComicLines"))
        assertEquals("the first calm one and the first alert one", IndicatorSelection(CalmEffect.Bloom, AlertEffect.ComicLines), m("SnakeOutline,DotsBloomEdges,DotsBreathe,ComicLines,Vignette"))
        assertEquals(IndicatorSelection(CalmEffect.AnimatedDots, AlertEffect.Vignette), m("DotsBreathe,DotsBloomCentre,Vignette,ComicLines"))
        assertEquals("the removed effects read as nothing", IndicatorSelection(null, null), m("SnakeOutline,EdgeMeter,Nonsense"))
    }

    @Test fun PAL_24_w35_theStoreMigratesTheOldKeysOnceAndRemovesThem() {
        val sp = store("indEffects" to "DotsBloomCentre,Vignette", "indInvert" to true, "indMetric" to "Storage")
        val p = LauncherPrefsStore(sp).prefs.value
        assertEquals(CalmEffect.Bloom, p.indicatorCalm); assertEquals(AlertEffect.Vignette, p.indicatorAlert)
        assertEquals("any other old measurement becomes Signal", IndicatorMetric.Signal, p.indicatorMetric)
        assertFalse(sp.contains("indEffects")); assertFalse(sp.contains("indInvert"))
        assertEquals("Bloom", sp.getString("indCalm", null)); assertEquals("Vignette", sp.getString("indAlert", null))
        // A second start reads the new keys and does not go back to the old ones.
        val again = LauncherPrefsStore(sp).prefs.value
        assertEquals(p.indicatorCalm, again.indicatorCalm); assertEquals(p.indicatorAlert, again.indicatorAlert)
    }

    @Test fun PAL_24_w35_aFreshInstallIsAnimatedDotsAndAnOldBatteryChoiceStays() {
        val fresh = LauncherPrefsStore(store()).prefs.value
        assertEquals(CalmEffect.AnimatedDots, fresh.indicatorCalm); assertNull(fresh.indicatorAlert); assertEquals(IndicatorMetric.Signal, fresh.indicatorMetric)
        assertEquals(IndicatorMetric.Battery, LauncherPrefsStore(store("indMetric" to "Battery")).prefs.value.indicatorMetric)
        assertEquals(IndicatorMetric.Signal, LauncherPrefsStore(store("indMetric" to "Network")).prefs.value.indicatorMetric)
    }

    @Test fun PAL_24_w35_bothOffRoundTripsAndOnePairRoundTrips() {
        val sp = store()
        val s = LauncherPrefsStore(sp)
        s.update { it.copy(indicatorCalm = null, indicatorAlert = null) }
        val off = LauncherPrefsStore(sp).prefs.value
        assertNull(off.indicatorCalm); assertNull(off.indicatorAlert)
        s.update { it.copy(indicatorCalm = CalmEffect.Bloom, indicatorAlert = AlertEffect.ComicLines) }
        val on = LauncherPrefsStore(sp).prefs.value
        assertEquals(CalmEffect.Bloom, on.indicatorCalm); assertEquals(AlertEffect.ComicLines, on.indicatorAlert)
    }

    @Test fun PAL_24_w35_oldKeysAreRetiredAndTheEffectSetIsOnlyPurgedAfterItsMigration() {
        assertTrue("indInvert" in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs")); assertTrue("indInvert" in RetiredPrefKeys.DROPPED_ON_IMPORT)
        val before = mapOf("launcher_prefs" to store("indEffects" to "Vignette", "indInvert" to false))
        assertEquals("the effect set is kept until its migration has written the new keys", 1, RetiredPrefKeys.purge { before[it] })
        assertTrue(before.getValue("launcher_prefs").contains("indEffects"))
        val after = mapOf("launcher_prefs" to store("indEffects" to "Vignette", "indCalm" to "Off", "indAlert" to "Vignette"))
        assertEquals(1, RetiredPrefKeys.purge { after[it] })
        assertFalse(after.getValue("launcher_prefs").contains("indEffects")); assertTrue(after.getValue("launcher_prefs").contains("indAlert"))
    }

    @Test fun PAL_24_w35_severityHealthyIsNothingFallingRampsAndLostIsUrgent() {
        fun sig(v: Float) = IndicatorSeverity.of(IndicatorMetric.Signal, IndicatorReading(ReadingKind.Live, v, ""))
        assertEquals(IndicatorSeverity.Healthy, sig(1f)); assertEquals(IndicatorSeverity.Healthy, sig(0.75f))
        assertFalse(sig(1f).active)
        val a = sig(0.5f); val b = sig(0.25f); val c = sig(0f)
        assertTrue(a.active && !a.urgent); assertTrue(b.level > a.level); assertTrue(c.level > b.level); assertEquals(1f, c.level, 0f)
        assertFalse("no bars but connected is severe, not lost", c.urgent)
        assertTrue("just under healthy starts at about nothing", sig(0.7499f).level < 0.01f)
        assertEquals(IndicatorSeverity(1f, true), IndicatorSeverity.of(IndicatorMetric.Signal, IndicatorReading(ReadingKind.Off, 0f, "Offline")))
        assertEquals("airplane mode is deliberate, not an alarm", IndicatorSeverity.Healthy, IndicatorSeverity.of(IndicatorMetric.Signal, IndicatorReading(ReadingKind.Idle, 0f, "Airplane")))
    }

    @Test fun PAL_24_w35_batteryChargingCountsAsHealthyAndUrgentIsFivePercentAndNotCharging() {
        fun bat(v: Float, charging: Boolean = false) = IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorReading(ReadingKind.Live, v, "", charging))
        assertEquals(IndicatorSeverity.Healthy, bat(0.6f)); assertTrue(bat(0.59f).active)
        assertEquals(IndicatorSeverity.Healthy, bat(0.02f, charging = true)); assertEquals(IndicatorSeverity.Healthy, bat(0.3f, charging = true))
        assertTrue(bat(0.3f).level > 0f && !bat(0.3f).urgent)
        assertTrue(bat(0.06f).level > bat(0.3f).level && !bat(0.06f).urgent)
        assertEquals(IndicatorSeverity(1f, true), bat(0.05f)); assertEquals(IndicatorSeverity(1f, true), bat(0f))
        assertEquals("a battery is never offline", IndicatorSeverity.Healthy, IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorReading(ReadingKind.Off, 0f, "")))
    }

    @Test fun PAL_24_w35_theUrgentColourIsAlwaysRedAndTheLowOnesFollowThePalette() {
        val low = 0xFF111111L; val mid = 0xFF999999L
        assertEquals(IndicatorColors.URGENT_RED, IndicatorColors.alertArgb(IndicatorSeverity(1f, true), low, mid))
        assertEquals(mid, IndicatorColors.alertArgb(IndicatorSeverity(0f, false), low, mid))
        assertEquals(low, IndicatorColors.alertArgb(IndicatorSeverity(1f, false), low, mid))
    }

    @Test fun PAL_24_w35_theSimulatedPreviewReadingIsLostAtZeroForSignalOnly() {
        assertEquals(ReadingKind.Off, IndicatorPreviewReading.of(IndicatorMetric.Signal, 0f).kind)
        assertEquals(ReadingKind.Live, IndicatorPreviewReading.of(IndicatorMetric.Battery, 0f).kind)
        assertTrue(IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorPreviewReading.of(IndicatorMetric.Battery, 0.03f)).urgent)
    }
}
