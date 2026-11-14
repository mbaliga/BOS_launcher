package com.bos.sphere.core.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

// STR-10: the system state row at the top of Launchpad. Tiles show state; they never claim to toggle
// what Android blocks (Wi-Fi, Bluetooth and mobile data cannot be switched by an app since Android 10).

enum class SystemTileKind(val label: String) { Time("Time"), Battery("Battery"), Connectivity("Connectivity"), Sound("Sound") }

enum class Connectivity { None, Wifi, Cellular, Ethernet, Other }
enum class SoundState { Normal, Vibrate, Silent }

data class SystemSnapshot(
    val now: Long,
    val batteryPercent: Int,
    val charging: Boolean,
    val connectivity: Connectivity,
    val sound: SoundState,
)

data class SystemTile(
    val kind: SystemTileKind,
    /** Main text, e.g. "82%". Time is formatted by the caller from [SystemSnapshot.now], so it is empty here. */
    val value: String,
    /** Second line, e.g. "Charging". */
    val detail: String,
    /** The public system action opened when the tile is tapped. */
    val panelAction: String,
)

object SystemStateLogic {
    // All four are public platform constants (Settings.ACTION_DATE_SETTINGS, Intent.ACTION_POWER_USAGE_SUMMARY,
    // Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.Panel.ACTION_VOLUME); no hidden API is involved.
    const val ACTION_TIME = "android.settings.DATE_SETTINGS"
    const val ACTION_BATTERY = "android.intent.action.POWER_USAGE_SUMMARY"
    const val ACTION_CONNECTIVITY = "android.settings.panel.action.INTERNET_CONNECTIVITY"
    const val ACTION_SOUND = "android.settings.panel.action.VOLUME"

    fun panelFor(kind: SystemTileKind): String = when (kind) {
        SystemTileKind.Time -> ACTION_TIME
        SystemTileKind.Battery -> ACTION_BATTERY
        SystemTileKind.Connectivity -> ACTION_CONNECTIVITY
        SystemTileKind.Sound -> ACTION_SOUND
    }

    fun tiles(s: SystemSnapshot): List<SystemTile> = listOf(
        SystemTile(SystemTileKind.Time, "", "", panelFor(SystemTileKind.Time)),
        SystemTile(
            SystemTileKind.Battery, if (s.batteryPercent in 0..100) "${s.batteryPercent}%" else "",
            if (s.charging) "Charging" else "", panelFor(SystemTileKind.Battery),
        ),
        SystemTile(
            SystemTileKind.Connectivity,
            when (s.connectivity) {
                Connectivity.None -> "Offline"; Connectivity.Wifi -> "Wi-Fi"; Connectivity.Cellular -> "Mobile"
                Connectivity.Ethernet -> "Ethernet"; Connectivity.Other -> "Connected"
            },
            "", panelFor(SystemTileKind.Connectivity),
        ),
        SystemTile(
            SystemTileKind.Sound,
            when (s.sound) { SoundState.Normal -> "Sound on"; SoundState.Vibrate -> "Vibrate"; SoundState.Silent -> "Silent" },
            "", panelFor(SystemTileKind.Sound),
        ),
    )

    /** Reads the state with public APIs only. Needs ACCESS_NETWORK_STATE, which the app already holds. */
    fun read(ctx: Context, now: Long = System.currentTimeMillis()): SystemSnapshot {
        val bat = runCatching { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val conn = runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> Connectivity.None
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connectivity.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connectivity.Cellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connectivity.Ethernet
                else -> Connectivity.Other
            }
        }.getOrDefault(Connectivity.None)
        val sound = runCatching {
            when (ctx.getSystemService(AudioManager::class.java).ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> SoundState.Silent
                AudioManager.RINGER_MODE_VIBRATE -> SoundState.Vibrate
                else -> SoundState.Normal
            }
        }.getOrDefault(SoundState.Normal)
        return SystemSnapshot(now, pct, charging, conn, sound)
    }

    /**
     * An intent for [kind]'s panel. It is not pre-checked with queryIntentActivities: package visibility (Android 11+)
     * would make that return nothing for system settings screens. The caller starts it inside runCatching, so a device
     * with no handler simply does nothing.
     */
    fun intentFor(@Suppress("UNUSED_PARAMETER") ctx: Context, kind: SystemTileKind): Intent? =
        Intent(panelFor(kind)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
