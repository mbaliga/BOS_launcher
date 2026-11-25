package com.bos.sphere.feature.sphere

import com.bos.sphere.core.data.AppEntry

/**
 * One tile on the coverflow equator. The prototype's "widgets-as-tiles" put hosted App Widgets on
 * the equator between app tiles, so the equator renders a mixed list. [Widget] carries only what
 * the surface needs (an id for keys and hit-testing, a label for accessibility); the widget's own
 * view is drawn by the caller's `widgetTile` slot, so this module stays free of widget hosting.
 */
sealed interface EquatorItem {
    /** Stable identity for composition keys (survives reorders and inserts). */
    val key: String

    data class App(val entry: AppEntry) : EquatorItem {
        override val key: String get() = "app:${entry.key}"
    }

    data class Widget(val appWidgetId: Int, val label: String) : EquatorItem {
        override val key: String get() = "widget:$appWidgetId"
    }
}
