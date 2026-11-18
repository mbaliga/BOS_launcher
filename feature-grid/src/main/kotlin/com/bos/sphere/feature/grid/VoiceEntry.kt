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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

    private var session = 0
    private var disposed = false

    fun press() {
        if (disposed || listening) return
        if (!hasPermission()) { askPermission(); return }
        listening = true
        // Wave 22 (audit): each press is a session; a late callback of an older session (a recogniser that was still finishing) or of a
        // control that has left the screen is ignored, so it can neither end the new listening nor write into a closed Radar. A blank
        // result (silence) never replaces what is in the field.
        val id = ++session
        fun live() = id == session && !disposed
        recogniser.start(
            onPartial = { if (live() && it.isNotBlank()) onText(it) },
            onFinal = { if (live()) { if (it.isNotBlank()) onText(it); listening = false } },
            onError = { if (live()) listening = false },
        )
    }

    fun release() { if (listening) { listening = false; recogniser.stop() } }

    /** A tap for TalkBack and keyboard users, who cannot hold: it flips between listening and not. */
    fun toggle() { if (listening) release() else press() }

    fun dispose() { release(); disposed = true; recogniser.release() }

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

/** Wave 23 (NFR-13): the English state words the mic exposes as its stateDescription (translated at the call). */
object VoiceStates {
    const val READY = "Ready"
    const val LISTENING = "Listening"
    const val UNAVAILABLE = "Unavailable, microphone permission is off"
    fun describe(s: VoiceState): String = when (s) {
        VoiceState.Listening -> LISTENING
        VoiceState.NeedsPermission, VoiceState.Hidden -> UNAVAILABLE
        VoiceState.Idle -> READY
    }
}

/**
 * A push-to-talk button for the palette (INP-13): hold to talk, release to stop; a tap (TalkBack, keyboard Enter or Space
 * held) works too. Draws nothing when the device has no on-device recogniser. The text goes to [onText] as it comes.
 */
@Composable
<<<<<<< HEAD
fun PushToTalkButton(onText: (String) -> Unit, modifier: Modifier = Modifier, availableOverride: Boolean? = null, shade: Float = 0f, onFocusChange: (Boolean) -> Unit = {}, size: androidx.compose.ui.unit.Dp = GlassSpec.BigButton) {
=======
fun PushToTalkButton(onText: (String) -> Unit, modifier: Modifier = Modifier, availableOverride: Boolean? = null, shade: Float = 0f, onFocusChange: (Boolean) -> Unit = {}, recogniserOverride: OnDeviceRecogniser? = null) {
>>>>>>> w23-auditSweep
    val context = LocalContext.current
    val available = remember(context, availableOverride) { availableOverride ?: PushToTalk.visible(onDeviceSpeechAvailable(context)) }
    if (!available) return
    var listening by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Wave 23 (NFR-13): the state is announced as a state; the permission is read again whenever the listening state or the grant changes.
    var permitted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    LaunchedEffect(listening) { permitted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    // Allowed in Settings while Radar stays open: read it again when the app comes back, or the mic would keep saying "Unavailable".
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permitted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val latestText by rememberUpdatedState(onText)
    // Wave 22 (audit): a refusal (also a permanent one, which returns at once with no dialog) used to be silent; say what to do. Nothing listens
    // after a grant until the next press (INP-13).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (!granted) runCatching { android.widget.Toast.makeText(context, L10n.tr("Microphone is off. Allow it in Settings to speak to search."), android.widget.Toast.LENGTH_LONG).show() }
    }
    val ptt = remember(context) {
        PushToTalk(
            recogniserOverride ?: AndroidOnDeviceRecogniser(context),
            hasPermission = { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            askPermission = { com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.Microphone) { permission.launch(Manifest.permission.RECORD_AUDIO) } }, // CFG-07: explanation first
            onText = { latestText(it) },
            onListening = { listening = it },
        )
    }
    DisposableEffect(ptt) { onDispose { ptt.dispose() } }
    // Wave 23 sweep: a hold that goes on while the app leaves the screen (a tap-toggled listening, or a key held, then the user switches app) must not
    // keep the microphone open in the background; the screen pausing is the end of listening, as releasing the finger is.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, ptt) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && ptt.listening) { ptt.release(); listening = false } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val label = L10n.tr("Speak to search")
    // Wave 21: glass (violet-tinted while listening), 56 dp, the same hold-to-talk behaviour.
    Box(
        modifier
            .size(size)
            .glass(CircleShape, tint = if (listening) Color(0xFFB9A8FF) else Color.White, pressed = listening, shade = shade)
            .clip(CircleShape)
            .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier) // a visible keyboard focus (second audit)
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
            // A key held down when focus moves away never sends its key-up: stop listening then (the finger case ends with the pointer).
            .onFocusChanged { f -> focused = f.hasFocus; onFocusChange(f.hasFocus); if (!f.hasFocus && ptt.listening) { ptt.release(); listening = false } }
            .focusable()
            .semantics { role = Role.Button; contentDescription = label
                stateDescription = L10n.tr(VoiceStates.describe(PushToTalk.state(true, permitted, listening)))
                liveRegion = LiveRegionMode.Polite // a change is announced after what is being said, never over it
                onClick(label = label) { ptt.toggle(); listening = ptt.listening; true } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.Mic, null, tint = GlassSpec.content(true), modifier = Modifier.size(24.dp))
    }
}
