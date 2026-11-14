package com.bos.sphere.ui

import com.bos.sphere.core.data.Connectivity
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.LAUNCHPAD_SCOPE_NOTICE
import com.bos.sphere.core.data.LaunchpadAction
import com.bos.sphere.core.data.LaunchpadGesture
import com.bos.sphere.core.data.RowCommand
import com.bos.sphere.core.data.SnoozeLogic
import com.bos.sphere.core.data.SoundState
import com.bos.sphere.core.data.SystemSnapshot
import com.bos.sphere.core.data.SystemStateLogic
import com.bos.sphere.core.data.SystemTileKind
import com.bos.sphere.core.design.GrantGateLogic
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.PaletteSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CFG-19: strings that reach the UI through L10n.tr(variable) (enum labels, pure-logic texts) cannot be found by the
 * source scan, so each such set is listed here and checked against all eight languages.
 */
class LaunchpadStringsTest {
    private val codes = listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")

    private fun launchpadEnglish(): Set<String> = buildSet {
        RowCommand.entries.forEach { add(it.label) }
        // Palette cell captions (shortened labels shown on the slid-right row).
        addAll(listOf("Snooze", "Read", "Mute", "Open", "Rule", "Actions"))
        SnoozeLogic.Preset.entries.forEach { add(it.label) }
        DeliveryMode.entries.forEach { add(it.label); add(it.hint) }
        add(LAUNCHPAD_SCOPE_NOTICE)
        SystemTileKind.entries.forEach { add(it.label) }
        for (c in Connectivity.entries) for (s in SoundState.entries) SystemStateLogic.tiles(SystemSnapshot(0, 50, true, c, s)).forEach { t -> if (t.value.isNotEmpty() && t.kind != SystemTileKind.Battery) add(t.value); if (t.detail.isNotEmpty()) add(t.detail) }
        LaunchpadAction.entries.forEach { add(it.label) }
        LaunchpadGesture.entries.forEach { add(it.label); add(it.hint) }
        addAll(GrantGateLogic.allEnglish)
        PaletteSource.entries.forEach { add(it.label) }
        addAll(listOf("Default", "Slide left runs", "Delivery per app", "Add an app (type to search)", "App and conversation", "Digest", "next at %s", "Reply sent", "Snoozed", "Write a reply", "Send", "Reply", "Close Launchpad", "Home", "%d waiting. Launchpad", "%d held for the digest at %s"))
    }

    @Test fun CFG_19_launchpadLabelsExistInAllEightLanguages() {
        val problems = launchpadEnglish().filter { k -> codes.any { L10n.tableFor(it)?.containsKey(k) != true } }
        assertTrue("Missing in some language:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
