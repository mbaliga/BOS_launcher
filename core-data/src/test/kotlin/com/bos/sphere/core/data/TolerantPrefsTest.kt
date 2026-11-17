package com.bos.sphere.core.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** NFR-09 (wave 18): a key of the wrong type reads as its default, never as a ClassCastException out of a store constructor. */
class TolerantPrefsTest {
    /** A preference file as the platform's implementation behaves: a typed getter casts the stored value. */
    private fun platformLike(values: Map<String, Any>): SharedPreferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, a ->
        when (m.name) {
            "getString" -> (values[a[0]] ?: a[1]) as String?
            "getInt" -> (values[a[0]] ?: a[1]) as Int
            "getLong" -> (values[a[0]] ?: a[1]) as Long
            "getFloat" -> (values[a[0]] ?: a[1]) as Float
            "getBoolean" -> (values[a[0]] ?: a[1]) as Boolean
            "getStringSet" -> (values[a[0]] ?: a[1])
            "contains" -> values.containsKey(a[0])
            else -> null
        }
    } as SharedPreferences

    @Test fun NFR_09_everyTypedGetterFallsBackToTheDefaultForAKeyOfAnotherType() {
        val sp = TolerantPrefs(platformLike(mapOf("s" to 1, "i" to "x", "l" to 1, "f" to "x", "b" to "x", "set" to "x")))
        assertEquals("d", sp.getString("s", "d"))
        assertEquals(7, sp.getInt("i", 7))
        assertEquals(8L, sp.getLong("l", 8L))
        assertEquals(1.5f, sp.getFloat("f", 1.5f), 0f)
        assertTrue(sp.getBoolean("b", true))
        assertNull(sp.getStringSet("set", null))
    }

    @Test fun NFR_09_aKeyOfTheRightTypeIsReadAsBefore() {
        val sp = TolerantPrefs(platformLike(mapOf("s" to "v", "i" to 3, "b" to true)))
        assertEquals("v", sp.getString("s", "d")); assertEquals(3, sp.getInt("i", 0)); assertTrue(sp.getBoolean("b", false))
        assertEquals("missing keys still give the default", 9, sp.getInt("none", 9))
    }
}
