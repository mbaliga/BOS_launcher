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
fun settingsSummaries(homePages: HomePages, pageCount: Int, gestureCount: Int, hiddenCount: Int?, categories: List<SettingCategory>): Map<String, String> {
    fun value(c: SettingCategory, english: String): String? =
        c.items.firstOrNull { it.title == english || it.title == L10n.tr(english) }?.value?.takeIf { it.isNotBlank() }
    fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" · ").ifBlank { null }
    val out = LinkedHashMap<String, String>()
    for (c in categories) {
        val summary: String? = when (OneUiModel.key(c.title)) {
            OneUiModel.PAGE -> value(c, "Layout")
            "Look" -> join(value(c, "Light / dark"), value(c, "Icon shape")?.let { L10n.tr("%s icons", it) })
            "Home screen" -> when (homePages) {
                HomePages.Off -> L10n.tr("Glance only")
                else -> join(value(c, "Pages"), if (pageCount == 1) L10n.tr("1 page") else L10n.tr("%d pages", pageCount))
            }
            "App drawer" -> join(value(c, "All apps opens as"), value(c, "Sort by"))
            "Corners" -> join(value(c, "Corner style"), value(c, "Icon type"))
            "Gestures" -> if (gestureCount == 0) L10n.tr("No gestures assigned") else L10n.tr("%d gestures assigned", gestureCount)
            "Radar" -> join(value(c, "Radar layout"), value(c, "Web engine"))
            "Comms" -> join(value(c, "Default view"), value(c, "Comms size"))
            "System & sound" -> join(value(c, "Language"), if (value(c, "Interface sounds") == "On") L10n.tr("Sounds on") else L10n.tr("Sounds off"))
            "Hidden apps" -> hiddenCount?.let { if (it == 0) L10n.tr("Nothing hidden") else L10n.tr("%d hidden", it) }
            "About" -> c.items.firstOrNull()?.value?.takeIf { it.isNotBlank() }
            else -> null
        }
        out[c.title] = summary ?: OneUiModel.fallbackSummary(c)
    }
    return out
}
