package com.bos.sphere.feature.grid

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.HealthFormat
import com.bos.sphere.core.data.HealthSourcePref
import com.bos.sphere.core.data.HealthToday
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.NetworkKind
import com.bos.sphere.core.data.NetworkSignal
import com.bos.sphere.core.data.StepsSource
import com.bos.sphere.core.data.StripItem
import com.bos.sphere.core.data.StripZone
import com.bos.sphere.core.data.WeatherInfo
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.delay

/** Height the strip needs: tall enough for the clock when it is shown, else a single line. */
fun stripHeight(prefs: LauncherPrefs): Dp =
    if (StripItem.Time in prefs.strip) clockBlockHeight(prefs) else 26.dp

/**
 * The status strip — a Nintendo-3DS-style bar of small readouts. Items are assigned to the left,
 * centre or right zone in settings; the clock is one of them (at the configured size), the rest are
 * compact glyph + value chips (battery, signal, steps, alarm, ring mode, storage, memory,
 * notifications). Zones keep clear of the corner icons via [sidePadding].
 */
@Composable
fun StatusStrip(
    prefs: LauncherPrefs,
    weather: WeatherInfo?,
    health: HealthToday?,
    sidePadding: Dp,
    onClockTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** Height of the corner icons' row, so the strip is vertically centred on them. */
    rowHeight: Dp = 0.dp,
) {
    // One layer per zone over the full width: the centre zone is truly centred and none is squeezed
    // into a third (a big clock needs the room); left / right hug their edges.
    Box(modifier.fillMaxWidth().padding(horizontal = sidePadding).heightIn(min = rowHeight)) {
        for (zone in StripZone.entries) {
            val items = StripItem.entries.filter { prefs.strip[it] == zone }
            if (items.isEmpty()) continue
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (zone == StripZone.Centre && StripItem.Time in items) {
                    // Put the time's own line on the corner icons' centre line (the date / weather hang below it).
                    val timeLine = (prefs.clockSize * 1.22f).dp
                    Modifier.align(Alignment.TopCenter).padding(top = ((rowHeight - timeLine) / 2).coerceAtLeast(0.dp))
                } else Modifier.align(
                    when (zone) {
                        StripZone.Left -> Alignment.CenterStart
                        StripZone.Centre -> Alignment.Center
                        StripZone.Right -> Alignment.CenterEnd
                    },
                ),
            ) {
                items.forEach { item ->
                    if (item == StripItem.Time) ClockBlock(prefs, weather, onClockTap) else StripChip(item, prefs, health)
                }
            }
        }
    }
}

/** A small glyph + value readout. */
@Composable
private fun StripChip(item: StripItem, prefs: LauncherPrefs, health: HealthToday?) {
    val v = rememberStripValue(item, prefs, health)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "${item.label} ${v.text}" },
    ) {
        Icon(v.glyph, null, tint = HyleColors.Ink, modifier = Modifier.size(14.dp))
        Text(v.text, color = HyleColors.Ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
    }
}

internal data class StripValue(val glyph: ImageVector, val text: String)

@Composable
private fun rememberStripValue(item: StripItem, prefs: LauncherPrefs, health: HealthToday?): StripValue {
    val imperial = HealthFormat.usesImperial(java.util.Locale.getDefault().country)
    val hc = prefs.healthSource == HealthSourcePref.HealthConnect
    return when (item) {
        StripItem.Steps -> StripValue(Glyphs.Steps, if (hc) health?.let { HealthFormat.steps(it.steps) } ?: "…" else rememberStepsText())
        StripItem.Floors -> StripValue(Glyphs.Up, health?.let { HealthFormat.floors(it.floors) } ?: "—")
        StripItem.Elevation -> StripValue(Glyphs.Up, health?.let { HealthFormat.elevation(it.elevationM, imperial) } ?: "—")
        StripItem.Distance -> StripValue(Glyphs.Swipe, health?.let { HealthFormat.distance(it.distanceM, imperial) } ?: "—")
        StripItem.Calories -> StripValue(Glyphs.Bolt, health?.let { HealthFormat.kcal(it.activeKcal) } ?: "—")
        else -> rememberBasicStripValue(item)
    }
}

