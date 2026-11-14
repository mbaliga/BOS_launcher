package com.bos.sphere.feature.dock

import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.input.LauncherCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockLogicTest {
    private fun app(n: String) = DockItemRef.app(n)
    private val labels = mapOf("a" to "Alpha", "b" to "Beta", "c" to "Gamma", "d" to "Delta")
    private fun build(
        pins: List<DockItemRef> = listOf(app("a"), app("b")), recent: List<String> = listOf("c", "a", "d"),
        granted: Boolean = true, mode: DockMode = DockMode.PinnedPlusRecent, used: Set<String> = emptySet(), media: MediaInfo? = null,
    ) = DockContentBuilder.build(pins, { labels[it.id] }, recent, { labels[it] }, granted, mode, used, media)

    @Test fun DCK_01_pinsReorderByDragAndKeyboard() {
        val pins = listOf(app("a"), app("b"), app("c"))
        assertEquals(listOf(app("b"), app("c"), app("a")), DockPins.move(pins, 0, 2))
        assertEquals(listOf(app("b"), app("a"), app("c")), DockPins.moveBy(pins, app("a"), +1))
        assertEquals(pins, DockPins.moveBy(pins, app("a"), -1))
        assertEquals(2, DockReorder.targetIndex(0, 130f, 60f, 3))
        assertEquals(0, DockReorder.targetIndex(1, -500f, 60f, 3))
        assertEquals(pins, DockPins.add(pins, app("a")))
    }

    @Test fun DCK_01_keyboardMapsArrowsAndAltToFocusAndReorder() {
        assertEquals(DockKeyAction.FocusNext, DockKeys.interpret(DockKeys.DPAD_RIGHT, false, horizontal = true))
        assertEquals(DockKeyAction.MoveEarlier, DockKeys.interpret(DockKeys.DPAD_LEFT, true, horizontal = true))
        assertEquals(DockKeyAction.MoveLater, DockKeys.interpret(DockKeys.DPAD_DOWN, true, horizontal = false))
        assertEquals(DockKeyAction.Activate, DockKeys.interpret(DockKeys.ENTER, false, true))
        assertEquals(DockKeyAction.None, DockKeys.interpret(DockKeys.DPAD_UP, false, true))
        assertEquals(0, DockKeys.step(0, -1, 5)); assertEquals(4, DockKeys.step(4, 1, 5))
    }

    @Test fun DCK_01_pinsCanBeAppsShortcutsAndFolders() {
        for (r in listOf(app("x#1"), DockItemRef(ItemKind.Shortcut, "pkg/s1"), DockItemRef(ItemKind.Folder, "f1"), DockItemRef.group("g"))) assertEquals(r, DockItemRef.decode(r.encode()))
        assertEquals(null, DockItemRef.decode("nonsense"))
    }

    @Test fun DCK_02_recentExcludesPinnedAndKeepsNewestFirst() {
        val c = build()
        assertEquals(listOf("c", "d"), c.recent.map { it.ref.id })
        assertTrue(c.showRecentSection)
    }

    @Test fun DCK_02_sectionIsLabelledRecentNeverRunning() {
        assertEquals("Recent", DockText.RECENT_LABEL)
        listOf(DockText.RECENT_LABEL, DockText.RECENCY_HELP, DockText.ENABLE_ROW).forEach { assertFalse(it.contains("Running", ignoreCase = false)) }
    }

    @Test fun DCK_03_pinnedAppsUsedThisSessionCarryMarkerAndHelpExplainsIt() {
        val c = build(used = setOf("b"))
        assertEquals(listOf(false, true), c.pinned.map { it.recentlyUsed })
        assertTrue(DockText.RECENCY_HELP.contains("used in this session"))
        assertTrue(c.recent.none { it.recentlyUsed })
    }

    @Test fun DCK_04_defaultsFollowTierAndUserChoiceWins() {
        val d = DockSettings()
        assertFalse(d.isEnabled(Tier.T1)); assertTrue(d.isEnabled(Tier.T2)); assertTrue(d.isEnabled(Tier.T3))
        assertTrue(d.copy(enabled = true).isEnabled(Tier.T1)); assertFalse(d.copy(enabled = false).isEnabled(Tier.T3))
    }

    @Test fun DCK_04_collapsedBandAndPinnedPlusRecentModes() {
        val band = build(mode = DockMode.Collapsed)
        assertTrue(band.collapsed); assertTrue(band.recent.isEmpty()); assertFalse(band.showEnableRow)
        assertFalse(build().collapsed)
    }

    @Test fun DCK_04_edgeIsBottomInPortraitAndChosenInLandscape() {
        val d = DockSettings(edge = DockEdge.Left)
        assertEquals(DockEdge.Bottom, d.edgeFor(landscape = false)); assertEquals(DockEdge.Left, d.edgeFor(landscape = true))
    }

    @Test fun DCK_05_focusAndNumberShortcutsLaunchItemsOneToNine() {
        var focused = 0; val launched = mutableListOf<Int>()
        val h = DockCommandHandler({ focused++ }, { launched += it; true })
        assertTrue(h.handle(LauncherCommand.FocusDock)); assertEquals(1, focused)
        for (n in 1..9) assertTrue(h.handle(LauncherCommand.valueOf("Dock$n")))
        assertEquals((0..8).toList(), launched)
        assertFalse(h.handle(LauncherCommand.OpenPalette))
    }

    @Test fun DCK_05_numberedItemsAreFirstNineInRailOrder() {
        val many = (1..12).map { app("p$it") }
        val c = DockContentBuilder.build(many, { it.id }, emptyList(), { null }, true, DockMode.PinnedPlusRecent, emptySet(), null)
        assertEquals(9, c.numbered.size); assertEquals("p9", c.numbered.last().label)
    }

    @Test fun DCK_06_withoutUsageAccessRecentIsReplacedByOneEnableRowAndPinsWork() {
        val c = build(granted = false)
        assertTrue(c.showEnableRow); assertFalse(c.showRecentSection); assertTrue(c.recent.isEmpty())
        assertEquals(2, c.pinned.size)
    }

    @Test fun DCK_07_displaysShareOnePinList() {
        var pins = listOf(app("a"))
        val hub = DockHub({ pins }, { pins = it })
        val main = hub.forDisplay(0); val ext = hub.forDisplay(5)
        assertNotEquals(main, ext)
        ext.pin(app("b"))
        assertEquals(listOf(app("a"), app("b")), main.sharedPins)
        main.reorder(0, 1)
        assertEquals(ext.sharedPins, main.sharedPins)
        hub.release(5); assertEquals(setOf(0), hub.displays()); assertEquals(2, pins.size)
    }

    @Test fun DCK_08_mediaItemOnlyWhenASessionIsActive() {
        assertEquals(null, build().media)
        val m = MediaInfo("t", "a", "p", playing = true, canPrev = true, canNext = false)
        assertEquals(m, build(media = m).media)
    }

    @Test fun DCK_01_uninstalledPinsAreSkippedButKept() {
        val c = build(pins = listOf(app("a"), app("gone")))
        assertEquals(1, c.pinned.size)
    }
}
