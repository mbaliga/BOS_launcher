package com.bos.sphere.core.signals

import android.content.Context
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.NotificationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** Which apps may show in the calm centre while a workspace is active. A display filter only; system settings are untouched (WSP-08). */
data class NotificationScope(val allowedPackages: Set<String>? = null) {
    fun allows(packageName: String) = allowedPackages == null || packageName in allowedPackages
    fun <T> apply(items: List<T>, packageOf: (T) -> String): List<T> = if (allowedPackages == null) items else items.filter { packageOf(it) in allowedPackages }
}

/** Notification data, wrapping the existing [NotificationProvider]; nothing is duplicated. */
object NotificationSignals {
    /** Makes the Launchpad feed honour the active workspace's filter (null shows everything). */
    fun setScope(scope: NotificationScope) { NotificationProvider.displayFilter.value = scope.allowedPackages }

    /** The system feed, narrowed by [scope]. */
    fun scoped(scope: Flow<NotificationScope>): Flow<List<HubNotification>> =
        combine(NotificationProvider.notifications, scope) { list, s -> s.apply(list) { it.packageName } }
}

/** The active media session as the dock sees it, wrapping [MediaNow] (STR-06, DCK-08). */
object MediaSignals {
    val info: StateFlow<MediaInfo?> get() = MediaNow.info
    val needsAccess: StateFlow<Boolean> get() = MediaNow.needsAccess
    fun start(context: Context) = MediaNow.start(context)
    fun stop() = MediaNow.stop()
    fun playPause() = MediaNow.playPause()
    fun next() = MediaNow.next()
    fun previous() = MediaNow.previous()
    fun open(context: Context) = MediaNow.openPlayer(context)
}
