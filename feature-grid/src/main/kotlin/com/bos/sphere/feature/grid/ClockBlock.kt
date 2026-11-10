package com.bos.sphere.feature.grid

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.WeatherCodes
import com.bos.sphere.core.data.WeatherInfo
import com.bos.sphere.core.design.HyleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure formatting for the clock block (testable). */
object ClockText {
    fun timePattern(format: ClockFormat, system24: Boolean): String = when (format) {
        ClockFormat.Hour12 -> "h:mm"
        ClockFormat.Hour24 -> "HH:mm"
        ClockFormat.System -> if (system24) "HH:mm" else "h:mm"
    }

    /** True when an AM/PM marker should follow the time. */
    fun showsMeridiem(format: ClockFormat, system24: Boolean): Boolean = timePattern(format, system24) == "h:mm"

    fun dateLine(showDay: Boolean, showDate: Boolean): String? = when {
        showDay && showDate -> "EEEE · d MMM"
        showDay -> "EEEE"
        showDate -> "d MMMM"
        else -> null
    }
}

/** Height the block needs, for reserving layout space. */
fun clockBlockHeight(prefs: LauncherPrefs): Dp {
    var h = (prefs.clockSize * 1.22f).dp
    if (prefs.clockLayout == com.bos.sphere.core.data.ClockLayout.Inline) return h
    if (prefs.clockShowDay || prefs.clockShowDate || prefs.clockShowWeather) h += (prefs.clockSize * 0.42f).coerceIn(14f, 28f).dp + 2.dp
    return h
}

/**
 * The home clock: time at the configured size and format, with optional weekday / date / weather
 * on a second line.
 */
@Composable
fun ClockBlock(
    prefs: LauncherPrefs,
    weather: WeatherInfo?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = rememberNow()
    val is24 = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val time = remember(now, prefs.clockFormat, is24) { SimpleDateFormat(ClockText.timePattern(prefs.clockFormat, is24), locale).format(now) }
    val meridiem = remember(now, prefs.clockFormat, is24) {
        if (ClockText.showsMeridiem(prefs.clockFormat, is24)) SimpleDateFormat("a", locale).format(now) else null
    }
    val dateLine = remember(now, prefs.clockShowDay, prefs.clockShowDate) {
        ClockText.dateLine(prefs.clockShowDay, prefs.clockShowDate)?.let { SimpleDateFormat(it, locale).format(now) }
    }
    val size = prefs.clockSize
    if (prefs.clockLayout == com.bos.sphere.core.data.ClockLayout.Inline) {
        // "23:02   18°C": everything on one line, like the mock-ups.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((size * 0.55f).dp),
            modifier = modifier
                .semantics { contentDescription = "Time $time ${meridiem.orEmpty()}" }
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, color = HyleColors.InkBright, fontSize = size.sp, fontWeight = FontWeight.Normal, maxLines = 1)
                if (meridiem != null) Text(meridiem, color = HyleColors.InkDim, fontSize = (size * 0.4f).coerceAtLeast(10f).sp, modifier = androidx.compose.ui.Modifier.padding(start = 3.dp, bottom = (size * 0.1f).dp), maxLines = 1)
            }
            if (dateLine != null) Text(dateLine, color = HyleColors.InkBright, fontSize = size.sp, maxLines = 1)
            if (prefs.clockShowWeather && weather != null) {
                Text(
                    weather.temperature(prefs.weatherFahrenheit) + if (prefs.weatherFahrenheit) "F" else "C",
                    color = HyleColors.InkBright, fontSize = size.sp, maxLines = 1,
                )
            }
        }
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics { contentDescription = "Time $time ${meridiem.orEmpty()}" }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                time,
                color = HyleColors.InkBright,
                fontSize = size.sp,
                fontWeight = if (size >= 28f) FontWeight.Light else FontWeight.Normal,
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )
            if (meridiem != null) {
                Text(
                    meridiem,
                    color = HyleColors.InkDim,
                    fontSize = (size * 0.38f).coerceAtLeast(10f).sp,
                    modifier = androidx.compose.ui.Modifier.padding(start = 4.dp, bottom = (size * 0.12f).dp),
                    maxLines = 1,
                )
            }
        }
        val showWeather = prefs.clockShowWeather && weather != null
        if (dateLine != null || showWeather) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val sub = (size * 0.42f).coerceIn(12f, 24f)
                if (dateLine != null) Text(dateLine, color = HyleColors.Ink, fontSize = sub.sp, maxLines = 1)
                if (showWeather && weather != null) {
                    Text(
                        "${WeatherCodes.glyph(weather.code)} ${weather.temperature(prefs.weatherFahrenheit)}",
                        color = HyleColors.Ink,
                        fontSize = sub.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
