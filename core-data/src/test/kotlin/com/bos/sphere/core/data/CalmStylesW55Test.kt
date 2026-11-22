package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 55 (calmStyles): the style model, the default per wallpaper, the migrations from waves 35 and before, the retired keys, the severity mapping and the regular indicator content. */
class CalmStylesW55Test {
    private fun store(vararg kv: Pair<String, Any>) = MapSharedPreferences(mapOf(*kv))

    @Test fun PAL_24_w55_aHandfulOfStylesPlusOffAndRegular() {
        assertEquals(listOf(CalmStyle.None, CalmStyle.Regular, CalmStyle.EdgeDots, CalmStyle.CornerBloom, CalmStyle.BottomGlow, CalmStyle.Beam), CalmStyle.entries)
        assertEquals("four ambient styles", 4, CalmStyle.entries.count { it.ambient })
        assertTrue(CalmStyles.MAX_ALPHA <= 0.35f)
        assertEquals(0f, CalmStyles.alphaCap(0f), 0f); assertEquals(0.35f, CalmStyles.alphaCap(1f), 1e-6f); assertEquals(0.35f, CalmStyles.alphaCap(9f), 1e-6f)
        assertEquals(listOf(IndicatorMetric.Signal, IndicatorMetric.Battery), IndicatorMetric.entries)
    }

    @Test fun PAL_24_w55_theDefaultIsTheSubtlestForTheWallpaperAndNeverCoversAnotherKind() {
        for (k in WallpaperKind.entries) {
            val d = CalmStyles.resolve(null, k)
            if (k == WallpaperKind.Live) assertEquals(CalmStyle.EdgeDots, d) else assertEquals("$k keeps its own look untouched until asked", CalmStyle.None, d)
        }
        for (k in WallpaperKind.entries) assertEquals("an explicit choice always wins", CalmStyle.Beam, CalmStyles.resolve(CalmStyle.Beam, k))
        assertNull(LauncherPrefs().calmStyle); assertFalse(LauncherPrefs().calmAlsoRegular)
    }

    @Test fun PAL_24_w55_regularShowsAloneOrBesideAnAmbientStyleButNotBesideOffOrItself() {
        assertTrue(CalmStyles.showsRegular(CalmStyle.Regular, false)); assertTrue(CalmStyles.showsRegular(CalmStyle.Regular, true))
        assertFalse(CalmStyles.showsRegular(CalmStyle.None, true)); assertFalse(CalmStyles.showsRegular(CalmStyle.None, false))
        for (s in CalmStyle.entries.filter { it.ambient }) { assertTrue(CalmStyles.showsRegular(s, true)); assertFalse(CalmStyles.showsRegular(s, false)) }
    }

    @Test fun PAL_24_w55_wave35PairsMapToTheNearestStyle() {
        assertEquals(CalmStyle.CornerBloom, CalmStyles.fromW35("Bloom", null)); assertEquals(CalmStyle.CornerBloom, CalmStyles.fromW35("Bloom", "ComicLines"))
        assertEquals(CalmStyle.EdgeDots, CalmStyles.fromW35("AnimatedDots", "Vignette"))
        assertEquals(CalmStyle.BottomGlow, CalmStyles.fromW35("Off", "Vignette")); assertEquals(CalmStyle.Beam, CalmStyles.fromW35("Off", "ComicLines"))
        assertEquals(CalmStyle.None, CalmStyles.fromW35("Off", "Off")); assertEquals(CalmStyle.None, CalmStyles.fromW35("Nonsense", null))
        for (s in CalmStyle.entries) assertEquals(s, CalmStyles.parse(s.name)); assertNull(CalmStyles.parse("Vignette")); assertNull(CalmStyles.parse(null))
    }

