package com.bos.sphere.ui

import com.bos.sphere.core.design.GrantHooks
import com.bos.sphere.core.design.GrantKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantWiringTest {
    @After fun reset() { GrantHooks.interceptor = null }

    @Test fun CFG_07_withoutAnInterceptorTheRequestRunsAtOnce() {
        var ran = 0
        GrantHooks.ask(GrantKind.UsageAccess) { ran++ }
        assertEquals(1, ran)
    }

    @Test fun CFG_07_withAnInterceptorTheExplanationComesFirstAndTheRequestWaitsForIt() {
        val asked = mutableListOf<GrantKind>()
        var proceed: (() -> Unit)? = null
        GrantHooks.interceptor = { k, p -> asked += k; proceed = p }
        var ran = 0
        GrantHooks.ask(GrantKind.Microphone) { ran++ }
        assertEquals(listOf(GrantKind.Microphone), asked)
        assertEquals("nothing runs before the user taps the explanation's button", 0, ran)
        proceed!!.invoke()
        assertEquals(1, ran)
    }

    @Test fun CFG_07_everyGrantHasARowWithItsStateAndActionInThePane() {
        val rows = AccessLogic.rows { it == GrantKind.Contacts }
        assertEquals(GrantKind.entries, rows.map { it.kind })
        assertEquals("On", rows.first { it.kind == GrantKind.Contacts }.state)
        assertEquals("Manage", rows.first { it.kind == GrantKind.Contacts }.action)
        val off = rows.first { it.kind == GrantKind.Folders }
        assertEquals("Off", off.state); assertEquals("Choose folders", off.action)
        assertTrue(rows.all { it.title.isNotBlank() })
    }
}
