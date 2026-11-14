package com.bos.sphere

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.WidgetConfigureResults
import com.bos.sphere.feature.grid.InputModeProvider
import com.bos.sphere.feature.grid.LauncherInput
import com.bos.sphere.feature.grid.hotCorners
import com.bos.sphere.feature.grid.inputSensing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import com.bos.sphere.core.input.KeyLabels

/** [seq] makes repeated requests for the same action distinct. */
data class ExternalAction(val id: CornerActionId, val seq: Int)

/**
 * Single-Activity Compose host for the launcher surface. The window is transparent and
 * wallpaper-backed (see Theme.SphereLauncher), so the Compose content paints over the live
 * wallpaper. `singleTask` + the HOME intent-filter make the home button return here; each such
 * return bumps [homeSignal] so the surface can reset (close globe/hub, recenter).
 */
class MainActivity : ComponentActivity() {

    private var homeSignal by mutableIntStateOf(0)
    private var externalAction by mutableStateOf<ExternalAction?>(null)
    private var actionSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAction(intent)
        com.bos.sphere.core.data.CrashLoopGuard.onStart(this)
        window.decorView.postDelayed({ com.bos.sphere.core.data.CrashLoopGuard.onStable(applicationContext) }, com.bos.sphere.core.data.CrashLoopMath.WINDOW_MS)
        // Apply theme and language before the first frame so there is no flash of defaults.
        LauncherStores.get(this).prefs.prefs.value.let { p ->
            HyleTheme.apply(
                when (p.themeMode) { ThemeMode.Dark -> ThemeVariant.Dark; ThemeMode.Amoled -> ThemeVariant.Amoled; ThemeMode.Light -> ThemeVariant.Light },
                p.accentColor,
            )
            L10n.setLanguage(p.language.code)
        }
        setContent {
            SphereTheme {
                // The input layer observes pointer and touch from here without consuming anything (INP-06, INP-16).
                InputModeProvider {
                    Box(Modifier.fillMaxSize().inputSensing().hotCorners()) {
                        HomeScreen(homeSignal = homeSignal, externalAction = externalAction)
                    }
                }
            }
        }
    }

    /** A launcher-internal action forwarded by the motion service or its notification. */
    private fun handleAction(intent: Intent?): Boolean {
        val name = intent?.getStringExtra(EXTRA_ACTION) ?: return false
        val id = CornerActionId.entries.firstOrNull { it.name == name } ?: return false
        intent.removeExtra(EXTRA_ACTION)
        externalAction = ExternalAction(id, ++actionSeq)
        return true
    }

    companion object {
        const val EXTRA_ACTION = "com.bos.sphere.extra.ACTION"
    }

    /** The system-launched widget configure screen reports back here (not through Compose). */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        WidgetConfigureResults.deliver(requestCode, resultCode)
    }

    /** Controller buttons become arrows, Enter and Esc; registered shortcuts and focus keys are handled before Compose sees them (INP-04, INP-20). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val e = LauncherInput.translate(event)
        if (LauncherInput.onKeyEvent(e)) return true
        return super.dispatchKeyEvent(e)
    }

    /** Publishes every registered shortcut to the system's keyboard shortcut helper (INP-04). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        val infos = LauncherInput.registry.all().mapNotNull { (cmd, chord) ->
            chord?.let {
                val mods = (if (it.ctrl) KeyEvent.META_CTRL_ON else 0) or (if (it.alt) KeyEvent.META_ALT_ON else 0) or
                    (if (it.shift) KeyEvent.META_SHIFT_ON else 0) or (if (it.meta) KeyEvent.META_META_ON else 0)
                KeyboardShortcutInfo(L10n.tr(cmd.label), it.keyCode, mods)
            }
        }
        data.add(KeyboardShortcutGroup(L10n.tr("Liftoff"), infos))
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    /** Pressing Home while already here delivers a new MAIN/HOME intent — reset the surface. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!handleAction(intent)) homeSignal++
    }
}
