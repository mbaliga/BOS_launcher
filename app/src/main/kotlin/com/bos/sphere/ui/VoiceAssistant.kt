package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors

/**
 * Voice assistant button and input handler. Scaffolded for M4+ integration with on-device
 * or cloud-based speech recognition and command processing. Currently a stub that logs
 * voice input availability.
 */
@Composable
fun VoiceAssistantButton(
    onVoiceInput: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isListening by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                HyleColors.Violet.copy(alpha = if (isListening) 0.25f else 0.10f),
                CircleShape
            )
            .clickable {
                isListening = !isListening
                if (isListening) {
                    startVoiceInput(onVoiceInput)
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
 * Starts voice input handling. This is a scaffold point for M4+ that would integrate
 * with Android's SpeechRecognizer or an on-device inference model.
 */
private fun startVoiceInput(onInput: (String) -> Unit) {
    // Scaffold for M4+ integration:
    // - Wire to Android SpeechRecognizer API (requires android.permission.RECORD_AUDIO)
    // - Or route to on-device inference (Urbana integration)
    // - Or route to cloud assistant with explicit user opt-in
    // For now, this is a no-op stub.
}

/**
 * Voice indicator dot that animates when listening. Used in chrome.
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
