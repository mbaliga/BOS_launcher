package com.bos.sphere

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.HiddenAppsStore
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.feature.sphere.RowMode
import com.bos.sphere.feature.sphere.SphereParams
import com.bos.sphere.feature.sphere.LauncherSurface
import com.bos.sphere.feature.sphere.rememberLauncherSurfaceState
import com.bos.sphere.feature.search.SearchKeyboard
import com.bos.sphere.ui.AppContextMenu
import com.bos.sphere.ui.Chrome
import com.bos.sphere.ui.DefaultLauncherBanner
import com.bos.sphere.ui.DotGridBackground
import com.bos.sphere.ui.HubPanel
import com.bos.sphere.ui.SettingsPanel

/**
 * The home root. Builds the (hidden-filtered) inventory, resolves the row split, hosts the
 * interactive [LauncherSurface], and overlays chrome, the hub, settings, the long-press menu and
 * the set-default prompt. Surface params are hoisted here so the settings panel tunes them live.
 */
@Composable
fun HomeScreen(homeSignal: Int) {
    val context = LocalContext.current
    val repository = remember { LauncherAppsAppRepository(context) }
    val hiddenStore = remember { HiddenAppsStore(context) }

    val allApps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by hiddenStore.hidden.collectAsStateWithLifecycle()
    val apps = remember(allApps, hidden) { allApps.filter { it.key !in hidden } }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var rowMode by remember { mutableStateOf(RowMode.Auto) }
    val rows = when (rowMode) {
        RowMode.One -> 1
        RowMode.Two -> 2
        RowMode.Auto -> if (isPortrait) 2 else 1
    }

    var params by remember { mutableStateOf(SphereParams()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<com.bos.sphere.core.data.AppEntry?>(null) }

    val surface = rememberLauncherSurfaceState(itemCount = apps.size, rows = rows, params = params)

    // Centre the equator on the middle app the first time the inventory arrives.
    var centred by remember { mutableStateOf(false) }
    LaunchedEffect(apps.size) {
        if (!centred && apps.isNotEmpty()) {
            surface.sphereState.position = (surface.sphereState.maxIndex / 2).toFloat()
            centred = true
        }
    }

    // Home-key: collapse globe + hub back to the equator.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            settingsOpen = false
            menuApp = null
            surface.zoom.animateTo(0f)
            surface.hub.animateTo(0f)
        }
    }

    val chromeAlpha = (1f - smooth(surface.zoomValue)) * (1f - surface.hubValue)

    Box(modifier = Modifier.fillMaxSize()) {
        DotGridBackground(Modifier.fillMaxSize())

        LauncherSurface(
            apps = apps,
            state = surface,
            onLaunch = { repository.launch(it) },
            onLongPressApp = { menuApp = it },
            onOpenSettings = { settingsOpen = true },
            modifier = Modifier
                .fillMaxSize()
                // In portrait, leave room for the always-keyboard so the equator stays clear.
                .padding(bottom = if (isPortrait) 300.dp else 0.dp),
        ) { fraction ->
            HubPanel(fraction = fraction)
        }

        // Chrome (clock / weather / battery), fading out of the way for globe + hub.
        Chrome(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chromeAlpha },
            onOpenSettings = { settingsOpen = true },
        )

        DefaultLauncherBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 56.dp),
        )

        SettingsPanel(
            visible = settingsOpen,
            params = params,
            rowMode = rowMode,
            onParamsChange = { params = it },
            onRowModeChange = { rowMode = it },
            onClose = { settingsOpen = false },
            modifier = Modifier.align(Alignment.TopEnd),
        )

        // Portrait: the always-open keyboard for type-to-search (hidden in globe / hub).
        if (isPortrait && surface.zoomValue < 0.4f && surface.hubValue < 0.5f) {
            SearchKeyboard(
                apps = apps,
                onLaunch = { repository.launch(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                isHidden = app.key in hidden,
                onDismiss = { menuApp = null },
                onAppInfo = { repository.openAppDetails(app); menuApp = null },
                onUninstall = { repository.requestUninstall(app); menuApp = null },
                onToggleHidden = {
                    hiddenStore.setHidden(app.key, app.key !in hidden)
                    menuApp = null
                },
            )
        }
    }
}

private fun smooth(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)
