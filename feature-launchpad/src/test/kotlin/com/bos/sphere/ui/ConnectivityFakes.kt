package com.bos.sphere.ui

/** Fakes for the Connectivity sheet's two seams. */
class FakeConnectivityActions : ConnectivityActions {
    val calls = mutableListOf<String>()
    override fun openMobilePanel() { calls += "mobile" }
    override fun openWifiPanel() { calls += "wifiPanel" }
    override fun openWifiSettings() { calls += "wifiSettings" }
    override fun shareWifi() { calls += "share" }
}

object ConnectivityFixtures {
    val coffee = listOf(
        WifiNetworkRow("Third Wave Coffee 5Ghz", secured = true, current = true),
        WifiNetworkRow("Third Wave Coffee 2.3Ghz", secured = true),
        WifiNetworkRow("Third Wave Coffee Guest", secured = true),
    )
    fun snap(
        hasSim: Boolean = true, carrier: String? = "Airtel", mobile: Boolean = true, wifiOn: Boolean = true, airplane: Boolean = false,
        connected: Boolean = true, networks: List<WifiNetworkRow> = coffee,
    ) = ConnectivityState(ConnectivitySnapshot(hasSim, carrier, mobile, wifiOn, airplane, connected, networks))
    /** The owner's mock-up: Airtel on, Wi-Fi shown off, the three Third Wave networks. */
    val mock = snap(wifiOn = false)
}
