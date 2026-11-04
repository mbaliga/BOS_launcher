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
import com.bos.sphere.feature.homewidgets.HomeWidgets
import com.bos.sphere.feature.widgets.WidgetHostActivityBinding

/**
 * Single-Activity Compose host for the launcher surface. The window is transparent and
 * wallpaper-backed (see Theme.SphereLauncher), so the Compose content paints over the live
 * wallpaper. `singleTask` + the HOME intent-filter make the home button return here; each such
 * return bumps [homeSignal] so the surface can reset (close globe/hub, recenter).
 *
 * Sphere's own home-screen widgets (Needs you) open this activity with [HomeWidgets.EXTRA_OPEN] =
 * [HomeWidgets.OPEN_HUB]; that bumps [hubSignal] instead, which opens home with the hub pulled down.
 */
class MainActivity : ComponentActivity() {

    private var homeSignal by mutableIntStateOf(0)
    private var hubSignal by mutableIntStateOf(0)

    /**
     * Hosts other apps' App Widgets: the shared AppWidgetHost listens while this activity is
     * started, and the bind prompt / configure results come back through here.
     */
    private val widgetHost = WidgetHostActivityBinding(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && opensHub(intent)) hubSignal = 1
        setContent {
            SphereTheme {
                HomeScreen(homeSignal = homeSignal, hubSignal = hubSignal, widgetHost = widgetHost)
            }
        }
    }

    /**
     * Pressing Home while already here delivers a new MAIN/HOME intent — reset the surface. A
     * widget's "open the hub" intent opens the hub instead.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (opensHub(intent)) hubSignal++ else homeSignal++
    }

    private fun opensHub(intent: Intent?): Boolean =
        intent?.getStringExtra(HomeWidgets.EXTRA_OPEN) == HomeWidgets.OPEN_HUB

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