@Composable
private fun rememberBasicStripValue(item: StripItem): StripValue = when (item) {
    StripItem.Battery -> StripValue(Glyphs.Battery, rememberComplicationValue(ComplicationKind.Battery).text)
    StripItem.NextAlarm -> StripValue(Glyphs.Clock, rememberComplicationValue(ComplicationKind.NextAlarm).text)
    StripItem.Ringer -> StripValue(Glyphs.Ring, rememberComplicationValue(ComplicationKind.Ringer).text)
    StripItem.Storage -> StripValue(Glyphs.Storage, rememberComplicationValue(ComplicationKind.Storage).text)
    StripItem.Notifications -> StripValue(Glyphs.Bell, rememberComplicationValue(ComplicationKind.Notifications).text)
    StripItem.Signal -> StripValue(Glyphs.Signal, rememberSignalText())
    StripItem.Memory -> StripValue(Glyphs.Memory, rememberMemoryText())
    else -> StripValue(Glyphs.Clock, "")
}

@Composable
private fun rememberSignalText(): String {
    val context = LocalContext.current
    val source = remember { NetworkSignal(context) }
    DisposableEffect(source) { source.start(); onDispose { source.stop() } }
    val st by source.status.collectAsStateWithLifecycle()
    return when (st.kind) {
        NetworkKind.Airplane -> "Airplane"
        NetworkKind.None -> "Offline"
        else -> "${(st.strength * 100).toInt()}%"
    }
}

@Composable
private fun rememberStepsText(): String {
    val context = LocalContext.current
    val source = remember { StepsSource(context) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Re-try on resume so the counter starts right after the permission dialog closes.
    DisposableEffect(source, owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) source.start() }
        owner.lifecycle.addObserver(obs)
        source.start()
        onDispose { owner.lifecycle.removeObserver(obs); source.stop() }
    }
    val steps by source.steps.collectAsStateWithLifecycle()
    return when {
        !source.available -> "—"
        !source.hasPermission() -> "Allow"
        else -> steps?.toString() ?: "…"
    }
}

@Composable
private fun rememberMemoryText(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            text = "${(100 - info.availMem * 100 / info.totalMem.coerceAtLeast(1)).toInt()}%"
            delay(5_000)
        }
    }
    return text
}

/** Places the strip at the top or bottom (or hides it) per [LauncherPrefs.clockPosition], centred on the corner row. */
@Composable
fun BoxScope.StripHost(
    prefs: LauncherPrefs,
    weather: WeatherInfo?,
    health: HealthToday?,
    onClockTap: () -> Unit,
) {
    val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
    val bubble = prefs.cornerStyle != com.bos.sphere.core.data.CornerStyle.Glyph
    val edge = if (bubble) 10.dp else 6.dp
    val rowH = prefs.cornerIconSize.dp + 26.dp
    val side = prefs.cornerIconSize.dp + 26.dp
    when (prefs.clockPosition) {
        com.bos.sphere.core.data.ClockPosition.Top -> StatusStrip(
            prefs, weather, health, side, onClockTap, rowHeight = rowH,
            modifier = Modifier.align(Alignment.TopCenter).androidx_windowInsetsPadding(safe).padding(top = edge),
        )
        com.bos.sphere.core.data.ClockPosition.Bottom -> StatusStrip(
            // Not centred on the corner icons here: it stacks above the page dots, which sit on the bottom edge.
            // HOM-20: weather line dropped, then the clock scaled, so clock and dots fit the margin band.
            bottomClockFit(prefs).let { f -> prefs.copy(clockSize = prefs.clockSize * f.clockScale, clockShowWeather = prefs.clockShowWeather && f.showWeather) },
            weather, health, side, onClockTap, rowHeight = 0.dp,
            modifier = Modifier.align(Alignment.BottomCenter).androidx_windowInsetsPadding(safe).padding(bottom = ClockBandFit.clockBottomDp(prefs.pagingFor(androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }) != com.bos.sphere.core.data.PagingAxis.Vertical).dp),
        )
        com.bos.sphere.core.data.ClockPosition.Hidden -> Unit
    }
}

private fun Modifier.androidx_windowInsetsPadding(insets: androidx.compose.foundation.layout.WindowInsets): Modifier =
    this.then(Modifier.windowInsetsPadding(insets))

/** HOM-20: how the bottom clock fits the bottom margin band at the current window size. */
@Composable
internal fun bottomClockFit(prefs: LauncherPrefs): ClockBandFit {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val band = HomeFrame.band(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
    val dotsAtBottom = prefs.pagingFor(cfg.screenWidthDp > cfg.screenHeightDp) != com.bos.sphere.core.data.PagingAxis.Vertical
    return ClockBandFit.compute(
        band, clockBlockHeight(prefs).value, clockBlockHeight(prefs.copy(clockShowWeather = false)).value, dotsAtBottom,
    )
}
