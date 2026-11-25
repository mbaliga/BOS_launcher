package com.bos.sphere.feature.widgets.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bos.sphere.feature.widgets.SizeDp
import com.bos.sphere.feature.widgets.TouchGateLayout
import com.bos.sphere.feature.widgets.WidgetHosting
import kotlinx.coroutines.delay

/** Quiet period before telling a provider its new size, so slider drags don't flood it. */
private const val SIZE_REPORT_DEBOUNCE_MS = 300L

/**
 * Hosted widget views for one composition (one Activity): created once per widget id through the
 * shared AppWidgetHost and reused as tiles scroll in and out of the equator's culling window, so
 * a widget isn't re-inflated on every scrub. Cleared when the home composition goes away.
 */
internal class HostedViewCache(
    private val context: Context,
    private val hosting: WidgetHosting,
) {
    private val views = HashMap<Int, AppWidgetHostView>()
    private val reported = HashMap<Int, List<SizeDp>>()

    fun viewFor(appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView? =
        views[appWidgetId] ?: runCatching { hosting.host.createView(context, appWidgetId, info) }
            .getOrNull()
            ?.also { views[appWidgetId] = it }

    /** `updateAppWidgetSize(Bundle, List<SizeF>)` once per distinct size list. */
    fun reportSizes(appWidgetId: Int, sizes: List<SizeDp>) {
        if (sizes.isEmpty() || reported[appWidgetId] == sizes) return
        val view = views[appWidgetId] ?: return
        runCatching { view.updateAppWidgetSize(Bundle(), sizes.map { SizeF(it.width, it.height) }) }
            .onSuccess { reported[appWidgetId] = sizes }
    }

    fun evict(appWidgetId: Int) {
        views.remove(appWidgetId)
        reported.remove(appWidgetId)
    }

    fun retain(ids: Set<Int>) {
        views.keys.retainAll(ids)
        reported.keys.retainAll(ids)
    }

    fun clear() {
        views.clear()
        reported.clear()
    }
}

/**
 * A hosted widget's own RemoteViews, drawn as is, behind a [TouchGateLayout] that lets touches
 * through only while [interactive]. Reports [sizes] to the provider (debounced).
 */
@Composable
internal fun HostedWidgetView(
    cache: HostedViewCache,
    appWidgetId: Int,
    info: AppWidgetProviderInfo,
    interactive: Boolean,
    sizes: List<SizeDp>,
    modifier: Modifier = Modifier,
) {
    key(appWidgetId) {
        AndroidView(
            factory = { ctx ->
                TouchGateLayout(ctx).apply { cache.viewFor(appWidgetId, info)?.let { host(it) } }
            },
            update = { gate ->
                gate.passTouches = interactive
                if (gate.childCount == 0) cache.viewFor(appWidgetId, info)?.let(gate::host)
            },
            modifier = modifier,
        )
    }
    LaunchedEffect(appWidgetId, sizes) {
        delay(SIZE_REPORT_DEBOUNCE_MS)
        cache.reportSizes(appWidgetId, sizes)
    }
}

/**
 * Renders a provider's `previewLayout` (API 31+) the way launchers do: an unbound host view fed
 * `RemoteViews(package, previewLayout)`. Returns null when inflation fails (the host view would
 * otherwise show its generic error view), so the picker can fall back to the app icon.
 */
internal fun buildPreviewLayoutView(context: Context, info: AppWidgetProviderInfo): View? {
    if (info.previewLayout == 0) return null
    return runCatching {
        val view = PreviewHostView(context)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.setAppWidget(-1, info)
        view.updateAppWidget(RemoteViews(info.provider.packageName, info.previewLayout))
        view.takeUnless { it.failed }
    }.getOrNull()
}

private class PreviewHostView(context: Context) : AppWidgetHostView(context) {
    var failed = false
        private set

    override fun getErrorView(): View {
        failed = true
        return View(context)
    }
}
