package com.bos.sphere.ui

import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.OneUiModel
import com.bos.sphere.feature.grid.SettingCategory

/**
 * Wave 40 (settingsOneUI), CFG-21: the live grey line under each category of the root settings list ("Dark · Rounded icons",
 * "Paginated · 3 pages", "2 hidden"). They are read from the rows the categories already hold (so they are always what the rows
 * show), with the page, gesture and hidden counts from the model; a category with nothing better falls back to [OneUiModel.fallbackSummary].
 * Keyed by the (translated) category title.
 */
fun settingsSummaries(
    homePages: HomePages, pageCount: Int, gestureCount: Int, hiddenCount: Int?, categories: List<SettingCategory>,
    /** Wave 52: the page the Pages row speaks for (0-based), the saved workspaces, and the widgets the right-hand panel holds (0 until the panel can hold some). */
    pageIndex: Int = 0, workspaceCount: Int = 1, sidePanelWidgets: Int = 0,
): Map<String, String> {
    fun value(c: SettingCategory, english: String): String? =
        c.items.firstOrNull { it.title == english || it.title == L10n.tr(english) }?.value?.takeIf { it.isNotBlank() }
    fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" · ").ifBlank { null }
    val out = LinkedHashMap<String, String>()
    for (c in categories) {
        val summary: String? = when (OneUiModel.key(c.title)) {
            OneUiModel.PAGE -> join(L10n.tr("Page %d of %d", pageIndex + 1, pageCount.coerceAtLeast(1)), value(c, "Layout"))
            "Look" -> join(value(c, "Light / dark"), value(c, "Icon shape")?.let { L10n.tr("%s icons", it) }, value(c, "Font"))
            "Home screen" -> when (homePages) {
                HomePages.Off -> L10n.tr("Glance only")
                else -> join(value(c, "Pages"), if (pageCount == 1) L10n.tr("1 page") else L10n.tr("%d pages", pageCount))
            }
            "App drawer" -> value(c, "Ring segments")?.let { L10n.tr("%s shortcut segments", it) }
            "Corners" -> join(value(c, "Corner style"), value(c, "Icon type"))
            "Gestures" -> if (gestureCount == 0) L10n.tr("No gestures assigned") else L10n.tr("%d gestures assigned", gestureCount)
            "Radar" -> join(value(c, "Radar layout"), value(c, "Web engine"))
            OneUiModel.SIDE_PANEL -> sidePanelSummary(value(c, "Comms size"), sidePanelWidgets)
            "Workspaces" -> if (workspaceCount == 1) L10n.tr("1 workspace") else L10n.tr("%d workspaces", workspaceCount)
            "System & sound" -> join(value(c, "Language"), if (value(c, "Interface sounds") == "On") L10n.tr("Sounds on") else L10n.tr("Sounds off"))
            "Hidden apps" -> hiddenCount?.let { if (it == 0) L10n.tr("Nothing hidden") else L10n.tr("%d hidden", it) }
            "About" -> c.items.firstOrNull()?.value?.takeIf { it.isNotBlank() }
            else -> null
        }
        out[c.title] = summary ?: OneUiModel.fallbackSummary(c)
    }
    return out
}

/**
 * Wave 52: the grey line of the Side panel row: what the panel holds. The Comms section shows its size ("Comms · 100%"), the widgets their count
 * ("Widgets · 2"); both, in that order, when the panel has both; just the Comms size while no widgets are placed.
 */
fun sidePanelSummary(commsSize: String?, widgets: Int): String {
    val comms = commsSize?.let { L10n.tr("Comms") + " · " + it }
    val w = if (widgets > 0) L10n.tr("Widgets") + " · " + widgets else null
    return listOfNotNull(comms, w).joinToString(", ").ifBlank { L10n.tr("Comms") }
}
