package com.bos.sphere.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** INP-10, wave 7: a dropped part's default chord can itself be taken; then the command ends unbound. */
class W7ImportEdgeTest {
    private val x = KeyChord(52, ctrl = true)
    private val puckDefault = KeyChord(44, ctrl = true)

    private fun assertNoSharedChord(r: ShortcutRegistry) {
        val used = r.all().mapNotNull { it.second }
        assertEquals("no two commands share a chord", used.size, used.toSet().size)
    }

    @Test fun INP_10_a_dropped_part_whose_default_is_taken_by_a_kept_override_ends_unbound() {
        val r = ShortcutRegistry()
        // OpenPuck's part clashes with the earlier OpenDrawer part and is dropped; its default is then taken by OpenSettings.
        r.import("OpenDrawer=${x.encode()};OpenPuck=${x.encode()};OpenSettings=${puckDefault.encode()}")
        assertEquals(x, r.binding(LauncherCommand.OpenDrawer))
        assertEquals(puckDefault, r.binding(LauncherCommand.OpenSettings))
        assertNull(r.binding(LauncherCommand.OpenPuck))
        assertNoSharedChord(r)
    }

    @Test fun INP_10_a_dropped_part_whose_default_is_free_still_falls_back_to_it() {
        val r = ShortcutRegistry()
        r.import("OpenDrawer=${x.encode()};OpenPuck=${x.encode()}")
        assertEquals(puckDefault, r.binding(LauncherCommand.OpenPuck))
        assertNoSharedChord(r)
    }

    @Test fun INP_10_a_part_clashing_with_an_untouched_default_is_dropped_and_that_default_stays() {
        val r = ShortcutRegistry()
        r.import("OpenPuck=${KeyChord(32, ctrl = true).encode()}") // the drawer's default
        assertEquals(KeyChord(32, ctrl = true), r.binding(LauncherCommand.OpenDrawer))
        assertEquals(puckDefault, r.binding(LauncherCommand.OpenPuck))
    }

    @Test fun INP_10_random_files_never_leave_two_commands_on_one_chord() {
        val rnd = Random(10)
        val cmds = LauncherCommand.entries
        val pool = cmds.mapNotNull { DefaultShortcuts.all.firstOrNull { d -> d.command == it }?.default } + listOf(x, KeyChord(1), KeyChord(2, alt = true))
        repeat(400) {
            val raw = (0 until rnd.nextInt(12)).joinToString(";") {
                val c = cmds[rnd.nextInt(cmds.size)]
                val chord = if (rnd.nextInt(6) == 0) "-" else pool[rnd.nextInt(pool.size)].encode()
                "${c.name}=$chord"
            }
            val r = ShortcutRegistry(); r.import(raw)
            assertNoSharedChord(r)
            val again = ShortcutRegistry(); again.import(r.export())
            assertEquals("a resolved import is stable: $raw", r.all(), again.all())
            assertTrue(r.all().size == cmds.size)
        }
    }
}
