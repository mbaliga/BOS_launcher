package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Wave 13 audit of STR-13: edge cases of the name folding and the rule engine. */
class StrAuditW13Test {
    private val zone = ZoneOffset.UTC
    private fun n(sender: String, pkg: String = "chat.a", text: String = "hello") = HubNotification(
        id = sender, key = "k:" + sender.hashCode(), source = "x", title = "t", summary = text, timestamp = 1_700_000_000_000L,
        packageName = pkg, appLabel = pkg, sender = sender, isMessaging = true,
    )
    private fun cfgWith(vararg conds: Condition): GroupsConfig =
        GroupsEdit.addRule(GroupsEdit.addGroup(GroupsConfig(), "G", id = "g0"), GroupRule("r1", conds.toList(), "g0"))

    @Test fun STR_13_turkishDottedAndDotlessIAreOneLetterWhenMatchingNames() {
        assertEquals(PersonNames.key("Işık Yılmaz"), PersonNames.key("ISIK YILMAZ"))
        assertEquals(PersonNames.key("İsmail Çelik"), PersonNames.key("ismail celik"))
        assertEquals(PersonNames.key("Sinan Işık"), PersonNames.key("SINAN IŞIK"))
        val c = cfgWith(Condition(ConditionKind.SenderContains, "ışık"))
        assertEquals("g0", GroupResolver(c, NoContacts, zone).resolve(n("SINAN IŞIK")).group?.id)
        assertEquals("g0", GroupResolver(c, NoContacts, zone).resolve(n("Sinan Isik")).group?.id)
    }

    @Test fun STR_13_aHugeUnbalancedSenderNameIsHandledInBoundedTime() {
        val start = System.nanoTime()
        val huge = "(".repeat(300_000) + "Priya"
        val k = PersonNames.key(huge)
        val c = cfgWith(Condition(ConditionKind.SenderContains, "x"))
        GroupResolver(c, NoContacts, zone).resolve(n(huge))
        assertTrue(k.length <= PersonNames.MAX_NAME_CHARS)
        assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", (System.nanoTime() - start) / 1_000_000 < 3000)
    }

    @Test fun STR_13_aBlankConditionValueNeverMatchesEvenWhenNegated() {
        for (kind in listOf(ConditionKind.AppIs, ConditionKind.PersonIs, ConditionKind.SenderContains, ConditionKind.SenderEquals, ConditionKind.TextContains, ConditionKind.ConversationContains)) {
            val c = cfgWith(Condition(kind, "  ", negate = true))
            assertNull("$kind", GroupResolver(c, NoContacts, zone).resolve(n("Priya")).group)
        }
        // A value that is only decoration folds to nothing: same rule.
        val emoji = cfgWith(Condition(ConditionKind.SenderContains, "😀", negate = true))
        assertNull(GroupResolver(emoji, NoContacts, zone).resolve(n("Priya")).group)
    }

    @Test fun STR_13_aRuleForADeletedGroupAndAPersonLinkedToAGoneContactNeverCrashOrMatch() {
        var c = cfgWith(Condition(ConditionKind.SenderContains, "priya"))
        c = c.copy(groups = emptyList())
        assertFalse(GroupResolver(c, NoContacts, zone).resolve(n("Priya")).group != null)
        var d = GroupsEdit.addGroup(GroupsConfig(), "G", id = "g0")
        d = d.copy(people = listOf(Person(id = "p1", name = "Priya", aliases = listOf("P"), contactKey = "gone")))
        d = GroupsEdit.assign(d, AssignKind.Person, "p1", "g0")
        val r = GroupResolver(d, ContactIndex(listOf(ContactIdentity("other", "Somebody Else"))), zone)
        assertEquals("g0", r.resolve(n("Priya")).group?.id) // still found by name
        assertNull(r.resolve(n("Somebody Else")).group)
    }

    @Test fun STR_13_emptySenderAndEmptyMyLastNameAreSafe() {
        val c = cfgWith(Condition(ConditionKind.LastNameIsMine)).copy(myLastName = "")
        assertNull(GroupResolver(c, NoContacts, zone).resolve(n("")).group)
        assertNull(GroupResolver(c.copy(myLastName = "Sharma"), NoContacts, zone).resolve(n("")).group)
        assertEquals("g0", GroupResolver(c.copy(myLastName = "Sharma"), NoContacts, zone).resolve(n("Priya Sharma")).group?.id)
    }
}
