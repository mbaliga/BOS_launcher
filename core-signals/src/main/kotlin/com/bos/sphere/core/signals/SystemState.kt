package com.bos.sphere.core.signals

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

data class BatteryState(val percent: Int, val charging: Boolean)

enum class LinkKind { None, Wifi, Cellular, Ethernet, Other }

data class ConnectivityState(val kind: LinkKind, val validated: Boolean)

object SystemStateLogic {
    fun battery(level: Int, scale: Int, status: Int): BatteryState {
        val pct = if (scale > 0 && level >= 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryState(pct, charging)
    }

    fun describe(b: BatteryState): String = if (b.percent < 0) "Battery unknown" else "${b.percent}%" + if (b.charging) " charging" else ""

    fun describe(c: ConnectivityState): String = when (c.kind) {
        LinkKind.None -> "Offline"
        LinkKind.Wifi -> if (c.validated) "Wi-Fi" else "Wi-Fi, no internet"
        LinkKind.Cellular -> if (c.validated) "Mobile data" else "Mobile data, no internet"
        LinkKind.Ethernet -> "Wired"
        LinkKind.Other -> "Connected"
    }
}

