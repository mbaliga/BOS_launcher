package com.bos.sphere.core.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset

/** STR-13: persistence, export and import, migration, snapshots, backup classification and the performance budget. */
class StrGroupsStateTest {
    @After fun clean() { DestructiveEdits.install(null); GroupsSettings.restore(GroupsConfig()) }

    private fun sample(): GroupsConfig {
        var c = GroupsConfig()
        c = GroupsEdit.addGroup(c, "Family", GroupPriority.Priority, "heart", 0xFFE91E63, id = "fam")
        c = GroupsEdit.addGroup(c, "Work", id = "work")
        c = GroupsEdit.addGroup(c, "Noise", GroupPriority.Quiet, id = "noise")
        c = GroupsEdit.setDelivery(c, "noise", DeliveryMode.Digest)
        c = GroupsEdit.linkSenders(c, "Priya S", "Priya Sharma (WhatsApp)", contactKey = "c9", id = "p:1")
        c = GroupsEdit.assign(c, AssignKind.Person, "p:1", "work")
        c = GroupsEdit.assign(c, AssignKind.App, "com.news", "noise")
        c = GroupsEdit.assign(c, AssignKind.Conversation, AssignTargets.conversation("chat.a", "Book club"), null)
        c = GroupsEdit.addRule(c, GroupRule("r1", listOf(
            Condition(ConditionKind.LastNameIsMine), Condition(ConditionKind.TimeWindow, from = 60, to = 120),
            Condition(ConditionKind.SenderContains, "x", negate = true),
        ), "fam", name = "Kin"))
        return GroupsEdit.setMyLastName(c, "Sharma").copy(extra = """{"future":{"k":1}}""")
    }

    @Test fun STR_13_theConfigRoundTripsThroughJson() {
        val c = sample()
        val back = GroupsJson.decode(JSONObject(GroupsJson.encode(c).toString()))
        assertEquals(c.copy(extra = back.extra), back)
        assertEquals(1, JSONObject(back.extra).getJSONObject("future").getInt("k"))
        assertEquals(back, GroupsJson.decode(JSONObject(GroupsJson.encode(back).toString())))
    }

    @Test fun STR_13_decodingIsTolerantOfJunkAndCapsSizes() {
        assertEquals(GroupsConfig(), GroupsJson.decode(null))
        assertEquals(GroupsConfig(), GroupsJson.decodeString("not json"))
        val o = JSONObject("""{"groups":[{"id":"a","name":"","priority":"Bogus","delivery":"Nope"},{"id":"a","name":"dup"},{"name":"noid"},5],
            "assignments":[{"kind":"Alien","target":"x"},{"kind":"App","target":"p"},{"kind":"App","target":"p","group":"a"}],
            "rules":[{"id":"r","group":"a","conditions":[{"kind":"Bogus"},{"kind":"AppIs","value":"p"}]},{"id":"bad"}]}""")
        val c = GroupsJson.decode(o)
        assertEquals(listOf("a"), c.groups.map { it.id })
        assertEquals("Group", c.groups.single().name)
        assertEquals(GroupPriority.Normal, c.groups.single().priority)
        assertNull(c.groups.single().delivery)
        assertEquals(1, c.assignments.size)
        assertEquals(listOf(Condition(ConditionKind.AppIs, "p")), c.rules.single().conditions)
        val many = JSONObject().put("groups", org.json.JSONArray().also { a -> repeat(200) { a.put(JSONObject().put("id", "g$it").put("name", "G")) } })
        assertEquals(GroupsConfig.MAX_GROUPS, GroupsJson.decode(many).groups.size)
    }

