package com.bos.sphere.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the dot grid shows. */
enum class NetworkKind { Good, Poor, Airplane, None }

/** [strength] 0 (unusable) .. 1 (excellent); meaningful for [NetworkKind.Good] / [NetworkKind.Poor]. */
data class NetworkStatus(val kind: NetworkKind, val strength: Float)

/** Pure classification so it can be unit-tested. */
object NetworkStatusMath {
    /** Strength at or above which the connection counts as good. */
    const val GOOD_THRESHOLD = 0.5f

    /** Wi-Fi RSSI (dBm) → 0..1, linear between -90 (0) and -55 (1). */
    fun wifiStrength(rssi: Int): Float = ((rssi + 90) / 35f).coerceIn(0f, 1f)

    /** Cellular SignalStrength level 0..4 → 0..1. */
    fun cellStrength(level: Int): Float = (level.coerceIn(0, 4) / 4f)

    fun classify(
        airplane: Boolean,
        connected: Boolean,
        validated: Boolean,
        wifiRssi: Int?,
        cellLevel: Int?,
        onWifi: Boolean,
    ): NetworkStatus {
        // Airplane mode reads as grey even if Wi-Fi was turned back on.
        if (airplane) return NetworkStatus(NetworkKind.Airplane, 0f)
        if (!connected) return NetworkStatus(NetworkKind.None, 0f)
        val raw = when {
            onWifi && wifiRssi != null -> wifiStrength(wifiRssi)
            !onWifi && cellLevel != null -> cellStrength(cellLevel)
            else -> 0.75f // connected, strength unknown (ethernet, VPN…): assume decent
        }
        // A connection that can't reach the internet is poor however strong the radio is.
        val s = if (validated) raw else minOf(raw, 0.2f)
        return NetworkStatus(if (s >= GOOD_THRESHOLD) NetworkKind.Good else NetworkKind.Poor, s)
    }
}

/**
 * Live [NetworkStatus] from the default network (ConnectivityManager callback), Wi-Fi RSSI,
 * cellular signal level (TelephonyCallback — no permission needed) and airplane mode. [start] /
 * [stop] bracket the observation (the home screen while it is visible).
 */
class NetworkSignal(context: Context) {
    private val ctx = context.applicationContext
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val tm = ctx.getSystemService(TelephonyManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus(NetworkKind.None, 0f))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    @Volatile private var caps: NetworkCapabilities? = null
    @Volatile private var cellLevel: Int? = null
    private var running = false

    private val netCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) { caps = nc; recompute() }
        override fun onLost(network: Network) { caps = null; recompute() }
        override fun onUnavailable() { caps = null; recompute() }
    }

    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            cellLevel = signalStrength.level
            recompute()
        }
    }

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = recompute()
    }

    fun start() {
        if (running) return
        running = true
        runCatching { cm?.registerDefaultNetworkCallback(netCallback) }
        runCatching { tm?.registerTelephonyCallback(ctx.mainExecutor, telephonyCallback) }
        ctx.registerReceiver(airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        recompute()
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { cm?.unregisterNetworkCallback(netCallback) }
        runCatching { tm?.unregisterTelephonyCallback(telephonyCallback) }
        runCatching { ctx.unregisterReceiver(airplaneReceiver) }
    }

    private fun recompute() {
        val nc = caps
        val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        val onWifi = nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val rssi = (nc?.transportInfo as? WifiInfo)?.rssi?.takeIf { it in -126..-1 }
            ?: nc?.signalStrength?.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && onWifi }
        _status.value = NetworkStatusMath.classify(
            airplane = airplane,
            connected = nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifiRssi = rssi,
            cellLevel = cellLevel,
            onWifi = onWifi,
        )
    }
}
