package com.bos.sphere.ui

import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomeScroll
import com.bos.sphere.core.data.RowCommand
import com.bos.sphere.core.design.L10n
import org.junit.Assert.assertTrue
import org.junit.Test

/** CFG-19 (wave 13 audit): labels held in enums and shown through `L10n.tr(label)` are invisible to the source scan; check them by value. */
class W13LabelsTest {
    private val codes = listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")

    private fun missing(labels: Collection<String>) = labels.flatMap { l -> codes.filter { L10n.tableFor(it)?.containsKey(l) != true }.map { "$l/$it" } }

    @Test fun CFG_19_everyRowCommandLabelAndItsShortPaletteFormIsTranslated() {
        assertTrue(missing(RowCommand.entries.map { it.label } + listOf("Snooze", "Read", "Mute", "Open", "Rule")).toString(), missing(RowCommand.entries.map { it.label } + listOf("Snooze", "Read", "Mute", "Open", "Rule")).isEmpty())
    }

    @Test fun CFG_19_everyHomeOrderAndPagesModeLabelIsTranslated() {
        val labels = HomeOrder.entries.map { it.label } + HomeScroll.entries.map { it.label }
        assertTrue(missing(labels).toString(), missing(labels).isEmpty())
    }
}
