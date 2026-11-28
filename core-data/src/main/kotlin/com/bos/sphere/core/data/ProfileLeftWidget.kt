package com.bos.sphere.core.data

/**
 * Wave 58 (defects58, CFG-62): what activating a profile (workspace) does to the left page. A profile that keeps a left-page widget shows it; a profile
 * without one only clears the widget id. The global Left page setting (Off / Comms / A widget) is the user's choice and is never changed by a switch:
 * before wave 58 a profile without a widget turned a "A widget" left page Off.
 */
object ProfileLeftWidget {
    fun apply(p: LauncherPrefs, widgetId: Int?): LauncherPrefs = when {
        widgetId != null -> p.copy(leftPage = LeftPage.Widget, leftWidgetId = widgetId)
        p.leftPage == LeftPage.Widget -> p.copy(leftWidgetId = LauncherPrefs().leftWidgetId)
        else -> p
    }
}
