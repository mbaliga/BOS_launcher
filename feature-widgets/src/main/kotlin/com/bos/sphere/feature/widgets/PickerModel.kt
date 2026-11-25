package com.bos.sphere.feature.widgets

import kotlin.math.ceil

/**
 * One installed widget provider as the picker shows it. Pure data, built from an
 * `AppWidgetProviderInfo` on the Android side ([key] ties it back).
 */
data class PickerEntry(
    val key: String,
    val packageName: String,
    val providerClass: String,
    val appLabel: String,
    val label: String,
    val description: String?,
    val userSerial: Long,
    val isWorkProfile: Boolean,
    val widgetCategory: Int,
    val widgetFeatures: Int,
    val spanX: Int,
    val spanY: Int,
)

/** Providers of one app in one profile (personal and work copies are separate groups). */
data class PickerGroup(
    val key: String,
    val appLabel: String,
    val packageName: String,
    val userSerial: Long,
    val isWorkProfile: Boolean,
    val entries: List<PickerEntry>,
)

object PickerModel {
    // Mirrors of the AppWidgetProviderInfo constants (a unit test pins them to the platform's).
    const val CATEGORY_HOME_SCREEN = 1
    const val FEATURE_RECONFIGURABLE = 1
    const val FEATURE_HIDE_FROM_PICKER = 2
    const val FEATURE_CONFIGURATION_OPTIONAL = 4

    /** Home-screen widgets that don't ask to be hidden from pickers. */
    fun isPickable(entry: PickerEntry): Boolean =
        (entry.widgetCategory and CATEGORY_HOME_SCREEN) != 0 &&
            (entry.widgetFeatures and FEATURE_HIDE_FROM_PICKER) == 0

    /**
     * Groups pickable providers by app and profile. [query] filters locally (case-insensitive) on
     * the app label, the widget label and its description; an app-label match keeps all of that
     * app's widgets. Groups sort by app label (personal before work), entries by label.
     */
    fun group(entries: List<PickerEntry>, query: String = ""): List<PickerGroup> {
        val q = query.trim().lowercase()
        return entries
            .asSequence()
            .filter(::isPickable)
            .filter { e ->
                q.isEmpty() ||
                    q in e.appLabel.lowercase() ||
                    q in e.label.lowercase() ||
                    (e.description?.lowercase()?.contains(q) == true)
            }
            .groupBy { "${it.packageName}#${it.userSerial}" }
            .map { (key, list) ->
                val first = list.first()
                PickerGroup(
                    key = key,
                    appLabel = first.appLabel,
                    packageName = first.packageName,
                    userSerial = first.userSerial,
                    isWorkProfile = first.isWorkProfile,
                    entries = list.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER) { e: PickerEntry -> e.label }
                            .thenBy { it.providerClass },
                    ),
                )
            }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { g: PickerGroup -> g.appLabel }
                    .thenBy { it.isWorkProfile }
                    .thenBy { it.packageName },
            )
    }

    /**
     * Cells a widget asks for: the provider's target cells (API 31+) when it declares them, else
     * the classic `70 × n − 30` dp rule applied to its minimum size.
     */
    fun span(targetCells: Int, minSizeDp: Float): Int =
        if (targetCells > 0) targetCells else maxOf(1, ceil((minSizeDp + 30f) / 70f).toInt())

    /** A configure activity runs at placement unless the provider marks it optional. */
    fun needsConfigure(hasConfigureActivity: Boolean, widgetFeatures: Int): Boolean =
        hasConfigureActivity && (widgetFeatures and FEATURE_CONFIGURATION_OPTIONAL) == 0

    /** "Reconfigure" is offered only for providers that declare themselves reconfigurable. */
    fun canReconfigure(hasConfigureActivity: Boolean, widgetFeatures: Int): Boolean =
        hasConfigureActivity && (widgetFeatures and FEATURE_RECONFIGURABLE) != 0
}
