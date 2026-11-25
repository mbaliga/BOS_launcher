package com.bos.sphere.feature.homewidgets.model

import java.util.Locale

/**
 * One active notification, reduced to what a count needs: the posting app, the profile it was
 * posted in ([userSerial], a `UserManager` serial; a personal-profile listener also sees the work
 * profile's notifications) and two flags. No content.
 */
data class NotificationFact(val packageName: String, val isGroupSummary: Boolean, val isOngoing: Boolean, val userSerial: Long = 0L)

/**
 * One row of "Needs you": an app in one profile and how many of its notifications are waiting.
 * The work copy of an app is its own row, labelled and opened in its own profile.
 */
data class NeedsYouRow(val packageName: String, val label: String, val count: Int, val userSerial: Long = 0L)

sealed interface NeedsYouView {
    /** Notification access is off. */
    data object NotConnected : NeedsYouView

    /** Access is granted but the listener hasn't connected yet (just after start-up or a grant). */
    data object Connecting : NeedsYouView

    /**
     * [total] notifications from [apps] apps. [rows] are the apps shown; [moreApps] those that
     * didn't fit, and apps hidden in Sphere, which never get a row. [namesShown] false = this
     * widget's setting is "counts only" (no rows at all).
     */
    data class Counts(
        val total: Int,
        val apps: Int,
        val rows: List<NeedsYouRow>,
        val moreApps: Int,
        val namesShown: Boolean,
    ) : NeedsYouView
}

object NeedsYouModel {
    /** A widget with no stored option names apps (never hidden ones, never content). */
    const val SHOW_NAMES_DEFAULT = true

    /** Rows each layout shows: the small one is the total alone. */
    fun rowsFor(tier: Tier): Int = when (tier) {
        Tier.SMALL -> 0
        Tier.MEDIUM -> 3
        Tier.LARGE -> 5
    }

    /**
     * What counts, the same rule as the launcher's `launcher.notifications` channel (docs/pages.md):
     * group summaries are skipped (their children are counted) and so are ongoing notifications
     * (media, navigation, foreground services: status, not something waiting). Sphere's own are
     * skipped too.
     */
    fun counted(facts: List<NotificationFact>, ownPackage: String): List<NotificationFact> =
        facts.filter { !it.isGroupSummary && !it.isOngoing && it.packageName != ownPackage }

    /**
     * Packages hidden in Sphere, from `HiddenAppsStore` keys (`package/Activity#profile`). A package
     * with any hidden launcher entry, in any profile, is never named on a widget (Needs you or Now
     * playing).
     */
    fun hiddenPackages(hiddenKeys: Set<String>): Set<String> =
        hiddenKeys.mapNotNull { key -> key.substringBefore('/', "").takeIf { it.isNotEmpty() } }.toSet()

    /**
     * [access]: notification access granted. [facts]: every active notification, or null while the
     * listener isn't connected. [labelOf] names a package in a profile (its app label, badged for a
     * work profile). [maxRows] comes from [rowsFor]. An app is a package in one profile.
     *
     * Apps hidden in Sphere count in the total and in "+N more apps", but get no row: not their
     * names, and no "hidden apps" row either, which would tell anyone holding the phone that the
     * user hides apps.
     */
    fun view(
        access: Boolean,
        facts: List<NotificationFact>?,
        ownPackage: String,
        hiddenPackages: Set<String>,
        showNames: Boolean,
        maxRows: Int,
        labelOf: (packageName: String, userSerial: Long) -> String,
    ): NeedsYouView {
        if (!access) return NeedsYouView.NotConnected
        if (facts == null) return NeedsYouView.Connecting
        val byApp = counted(facts, ownPackage).groupBy { it.packageName to it.userSerial }
        val total = byApp.values.sumOf { it.size }
        if (!showNames) return NeedsYouView.Counts(total, byApp.size, emptyList(), 0, namesShown = false)

        val named = byApp.filterKeys { (pkg, _) -> pkg !in hiddenPackages }
            .map { (app, list) -> NeedsYouRow(app.first, labelOf(app.first, app.second).ifBlank { app.first }, list.size, app.second) }
            .sortedWith(
                compareByDescending<NeedsYouRow> { it.count }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.userSerial },
            )
        val rows = named.take(maxRows.coerceAtLeast(0))
        return NeedsYouView.Counts(total, byApp.size, rows, byApp.size - rows.size, namesShown = true)
    }
}
