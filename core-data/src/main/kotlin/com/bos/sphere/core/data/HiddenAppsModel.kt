package com.bos.sphere.core.data

/** Where an app is hidden from (wave 38, popupsHidden). Every mechanism in the code base is one of these. */
enum class HideSource(val label: String) {
    /** The hidden-apps set: the app menu's "Hide" (home, drawer, Radar and Recents all read it). */
    Launcher("Hidden from home, the drawer and Radar"),
    /** A Comms Hide rule for the whole app ("Hide this app" on a notification). */
    CommsRule("Hidden in Comms by a rule"),
}

/** The three fields of an app the model needs (the app's [AppEntry] maps to this; plain data so the logic runs on the JVM). */
data class HideCandidate(val key: String, val packageName: String, val label: String)

/** One app on the Hidden apps screen: [sources] says which mechanisms hide it, [ruleIds] are the Comms rules to remove with it. */
data class HiddenAppRow(val key: String, val packageName: String, val label: String, val sources: Set<HideSource>, val ruleIds: List<String>)

/**
 * Wave 38 (popupsHidden): what "Hidden apps" lists and how "Unhide" clears it. An app is hidden when its key is in the hidden set, or when a Comms Hide
 * rule that names only the app (no sender, chat, channel or text) exists for its package. Narrower Hide rules (one sender, one chat) hide notifications, not
 * the app, so they only show in [commsRules] (the "Hidden by Comms rules" list). Unhiding an app clears both mechanisms for it.
 */
object HiddenAppsModel {
    /** A Hide rule that covers a whole app. */
    fun isAppWide(r: Rule): Boolean =
        r.action == RuleAction.Hide && r.packageName != null && r.sender == null && r.conversation == null && r.channel == null && r.text == null

    /** Every hidden app among [all], sorted by name, with the sources named. */
    fun rows(all: List<HideCandidate>, hiddenKeys: Set<String>, rules: List<Rule>): List<HiddenAppRow> {
        val wide = rules.filter(::isAppWide)
        return all.mapNotNull { a ->
            val mine = wide.filter { it.packageName.equals(a.packageName, ignoreCase = true) }
            val sources = buildSet { if (a.key in hiddenKeys) add(HideSource.Launcher); if (mine.isNotEmpty()) add(HideSource.CommsRule) }
            if (sources.isEmpty()) null else HiddenAppRow(a.key, a.packageName, a.label, sources, mine.map { it.id })
        }.sortedBy { it.label.lowercase() }
    }

    /** The "Hidden by Comms rules" list: every Hide rule, app-wide or narrower. */
    fun commsRules(rules: List<Rule>): List<Rule> = rules.filter { it.action == RuleAction.Hide }

    /** The rows whose name contains [query] (blank = all). */
    fun filter(rows: List<HiddenAppRow>, query: String): List<HiddenAppRow> {
        val q = query.trim()
        return if (q.isEmpty()) rows else rows.filter { it.label.contains(q, ignoreCase = true) }
    }

    /** Candidates among [all] that the hidden set hides and whose name matches [query] (Radar's "N hidden apps match"); blank query matches nothing. */
    fun radarMatches(all: List<HideCandidate>, hiddenKeys: Set<String>, query: String): List<HideCandidate> {
        val q = query.trim()
        return if (q.isEmpty()) emptyList() else all.filter { it.key in hiddenKeys && it.label.contains(q, ignoreCase = true) }.sortedBy { it.label.lowercase() }
    }

    /** The Hide rules to remove so [packageName] is no longer hidden in Comms: every Hide rule that names the package, whatever else it matches. */
    fun rulesToClear(packageName: String, rules: List<Rule>): List<Rule> =
        rules.filter { it.action == RuleAction.Hide && it.packageName.equals(packageName, ignoreCase = true) }

    /** The badge on the settings row: the number of hidden apps, null when none. */
    fun badge(rows: List<HiddenAppRow>): String? = rows.size.takeIf { it > 0 }?.toString()
}
