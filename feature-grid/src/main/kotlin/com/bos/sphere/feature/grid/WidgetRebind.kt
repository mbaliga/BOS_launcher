package com.bos.sphere.feature.grid

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bos.sphere.core.data.PendingWidget

/** Why a re-add did not finish; [Done] means the widget is bound and (if it needs it) configured. */
enum class RebindOutcome { Done, ProviderMissing, Declined, Failed }

/** Handle returned by [rememberWidgetRebinder]. */
class WidgetRebinder internal constructor(private val onStart: (PendingWidget) -> Unit) {
    /** Re-adds [widget]: the system's bind consent (when needed), then the provider's configure screen (when it has one). */
    fun start(widget: PendingWidget) = onStart(widget)
}

/**
 * CFG-02: re-adds a widget an import listed as pending, one consent step each. Finds the provider by the component the export
 * recorded, binds it (`bindAppWidgetIdIfAllowed`, else the system's ACTION_APPWIDGET_BIND screen), runs the configure screen if
 * the provider has one, then calls [onResult]. [onResult] gets the new id and label on [RebindOutcome.Done]; on any other outcome
 * the allocated id is deleted and nothing changes. Public AppWidget APIs only; the host must not be listening (the home owns that).
 */
@Composable
fun rememberWidgetRebinder(
    host: LauncherWidgetHost,
    onResult: (widget: PendingWidget, outcome: RebindOutcome, appWidgetId: Int, label: String) -> Unit,
): WidgetRebinder {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current by rememberUpdatedState(onResult)
    var pendingId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var slot by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun finish(outcome: RebindOutcome) {
        val id = pendingId
        val w = PendingWidget(slot, label, provider)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        info = null
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val shown = host.providerInfo(id)?.loadLabel(context.packageManager)?.toString().orEmpty()
        if (outcome == RebindOutcome.Done && host.providerInfo(id) != null) current(w, outcome, id, shown.ifBlank { label })
        else {
            host.deleteWidget(id)
            current(w, if (outcome == RebindOutcome.Done) RebindOutcome.Failed else outcome, AppWidgetManager.INVALID_APPWIDGET_ID, label)
        }
    }

    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        finish(if (r.resultCode == Activity.RESULT_OK) RebindOutcome.Done else RebindOutcome.Declined)
    }

    fun configureOrFinish() {
        val id = pendingId
        val p = info ?: host.providerInfo(id)
        if (p?.configure == null || isConfigurationOptional(p)) return finish(RebindOutcome.Done)
        val launched = p.profile == Process.myUserHandle() && runCatching {
            configureLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(p.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            )
        }.isSuccess
        if (!launched) finish(RebindOutcome.Failed)
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) configureOrFinish() else finish(RebindOutcome.Declined)
    }

    return remember {
        WidgetRebinder { w ->
            if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) finish(RebindOutcome.Declined)
            slot = w.slot; label = w.label; provider = w.provider
            val component = ComponentName.unflattenFromString(w.provider)
            val found = component?.let { c ->
                runCatching { host.appWidgetManager.installedProviders }.getOrDefault(emptyList()).firstOrNull { it.provider == c }
            }
            if (found == null) {
                current(w, RebindOutcome.ProviderMissing, AppWidgetManager.INVALID_APPWIDGET_ID, w.label)
                return@WidgetRebinder
            }
            val id = runCatching { host.allocateAppWidgetId() }.getOrNull()
            if (id == null) {
                current(w, RebindOutcome.Failed, AppWidgetManager.INVALID_APPWIDGET_ID, w.label)
                return@WidgetRebinder
            }
            pendingId = id
            info = found
            val bound = runCatching { host.appWidgetManager.bindAppWidgetIdIfAllowed(id, found.profile, found.provider, null) }.getOrDefault(false)
            if (bound) configureOrFinish()
            else {
                val asked = runCatching {
                    bindLauncher.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, found.provider)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, found.profile),
                    )
                }.isSuccess
                if (!asked) finish(RebindOutcome.Failed)
            }
        }
    }
}

private fun isConfigurationOptional(info: AppWidgetProviderInfo): Boolean {
    val f = info.widgetFeatures
    return f and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
        f and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
}
