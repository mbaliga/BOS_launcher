package com.bos.sphere.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager

/** One Wi-Fi network in the sheet's list. [name] is null when Android hides it from us; [current] is the connected one (or the last known when Wi-Fi is off). */
data class WifiNetworkRow(val name: String?, val secured: Boolean = false, val current: Boolean = false)

/** The quiet one-line explanation under the list, when there is one. */
enum class ConnectivityNote(val english: String) {
    Airplane("Airplane mode is on"),
    NamesHidden("Android hides network names and nearby networks from apps without location access. See all shows them."),
}

/** What the platform says right now. The only input of [ConnectivityState]; produced by a [ConnectivitySource]. */
data class ConnectivitySnapshot(
    val hasSim: Boolean,
    val carrier: String?,
    val mobileDataOn: Boolean,
    val wifiOn: Boolean,
    val airplane: Boolean,
    val wifiConnected: Boolean,
    val networks: List<WifiNetworkRow>,
)

/**
 * Wave 34 (connectivity): the pure model of the Connectivity sheet. What is drawn is derived from a [ConnectivitySnapshot], so every state of
 * the platform (no SIM, Wi-Fi off, airplane mode, names hidden) is a plain function of the input and needs no Android to test.
 */
data class ConnectivityState(val snapshot: ConnectivitySnapshot) {
    /** The mobile row is hidden without a SIM. */
    val showMobileRow: Boolean get() = snapshot.hasSim
    /** The row's label: the carrier's name, else "Mobile data" (a localised key the sheet translates). */
    val mobileLabel: String get() = snapshot.carrier?.takeIf { it.isNotBlank() } ?: MOBILE_FALLBACK
    /** Airplane mode switches the radios off, so the toggle shows off whatever the data setting. */
    val mobileOn: Boolean get() = snapshot.mobileDataOn && !snapshot.airplane
    val wifiOn: Boolean get() = snapshot.wifiOn
    /** With Wi-Fi off the list stays, dimmed, with the last known network (the owner's mock-up). */
    val listDimmed: Boolean get() = !snapshot.wifiOn
    /** The connected (or last known) network first, then the rest, at most [MAX_VISIBLE]. */
    val visibleNetworks: List<WifiNetworkRow> get() = (snapshot.networks.filter { it.current } + snapshot.networks.filter { !it.current }).take(MAX_VISIBLE)
    val note: ConnectivityNote? get() = when {
        snapshot.airplane -> ConnectivityNote.Airplane
        snapshot.wifiOn && snapshot.wifiConnected && snapshot.networks.none { !it.name.isNullOrBlank() } -> ConnectivityNote.NamesHidden
        else -> null
    }

    companion object {
        const val MAX_VISIBLE = 3
        const val MOBILE_FALLBACK = "Mobile data"
        /** The Android placeholder for a hidden SSID, and quotes around a real one, normalised to a name or null. */
        fun cleanSsid(raw: String?): String? {
            val s = raw?.trim()?.removeSurrounding("\"").orEmpty()
            return s.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != WifiManager.UNKNOWN_SSID }
        }
    }
}

/** Reads the platform. The real one is [AndroidConnectivitySource]; tests fake it. */
fun interface ConnectivitySource { fun read(): ConnectivityState }

/** What the sheet's buttons do. Apps cannot toggle mobile data or Wi-Fi on Android 12+, so every one of them hands over to the system. */
interface ConnectivityActions {
    /** The mobile-data toggle: the system Internet panel. */
    fun openMobilePanel()
    /** The Wi-Fi toggle and "See all": the system Wi-Fi panel. */
    fun openWifiPanel()
    /** The tune icon of the connected network: the system Wi-Fi settings. */
    fun openWifiSettings()
    /** "Share Wi-Fi": there is no public sharing action, so this is the Wi-Fi settings, where the network's Share button is. */
    fun shareWifi()
}

/** Public APIs only: ConnectivityManager and NetworkCapabilities, WifiManager (ACCESS_WIFI_STATE), TelephonyManager state calls that need no permission. */
class AndroidConnectivitySource(context: Context) : ConnectivitySource {
    private val ctx = context.applicationContext
    @Volatile private var lastKnown: WifiNetworkRow? = null

    @Suppress("DEPRECATION")
    override fun read(): ConnectivityState {
        val cm = runCatching { ctx.getSystemService(ConnectivityManager::class.java) }.getOrNull()
        val tm = runCatching { ctx.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val wm = runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java) }.getOrNull()
        val airplane = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
        val telephony = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val simState = runCatching { tm?.simState }.getOrNull()
        val hasSim = telephony && simState != null && simState != TelephonyManager.SIM_STATE_ABSENT && simState != TelephonyManager.SIM_STATE_UNKNOWN
        val carrier = if (hasSim) runCatching { tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: tm?.simOperatorName?.takeIf { it.isNotBlank() } }.getOrNull() else null
        val caps = runCatching { cm?.allNetworks?.mapNotNull { cm.getNetworkCapabilities(it) } }.getOrNull().orEmpty()
        // The data setting itself needs READ_PHONE_STATE; a live cellular network with Internet is the public evidence that mobile data is on.
        val dataOn = caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
        val wifiOn = runCatching { wm?.isWifiEnabled }.getOrNull() ?: caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        val wifiCaps = caps.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        val connected = wifiCaps != null
        if (connected) {
            val info = wifiCaps?.transportInfo as? WifiInfo
            // Without location access Android reports "<unknown ssid>": the row is then drawn without a name.
            lastKnown = WifiNetworkRow(ConnectivityState.cleanSsid(info?.ssid), secured = true, current = true)
        }
        val list = if (connected || (!wifiOn)) listOfNotNull(lastKnown?.copy(current = true)) else emptyList()
        return ConnectivityState(ConnectivitySnapshot(hasSim, carrier, dataOn, wifiOn, airplane, connected, list))
    }
}

/** Opens the system's public panels and settings screens, falling back to the plain Wi-Fi settings, and does nothing (never throws) when nothing handles them. */
class AndroidConnectivityActions(private val ctx: Context) : ConnectivityActions {
    private fun start(vararg actions: String) {
        for (a in actions) if (runCatching { ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
    override fun openMobilePanel() = start(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)
    override fun openWifiPanel() = start(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
    override fun openWifiSettings() = start(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    override fun shareWifi() = start(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
}
