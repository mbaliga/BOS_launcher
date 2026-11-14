package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.VoiceInputManager
import com.bos.sphere.core.design.HyleColors

/**
 * Voice assistant button with real SpeechRecognizer integration (M4+). Tapping the button
 * starts listening for voice input; results are fed back via onVoiceInput callback. The button
 * highlights when listening and dims when idle.
 */
@Composable
fun VoiceAssistantButton(
    onVoiceInput: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    val voiceResults by VoiceInputManager.voiceResults.collectAsStateWithLifecycle(initialValue = null)
    // CFG-07: the microphone is asked for on the first press, after the one-screen explanation.
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) isListening = VoiceInputManager.startListening(context)
    }

    // Feed voice results to the callback (search/launch)
    LaunchedEffect(voiceResults) {
        voiceResults?.let { result ->
            if (result.isFinal) {
                onVoiceInput(result.text)
                isListening = false
            }
        }
    }

    // Clean up on disposal
    DisposableEffect(Unit) {
        onDispose {
            VoiceInputManager.stopListening()
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                HyleColors.Violet.copy(alpha = if (isListening) 0.25f else 0.10f),
                CircleShape
            )
            .clickable {
                if (isListening) {
                    VoiceInputManager.stopListening()
                    isListening = false
                } else if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    isListening = VoiceInputManager.startListening(context)
                } else {
                    com.bos.sphere.core.design.GrantHooks.ask(com.bos.sphere.core.design.GrantKind.Microphone) { micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "🎤",
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Voice indicator dot that shows when listening is active. Used in chrome.
 */
@Composable
fun VoiceIndicator(
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isListening) HyleColors.Violet else HyleColors.InkDim
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}
