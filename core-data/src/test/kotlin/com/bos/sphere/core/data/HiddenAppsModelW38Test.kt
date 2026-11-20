package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 38 (popupsHidden): what the Hidden apps page lists, names and clears. */
class HiddenAppsModelW38Test {
    private val wa = HideCandidate("wa#0", "com.whatsapp", "WhatsApp")
    private val maps = HideCandidate("maps#0", "com.maps", "Maps")
    private val mail = HideCandidate("mail#0", "com.mail", "Mail")
    private val all = listOf(wa, maps, mail)

    @Test fun HOM_05_w38_anAppInTheHiddenSetIsListedWithTheLauncherSource() {
        val rows = HiddenAppsModel.rows(all, setOf("wa#0"), emptyList())
        assertEquals(listOf("WhatsApp"), rows.map { it.label })
        assertEquals(setOf(HideSource.Launcher), rows.single().sources)
    }

    @Test fun HOM_05_w38_aCommsHideThisAppRuleListsTheAppWithTheCommsSource() {
        val rule = Rule.create(RuleAction.Hide, packageName = "com.whatsapp")
        val rows = HiddenAppsModel.rows(all, emptySet(), listOf(rule))
        assertEquals(setOf(HideSource.CommsRule), rows.single().sources)
        assertEquals(listOf(rule.id), rows.single().ruleIds)
    }

    @Test fun HOM_05_w38_bothMechanismsAreNamedTogetherAndEveryMechanismIsCovered() {
        val rows = HiddenAppsModel.rows(all, setOf("wa#0", "maps#0"), listOf(Rule.create(RuleAction.Hide, packageName = "com.whatsapp")))
        assertEquals(listOf("Maps", "WhatsApp"), rows.map { it.label })
        assertEquals(setOf(HideSource.Launcher, HideSource.CommsRule), rows.first { it.label == "WhatsApp" }.sources)
        assertEquals("the two mechanisms in the code base", setOf(HideSource.Launcher, HideSource.CommsRule), HideSource.entries.toSet())
    }

    @Test fun HOM_05_w38_aNarrowHideRuleHidesNotificationsNotTheAppSoItIsOnlyInTheRulesList() {
        val narrow = Rule.create(RuleAction.Hide, packageName = "com.whatsapp", sender = "Spam Bot")
        val silent = Rule.create(RuleAction.Silent, packageName = "com.maps")
        assertTrue(HiddenAppsModel.rows(all, emptySet(), listOf(narrow, silent)).isEmpty())
        assertEquals(listOf(narrow), HiddenAppsModel.commsRules(listOf(narrow, silent)))
    }

    @Test fun HOM_05_w38_searchFiltersByNameIgnoringCase() {
        val rows = HiddenAppsModel.rows(all, setOf("wa#0", "maps#0"), emptyList())
        assertEquals(listOf("WhatsApp"), HiddenAppsModel.filter(rows, " whats ").map { it.label })
        assertEquals(2, HiddenAppsModel.filter(rows, "").size)
    }

    @Test fun PAL_02_w38_radarOffersOnlyHiddenAppsWhoseNameMatchesAndNothingForABlankQuery() {
        val keys = setOf("wa#0")
        assertEquals(listOf("WhatsApp"), HiddenAppsModel.radarMatches(all, keys, "what").map { it.label })
        assertTrue(HiddenAppsModel.radarMatches(all, keys, "").isEmpty())
        assertTrue("a visible app never counts", HiddenAppsModel.radarMatches(all, keys, "maps").isEmpty())
    }

    @Test fun HOM_05_w38_unhideClearsEveryHideRuleOfThePackageButNotOtherRules() {
        val appWide = Rule.create(RuleAction.Hide, packageName = "com.whatsapp")
        val narrow = Rule.create(RuleAction.Hide, packageName = "com.whatsapp", sender = "Bot")
        val other = Rule.create(RuleAction.Hide, packageName = "com.maps")
        val pin = Rule.create(RuleAction.Pin, packageName = "com.whatsapp", sender = "Mum")
        val cleared = HiddenAppsModel.rulesToClear("com.whatsapp", listOf(appWide, narrow, other, pin)).map { it.id }
        assertEquals(setOf(appWide.id, narrow.id), cleared.toSet())
    }

    @Test fun HOM_05_w38_theBadgeIsTheCountAndAbsentWhenNothingIsHidden() {
        assertNull(HiddenAppsModel.badge(emptyList()))
        assertEquals("2", HiddenAppsModel.badge(HiddenAppsModel.rows(all, setOf("wa#0", "maps#0"), emptyList())))
    }
}
