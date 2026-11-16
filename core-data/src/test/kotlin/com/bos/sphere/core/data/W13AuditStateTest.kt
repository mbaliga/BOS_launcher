package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 13 cross-cutting audit of the state added in waves 8 to 12: groups, rules, person links, the user's last name, the
 * home landing, the page order and the empty first home. Round trip through the file, unknown fields, older files.
 */
class W13AuditStateTest {
    private fun filled(): GroupsConfig {
        var c = GroupsConfig()
        c = GroupsEdit.addGroup(c, "Family", GroupPriority.Priority, id = "fam")
        c = GroupsEdit.linkSenders(c, "Priya S", "Priya Sharma", contactKey = "c9", id = "p:1")
        c = GroupsEdit.assign(c, AssignKind.App, "com.news", "fam")
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(Condition(ConditionKind.AppIs, "com.mail")), "fam", name = "Mail"))
        return GroupsEdit.setMyLastName(c, "Sharma")
    }

    private fun parsed(s: String) = GroupsJson.decode(JSONObject(s))

    @Test fun CFG_03_unknownFieldsInsideGroupPersonAssignmentAndRuleEntriesSurviveTheRoundTrip() {
        val file = GroupsJson.encode(filled())
        file.getJSONArray("groups").getJSONObject(0).put("emoji", "x").put("nested", JSONObject().put("a", 1))
        file.getJSONArray("people").getJSONObject(0).put("birthday", "never")
        file.getJSONArray("assignments").getJSONObject(0).put("pinned", true)
        file.getJSONArray("rules").getJSONObject(0).put("schedule", "weekdays")
        file.put("topLevelFuture", 7)
        val live = parsed(file.toString())
        val back = GroupsJson.encode(live)
        assertEquals("x", back.getJSONArray("groups").getJSONObject(0).getString("emoji"))
        assertEquals(1, back.getJSONArray("groups").getJSONObject(0).getJSONObject("nested").getInt("a"))
        assertEquals("never", back.getJSONArray("people").getJSONObject(0).getString("birthday"))
        assertTrue(back.getJSONArray("assignments").getJSONObject(0).getBoolean("pinned"))
        assertEquals("weekdays", back.getJSONArray("rules").getJSONObject(0).getString("schedule"))
        assertEquals(7, back.getInt("topLevelFuture"))
        // The bookkeeping key is never written to the file, and a second round trip changes nothing.
        assertFalse(back.has("~entries"))
        assertEquals(back.toString(), GroupsJson.encode(parsed(back.toString())).toString())
    }

    @Test fun CFG_03_aKnownFieldIsNeverOverwrittenByAKeptOne() {
        val file = GroupsJson.encode(filled())
        file.getJSONArray("groups").getJSONObject(0).put("emoji", "x")
        val live = parsed(file.toString())
        val renamed = live.copy(groups = live.groups.map { it.copy(name = "Kin", priority = GroupPriority.Quiet) })
        val back = GroupsJson.encode(renamed).getJSONArray("groups").getJSONObject(0)
        assertEquals("Kin", back.getString("name"))
        assertEquals("Quiet", back.getString("priority"))
        assertEquals("x", back.getString("emoji"))
    }

    @Test fun CFG_03_keptFieldsOfADeletedEntryAreDroppedWithIt() {
        val file = GroupsJson.encode(filled())
        file.getJSONArray("groups").getJSONObject(0).put("emoji", "x")
        file.getJSONArray("rules").getJSONObject(0).put("schedule", "weekdays")
        val live = GroupsEdit.removeRule(GroupsEdit.delete(parsed(file.toString()), "fam"), "r1")
        val again = parsed(GroupsJson.encode(live).toString())
        assertFalse("no stale entry bookkeeping is carried", JSONObject(again.extra).has("~entries"))
        assertTrue(again.groups.isEmpty())
    }

    @Test fun CFG_03_aRuleWithAConditionFromANewerBuildLoadsOffAndIsWrittenBackWhole() {
        val file = GroupsJson.encode(filled())
        file.getJSONArray("rules").getJSONObject(0).getJSONArray("conditions")
            .put(JSONObject().put("kind", "FutureKind").put("value", "z"))
        val live = parsed(file.toString())
        val rule = live.rules.single()
        // Applying the rule without its unreadable condition would match more notifications than its author meant.
        assertFalse(rule.enabled)
        assertEquals(listOf(Condition(ConditionKind.AppIs, "com.mail")), rule.conditions)
        val back = GroupsJson.encode(live).getJSONArray("rules").getJSONObject(0)
        assertTrue("the stored on state comes back", back.getBoolean("enabled"))
        val kinds = back.getJSONArray("conditions").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("kind") } }
        assertEquals(listOf("AppIs", "FutureKind"), kinds)
        assertFalse(back.has("~unreadable"))
    }

    @Test fun CFG_01_groupsRoundTripThroughTheWholeFileWithTheirKeptFields() {
        val file = GroupsJson.encode(filled())
        file.getJSONArray("people").getJSONObject(0).put("birthday", "never")
        val parts = StateParts(launchpadGroups = parsed(file.toString()))
        val doc = StateCodec.export(parts, 1L)
        val decoded = StateCodec.decode(StateDocument.parse(doc.encode())).parts.launchpadGroups!!
        assertEquals("never", GroupsJson.encode(decoded).getJSONArray("people").getJSONObject(0).getString("birthday"))
        assertEquals(parts.launchpadGroups!!.copy(extra = decoded.extra), decoded)
    }

    @Test fun CFG_01_theLandingSettingsRoundTripAndAnOlderSettingsFileKeepsTheDefaults() {
        val prefs = LauncherPrefs(homeLanding = HomeLanding.AllApps, recentsLimit = 30, minimalHints = false)
        val back = SettingsCodec.decode(SettingsCodec.encode(prefs)).prefs
        assertEquals(HomeLanding.AllApps, back.homeLanding)
        assertEquals(30, back.recentsLimit)
        assertFalse(back.minimalHints)
        val old = SettingsCodec.decode(JSONObject().put("format", StateSchema.SETTINGS_FORMAT).put("version", 1)).prefs
        assertEquals(HomeLanding.Selected, old.homeLanding)
    }

    @Test fun HOM_01_anImportedEmptyHomeOrAnyOtherSavedHomeIsNeverTakenForAFreshInstall() {
        assertTrue(FreshHome.isFresh(HomeLayout(), onboardingDone = false))
        assertFalse("the one empty page is a saved layout", FreshHome.isFresh(FreshHome.emptyLayout(), onboardingDone = false))
        assertFalse(FreshHome.isFresh(HomeLayout(removed = setOf("a")), onboardingDone = false))
        assertFalse(FreshHome.isFresh(HomeLayout(), onboardingDone = true))
        assertFalse(FreshHome.isFresh(HomeLayout(), onboardingDone = false, legacySeeded = true))
    }

    @Test fun HOM_12_thePageOrderSurvivesTheFile() {
        val layout = HomeLayout(pages = listOf(HomePage(listOf("b"), id = 7L), HomePage(listOf("a"), id = 3L), HomePage(emptyList(), id = 9L)), seeded = true)
        val doc = StateCodec.export(StateParts(layout = layout), 1L)
        val back = StateCodec.decode(StateDocument.parse(doc.encode())).parts.layout
        assertEquals(listOf(7L, 3L, 9L), back.pages.map { it.id })
        assertEquals(listOf(listOf("b"), listOf("a"), emptyList<String>()), back.pages.map { it.keys })
    }

    @Test fun STR_13_removingAPersonDropsTheRulesThatRequiredThemInsteadOfWideningThem() {
        var c = GroupsEdit.addGroup(GroupsConfig(), "Work", id = "work")
        c = GroupsEdit.linkSenders(c, "Priya S", "Priya Sharma", id = "p:1")
        c = GroupsEdit.addRule(c, GroupRule("only", listOf(Condition(ConditionKind.PersonIs, "p:1"), Condition(ConditionKind.TimeWindow, from = 540, to = 1020)), "work"))
        c = GroupsEdit.addRule(c, GroupRule("not", listOf(Condition(ConditionKind.AppIs, "com.mail"), Condition(ConditionKind.PersonIs, "p:1", negate = true)), "work"))
        c = GroupsEdit.addRule(c, GroupRule("other", listOf(Condition(ConditionKind.AppIs, "com.chat")), "work"))
        val after = GroupsEdit.removePerson(c, "p:1")
        assertEquals(listOf("not", "other"), after.rules.map { it.id })
        assertEquals(listOf(Condition(ConditionKind.AppIs, "com.mail")), after.rules.first().conditions)
    }

    @Test fun NFR_17_aBrokenStateFileNeverEchoesItsContentInTheErrorText() {
        val platform = org.json.JSONException("Unterminated object at character 25 of {\"myLastName\":\"Sharma\",\"people\":[{\"name\":\"Priya\"")
        val text = StateDocument.describe(platform)
        assertEquals("Unterminated object at character 25", text)
        assertFalse(text.contains("Sharma") || text.contains("Priya"))
        val thrown = runCatching { StateDocument.parse("{\"myLastName\":\"Sharma\",") }.exceptionOrNull()
        assertTrue(thrown is StateFormatException)
    }
}
