package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StrExportTest {
    @Test
    fun STR_04_deliveryPoliciesRoundTripThroughTheConfigFile() {
        val policies = listOf(
            AppPolicy("com.a", DeliveryMode.Digest, listOf(480, 1080)),
            AppPolicy("com.b", DeliveryMode.SilentInLaunchpad),
        )
        val doc = StateCodec.export(StateParts(launchpadPolicies = policies), 1L)
        val back = StateCodec.decode(StateDocument.parse(doc.encode())).parts
        assertEquals(policies, back.launchpadPolicies)
    }

    @Test
    fun STR_04_applierExportsAndRestoresPolicies() {
        val src = StateTestSupport.FakeLaunchpad().also { it.held = listOf(AppPolicy("com.a", DeliveryMode.Digest, listOf(600))) }
        val dst = StateTestSupport.FakeLaunchpad()
        val live = StateParts(launchpadPolicies = src.policies())
        assertEquals(src.held, live.launchpadPolicies)
        dst.setPolicies(live.launchpadPolicies)
        assertEquals(src.held, dst.policies())
    }
}
