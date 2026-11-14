package com.bos.sphere.core.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.bos.sphere.core.quality.MotionPrefs

/**
 * NFR-14: the system's animation scales as Compose state. Surfaces read this to turn their morph (scale, slide, spring)
 * into a cross-fade when the user has animations off. Public API only: `Settings.Global` plus a content observer.
 * Inspection mode (previews, screenshot tests) reports full motion unless a test forces it.
 */
@Composable
fun rememberMotionPrefs(): MotionPrefs {
    val forced = ReducedMotionTesting.forced
    if (forced != null) return forced
    if (LocalInspectionMode.current) return MotionPrefs.Default
    val context = LocalContext.current
    val state = remember { mutableStateOf(runCatching { MotionPrefs.read(context.contentResolver) }.getOrDefault(MotionPrefs.Default)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value =
                    runCatching { MotionPrefs.read(resolver) }.getOrDefault(MotionPrefs.Default)
            }
        }
        listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE)
            .forEach { runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(it), false, obs) } }
        onDispose { runCatching { resolver.unregisterContentObserver(obs) } }
    }
    return state.value
}

/** Test hook: when [force]d, [rememberMotionPrefs] returns that value instead of reading the system. */
object ReducedMotionTesting {
    @Volatile var forced: MotionPrefs? = null
        private set
    fun force(prefs: MotionPrefs?) { forced = prefs }
}
