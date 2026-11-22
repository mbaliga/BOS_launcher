package com.bos.sphere.ui

import com.bos.sphere.core.data.CommsCategory
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.Rule
import com.bos.sphere.core.data.RuleAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

/** Wave 53 (STR-17): the category filter of the history tabs and the Default view choice, without a screen. */
class CommsCategoriesLogicW53Test {
    @After fun clean() { LaunchpadSettings.setDefaultCategory(null); CommsOptions.setDefaultView(HubView.All) }

    private fun e(id: String, pkg: String, title: String, text: String, category: String = "", sender: String = "") =
        HistoryEntry(id, id, pkg, pkg, sender, title, text, "", 1_000L, category = category)

    private val rows = listOf(
        e("otp", "sms", "HDFC", "482913 is your OTP", "msg", "HDFC"),
        e("chat", "wa", "Mum", "Hi", "msg", "Mum"),
        e("promo", "shop", "Sale", "50% off", "promo"),
        e("news", "news", "Breaking", "Markets"),
    )

    private fun ids(f: HubFilters, rules: List<Rule> = emptyList()) = HubLogic.query(rows, HubView.All, f, 2_000L, ZoneOffset.UTC, rules).map { it.id }.sorted()

    @Test fun STR_17_theHistoryTabsFilterByCategory() {
        assertEquals(listOf("otp"), ids(HubFilters(category = CommsCategory.Alerts)))
        assertEquals(listOf("chat"), ids(HubFilters(category = CommsCategory.Messages)))
        assertEquals(listOf("news", "promo"), ids(HubFilters(category = CommsCategory.Notifications)))
        assertEquals(4, ids(HubFilters()).size)
    }

    @Test fun STR_17_aTreatAsRuleAppliesToHistoryRowsToo() {
        val r = listOf(Rule.create(RuleAction.AsAlerts, packageName = "shop"))
        assertEquals(listOf("otp", "promo"), ids(HubFilters(category = CommsCategory.Alerts), r))
    }

    @Test fun STR_17_theCategoryCountsAsAnActiveFilter() {
        assertEquals(true, HubFilters(category = CommsCategory.Alerts).active); assertEquals(false, HubFilters().active)
    }

    @Test fun STR_17_theDefaultViewChoiceListsTheCategoriesRightBelowAll() {
        val labels = DefaultViewChoice.options(listOf(HubView.All, HubView.Unread, HubView.Files)).map { it.label }
        assertEquals(listOf("All", "Messages", "Notifications", "Alerts", "Unread", "Files"), labels)
    }

    @Test fun STR_17_choosingACategoryAsTheDefaultSetsItAndChoosingAViewClearsIt() {
        CommsOptions.setDefaultChoice(DefaultViewChoice(HubView.All, CommsCategory.Alerts))
        assertEquals(CommsCategory.Alerts, LaunchpadSettings.prefs.value.defaultCategory)
        assertEquals(DefaultViewChoice(HubView.All, CommsCategory.Alerts), CommsOptions.defaultChoice())
        CommsOptions.setDefaultChoice(DefaultViewChoice(HubView.Unread))
        assertNull(LaunchpadSettings.prefs.value.defaultCategory)
        assertEquals(DefaultViewChoice(HubView.Unread), CommsOptions.defaultChoice())
    }

    @Test fun STR_16_showPinnedAtTheTopDefaultsOnAndIsStoredWithTheExportedPrefs() {
        assertEquals(true, LaunchpadSettings.prefs.value.pinnedAtTop)
        LaunchpadSettings.setPinnedAtTop(false)
        assertEquals(false, LaunchpadSettings.snapshot().prefs.pinnedAtTop) // the snapshot is what the export writes
        LaunchpadSettings.setPinnedAtTop(true)
    }
}
