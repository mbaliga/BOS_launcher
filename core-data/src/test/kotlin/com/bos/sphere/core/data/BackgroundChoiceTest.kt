package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-16 (wave 15): the two-slot background model, its rules, the default pair and the migration of stored values. */
class BackgroundChoiceTest {
    @Test fun HOM_16_nineItemsFiveLivingWallpapersAndFourEffects() {
        assertEquals(listOf("beam", "spire", "halo", "laser", "shaft"), BgItem.livingWallpapers.map { it.id })
        assertEquals(listOf("glow-lines", "breathing-dots", "bottom-glow", "radar-sweep"), BgItem.effects.map { it.id })
        assertEquals(10, BgItem.entries.size) // nine items plus None
        assertEquals(BgItem.entries.size, BgItem.entries.map { it.id }.toSet().size)
    }

    @Test fun HOM_16_defaultPairIsSpireAndBreathingDots() {
        assertEquals(BackgroundChoice(BgItem.Spire, BgItem.BreathingDots), BackgroundChoice.DEFAULT)
        assertEquals(BackgroundChoice.DEFAULT, BackgroundChoice.fromStored(null, null))
        assertEquals(BgItem.Spire, BackgroundChoice.DEFAULT.wallpaper)
    }

    @Test fun HOM_16_atMostOneLivingWallpaperAndNoItemTwice() {
        val c = BackgroundChoice(BgItem.Beam, BgItem.GlowLines)
        assertEquals(BackgroundRefusal.TwoWallpapers, c.refusal(2, BgItem.Halo))
        assertEquals(BackgroundRefusal.SameItem, c.refusal(2, BgItem.Beam))
        assertNull(c.refusal(2, BgItem.None)); assertNull(c.refusal(2, BgItem.RadarSweep))
        assertEquals(c, c.with(2, BgItem.Halo)) // refused: unchanged
        assertEquals(BackgroundChoice(BgItem.Beam, BgItem.BottomGlow), c.with(2, BgItem.BottomGlow))
        // Replacing the wallpaper itself is fine: the other slot holds an effect.
        assertTrue(c.isAllowed(1, BgItem.Laser))
        // Two effects, or an effect and a wallpaper, in either order.
        assertTrue(BackgroundChoice(BgItem.GlowLines, BgItem.BreathingDots).isValid)
        assertTrue(BackgroundChoice(BgItem.BottomGlow, BgItem.Shaft).isValid)
        assertFalse(BackgroundChoice(BgItem.Shaft, BgItem.Spire).isValid)
        assertTrue(BackgroundChoice(BgItem.None, BgItem.None).isValid)
    }

    @Test fun HOM_16_noneIsValidInEachSlot() {
        assertEquals(emptyList<BgItem>(), BackgroundChoice(BgItem.None, BgItem.None).items)
        assertEquals(listOf(BgItem.Halo), BackgroundChoice(BgItem.None, BgItem.Halo).items)
        assertNull(BackgroundChoice(BgItem.GlowLines, BgItem.None).wallpaper)
    }

    @Test fun HOM_16_storedIdsMapAndUnknownIdsFallBack() {
        assertEquals(BackgroundChoice(BgItem.Laser, BgItem.RadarSweep), BackgroundChoice.fromStored("laser", "radar-sweep"))
        assertEquals(BackgroundChoice(BgItem.Spire, BgItem.None), BackgroundChoice.fromStored("nope", "none"))
        assertEquals(BackgroundChoice(BgItem.Halo, BgItem.None), BackgroundChoice.fromStored("halo", "beam")) // rule broken: second slot emptied
        assertEquals(BackgroundChoice(BgItem.Beam, BgItem.BreathingDots), BackgroundChoice.fromStored("beam", null))
    }
}

/** CFG-03, HOM-16: the schema 3 step drops the settings of the removed background gallery; imports of old files are accepted. */
class BackgroundMigrationTest {
    @Test fun CFG_03_aVersion2FileWithOldBackgroundSettingsImportsAsTheDefaultPairAndKeepsTheRest() {
        val doc = StateDocument.parse(StateTestSupport.resource("state_v2.json"))
        val migrated = StateMigrations.toCurrent(doc)
        assertEquals(listOf(2), migrated.steps)
        val prefs = migrated.doc.json.getJSONObject("settings").getJSONObject("prefs")
        RetiredBackgroundKeys.KEYS.forEach { assertFalse("$it dropped", prefs.has(it)) }
        assertEquals(2, migrated.doc.json.getJSONObject("settings").getInt("settingsVersion"))
        assertEquals("keep-me-v2", prefs.getString("flavour"))
        assertEquals(2, migrated.doc.json.getJSONObject("futureThing").getInt("a"))
        val dec = StateCodec.decode(doc)
        assertEquals(BackgroundChoice.DEFAULT, BackgroundChoice(dec.parts.prefs.backgroundSlot1, dec.parts.prefs.backgroundSlot2))
        assertEquals(RadarIntensity.Subtle, dec.parts.prefs.radarIntensity) // the one wave 14 value that lives on
        assertEquals(HomeStyle.List, dec.parts.prefs.homeStyle)
    }

    @Test fun CFG_03_retiredKeysNeverComeBackThroughTheUnknownKeyPassThrough() {
        val dec = StateCodec.decode(StateDocument.parse(StateTestSupport.resource("state_v2.json")))
        val again = StateCodec.export(dec.parts, 1L).json.getJSONObject("settings").getJSONObject("prefs")
        RetiredBackgroundKeys.KEYS.forEach { assertFalse("$it re-exported", again.has(it)) }
        assertEquals("keep-me-v2", again.getString("flavour"))
        assertEquals("spire", again.getString("bgSlot1")); assertEquals("breathing-dots", again.getString("bgSlot2"))
    }

    @Test fun CFG_03_aNewExportRoundTripsTheTwoSlots() {
        val parts = StateCodec.decode(StateDocument.parse(StateTestSupport.resource("state_v2.json"))).parts
        val chosen = parts.copy(prefs = parts.prefs.copy(backgroundSlot1 = BgItem.Halo, backgroundSlot2 = BgItem.RadarSweep))
        val back = StateCodec.decode(StateDocument.parse(StateCodec.export(chosen, 1L).encode())).parts.prefs
        assertEquals(BgItem.Halo, back.backgroundSlot1); assertEquals(BgItem.RadarSweep, back.backgroundSlot2)
    }

    @Test fun CFG_03_theSchemaVersionsWereBumpedAndTheSettingsDocumentToo() {
        assertEquals(3, StateSchema.CURRENT); assertEquals(2, StateSchema.SETTINGS_CURRENT)
    }
}
