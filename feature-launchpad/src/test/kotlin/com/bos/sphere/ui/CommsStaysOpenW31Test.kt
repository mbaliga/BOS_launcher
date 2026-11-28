package com.bos.sphere.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** W31: opening a notification (or its app) from Comms must not close Comms, so Back from that app returns to it. */
class CommsStaysOpenW31Test {
    private val src = File("src/main/kotlin/com/bos/sphere/ui/Launchpad.kt").readText()

    @Test fun opening_a_notification_hands_off_without_closing_comms() {
        val fn = src.substringAfter("private fun openThrough(").substringBefore("private sealed interface Entry")
        assertTrue("close is a no-op", fn.contains("close = {}"))
        assertFalse("onDismiss is not wired to the hand-off", fn.contains("close = onDismiss"))
    }

    @Test fun opening_an_app_row_does_not_dismiss() {
        assertFalse(src.contains("onOpenApp = { pkg -> onOpenApp(pkg); onDismiss() }"))
        assertFalse(src.contains("if (ok) onDismiss()"))
    }
}
