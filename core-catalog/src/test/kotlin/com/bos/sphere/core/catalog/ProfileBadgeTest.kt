package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-06: the second work badge decision and the private-space gate. */
class ProfileBadgeTest {
    @Test
    fun HOM_06_aSystemBadgedIconGetsNoSecondBadge() {
        for (src in listOf(IconSource.Adaptive, IconSource.Legacy)) {
            assertEquals(ProfileBadgeKind.None, ProfileBadgeRule.decide(ProfileKind.Work, src))
            assertEquals(ProfileBadgeKind.None, ProfileBadgeRule.decide(ProfileKind.Private, src))
        }
    }

    @Test
    fun HOM_06_anIconTheLauncherReplacedGetsTheBadgeDrawnExplicitly() {
        for (src in listOf(IconSource.Override("pack/x"), IconSource.Pack("pk", "d"), IconSource.Themed)) {
            assertTrue(ProfileBadgeRule.systemBadgeLost(src))
            assertEquals(ProfileBadgeKind.Work, ProfileBadgeRule.decide(ProfileKind.Work, src))
            assertEquals(ProfileBadgeKind.Private, ProfileBadgeRule.decide(ProfileKind.Private, src))
        }
    }

    @Test
    fun HOM_06_personalAndOtherProfilesNeverGetABadge() {
        assertEquals(ProfileBadgeKind.None, ProfileBadgeRule.decide(ProfileKind.Personal, true))
        assertEquals(ProfileBadgeKind.None, ProfileBadgeRule.decide(ProfileKind.Other, true))
        assertFalse(ProfileBadgeRule.systemBadgeLost(IconSource.Adaptive))
    }

    @Test
    fun HOM_06_privateGateIsHiddenWithoutAProfileBelowApi35OrWithoutTheCapability() {
        assertEquals(PrivateSpaceGate.Hidden, PrivateSpaceGate.of(null, 35, true))
        assertEquals(PrivateSpaceGate.Hidden, PrivateSpaceGate.of(true, 34, true))
        assertEquals(PrivateSpaceGate.Hidden, PrivateSpaceGate.of(false, 35, false))
    }

    @Test
    fun HOM_06_lockedOffersUnlockAndUnlockedOffersLockOnlyInThePrivateTab() {
        val locked = PrivateSpaceGate.of(true, 35, true)
        val open = PrivateSpaceGate.of(false, 35, true)
        assertEquals(PrivateSpaceGate.Locked, locked)
        assertEquals(PrivateSpaceGate.Unlocked, open)
        assertEquals("Unlock private space", PrivateSpaceGate.chipLabel(locked, false))
        assertEquals("Lock private space", PrivateSpaceGate.chipLabel(open, true))
        assertNull(PrivateSpaceGate.chipLabel(open, false))
        assertNull(PrivateSpaceGate.chipLabel(PrivateSpaceGate.Hidden, true))
    }

    @Test
    fun HOM_06_aLockedPrivateSpaceStaysOutOfTheListAndTheTabs() {
        val locked = ProfileInfo(10, ProfileKind.Private, quiet = true)
        val vis = ProfilePolicy.visibleProfiles(listOf(ProfileInfo(0, ProfileKind.Personal), locked), 35, true)
        assertFalse(ProfilePolicy.tabs(vis).contains(DrawerTab.Private))
    }
}
