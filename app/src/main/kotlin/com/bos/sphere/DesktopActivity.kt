package com.bos.sphere

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.feature.desktop.DesktopController
import com.bos.sphere.feature.desktop.DesktopPolicy
import com.bos.sphere.feature.desktop.DesktopSurface
import com.bos.sphere.feature.desktop.DesktopSurfaces
import com.bos.sphere.feature.desktop.DisconnectAction
import com.bos.sphere.feature.desktop.DisplayEffect
import com.bos.sphere.feature.desktop.DisplayIdentity
import com.bos.sphere.feature.desktop.SurfaceInfo
import kotlinx.coroutines.launch

/**
 * The launcher's desktop surface for an external display (EXT-02 to EXT-06). A resizable ordinary activity,
 * launched with that display's id through core-launch, never assumed to be the home app there (SPK-01).
 * It keeps its own arrangement and dock per display identity and survives resizing without recreation.
 */
class DesktopActivity : ComponentActivity() {
    private val token = Any()
    private lateinit var controller: DesktopController
    private var ownerDisplayId = 0
    private var identity = ""
    private var displayName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        controller = DesktopController.get(this).also { it.start() }
        ownerDisplayId = display?.displayId ?: 0
        val tracked = controller.state.value.displays[ownerDisplayId]
        displayName = tracked?.name ?: display?.name ?: "Display $ownerDisplayId"
        identity = tracked?.identity ?: DisplayIdentity.key(displayName, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

        // One desktop surface per display: a second launch on the same display just closes itself.
        if (DesktopSurfaces.list().any { it.ownerDisplayId == ownerDisplayId || it.currentDisplayId == ownerDisplayId }) { finish(); return }
        DesktopSurfaces.register(token, SurfaceInfo(ownerDisplayId, ownerDisplayId, identity))
        // The dock stream's rail replaces the basic recents dock on this display (DCK-07).
        com.bos.sphere.feature.desktop.DesktopDockSlot.provider = com.bos.sphere.ui.DesktopDockProvider

        LauncherStores.get(this).prefs.prefs.value.let { p ->
            HyleTheme.apply(
                when (p.themeMode) { ThemeMode.Dark -> ThemeVariant.Dark; ThemeMode.Amoled -> ThemeVariant.Amoled; ThemeMode.Light -> ThemeVariant.Light },
                p.accentColor,
            )
            L10n.setLanguage(p.language.code)
        }
        setContent {
            SphereTheme { DesktopSurface(controller, { display?.displayId ?: ownerDisplayId }, identity, displayName, token) }
        }

        // EXT-06: close cleanly or migrate when the display this surface was opened on goes away; reconnect yields a migrated surface.
        lifecycleScope.launch {
            controller.effects.collect { e ->
                val me = DesktopSurfaces.surfaces.value[token] ?: return@collect
                when (e) {
                    is DisplayEffect.Disconnected -> {
                        val primary = controller.state.value.primary?.id ?: 0
                        when (DesktopPolicy.onDisconnected(me, e.display.id, DesktopSurfaces.list(), primary, controller.migrateOnDisconnect)) {
                            DisconnectAction.Close -> finishAndRemoveTask()
                            DisconnectAction.MigrateToPrimary -> DesktopSurfaces.moved(token, primary)
                            DisconnectAction.None -> Unit
                        }
                    }
                    is DisplayEffect.Connected -> {
                        val primary = controller.state.value.primary?.id ?: 0
                        if (DesktopPolicy.migratedShouldYield(me, primary, e.display)) finishAndRemoveTask()
                    }
                    is DisplayEffect.Updated -> Unit
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DesktopSurfaces.setVisible(token, true)
                try { kotlinx.coroutines.awaitCancellation() } finally { DesktopSurfaces.setVisible(token, false) }
            }
        }
    }

    // The system moves tasks off a removed display; follow wherever the window now is.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        display?.displayId?.let { DesktopSurfaces.moved(token, it) }
    }

    /** Window focus decides which surface the phone's trackpad drives when two displays each show one (EXT-03). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        DesktopSurfaces.setWindowFocus(token, hasFocus)
    }

    /** Dock shortcuts reach this display's dock even when nothing in the surface holds focus (DCK-05). Other keys are untouched. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val dockId = display?.displayId ?: ownerDisplayId
        val command = com.bos.sphere.feature.grid.LauncherInput.registry.commandFor(
            com.bos.sphere.core.input.KeyChord(keyCode, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed),
        )
        if (command != null && com.bos.sphere.ui.DesktopDockProvider.handle(dockId, command)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        display?.displayId?.let { DesktopSurfaces.moved(token, it) }
    }

    override fun onDestroy() {
        DesktopSurfaces.unregister(token)
        super.onDestroy()
    }
}
