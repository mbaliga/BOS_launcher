package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** STR-13: people across apps, conditions, rule precedence, previews, edits. */
class StrGroupsEngineTest {
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = java.time.LocalDate.of(2024, 3, 10).atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun n(
        key: String, sender: String? = null, pkg: String = "chat.a", conv: String? = null, title: String = "t", text: String = "hello",
        msg: Boolean = true, cat: String? = null, ts: Long = at(12), style: String? = null,
    ) = HubNotification(
        id = key, key = key, source = "x", title = title, summary = text, timestamp = ts, packageName = pkg, appLabel = pkg,
        sender = sender, conversation = conv, isMessaging = msg, category = cat, style = style,
    )

    private fun cfg(vararg names: String): GroupsConfig {
        var c = GroupsConfig()
        names.forEachIndexed { i, nm -> c = GroupsEdit.addGroup(c, nm, id = "g$i") }
        return c
    }

    private fun r(c: GroupsConfig, list: HubNotification, contacts: ContactDirectory = NoContacts) = GroupResolver(c, contacts, zone).resolve(list)

    // ---- people across platforms ----

    @Test fun STR_13_aPersonsChatsFromEveryAppLandInTheirGroup() {
        var c = cfg("Work")
        c = GroupsEdit.assign(c, AssignKind.Person, "n:" + PersonNames.key("Priya Sharma"), "g0")
        val a = n("1", "Priya Sharma", pkg = "com.whatsapp")
        val b = n("2", "PRIYA SHARMA (Telegram)", pkg = "org.telegram")
        val d = n("3", "priya sharma 😊", pkg = "org.signal")
        val other = n("4", "Rahul Verma", pkg = "com.whatsapp")
        for (x in listOf(a, b, d)) assertEquals("g0", r(c, x).group?.id)
        assertNull(r(c, other).group)
    }

    @Test fun STR_13_linkingSendersMakesTwoNamesOnePersonAndMovesTheirAssignments() {
        var c = cfg("Work")
        c = GroupsEdit.assign(c, AssignKind.Person, "n:" + PersonNames.key("Priya S."), "g0")
        assertNull(r(c, n("1", "Priya from Accounts")).group)
        c = GroupsEdit.linkSenders(c, "Priya S.", "Priya from Accounts", id = "p:1")
        assertEquals(1, c.people.size)
        assertEquals(listOf(GroupAssignment(AssignKind.Person, "p:1", "g0")), c.assignments)
        assertEquals("g0", r(c, n("1", "Priya from Accounts")).group?.id)
        assertEquals("g0", r(c, n("2", "priya s.", pkg = "other")).group?.id)
        assertEquals(c, GroupsEdit.linkSenders(c, "Priya S.", "Priya from Accounts")) // idempotent
    }

    @Test fun STR_13_linkingAThirdNameJoinsTheExistingPersonAndTwoPeopleMerge() {
        var c = cfg("Work")
        c = GroupsEdit.linkSenders(c, "Priya", "P. Sharma", id = "p:1")
        c = GroupsEdit.linkSenders(c, "Priya", "Pri", id = "p:ignored")
        assertEquals(1, c.people.size)
        assertEquals(3, c.people.single().aliases.size)
        c = GroupsEdit.linkSenders(c, "Rahul", "Rahul V", id = "p:2")
        c = GroupsEdit.assign(c, AssignKind.Person, "p:2", "g0")
        c = GroupsEdit.linkSenders(c, "Pri", "Rahul V")
        assertEquals(1, c.people.size)
        assertEquals("g0", r(c, n("1", "Priya")).group?.id) // the merged person carries the assignment
    }

    @Test fun STR_13_aContactMatchLinksSendersAndExtendsMatchingByAlternateNamesPhonesAndEmails() {
        val contacts = ContactIndex(listOf(ContactIdentity("c1", "Priya Sharma", setOf("Pri", "प्रिया"), setOf("+1 (555) 010-0199"), setOf("priya@example.org"), starred = true)))
        var c = cfg("Work")
        c = GroupsEdit.assign(c, AssignKind.Person, "c:c1", "g0")
        for (s in listOf("Priya Sharma", "Pri", "प्रिया", "+15550100199", "555 010 0199", "PRIYA@example.org"))
            assertEquals("contact name $s", "g0", r(c, n("x$s", s), contacts).group?.id)
        assertNull(r(c, n("y", "Someone Else"), contacts).group)
        assertNull("without the grant the contact identity does not exist", r(c, n("z", "Pri"), NoContacts).group)
    }

