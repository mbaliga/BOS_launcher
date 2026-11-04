package com.bos.sphere.feature.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.SizeF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the launcher knows about a placed widget's provider right now. */
sealed interface WidgetStatus {
    /** Bound and its provider is present. */
    data class Available(
        val info: AppWidgetProviderInfo,
        val label: String,
        val appLabel: String,
        val reconfigurable: Boolean,
        val isWorkProfile: Boolean,
    ) : WidgetStatus

    /**
     * No provider to show: either `getAppWidgetInfo` returned null for a bound id (mainly a
     * restored widget whose app isn't installed yet, or a non-system provider in safe mode), or,
     * with [restoring], the placement is still waiting for the backup restore to give it its id
     * here. A disabled or uninstalled provider app is different: the system deletes its widgets,
     * so those placements are dropped, not shown here.
     */
    data class Unavailable(val appLabel: String, val restoring: Boolean = false) : WidgetStatus
}

/** A provider offered by the picker, with the platform objects needed to bind it. */
class PickerItem(
    val entry: PickerEntry,
    val info: AppWidgetProviderInfo,
    val user: UserHandle,
)

/**
 * Process-wide App Widget hosting: one [AppWidgetHost] with a fixed [HOST_ID], created once per
 * process (Activity recreation, the restore receiver and the UI all share it), the placement store,
 * the bind flow, and the live status of every placed widget. Main thread unless noted.
 *
 * Nothing here touches the network; it reads only the system's widget and package services.
 */
class WidgetHosting private constructor(private val appContext: Context) {

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val pm: PackageManager = appContext.packageManager

    internal val host: SphereAppWidgetHost = SphereAppWidgetHost(appContext, HOST_ID) { refresh() }
    private val store = WidgetPlacementStore(appContext)
    internal val flow = BindFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconcileQueued = false
    private val queuedReconcile = Runnable {
        reconcileQueued = false
        reconcile()
    }

    val placements: StateFlow<List<WidgetPlacement>> = store.placements

    private val _status = MutableStateFlow<Map<Int, WidgetStatus>>(emptyMap())

    /** Status per placed id. A placed id missing from the map has not been checked yet. */
    val status: StateFlow<Map<Int, WidgetStatus>> = _status.asStateFlow()

    private val _focusRequest = MutableStateFlow<Int?>(null)

    /** The id of a widget just placed, so the home surface can bring it into view once. */
    val focusRequest: StateFlow<Int?> = _focusRequest.asStateFlow()

    fun consumeFocusRequest(appWidgetId: Int) {
        if (_focusRequest.value == appWidgetId) _focusRequest.value = null
    }

    /**
     * Brings placements and the host's ids into agreement (see [PlacementReconciler]) and refreshes
     * every placement's status. Runs on start and after a restore; provider and package changes
     * go through [refresh]. If the host's ids can't be read, nothing is deleted or dropped.
     * Restore-pending placements never touch the host: no info lookup, no delete.
     */
    fun reconcile() {
        val placed = store.placements.value
        val infos = placed.filterNot { it.isRestorePending }
            .associate { it.appWidgetId to infoFor(it.appWidgetId) }
        val waitingForRestore = placed.any { it.isRestorePending }
        val hostIds = runCatching { host.appWidgetIds.toList() }.getOrNull()
        if (hostIds != null) {
            val plan = PlacementReconciler.plan(
                placements = placed,
                hostIds = hostIds,
                pendingIds = flow.pendingIds,
                reservedIds = store.reservedIds,
                restoreWindowOpen = waitingForRestore && store.restoreWindowOpen(),
            ) { infos[it] != null }
            plan.idsToDelete.forEach { id -> runCatching { host.deleteAppWidgetId(id) } }
            if (plan.dropped.isNotEmpty()) {
                val gone = plan.dropped.mapTo(HashSet()) { it.appWidgetId }
                store.update { list -> list.filterNot { it.appWidgetId in gone } }
            }
            if (plan.restoring.isEmpty()) store.clearRestoreWindow()
        }
        publishStatus(infos)
    }

