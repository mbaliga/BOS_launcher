package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactActionsTest {
    @Test fun digits() {
        assertEquals("447911123456", ContactLogic.digits("+44 7911-123 456"))
        assertEquals("", ContactLogic.digits("abc"))
        assertTrue(ContactLogic.hasUsableNumber("+44 7911"))
        assertFalse(ContactLogic.hasUsableNumber("123"))
    }

    @Test fun channelSelection() {
        assertEquals(emptyList<Channel>(), ContactLogic.channels(false, false, true, true, true))
        assertEquals(listOf(Channel.Email), ContactLogic.channels(false, true, true, true, true))
        assertEquals(listOf(Channel.Call, Channel.Sms), ContactLogic.channels(true, false, false, false, false))
        assertEquals(
            listOf(Channel.Call, Channel.Sms, Channel.Email, Channel.WhatsApp, Channel.Signal),
            ContactLogic.channels(true, true, true, false, true),
        )
    }

    @Test fun initials() {
        assertEquals("AL", ContactLogic.initials("Ada Byron Lovelace"))
        assertEquals("B", ContactLogic.initials("bob"))
        assertEquals("?", ContactLogic.initials("  "))
    }
}
