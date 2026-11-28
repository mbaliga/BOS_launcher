package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 22 audit of the push-to-talk edge cases (INP-13) and the ring tier names (PAL-18). */
class VoiceAuditW22Test {
    private class Rec : OnDeviceRecogniser {
        val sessions = mutableListOf<Triple<(String) -> Unit, (String) -> Unit, () -> Unit>>()
        var stops = 0; var released = false
        override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) { sessions += Triple(onPartial, onFinal, onError) }
        override fun stop() { stops++ }
        override fun release() { released = true }
    }

    @Test fun INP_13_aLateCallbackOfAnOlderPressCannotEndTheNewListening() {
        val rec = Rec(); val out = mutableListOf<String>()
        val ptt = PushToTalk(rec, { true }, {}, { out += it })
        ptt.press(); ptt.release()
        ptt.press(); assertTrue(ptt.listening)
        rec.sessions[0].third() // the first recogniser reports an error late
        assertTrue("the new press is still listening", ptt.listening)
        rec.sessions[0].second("old words")
        assertTrue(out.isEmpty())
        rec.sessions[1].second("new words")
        assertEquals(listOf("new words"), out); assertFalse(ptt.listening)
    }

    @Test fun INP_13_theFinalResultOfTheReleasedPressStillArrives() {
        val rec = Rec(); val out = mutableListOf<String>()
        val ptt = PushToTalk(rec, { true }, {}, { out += it })
        ptt.press(); ptt.release(); rec.sessions[0].second("maps")
        assertEquals(listOf("maps"), out)
    }

    @Test fun INP_13_nothingReachesTheFieldAfterTheControlLeavesTheScreen() {
        val rec = Rec(); val out = mutableListOf<String>(); val seen = mutableListOf<Boolean>()
        val ptt = PushToTalk(rec, { true }, {}, { out += it }, { seen += it })
        ptt.press(); ptt.dispose()
        rec.sessions[0].first("late"); rec.sessions[0].second("later")
        assertTrue(out.isEmpty()); assertTrue(rec.released)
        ptt.press() // a disposed control never listens again
        assertEquals(1, rec.sessions.size)
        assertEquals(listOf(true, false), seen)
    }

    @Test fun INP_13_silenceNeverClearsWhatIsInTheField() {
        val rec = Rec(); val out = mutableListOf<String>()
        val ptt = PushToTalk(rec, { true }, {}, { out += it })
        ptt.press(); rec.sessions[0].first(""); rec.sessions[0].second("  ")
        assertTrue("no blank text is sent: $out", out.isEmpty()); assertFalse(ptt.listening)
    }

    @Test fun INP_13_aPressThatFindsNoPermissionAsksOnceAndTheNextPressAfterAGrantListens() {
        val rec = Rec(); var granted = false; var asked = 0
        val ptt = PushToTalk(rec, { granted }, { asked++ }, {})
        ptt.press(); ptt.press()
        assertEquals(2, asked); assertEquals(0, rec.sessions.size); assertFalse(ptt.listening)
        granted = true; ptt.press()
        assertEquals(1, rec.sessions.size); assertTrue(ptt.listening)
    }

    @Test fun INP_13_theRefusedMicrophoneMessageIsTranslatedInAllEightLanguages() {
        val key = "Microphone is off. Allow it in Settings to speak to search."
        try {
            for (lang in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
                L10n.setLanguage(lang)
                assertNotEquals("$lang is missing", key, L10n.tr(key))
            }
        } finally { L10n.setLanguage("en") }
    }

    @Test fun PAL_18_everyRingTierNameOfARestingAndOfATypedOrbitIsTranslatedInAllEightLanguages() {
        try {
            for (lang in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) {
                L10n.setLanguage(lang)
                for (n in RadarRings.tierNames + RadarRings.queryTierNames) assertNotEquals("$n has no $lang name", n, L10n.tr(n))
            }
        } finally { L10n.setLanguage("en") }
    }
}