    @Test fun STR_13_aStoredPersonLinkedToAContactFollowsTheContact() {
        val contacts = ContactIndex(listOf(ContactIdentity("c1", "Priya Sharma", setOf("Pri"))))
        var c = cfg("Work")
        c = GroupsEdit.linkSenders(c, "Priya Sharma", "Work Priya", contactKey = "c1", id = "p:1")
        c = GroupsEdit.assign(c, AssignKind.Person, "p:1", "g0")
        assertEquals("g0", r(c, n("1", "Pri"), contacts).group?.id) // reaches the person through the contact
    }

    @Test fun STR_13_personAssignmentsAreForOneToOneChatsNotGroupChats() {
        var c = cfg("Work")
        c = GroupsEdit.assign(c, AssignKind.Person, "n:priya sharma", "g0")
        assertEquals("g0", r(c, n("1", "Priya Sharma", conv = "Priya Sharma")).group?.id)
        assertEquals("g0", r(c, n("2", "Priya Sharma", conv = null)).group?.id)
        assertNull(r(c, n("3", "Priya Sharma", conv = "Weekend trip")).group)
    }

    // ---- precedence ----

    @Test fun STR_13_aManualAssignmentAlwaysBeatsRulesAndConversationBeatsPersonBeatsApp() {
        var c = cfg("Work", "Family", "Fun")
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(Condition(ConditionKind.SenderContains, "sharma")), "g1"))
        val x = n("1", "Priya Sharma", pkg = "chat.a", conv = "Priya Sharma")
        assertEquals("g1", r(c, x).group?.id)
        assertEquals(GroupSource.Rule, r(c, x).source)
        c = GroupsEdit.assign(c, AssignKind.App, AssignTargets.app("chat.a"), "g2")
        assertEquals(GroupSource.Manual, r(c, x).source)
        assertEquals("g2", r(c, x).group?.id)
        c = GroupsEdit.assign(c, AssignKind.Person, "n:priya sharma", "g0")
        assertEquals("g0", r(c, x).group?.id)
        c = GroupsEdit.assign(c, AssignKind.Conversation, AssignTargets.conversation("chat.a", "priya sharma"), "g2")
        assertEquals("g2", r(c, x).group?.id)
        assertEquals(AssignKind.Conversation, r(c, x).assignKind)
    }

    @Test fun STR_13_anExplicitNoGroupAssignmentStopsRulesToo() {
        var c = cfg("Family")
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(Condition(ConditionKind.AppIs, "chat.a")), "g0"))
        val x = n("1", "Priya Sharma")
        assertEquals("g0", r(c, x).group?.id)
        c = GroupsEdit.assign(c, AssignKind.Person, "n:priya sharma", null)
        val res = r(c, x)
        assertNull(res.group)
        assertEquals(GroupSource.Manual, res.source)
    }

    @Test fun STR_13_rulesAreOrderedFirstMatchWinsAndAllConditionsAreAnded() {
        var c = cfg("A", "B")
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(Condition(ConditionKind.AppIs, "chat.a"), Condition(ConditionKind.SenderContains, "bob")), "g0"))
        c = GroupsEdit.addRule(c, GroupRule("r2", listOf(Condition(ConditionKind.AppIs, "chat.a")), "g1"))
        assertEquals("g0", r(c, n("1", "Bob")).group?.id)
        assertEquals("g1", r(c, n("2", "Alice")).group?.id)
        assertNull(r(c, n("3", "Bob", pkg = "other")).group)
        c = GroupsEdit.moveRule(c, "r2", -1)
        assertEquals("g1", r(c, n("1", "Bob")).group?.id) // now the broad rule is first
        assertEquals(listOf("r2", "r1"), c.rules.map { it.id })
    }

    @Test fun STR_13_disabledRulesAndRulesWithoutConditionsOrWithAMissingGroupMatchNothing() {
        var c = cfg("A")
        c = c.copy(rules = listOf(
            GroupRule("r1", listOf(Condition(ConditionKind.AppIs, "chat.a")), "g0", enabled = false),
            GroupRule("r2", emptyList(), "g0"),
            GroupRule("r3", listOf(Condition(ConditionKind.AppIs, "chat.a")), "gone"),
        ))
        assertNull(r(c, n("1", "Bob")).group)
    }

    @Test fun STR_13_resolutionIsDeterministic() {
        var c = cfg("A", "B")
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(Condition(ConditionKind.SenderStartsWith, "a")), "g0"))
        c = GroupsEdit.addRule(c, GroupRule("r2", listOf(Condition(ConditionKind.TextContains, "hello")), "g1"))
        val list = (1..50).map { n("k$it", if (it % 2 == 0) "Anna $it" else "Ben $it") }
        val one = GroupResolver(c, NoContacts, zone).resolveAll(list)
        repeat(3) { assertEquals(one, GroupResolver(c, NoContacts, zone).resolveAll(list.shuffled())) }
    }

    // ---- conditions ----

    private fun matches(c: Condition, x: HubNotification, contacts: ContactDirectory = NoContacts, my: String = "") =
        GroupResolver(GroupsConfig(myLastName = my), contacts, zone).conditionMatches(c, x)

    @Test fun STR_13_senderAndConversationAndTextConditions() {
        val x = n("1", "Priya Sharma", conv = "Café Crew", title = "Priya Sharma", text = "Lunch at noon?")
        assertTrue(matches(Condition(ConditionKind.SenderContains, "sharm"), x))
        assertTrue(matches(Condition(ConditionKind.SenderContains, "SHARMA"), x))
        assertTrue(matches(Condition(ConditionKind.SenderStartsWith, "priya"), x))
        assertFalse(matches(Condition(ConditionKind.SenderStartsWith, "sharma"), x))
        assertTrue(matches(Condition(ConditionKind.SenderEquals, "priya sharma"), x))
        assertTrue(matches(Condition(ConditionKind.SenderEquals, "Sharma, Priya"), x))
        assertFalse(matches(Condition(ConditionKind.SenderEquals, "priya"), x))
        assertTrue(matches(Condition(ConditionKind.ConversationContains, "cafe"), x))
        assertTrue(matches(Condition(ConditionKind.TextContains, "LUNCH"), x))
        assertTrue(matches(Condition(ConditionKind.TextContains, "priya"), x)) // the title counts
        assertFalse(matches(Condition(ConditionKind.TextContains, ""), x))
        assertFalse(matches(Condition(ConditionKind.SenderContains, ""), x))
        assertTrue(matches(Condition(ConditionKind.SenderContains, "sharma", negate = true), n("2", "Rahul Verma")))
    }

    @Test fun STR_13_appCategoryAndConversationConditions() {
        assertTrue(matches(Condition(ConditionKind.AppIs, "CHAT.A"), n("1", "x")))
        assertFalse(matches(Condition(ConditionKind.AppIs, "chat.b"), n("1", "x")))
        assertTrue(matches(Condition(ConditionKind.CategoryIs, "message"), n("1", "x", msg = true)))
        assertTrue(matches(Condition(ConditionKind.CategoryIs, "message"), n("1", "x", msg = false, cat = "msg")))
        assertTrue(matches(Condition(ConditionKind.CategoryIs, "call"), n("1", "x", msg = false, cat = "call")))
        assertTrue(matches(Condition(ConditionKind.CategoryIs, "reminder"), n("1", "x", msg = false, cat = "alarm")))
        assertFalse(matches(Condition(ConditionKind.CategoryIs, "call"), n("1", "x")))
        assertTrue(matches(Condition(ConditionKind.IsConversation), n("1", "x", msg = true)))
        assertTrue(matches(Condition(ConditionKind.IsConversation), n("1", "x", msg = false, style = "messaging")))
        assertFalse(matches(Condition(ConditionKind.IsConversation), n("1", "x", msg = false)))
    }

    @Test fun STR_13_lastNameIsMineMatchesFamilyAndNeverWithoutASetting() {
        val c = Condition(ConditionKind.LastNameIsMine)
        assertTrue(matches(c, n("1", "Anita Sharma"), my = "Sharma"))
        assertTrue(matches(c, n("2", "Sharma, Rahul"), my = "sharma"))
        assertFalse(matches(c, n("3", "Anita Verma"), my = "Sharma"))
        assertFalse(matches(c, n("4", "Mum"), my = "Sharma")) // single name: no match
        assertFalse(matches(c, n("5", "Anita Sharma"), my = ""))
        assertFalse("negated and unset is still no match", matches(c.copy(negate = true), n("6", "Anita Sharma"), my = ""))
        assertTrue(matches(c.copy(negate = true), n("7", "Anita Verma"), my = "Sharma"))
    }

    @Test fun STR_13_contactConditionsNeedTheGrantAndNegationDoesNotTurnNoGrantIntoEverything() {
        val contacts = ContactIndex(listOf(ContactIdentity("c1", "Priya Sharma", starred = true), ContactIdentity("c2", "Rahul Verma", starred = false)))
        assertTrue(matches(Condition(ConditionKind.InContacts), n("1", "priya sharma"), contacts))
        assertFalse(matches(Condition(ConditionKind.InContacts), n("2", "Stranger Danger"), contacts))
        assertTrue(matches(Condition(ConditionKind.InContacts, negate = true), n("2", "Stranger Danger"), contacts))
        assertTrue(matches(Condition(ConditionKind.StarredContact), n("1", "Priya Sharma"), contacts))
        assertFalse(matches(Condition(ConditionKind.StarredContact), n("3", "Rahul Verma"), contacts))
        assertFalse(matches(Condition(ConditionKind.InContacts), n("1", "Priya Sharma"), NoContacts))
        assertFalse(matches(Condition(ConditionKind.InContacts, negate = true), n("1", "Priya Sharma"), NoContacts))
        assertFalse(matches(Condition(ConditionKind.StarredContact, negate = true), n("1", "Priya Sharma"), NoContacts))
        assertTrue(Condition(ConditionKind.InContacts).needsContacts)
    }

    @Test fun STR_13_timeWindowWrapsMidnightAndUsesThePostingTime() {
        val night = Condition(ConditionKind.TimeWindow, from = 22 * 60, to = 7 * 60)
        assertTrue(matches(night, n("1", "x", ts = at(23))))
        assertTrue(matches(night, n("2", "x", ts = at(3))))
        assertFalse(matches(night, n("3", "x", ts = at(12))))
        assertFalse(matches(night, n("4", "x", ts = at(7))))
        val work = Condition(ConditionKind.TimeWindow, from = 9 * 60, to = 17 * 60)
        assertTrue(matches(work, n("5", "x", ts = at(9))))
        assertFalse(matches(work, n("6", "x", ts = at(17))))
        assertTrue(matches(Condition(ConditionKind.TimeWindow, from = 5, to = 5), n("7", "x", ts = at(3))))
    }

    // ---- preview, sample test, summary, edits ----

    @Test fun STR_13_previewCountsMatchesNamesTheFirstFewAndTheWinnersAccountForOverrides() {
        var c = cfg("Family", "Work")
        val list = listOf(n("1", "Anita Sharma"), n("2", "Rahul Sharma"), n("3", "Anita Sharma", pkg = "b"), n("4", "Sam Verma"), n("5", "Dev Sharma"), n("6", "Zed Sharma"))
        c = GroupsEdit.setMyLastName(c, "Sharma")
        val conds = listOf(Condition(ConditionKind.LastNameIsMine))
        val res = GroupResolver(c, NoContacts, zone)
        val p = res.preview(conds, list)
        assertEquals(5, p.count)
        assertEquals(listOf("Anita Sharma", "Rahul Sharma", "Dev Sharma"), p.names)
        assertEquals(listOf("1", "2", "3", "5", "6"), p.keys)
        assertEquals(0, res.preview(emptyList(), list).count)
        // Rahul is moved by hand to Work: the rule no longer wins for him although it matches.
        val c2 = GroupsEdit.assign(c, AssignKind.Person, "n:rahul sharma", "g1")
        val rule = GroupRule("new", conds, "g0")
        assertEquals(5, GroupResolver(c2, NoContacts, zone).preview(conds, list).count)
        assertEquals(4, GroupResolver(c2, NoContacts, zone).previewWinning(rule, list).count)
        assertEquals(5, res.previewWinning(rule, list).count)
    }

    @Test fun STR_13_theSampleTestFieldAnswersForAnyNameWithoutANotification() {
        val c = GroupsEdit.setMyLastName(cfg("Family"), "Sharma")
        val res = GroupResolver(c, NoContacts, zone)
        val conds = listOf(Condition(ConditionKind.LastNameIsMine), Condition(ConditionKind.AppIs, "com.whatsapp"))
        assertTrue(res.testSample(conds, "Anita Sharma", "com.whatsapp"))
        assertFalse(res.testSample(conds, "Anita Sharma", "org.telegram"))
        assertFalse(res.testSample(conds, "Anita Verma", "com.whatsapp"))
        assertFalse(res.testSample(emptyList(), "Anita Sharma"))
    }

    @Test fun STR_13_plainLanguageSummary() {
        val fam = LaunchpadGroup("g", "Family", priority = GroupPriority.Priority)
        val rule = GroupRule("r", listOf(Condition(ConditionKind.LastNameIsMine)), "g")
        assertEquals("Anyone whose last name is Sharma -> Family (Priority)", GroupSummaries.describe(rule, fam, "Sharma"))
        val two = GroupRule("r", listOf(Condition(ConditionKind.AppIs, "com.whatsapp"), Condition(ConditionKind.TextContains, "invoice")), "g")
        assertEquals("Any notification in WhatsApp and whose text contains “invoice” -> Work",
            GroupSummaries.describe(two, LaunchpadGroup("g", "Work"), appLabel = { "WhatsApp" }))
        assertEquals("Anyone who is not in my contacts -> Quiet (Quiet)", GroupSummaries.describe(
            GroupRule("r", listOf(Condition(ConditionKind.InContacts, negate = true)), "g"), LaunchpadGroup("g", "Quiet", priority = GroupPriority.Quiet)))
        assertTrue(GroupSummaries.describe(GroupRule("r", emptyList(), "g"), fam).contains("no conditions"))
        assertTrue(GroupSummaries.describe(rule, fam, "").contains("(not set)"))
    }

    @Test fun STR_13_groupEditsRenameReorderWithMoveUpDownAndDeleteCleanUp() {
        var c = cfg("A", "B", "C")
        c = GroupsEdit.rename(c, "g1", "  Bee   Hive ")
        assertEquals("Bee Hive", c.group("g1")!!.name)
        assertEquals(c, GroupsEdit.rename(c, "g1", "  "))
        c = GroupsEdit.move(c, "g2", -1)
        assertEquals(listOf("g0", "g2", "g1"), c.groups.sortedBy { it.order }.map { it.id })
        assertEquals(listOf(0, 1, 2), c.groups.sortedBy { it.order }.map { it.order })
        assertEquals(c, GroupsEdit.move(c, "g0", -1)) // first stays first
        assertEquals(c, GroupsEdit.move(c, "g1", 1))  // last stays last
        c = GroupsEdit.assign(c, AssignKind.App, "x", "g2")
        c = GroupsEdit.addRule(c, GroupRule("r", listOf(Condition(ConditionKind.AppIs, "x")), "g2"))
        c = GroupsEdit.delete(c, "g2")
        assertEquals(listOf("g0", "g1"), c.groups.map { it.id })
        assertEquals(listOf(0, 1), c.groups.map { it.order })
        assertTrue(c.assignments.isEmpty() && c.rules.isEmpty())
    }

    @Test fun STR_13_priorityOrdersGroupsPriorityFirstThenNormalThenQuiet() {
        var c = cfg("A", "B", "C")
        c = GroupsEdit.setPriority(c, "g2", GroupPriority.Priority)
        c = GroupsEdit.setPriority(c, "g0", GroupPriority.Quiet)
        assertEquals(listOf("g2", "g1", "g0"), c.ordered.map { it.id })
    }

    @Test fun STR_13_editsRefuseBlankNamesUnknownTargetsAndOverTheLimits() {
        var c = cfg("A")
        assertEquals(c, GroupsEdit.addGroup(c, "   "))
        assertEquals(c, GroupsEdit.assign(c, AssignKind.App, "x", "missing"))
        assertEquals(c, GroupsEdit.assign(c, AssignKind.App, "", "g0"))
        assertEquals(c, GroupsEdit.addRule(c, GroupRule("r", listOf(Condition(ConditionKind.AppIs, "x")), "missing")))
        repeat(GroupsConfig.MAX_GROUPS) { c = GroupsEdit.addGroup(c, "G$it") }
        assertEquals(GroupsConfig.MAX_GROUPS, c.groups.size)
        assertEquals(GroupsConfig.MAX_NAME, GroupsEdit.addGroup(GroupsConfig(), "x".repeat(200)).groups.single().name.length)
    }

    @Test fun STR_13_ruleDraftFromARowTargetsTheSenderOrTheApp() {
        val d = RuleDrafts.fromNotification(n("1", "Priya Sharma (WhatsApp)"), "g0", "r")
        assertEquals(listOf(Condition(ConditionKind.SenderEquals, "Priya Sharma")), d.conditions)
        val app = RuleDrafts.fromNotification(n("2", null, msg = false, title = "Backup done"), "g0", "r")
        assertEquals(listOf(Condition(ConditionKind.AppIs, "chat.a")), app.conditions)
        assertNotNull(d.id)
    }
}
