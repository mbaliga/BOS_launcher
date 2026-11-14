package com.bos.sphere.feature.grid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** What the voice control does at a moment. */
enum class VoiceState { Hidden, Idle, NeedsPermission, Listening }

/** The recogniser behind the push-to-talk control. [start] must only ever be called after an explicit press. */
interface OnDeviceRecogniser {
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit)
    fun stop()
    fun release()
}

/**
 * INP-13: push-to-talk rules, pure so they are tested without a device.
 *  - The control exists only when an on-device recogniser exists (never a cloud one): [visible].
 *  - Nothing listens until the person presses; permission is asked on a press and listening starts on the next press.
 *  - Releasing, a final result, an error or leaving the screen stops it.
 */
class PushToTalk(
    private val recogniser: OnDeviceRecogniser,
    private val hasPermission: () -> Boolean,
    private val askPermission: () -> Unit,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit = {},
) {
    var listening = false
        private set(v) { if (field != v) { field = v; onListening(v) } }

    fun press() {
        if (listening) return
        if (!hasPermission()) { askPermission(); return }
        listening = true
        recogniser.start(
            onPartial = { onText(it) },
            onFinal = { onText(it); listening = false },
            onError = { listening = false },
        )
    }

    fun release() { if (listening) { listening = false; recogniser.stop() } }

    /** A tap for TalkBack and keyboard users, who cannot hold: it flips between listening and not. */
    fun toggle() { if (listening) release() else press() }

    fun dispose() { release(); recogniser.release() }

    companion object {
        /** On-device only: the control is hidden when there is no on-device recogniser, whatever else is installed. */
        fun visible(onDeviceAvailable: Boolean?): Boolean = onDeviceAvailable == true
        fun state(visible: Boolean, permission: Boolean, listening: Boolean) = when {
            !visible -> VoiceState.Hidden
            listening -> VoiceState.Listening
            !permission -> VoiceState.NeedsPermission
            else -> VoiceState.Idle
        }
    }
}

/** The Android recogniser, always the on-device one (API 33+); never the default, possibly cloud-backed, service. */
class AndroidOnDeviceRecogniser(private val context: Context) : OnDeviceRecogniser {
    private var rec: SpeechRecognizer? = null

    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { onError(); return }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { rec?.destroy(); rec = it }
        r.setRecognitionListener(object : RecognitionListener {
            private fun text(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            override fun onPartialResults(partialResults: Bundle?) { text(partialResults)?.let(onPartial) }
            override fun onResults(results: Bundle?) { text(results)?.let(onFinal) ?: onError() }
            override fun onError(error: Int) { onError() }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
        )
    }

    override fun stop() { rec?.stopListening() }
    override fun release() { rec?.destroy(); rec = null }
}

internal fun onDeviceSpeechAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

/**
 * A push-to-talk button for the palette (INP-13): hold to talk, release to stop; a tap (TalkBack, keyboard Enter or Space
 * held) works too. Draws nothing when the device has no on-device recogniser. The text goes to [onText] as it comes.
 */
@Composable
fun PushToTalkButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember(context) { PushToTalk.visible(onDeviceSpeechAvailable(context)) }
    if (!available) return
    var listening by remember { mutableStateOf(false) }
    val latestText by rememberUpdatedState(onText)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ptt = remember(context) {
        PushToTalk(
            AndroidOnDeviceRecogniser(context),
            hasPermission = { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            askPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) },
            onText = { latestText(it) },
            onListening = { listening = it },
        )
    }
    DisposableEffect(ptt) { onDispose { ptt.dispose() } }
    val label = L10n.tr("Speak to search")
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (listening) Color(0x66B9A8FF) else Color(0x22FFFFFF))
            .pointerInput(ptt) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    ptt.press(); listening = ptt.listening
                    waitForUpOrCancellation()
                    ptt.release(); listening = false
                }
            }
            .onKeyEvent { e ->
                if (e.key == Key.Spacebar || e.key == Key.Enter) {
                    if (e.type == KeyEventType.KeyDown) { ptt.press(); listening = ptt.listening } else { ptt.release(); listening = false }
                    true
                } else false
            }
            .focusable()
            .semantics { role = Role.Button; contentDescription = label; onClick(label = label) { ptt.toggle(); listening = ptt.listening; true } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.Mic, null, tint = HyleColors.InkBright, modifier = Modifier.size(22.dp))
    }
}