    @Test fun STR_13_theStateFileCarriesGroupsAndAnOlderFileWithoutThemLeavesTodaysBehaviour() {
        val c = sample()
        val doc = StateCodec.export(StateParts(launchpadGroups = c), 1L)
        val parts = StateCodec.decode(StateDocument.parse(doc.encode())).parts
        assertEquals(c.copy(extra = parts.launchpadGroups!!.extra), parts.launchpadGroups)
        assertEquals(StateSchema.CURRENT, doc.schemaVersion)
        // A file that predates groups: no key, so nothing to apply (null), and the version-1 fixture still migrates.
        val old = StateCodec.export(StateParts(), 1L)
        assertFalse(old.section(StateSchema.K_LAUNCHPAD)!!.has("groups"))
        assertNull(StateCodec.decode(StateDocument.parse(old.encode())).parts.launchpadGroups)
        val v1 = File("src/test/resources/state_v1.json").takeIf { it.exists() }?.readText() ?: javaClass.getResource("/state_v1.json")!!.readText()
        assertNull(StateCodec.decode(StateDocument.parse(v1)).parts.launchpadGroups)
    }

    @Test fun STR_13_unknownFieldsNextToGroupsSurviveTheLiveRoundTrip() {
        val doc = StateCodec.export(StateParts(launchpadGroups = sample()), 1L)
        doc.section(StateSchema.K_LAUNCHPAD)!!.put("futureThing", "kept")
        val decoded = StateCodec.decode(StateDocument.parse(doc.encode()))
        assertEquals("kept", decoded.parts.extras.nested.getJSONObject("launchpad").getString("futureThing"))
        assertFalse("groups is a known key, not carried as an unknown", decoded.parts.extras.nested.optJSONObject("launchpad")!!.has("groups"))
    }

    private class Sink : LaunchpadSink {
        var stored: GroupsConfig? = null
        override fun rules() = emptyList<Rule>()
        override fun removeRule(id: String) {}
        override fun addRule(r: Rule) {}
        override fun setDefaultMode(m: GroupMode) {}
        override fun perApp() = emptyMap<String, GroupMode>()
        override fun setAppMode(pkg: String, m: GroupMode?) {}
        override fun setGesture(g: LaunchpadGesture, a: LaunchpadAction) {}
        override fun groups() = stored
        override fun setGroups(c: GroupsConfig) { stored = c }
    }

    @Test fun STR_13_importWritesGroupsOnlyWhenTheFileHasThem() {
        val sink = Sink().also { it.stored = sample() }
        val tmp = File.createTempFile("extras", ".json").also { it.deleteOnExit() }
        val applier = StateApplier(
            LauncherPrefsStore(MapSharedPreferences()), HomeLayoutStore(MapSharedPreferences()), ShortcutStore(MapSharedPreferences()), sink,
            StateExtrasStore(tmp)::read, StateExtrasStore(tmp)::write,
        )
        assertEquals(sample().copy(extra = applier.current().launchpadGroups!!.extra), applier.current().launchpadGroups)
        val before = sink.stored
        applier.apply(StateParts(launchpadGroups = null), setOf(StateSchema.K_LAUNCHPAD))
        assertEquals(before, sink.stored) // an older file leaves the groups alone
        val incoming = GroupsEdit.addGroup(GroupsConfig(), "Solo", id = "solo")
        applier.apply(StateParts(launchpadGroups = incoming), setOf(StateSchema.K_LAUNCHPAD))
        assertEquals(incoming, sink.stored)
        applier.apply(StateParts(launchpadGroups = GroupsConfig()), setOf(StateSchema.K_LAUNCHPAD))
        assertTrue(sink.stored!!.isEmpty)
    }

