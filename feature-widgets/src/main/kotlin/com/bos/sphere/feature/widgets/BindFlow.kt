package com.bos.sphere.feature.widgets

/** Where a widget being added should go. [equatorIndex] is its index in the merged equator list. */
data class PlacementTarget(val surface: WidgetSurface, val equatorIndex: Int)

/** Everything needed to place a widget once it is bound (and configured, if needed). */
data class AddRequest(
    val providerPackage: String,
    val providerClass: String,
    val userSerial: Long,
    val target: PlacementTarget,
    val hubSize: HubSize,
) {
    fun toPlacement(appWidgetId: Int): WidgetPlacement = WidgetPlacement(
        appWidgetId = appWidgetId,
        providerPackage = providerPackage,
        providerClass = providerClass,
        userSerial = userSerial,
        surface = target.surface,
        slot = target.equatorIndex.coerceAtLeast(0),
        hubSize = hubSize,
    )
}

/** Where the add / reconfigure flow is waiting. */
sealed interface BindStep {
    data object Idle : BindStep

    /** `bindAppWidgetIdIfAllowed` said no; the system bind prompt is showing. */
    data class AwaitingPermission(
        val appWidgetId: Int,
        val request: AddRequest,
        val needsConfigure: Boolean,
    ) : BindStep

    /** The provider's configure activity is showing (for a new widget, or a reconfigure). */
    data class AwaitingConfigure(
        val appWidgetId: Int,
        val request: AddRequest?,
    ) : BindStep {
        val isReconfigure: Boolean get() = request == null
    }
}

/** Why an allocated id is being given back. */
enum class DeleteReason { Cancelled, Failed, Superseded }

/** Side effects the Android layer carries out for the flow. */
sealed interface BindEffect {
    data class LaunchBindPermission(val appWidgetId: Int, val request: AddRequest) : BindEffect
    data class LaunchConfigure(val appWidgetId: Int) : BindEffect
    data class Place(val appWidgetId: Int, val request: AddRequest) : BindEffect
    data class DeleteId(val appWidgetId: Int, val reason: DeleteReason) : BindEffect
}

/**
 * The bind-and-configure state machine, pure so every path is unit-tested:
 *
 * allocate → bind allowed? → (configure needed? → configure → place) / place
 *          → not allowed → bind prompt → granted → (configure → place) / place
 *                                       → refused → delete id
 *
 * Every cancel or failure on a *new* widget deletes its id, so no orphan ids leak. A reconfigure
 * that is cancelled keeps the widget as it was. Starting a new flow while one is pending gives the
 * pending id back first. Results that arrive when nothing is waiting for them are ignored.
 */
class BindFlow {

    var step: BindStep = BindStep.Idle
        private set

    /** Ids a flow is still waiting on; reconciliation must not delete them. */
    val pendingIds: Set<Int>
        get() = when (val s = step) {
            BindStep.Idle -> emptySet()
            is BindStep.AwaitingPermission -> setOf(s.appWidgetId)
            is BindStep.AwaitingConfigure -> setOf(s.appWidgetId)
        }

    /** A new id was allocated for [request] and `bindAppWidgetIdIfAllowed` returned [bindAllowed]. */
    fun onAllocated(
        appWidgetId: Int,
        request: AddRequest,
        bindAllowed: Boolean,
        needsConfigure: Boolean,
    ): List<BindEffect> {
        val effects = supersede()
        effects += if (bindAllowed) {
            afterBound(appWidgetId, request, needsConfigure)
        } else {
            step = BindStep.AwaitingPermission(appWidgetId, request, needsConfigure)
            listOf(BindEffect.LaunchBindPermission(appWidgetId, request))
        }
        return effects
    }

    /** `bindAppWidgetIdIfAllowed` threw for a freshly allocated id. */
    fun onBindError(appWidgetId: Int): List<BindEffect> {
        val effects = supersede()
        effects += BindEffect.DeleteId(appWidgetId, DeleteReason.Failed)
        return effects
    }

    /** The system bind prompt returned; [granted] means OK *and* the id is now bound. */
    fun onPermissionResult(granted: Boolean): List<BindEffect> {
        val s = step as? BindStep.AwaitingPermission ?: return emptyList()
        step = BindStep.Idle
        return if (granted) {
            afterBound(s.appWidgetId, s.request, s.needsConfigure)
        } else {
            listOf(BindEffect.DeleteId(s.appWidgetId, DeleteReason.Cancelled))
        }
    }

    /** The configure activity returned. */
    fun onConfigureResult(ok: Boolean): List<BindEffect> {
        val s = step as? BindStep.AwaitingConfigure ?: return emptyList()
        step = BindStep.Idle
        val request = s.request ?: return emptyList() // reconfigure: keep the widget either way
        return if (ok) {
            listOf(BindEffect.Place(s.appWidgetId, request))
        } else {
            listOf(BindEffect.DeleteId(s.appWidgetId, DeleteReason.Cancelled))
        }
    }

    /** The bind prompt or configure activity could not be started at all. */
    fun onLaunchFailed(): List<BindEffect> {
        val effects = mutableListOf<BindEffect>()
        when (val s = step) {
            BindStep.Idle -> Unit
            is BindStep.AwaitingPermission ->
                effects += BindEffect.DeleteId(s.appWidgetId, DeleteReason.Failed)
            is BindStep.AwaitingConfigure ->
                if (!s.isReconfigure) effects += BindEffect.DeleteId(s.appWidgetId, DeleteReason.Failed)
        }
        step = BindStep.Idle
        return effects
    }

    /** Re-run the configure activity of a placed widget. */
    fun startReconfigure(appWidgetId: Int): List<BindEffect> {
        val effects = supersede()
        step = BindStep.AwaitingConfigure(appWidgetId, request = null)
        effects += BindEffect.LaunchConfigure(appWidgetId)
        return effects
    }

    private fun afterBound(id: Int, request: AddRequest, needsConfigure: Boolean): List<BindEffect> =
        if (needsConfigure) {
            step = BindStep.AwaitingConfigure(id, request)
            listOf(BindEffect.LaunchConfigure(id))
        } else {
            step = BindStep.Idle
            listOf(BindEffect.Place(id, request))
        }

    /** Gives back the id of a new widget still pending, before starting something else. */
    private fun supersede(): MutableList<BindEffect> {
        val effects = mutableListOf<BindEffect>()
        when (val s = step) {
            BindStep.Idle -> Unit
            is BindStep.AwaitingPermission ->
                effects += BindEffect.DeleteId(s.appWidgetId, DeleteReason.Superseded)
            is BindStep.AwaitingConfigure ->
                if (!s.isReconfigure) effects += BindEffect.DeleteId(s.appWidgetId, DeleteReason.Superseded)
        }
        step = BindStep.Idle
        return effects
    }
}
