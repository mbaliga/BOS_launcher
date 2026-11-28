package com.bos.sphere.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.bos.sphere.core.data.HapticStrength
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SheetBlack = Color.Black
private val InkWhite = Color.White
private val TrackDim = Color(0x66FFFFFF)

/**
 * Wave 27 (soundPopup, owner mock-up): the "Sound and Vibration" bottom sheet. Pure black, large top radius over the dimmed screen, four
 * hairline sliders (Music, Video and Games; Notifications; Alarms; then Vibration). Volumes go through public AudioManager calls with
 * flag 0 (no system panel). Vibration is the launcher's own haptic strength, because the system vibration intensity needs WRITE_SETTINGS
 * (docs/decisions/soundPopup.md). Back, Escape, a tap on the dimmed area and a drag down close it. The header's [onOpenSound] hook and the
 * system row's sound button both open it.
 */
@Composable
fun SoundVibrationSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    volumes: VolumeController = rememberAndroidVolumeController(),
    haptics: HapticControl = LauncherHapticControl,
) {
    var state by remember(volumes, haptics) { mutableStateOf(SoundVibrationState.read(volumes, haptics)) }
    fun refresh() { state = SoundVibrationState.read(volumes, haptics, refused = state.rows.filter { !it.enabled }.map { it.kind }.toSet()) }
    val resolver = LocalContext.current.contentResolver
    // External volume changes (hardware keys, another app): a ContentObserver on the public Settings.System URI, plus a slow poll as a
    // safety net and a re-read on resume. The observer is unregistered when the sheet leaves the composition.
    DisposableEffect(resolver) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { refresh() } }
        runCatching { resolver.registerContentObserver(Settings.System.CONTENT_URI, true, obs) }
        onDispose { runCatching { resolver.unregisterContentObserver(obs) } }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(volumes) { while (true) { delay(1500); refresh() } }
    SoundVibrationContent(state, onDismiss, modifier) { kind, v -> state = state.set(kind, v, volumes, haptics) }
}

@Composable
fun rememberAndroidVolumeController(): VolumeController {
    val ctx = LocalContext.current
    return remember(ctx) { AndroidVolumeController(ctx) }
}

/** The sheet over its scrim for a given [state]; [onSet] asks for a new value on a row. Separate so tests can drive it with a fake. */
@Composable
internal fun SoundVibrationContent(state: SoundVibrationState, onDismiss: () -> Unit, modifier: Modifier = Modifier, onSet: (SoundRowKind, Int) -> Unit) {
    PopupSheet(L10n.tr("Sound and Vibration"), L10n.tr("Close Sound and Vibration"), "snd", onDismiss, modifier) { compact ->
        @Composable fun rows(kinds: List<SoundRowKind>, mod: Modifier) = Column(mod) {
            for (kind in kinds) {
                if (kind == SoundRowKind.Vibration) Box(Modifier.padding(vertical = if (compact) 6.dp else 18.dp).fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)).testTag("snd.divider"))
                SoundRowView(state.row(kind)) { onSet(kind, it) }
            }
        }
        if (compact) Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) { // landscape: two columns so all four sliders show without scrolling
            rows(listOf(SoundRowKind.Media, SoundRowKind.Notifications), Modifier.weight(1f))
            rows(listOf(SoundRowKind.Alarms, SoundRowKind.Vibration), Modifier.weight(1f))
        } else rows(SoundRowKind.entries, Modifier.fillMaxWidth())
        Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
    }
}

