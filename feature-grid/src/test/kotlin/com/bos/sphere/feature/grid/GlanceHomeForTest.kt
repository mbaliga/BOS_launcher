package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.RightDestination

/**
 * Wave 38 test host: the calm Glance page ([GlanceSurface]) with the fixed chrome ([FixedHomeChrome]) above it, the way the home composes them, so the
 * tests that exercise the music line, the notch, the hotspots and the indicator keep one call.
 */
@Composable
fun GlanceHomeForTest(
    prefs: LauncherPrefs,
    media: MediaInfo?,
    hotspots: List<Hotspot>,
    appsByKey: Map<String, AppEntry>,
    rightDestinations: List<RightDestination>,
    hasLaunchpad: Boolean,
    onRun: (CornerItem, Corner) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLaunchpad: () -> Unit = {},
    onOpenRight: () -> Unit = {},
    indicator: Boolean = true,
    dark: Boolean = isDarkSurface(),
    onClockTap: () -> Unit = {},
) {
    var awake by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize()) {
        GlanceSurface(prefs, rightDestinations, hasLaunchpad, Modifier.fillMaxSize(), onOpenLaunchpad, onOpenRight, indicator, onAwake = { awake++ }, dark = dark, onClockTap = onClockTap)
        FixedHomeChrome(media, prefs.showMusic, prefs.glanceControlsSeconds, onPrevious, onPlayPause, onNext, dark = dark, corners = { MinimalHotspotLayer(hotspots, appsByKey, showHints = false, onRun = onRun, showIcons = true, iconTint = GlanceInk.secondary(dark)) })
    }
}
