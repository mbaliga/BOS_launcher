package com.bos.sphere.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.LaunchpadView
import com.bos.sphere.core.data.LaunchpadViewBuilder
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.launchpad.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the widget shows, with no Android types so it is unit-tested. */
data class DigestWidgetModel(val title: String, val summary: String, val rows: List<String>)

object DigestWidgetLogic {
    const val MAX_ROWS = 4
    const val EMPTY = "Nothing in the digest"

    /**
     * The released digest (newest first), then a line saying how many are still held and when they arrive.
     * The title and rows come from the same [LaunchpadView] the Launchpad list uses, so rules and snoozes apply.
     */
    fun model(view: LaunchpadView, now: Long, zone: ZoneId, digestLabel: String = "Digest", t: (String, Array<out Any?>) -> String = { k, a -> String.format(k, *a) }): DigestWidgetModel {
        val digest = view.sections.firstOrNull { it.key == LaunchpadView.DIGEST_KEY }?.items.orEmpty()
        val rows = digest.take(MAX_ROWS).map(::rowText)
        val held = view.held.size
        val summary = when {
            held > 0 -> t("%d held for the digest at %s", arrayOf(held, timeText(view.nextRelease ?: now, zone)))
            digest.isEmpty() -> t(EMPTY, emptyArray())
            else -> t("%d in the digest", arrayOf(digest.size))
        }
        return DigestWidgetModel(digestLabel, summary, rows)
    }

    fun rowText(n: HubNotification): String {
        val app = n.appLabel.ifBlank { n.source }
        val what = n.title.ifBlank { n.summary }
        return if (app.isBlank()) what else if (what.isBlank()) app else "$app · $what"
    }

    fun timeText(epochMs: Long, zone: ZoneId): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMs).atZone(zone))
}

/** STR-11: builds the widget's RemoteViews and keeps the placed widgets current while the process lives. */
object DigestWidget {
    private val rowIds = intArrayOf(R.id.digest_row0, R.id.digest_row1, R.id.digest_row2, R.id.digest_row3)
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun views(context: Context, model: DigestWidgetModel): RemoteViews = RemoteViews(context.packageName, R.layout.widget_digest).apply {
        setTextViewText(R.id.digest_title, model.title)
        setTextViewText(R.id.digest_summary, model.summary)
        rowIds.forEachIndexed { i, id ->
            val text = model.rows.getOrNull(i)
            setTextViewText(id, text ?: "")
            setViewVisibility(id, if (text == null) android.view.View.GONE else android.view.View.VISIBLE)
        }
        // Tapping the widget opens the launcher (no hidden routes, no content in the intent).
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            val pi = PendingIntent.getActivity(context, 0, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            setOnClickPendingIntent(R.id.digest_root, pi)
        }
    }

    fun currentModel(context: Context, items: List<HubNotification>, now: Long = System.currentTimeMillis()): DigestWidgetModel {
        LaunchpadSettings.init(context)
        com.bos.sphere.core.data.GroupsSettings.init(context)
        val zone = ZoneId.systemDefault()
        // STR-13: a Priority group's notifications bypass the digest here too, so the widget agrees with Launchpad.
        val groups = com.bos.sphere.core.data.GroupsSettings.snapshot()
        val resolver = if (groups.groups.isEmpty()) null else com.bos.sphere.core.data.GroupResolver(groups, ContactsRuntime.peek(), zone)
        val view = LaunchpadViewBuilder.build(
            items, LaunchpadSettings.rules.value, LaunchpadSettings.prefs.value, LaunchpadSettings.policies.value,
            LaunchpadSettings.snoozed.value, now, zone, digestLabel = L10n.tr("Digest"), grouping = resolver,
        )
        return DigestWidgetLogic.model(view, now, zone, L10n.tr("Digest")) { k, a -> L10n.tr(k, *a) }
    }

    internal fun launchIo(block: suspend () -> Unit) { scope.launch { block() } }

    fun ids(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)): IntArray =
        manager.getAppWidgetIds(ComponentName(context, DigestWidgetProvider::class.java))

    @Volatile private var lastPainted: DigestWidgetModel? = null

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, items: List<HubNotification>) {
        if (ids.isEmpty()) return
        val model = currentModel(context, items)
        manager.updateAppWidget(ids, views(context, model))
        lastPainted = model
    }

    /**
     * Repaints only when what the widget shows has changed; returns whether it did. The collector calls this every
     * minute as well as on every feed change, because a digest releases at a clock time with no event to announce it,
     * and the system's own refresh is at best every 30 minutes.
     */
    fun updateIfChanged(context: Context, manager: AppWidgetManager, ids: IntArray, items: List<HubNotification>, now: Long = System.currentTimeMillis()): Boolean {
        if (ids.isEmpty()) return false
        val model = currentModel(context, items, now)
        if (model == lastPainted) return false
        manager.updateAppWidget(ids, views(context, model))
        lastPainted = model
        return true
    }

    /** Follows the notification feed and the rules; call from anything that runs while the app does. Safe to call repeatedly. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        LaunchpadSettings.init(app)
        val minute = kotlinx.coroutines.flow.flow { while (true) { emit(Unit); kotlinx.coroutines.delay(60_000) } }
        job = scope.launch {
            combine(
                NotificationProvider.notifications, LaunchpadSettings.rules, LaunchpadSettings.prefs,
                LaunchpadSettings.policies, LaunchpadSettings.snoozed, minute,
            ) { values -> @Suppress("UNCHECKED_CAST") (values[0] as List<HubNotification>) }
                .debounce(400)
                .collect { items ->
                    val m = AppWidgetManager.getInstance(app)
                    updateIfChanged(app, m, ids(app, m), items)
                }
        }
    }
}

class DigestWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) { val app = context.applicationContext; DigestWidget.launchIo { DigestWidget.start(app) } }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // First paint off the main thread (settings read from disk); the collector refreshes it afterwards.
        val pending = runCatching { goAsync() }.getOrNull()
        val app = context.applicationContext
        val paint = {
            DigestWidget.start(app)
            DigestWidget.update(app, appWidgetManager, appWidgetIds, NotificationProviderSnapshot.items())
        }
        if (pending == null) paint() // not delivered by a broadcast (direct call): paint inline
        else DigestWidget.launchIo { try { paint() } finally { pending.finish() } }
    }
}

/** The latest feed value for one-shot reads (the provider's first paint). */
internal object NotificationProviderSnapshot {
    fun items(): List<HubNotification> = NotificationProvider.snapshot
}
