package com.bos.sphere.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Current conditions. [code] is a WMO weather code (Open-Meteo). */
data class WeatherInfo(val tempC: Float, val code: Int, val fetchedAtMillis: Long) {
    fun temperature(fahrenheit: Boolean): String =
        if (fahrenheit) "${(tempC * 9f / 5f + 32f).roundToInt()}°" else "${tempC.roundToInt()}°"
}

/** Pure WMO-code helpers. */
object WeatherCodes {
    fun glyph(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "☁"
    }

    fun description(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}

/**
 * Opt-in weather for the clock. Uses the last known coarse location and Open-Meteo (no API key);
 * only rounded coordinates leave the device, and only when the user turns weather on. Cached for
 * [MAX_AGE_MS]. Needs INTERNET and ACCESS_COARSE_LOCATION (the latter requested at runtime).
 */
class WeatherSource(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _weather = MutableStateFlow(readCache())
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()
    @Volatile private var inFlight = false

    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Refreshes if the cache is stale (or [force]); safe to call often. */
    @SuppressLint("MissingPermission")
    fun refresh(force: Boolean = false) {
        val cur = _weather.value
        if (!force && cur != null && System.currentTimeMillis() - cur.fetchedAtMillis < MAX_AGE_MS) return
        if (inFlight || !hasPermission()) return
        inFlight = true
        scope.launch {
            try {
                val lm = ctx.getSystemService(LocationManager::class.java)
                val loc = lm?.getProviders(true)?.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                    ?.maxByOrNull { it.time } ?: return@launch
                // ~11 km precision is plenty for a temperature.
                val lat = "%.1f".format(java.util.Locale.US, loc.latitude)
                val lon = "%.1f".format(java.util.Locale.US, loc.longitude)
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
                val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
                try {
                    if (conn.responseCode != 200) return@launch
                    val cur2 = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("current")
                    val info = WeatherInfo(cur2.getDouble("temperature_2m").toFloat(), cur2.getInt("weather_code"), System.currentTimeMillis())
                    _weather.value = info
                    prefs.edit().putFloat("t", info.tempC).putInt("c", info.code).putLong("at", info.fetchedAtMillis).apply()
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // Offline or no fix: keep showing the cached reading.
            } finally {
                inFlight = false
            }
        }
    }

    private fun readCache(): WeatherInfo? =
        if (prefs.contains("at")) WeatherInfo(prefs.getFloat("t", 0f), prefs.getInt("c", 3), prefs.getLong("at", 0L)) else null

    companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
    }
}
