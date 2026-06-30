package com.bos.sphere.core.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Voice recognition result with confidence score.
 */
data class VoiceResult(
    val text: String,
    val confidence: Float = 1f,  // 0-1 scale
    val isFinal: Boolean = true,
)

/**
 * Singleton manager for voice input via Android SpeechRecognizer. Provides a Flow API
 * for voice results (interim and final). Requires android.permission.RECORD_AUDIO.
 *
 * Voice input is M4+ feature. On-device inference integration (Urbana) or cloud assistant
 * routing can be added as downstream processors of the voice results.
 */
object VoiceInputManager {
    private var speechRecognizer: SpeechRecognizer? = null
    private val listeners = mutableListOf<(VoiceResult) -> Unit>()

    val voiceResults: Flow<VoiceResult> = callbackFlow {
        val callback: (VoiceResult) -> Unit = { result ->
            trySend(result)
        }
        listeners.add(callback)
        awaitClose { listeners.remove(callback) }
    }

    internal fun onVoiceResult(result: VoiceResult) {
        listeners.forEach { it(result) }
    }

    /**
     * Start listening for voice input. Returns false if SpeechRecognizer is unavailable
     * or RECORD_AUDIO permission is not granted.
     */
    fun startListening(context: Context): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return false
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            val listener = VoiceRecognitionListener(::onVoiceResult)
            speechRecognizer?.setRecognitionListener(listener)
            speechRecognizer?.startListening(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Stop listening and clean up. */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore; may already be stopped
        }
    }

    /** Release resources. Call when the app exits or voice input is no longer needed. */
    fun release() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

private class VoiceRecognitionListener(
    private val onResult: (VoiceResult) -> Unit,
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        // Error codes: NETWORK_TIMEOUT, NETWORK, AUDIO, SERVER, CLIENT, SPEECH_TIMEOUT, etc.
        // For M4, these can be logged or reported to analytics.
    }

    override fun onResults(results: Bundle?) {
        if (results == null) return

        val matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        val scores = results.getFloatArray(android.speech.SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            val confidence = scores?.getOrNull(0) ?: 1f
            onResult(VoiceResult(text = text, confidence = confidence, isFinal = true))
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (partialResults == null) return

        val matches = partialResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            onResult(VoiceResult(text = text, confidence = 0.5f, isFinal = false))
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
