package com.bos.sphere.ui

import com.bos.sphere.core.data.Condition
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupRule
import com.bos.sphere.core.data.GroupedSection
import com.bos.sphere.core.data.GroupSummaries
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.Section
import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** STR-13: pure logic of the groups UI (words, section mapping, completeness) and the translation coverage of its strings. */
class StrGroupsUiLogicTest {
    private val cfg = GroupsConfig(
        groups = listOf(LaunchpadGroup("fam", "Family", priority = GroupPriority.Priority), LaunchpadGroup("work", "Work")),
        myLastName = "Sharma",
    )

    @Test fun STR_13_summaryInEnglishMatchesTheCoreSummary() {
        val rules = listOf(
            GroupRule("a", listOf(Condition(ConditionKind.LastNameIsMine)), "fam"),
            GroupRule("b", listOf(Condition(ConditionKind.AppIs, "demo.cal"), Condition(ConditionKind.TimeWindow, from = 540, to = 1020, negate = true)), "work"),
            GroupRule("c", listOf(Condition(ConditionKind.SenderContains, "ravi"), Condition(ConditionKind.InContacts, negate = true)), "work"),
            GroupRule("d", emptyList(), "work"),
        )
        for (r in rules) assertEquals(GroupSummaries.describe(r, cfg.group(r.groupId), "Sharma").replace("“", "\"").replace("”", "\"").replace(" -> ", " → "), GroupTexts.summary(r, cfg))
    }

    @Test fun STR_13_summarySentenceFromTheOwnerRequest() {
        val r = GroupRule("a", listOf(Condition(ConditionKind.LastNameIsMine)), "fam")
        assertEquals("Anyone whose last name is Sharma → Family (Priority)", GroupTexts.summary(r, cfg))
    }

    @Test fun STR_13_summaryWithoutALastNameSaysSo() {
        val r = GroupRule("a", listOf(Condition(ConditionKind.LastNameIsMine)), "fam")
        assertTrue(GroupTexts.summary(r, cfg.copy(myLastName = "")).contains("(not set)"))
    }

    @Test fun STR_13_summaryIsTranslated() {
        val before = L10n.language
        try {
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { L10n.setLanguage("de") }
            val r = GroupRule("a", listOf(Condition(ConditionKind.LastNameIsMine)), "fam")
            val s = GroupTexts.summary(r, cfg)
            assertTrue(s, s.startsWith("Jeder dessen Nachname Sharma ist"))
            assertTrue(s, s.contains("Priorität"))
        } finally { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { L10n.setLanguage(before) } }
    }

    @Test fun STR_13_columnHasOneSectionPerGroupSectionAndKeepsTheInnerGrouping() {
        val inner = listOf(Section("Chat", "app:a", emptyList()), Section("Mail", "app:b", emptyList()))
        val gs = GroupedSection("group:fam", cfg.group("fam"), inner, false)
        val fb = GroupedSection(GroupedSection.FALLBACK_KEY, null, listOf(inner[0]), false)
        val s = GroupColumn.sections(listOf(gs, fb), "Everything else")
        assertEquals(listOf("Family", "Everything else"), s.map { it.title })
        assertTrue(GroupColumn.showSubHeads(gs))
        assertFalse(GroupColumn.showSubHeads(fb))
    }

    @Test fun STR_13_conditionCompleteness() {
        assertFalse(conditionComplete(Condition(ConditionKind.SenderContains, " ")))
        assertTrue(conditionComplete(Condition(ConditionKind.SenderContains, "x")))
        assertFalse(conditionComplete(Condition(ConditionKind.AppIs)))
        assertTrue(conditionComplete(Condition(ConditionKind.LastNameIsMine)))
        assertTrue(conditionComplete(newCondition(ConditionKind.CategoryIs)))
        assertTrue(conditionComplete(newCondition(ConditionKind.TimeWindow)))
        assertFalse(conditionComplete(Condition(ConditionKind.CategoryIs, "nope")))
    }

    @Test fun STR_13_everyKindAndPriorityHasAWord() {
        ConditionKind.entries.forEach { assertTrue(GroupTexts.kindLabel(it).isNotBlank()) }
        GroupPriority.entries.forEach { assertTrue(GroupTexts.priorityLabel(it).isNotBlank()) }
    }

    @Test fun STR_13_everyStringOfTheGroupsUiExistsInAllEightLanguages() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val dir = File(root, "feature-launchpad/src/main/kotlin/com/bos/sphere/ui")
        val names = listOf("GroupTexts", "GroupUiKit", "GroupRuleEditor", "GroupAssignSheet", "LaunchpadGroupsPane", "WaitingIndicator", "Launchpad")
        val lit = "\"((?:[^\"\\\\]|\\\\.)*)\""
        val call = Regex("""L10n\.tr\(\s*(?:if \([^)]*\)\s*)?$lit(?:\s*else\s*$lit)?""")
        val problems = ArrayList<String>()
        var found = 0
        for (n in names) for (m in call.findAll(File(dir, "$n.kt").readText())) for (g in m.groupValues.drop(1).filter { it.isNotEmpty() }) {
            found++
            val key = g.replace("\\\"", "\"").replace("\\$", "$")
            for (c in listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")) if (L10n.tableFor(c)?.containsKey(key) != true) problems += "$c: $key"
        }
        assertTrue("found $found strings", found > 100)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
