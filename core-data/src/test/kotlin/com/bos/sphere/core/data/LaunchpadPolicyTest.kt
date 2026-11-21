package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class LaunchpadPolicyTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2024, 3, 10)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = at(12)

    private fun n(
        key: String, pkg: String = "pkg.a", app: String = "A", ts: Long = now, conv: String? = null, sender: String? = null,
        ongoing: Boolean = false,
    ) = HubNotification(
        id = key, key = key, source = "x", title = "t$key", summary = "s", timestamp = ts,
        packageName = pkg, appLabel = app, conversation = conv, sender = sender, isOngoing = ongoing,
    )

    // ---- STR-02: grouped by app, then conversation, newest first ----

    @Test fun STR_02_groupedByAppThenConversationNewestFirst() {
        val list = listOf(
            n("a1", "pkg.a", "A", at(9), conv = "c1"), n("a2", "pkg.a", "A", at(11), conv = "c2"),
            n("b1", "pkg.b", "B", at(11, 30), conv = "x"), n("a3", "pkg.a", "A", at(8), conv = "c1"),
        )
        val s = LaunchpadLogic.apply(list, emptyList(), LaunchpadPrefs(GroupMode.AppConversation), now, zone)
        // App B is newest (11:30) so it leads; app A's conversations follow together, c2 (11:00) before c1 (9:00).
        assertEquals(listOf("x", "c2", "c1"), s.map { it.title })
        assertEquals(listOf("B", "A", "A"), s.map { it.subtitle })
        assertEquals(listOf("a1", "a3"), s.last().items.map { it.key })
    }

    @Test fun STR_02_appConversationIsTheDefault() {
        assertEquals(GroupMode.AppConversation, LaunchpadPrefs().defaultMode)
    }

    // ---- STR-04: delivery per app ----

    @Test fun STR_04_digestReleaseTimes() {
        val times = listOf(8 * 60, 18 * 60)
        assertEquals(at(8), DeliveryLogic.lastRelease(now, zone, times))
        assertEquals(at(18), DeliveryLogic.nextRelease(now, zone, times))
        assertEquals(at(18, d = day.minusDays(1)), DeliveryLogic.lastRelease(at(7), zone, times))
        assertEquals(at(8, d = day.plusDays(1)), DeliveryLogic.nextRelease(at(19), zone, times))
        assertNull(DeliveryLogic.lastRelease(now, zone, emptyList()))
    }

    @Test fun STR_04_digestHoldsNewItemsAndReleasesOlderOnes() {
        val p = listOf(AppPolicy("pkg.a", DeliveryMode.Digest))
        val fresh = n("new", ts = at(10)); val old = n("old", ts = at(7, 30)); val ongoing = n("on", ts = at(10), ongoing = true)
        assertTrue(DeliveryLogic.isHeld(fresh, p, now, zone))
        assertFalse(DeliveryLogic.isHeld(old, p, now, zone))
        assertFalse(DeliveryLogic.isHeld(ongoing, p, now, zone))
        assertFalse(DeliveryLogic.isHeld(n("other", "pkg.z", ts = at(10)), p, now, zone))
        // Once 18:00 passes, the held item is released.
        assertFalse(DeliveryLogic.isHeld(fresh, p, at(18, 1), zone))
    }

    @Test fun STR_04_viewSplitsDigestHeldAndSilent() {
        val policies = listOf(AppPolicy("pkg.a", DeliveryMode.Digest), AppPolicy("pkg.s", DeliveryMode.SilentInLaunchpad))
        val items = listOf(
            n("held", "pkg.a", ts = at(10)), n("released", "pkg.a", ts = at(7)),
            n("quiet", "pkg.s", "S", ts = at(11)), n("live", "pkg.l", "L", ts = at(11, 30)),
        )
        val v = LaunchpadViewBuilder.build(items, emptyList(), LaunchpadPrefs(), policies, emptyMap(), now, zone)
        assertEquals(listOf("held"), v.held.map { it.key })
        assertEquals(at(18), v.nextRelease)
        assertEquals(setOf("quiet"), v.silentKeys)
        val digest = v.sections.first { it.key == LaunchpadView.DIGEST_KEY }
        assertEquals(listOf("released"), digest.items.map { it.key })
        assertTrue(v.sections.flatMap { it.items }.none { it.key == "held" })
        assertTrue(v.sections.flatMap { it.items }.any { it.key == "quiet" })
    }

    @Test fun STR_04_pinnedStaysFirstAndSnoozeHides() {
        val rules = listOf(Rule.create(RuleAction.Pin, packageName = "pkg.l"))
        val items = listOf(n("p", "pkg.l", "L"), n("z", "pkg.z", "Z"), n("zz", "pkg.y", "Y"))
        val v = LaunchpadViewBuilder.build(items, rules, LaunchpadPrefs(), emptyList(), mapOf("zz" to now + 1000), now, zone)
        assertTrue(v.sections.first().pinned)
        assertTrue(v.sections.flatMap { it.items }.none { it.key == "zz" })
    }

    @Test fun STR_04_primaryActionPerApp() {
        val p = listOf(AppPolicy("pkg.a", primaryAction = LaunchpadAction.MarkRead))
        assertEquals(LaunchpadAction.MarkRead, DeliveryLogic.primaryFor("pkg.a", p, LaunchpadAction.Dismiss))
        assertEquals(LaunchpadAction.Dismiss, DeliveryLogic.primaryFor("pkg.b", p, LaunchpadAction.Dismiss))
    }

    @Test fun STR_04_snapshotRoundTripKeepsEverythingAndUnknownFields() {
        val snap = LaunchpadRulesSnapshot(
            rules = listOf(Rule.create(RuleAction.Hide, packageName = "pkg.h")),
            prefs = LaunchpadPrefs(GroupMode.Flat, mapOf("pkg.a" to GroupMode.App)),
            gestures = LaunchpadGestures.defaults + (LaunchpadGesture.SwipeItemLeft to LaunchpadAction.MarkRead),
            policies = listOf(AppPolicy("pkg.a", DeliveryMode.Digest, listOf(600, 1200), LaunchpadAction.MuteApp)),
        )
        val withFuture = org.json.JSONObject(snap.toJson()).put("futureField", org.json.JSONObject().put("x", 1)).toString()
        val back = LaunchpadRulesSnapshot.fromJson(withFuture)
        assertEquals(snap.rules, back.rules)
        assertEquals(snap.prefs, back.prefs)
        assertEquals(snap.gestures, back.gestures)
        assertEquals(snap.policies, back.policies)
        assertTrue(org.json.JSONObject(back.toJson()).has("futureField"))
        assertEquals(1, org.json.JSONObject(back.toJson()).getJSONObject("futureField").getInt("x"))
    }

    @Test fun STR_04_snapshotSurvivesGarbageAndIsPersonalContentFree() {
        assertEquals(LaunchpadRulesSnapshot(), LaunchpadRulesSnapshot.fromJson("not json"))
        assertEquals(LaunchpadRulesSnapshot(), LaunchpadRulesSnapshot.fromJson(null))
        val json = LaunchpadRulesSnapshot(policies = listOf(AppPolicy("p"))).toJson()
        assertFalse(json.contains("snooz")); assertFalse(json.contains("readKeys"))
        assertTrue(LAUNCHPAD_SCOPE_NOTICE.contains("Liftoff"))
    }

    @Test fun STR_04_policyDecodingDropsBadModesAndTimes() {
        val arr = org.json.JSONArray("""[{"pkg":"a","mode":"Nope"},{"pkg":"b","mode":"Digest","times":[600,99999,600,-3]},{"mode":"Digest"}]""")
        val p = PolicyJson.decode(arr)
        assertEquals(1, p.size)
        assertEquals(listOf(600), p[0].digestMinutes)
    }

    // ---- STR-03: snooze and the one row menu ----

    @Test fun STR_03_snoozePresets() {
        assertEquals(now + 600_000, SnoozeLogic.until(SnoozeLogic.Preset.TenMinutes, now, zone))
        assertEquals(at(18), SnoozeLogic.until(SnoozeLogic.Preset.ThisEvening, now, zone))
        assertEquals(at(18, d = day.plusDays(1)), SnoozeLogic.until(SnoozeLogic.Preset.ThisEvening, at(19), zone))
        assertEquals(at(8, d = day.plusDays(1)), SnoozeLogic.until(SnoozeLogic.Preset.Tomorrow, now, zone))
        assertTrue(SnoozeLogic.isSnoozed("k", mapOf("k" to now + 1), now))
        assertFalse(SnoozeLogic.isSnoozed("k", mapOf("k" to now), now))
        assertEquals(mapOf("b" to now + 5), SnoozeLogic.prune(mapOf("a" to now - 1, "b" to now + 5, "gone" to now + 9), setOf("a", "b"), now))
    }

    @Test fun STR_03_longPressSecondaryClickMenuKeyAndPaletteGiveIdenticalEntries() {
        for (c in listOf(RowMenuContext(), RowMenuContext(canDismiss = false, unread = false, hasSender = false), RowMenuContext(canReply = true, hasPackage = false))) {
            val e = LaunchpadRowMenus.entries(c)
            assertEquals(e, LaunchpadRowMenus.onLongPress(c))
            assertEquals(e, LaunchpadRowMenus.onSecondaryClick(c))
            assertEquals(e, LaunchpadRowMenus.onMenuKey(c))
            assertEquals(e, LaunchpadRowMenus.palette(c))
            assertTrue(RowCommand.Snooze in e)
        }
        assertTrue(RowCommand.Dismiss !in LaunchpadRowMenus.entries(RowMenuContext(canDismiss = false)))
        assertTrue(RowCommand.Reply in LaunchpadRowMenus.entries(RowMenuContext(canReply = true)))
        assertTrue(LaunchpadRowMenus.paletteCells(RowMenuContext()).all { it in LaunchpadRowMenus.entries(RowMenuContext()) })
    }

    @Test fun STR_03_deleteRunsPrimaryAndMenuKeyOpensPalette() {
        assertEquals(RowKeys.Result.Primary, RowKeys.map(RowKeys.FORWARD_DEL))
        assertEquals(RowKeys.Result.Primary, RowKeys.map(RowKeys.DEL))
        assertEquals(RowKeys.Result.Palette, RowKeys.map(RowKeys.MENU))
        assertEquals(RowKeys.Result.Palette, RowKeys.map(RowKeys.F10, shift = true))
        assertNull(RowKeys.map(RowKeys.F10))
        assertNull(RowKeys.map(999))
    }

    // ---- STR-08: arrows between groups and rows ----

    private val sections = listOf(
        Section("A", "g1", listOf(n("r1"), n("r2"))), Section("B", "g2", listOf(n("r3"))),
    )

    @Test fun STR_08_arrowsWalkGroupsAndRowsWithoutWrap() {
        val o = LaunchpadFocus.order(sections, emptySet())
        assertEquals(listOf("g1", "r1", "r2", "g2", "r3"), o.map { it.key })
        var f: FocusTarget? = null
        f = LaunchpadFocus.move(o, f, 1); assertEquals("g1", f!!.key)
        repeat(4) { f = LaunchpadFocus.move(o, f, 1) }
        assertEquals("r3", f!!.key)
        assertEquals("r3", LaunchpadFocus.move(o, f, 1)!!.key)
        assertEquals("g2", LaunchpadFocus.move(o, f, -1)!!.key)
        assertEquals("g1", LaunchpadFocus.owningGroup(o, o[2])!!.key)
        assertNull(LaunchpadFocus.owningGroup(o, o[0]))
    }

    @Test fun STR_08_collapsedGroupsHideTheirRows() {
        assertEquals(listOf("g1", "g2", "r3"), LaunchpadFocus.order(sections, setOf("g1")).map { it.key })
    }

    @Test fun STR_08_focusLandsOnNeighbourAfterRemoval() {
        val before = LaunchpadFocus.order(sections, emptySet())
        val after = LaunchpadFocus.order(listOf(Section("A", "g1", listOf(n("r2"))), sections[1]), emptySet())
        assertEquals("r2", LaunchpadFocus.afterRemoval(before, before[1], after)!!.key)
        assertNull(LaunchpadFocus.afterRemoval(before, before[1], emptyList()))
        assertNotNull(LaunchpadFocus.move(before, null, 0))
    }

    @Test fun STR_08_keyMapping() {
        assertEquals(RowKeys.Result.Move(1), RowKeys.map(RowKeys.DPAD_DOWN))
        assertEquals(RowKeys.Result.Move(-1), RowKeys.map(RowKeys.DPAD_UP))
        assertEquals(RowKeys.Result.Close, RowKeys.map(RowKeys.ESCAPE))
        assertEquals(RowKeys.Result.Activate, RowKeys.map(RowKeys.ENTER))
        assertEquals(RowKeys.Result.Collapse, RowKeys.map(RowKeys.DPAD_LEFT))
        assertEquals(RowKeys.Result.Expand, RowKeys.map(RowKeys.DPAD_RIGHT))
    }

    // ---- STR-09: waiting count ----

    @Test fun STR_09_waitingCountRespectsRulesDigestSnoozeAndRead() {
        val rules = listOf(Rule.create(RuleAction.Silent, packageName = "pkg.m"), Rule.create(RuleAction.Hide, packageName = "pkg.h"))
        val policies = listOf(AppPolicy("pkg.d", DeliveryMode.Digest), AppPolicy("pkg.q", DeliveryMode.SilentInLaunchpad))
        val items = listOf(
            n("count1"), n("count2", "pkg.b"), n("muted", "pkg.m"), n("hidden", "pkg.h"), n("held", "pkg.d", ts = at(10)),
            n("quiet", "pkg.q"), n("read"), n("snoozed", "pkg.s"), n("ongoing", "pkg.o", ongoing = true),
        )
        val w = WaitingLogic.waiting(items, rules, policies, setOf("read"), mapOf("snoozed" to now + 5), now, zone)
        assertEquals(listOf("count1", "count2"), w.map { it.key })
        assertEquals(listOf("count2"), WaitingLogic.fresh(setOf("count1"), w).map { it.key })
    }
}
