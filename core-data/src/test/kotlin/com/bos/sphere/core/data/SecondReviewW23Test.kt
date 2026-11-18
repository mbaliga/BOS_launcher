package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 23 second review (auditSweep): the late callback of an old listener and the words of an action toast. */
class SecondReviewW23Test {
    @Test fun STR_05_aLateCallbackOfTheOldInstanceDoesNotActForTheNewOne() {
        val slot = BoundSlot<Any>()
        val old = Any(); val new = Any()
        slot.bind(old); assertTrue(slot.owns(old))
        slot.bind(new)
        // the old instance's late onListenerDisconnected (or a last update) is not the bound one's: the feed of the new one stays
        assertFalse(slot.owns(old))
        assertTrue(slot.owns(new))
        slot.release(new); assertFalse(slot.owns(new))
    }

    @Test fun INP_15_aToastWithAPlaceholderIsFilledInTheTranslation() {
        assertEquals("Keine App für Kamera", ActionToastText.of({ if (it == "No app for %s") "Keine App für %s" else it }, "No app for %s", "Kamera"))
        assertEquals("Flashlight on", ActionToastText.of({ it }, "Flashlight on"))
    }

    @Test fun INP_15_aTranslationWithBrokenPlaceholdersFallsBackToTheEnglishWordsAndNeverThrows() {
        assertEquals("No app for Camera", ActionToastText.of({ "Keine App für %d" }, "No app for %s", "Camera"))
        assertEquals("a %q toast without arguments is shown as written", "%q", ActionToastText.of({ "%q" }, "No app for %s"))
        assertEquals("No app for Camera", ActionToastText.of({ throw IllegalStateException() }, "No app for %s", "Camera"))
    }
}