    /**
     * Re-checks after packages or providers changed. Host callbacks arrive in bursts (each
     * provider change, each removed id, and `startListening` replaying what it missed while
     * stopped), so they are coalesced into one [reconcile] posted to the main thread.
     */
    fun refresh() {
        if (reconcileQueued) return
        reconcileQueued = true
        mainHandler.post(queuedReconcile)
    }

    /**
     * Drops the host's references to hosted views, which hold the Activity they were created
     * with. Called when the home composition goes away; live views are re-created on next use.
     */
    internal fun releaseViews() {
        host.releaseViews()
    }

    /**
     * Removes a placed widget and gives its id back to the system. A restore-pending placement
     * only holds an id from another device, so the host is not touched for it.
     */
    fun remove(appWidgetId: Int) {
        if (appWidgetId > 0) runCatching { host.deleteAppWidgetId(appWidgetId) }
        store.update { PlacementEdits.remove(it, appWidgetId) }
        _status.value = _status.value - appWidgetId
    }

    fun moveToHub(appWidgetId: Int) {
        store.update { PlacementEdits.moveToHub(it, appWidgetId) }
        _focusRequest.value = appWidgetId
    }

    fun moveToEquator(appWidgetId: Int, equatorIndex: Int) {
        store.update { PlacementEdits.moveToEquator(it, appWidgetId, equatorIndex) }
        _focusRequest.value = appWidgetId
    }

    fun setHubSize(appWidgetId: Int, size: HubSize) {
        store.update { PlacementEdits.setHubSize(it, appWidgetId, size) }
    }

    /**
     * `ACTION_APPWIDGET_HOST_RESTORED`: give restore-pending placements named in this broadcast
     * their new ids. Placements it doesn't name keep waiting (a later broadcast may name them)
     * until their restore window closes.
     */
    fun onHostRestored(oldIds: IntArray, newIds: IntArray) {
        store.update { PlacementReconciler.remapRestored(it, oldIds, newIds).placements }
        reconcile()
    }

    // --- bind flow plumbing (driven by WidgetHostActivityBinding) ---------------------------

    internal fun allocateId(): Int = host.allocateAppWidgetId()

