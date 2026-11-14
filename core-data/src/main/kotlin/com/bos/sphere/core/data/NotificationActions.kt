package com.bos.sphere.core.data

import android.app.Notification

/** STR-02: turns a notification's own actions into [HubAction]s, keeping the PendingIntent and any free-form RemoteInput. */
object NotificationActions {
    fun parse(n: Notification): List<HubAction> =
        n.actions?.mapNotNull { action ->
            val label = action.title?.toString() ?: return@mapNotNull null
            val inputs = action.remoteInputs?.filter { it.resultKey != null }
            val reply = inputs?.firstOrNull { it.allowFreeFormInput }?.let { first ->
                ReplyTarget(action.remoteInputs.orEmpty(), first.resultKey, first.label?.toString())
            }
            val pi = action.actionIntent
            HubAction(label = label, action = { runCatching { pi?.send() } }, intent = pi, reply = reply)
        } ?: emptyList()
}
