package com.bos.sphere.feature.grid

import com.bos.sphere.core.input.FocusKind
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.Torch
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One rendered complication reading.
 *
 * @property text the big readout (a number or a 2–5 character word).
 * @property caption tiny label under it (keep ≤ 6 characters; it is drawn in spaced caps).
 * @property progress 0..1 fills the dial's ring; null draws no ring.
 * @property accent ring / emphasis colour.
 * @property description spoken form for accessibility (falls back to text + caption).
 */
@Immutable
data class ComplicationValue(
    val text: String,
    val caption: String,
    val progress: Float?,
    val accent: Color,
    val description: String = "",
)

/** Accent tints used by complications beyond the core Hyle palette. */
internal object ComplicationColors {
    val Charging = HyleColors.ProvenanceOnDevice
    val Amber = Color(0xFFFFB547)
}

// ---------------------------------------------------------------------------------------------
// Live data
// ---------------------------------------------------------------------------------------------

/**
 * A live, lifecycle-safe reading for [kind]. Broadcast receivers are registered while this is in
 * the composition and unregistered when it leaves; time-based readings also refresh on resume.
 * Needs no runtime permissions.
 */
@Composable
fun rememberComplicationValue(kind: ComplicationKind): ComplicationValue = key(kind) {
    when (kind) {
        ComplicationKind.Battery -> batteryValue()
        ComplicationKind.Date -> dateValue()
        ComplicationKind.Time -> timeValue()
        ComplicationKind.NextAlarm -> nextAlarmValue()
        ComplicationKind.Ringer -> ringerValue()
        ComplicationKind.Flashlight -> flashlightValue()
        ComplicationKind.Storage -> storageValue()
        ComplicationKind.Notifications -> notificationsValue()
    }
}

/**
 * Holds `read(context, intent)` and re-reads it whenever one of [actions] is broadcast. The
 * sticky intent returned at registration (e.g. battery) is fed in immediately.
 */
@Composable
private fun <T> broadcastState(vararg actions: String, read: (Context, Intent?) -> T): State<T> {
    val context = LocalContext.current
    val currentRead by rememberUpdatedState(read)
    val state = remember(context) { mutableStateOf(read(context, null)) }
    val actionKey = actions.joinToString("|")
    DisposableEffect(context, actionKey) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                state.value = currentRead(context, intent)
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        // System broadcasts are delivered to non-exported receivers.
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = currentRead(context, sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/** A counter bumped on every lifecycle resume (cheap "refresh now" signal). */
@Composable
private fun resumeTick(): Int {
    val tick = remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick.intValue++
        onPauseOrDispose { }
    }
    return tick.intValue
}

@Composable
private fun batteryValue(): ComplicationValue {
    val reading by broadcastState(Intent.ACTION_BATTERY_CHANGED) { ctx, intent ->
        val sticky = intent ?: ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level < 0) -1 else (level * 100f / scale).roundToInt().coerceIn(0, 100)
        pct to charging
    }
    val (pct, charging) = reading
    return ComplicationFormat.battery(pct, charging)
}

@Composable
private fun dateValue(): ComplicationValue {
    val signal by broadcastState(
        Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    ) { _, _ -> System.nanoTime() }
    val resumed = resumeTick()
    val midnight = remember { mutableIntStateOf(0) }
    LaunchedEffect(signal) {
        while (true) {
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay((next.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_000L) + 500L)
            midnight.intValue++
        }
    }
    val today = remember(signal, resumed, midnight.intValue) { LocalDate.now() }
    return ComplicationFormat.date(today, Locale.getDefault())
}

@Composable
private fun timeValue(): ComplicationValue {
    val signal by broadcastState(
        Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    ) { _, _ -> System.nanoTime() }
    val resumed = resumeTick()
    val context = LocalContext.current
    return remember(signal, resumed) {
        ComplicationFormat.time(LocalDateTime.now(), DateFormat.is24HourFormat(context), Locale.getDefault())
    }
}

@Composable
private fun nextAlarmValue(): ComplicationValue {
    val trigger by broadcastState(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) { ctx, _ ->
        ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }
    val resumed = resumeTick()
    val context = LocalContext.current
    return remember(trigger, resumed) {
        val zone = ZoneId.systemDefault()
        ComplicationFormat.nextAlarm(
            trigger?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            LocalDateTime.now(zone),
            DateFormat.is24HourFormat(context),
            Locale.getDefault(),
        )
    }
}

