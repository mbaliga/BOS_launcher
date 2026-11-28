package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerAndProfilesTest {
    private val apps = listOf(
        DrawerApp("1", "Maps"), DrawerApp("2", "Mail"), DrawerApp("3", "Äpfel"), DrawerApp("4", "1Password"),
        DrawerApp("5", "Slack", ProfileKind.Work), DrawerApp("6", "Vault", ProfileKind.Private), DrawerApp("7", "Zoom"),
    )

    @Test
    fun HOM_05_alphabeticalIndexWithOtherLast() {
        val r = DrawerModel.build(apps, DrawerTab.Personal)
        assertEquals(listOf('A', 'M', 'Z', '#'), r.letters)
        assertEquals(listOf("Äpfel", "Mail", "Maps", "Zoom", "1Password"), r.flat.map { it.label })
        assertEquals(1, r.offsetOf('M') - r.offsetOf('A'))
        assertEquals(-1, r.offsetOf('Q'))
    }

    @Test
    fun HOM_05_typeToFilterPrefixBeatsSubstring() {
        val r = DrawerModel.build(apps + DrawerApp("8", "Gamma"), DrawerTab.Personal, filter = "ma")
        assertEquals(listOf("Mail", "Maps", "Gamma"), r.flat.map { it.label })
        assertTrue(r.letters.isEmpty())
        assertEquals(listOf("Äpfel"), DrawerModel.build(apps, DrawerTab.Personal, filter = "apf").flat.map { it.label })
    }

    @Test
    fun HOM_05_workAndPersonalTabsSeparate() {
        assertEquals(listOf("Slack"), DrawerModel.build(apps, DrawerTab.Work).flat.map { it.label })
        assertFalse(DrawerModel.build(apps, DrawerTab.Personal).flat.any { it.profile == ProfileKind.Work })
    }

    @Test
    fun HOM_05_hiddenAppsAndTags() {
        val tags = mapOf("1" to setOf("travel"), "7" to setOf("work", "video"))
        assertFalse(DrawerModel.build(apps, DrawerTab.Personal, hidden = setOf("1")).flat.any { it.key == "1" })
        assertEquals(listOf("Maps"), DrawerModel.build(apps, DrawerTab.Personal, hidden = setOf("1"), showHidden = true).flat.map { it.label })
        assertEquals(listOf("Zoom"), DrawerModel.build(apps, DrawerTab.Personal, tags = tags, tag = "video").flat.map { it.label })
        assertEquals(listOf("Zoom"), DrawerModel.build(apps, DrawerTab.Personal, tags = tags, filter = "vid").flat.map { it.label })
    }

    @Test
    fun HOM_05_tagCodecRoundTripAndToggle() {
        var t = TagCodec.toggle(emptyMap(), "k,1", "a,b")
        t = TagCodec.toggle(t, "k2", "x")
        assertEquals(t, TagCodec.decode(TagCodec.encode(t)))
        assertEquals(listOf("a,b", "x"), TagCodec.allTags(t))
        assertEquals(setOf("k2"), TagCodec.toggle(t, "k,1", "a,b").keys)
    }

    private val work = ProfileInfo(1, ProfileKind.Work)
    private val priv = ProfileInfo(2, ProfileKind.Private, quiet = false)

    @Test
    fun HOM_06_privateSpaceOnlyWhenUnlockedOnApi35AndCapable() {
        assertTrue(ProfilePolicy.visible(priv, 35, true))
        assertFalse(ProfilePolicy.visible(priv.copy(quiet = true), 35, true))
        assertFalse(ProfilePolicy.visible(priv, 34, true))
        assertFalse(ProfilePolicy.visible(priv, 35, false))
    }

    @Test
    fun HOM_06_workAppsBadgedPausableAndStayListedWhenPaused() {
        val paused = work.copy(quiet = true)
        assertTrue(ProfilePolicy.badged(ProfileKind.Work))
        assertTrue(ProfilePolicy.canPause(ProfileKind.Work))
        assertFalse(ProfilePolicy.canPause(ProfileKind.Private))
        assertTrue(ProfilePolicy.visible(paused, 34, false))
        assertFalse(ProfilePolicy.launchable(paused))
    }

    @Test
    fun HOM_06_tabsFollowVisibleProfiles() {
        val all = listOf(ProfileInfo(0, ProfileKind.Personal), work, priv.copy(quiet = true))
        val vis = ProfilePolicy.visibleProfiles(all, 35, true)
        assertEquals(listOf(DrawerTab.Personal, DrawerTab.Work), ProfilePolicy.tabs(vis))
        assertEquals(listOf(DrawerTab.Personal), ProfilePolicy.tabs(listOf(ProfileInfo(0, ProfileKind.Personal))))
    }
}
