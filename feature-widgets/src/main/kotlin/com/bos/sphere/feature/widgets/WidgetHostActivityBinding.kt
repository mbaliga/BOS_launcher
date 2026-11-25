package com.bos.sphere.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Ties App Widget hosting to the home Activity:
 *
 * - the host listens while the activity is started (`startListening` in onStart, `stopListening`
 *   in onStop), and placements are reconciled on every start;
 * - the bind prompt (`ACTION_APPWIDGET_BIND`) runs through an Activity Result launcher registered
 *   here, and configure activities through `AppWidgetHost.startAppWidgetConfigureActivityForResult`
 *   whose result the activity forwards to [onActivityResult].
 *
 * Construct it as a field of the activity (before it is started). No permission is needed:
 * `BIND_APPWIDGET` is system-only, so binding falls back to the user-facing prompt.
 */
class WidgetHostActivityBinding(private val activity: ComponentActivity) {

    /** Created lazily: the activity has no application context while its fields initialise. */
    val hosting: WidgetHosting by lazy { WidgetHosting.get(activity.applicationContext) }

    /** Sizes of the widget being added, passed along to the bind prompt as its options. */
    private var pendingOptions: List<SizeDp> = emptyList()

    private val bindLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val waiting = (hosting.flow.step as? BindStep.AwaitingPermission)?.appWidgetId
            val granted = result.resultCode == Activity.RESULT_OK &&
                waiting != null && hosting.isBound(waiting)
            run(hosting.flow.onPermissionResult(granted))
        }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                hosting.reconcile()
                runCatching { hosting.host.startListening() }
            }

            override fun onStop(owner: LifecycleOwner) {
                runCatching { hosting.host.stopListening() }
            }
        })
    }

    /** Starts adding [item] at [target]; [sizes] are the sizes it will be shown at. */
    fun addWidget(item: PickerItem, target: PlacementTarget, sizes: List<SizeDp>) {
        val id = runCatching { hosting.allocateId() }.getOrElse {
            toast(R.string.widget_add_failed)
            return
        }
        val defaultHeightDp = item.info.minHeight / activity.resources.displayMetrics.density
        val request = AddRequest(
            providerPackage = item.entry.packageName,
            providerClass = item.entry.providerClass,
            userSerial = item.entry.userSerial,
            target = target,
            hubSize = WidgetSizing.defaultHubSize(defaultHeightDp),
        )
        val needsConfigure = PickerModel.needsConfigure(item.info.configure != null, item.info.widgetFeatures)
        val allowed = runCatching {
            hosting.bindIfAllowed(id, item, WidgetHosting.optionsFor(sizes))
        }.getOrElse {
            run(hosting.flow.onBindError(id))
            return
        }
        pendingOptions = sizes
        run(hosting.flow.onAllocated(id, request, allowed, needsConfigure))
    }

    /** Re-runs the configure activity of a placed, reconfigurable widget. */
    fun reconfigure(appWidgetId: Int) {
        run(hosting.flow.startReconfigure(appWidgetId))
    }

    /** Forwarded from `Activity.onActivityResult`; true if it was a configure result. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CONFIGURE) return false
        run(hosting.flow.onConfigureResult(resultCode == Activity.RESULT_OK))
        return true
    }

    private fun run(effects: List<BindEffect>) {
        for (effect in effects) {
            when (effect) {
                is BindEffect.LaunchBindPermission -> launchBindPrompt(effect)
                is BindEffect.LaunchConfigure -> launchConfigure(effect.appWidgetId)
                is BindEffect.Place -> hosting.place(effect.appWidgetId, effect.request)
                is BindEffect.DeleteId -> {
                    hosting.deleteId(effect.appWidgetId)
                    if (effect.reason == DeleteReason.Failed) toast(R.string.widget_add_failed)
                }
            }
        }
    }

    private fun launchBindPrompt(effect: BindEffect.LaunchBindPermission) {
        val user = hosting.userForSerial(effect.request.userSerial)
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, effect.appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, WidgetHosting.componentOf(effect.request))
            if (user != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user)
            if (pendingOptions.isNotEmpty()) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, WidgetHosting.optionsFor(pendingOptions))
            }
        }
        runCatching { bindLauncher.launch(intent) }.onFailure { run(hosting.flow.onLaunchFailed()) }
    }

    private fun launchConfigure(appWidgetId: Int) {
        runCatching {
            hosting.host.startAppWidgetConfigureActivityForResult(
                activity, appWidgetId, 0, REQUEST_CONFIGURE, null,
            )
        }.onFailure { run(hosting.flow.onLaunchFailed()) }
    }

    private fun toast(res: Int) {
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Below the Activity Result registry's generated range (≥ 0x10000), so they can't clash. */
        const val REQUEST_CONFIGURE = 0x0A51
    }
}
