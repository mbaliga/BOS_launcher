package com.bos.sphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bos.sphere.core.design.SphereTheme

/**
 * Single-Activity Compose host for the launcher surface. The window is transparent and
 * wallpaper-backed (see Theme.SphereLauncher), so the Compose content paints over the live
 * wallpaper. `singleTask` + the HOME intent-filter make the home button return here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SphereTheme {
                HomeScreen()
            }
        }
    }
}
