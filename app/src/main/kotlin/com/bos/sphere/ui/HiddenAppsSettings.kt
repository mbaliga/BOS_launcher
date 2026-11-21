package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HiddenAppRow
import com.bos.sphere.core.data.HiddenAppsModel
import com.bos.sphere.core.data.HideCandidate
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.AppIcon
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.HiddenAppsPane
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import kotlinx.coroutines.flow.StateFlow

/** Wave 38 (popupsHidden): what Ground Control's Hidden apps page reads and writes. [count] is the number of hidden apps, for the badge. */
class HiddenAppsAccess(
    val count: Int,
    val hiddenKeys: StateFlow<Set<String>>,
    val allApps: List<AppEntry>,
    val setHidden: (key: String, hidden: Boolean) -> Unit,
)

internal fun candidates(apps: List<AppEntry>) = apps.map { HideCandidate(it.key, it.packageName, it.label) }

/** Clears every mechanism that hides [row]: the hidden-apps set and the Comms Hide rules that name its package. */
fun unhideEverywhere(row: HiddenAppRow, access: HiddenAppsAccess) {
    access.setHidden(row.key, false)
    HiddenAppsModel.rulesToClear(row.packageName, LaunchpadSettings.rules.value).forEach { LaunchpadSettings.removeRule(it.id) }
}

/** The "Hidden apps" row of Ground Control's top-level list (the tab title carries the count while any app is hidden). */
fun hiddenAppsCategory(a: HiddenAppsAccess): SettingCategory {
    val title = L10n.tr("Hidden apps")
    return SettingCategory(
        if (a.count > 0) "$title · ${a.count}" else title, Glyphs.Image,
        listOf(
            SettingItem.Pane(title, Glyphs.Image, if (a.count > 0) L10n.tr("%d hidden", a.count) else L10n.tr("None")) {
                val keys by a.hiddenKeys.collectAsState()
                val rules by LaunchpadSettings.rules.collectAsState()
                val byKey = remember(a.allApps) { a.allApps.associateBy { it.key } }
                val rows = HiddenAppsModel.rows(candidates(a.allApps), keys, rules)
                HiddenAppsPane(
                    rows = rows, commsRules = HiddenAppsModel.commsRules(rules),
                    appLabel = { pkg -> a.allApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }?.label },
                    onUnhide = { unhideEverywhere(it, a) },
                    onUnhideAll = { rows.forEach { unhideEverywhere(it, a) } },
                    onRemoveRule = { LaunchpadSettings.removeRule(it.id) },
                    icon = { r -> byKey[r.key]?.let { AppIcon(it, 40.dp) } },
                )
            },
        ),
    )
}
