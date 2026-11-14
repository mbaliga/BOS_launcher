package com.bos.sphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.desktop.TrackpadPanel

/** Shows the phone as trackpad and keyboard for the launcher's desktop surface (EXT-10). Launcher surface only. */
class PhoneInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SphereTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) { TrackpadPanel(Modifier.fillMaxSize()) }
            }
        }
    }
}
