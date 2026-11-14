package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyAndSystemStateTest {
    @Test fun STR_10_everyTileOpensAPublicPanel() {
        val t = SystemStateLogic.tiles(SystemSnapshot(0, 82, true, Connectivity.Wifi, SoundState.Vibrate))
        assertEquals(SystemTileKind.entries, t.map { it.kind })
        assertTrue(t.all { it.panelAction.startsWith("android.") })
        assertEquals("82%", t[1].value); assertEquals("Charging", t[1].detail)
        assertEquals("Wi-Fi", t[2].value); assertEquals("Vibrate", t[3].value)
        assertEquals("android.settings.panel.action.VOLUME", t[3].panelAction)
    }

    @Test fun STR_10_offlineAndUnknownBatteryAreStatedNotHidden() {
        val t = SystemStateLogic.tiles(SystemSnapshot(0, -1, false, Connectivity.None, SoundState.Silent))
        assertEquals("Offline", t[2].value); assertEquals("", t[1].value); assertEquals("Silent", t[3].value)
    }

    @Test fun STR_10_noTileClaimsToToggle() {
        // Tiles are display + open-panel only: the model has no toggle field at all.
        val names = SystemTile::class.java.declaredFields.map { it.name }
        assertFalse(names.any { it.contains("toggle", true) || it.contains("enabled", true) || it.contains("checked", true) })
    }

    @Test fun STR_02_noReplyWithoutIntentOrText() {
        assertNull(ReplyLogic.pick(listOf(HubAction("x"), HubAction("y", reply = ReplyTarget(emptyArray(), "k", null)))))
        assertFalse(ReplyLogic.canSend("  "))
        assertTrue(ReplyLogic.canSend("hi"))
    }
}
