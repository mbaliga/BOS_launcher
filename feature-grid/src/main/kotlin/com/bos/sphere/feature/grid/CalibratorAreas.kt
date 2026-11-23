package com.bos.sphere.feature.grid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HoldMeter
import com.bos.sphere.core.data.IntervalMeter
import com.bos.sphere.core.data.ShakePeakMeter
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Wave 56 (looksPresets), CFG-27: test tags of the "Try it" areas. */
object CalibratorTags {
    fun area(id: String) = "calib.$id.area"
    fun readout(id: String) = "calib.$id.readout"
    fun reset(id: String) = "calib.$id.reset"
}

/**
 * The "Try it" area of a setting (Clackpad's calibrators): a flat, outlined area (hairline, no fill) where the person does the thing and the measured value is written
 * to the same setting the slider above edits, with what was measured, what the setting is now and a text "Reset to default". [content] is the touchable area.
 */
@Composable
private fun CalibratorFrame(id: String, prompt: String, measured: String?, setTo: String, onReset: () -> Unit, area: Modifier) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 96.dp).border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp)).then(area).testTag(CalibratorTags.area(id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(prompt, color = HyleColors.InkDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).testTag(CalibratorTags.readout(id)).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                if (measured != null) Text(L10n.tr("Measured %s", measured), color = HyleColors.InkBright, fontSize = 13.sp)
                Text(L10n.tr("Set to %s", setTo), color = HyleColors.InkDim, fontSize = 13.sp)
            }
            Box(Modifier.heightIn(min = 48.dp).testTag(CalibratorTags.reset(id)).clickable(role = Role.Button, onClick = onReset).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(L10n.tr("Reset to default"), color = HyleColors.Violet, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Press and hold as long as you want the value to be: the held time is measured from the touch events' own times ([HoldMeter]) and given to [onHeld] (ms).
 * [format] shows it ("240 ms"); [setTo] is the setting as the slider shows it now.
 */
@Composable
fun HoldCalibrator(id: String, prompt: String, setTo: String, format: (Long) -> String, onHeld: (Long) -> Unit, onReset: () -> Unit) {
    var measured by remember { mutableStateOf<String?>(null) }
    var holding by remember { mutableStateOf(false) }
    val held = rememberUpdatedState(onHeld); val fmt = rememberUpdatedState(format)
    CalibratorFrame(
        id, if (holding) L10n.tr("Keep holding…") else prompt, measured, setTo, { measured = null; onReset() },
        Modifier.pointerInput(Unit) {
            val meter = HoldMeter()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                meter.down(down.uptimeMillis); holding = true
                val up = waitForUpOrCancellation()
                holding = false
                if (up != null) meter.up(up.uptimeMillis)?.let { ms -> measured = fmt.value(ms); held.value(ms) } else meter.cancel()
            }
        },
    )
}

/** Tap, wait as long as you want the value to be, tap again: the time between the taps ([IntervalMeter]) goes to [onWaited] (ms). */
@Composable
fun IntervalCalibrator(id: String, prompt: String, secondPrompt: String, setTo: String, format: (Long) -> String, onWaited: (Long) -> Unit, onReset: () -> Unit) {
    var measured by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    val waited = rememberUpdatedState(onWaited); val fmt = rememberUpdatedState(format)
    CalibratorFrame(
        id, if (waiting) secondPrompt else prompt, measured, setTo, { measured = null; waiting = false; onReset() },
        Modifier.pointerInput(Unit) {
            val meter = IntervalMeter()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val ms = meter.tap(down.uptimeMillis)
                waiting = meter.waiting
                if (ms != null) { measured = fmt.value(ms); waited.value(ms) }
                waitForUpOrCancellation()
            }
        },
    )
}

/** A feed of accelerometer samples (m/s², gravity included, time in ms); [start] returns what stops it. Null source = the device has no accelerometer. */
fun interface AccelSource { fun start(onSample: (tMs: Long, x: Float, y: Float, z: Float) -> Unit): () -> Unit }

/** The device's accelerometer, or null when it has none. It only runs while the Try it area is on screen. */
@Composable
fun rememberAccelSource(): AccelSource? {
    val ctx = LocalContext.current
    return remember(ctx) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val a = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || a == null) null else AccelSource { on ->
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { on(e.timestamp / 1_000_000L, e.values[0], e.values[1], e.values[2]) }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(l, a, SensorManager.SENSOR_DELAY_GAME)
            val stop: () -> Unit = { sm.unregisterListener(l) }
            stop
        }
    }
}

/** Shake the phone the way you want it to react: the strongest push of the shake ([ShakePeakMeter]) goes to [onShake] (m/s²). [source] null shows [unavailable]. */
@Composable
fun ShakeCalibrator(id: String, prompt: String, unavailable: String, setTo: String, source: AccelSource?, format: (Float) -> String, onShake: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    var measured by remember { mutableStateOf<String?>(null) }
    val shake = rememberUpdatedState(onShake); val fmt = rememberUpdatedState(format)
    DisposableEffect(source) {
        val meter = ShakePeakMeter()
        val stop = source?.start { t, x, y, z -> meter.feed(t, x, y, z)?.let { peak -> measured = fmt.value(peak); shake.value(peak) } }
        onDispose { stop?.invoke() }
    }
    Box(modifier) { CalibratorFrame(id, if (source == null) unavailable else prompt, measured, setTo, { measured = null; onReset() }, Modifier.semantics { contentDescription = prompt }) }
}
