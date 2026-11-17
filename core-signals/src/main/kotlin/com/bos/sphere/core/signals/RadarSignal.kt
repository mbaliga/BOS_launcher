package com.bos.sphere.core.signals

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.Dispatchers

/** PAL-24: how the phone is connected, for the Radar background. */
enum class SignalKind { None, Wifi, Cellular, Ethernet }

/**
 * PAL-24: the connectivity signal as the Radar background sees it. [level] 0..4 (0 = no connectivity, 1 weak,
 * 2 fair, 3 good, 4 excellent); [validated] is the system's "this network reaches the internet" verdict.
 */
data class SignalState(val level: Int, val kind: SignalKind, val validated: Boolean) {
    init { require(level in 0..4) { "level $level" } }
    val hasSignal: Boolean get() = level > 0

    companion object {
        val NoSignal = SignalState(0, SignalKind.None, false)
        fun forced(level: Int, kind: SignalKind = SignalKind.Wifi): SignalState =
            if (level <= 0) NoSignal else SignalState(level.coerceAtMost(4), kind, true)
    }
}

/** PAL-24: pure normalisation and wording; no Android types so the JVM tests run it directly. */
object SignalLogic {
    const val UNSPECIFIED: Int = Int.MIN_VALUE

    /** Level 1..4 for a measured strength in dBm (Wi-Fi RSSI thresholds or cellular RSRP-like thresholds). */
    fun levelFromDbm(kind: SignalKind, dbm: Int): Int = when (kind) {
        SignalKind.Wifi -> when { dbm >= -55 -> 4; dbm >= -65 -> 3; dbm >= -75 -> 2; else -> 1 }
        SignalKind.Cellular -> when { dbm >= -85 -> 4; dbm >= -95 -> 3; dbm >= -105 -> 2; else -> 1 }
        SignalKind.Ethernet -> 4
        SignalKind.None -> 0
    }

    /**
     * A default network becomes a [SignalState]. No network = level 0. A network the system could not validate
     * (captive portal, no internet) is capped at weak (1). An unreported strength reads as good (3), wired as 4.
     */
    fun normalise(kind: SignalKind, dbm: Int, validated: Boolean): SignalState {
        if (kind == SignalKind.None) return SignalState.NoSignal
        val raw = if (dbm == UNSPECIFIED || dbm == 0) (if (kind == SignalKind.Ethernet) 4 else 3) else levelFromDbm(kind, dbm)
        return SignalState(if (validated) raw else minOf(raw, 1), kind, validated)
    }

    /** English level word ("none", "weak", "fair", "good", "excellent"); the UI translates it. */
    fun levelWord(level: Int): String = when (level.coerceIn(0, 4)) { 0 -> "none"; 1 -> "weak"; 2 -> "fair"; 3 -> "good"; else -> "excellent" }

    fun kindWord(kind: SignalKind): String = when (kind) { SignalKind.Wifi -> "Wi-Fi"; SignalKind.Cellular -> "Mobile data"; SignalKind.Ethernet -> "Wired"; SignalKind.None -> "Offline" }

    /**
     * The same debounce the live source uses, as a pure function over timestamped values (ms): a value is emitted when
     * no newer one arrives within [windowMs]; the first is emitted at once; repeats are dropped.
     */
    fun <T> settle(events: List<Pair<Long, T>>, windowMs: Long): List<T> {
        val out = ArrayList<T>()
        events.forEachIndexed { i, (at, v) ->
            val next = events.getOrNull(i + 1)?.first
            val stable = i == 0 || next == null || next - at >= windowMs
            if (stable && (out.isEmpty() || out.last() != v)) out += v
        }
        return out
    }
}

/** PAL-24: where the Radar background gets its signal; tests inject [FixedSignalSource]. */
interface SignalSource {
    /** Cold flow: collecting registers the callback, cancelling unregisters it. Collect only while Radar is visible. */
    fun states(): Flow<SignalState>
}

class FixedSignalSource(private val state: SignalState) : SignalSource {
    override fun states(): Flow<SignalState> = flow { emit(state) }
}

/**
 * The phone's real signal through public API only: the default-network callback and its NetworkCapabilities
 * (transport, validated, signalStrength). No permission beyond ACCESS_NETWORK_STATE, no traffic of our own. The
 * telephony signal-strength listener is not used: see docs/decisions/radarSignal.md.
 */
class AndroidSignalSource(context: Context, private val debounceMs: Long = 600L) : SignalSource {
    private val app = context.applicationContext

    override fun states(): Flow<SignalState> {
        val raw = callbackFlow {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            if (cm == null) { trySend(SignalState.NoSignal); awaitClose { }; return@callbackFlow }
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(fromCaps(caps)) }
                override fun onLost(network: Network) { trySend(SignalState.NoSignal) }
                override fun onUnavailable() { trySend(SignalState.NoSignal) }
            }
            trySend(runCatching { cm.getNetworkCapabilities(cm.activeNetwork)?.let { fromCaps(it) } }.getOrNull() ?: SignalState.NoSignal)
            val ok = runCatching { cm.registerDefaultNetworkCallback(cb) }.isSuccess
            awaitClose { if (ok) runCatching { cm.unregisterNetworkCallback(cb) } }
        }
        return raw.settled(debounceMs).flowOn(Dispatchers.Default)
    }

    private fun fromCaps(c: NetworkCapabilities): SignalState {
        val kind = when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SignalKind.Wifi
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SignalKind.Cellular
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> SignalKind.Ethernet
            else -> SignalKind.Wifi // VPN / other transports: treat as a generic link
        }
        val validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return SignalLogic.normalise(kind, c.signalStrength, validated)
    }
}

/** First value at once, then each change only after it has been stable for [windowMs]. */
fun Flow<SignalState>.settled(windowMs: Long): Flow<SignalState> {
    val upstream = this
    return flow {
        var first = true
        emitAll(upstream.distinctUntilChanged().transformLatest { v ->
            if (first) first = false else delay(windowMs)
            emit(v)
        })
    }.distinctUntilChanged()
}
