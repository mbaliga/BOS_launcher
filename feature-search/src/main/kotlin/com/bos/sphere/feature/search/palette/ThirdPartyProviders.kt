package com.bos.sphere.feature.search.palette

import android.content.Context
import android.content.Intent

/**
 * PAL-17: the documented, permission-guarded interface for other apps (docs/SEARCH_PROVIDER.md, PAL-22 contract).
 * An app registers by declaring a read-only content provider with authority `<package>.search`, `android:readPermission`
 * set to [PERMISSION] and an intent filter for [ACTION]. Radar lists those apps; nothing is queried until the user
 * approves the app in Ground Control (Search providers). Rows are shown in Files like Fylz and Fotoz results.
 */
object ThirdPartyProviders {
    const val ACTION = "com.bos.sphere.action.SEARCH_PROVIDER"
    const val PERMISSION = "com.bos.sphere.permission.READ_SEARCH"

    data class Candidate(val packageName: String, val label: String, val approved: Boolean)

    /** Is this provider declaration acceptable? It must be exported, use the guard permission and follow the authority rule. */
    fun acceptable(packageName: String, authority: String?, readPermission: String?, exported: Boolean): Boolean =
        exported && authority == "$packageName.search" && readPermission == PERMISSION

    fun discover(context: Context, approved: Set<String>): List<Candidate> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val found = runCatching { pm.queryIntentContentProviders(Intent(ACTION), 0) }.getOrDefault(emptyList())
        return found.mapNotNull { ri ->
            val p = ri.providerInfo ?: return@mapNotNull null
            if (!acceptable(p.packageName, p.authority, p.readPermission, p.exported)) return@mapNotNull null
            Candidate(p.packageName, runCatching { pm.getApplicationLabel(p.applicationInfo).toString() }.getOrDefault(p.packageName), p.packageName in approved)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
