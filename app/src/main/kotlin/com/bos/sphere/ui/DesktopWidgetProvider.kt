package com.bos.sphere.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.bos.sphere.feature.desktop.DesktopWidgetHost
import com.bos.sphere.feature.grid.HostedWidget
import com.bos.sphere.feature.grid.LauncherWidgetHost
import com.bos.sphere.feature.grid.rememberLauncherWidgetHost
import com.bos.sphere.feature.grid.rememberWidgetPicker

/**
 * HOM-13: the desktop surface's real widgets. It draws an `AppWidgetHostView` through the same host the home uses (one host
 * per process, listening counted per surface), and adds widgets through the home's picker, bind and configure flow.
 */
class DesktopWidgetProvider(context: Context) : DesktopWidgetHost {
    private val app = context.applicationContext

    @Composable
    override fun Content(appWidgetId: Int, modifier: Modifier) {
        HostedWidget(rememberLauncherWidgetHost(), appWidgetId, modifier)
    }

    @Composable
    override fun rememberAdder(onAdded: (appWidgetId: Int, minWidthDp: Float, minHeightDp: Float) -> Unit): () -> Unit {
        val host = rememberLauncherWidgetHost()
        val density = LocalDensity.current.density
        val latest by rememberUpdatedState(onAdded)
        val picker = rememberWidgetPicker(host) { id, _ ->
            val info = host.providerInfo(id)
            latest(id, (info?.minWidth ?: 0) / density, (info?.minHeight ?: 0) / density)
        }
        return { picker.launch() }
    }

    override fun isBound(appWidgetId: Int): Boolean = LauncherWidgetHost.shared(app).providerInfo(appWidgetId) != null

    override fun release(appWidgetId: Int) = LauncherWidgetHost.shared(app).deleteWidget(appWidgetId)

    /** HOM-13: the provider's own resize rules (`resizeMode`, `minResizeWidth` / `minResizeHeight`, else its minimum), in dp. */
    override fun resizeLimits(appWidgetId: Int, density: Float): com.bos.sphere.feature.desktop.ResizeLimits? {
        val info = LauncherWidgetHost.shared(app).providerInfo(appWidgetId) ?: return null
        val d = density.coerceAtLeast(0.5f)
        return com.bos.sphere.feature.desktop.ResizeLimits(
            minWidthDp = (if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) / d,
            minHeightDp = (if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / d,
            horizontal = info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            vertical = info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
    }
}
