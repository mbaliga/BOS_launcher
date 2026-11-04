package com.bos.sphere

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.widgets.WidgetHostActivityBinding

/**
 * Single-Activity Compose host for the launcher surface. The window is transparent and
 * wallpaper-backed (see Theme.SphereLauncher), so the Compose content paints over the live
 * wallpaper. `singleTask` + the HOME intent-filter make the home button return here; each such
 * return bumps [homeSignal] so the surface can reset (close globe/hub, recenter).
 */
class MainActivity : ComponentActivity() {

    private var homeSignal by mutableIntStateOf(0)

    /**
     * Hosts other apps' App Widgets: the shared AppWidgetHost listens while this activity is
     * started, and the bind prompt / configure results come back through here.
     */
    private val widgetHost = WidgetHostActivityBinding(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SphereTheme {
                HomeScreen(homeSignal = homeSignal, widgetHost = widgetHost)
            }
        }
    }

    /** Pressing Home while already here delivers a new MAIN/HOME intent — reset the surface. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }

    /**
     * Widget configure activities can only be started with a request code
     * (`AppWidgetHost.startAppWidgetConfigureActivityForResult`), so their result arrives here.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (widgetHost.onActivityResult(requestCode, resultCode, data)) return
        super.onActivityResult(requestCode, resultCode, data)
    }
}
