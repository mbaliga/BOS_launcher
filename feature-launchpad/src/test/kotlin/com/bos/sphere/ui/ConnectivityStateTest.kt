package com.bos.sphere.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 34 (connectivity): the pure model; no Android involved. */
class ConnectivityStateTest {
    @Test fun CON_01_noSimHidesTheMobileRow() {
        assertTrue(ConnectivityFixtures.snap().showMobileRow)
        assertFalse(ConnectivityFixtures.snap(hasSim = false, carrier = null).showMobileRow)
    }

    @Test fun CON_02_theMobileRowIsLabelledWithTheCarrierElseMobileData() {
        assertEquals("Airtel", ConnectivityFixtures.snap().mobileLabel)
        assertEquals("Mobile data", ConnectivityFixtures.snap(carrier = null).mobileLabel)
        assertEquals("Mobile data", ConnectivityFixtures.snap(carrier = "  ").mobileLabel)
    }

    @Test fun CON_03_airplaneModeShowsMobileOffAndSaysSo() {
        val s = ConnectivityFixtures.snap(airplane = true, mobile = true, connected = false)
        assertFalse(s.mobileOn)
        assertEquals(ConnectivityNote.Airplane, s.note)
    }

    @Test fun CON_04_wifiOffKeepsTheListDimmedWithTheLastKnownNetworkFirst() {
        val s = ConnectivityFixtures.snap(wifiOn = false, networks = listOf(WifiNetworkRow("B"), WifiNetworkRow("A", current = true)))
        assertTrue(s.listDimmed); assertFalse(s.wifiOn)
        assertEquals(listOf("A", "B"), s.visibleNetworks.map { it.name })
        assertFalse(ConnectivityFixtures.snap(wifiOn = true).listDimmed)
    }

    @Test fun CON_05_theListShowsAtMostThreeNetworks() {
        val many = (1..8).map { WifiNetworkRow("N$it", secured = true) }
        assertEquals(3, ConnectivityFixtures.snap(networks = many).visibleNetworks.size)
    }

    @Test fun CON_06_hiddenNamesGiveTheLocationNoteOnlyWhenConnectedWithAnUnnamedNetwork() {
        assertEquals(ConnectivityNote.NamesHidden, ConnectivityFixtures.snap(networks = listOf(WifiNetworkRow(null, current = true))).note)
        assertNull(ConnectivityFixtures.snap().note)
        assertNull(ConnectivityFixtures.snap(connected = false, networks = emptyList()).note)
        assertNull(ConnectivityFixtures.snap(wifiOn = false, connected = false, networks = listOf(WifiNetworkRow(null, current = true))).note)
    }

    @Test fun CON_07_androidsPlaceholderAndQuotesNeverReachTheScreen() {
        assertNull(ConnectivityState.cleanSsid("<unknown ssid>"))
        assertNull(ConnectivityState.cleanSsid(null)); assertNull(ConnectivityState.cleanSsid("\"\""))
        assertEquals("Cafe", ConnectivityState.cleanSsid("\"Cafe\""))
    }
}