    @Test fun PAL_24_w55_migrationFromEveryOldSetShape() {
        fun m(raw: String?) = IndicatorMigration.fromLegacy(raw)
        assertNull("never stored: the automatic default stays", m(null))
        assertEquals(CalmStyle.None, m(""))
        assertEquals(CalmStyle.EdgeDots, m("DotsBreathe")); assertEquals(CalmStyle.CornerBloom, m("DotsBloomCentre")); assertEquals(CalmStyle.CornerBloom, m("DotsBloomEdges"))
        assertEquals(CalmStyle.BottomGlow, m("Vignette")); assertEquals(CalmStyle.Beam, m("ComicLines"))
        assertEquals("the first calm one wins over an alert", CalmStyle.CornerBloom, m("SnakeOutline,DotsBloomEdges,DotsBreathe,ComicLines,Vignette"))
        assertEquals("the removed effects read as nothing", CalmStyle.None, m("SnakeOutline,EdgeMeter,Nonsense"))
    }

    @Test fun PAL_24_w55_theStoreMigratesTheWave35KeysOnceAndRemovesThem() {
        val sp = store("indCalm" to "Bloom", "indAlert" to "ComicLines", "indMetric" to "Storage")
        val p = LauncherPrefsStore(sp).prefs.value
        assertEquals(CalmStyle.CornerBloom, p.calmStyle)
        assertEquals("any other old measurement becomes Signal", IndicatorMetric.Signal, p.indicatorMetric)
        assertFalse(sp.contains("indCalm")); assertFalse(sp.contains("indAlert")); assertEquals("CornerBloom", sp.getString("calmStyle", null))
        assertEquals("a second start reads the new key", CalmStyle.CornerBloom, LauncherPrefsStore(sp).prefs.value.calmStyle)
    }

    @Test fun PAL_24_w55_theStoreMigratesTheOlderEffectSetAndInvertKey() {
        val sp = store("indEffects" to "DotsBloomCentre,Vignette", "indInvert" to true)
        assertEquals(CalmStyle.CornerBloom, LauncherPrefsStore(sp).prefs.value.calmStyle)
        assertFalse(sp.contains("indEffects")); assertFalse(sp.contains("indInvert"))
    }

    @Test fun PAL_24_w55_aFreshInstallIsAutomaticAndAnOldBatteryChoiceStays() {
        val fresh = LauncherPrefsStore(store()).prefs.value
        assertNull(fresh.calmStyle); assertEquals(IndicatorMetric.Signal, fresh.indicatorMetric)
        assertEquals(IndicatorMetric.Battery, LauncherPrefsStore(store("indMetric" to "Battery")).prefs.value.indicatorMetric)
        assertEquals(IndicatorMetric.Signal, LauncherPrefsStore(store("indMetric" to "Network")).prefs.value.indicatorMetric)
    }

    @Test fun PAL_24_w55_everyStyleAndTheRegularSwitchRoundTrip() {
        val sp = store()
        val s = LauncherPrefsStore(sp)
        for (style in CalmStyle.entries) {
            s.update { it.copy(calmStyle = style, calmAlsoRegular = style.ambient) }
            val back = LauncherPrefsStore(sp).prefs.value
            assertEquals(style, back.calmStyle); assertEquals(style.ambient, back.calmAlsoRegular)
        }
        s.update { it.copy(calmStyle = null) }
        assertNull(LauncherPrefsStore(sp).prefs.value.calmStyle); assertFalse(sp.contains("calmStyle"))
    }

    @Test fun PAL_24_w55_anImportedOldBackupMigratesAndLeavesNoOldKeys() {
        val sp = store("indCalm" to "AnimatedDots", "indAlert" to "Off", "calmRegular" to true)
        val p = LauncherPrefsStore(sp).prefs.value
        assertEquals(CalmStyle.EdgeDots, p.calmStyle); assertTrue(p.calmAlsoRegular)
    }

    @Test fun PAL_24_w55_oldKeysArePurgedOnlyAfterTheirMigration() {
        assertTrue("indInvert" in RetiredPrefKeys.BY_FILE.getValue("launcher_prefs")); assertTrue("indInvert" in RetiredPrefKeys.DROPPED_ON_IMPORT)
        val before = mapOf("launcher_prefs" to store("indCalm" to "Bloom", "indAlert" to "Vignette", "indInvert" to false))
        assertEquals("the pair is kept until calmStyle exists", 1, RetiredPrefKeys.purge { before[it] })
        assertTrue(before.getValue("launcher_prefs").contains("indCalm")); assertTrue(before.getValue("launcher_prefs").contains("indAlert"))
        val after = mapOf("launcher_prefs" to store("indEffects" to "Vignette", "indCalm" to "Off", "indAlert" to "Vignette", "calmStyle" to "BottomGlow"))
        assertEquals(3, RetiredPrefKeys.purge { after[it] })
        for (k in listOf("indEffects", "indCalm", "indAlert")) assertFalse(after.getValue("launcher_prefs").contains(k))
        assertTrue(after.getValue("launcher_prefs").contains("calmStyle"))
    }

