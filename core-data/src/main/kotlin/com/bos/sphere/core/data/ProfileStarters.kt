package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wave 57 (profiles), CFG-45: what a new profile starts from. [Current] copies what is on screen now (home, dock, calm centre and
 * the live value of every setting it carries); [Empty] is one empty page and an empty dock; the three starters are [Empty] plus
 * sensible defaults (docs/decisions/profiles.md, "Starters").
 */
enum class ProfileStart(val label: String, val detail: String) {
    Current("Current setup", "Copy this home, dock and settings"),
    Empty("Empty", "One empty page"),
    Work("Work", "Work apps on page 1, their notifications, Radar files on"),
    Calm("Calm", "Glance only, calm style, messages and alerts only, two hotspots"),
    Desk("Desk", "Dock on, side dock in landscape, keyboard open"),
}

object ProfileStarters {
    /** The settings groups a start carries by default (the user can change them before saving). */
    fun carries(start: ProfileStart): Set<ProfileGroup> = when (start) {
        ProfileStart.Current, ProfileStart.Empty -> emptySet()
        ProfileStart.Work -> setOf(ProfileGroup.Radar)
        ProfileStart.Calm -> setOf(ProfileGroup.Look, ProfileGroup.Home)
        ProfileStart.Desk -> setOf(ProfileGroup.Home)
    }

    /** The values a start sets, as JSON texts of the Backup file. Only those of a carried group are kept ([settingsFor]). */
    fun overrides(start: ProfileStart): Map<String, String> = when (start) {
        ProfileStart.Current, ProfileStart.Empty -> emptyMap()
        ProfileStart.Work -> mapOf("searchFiles" to "true")
        ProfileStart.Calm -> mapOf(
            "homePages" to "\"${HomePages.Off.name}\"", // Glance only: the pages stay stored, they are not shown
            "calmStyle" to "\"${CalmStyle.EdgeDots.name}\"",
            "glanceHotspots" to "\"" + GlanceHotspots.encode(GlanceHotspots.preset(HotspotPreset.Two)!!) + "\"",
        )
        ProfileStart.Desk -> mapOf(
            "dock.enabled" to "true",
            "dock.edge" to "\"Left\"", // the dock sits at the side in landscape; portrait keeps it at the bottom (DockSettings.edgeFor)
            "kbOpenP" to "true", "kbOpenL" to "true",
        )
    }

    /** The settings snapshot of a new profile: the [live] value of every carried key, with the start's own values on top. */
    fun settingsFor(start: ProfileStart, carried: Set<ProfileGroup>, live: Map<String, String>): Map<String, String> {
        val keys = ProfileSettings.keysOf(carried)
        return ProfileSwitch.capture(live, keys) + overrides(start).filterKeys { it in keys }
    }

    /** The calm-centre apps of a start: null shows every app. */
    fun notificationApps(start: ProfileStart, apps: List<SuggestApp>): Set<String>? = when (start) {
        ProfileStart.Work -> AppGroupSuggester.packagesOf(apps, SuggestionKind.Work).takeIf { it.isNotEmpty() }
        ProfileStart.Calm -> AppGroupSuggester.calmPackages(apps)
        else -> null
    }

    /** The app keys page 1 of a start holds (the Work starter: the work apps, best first). */
    fun firstPage(start: ProfileStart, apps: List<SuggestApp>): List<String> = when (start) {
        ProfileStart.Work -> AppGroupSuggester.suggest(apps).firstOrNull { it.kind == SuggestionKind.Work }?.apps?.map { it.key }.orEmpty()
        else -> emptyList()
    }

    /** A home arrangement in the shape the workspace hooks write: one page holding [firstPage], nothing removed, [dock] as the dock list. */
    fun arrangement(firstPage: List<String>, dock: List<String> = emptyList()): String =
        JSONObject().put("pages", HomeLayoutCodec.encode(listOf(HomePage(keys = firstPage, id = 1L)))).put("removed", JSONArray()).put("dock", JSONArray(dock)).toString()
}