@Composable
private fun SoundRowView(row: SoundRow, onChange: (Int) -> Unit) {
    val alpha = if (row.enabled) 1f else 0.38f
    val second = when {
        !row.enabled -> L10n.tr(row.reason ?: SoundVibrationState.DND_REASON)
        row.kind == SoundRowKind.Vibration -> L10n.tr("Launcher vibration")
        else -> null
    }
    val valueText = if (row.kind == SoundRowKind.Vibration) L10n.tr(strengthLabel(HapticStrength.forStep(row.value))) else L10n.tr("%d percent", row.percent)
    Column(
        Modifier.fillMaxWidth().testTag("snd.row.${row.kind.name.lowercase()}")
            .semantics(mergeDescendants = true) {
                stateDescription = if (row.enabled) valueText else "$valueText, ${second ?: ""}"
                progressBarRangeInfo = ProgressBarRangeInfo(row.value.toFloat(), 0f..row.max.toFloat(), (row.max - 1).coerceAtLeast(0))
                if (!row.enabled) disabled()
                setProgress { target -> if (row.enabled) { onChange(target.roundToInt()); true } else false }
            },
    ) {
        Row(Modifier.fillMaxWidth().height(if (second == null) 28.dp else 44.dp).graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically) {
            SoundGlyph(row.kind, Modifier.size(26.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(L10n.tr(row.kind.english), color = InkWhite, fontSize = 18.sp, maxLines = 1)
                if (second != null) Text(second, color = Color(0xB3FFFFFF), fontSize = 13.sp, maxLines = 1, modifier = Modifier.testTag("snd.second.${row.kind.name.lowercase()}"))
            }
        }
        HairlineSlider(row, onChange, Modifier.graphicsLayer { this.alpha = alpha })
    }
}

private fun strengthLabel(s: HapticStrength) = when (s) { HapticStrength.Off -> "Off"; HapticStrength.Light -> "Light"; HapticStrength.Medium -> "Medium"; HapticStrength.Strong -> "Strong" }

/**
 * The slider of the mock-up: a hairline, brighter where filled, with a small white rectangular thumb; 48 dp touch target. Operable by
 * touch (tap and drag), keyboard (arrows step, Page keys, Home and End) and TalkBack (the row's progress semantics, set in [SoundRowView]).
 */
@Composable
private fun HairlineSlider(row: SoundRow, onSetNow: (Int) -> Unit, modifier: Modifier = Modifier) {
    val onSet by rememberUpdatedState(onSetNow)
    val rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var width by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val thumbW = 10.dp
    val current by rememberUpdatedState(row)
    fun valueAt(x: Float, density: androidx.compose.ui.unit.Density): Int {
        val tw = with(density) { thumbW.toPx() }
        val f = ((x - tw / 2) / (width - tw).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return ((if (rtl) 1f - f else f) * current.max).roundToInt()
    }
    Box(
        modifier.fillMaxWidth().height(48.dp).testTag("snd.slider.${row.kind.name.lowercase()}")
            .onSizeChanged { width = it.width }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(1.5.dp, InkWhite, RoundedCornerShape(6.dp)) else Modifier)
            .then(
                if (!row.enabled) Modifier else Modifier
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val flip = if (rtl) -1 else 1
                        when (e.key) {
                            Key.DirectionRight -> onSet(current.value + flip)
                            Key.DirectionLeft -> onSet(current.value - flip)
                            Key.DirectionUp, Key.PageUp -> onSet(current.value + 1)
                            Key.DirectionDown, Key.PageDown -> onSet(current.value - 1)
                            Key.MoveHome -> onSet(0)
                            Key.MoveEnd -> onSet(current.max)
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    .pointerInput(row.kind) { detectTapGestures { onSet(valueAt(it.x, this)) } }
                    .pointerInput(row.kind) { detectHorizontalDragGestures(onDragStart = { onSet(valueAt(it.x, this)) }) { change, _ -> change.consume(); onSet(valueAt(change.position.x, this)) } },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val tw = thumbW.toPx(); val th = 22.dp.toPx()
            val f = if (rtl) 1f - row.fraction else row.fraction
            val cx = tw / 2 + f * (size.width - tw)
            val y = size.height / 2
            val hair = 1.dp.toPx()
            if (!rtl) {
                drawLine(TrackDim, Offset(cx, y), Offset(size.width, y), hair)
                drawLine(InkWhite, Offset(0f, y), Offset(cx, y), hair * 1.5f)
            } else {
                drawLine(TrackDim, Offset(0f, y), Offset(cx, y), hair)
                drawLine(InkWhite, Offset(cx, y), Offset(size.width, y), hair * 1.5f)
            }
            drawRect(InkWhite, Offset(cx - tw / 2, y - th / 2), Size(tw, th))
        }
    }
}

/** Vector glyphs on a 24 x 24 grid, white on black: music note, bell, alarm clock with a check, vibrating phone. */
@Composable
internal fun SoundGlyph(kind: SoundRowKind, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val k = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val line = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            SoundRowKind.Media -> {
                drawCircle(InkWhite, 4.2f * k, p(8f, 18.3f))
                drawRect(InkWhite, p(10.8f, 3f), Size(2.6f * k, 15.3f * k))
                val flag = Path().apply { moveTo(13.4f * k, 3f * k); cubicTo(14f * k, 8f * k, 20f * k, 8f * k, 19.5f * k, 14f * k); cubicTo(18.2f * k, 11.5f * k, 15.5f * k, 11f * k, 13.4f * k, 11f * k); close() }
                drawPath(flag, InkWhite)
            }
            SoundRowKind.Notifications -> {
                val bell = Path().apply {
                    moveTo(12f * k, 3f * k); cubicTo(7.5f * k, 3f * k, 5.5f * k, 6.5f * k, 5.5f * k, 10.5f * k); lineTo(5.5f * k, 14.5f * k); lineTo(3f * k, 17.5f * k)
                    lineTo(21f * k, 17.5f * k); lineTo(18.5f * k, 14.5f * k); lineTo(18.5f * k, 10.5f * k); cubicTo(18.5f * k, 6.5f * k, 16.5f * k, 3f * k, 12f * k, 3f * k); close()
                }
                drawPath(bell, InkWhite)
                drawRoundRect(InkWhite, p(10f, 19.2f), Size(4f * k, 2.2f * k), CornerRadius(1.1f * k))
                drawLine(InkWhite, p(2.5f, 8f), p(4.2f, 5.2f), 1.6f * k, StrokeCap.Round); drawLine(InkWhite, p(21.5f, 8f), p(19.8f, 5.2f), 1.6f * k, StrokeCap.Round)
            }
            SoundRowKind.Alarms -> {
                drawCircle(InkWhite, 8f * k, p(12f, 13.5f))
                val check = Path().apply { moveTo(8.4f * k, 13.6f * k); lineTo(11f * k, 16.2f * k); lineTo(15.8f * k, 11f * k) }
                drawPath(check, SheetBlack, style = Stroke(2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(InkWhite, p(2.5f, 6.5f), p(6f, 3.2f), line.width, StrokeCap.Round); drawLine(InkWhite, p(21.5f, 6.5f), p(18f, 3.2f), line.width, StrokeCap.Round)
            }
            SoundRowKind.Vibration -> {
                drawRoundRect(InkWhite, p(6f, 3.5f), Size(12f * k, 17f * k), CornerRadius(1.5f * k), style = line)
                drawLine(InkWhite, p(10f, 17f), p(14f, 17f), line.width, StrokeCap.Round)
                for (s in listOf(-1f, 1f)) {
                    val x = 12f + s * 10f
                    drawLine(InkWhite, p(x, 7f), p(x, 17f), line.width, StrokeCap.Round)
                    drawLine(InkWhite, p(12f + s * 8.2f, 9.5f), p(12f + s * 8.2f, 14.5f), line.width, StrokeCap.Round)
                }
            }
        }
    }
}

/** Whether the Sound and Vibration sheet is showing. The header's `onOpenSound` hook is `state::show`. */
class SoundSheetState { var open by mutableStateOf(false); fun show() { open = true } }

@Composable
fun rememberSoundSheetState(): SoundSheetState = remember { SoundSheetState() }
