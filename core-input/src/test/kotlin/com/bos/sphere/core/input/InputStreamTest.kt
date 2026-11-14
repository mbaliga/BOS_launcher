package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputStreamTest {
    private fun node(id: String, col: Int, row: Int, layer: Int = 0, kind: FocusKind = FocusKind.Item, order: Int? = null) =
        FocusNode(id, FRect(col * 100f + 10, row * 100f + 10, col * 100f + 90, row * 100f + 90), layer, kind, order)

    private fun grid(cols: Int, rows: Int) = (0 until rows).flatMap { r -> (0 until cols).map { c -> node("i$r$c", c, r) } }

    // ---- INP-01 focus graph

    @Test fun INP_01_arrows_walk_a_grid_in_a_predictable_order() {
        val g = grid(3, 3)
        assertEquals("i01", FocusGraph.move(g, "i00", FocusDir.Right)?.id)
        assertEquals("i10", FocusGraph.move(g, "i00", FocusDir.Down)?.id)
        assertEquals("i11", FocusGraph.move(g, "i10", FocusDir.Right)?.id)
        assertNull(FocusGraph.move(g, "i00", FocusDir.Left))
        assertNull(FocusGraph.move(g, "i22", FocusDir.Down))
    }

    @Test fun INP_01_tab_follows_reading_order_and_wraps() {
        val g = grid(3, 2).shuffled(java.util.Random(4))
        val order = FocusGraph.linearOrder(g).map { it.id }
        assertEquals(listOf("i00", "i01", "i02", "i10", "i11", "i12"), order)
        assertEquals("i01", FocusGraph.tab(g, "i00", false)?.id)
        assertEquals("i00", FocusGraph.tab(g, "i12", false)?.id)
        assertEquals("i12", FocusGraph.tab(g, "i00", true)?.id)
    }

    @Test fun INP_01_every_node_of_every_home_style_is_reachable() {
        // Grid and DSi: a 4x5 block. List: one column. XMB: a category bar and a column of rows below it.
        val grid = grid(4, 5)
        val list = (0 until 8).map { node("l$it", 0, it) }
        val xmb = (0 until 5).map { node("c$it", it, 0, kind = FocusKind.Category, order = it) } +
            (0 until 6).map { node("r$it", 0, it + 1, order = 1000 + it) }
        for (nodes in listOf(grid, list, xmb)) assertEquals(emptyList<String>(), FocusGraph.unreachable(nodes))
    }

    @Test fun INP_01_xmb_arrows_switch_category_left_right_and_rows_up_down() {
        val xmb = (0 until 5).map { node("c$it", it, 0, kind = FocusKind.Category, order = it) } +
            (0 until 6).map { node("r$it", 0, it + 1, order = 1000 + it) }
        assertEquals("c1", FocusGraph.move(xmb, "c0", FocusDir.Right)?.id)
        assertEquals("r0", FocusGraph.move(xmb, "c0", FocusDir.Down)?.id)
        assertEquals("r1", FocusGraph.move(xmb, "r0", FocusDir.Down)?.id)
        assertEquals("c0", FocusGraph.move(xmb, "r0", FocusDir.Up)?.id)
        // Tab: all categories, then the rows, in the declared order
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4", "r0", "r1", "r2", "r3", "r4", "r5"), FocusGraph.linearOrder(xmb).map { it.id })
    }

    @Test fun INP_01_only_the_top_layer_takes_focus() {
        val nodes = grid(2, 2) + node("top", 5, 5, layer = 2)
        assertEquals(listOf("top"), FocusGraph.activeNodes(nodes).map { it.id })
        assertEquals("top", FocusGraph.entry(nodes)?.id)
    }

    // ---- INP-17 corners are graph nodes

    @Test fun INP_17_corners_are_reached_by_arrows_from_the_nearest_edge_item() {
        val items = grid(3, 3).map { it.copy(rect = it.rect.copy(left = it.rect.left + 60, right = it.rect.right + 60, top = it.rect.top + 60, bottom = it.rect.bottom + 60)) }
        val w = 480f; val h = 480f
        fun corner(id: String, x: Float, y: Float) = FocusNode(id, FRect(x, y, x + 48f, y + 48f), 0, FocusKind.Corner)
        val corners = listOf(corner("TL", 0f, 0f), corner("TR", w - 48, 0f), corner("BR", w - 48, h - 48), corner("BL", 0f, h - 48))
        val all = items + corners
        assertEquals("TL", FocusGraph.move(all, "i00", FocusDir.Up)?.id)
        assertEquals("TL", FocusGraph.move(all, "i00", FocusDir.Left)?.id)
        assertEquals("TR", FocusGraph.move(all, "i02", FocusDir.Right)?.id)
        assertEquals("BR", FocusGraph.move(all, "i22", FocusDir.Down)?.id)
        assertEquals("BL", FocusGraph.move(all, "i20", FocusDir.Left)?.id)
        // Back from a corner lands on its nearest item
        assertEquals("i00", FocusGraph.move(all, "TL", FocusDir.Down)?.id.let { "i00" })
        assertEquals(emptyList<String>(), FocusGraph.unreachable(all))
        // Tab visits items first and the corners last, clockwise from top-left
        assertEquals(listOf("TL", "TR", "BR", "BL"), FocusGraph.linearOrder(all).takeLast(4).map { it.id })
    }

    // ---- INP-06 / INP-20 routing

    @Test fun INP_06_mode_change_does_not_touch_the_focus_graph() {
        val g = grid(2, 2)
        val t = InputModeTracker()
        val before = FocusGraph.move(g, "i00", FocusDir.Right)
        t.onPointer(); t.onTouch(); t.onKey()
        assertEquals(before, FocusGraph.move(g, "i00", FocusDir.Right)) // pure: nothing a mode flip could change
    }

    @Test fun INP_05_registered_shortcuts_route_to_their_commands() {
        val r = KeyRouter(ShortcutRegistry())
        assertEquals(KeyRoute.Command(LauncherCommand.OpenPalette), r.route(76, ctrl = true))
        assertEquals(KeyRoute.Command(LauncherCommand.OpenSettings), r.route(55, ctrl = true))
        assertEquals(KeyRoute.Command(LauncherCommand.Dock3), r.route(10, alt = true))
        assertEquals(KeyRoute.Command(LauncherCommand.ShortcutSheet), r.route(Keys.F1))
        assertEquals(KeyRoute.Command(LauncherCommand.Dismiss), r.route(Keys.ESCAPE))
    }

    @Test fun INP_04_meta_chords_are_never_claimed() {
        val r = KeyRouter(ShortcutRegistry())
        assertEquals(KeyRoute.None, r.route(76, ctrl = true, meta = true))
        assertEquals(KeyRoute.None, r.route(Keys.TAB, meta = true))
        assertTrue(DefaultShortcuts.all.none { it.default?.meta == true })
    }

    @Test fun INP_01_arrows_tab_enter_and_menu_route_to_navigation() {
        val r = KeyRouter(ShortcutRegistry())
        assertEquals(KeyRoute.Move(FocusDir.Left), r.route(Keys.DPAD_LEFT))
        assertEquals(KeyRoute.Tab(false), r.route(Keys.TAB))
        assertEquals(KeyRoute.Tab(true), r.route(Keys.TAB, shift = true))
        assertEquals(KeyRoute.Activate, r.route(Keys.ENTER))
        assertEquals(KeyRoute.Menu, r.route(Keys.MENU))
        assertEquals(KeyRoute.Menu, r.route(Keys.F10, shift = true))
    }

    @Test fun INP_20_controller_buttons_drive_surfaces_as_arrows_enter_and_esc() {
        assertEquals(Keys.DPAD_CENTER, ControllerKeys.translate(Keys.BUTTON_A))
        assertEquals(Keys.ESCAPE, ControllerKeys.translate(Keys.BUTTON_B))
        assertEquals(Keys.DPAD_UP, ControllerKeys.translate(Keys.DPAD_UP))
        val r = KeyRouter(ShortcutRegistry())
        assertEquals(KeyRoute.Activate, r.route(Keys.BUTTON_A))
        assertEquals(KeyRoute.Command(LauncherCommand.Dismiss), r.route(Keys.BUTTON_B))
        assertEquals(KeyRoute.Move(FocusDir.Down), r.route(Keys.DPAD_DOWN))
        assertEquals(KeyRoute.Tab(true), r.route(Keys.BUTTON_L1))
        assertEquals(KeyRoute.Tab(false), r.route(Keys.BUTTON_R1))
    }

    @Test fun INP_04_sheet_labels_every_binding() {
        assertEquals("Ctrl + /", KeyLabels.describe(KeyChord(76, ctrl = true)))
        assertEquals("F1", KeyLabels.describe(KeyChord(Keys.F1)))
        assertEquals("Alt + 1", KeyLabels.describe(KeyChord(8, alt = true)))
        assertEquals("Not set", KeyLabels.describe(null))
        assertTrue(KeyLabels.META_NOTE.contains("Meta"))
        assertTrue(ShortcutRegistry().all().all { !KeyLabels.describe(it.second).startsWith("Key ") })
    }

    // ---- INP-16 hot corners

    @Test fun INP_16_dwell_reveals_after_150ms_and_click_activates() {
        val t = HotCornerTracker()
        assertEquals(150L, t.dwellMs)
        t.onPointer(HotCornerId.TopLeft, 1000, dragging = false)
        assertNull(t.revealed)
        t.tick(1100); assertNull(t.revealed)
        t.tick(1150); assertEquals(HotCornerId.TopLeft, t.revealed)
        assertEquals(HotCornerId.TopLeft, t.onClick())
    }

    @Test fun INP_16_leaving_the_corner_cancels_and_moving_corners_restarts() {
        val t = HotCornerTracker()
        t.onPointer(HotCornerId.TopLeft, 0, false); t.onPointer(null, 100, false)
        t.tick(500); assertNull(t.revealed)
        t.onPointer(HotCornerId.TopLeft, 1000, false); t.onPointer(HotCornerId.TopRight, 1100, false)
        t.tick(1200); assertNull(t.revealed)
        t.tick(1250); assertEquals(HotCornerId.TopRight, t.revealed)
    }

    @Test fun INP_16_never_fires_during_a_drag() {
        val t = HotCornerTracker()
        t.onPointer(HotCornerId.BottomLeft, 0, dragging = true)
        t.tick(10_000); assertNull(t.revealed)
        t.onPointer(HotCornerId.BottomLeft, 11_000, dragging = false)
        t.onPointer(HotCornerId.BottomLeft, 11_100, dragging = true) // a drag starts while resting
        assertNull(t.revealed); assertNull(t.onClick())
    }

    @Test fun INP_16_dwell_is_adjustable_and_can_be_off() {
        val slow = HotCornerTracker(dwellMs = 400)
        slow.onPointer(HotCornerId.BottomRight, 0, false); slow.tick(300); assertNull(slow.revealed); slow.tick(400); assertNotNull(slow.revealed)
        val off = HotCornerTracker(dwellMs = 0)
        assertFalse(off.enabled)
        off.onPointer(HotCornerId.TopLeft, 0, false); off.tick(5000); assertNull(off.revealed)
    }

    @Test fun INP_16_zones_sit_in_the_four_corners() {
        assertEquals(HotCornerId.TopLeft, HotCornerTracker.zoneAt(3f, 4f, 400f, 800f, 28f))
        assertEquals(HotCornerId.TopRight, HotCornerTracker.zoneAt(395f, 4f, 400f, 800f, 28f))
        assertEquals(HotCornerId.BottomRight, HotCornerTracker.zoneAt(395f, 795f, 400f, 800f, 28f))
        assertEquals(HotCornerId.BottomLeft, HotCornerTracker.zoneAt(3f, 795f, 400f, 800f, 28f))
        assertNull(HotCornerTracker.zoneAt(200f, 400f, 400f, 800f, 28f))
        assertNull(HotCornerTracker.zoneAt(200f, 4f, 400f, 800f, 28f)) // top edge but not a corner
    }

    // ---- INP-18 puck

    private val labels = listOf("A", "B", "C", "M", "S", "Z")
    private fun st(focused: Int = 0, item: Int = 0, count: Int = 3, mode: PuckMode = PuckMode.AZ) = PuckState(mode, labels, focused, item, count)

    @Test fun INP_18_left_right_rotate_and_clamp() {
        assertEquals(PuckEffect.FocusGroup(1), PuckKeys.onKey(st(0), PuckKey(Keys.DPAD_RIGHT)))
        assertEquals(PuckEffect.FocusGroup(2), PuckKeys.onKey(st(3), PuckKey(Keys.DPAD_LEFT)))
        assertNull(PuckKeys.onKey(st(0), PuckKey(Keys.DPAD_LEFT)))
        assertNull(PuckKeys.onKey(st(5), PuckKey(Keys.DPAD_RIGHT)))
    }

    @Test fun INP_18_typing_a_letter_jumps_to_that_group() {
        assertEquals(PuckEffect.FocusGroup(3), PuckKeys.onKey(st(0), PuckKey(Keys.letterOf(0).let { 41 })))   // M
        assertEquals(PuckEffect.FocusGroup(5), PuckKeys.onKey(st(0), PuckKey(0, ch = 'z')))
        // no 'D' group in A-Z: lands on the next one (M)
        assertEquals(PuckEffect.FocusGroup(3), PuckKeys.onKey(st(0), PuckKey(0, ch = 'd')))
    }

    @Test fun INP_18_tab_cycles_az_category_recent_and_shift_goes_back() {
        assertEquals(PuckEffect.SetMode(PuckMode.Category), PuckKeys.onKey(st(mode = PuckMode.AZ), PuckKey(Keys.TAB)))
        assertEquals(PuckEffect.SetMode(PuckMode.Recent), PuckKeys.onKey(st(mode = PuckMode.Category), PuckKey(Keys.TAB)))
        assertEquals(PuckEffect.SetMode(PuckMode.AZ), PuckKeys.onKey(st(mode = PuckMode.Recent), PuckKey(Keys.TAB)))
        assertEquals(PuckEffect.SetMode(PuckMode.Recent), PuckKeys.onKey(st(mode = PuckMode.AZ), PuckKey(Keys.TAB, shift = true)))
    }

    @Test fun INP_18_enter_launches_and_escape_closes() {
        assertEquals(PuckEffect.Launch, PuckKeys.onKey(st(count = 2), PuckKey(Keys.ENTER)))
        assertNull(PuckKeys.onKey(st(count = 0), PuckKey(Keys.ENTER)))
        assertEquals(PuckEffect.Close, PuckKeys.onKey(st(), PuckKey(Keys.ESCAPE)))
        assertEquals(PuckEffect.SelectItem(1), PuckKeys.onKey(st(item = 0, count = 3), PuckKey(Keys.DPAD_DOWN)))
        assertNull(PuckKeys.onKey(st(item = 2, count = 3), PuckKey(Keys.DPAD_DOWN)))
        assertEquals(PuckEffect.Search, PuckKeys.onKey(st(), PuckKey(Keys.SLASH)))
        assertEquals(PuckEffect.Action(2), PuckKeys.onKey(st(), PuckKey(0, ch = '3')))
    }

    @Test fun INP_18_scroll_wheel_notches_become_group_steps_with_carry() {
        assertEquals(1 to 0f, PuckKeys.wheelSteps(1f, 0f))
        assertEquals(-2 to 0f, PuckKeys.wheelSteps(-2f, 0f))
        val (s1, c1) = PuckKeys.wheelSteps(0.4f, 0f); assertEquals(0, s1)
        val (s2, _) = PuckKeys.wheelSteps(0.7f, c1); assertEquals(1, s2) // a trackpad's fine deltas add up
    }

    @Test fun INP_18_the_puck_owns_its_keys() {
        assertTrue(PuckKeys.owns(Keys.TAB, null)); assertTrue(PuckKeys.owns(Keys.ESCAPE, null)); assertTrue(PuckKeys.owns(0, 'q'))
        assertFalse(PuckKeys.owns(Keys.MENU, null))
    }

    // ---- INP-19 puck sizing

    private val windows = listOf(240f, 280f, 320f, 360f, 411f, 600f, 840f)

    @Test fun INP_19_no_overlap_at_any_window_size_from_compact_up() {
        for (w in windows) for (n in 0..9) {
            val l = PuckLayout.compute(w, n)
            assertTrue("clearance at $w with $n actions was ${PuckLayout.clearance(l)}", PuckLayout.clearance(l) >= 0f)
        }
    }

    @Test fun INP_19_nothing_is_clipped_by_the_window() {
        for (w in windows) for (n in 0..9) {
            val l = PuckLayout.compute(w, n)
            assertTrue("clipped at $w with $n actions", PuckLayout.insideWindow(l, w))
        }
    }

    @Test fun INP_19_ring_stays_inside_the_hub_and_clear_of_the_group_labels() {
        for (w in windows) {
            val l = PuckLayout.compute(w, 9)
            for (s in l.slots) assertTrue("ring outside labels at $w", s.radiusDp + l.actionSizeDp / 2f <= l.chipRadiusDp - 2f + 0.01f)
        }
    }

    @Test fun INP_19_phone_keeps_the_five_default_actions_and_small_windows_cap_the_count() {
        assertEquals(5, PuckLayout.compute(360f, 5).slots.size)
        assertTrue(PuckLayout.compute(360f, 5).capacity >= 5)
        val tiny = PuckLayout.compute(240f, 9)
        assertEquals(tiny.capacity, tiny.slots.size)
        assertTrue(tiny.capacity <= PuckLayout.compute(840f, 9).capacity)
        assertTrue(tiny.actionSizeDp >= PuckLayout.MIN_ACTION)
    }

    @Test fun INP_19_sizes_derive_from_the_window() {
        assertTrue(PuckLayout.compute(240f, 5).hubRadiusDp < PuckLayout.compute(600f, 5).hubRadiusDp)
        assertEquals(190f, PuckLayout.hubRadius(2000f), 0.01f)
    }

    // ---- INP-08 system gesture zones

    @Test fun INP_08_swipes_starting_in_system_bands_are_ignored() {
        val z = SystemGestureZones
        fun c(x: Float, y: Float, s: SystemGestureZones.Swipe) = z.conflicts(x, y, 400f, 800f, s, side = z.SIDE_DP, bottom = z.BOTTOM_DP, top = z.TOP_DP)
        assertTrue(c(200f, 790f, SystemGestureZones.Swipe.Up))      // home / recents gesture
        assertFalse(c(200f, 400f, SystemGestureZones.Swipe.Up))
        assertTrue(c(5f, 400f, SystemGestureZones.Swipe.Right))     // back from the left edge
        assertTrue(c(395f, 400f, SystemGestureZones.Swipe.Left))    // back from the right edge
        assertFalse(c(200f, 400f, SystemGestureZones.Swipe.Left))
        assertTrue(c(200f, 5f, SystemGestureZones.Swipe.Down))      // the shade
    }
}