    @Test fun CFG_04_deletingAGroupOrRemovingARuleOrAnAssignmentSnapshotsFirst() {
        val seen = ArrayList<Pair<String, Int>>()
        GroupsSettings.restore(sample())
        DestructiveEdits.install { seen += it to GroupsSettings.config.value.groups.size }
        GroupsSettings.addGroup("New")
        GroupsSettings.renameGroup("work", "Job")
        GroupsSettings.moveGroup("work", 1)
        assertTrue("creating, renaming and reordering are not destructive", seen.isEmpty())
        GroupsSettings.deleteGroup("noise")
        assertEquals(listOf("delete Comms group" to 4), seen) // the group was still there when the hook ran
        assertNull(GroupsSettings.config.value.group("noise"))
        assertTrue(GroupsSettings.config.value.assignments.none { it.groupId == "noise" })
        GroupsSettings.deleteGroup("noise")
        GroupsSettings.removeRule("missing")
        GroupsSettings.removeRule("r1")
        GroupsSettings.unassign(AssignKind.App, "com.nothing")
        GroupsSettings.unassign(AssignKind.Person, "p:1")
        assertEquals(listOf("delete Comms group", "remove Comms group rule", "remove Comms assignment"), seen.map { it.first })
    }

    @Test fun STR_13_theStoreRestoresAndSnapshotsWholeConfigs() {
        val c = sample()
        GroupsSettings.restore(c)
        assertEquals(c, GroupsSettings.snapshot())
        GroupsSettings.setMyLastName("  Verma ")
        assertEquals("Verma", GroupsSettings.config.value.myLastName)
        GroupsSettings.linkSenders("Anil", "Anil K")
        assertEquals(2, GroupsSettings.config.value.people.size)
    }

    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }

    @Test fun NFR_18_theGroupsFileIsPersonalAndExcludedFromCloudBackupAndDeviceTransfer() {
        assertTrue(GroupsSettings.FILE in StateSchema.BACKUP_EXCLUDED_PREF_FILES)
        for (f in listOf("data_extraction_rules.xml", "backup_rules.xml")) {
            val body = File(root(), "app/src/main/res/xml/$f").readText()
            assertTrue("$f excludes ${GroupsSettings.FILE}", body.contains("""domain="sharedpref" path="launchpad_groups.xml""""))
        }
        // It is also carried by the config export (the launchpad section), not left out of it.
        assertNotNull(StateCodec.export(StateParts(launchpadGroups = GroupsConfig()), 1L).section(StateSchema.K_LAUNCHPAD)!!.optJSONObject("groups"))
    }

    @Test fun STR_13_matchingFiveHundredNotificationsFitsTheBudget() {
        var c = GroupsConfig()
        repeat(10) { c = GroupsEdit.addGroup(c, "G$it", id = "g$it") }
        c = GroupsEdit.setMyLastName(c, "Sharma")
        repeat(40) { i ->
            c = GroupsEdit.addRule(c, GroupRule("r$i", listOf(
                Condition(ConditionKind.SenderContains, "zz$i"), Condition(ConditionKind.TextContains, "nomatch$i"), Condition(ConditionKind.LastNameIsMine),
            ), "g${i % 10}"))
        }
        repeat(100) { i -> c = GroupsEdit.assign(c, AssignKind.Person, "n:person $i", "g${i % 10}") }
        val contacts = ContactIndex((0 until 300).map { ContactIdentity("c$it", "Contact Number$it", setOf("Nick$it"), setOf("+1555010${it.toString().padStart(4, '0')}")) })
        val list = (0 until 500).map { i ->
            HubNotification(id = "$i", key = "k$i", source = "x", title = "Contact Number${i % 400}", summary = "text $i", timestamp = 1_700_000_000_000L + i,
                packageName = "app.${i % 25}", sender = "Contact Number${i % 400}", conversation = "chat ${i % 50}", isMessaging = true)
        }
        fun once(): Long {
            val t = System.nanoTime()
            val res = GroupResolver(c, contacts, ZoneOffset.UTC)
            LaunchpadViewBuilder.build(list, emptyList(), LaunchpadPrefs(), emptyList(), emptyMap(), 1_700_000_100_000L, ZoneOffset.UTC, grouping = res)
            return (System.nanoTime() - t) / 1_000_000
        }
        once() // warm up the JIT
        val best = (1..5).minOf { once() }
        assertTrue("500 notifications x 40 rules took ${best} ms", best < 750)
    }
}
