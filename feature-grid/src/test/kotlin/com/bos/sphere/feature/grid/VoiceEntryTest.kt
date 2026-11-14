package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEntryTest {
    private class Fake : OnDeviceRecogniser {
        var starts = 0; var stops = 0; var released = false
        lateinit var partial: (String) -> Unit; lateinit var final: (String) -> Unit; lateinit var err: () -> Unit
        override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) { starts++; partial = onPartial; final = onFinal; err = onError }
        override fun stop() { stops++ }
        override fun release() { released = true }
    }

    private fun make(permission: Boolean, asked: MutableList<Unit> = mutableListOf(), out: MutableList<String> = mutableListOf(), rec: Fake = Fake()) =
        PushToTalk(rec, { permission }, { asked += Unit }, { out += it }) to rec

    @Test fun INP_13_the_control_is_hidden_unless_an_on_device_recogniser_exists() {
        assertFalse(PushToTalk.visible(false)); assertFalse(PushToTalk.visible(null)); assertTrue(PushToTalk.visible(true))
        assertEquals(VoiceState.Hidden, PushToTalk.state(visible = false, permission = true, listening = false))
        assertEquals(VoiceState.Idle, PushToTalk.state(true, true, false))
        assertEquals(VoiceState.NeedsPermission, PushToTalk.state(true, false, false))
        assertEquals(VoiceState.Listening, PushToTalk.state(true, true, true))
    }

    @Test fun INP_13_nothing_listens_until_pressed() {
        val (_, rec) = make(permission = true)
        assertEquals(0, rec.starts) // constructing the control and showing it never starts the recogniser
    }

    @Test fun INP_13_a_press_without_permission_asks_and_does_not_listen() {
        val asked = mutableListOf<Unit>()
        val (ptt, rec) = make(permission = false, asked = asked)
        ptt.press()
        assertEquals(1, asked.size); assertEquals(0, rec.starts); assertFalse(ptt.listening)
    }

    @Test fun INP_13_press_listens_release_stops_and_text_reaches_the_palette() {
        val out = mutableListOf<String>()
        val (ptt, rec) = make(permission = true, out = out)
        ptt.press(); assertTrue(ptt.listening); assertEquals(1, rec.starts)
        rec.partial("open cam")
        ptt.release(); assertFalse(ptt.listening); assertEquals(1, rec.stops)
        rec.final("open camera")
        assertEquals(listOf("open cam", "open camera"), out)
    }

    @Test fun INP_13_final_result_error_and_dispose_all_stop_listening() {
        val (a, ra) = make(true); a.press(); ra.final("x"); assertFalse(a.listening)
        val (b, rb) = make(true); b.press(); rb.err(); assertFalse(b.listening)
        val (c, rc) = make(true); c.press(); c.dispose(); assertFalse(c.listening); assertTrue(rc.released)
    }

    @Test fun INP_13_a_second_press_while_listening_does_not_start_again_and_toggle_flips() {
        val (ptt, rec) = make(true)
        ptt.press(); ptt.press(); assertEquals(1, rec.starts)
        ptt.toggle(); assertFalse(ptt.listening)
        ptt.toggle(); assertTrue(ptt.listening); assertEquals(2, rec.starts)
    }
}
