package com.bos.sphere.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bos.sphere.DefaultLauncher
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.delay

/**
 * A whisper of a hint — one small, dim line that fades in a moment after opening and away again
 * after a few seconds, only while Liftoff isn't the default home app. Tapping it opens the role
 * request. (Settings → System → Set as default home is always there.)
 */
@Composable
fun DefaultLauncherBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefault by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }
    var show by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefault = DefaultLauncher.isDefault(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isDefault) {
        if (!isDefault) {
            delay(2_500)
            show = true
            delay(9_000)
            show = false
        } else {
            show = false
        }
    }

    AnimatedVisibility(visible = show && !isDefault, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = L10n.tr("Make default") + " ›",
            color = HyleColors.InkDim,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .clickable {
                    val intent = DefaultLauncher.requestRoleIntent(context) ?: DefaultLauncher.homeSettingsIntent()
                    context.startActivity(intent)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
