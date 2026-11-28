package com.bos.sphere.feature.grid

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName

/** W51: providers for tests of the widget carousel (no device providers under Robolectric). Sizes are the cells the provider declares. */
internal fun fakeWidgetChoice(app: String, label: String, cols: Int, rows: Int, pkg: String = "demo.${app.lowercase()}"): WidgetChoice {
    val info = AppWidgetProviderInfo().apply {
        provider = ComponentName(pkg, "$pkg.${label.replace(' ', '_')}")
        targetCellWidth = cols; targetCellHeight = rows
        minWidth = cols * 70 - 30; minHeight = rows * 70 - 30
    }
    return WidgetChoice(info, label, app, isWork = false)
}

internal val fakeWidgets: List<WidgetChoice> = listOf(
    fakeWidgetChoice("Clockwork", "Big clock", 5, 2),
    fakeWidgetChoice("Clockwork", "Alarm", 2, 2),
    fakeWidgetChoice("Weatherly", "Forecast", 4, 2),
    fakeWidgetChoice("Weatherly", "Rain radar", 4, 3),
    fakeWidgetChoice("Notely", "Pinned note", 2, 3),
    fakeWidgetChoice("Musica", "Player", 4, 1),
)
