package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.bos.sphere.core.data.HapticKind
import com.bos.sphere.core.data.HapticPolicy
import com.bos.sphere.core.data.LauncherHapticSettings

/**
 * Wave 27 (soundPopup): the ONE place the launcher obtains haptic feedback. It wraps the platform feedback and applies the user's
 * launcher haptic strength (Off, Light, Medium, Strong; the Vibration slider of the Sound and Vibration sheet), read at the moment of
 * each call so a change applies at once. No other file may use LocalHapticFeedback (pinned by LauncherHapticsRoutingTest).
 */
@Composable
fun rememberLauncherHaptics(): HapticFeedback {
    val platform = LocalHapticFeedback.current
    LauncherHapticSettings.init(LocalContext.current)
    return remember(platform) { StrengthHaptics(platform) }
}

internal class StrengthHaptics(private val delegate: HapticFeedback) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val asked = if (hapticFeedbackType == HapticFeedbackType.LongPress) HapticKind.LongPress else HapticKind.Tick
        when (HapticPolicy.resolve(LauncherHapticSettings.strength.value, asked)) {
            null -> Unit
            HapticKind.LongPress -> delegate.performHapticFeedback(HapticFeedbackType.LongPress)
            HapticKind.Tick -> delegate.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
