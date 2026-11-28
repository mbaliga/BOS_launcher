package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class LaunchpadLogicTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = LocalDate.of(2024, 3, 10).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val hour = 3_600_000L
    private val labelFor: (LocalDate) -> String = { "D:$it" }

    private fun n(
        key: String, pkg: String = "pkg.a", app: String = "A", title: String = "t$key", summary: String = "",
        ago: Long = 0, sender: String? = null, conv: String? = null, channel: String? = null,
        ongoing: Boolean = false,
    ) = HubNotification(
        id = key, key = key, source = "x", title = title, summary = summary, timestamp = now - ago,
        packageName = pkg, appLabel = app, sender = sender, conversation = conv, channelId = channel,
        isOngoing = ongoing,
    )

    private fun apply(
        list: List<HubNotification>, rules: List<Rule> = emptyList(), prefs: LaunchpadPrefs = LaunchpadPrefs(GroupMode.Time),
    ) = LaunchpadLogic.apply(list, rules, prefs, now, zone, labelFor)

    // ---- groupByDay / counts / filter ----

    @Test fun `groupByDay labels today yesterday and older`() {
        val g = LaunchpadLogic.groupByDay(
            listOf(n("old", ago = 5 * 24 * hour), n("y", ago = 20 * hour), n("t", ago = hour), n("t2", ago = 2 * hour)),
            now, zone, labelFor,
        )
        assertEquals(listOf("Today", "Yesterday", "D:2024-03-05"), g.map { it.label })
        assertEquals(listOf("t", "t2"), g[0].items.map { it.key })
    }

    @Test fun `groupByDay empty`() = assertTrue(LaunchpadLogic.groupByDay(emptyList(), now, zone, labelFor).isEmpty())

    @Test fun `groupByDay respects time zone`() {
        val late = n("x", ago = 11 * hour + 30 * 60_000) // 00:30 UTC today
        val g = LaunchpadLogic.groupByDay(listOf(late), now, ZoneOffset.ofHours(-2), labelFor)
        assertEquals("Yesterday", g[0].label)
        assertEquals("Today", LaunchpadLogic.groupByDay(listOf(late), now, zone, labelFor)[0].label)
    }

    @Test fun `countsBySource sorted by count then label`() {
        val c = LaunchpadLogic.countsBySource(
            listOf(n("1", "b", "Bee"), n("2", "a", "Ay"), n("3", "c", "Cee"), n("4", "c", "Cee"), n("5", "c", "Cee")),
        )
        assertEquals(listOf("c" to 3, "a" to 1, "b" to 1), c.map { it.packageName to it.count })
        assertEquals("Cee", c[0].appLabel)
    }

    @Test fun `filter by package`() {
        val l = listOf(n("1", "a"), n("2", "b"))
        assertEquals(2, LaunchpadLogic.filter(l, null).size)
        assertEquals(listOf("2"), LaunchpadLogic.filter(l, "b").map { it.key })
    }

    // ---- matching ----

    @Test fun `all matchers must match case-insensitively`() {
        val note = n("1", "pkg.a", title = "Hello World", sender = "Mum", channel = "chat")
        assertTrue(Rule("r", "PKG.A", "mum", "CHAT", "world", RuleAction.Hide).matches(note))
        assertFalse(Rule("r", "pkg.b", null, null, null, RuleAction.Hide).matches(note))
        assertFalse(Rule("r", null, "dad", null, null, RuleAction.Hide).matches(note))
        assertFalse(Rule("r", null, null, "other", null, RuleAction.Hide).matches(note))
        assertFalse(Rule("r", null, null, null, "nope", RuleAction.Hide).matches(note))
        assertTrue(Rule("r", null, null, null, null, RuleAction.Hide).matches(note))
    }

    @Test fun `text matches summary and channel matches name`() {
        val note = n("1", summary = "lunch at noon").copy(channelName = "Promos")
        assertTrue(Rule("r", text = "NOON", action = RuleAction.Hide).matches(note))
        assertTrue(Rule("r", channel = "promos", action = RuleAction.Hide).matches(note))
    }

    @Test fun `conversation matcher and describe`() {
        val note = n("1", "a", sender = "Mum", conv = "Family")
        val r = Rule("r", "a", conversation = "family", action = RuleAction.Pin)
        assertTrue(r.matches(note))
        assertFalse(r.matches(n("2", "a", conv = "Work")))
        assertEquals("Pin conversation family in Chat", r.describe("Chat"))
        assertTrue(r.specificity < Rule("s", sender = "x", action = RuleAction.Pin).specificity)
        val back = LaunchpadJson.decodeRules(LaunchpadJson.encodeRules(listOf(r)))
        assertEquals(listOf(r), back)
    }

    @Test fun `sender falls back to title`() {
        assertTrue(Rule("r", sender = "t1", action = RuleAction.Pin).matches(n("1")))
    }

    @Test fun `most specific rule wins per action and ties go to latest`() {
        val note = n("1", "pkg.a", sender = "Mum")
        val app = Rule("app", "pkg.a", action = RuleAction.AutoClear, minutes = 60)
        val snd = Rule("snd", "pkg.a", "Mum", action = RuleAction.AutoClear, minutes = 5)
        val glob = Rule("glob", action = RuleAction.AutoClear, minutes = 999)
        assertEquals("snd", LaunchpadLogic.bestRule(listOf(glob, snd, app), note, RuleAction.AutoClear)?.id)
        assertEquals("app", LaunchpadLogic.bestRule(listOf(glob, app), note, RuleAction.AutoClear)?.id)
        val dup = app.copy(id = "app2", minutes = 10)
        assertEquals("app2", LaunchpadLogic.bestRule(listOf(app, dup), note, RuleAction.AutoClear)?.id)
        assertEquals(null, LaunchpadLogic.bestRule(listOf(app), note, RuleAction.Hide))
    }

    // ---- hide / auto-clear ----

    @Test fun `hide removes notifications`() {
        val s = apply(listOf(n("1", "a"), n("2", "b")), listOf(Rule("h", "a", action = RuleAction.Hide)))
        assertEquals(listOf("2"), s.flatMap { it.items }.map { it.key })
    }

    @Test fun `more specific pin rescues from hide`() {
        val rules = listOf(
            Rule("h", "a", action = RuleAction.Hide),
            Rule("p", "a", "Mum", action = RuleAction.Pin),
        )
        val s = apply(listOf(n("1", "a", sender = "Mum"), n("2", "a", sender = "Bob")), rules)
        assertEquals(listOf("1"), s.flatMap { it.items }.map { it.key })
    }

    @Test fun `less specific pin does not rescue from hide`() {
        val rules = listOf(Rule("h", "a", "Mum", action = RuleAction.Hide), Rule("p", "a", action = RuleAction.Pin))
        assertTrue(apply(listOf(n("1", "a", sender = "Mum")), rules).isEmpty())
    }

    @Test fun `auto clear after minutes`() {
        val rules = listOf(Rule("c", "a", action = RuleAction.AutoClear, minutes = 60))
        val s = apply(listOf(n("old", "a", ago = 2 * hour), n("new", "a", ago = 10 * 60_000L), n("b", "b", ago = 9 * hour)), rules)
        assertEquals(setOf("new", "b"), s.flatMap { it.items }.map { it.key }.toSet())
    }

    @Test fun `auto clear with zero minutes never clears`() {
        val rules = listOf(Rule("c", "a", action = RuleAction.AutoClear, minutes = 0))
        assertEquals(1, apply(listOf(n("1", "a", ago = 100 * hour)), rules).size)
    }

    @Test fun `auto clear boundary is inclusive`() {
        val rules = listOf(Rule("c", action = RuleAction.AutoClear, minutes = 60))
        assertTrue(LaunchpadLogic.isExpired(n("1", ago = hour), rules, now))
        assertFalse(LaunchpadLogic.isExpired(n("1", ago = hour - 1), rules, now))
    }

    // ---- pin / important ----

    @Test fun `pinned items go first in a pinned section`() {
        val s = apply(
            listOf(n("new", "a", ago = 0), n("pin", "b", ago = 3 * hour)),
            listOf(Rule("p", "b", action = RuleAction.Pin)),
        )
        assertEquals(LaunchpadLogic.PINNED_KEY, s[0].key)
        assertTrue(s[0].pinned)
        assertEquals(listOf("pin"), s[0].items.map { it.key })
        assertEquals(listOf("new"), s[1].items.map { it.key })
        assertFalse(s[1].pinned)
    }

    @Test fun `important floats to top of its section`() {
        val s = apply(
            listOf(n("new", "a", ago = 0), n("imp", "a", ago = hour)),
            listOf(Rule("i", sender = "timp", action = RuleAction.Important)),
        )
        assertEquals(listOf("imp", "new"), s[0].items.map { it.key })
    }

    // ---- grouping ----

    @Test fun `time mode groups by day newest first`() {
        val s = apply(listOf(n("y", ago = 20 * hour), n("t", ago = hour)))
        assertEquals(listOf("Today", "Yesterday"), s.map { it.title })
    }

    @Test fun `app mode one section per app ordered by newest`() {
        val list = listOf(n("1", "a", "A", ago = 5 * hour), n("2", "b", "B", ago = hour), n("3", "a", "A", ago = 6 * hour))
        val s = apply(list, prefs = LaunchpadPrefs(GroupMode.App))
        assertEquals(listOf("B", "A"), s.map { it.title })
        assertEquals(2, s[1].items.size)
        assertEquals("app:a", s[1].key)
    }

    @Test fun `sender mode groups per person within app`() {
        val list = listOf(
            n("1", "a", sender = "Mum", ago = 3 * hour), n("2", "a", sender = "mum", ago = hour),
            n("3", "a", sender = "Bob"), n("4", "b", sender = "Mum"),
        )
        val s = apply(list, prefs = LaunchpadPrefs(GroupMode.Sender))
        assertEquals(3, s.size)
        assertEquals(2, s.first { it.key == "sender:a:mum" }.items.size)
        assertEquals("A", s.first { it.key == "sender:a:bob" }.subtitle)
    }

    @Test fun `conversation mode uses conversation then sender then title`() {
        val list = listOf(
            n("1", "a", conv = "Family", sender = "Mum"), n("2", "a", conv = "Family", sender = "Dad"),
            n("3", "a", sender = "Bob"),
        )
        val s = apply(list, prefs = LaunchpadPrefs(GroupMode.Conversation))
        assertEquals(setOf("Family", "Bob"), s.map { it.title }.toSet())
        assertEquals(2, s.first { it.title == "Family" }.items.size)
    }

    @Test fun `flat mode is a single section`() {
        val s = apply(listOf(n("1", ago = 5 * hour), n("2", "b"), n("3", ago = 30 * hour)), prefs = LaunchpadPrefs(GroupMode.Flat))
        assertEquals(1, s.size)
        assertEquals(listOf("2", "1", "3"), s[0].items.map { it.key })
    }

    @Test fun `per app override beats default`() {
        val s = apply(
            listOf(n("1", "a", "A"), n("2", "b", "B")),
            prefs = LaunchpadPrefs(GroupMode.Flat, mapOf("a" to GroupMode.App)),
        )
        assertEquals(setOf("app:a", "flat"), s.map { it.key }.toSet())
    }

    @Test fun `grouping rule beats per app and default and most specific wins`() {
        val rules = listOf(
            Rule("g1", "a", action = RuleAction.GroupSender),
            Rule("g2", "a", "Mum", action = RuleAction.GroupApp),
        )
        val prefs = LaunchpadPrefs(GroupMode.Flat, mapOf("a" to GroupMode.Conversation))
        assertEquals(GroupMode.App, LaunchpadLogic.effectiveMode(n("1", "a", sender = "Mum"), rules, prefs))
        assertEquals(GroupMode.Sender, LaunchpadLogic.effectiveMode(n("2", "a", sender = "Bob"), rules, prefs))
        assertEquals(GroupMode.Conversation, LaunchpadLogic.effectiveMode(n("3", "z"), emptyList(), prefs.copy(perApp = mapOf("z" to GroupMode.Conversation))))
        assertEquals(GroupMode.Flat, LaunchpadLogic.effectiveMode(n("4", "q"), rules, prefs))
    }

    @Test fun `apply on empty list`() = assertTrue(apply(emptyList()).isEmpty())

    @Test fun `all hidden gives no sections`() {
        assertTrue(apply(listOf(n("1")), listOf(Rule("h", action = RuleAction.Hide))).isEmpty())
    }

    // ---- rail counts ----

    @Test fun `unread counts ignore silent hidden ongoing expired and read`() {
        val list = listOf(
            n("1", "a", "A"), n("2", "a", "A"), n("3", "b", "B"), n("4", "c", "C"), n("5", "d", "D"),
            n("6", "e", "E", ongoing = true), n("7", "f", "F", ago = 3 * hour), n("8", "a", "A"),
        )
        val rules = listOf(
            Rule("s", "b", action = RuleAction.Silent),
            Rule("h", "c", action = RuleAction.Hide),
            Rule("x", "f", action = RuleAction.AutoClear, minutes = 60),
        )
        val c = LaunchpadLogic.unreadCounts(list, rules, setOf("8"), now)
        assertEquals(listOf("a" to 2, "d" to 1), c.map { it.packageName to it.count })
        assertEquals(3, LaunchpadLogic.unreadTotal(list, rules, setOf("8"), now))
    }

    // ---- describe & json ----

    @Test fun `describe in words`() {
        assertEquals("Hide notifications from WhatsApp", Rule("1", "w", action = RuleAction.Hide).describe("WhatsApp"))
        assertEquals("Pin Mum in Messages", Rule("1", "m", "Mum", action = RuleAction.Pin).describe("Messages"))
        assertEquals("Auto-clear notifications from Mail after 1 day", Rule("1", "m", action = RuleAction.AutoClear, minutes = 1440).describe("Mail"))
        assertEquals("Auto-clear notifications from Mail after 1 hour", Rule("1", "m", action = RuleAction.AutoClear, minutes = 60).describe("Mail"))
        assertEquals("Mute notifications containing “promo” (no badges)", Rule("1", text = "promo", action = RuleAction.Silent).describe(null))
        assertEquals("Group notifications from Mail by app", Rule("1", "m", action = RuleAction.GroupApp).describe("Mail"))
    }

    @Test fun `rules json round trip`() {
        val rules = listOf(
            Rule("1", "p", "Mum", "ch", "txt", RuleAction.AutoClear, 90),
            Rule("2", action = RuleAction.Flat),
        )
        assertEquals(rules, LaunchpadJson.decodeRules(LaunchpadJson.encodeRules(rules)))
    }

    @Test fun `rules json tolerates garbage`() {
        assertTrue(LaunchpadJson.decodeRules(null).isEmpty())
        assertTrue(LaunchpadJson.decodeRules("not json").isEmpty())
        val partial = """[{"id":"1","action":"Bogus"},{"id":"2","action":"Hide","pkg":"x"}]"""
        assertEquals(listOf(Rule("2", "x", action = RuleAction.Hide)), LaunchpadJson.decodeRules(partial))
    }

    @Test fun `prefs json round trip`() {
        val p = LaunchpadPrefs(GroupMode.Sender, mapOf("a" to GroupMode.App, "b" to GroupMode.Flat))
        assertEquals(p, LaunchpadJson.decodePrefs(LaunchpadJson.encodePrefs(p)))
        assertEquals(LaunchpadPrefs(), LaunchpadJson.decodePrefs("???"))
    }

    @Test fun `create blanks become null`() {
        val r = Rule.create(RuleAction.Hide, packageName = "p", sender = " ".trim(), text = "")
        assertEquals(null, r.sender)
        assertEquals(null, r.text)
        assertEquals("p", r.packageName)
    }
}
