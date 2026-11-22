package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.sharesWallpaper

/** W55 (fixedBackdrop): the pure rules of the fixed home backdrop. */
object FixedHomeBackdrop {
    const val TAG = "home.backdrop"
    const val PAGES_TAG = "home.backdrop.pages"

    /**
     * Opacity of the Pages wallpaper over the Glance wallpaper for a pager position counted in stops (0 = Glance, 1 = the pages; fractions while a swipe is in
     * flight). With Glance off every stop is a page, so the Pages wallpaper is fully shown. The position follows the finger, so the two cross-fade as the pager
     * settles and nothing slides.
     */
    fun pagesAlpha(glanceEnabled: Boolean, position: Float): Float = if (!glanceEnabled) 1f else position.coerceIn(0f, 1f)
}

/**
 * W55 (fixedBackdrop): THE wallpaper of the home, drawn ONCE below the pager. It never takes part in the pager's scroll, the edit-mode page scale or the Comms/Recents
 * card (the host places it behind those layers): pages and the Glance content move over it. When the Pages wallpaper is the Glance wallpaper, one instance serves both
 * and nothing changes on a swipe. When they differ, the Glance wallpaper is the base and the Pages wallpaper fades in over it with [position] (alpha only, read in a
 * graphicsLayer lambda, so a swipe does not recompose this layer); at either end only the one wallpaper that shows is composed, so one live wallpaper draws at a time.
 * [overlay] is drawn above the wallpaper (the indicator effects), also once and fixed.
 */
@Composable
fun FixedHomeBackdrop(
    prefs: LauncherPrefs,
    position: () -> Float,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    val read by rememberUpdatedState(position)
    val glanceOn = prefs.glanceEnabled
    Box(modifier.fillMaxSize().testTag(FixedHomeBackdrop.TAG)) {
        if (prefs.sharesWallpaper(WallpaperSurface.Pages)) {
            HomeSurfaceBackground(WallpaperSurface.Glance, prefs, Modifier.fillMaxSize())
        } else {
            val showGlance by remember(glanceOn) { derivedStateOf { FixedHomeBackdrop.pagesAlpha(glanceOn, read()) < 0.999f } }
            val showPages by remember(glanceOn) { derivedStateOf { FixedHomeBackdrop.pagesAlpha(glanceOn, read()) > 0.001f } }
            if (showGlance) HomeSurfaceBackground(WallpaperSurface.Glance, prefs, Modifier.fillMaxSize())
            if (showPages) Box(Modifier.fillMaxSize().testTag(FixedHomeBackdrop.PAGES_TAG).graphicsLayer { alpha = FixedHomeBackdrop.pagesAlpha(glanceOn, read()) }) {
                HomeSurfaceBackground(WallpaperSurface.Pages, prefs, Modifier.fillMaxSize())
            }
        }
        overlay()
    }
}