@Composable
private fun ringerValue(): ComplicationValue {
    val mode by broadcastState(AudioManager.RINGER_MODE_CHANGED_ACTION) { ctx, _ ->
        ctx.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
    }
    return when (mode) {
        AudioManager.RINGER_MODE_SILENT -> ComplicationValue("SIL", "SOUND", null, HyleColors.InkDim, "Silent")
        AudioManager.RINGER_MODE_VIBRATE -> ComplicationValue("VIB", "SOUND", null, HyleColors.VioletSoft, "Vibrate")
        else -> ComplicationValue("RING", "SOUND", null, HyleColors.Violet, "Ring")
    }
}

@Composable
private fun flashlightValue(): ComplicationValue {
    val on by Torch.on.collectAsState()
    return if (on) ComplicationValue("ON", "TORCH", 1f, ComplicationColors.Amber, "Flashlight on")
    else ComplicationValue("OFF", "TORCH", null, HyleColors.InkDim, "Flashlight off")
}

@Composable
private fun storageValue(): ComplicationValue {
    val resumed = resumeTick()
    val stats by produceState<Pair<Long, Long>?>(initialValue = null, resumed) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val fs = StatFs(Environment.getDataDirectory().path)
                    fs.totalBytes to fs.availableBytes
                }.getOrNull()
            }
            delay(STORAGE_REFRESH_MS)
        }
    }
    val s = stats ?: return ComplicationValue("—", "DISK", null, HyleColors.InkDim, "Storage")
    return ComplicationFormat.storage(s.first, s.second)
}

@Composable
private fun notificationsValue(): ComplicationValue {
    val list by NotificationProvider.notifications.collectAsState(initial = emptyList())
    val n = list.size
    return ComplicationValue(
        text = if (n > 99) "99+" else n.toString(),
        caption = "NOTIF",
        progress = null,
        accent = if (n > 0) HyleColors.Violet else HyleColors.InkDim,
        description = if (n == 1) "1 notification" else "$n notifications",
    )
}

private const val STORAGE_REFRESH_MS = 5 * 60_000L

/** Pure formatters behind the live readings (JVM-testable). */
object ComplicationFormat {
    const val LOW_BATTERY = 20

    fun battery(percent: Int, charging: Boolean): ComplicationValue {
        if (percent < 0) return ComplicationValue("—", "BATT", null, HyleColors.InkDim, "Battery unknown")
        val accent = when {
            charging -> ComplicationColors.Charging
            percent < LOW_BATTERY -> ComplicationColors.Amber
            else -> HyleColors.Violet
        }
        return ComplicationValue(
            text = percent.toString(),
            caption = if (charging) "CHRG" else "BATT",
            progress = percent / 100f,
            accent = accent,
            description = "Battery $percent percent" + if (charging) ", charging" else "",
        )
    }

