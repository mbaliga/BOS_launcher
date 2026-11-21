package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CFG-02: which provider a recorded widget is re-added from when the same app exists in the main and the work profile. */
class WidgetProviderChoiceTest {
    private data class P(val name: String, val work: Boolean)
    private val c = "com.mail/.W"
    private fun pick(want: Boolean, vararg ps: P) = chooseProvider(c, want, ps.toList()) { (if (it.name == "mail") c else "x/.y") to it.work }

    @Test fun CFG_02_theProfileKindTheExportRecordedWinsWhenBothExist() {
        assertEquals(P("mail", true), pick(true, P("mail", false), P("mail", true)))
        assertEquals(P("mail", false), pick(false, P("mail", true), P("mail", false)))
    }

    @Test fun CFG_02_anAppInOnlyOneProfileIsStillFoundWhateverWasRecorded() {
        assertEquals(P("mail", true), pick(false, P("mail", true), P("other", false)))
        assertEquals(P("mail", false), pick(true, P("mail", false)))
    }

    @Test fun CFG_02_noProviderMeansNoChoice() {
        assertNull(pick(true, P("other", false)))
    }
}
