package com.bos.sphere

import android.app.Application
import android.content.res.Configuration
import com.bos.sphere.feature.homewidgets.HomeWidgets

/**
 * Process start-up. Its one job: start Sphere's own home-screen widgets (`:feature-homewidgets`)
 * whenever the process starts, including when Android starts it only to bind the notification
 * listener or to deliver a widget broadcast while another app is the home app. The widgets' taps
 * land on [MainActivity].
 */
class SphereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HomeWidgets.init(this, MainActivity::class.java)
    }

    /** The widgets' size keys follow the font scale; they are redrawn when it changes. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        HomeWidgets.onFontScale(this, newConfig.fontScale)
    }
}
