package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 46 (settingsPages), CFG-21: the pure rules: how each kind of row offers its options (one way, never two) and where the large title sits in its band. */
class OneUiPagesW46ModelTest {
    private val toggle = SettingItem.Toggle("T", Glyphs.Apps, true) {}
    private val slider = SettingItem.Slider("S", Glyphs.Sliders, 0.5f, 0f..1f, "50%") {}
    private val stepper = SettingItem.Stepper("N", Glyphs.Apps, 2, 1..5) {}
    private val plain = SettingItem.Choice("C", Glyphs.Apps, listOf("a", "b", "c", "d"), "a", { it }) {}
    private val rail = SettingItem.Choice("R", Glyphs.Apps, listOf("a", "b", "c"), "a", { it }, preview = {}) {}
    private val link = SettingItem.Action("A", Glyphs.Apps) {}
    private val nav = SettingItem.Action("N", Glyphs.Apps, navigates = true) {}
    private val pane = SettingItem.Pane("P", Glyphs.Apps, "") {}

    @Test fun CFG_21_w46_everyKindOfRowGetsExactlyOnePresentationOfItsOptions() {
        val table = mapOf(
            toggle to OneUiModel.Presentation.Switch, slider to OneUiModel.Presentation.Slider, stepper to OneUiModel.Presentation.Stepper,
            plain to OneUiModel.Presentation.Page, rail to OneUiModel.Presentation.Rail,
            link to OneUiModel.Presentation.Link, nav to OneUiModel.Presentation.LinkChevron, pane to OneUiModel.Presentation.Pane,
        )
        table.forEach { (item, want) -> assertEquals("${item.title}", want, OneUiModel.presentation(item)) }
    }

    @Test fun CFG_21_w46_noPresentationIsBothInlineAndAPage() {
        OneUiModel.Presentation.entries.forEach { p ->
            if (p.inline) {
                assertFalse("$p is inline so it has no chevron", p.chevron)
                assertFalse("$p is inline so it has no page of its own", p.opensPage)
            }
            if (p.opensPage) assertTrue("$p opens a page so it shows a chevron", p.chevron)
        }
    }

    @Test fun CFG_21_w46_aChoiceWithCardsIsInlineOnlyAndOneWithoutIsAChevronAndAPageNeverBoth() {
        val r = OneUiModel.presentation(rail); val c = OneUiModel.presentation(plain)
        assertTrue(r.inline && !r.chevron && !r.opensPage)
        assertTrue(!c.inline && c.chevron && c.opensPage)
    }

    @Test fun CFG_21_w46_theTitleSitsInTheVerticalCentreOfItsBand() {
        assertEquals(60f, OneUiModel.centredTop(240f, 120f), 0f)
        assertEquals("a one line title in a 172 dp band", 65f, OneUiModel.centredTop(172f, 42f), 0f)
        assertEquals("equal halves above and below", OneUiModel.centredTop(260f, 84f), 260f - 84f - OneUiModel.centredTop(260f, 84f), 0f)
        assertEquals("a block as tall as the band starts at the top", 0f, OneUiModel.centredTop(100f, 100f), 0f)
        assertEquals("a block taller than the band never goes above it", 0f, OneUiModel.centredTop(100f, 160f), 0f)
    }
}
