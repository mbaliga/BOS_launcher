package com.bos.sphere

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.feature.grid.HostedWidget
import com.bos.sphere.feature.grid.LauncherWidgetHost
import com.bos.sphere.feature.grid.canReconfigureWidget
import com.bos.sphere.feature.grid.launcherItem
import com.bos.sphere.feature.grid.reconfigureWidget
import com.bos.sphere.feature.grid.rememberLauncherWidgetHost
import com.bos.sphere.feature.grid.rememberWidgetPicker
import com.bos.sphere.ui.HubPrefs
import com.bos.sphere.ui.PanelWidget
import com.bos.sphere.ui.PanelWidgetSizing
import com.bos.sphere.ui.PanelWidgetStore
import com.bos.sphere.ui.PanelWidgetsApi
import com.bos.sphere.ui.PanelWidgetsSection

/**
 * Wave 52 (panelWidgets): the body of the Comms panel's Widgets section. It reuses the home's one [LauncherWidgetHost] (counted listening: it listens
 * while this section is composed, which is only while it is the shown section of an open panel, and the activity is started), the home's picker with its bind
 * consent and configure flow ([rememberWidgetPicker]), and the home's [HostedWidget]. The list lives in [PanelWidgetStore] (pref file `launchpad_hub`).
 */
@Composable
fun PanelWidgetsBody() {
    val context = LocalContext.current
    val host = rememberLauncherWidgetHost()
    val store = remember { PanelWidgetStore(HubPrefs.of(context)) }
    val picker = rememberWidgetPicker(host) { id, label -> store.add(PanelWidget(id, label)) }
    val density = LocalDensity.current
    val api = remember(host, picker, density) { panelWidgetsApi(context, host, density.density, { picker.launch() }) }
    PanelWidgetsSection(store, api)
}

internal fun panelWidgetsApi(context: android.content.Context, host: LauncherWidgetHost, density: Float, addWidget: () -> Unit) = PanelWidgetsApi(
    // Safe mode (the crash-loop guard) keeps hosted widgets off, as it does for the left page widget; the card then reads as unavailable.
    available = { id -> !CrashLoopGuard.safeMode && host.providerInfo(id) != null },
    heightDp = { id -> sizeOf(host.providerInfo(id), density) },
    canConfigure = { id -> canReconfigureWidget(host, id) },
    configure = { id -> reconfigureWidget(context, host, id) },
    addWidget = addWidget,
    released = { id -> host.deleteWidget(id) },
    itemModifier = { id, activate, menu -> Modifier.launcherItem(id, FocusKind.Item, onActivate = activate, onMenu = menu, ringRadiusDp = 20f) },
    render = { id, modifier -> HostedWidget(host, id, modifier) },
)

/** The card's widget height: the provider's minimum, its resize limits (px to dp) through [PanelWidgetSizing]. */
internal fun sizeOf(info: AppWidgetProviderInfo?, density: Float): Float {
    if (info == null) return PanelWidgetSizing.MIN_DP
    fun dp(px: Int) = px / density
    return PanelWidgetSizing.heightDp(dp(info.minHeight), dp(info.minResizeHeight), if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && info.maxResizeHeight > 0) dp(info.maxResizeHeight) else 0f)
}
