package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PuckSlot
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.DockPreviewStage
import com.bos.sphere.feature.dock.PanelApp
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.dock.dockPreviewDetail
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.PreviewDisc
import com.bos.sphere.feature.grid.PreviewRadar
import com.bos.sphere.feature.grid.PreviewSurfaces
import com.bos.sphere.feature.grid.PuckAction

/**
 * Wave 57 (previewAll), CFG-51..CFG-53: what the floating Settings Preview draws for the surfaces that are not the home: the real Radar with a
 * sample query, the real disc with the user's ring, the real Comms column with sample notifications, the dock rail with its settings. Every
 * renderer reads the current prefs (coalesced by the window) and the app list the home already holds; nothing new is listened to.
 */
@Composable
internal fun rememberPreviewSurfaces(
    prefs: LauncherPrefs,
    apps: List<AppEntry>,
    appsByKey: Map<String, AppEntry>,
    puckSlots: List<PuckSlot>,
    puckSegments: Int,
    puckLabels: Boolean,
    homeOnGlance: () -> Boolean,
): PreviewSurfaces {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val prefsNow = rememberUpdatedState(prefs)
    val appsNow = rememberUpdatedState(apps)
    val ring = rememberUpdatedState(Triple(puckSlots, puckSegments, puckLabels))
    val byKey = rememberUpdatedState(appsByKey)
    val onGlance = rememberUpdatedState(homeOnGlance)
    val manager = remember(context) { WorkspaceRepository.get(context) }
    return remember(manager, landscape) {
        PreviewSurfaces(
            prefs = { prefsNow.value },
            homeOnGlance = { onGlance.value() },
            radar = { p -> PreviewRadar(p, appsNow.value) },
            disc = { p ->
                val (slots, segments, labels) = ring.value
                val actions = remember(slots, segments, byKey.value) { previewPuckActions(slots, segments, byKey.value) }
                PreviewDisc(p, appsNow.value, actions, segments, labels)
            },
            sidePanel = { p -> com.bos.sphere.ui.CommsPreview(p.launchpadTheme) },
            dock = { _ ->
                val panel = remember(appsNow.value) { appsNow.value.map { PanelApp(it.key, it.label, it.packageName, com.bos.sphere.core.launch.LaunchTarget(it.packageName)) } }
                DockPreviewStage(manager, panel, landscape)
            },
            dockDetail = { dockPreviewDetail(manager, landscape) },
        )
    }
}

/** The disc's shortcut ring as the home builds it, without any action wired (the Preview is inert). */
internal fun previewPuckActions(slots: List<PuckSlot>, segments: Int, appsByKey: Map<String, AppEntry>): List<PuckAction> = slots.take(segments).mapIndexed { i, slot ->
    when (slot) {
        PuckSlot.Empty -> PuckAction(L10n.tr("Drawer shortcut %d, empty", i + 1)) {}
        is PuckSlot.Action -> PuckAction(L10n.tr(slot.id.label), Glyphs.forAction(slot.id)) {}
        is PuckSlot.App -> appsByKey[slot.key].let { app -> PuckAction(app?.label ?: L10n.tr("App not installed"), if (app == null) Glyphs.Apps else null, app, available = app != null) {} }
    }
}

