package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow

/**
 * W25: the one media source. The controls and the title are drawn only by the Glance notch (HOM-23); there is no bottom strip and nothing in the dock.
 * The current media, or null while nothing is playing / paused (or when [enabled] is false). Starts and stops [MediaNow] with the composition.
 */
@Composable
fun rememberMediaInfo(enabled: Boolean): State<MediaInfo?> {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (enabled) MediaNow.start(context)
        onDispose { if (enabled) MediaNow.stop() }
    }
    val flow = remember { MediaNow.info }
    val state = flow.collectAsState()
    return if (enabled) state else remember { androidx.compose.runtime.mutableStateOf<MediaInfo?>(null) }
}
