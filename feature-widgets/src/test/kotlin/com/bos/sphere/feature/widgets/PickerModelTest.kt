package com.bos.sphere.feature.widgets

import android.appwidget.AppWidgetProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerModelTest {

    private fun entry(
        label: String,
        app: String,
        pkg: String = "com.${app.lowercase()}",
        serial: Long = 0L,
        work: Boolean = false,
        category: Int = PickerModel.CATEGORY_HOME_SCREEN,
        features: Int = 0,
        description: String? = null,
        cls: String = "$pkg.${label.replace(" ", "")}",
    ) = PickerEntry(
        key = "$pkg/$cls#$serial",
        packageName = pkg,
        providerClass = cls,
        appLabel = app,
        label = label,
        description = description,
        userSerial = serial,
        isWorkProfile = work,
        widgetCategory = category,
        widgetFeatures = features,
        spanX = 2,
        spanY = 2,
    )

    @Test
    fun mirroredConstantsMatchThePlatform() {
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, PickerModel.CATEGORY_HOME_SCREEN)
        assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE, PickerModel.FEATURE_RECONFIGURABLE)
        assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER, PickerModel.FEATURE_HIDE_FROM_PICKER)
        assertEquals(
            AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL,
            PickerModel.FEATURE_CONFIGURATION_OPTIONAL,
        )
    }

    @Test
    fun hidesKeyguardOnlyAndHiddenFromPickerProviders() {
        val groups = PickerModel.group(
            listOf(
                entry("Clock", "Clock"),
                entry("Lock clock", "Clock", category = 2), // keyguard only
                entry("Both", "Clock", category = 1 or 2),
                entry("Secret", "Clock", features = PickerModel.FEATURE_HIDE_FROM_PICKER),
            ),
        )
        assertEquals(listOf("Both", "Clock"), groups.single().entries.map { it.label })
    }

    @Test
    fun groupsByAppAndProfileSortedByLabel() {
        val groups = PickerModel.group(
            listOf(
                entry("Inbox", "mail", pkg = "com.mail", serial = 10, work = true),
                entry("Week", "Calendar"),
                entry("Agenda", "Calendar"),
                entry("Inbox", "mail", pkg = "com.mail"),
                entry("Unread", "Mail", pkg = "com.mail"),
            ),
        )
        assertEquals(listOf("Calendar", "mail", "mail"), groups.map { it.appLabel })
        assertEquals(listOf(false, false, true), groups.map { it.isWorkProfile })
        assertEquals(listOf("Agenda", "Week"), groups[0].entries.map { it.label })
        assertEquals(listOf("Inbox", "Unread"), groups[1].entries.map { it.label })
        assertEquals(listOf("Inbox"), groups[2].entries.map { it.label })
    }

    @Test
    fun filterMatchesAppLabelWidgetLabelOrDescriptionIgnoringCase() {
        val all = listOf(
            entry("Agenda", "Calendar"),
            entry("Month", "Calendar", description = "The whole month at a glance"),
            entry("Now playing", "Music"),
        )
        assertEquals(listOf("Agenda", "Month"), PickerModel.group(all, "CALEN").single().entries.map { it.label })
        assertEquals(listOf("Now playing"), PickerModel.group(all, "  playing ").single().entries.map { it.label })
        assertEquals(listOf("Month"), PickerModel.group(all, "glance").single().entries.map { it.label })
        assertTrue(PickerModel.group(all, "weather").isEmpty())
        assertEquals(2, PickerModel.group(all, "").size)
    }

    @Test
    fun spanPrefersTargetCellsThenTheClassicRule() {
        assertEquals(3, PickerModel.span(targetCells = 3, minSizeDp = 40f))
        assertEquals(1, PickerModel.span(0, 40f))
        assertEquals(2, PickerModel.span(0, 110f))
        assertEquals(3, PickerModel.span(0, 111f))
        assertEquals(1, PickerModel.span(0, 0f))
    }

    @Test
    fun configureRules() {
        assertTrue(PickerModel.needsConfigure(true, 0))
        assertFalse(PickerModel.needsConfigure(true, PickerModel.FEATURE_CONFIGURATION_OPTIONAL))
        assertFalse(PickerModel.needsConfigure(false, 0))
        assertTrue(PickerModel.canReconfigure(true, PickerModel.FEATURE_RECONFIGURABLE))
        assertFalse(PickerModel.canReconfigure(true, 0))
        assertFalse(PickerModel.canReconfigure(false, PickerModel.FEATURE_RECONFIGURABLE))
    }
}