    internal fun bindIfAllowed(id: Int, item: PickerItem, options: Bundle): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, item.user, item.info.provider, options)

    internal fun isBound(id: Int): Boolean = infoFor(id) != null

    internal fun deleteId(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    internal fun place(appWidgetId: Int, request: AddRequest) {
        val placement = request.toPlacement(appWidgetId)
        var displaced = emptyList<Int>()
        store.update { list ->
            when (request.target.surface) {
                WidgetSurface.Equator ->
                    PlacementEdits.insertOnEquator(list, placement, request.target.equatorIndex)
                WidgetSurface.Hub -> PlacementEdits.appendToHub(list, placement)
                WidgetSurface.Page -> PlacementEdits.putOnPage(list, placement).also { displaced = it.second }.first
            }
        }
        displaced.filter { it > 0 }.forEach { id -> runCatching { host.deleteAppWidgetId(id) } }
        _status.value = _status.value + (appWidgetId to statusOf(appWidgetId, infoFor(appWidgetId)))
        _focusRequest.value = appWidgetId
    }

    internal fun userForSerial(serial: Long): UserHandle? =
        runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()

    // --- picker ------------------------------------------------------------------------------

    /**
     * Every pickable provider in every profile [LauncherApps] reports, with labels. Binder and
     * package-manager work: call off the main thread.
     */
    fun loadPickerItems(): List<PickerItem> {
        val me = Process.myUserHandle()
        val profiles = runCatching { launcherApps.profiles }.getOrNull().orEmpty().ifEmpty { listOf(me) }
        val density = appContext.resources.displayMetrics.density
        return profiles.flatMap { user ->
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(-1L)
            val providers = runCatching { manager.getInstalledProvidersForProfile(user) }
                .getOrNull().orEmpty()
            providers.mapNotNull { info ->
                runCatching {
                    val appInfo = info.activityInfo.applicationInfo
                    val appLabel = pm.getApplicationLabel(appInfo).toString()
                    PickerItem(
                        entry = PickerEntry(
                            key = "${info.provider.flattenToString()}#$serial",
                            packageName = info.provider.packageName,
                            providerClass = info.provider.className,
                            appLabel = appLabel,
                            label = info.loadLabel(pm) ?: info.provider.shortClassName,
                            description = info.loadDescription(appContext)?.toString(),
                            userSerial = serial,
                            isWorkProfile = user != me,
                            widgetCategory = info.widgetCategory,
                            widgetFeatures = info.widgetFeatures,
                            spanX = PickerModel.span(info.targetCellWidth, info.minWidth / density),
                            spanY = PickerModel.span(info.targetCellHeight, info.minHeight / density),
                        ),
                        info = info,
                        user = user,
                    )
                }.getOrNull()
            }
        }.filter { PickerModel.isPickable(it.entry) }
    }

    // --- internals ---------------------------------------------------------------------------

    private fun infoFor(id: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    private fun publishStatus(infos: Map<Int, AppWidgetProviderInfo?>) {
        _status.value = store.placements.value.associate { p ->
            p.appWidgetId to if (p.isRestorePending) {
                WidgetStatus.Unavailable(appLabelFor(p.providerPackage), restoring = true)
            } else {
                statusOf(p.appWidgetId, infos[p.appWidgetId])
            }
        }
    }

    private fun statusOf(id: Int, info: AppWidgetProviderInfo?): WidgetStatus {
        if (info == null) {
            val placement = store.placements.value.firstOrNull { it.appWidgetId == id }
            return WidgetStatus.Unavailable(appLabelFor(placement?.providerPackage))
        }
        val appLabel = runCatching {
            pm.getApplicationLabel(info.activityInfo.applicationInfo).toString()
        }.getOrDefault(info.provider.packageName)
        return WidgetStatus.Available(
            info = info,
            label = runCatching { info.loadLabel(pm) }.getOrNull() ?: appLabel,
            appLabel = appLabel,
            reconfigurable = PickerModel.canReconfigure(info.configure != null, info.widgetFeatures),
            isWorkProfile = info.profile != Process.myUserHandle(),
        )
    }

    private fun appLabelFor(packageName: String?): String {
        if (packageName == null) return ""
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        /** Fixed host id: AppWidgetService keys this launcher's widget ids by it. Never change it. */
        const val HOST_ID = 0x5348

        @Volatile
        private var instance: WidgetHosting? = null

        fun get(context: Context): WidgetHosting =
            instance ?: synchronized(this) {
                instance ?: WidgetHosting(context.applicationContext).also { instance = it }
            }

        /** Bind options carrying the sizes the widget will be shown at. */
        internal fun optionsFor(sizes: List<SizeDp>): Bundle {
            val b = WidgetSizing.bounds(sizes)
            return Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, b.minWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, b.minHeight)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, b.maxWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, b.maxHeight)
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                )
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    ArrayList(sizes.map { SizeF(it.width, it.height) }),
                )
            }
        }

        internal fun componentOf(request: AddRequest): ComponentName =
            ComponentName(request.providerPackage, request.providerClass)
    }
}

/**
 * The launcher's [AppWidgetHost]. The platform resets a hosted view itself when its provider
 * updates; these hooks additionally ask for a (coalesced) re-check of placements (provider removed,
 * providers changed) so the "widget unavailable" state and reconciliation stay current while the
 * launcher runs.
 */
internal class SphereAppWidgetHost(
    context: Context,
    hostId: Int,
    private val onChanged: () -> Unit,
) : AppWidgetHost(context, hostId) {

    override fun onProvidersChanged() {
        super.onProvidersChanged()
        onChanged()
    }

    override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
        super.onProviderChanged(appWidgetId, appWidget)
        onChanged()
    }

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        super.onAppWidgetRemoved(appWidgetId)
        onChanged()
    }

    /** [clearViews] is protected: the host's view map otherwise outlives the Activity. */
    fun releaseViews() {
        clearViews()
    }
}
