package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a link handed to the system may carry. */
class IntentRulesTest {

    @Test
    fun onlyViewIntentsLeave() {
        assertTrue(IntentRules.allowed(IntentRules.VIEW, "https"))
        assertTrue(IntentRules.allowed(IntentRules.VIEW, "mailto"))
        assertTrue(IntentRules.allowed(IntentRules.VIEW, null)) // an intent: URL with no data
        assertTrue(IntentRules.allowed(null, "geo"))
        assertFalse(IntentRules.allowed("android.intent.action.CALL", "tel"))
        assertFalse(IntentRules.allowed("android.intent.action.SEND", null))
    }

    @Test
    fun neverFileOrContentData() {
        // intent:///sdcard/x#Intent;scheme=file;end would throw FileUriExposedException in the launcher.
        for (scheme in listOf("file", "FILE", "content", "android.resource", "javascript", "data")) {
            assertFalse(scheme, IntentRules.allowed(IntentRules.VIEW, scheme))
        }
    }

    @Test
    fun overLongUrlsNeverLeave() {
        assertTrue(IntentRules.urlFits("mailto:" + "a".repeat(8000)))
        // A ~1.5 MB mailto: would fail the binder call inside the launcher.
        assertFalse(IntentRules.urlFits("mailto:" + "a".repeat(1_500_000)))
    }
}

class DialogBudgetTest {

    @Test
    fun aFewDialogsThenCancelUntilTheWindowPasses() {
        val b = DialogBudget(max = 3, windowMillis = 30_000)
        assertTrue(b.take(0))
        assertTrue(b.take(10))
        assertTrue(b.take(20))
        assertFalse(b.take(30)) // alert() in a loop: the fourth is cancelled
        assertFalse(b.take(29_999))
        assertTrue(b.take(30_000))
    }
}

class RateGateTest {

    @Test
    fun aBurstPassesThenTheRateHolds() {
        val g = RateGate(ratePerSecond = 30.0, burst = 60.0, closeAfterDrops = 1_000, dropWindowMillis = 10_000)
        repeat(60) { assertEquals(RateGate.Admit.Ok, g.admit(0)) }
        assertEquals(RateGate.Admit.Drop, g.admit(0))
        // 100 ms later three more tokens have come in.
        repeat(3) { assertEquals(RateGate.Admit.Ok, g.admit(100)) }
        assertEquals(RateGate.Admit.Drop, g.admit(100))
    }

    @Test
    fun aWidgetThatKeepsFloodingIsClosed() {
        val g = RateGate(ratePerSecond = 30.0, burst = 60.0, closeAfterDrops = 100, dropWindowMillis = 10_000)
        repeat(60) { g.admit(0) }
        var last = RateGate.Admit.Ok
        repeat(100) { last = g.admit(1) }
        assertEquals(RateGate.Admit.Close, last)
    }
}

class OpenGateTest {

    @Test
    fun anOpenMustAnswerARecentTapAndUsesItUp() {
        val g = OpenGate(windowMillis = 5_000)
        assertFalse(g.take(1_000)) // no tap yet: a timer can't open anything
        g.tapped(2_000)
        assertTrue(g.take(2_300))
        assertFalse(g.take(2_400)) // one tap, one open
        g.tapped(10_000)
        assertFalse(g.take(15_001)) // too long after the tap
    }
}
