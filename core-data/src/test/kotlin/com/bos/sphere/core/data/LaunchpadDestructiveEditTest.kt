package com.bos.sphere.core.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-04: removing a Launchpad rule (or clearing an app policy) snapshots first; adding one does not. */
class LaunchpadDestructiveEditTest {
    @After fun clean() { DestructiveEdits.install(null); LaunchpadSettings.rules.value.forEach { LaunchpadSettings.removeRule(it.id) } }

    @Test
    fun CFG_04_removingALaunchpadRuleAnnouncesTheEditBeforeItIsApplied() {
        val seen = ArrayList<Pair<String, Int>>()
        DestructiveEdits.install { seen += it to LaunchpadSettings.rules.value.size }
        LaunchpadSettings.addRule(Rule(id = "r1", packageName = "a.b", action = RuleAction.entries.first()))
        assertTrue("adding is not destructive", seen.isEmpty())
        LaunchpadSettings.removeRule("r1")
        assertEquals(listOf("remove Comms rule" to 1), seen) // the rule was still present when the hook ran
        LaunchpadSettings.removeRule("missing")
        assertEquals(1, seen.size)
    }

    @Test
    fun CFG_04_clearingAPerAppPolicyIsAlsoSnapshotted() {
        val seen = ArrayList<String>()
        LaunchpadSettings.setPolicy(AppPolicy("p.q", mode = DeliveryMode.entries.first { it != DeliveryMode.Immediate }))
        DestructiveEdits.install { seen += it }
        LaunchpadSettings.setPolicy(AppPolicy("p.q")) // back to the default: the stored policy is dropped
        assertEquals(listOf("clear Comms policy"), seen)
        LaunchpadSettings.setPolicy(AppPolicy("p.q"))
        assertEquals(1, seen.size) // nothing stored any more
    }
}