    fun date(day: LocalDate, locale: Locale): ComplicationValue {
        val weekday = day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).uppercase(locale)
        val full = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        return ComplicationValue(
            text = day.dayOfMonth.toString(),
            caption = weekday.take(4),
            progress = day.dayOfMonth / day.lengthOfMonth().toFloat(),
            accent = HyleColors.Violet,
            description = full,
        )
    }

    fun clockText(t: LocalDateTime, is24h: Boolean, locale: Locale): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm", locale))

    fun time(now: LocalDateTime, is24h: Boolean, locale: Locale): ComplicationValue {
        val caption = if (is24h) now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).uppercase(locale).take(4)
        else now.format(DateTimeFormatter.ofPattern("a", locale)).uppercase(locale)
        return ComplicationValue(
            text = clockText(now, is24h, locale),
            caption = caption,
            progress = null,
            accent = HyleColors.Violet,
            description = "Time " + now.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a", locale)),
        )
    }

    /** [alarm] null = none set. Alarms more than ~a day out are captioned with their weekday. */
    fun nextAlarm(alarm: LocalDateTime?, now: LocalDateTime, is24h: Boolean, locale: Locale): ComplicationValue {
        if (alarm == null) return ComplicationValue("—", "ALARM", null, HyleColors.InkDim, "No alarm set")
        val soon = alarm.isBefore(now.plusHours(20))
        val caption = if (soon) "ALARM" else alarm.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).uppercase(locale).take(4)
        val spoken = alarm.format(DateTimeFormatter.ofPattern(if (is24h) "EEEE H:mm" else "EEEE h:mm a", locale))
        return ComplicationValue(clockText(alarm, is24h, locale), caption, null, HyleColors.Violet, "Next alarm $spoken")
    }

    fun storage(totalBytes: Long, availableBytes: Long): ComplicationValue {
        if (totalBytes <= 0) return ComplicationValue("—", "DISK", null, HyleColors.InkDim, "Storage")
        val used = ((totalBytes - availableBytes).toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        val pct = (used * 100).roundToInt()
        val freeGb = availableBytes / 1_000_000_000.0
        return ComplicationValue(
            text = "$pct%",
            caption = "DISK",
            progress = used,
            accent = if (used > 0.9f) ComplicationColors.Amber else HyleColors.Violet,
            description = "Storage $pct percent used, ${"%.1f".format(Locale.ROOT, freeGb)} gigabytes free",
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Views
// ---------------------------------------------------------------------------------------------

/**
 * A watch-face style dial showing the live reading for [kind]: surface disc, hairline rim, a thin
 * accent progress ring, a large readout and a tiny caption. [onClick] null = not interactive.
 */
@Composable
fun ComplicationView(
    kind: ComplicationKind,
    size: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val value = rememberComplicationValue(kind)
    ComplicationDial(value, kind.label, size, onClick, modifier)
}

/** Stateless dial for an already-computed [value] (useful for previews / custom data). */
@Composable
fun ComplicationDial(
    value: ComplicationValue,
    label: String,
    size: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "complicationPress",
    )
    val accent by animateColorAsState(value.accent, spring(stiffness = Spring.StiffnessLow), label = "complicationAccent")
    val sweep by animateFloatAsState(
        targetValue = (value.progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "complicationSweep",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (value.progress != null) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "complicationRingAlpha",
    )
    val click = onClick
    val spoken = "$label: " + value.description.ifBlank { "${value.text} ${value.caption}" }
    val density = LocalDensity.current
    val sizeSp = with(density) { size.toSp() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(CircleShape)
            .background(HyleColors.Surface)
            .border(1.dp, HyleColors.Hairline, CircleShape)
            .then(
                if (click != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = click)
                else Modifier,
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                if (click != null) {
                    role = Role.Button
                    onClick(label = label) { click(); true }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (this.size.minDimension * 0.045f).coerceAtLeast(1.5f)
            val inset = stroke / 2f + this.size.minDimension * 0.06f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (ringAlpha > 0.01f) {
                drawArc(
                    color = HyleColors.Hairline,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke), alpha = ringAlpha,
                )
                if (sweep > 0.001f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round), alpha = ringAlpha,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(targetState = value.text, label = "complicationText") { text ->
                Text(
                    text = text,
                    style = TextStyle(
                        color = HyleColors.InkBright,
                        fontSize = sizeSp * ComplicationFormatting.textScale(text),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.02).em,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = value.caption,
                style = TextStyle(
                    color = if (value.progress == null && value.accent != HyleColors.InkDim) accent else HyleColors.InkDim,
                    fontSize = (sizeSp * 0.11f).value.coerceAtLeast(7f).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.14.em,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

internal object ComplicationFormatting {
    /** Big-text size as a fraction of the dial size, shrinking for longer readouts. */
    fun textScale(text: String): Float = when (text.length) {
        0, 1, 2 -> 0.34f
        3 -> 0.28f
        4 -> 0.23f
        else -> 0.19f
    }
}

/**
 * Several complications on a quarter-circle arc hugging the given SCREEN corner; the composable's
 * own bounds are a square of side [radius] + [itemSize], aligned by the caller into that corner.
 * Up to [CornerSlot.MAX_CLUSTER] items are spread evenly along the arc (inset from both screen
 * edges), ordered clockwise around the screen, and stagger in with a spring.
 */
@Composable
fun ComplicationCluster(
    kinds: List<ComplicationKind>,
    anchor: Corner,
    itemSize: Dp,
    radius: Dp,
    onClick: (ComplicationKind) -> Unit,
    modifier: Modifier = Modifier,
    /** INP-17: when set, every dial is a focus-graph node named "<prefix>.<kind>", in cluster order. */
    focusIdPrefix: String? = null,
) {
    val shown = kinds.distinct().take(CornerSlot.MAX_CLUSTER)
    val density = LocalDensity.current
    val sidePx = with(density) { (radius + itemSize).toPx() }
    val radiusPx = with(density) { radius.toPx() }
    val itemPx = with(density) { itemSize.toPx() }
    val centers = remember(shown.size, anchor, sidePx, radiusPx, itemPx) {
        ComplicationArc.centers(shown.size, anchor, radiusPx, itemPx)
    }
    val currentOnClick by rememberUpdatedState(onClick)

    Box(modifier.size(radius + itemSize)) {
        shown.forEachIndexed { i, kind ->
            key(kind) {
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(i * STAGGER_MS)
                    launch { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
                }
                val c = centers[i]
                ComplicationView(
                    kind = kind,
                    size = itemSize,
                    onClick = { currentOnClick(kind) },
                    modifier = Modifier
                        .offset { IntOffset((c.x - itemPx / 2f).roundToInt(), (c.y - itemPx / 2f).roundToInt()) }
                        .then(
                            if (focusIdPrefix == null) Modifier
                            else Modifier.launcherItem("$focusIdPrefix.${kind.name}", FocusKind.Corner, order = i, focusable = false, onActivate = { currentOnClick(kind) }, ringRadiusDp = itemSize.value / 2f),
                        )
                        .graphicsLayer {
                            val p = appear.value
                            alpha = p.coerceIn(0f, 1f)
                            val s = 0.6f + 0.4f * p
                            scaleX = s; scaleY = s
                            // Drift in from the corner.
                            val (cx, cy) = ComplicationArc.cornerPoint(anchor, sidePx)
                            translationX = (cx - c.x) * (1f - p) * 0.35f
                            translationY = (cy - c.y) * (1f - p) * 0.35f
                        },
                )
            }
        }
    }
}

private const val STAGGER_MS = 55L

/** A point in px inside a cluster's square bounds. */
data class ArcPoint(val x: Float, val y: Float)

/**
 * Pure placement math for [ComplicationCluster]. The cluster box is a square of side
 * `radius + itemSize` whose [anchor] corner sits on the screen corner; item centres lie on a
 * quarter circle of [radius] about that corner (clamped so items never leave the box).
 */
object ComplicationArc {
    /** Angular inset from each screen edge, in degrees. */
    const val EDGE_INSET_DEG = 14.0

    /**
     * Angles (degrees, 0 = along the horizontal screen edge, 90 = along the vertical one) for
     * [count] items, evenly spread inside the insets. A single item sits on the diagonal.
     */
    fun angles(count: Int): List<Double> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(45.0)
        else -> {
            val span = 90.0 - 2 * EDGE_INSET_DEG
            List(count) { EDGE_INSET_DEG + span * it / (count - 1) }
        }
    }

    /** The box corner that touches the screen corner. */
    fun cornerPoint(anchor: Corner, side: Float): Pair<Float, Float> = when (anchor) {
        Corner.TopLeft -> 0f to 0f
        Corner.TopRight -> side to 0f
        Corner.BottomRight -> side to side
        Corner.BottomLeft -> 0f to side
    }

    /**
     * Item centres for [count] items, ordered clockwise around the screen (e.g. for
     * [Corner.TopLeft]: from the one nearest the left edge to the one nearest the top edge).
     */
    fun centers(count: Int, anchor: Corner, radius: Float, itemSize: Float): List<ArcPoint> {
        val side = radius + itemSize
        val half = itemSize / 2f
        val angles = angles(count)
        // Clockwise order: top-left & bottom-right run from the vertical edge to the horizontal
        // one (high angle → low); the other two the opposite way.
        val ordered = when (anchor) {
            Corner.TopLeft, Corner.BottomRight -> angles.reversed()
            Corner.TopRight, Corner.BottomLeft -> angles
        }
        return ordered.map { deg ->
            val rad = Math.toRadians(deg)
            // Distance from the vertical edge (dx) and from the horizontal edge (dy).
            val dx = (radius * cos(rad)).toFloat().coerceIn(half, side - half)
            val dy = (radius * sin(rad)).toFloat().coerceIn(half, side - half)
            when (anchor) {
                Corner.TopLeft -> ArcPoint(dx, dy)
                Corner.TopRight -> ArcPoint(side - dx, dy)
                Corner.BottomRight -> ArcPoint(side - dx, side - dy)
                Corner.BottomLeft -> ArcPoint(dx, side - dy)
            }
        }
    }
}
