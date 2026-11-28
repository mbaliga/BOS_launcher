package com.bos.sphere.feature.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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
    // W42: when every route of a command failed, say so once ("Can't control <app>") instead of doing nothing.
    LaunchedEffect(enabled) {
        if (enabled) MediaNow.failures.collect { app -> Toast.makeText(context, com.bos.sphere.core.design.L10n.tr("Can't control %s", app), Toast.LENGTH_SHORT).show() }
    }
    val flow = remember { MediaNow.info }
    val state = flow.collectAsState()
    return if (enabled) state else remember { androidx.compose.runtime.mutableStateOf<MediaInfo?>(null) }
}
