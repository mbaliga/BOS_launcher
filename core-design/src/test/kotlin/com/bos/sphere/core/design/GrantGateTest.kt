package com.bos.sphere.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantGateTest {
    @Test fun CFG_07_grantedShowsFeatureFirstUseExplainsAskedBeforeSaysStillOff() {
        assertEquals(GrantStage.Granted, GrantGateLogic.stage(granted = true, askedBefore = false))
        assertEquals(GrantStage.Granted, GrantGateLogic.stage(granted = true, askedBefore = true))
        assertEquals(GrantStage.Explain, GrantGateLogic.stage(granted = false, askedBefore = false))
        assertEquals(GrantStage.ExplainStillOff, GrantGateLogic.stage(granted = false, askedBefore = true))
    }

    @Test fun CFG_07_everyGrantStatesWhatIsReadAndThatItStaysOnDevice() {
        for (k in GrantKind.entries) {
            val c = GrantGateLogic.copy(k)
            assertTrue("$k reads", c.reads.length > 20 && c.reads.startsWith("Reads") || c.reads.startsWith("Listens"))
            assertTrue("$k stays", c.stays.contains("device", ignoreCase = true))
            assertTrue("$k button", c.button.isNotBlank() && c.stillOff.isNotBlank() && c.title.isNotBlank())
        }
    }

    @Test fun CFG_07_coversNotificationUsageContactsFoldersAndMicrophone() {
        assertEquals(5, GrantKind.entries.size)
        assertTrue(GrantGateLogic.opensSettingsPage(GrantKind.Notifications))
        assertTrue(GrantGateLogic.opensSettingsPage(GrantKind.UsageAccess))
        assertFalse(GrantGateLogic.opensSettingsPage(GrantKind.Contacts))
        assertFalse(GrantGateLogic.opensSettingsPage(GrantKind.Microphone))
    }

    @Test fun CFG_07_noTextIsDuplicatedAcrossKinds() {
        val titles = GrantKind.entries.map { GrantGateLogic.copy(it).title }
        assertEquals(titles.size, titles.toSet().size)
    }
}
