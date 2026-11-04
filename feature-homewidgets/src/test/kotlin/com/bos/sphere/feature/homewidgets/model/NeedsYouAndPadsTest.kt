package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsYouAndPadsTest {

    private val own = "com.bos.sphere"
    private val labels = mapOf("org.chat" to "Chat", "org.mail" to "Mail", "org.bank" to "Bank", "org.news" to "News")
    private val work = 10L
    private fun n(pkg: String, summary: Boolean = false, ongoing: Boolean = false, user: Long = 0L) = NotificationFact(pkg, summary, ongoing, user)
    private fun view(facts: List<NotificationFact>?, hidden: Set<String> = emptySet(), names: Boolean = true, rows: Int = 5, access: Boolean = true) =
        NeedsYouModel.view(access, facts, own, hidden, names, rows) { pkg, user ->
            labels[pkg]?.let { if (user == work) "Work $it" else it } ?: ""
        }

    // --- Needs you ---

    @Test
    fun accessAndConnectionStates() {
        assertEquals(NeedsYouView.NotConnected, view(listOf(n("org.chat")), access = false))
        assertEquals(NeedsYouView.Connecting, view(null))
    }

    @Test
    fun summariesOngoingAndSphereItselfAreNotCounted() {
        val v = view(
            listOf(n("org.chat"), n("org.chat", summary = true), n("org.music", ongoing = true), n(own)),
        ) as NeedsYouView.Counts
        assertEquals(1, v.total)
        assertEquals(1, v.apps)
        assertEquals(listOf(NeedsYouRow("org.chat", "Chat", 1)), v.rows)
    }

    @Test
    fun rowsAreCountsPerAppMostFirstThenByName() {
        val v = view(listOf(n("org.mail"), n("org.chat"), n("org.chat"), n("org.bank"), n("org.mail"), n("org.chat"))) as NeedsYouView.Counts
        assertEquals(6, v.total)
        assertEquals(3, v.apps)
        assertEquals(listOf("Chat" to 3, "Mail" to 2, "Bank" to 1), v.rows.map { it.label to it.count })
        assertEquals(0, v.moreApps)
    }

    @Test
    fun rowsBeyondTheLayoutAreCountedAsMoreApps() {
        val v = view(listOf(n("org.mail"), n("org.chat"), n("org.bank"), n("org.news")), rows = NeedsYouModel.rowsFor(Tier.MEDIUM)) as NeedsYouView.Counts
        assertEquals(3, v.rows.size)
        assertEquals(1, v.moreApps)
        assertEquals(4, v.total)
    }

    @Test
    fun appsHiddenInSphereAreNeverNamedNorFlaggedAsHidden() {
        val v = view(listOf(n("org.bank"), n("org.bank"), n("org.chat"), n("org.news")), hidden = setOf("org.bank", "org.news")) as NeedsYouView.Counts
        assertEquals(4, v.total)
        assertEquals(3, v.apps)
        // No row of their own (named or "hidden apps"): they count in the total and "+N more apps".
        assertEquals(listOf(NeedsYouRow("org.chat", "Chat", 1)), v.rows)
        assertEquals(2, v.moreApps)
        // A hidden app in the work profile is hidden too.
        val w = view(listOf(n("org.bank", user = work), n("org.chat")), hidden = setOf("org.bank")) as NeedsYouView.Counts
        assertEquals(listOf("org.chat"), w.rows.map { it.packageName })
        assertEquals(1, w.moreApps)
    }

    @Test
    fun aWorkProfileAppIsItsOwnRowLabelledAndOpenedInItsProfile() {
        val v = view(listOf(n("org.mail"), n("org.mail", user = work), n("org.mail", user = work))) as NeedsYouView.Counts
        assertEquals(3, v.total)
        assertEquals(2, v.apps)
        assertEquals(
            listOf(NeedsYouRow("org.mail", "Work Mail", 2, work), NeedsYouRow("org.mail", "Mail", 1, 0L)),
            v.rows,
        )
    }

    @Test
    fun equalCountsAndLabelsKeepAStableOrder() {
        val v = viewLabelled(listOf(n("org.chat", user = 7L), n("org.chat", user = 3L))) { _, _ -> "Chat" }
        assertEquals(listOf(3L, 7L), (v as NeedsYouView.Counts).rows.map { it.userSerial })
    }

    private fun viewLabelled(facts: List<NotificationFact>, labelOf: (String, Long) -> String) =
        NeedsYouModel.view(true, facts, own, emptySet(), true, 5, labelOf)

    @Test
    fun aWidgetWithNoStoredOptionNamesApps() {
        assertTrue(NeedsYouModel.SHOW_NAMES_DEFAULT)
    }

    @Test
    fun countsOnlyHasNoRowsAtAll() {
        val v = view(listOf(n("org.chat"), n("org.mail")), names = false) as NeedsYouView.Counts
        assertEquals(2, v.total)
        assertEquals(2, v.apps)
        assertTrue(v.rows.isEmpty())
        assertFalse(v.namesShown)
    }

    @Test
    fun nothingWaitingIsZeroNotAnError() {
        val v = view(emptyList()) as NeedsYouView.Counts
        assertEquals(0, v.total)
        assertTrue(v.rows.isEmpty())
    }

    @Test
    fun anUnlabelledPackageIsNamedByItsPackage() {
        val v = view(listOf(n("org.unknown"))) as NeedsYouView.Counts
        assertEquals("org.unknown", v.rows.single().label)
    }

    @Test
    fun hiddenPackagesFromHiddenAppsStoreKeys() {
        val keys = setOf("org.bank/.Main#0", "org.chat/org.chat.Launcher#1234", "broken")
        assertEquals(setOf("org.bank", "org.chat"), NeedsYouModel.hiddenPackages(keys))
    }

    @Test
    fun rowsPerTier() {
        assertEquals(0, NeedsYouModel.rowsFor(Tier.SMALL))
        assertEquals(3, NeedsYouModel.rowsFor(Tier.MEDIUM))
        assertEquals(5, NeedsYouModel.rowsFor(Tier.LARGE))
    }

    // --- Pads ---

    private val a = PadRef("org.chat/org.chat.Main", 0)
    private val b = PadRef("org.mail/.Inbox", 0)
    private val bWork = PadRef("org.mail/.Inbox", 10)
    private val c = PadRef("org.bank/.Main", 0)

    @Test
    fun codecRoundTripsInOrderWithProfiles() {
        val refs = listOf(b, a, bWork)
        assertEquals(refs, PadsCodec.decode(PadsCodec.encode(refs)))
        assertEquals("org.mail", bWork.packageName)
    }

    @Test
    fun codecDropsDamageDuplicatesAndExtras() {
        assertEquals(emptyList<PadRef>(), PadsCodec.decode(null))
        assertEquals(emptyList<PadRef>(), PadsCodec.decode("2|org.chat/org.chat.Main#0"))
        assertEquals(listOf(a), PadsCodec.decode("1|org.chat/org.chat.Main#0|nonsense|org.x#1|/cls#0|org.chat/org.chat.Main#0|org.y/.Z#x"))
        val many = (1..9).map { PadRef("org.app$it/.Main", 0) }
        assertEquals(PadsCodec.MAX, PadsCodec.decode(PadsCodec.encode(many)).size)
    }

    @Test
    fun resolveKeepsOrderAndSkipsMissingAndHidden() {
        val installed = mapOf(
            a to AppFact(a, "Chat", hidden = false),
            b to AppFact(b, "Mail", hidden = false),
            c to AppFact(c, "Bank", hidden = true),
        )
        val v = PadsModel.resolve(listOf(c, b, bWork, a), installed)
        assertEquals(listOf("Mail", "Chat"), v.pads.map { it.label })
        assertEquals(2, v.unavailable) // the hidden Bank pad and the uninstalled work-profile Mail
    }

    @Test
    fun toggleAddsAtTheEndRemovesAndStopsAtSix() {
        assertEquals(listOf(a, b), PadsModel.toggle(listOf(a), b))
        assertEquals(listOf(b), PadsModel.toggle(listOf(a, b), a))
        val six = (1..6).map { PadRef("org.app$it/.Main", 0) }
        assertEquals(six, PadsModel.toggle(six, a))
    }

    @Test
    fun padsPerTier() {
        assertEquals(listOf(2, 4, 6), Tier.entries.map { PadsModel.padsFor(it) })
    }
}