    @Test fun PAL_24_w55_regularIndicatorContent() {
        fun r(v: Float, kind: ReadingKind = ReadingKind.Live, charging: Boolean = false) = IndicatorReading(kind, v, "", charging)
        assertEquals(4, RegularIndicator.bars(r(1f))); assertEquals(3, RegularIndicator.bars(r(0.75f))); assertEquals(2, RegularIndicator.bars(r(0.5f)))
        assertEquals(1, RegularIndicator.bars(r(0.25f))); assertEquals(0, RegularIndicator.bars(r(0f)))
        assertEquals("offline draws no bars", 0, RegularIndicator.bars(r(0.9f, ReadingKind.Off))); assertEquals("airplane too", 0, RegularIndicator.bars(r(0.9f, ReadingKind.Idle)))
        assertTrue(RegularIndicator.signalLost(r(0f, ReadingKind.Off))); assertFalse(RegularIndicator.signalLost(r(0f, ReadingKind.Idle)))
        assertEquals(87, RegularIndicator.percent(r(0.87f))); assertEquals(100, RegularIndicator.percent(r(7f))); assertNull(RegularIndicator.percent(r(0.5f, ReadingKind.Idle)))
        assertTrue(RegularIndicator.batteryLow(r(0.15f))); assertFalse(RegularIndicator.batteryLow(r(0.16f))); assertFalse("charging is never low", RegularIndicator.batteryLow(r(0.05f, charging = true)))
    }

    @Test fun PAL_24_w55_severityHealthyIsNothingFallingRampsAndLostIsUrgent() {
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

    @Test fun PAL_24_w55_batteryChargingCountsAsHealthyAndUrgentIsFivePercentAndNotCharging() {
        fun bat(v: Float, charging: Boolean = false) = IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorReading(ReadingKind.Live, v, "", charging))
        assertEquals(IndicatorSeverity.Healthy, bat(0.6f)); assertTrue(bat(0.59f).active)
        assertEquals(IndicatorSeverity.Healthy, bat(0.02f, charging = true)); assertEquals(IndicatorSeverity.Healthy, bat(0.3f, charging = true))
        assertTrue(bat(0.3f).level > 0f && !bat(0.3f).urgent)
        assertTrue(bat(0.06f).level > bat(0.3f).level && !bat(0.06f).urgent)
        assertEquals(IndicatorSeverity(1f, true), bat(0.05f)); assertEquals(IndicatorSeverity(1f, true), bat(0f))
        assertEquals("a battery is never offline", IndicatorSeverity.Healthy, IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorReading(ReadingKind.Off, 0f, "")))
    }

    @Test fun PAL_24_w55_theUrgentColourIsAlwaysRedAndTheLowOnesFollowThePalette() {
        val low = 0xFF111111L; val mid = 0xFF999999L
        assertEquals(IndicatorColors.URGENT_RED, IndicatorColors.alertArgb(IndicatorSeverity(1f, true), low, mid))
        assertEquals(mid, IndicatorColors.alertArgb(IndicatorSeverity(0f, false), low, mid))
        assertEquals(low, IndicatorColors.alertArgb(IndicatorSeverity(1f, false), low, mid))
    }

    @Test fun PAL_24_w55_theSimulatedPreviewReadingIsLostAtZeroForSignalOnly() {
        assertEquals(ReadingKind.Off, IndicatorPreviewReading.of(IndicatorMetric.Signal, 0f).kind)
        assertEquals(ReadingKind.Live, IndicatorPreviewReading.of(IndicatorMetric.Battery, 0f).kind)
        assertTrue(IndicatorSeverity.of(IndicatorMetric.Battery, IndicatorPreviewReading.of(IndicatorMetric.Battery, 0.03f)).urgent)
    }
}
